package org.openardf.radiooracle.desktop

import kotlinx.coroutines.CancellationException
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.effectiveLengthMeters

internal data class DesktopCourseBriefReport(
    val categoryId: String,
    val courseName: String,
    val horizontalLengthMeters: Int? = null,
    val climbMeters: Int? = null,
    val effectiveLengthMeters: Int? = null,
    val idealOrder: List<String> = emptyList(),
    val estimatedIdealSeconds: Int? = null,
    val routeMap: DesktopCourseRouteMap? = null,
    val notice: String? = null,
    val isLocked: Boolean = false,
    val legWarnings: List<String> = emptyList(),
    val assumedPaceMinutesPerKm: Double? = null,
    val elevationProfile: DesktopCourseElevationProfileSummary? = null,
    val idealRouteLegs: List<DesktopCourseBriefLeg> = emptyList()
)

/** A displayed course-object leg; mandatory waypoints shape its distance but never become table rows. */
internal data class DesktopCourseBriefLeg(
    val fromLabel: String,
    val toLabel: String,
    val distanceMeters: Int?
)

/** A read-only summary of active courses, independent of the CSV's control-set grouping. */
internal object DesktopCourseBriefReports {
    fun imported(project: EventProjectFile, categoryIds: Set<String>, password: String?, checkCancelled: () -> Unit = {}): List<DesktopCourseBriefReport> =
        (project.raceData.categories + project.raceData.courseMappings).filter { it.category.id in categoryIds }.map { data ->
            checkCancelled()
            report(project, data, data.category.storedCourseInfo(password), data.category.storedIdealOrder(password),
                DesktopVenueElevationCache::elevationMeters, importedRoute = true, checkCancelled = checkCancelled)
        }

    fun build(
        project: EventProjectFile,
        courseInfos: Map<String, ProtectedCourseInfo>,
        idealOrders: Map<String, String> = emptyMap(),
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        checkCancelled: () -> Unit = {},
        includeUnassigned: Boolean = false
    ): List<DesktopCourseBriefReport> = (project.raceData.categories +
        if (includeUnassigned) project.raceData.courseMappings else emptyList()).sortedBy { it.category.order }.map { data ->
        checkCancelled()
        val category = data.category
        val info = courseInfos[category.id] ?: category.courseInfo.takeIf { category.encryptedCourseInfo == null }
        val order = idealOrders[category.id] ?: category.idealOrder.takeIf { category.encryptedIdealOrder == null }
        report(project, data, info, order, elevationLookup, checkCancelled = checkCancelled)
    }

    private fun report(
        project: EventProjectFile, data: EventCategoryData, info: ProtectedCourseInfo?, order: String?,
        elevationLookup: (CourseGeoPoint) -> Double?, importedRoute: Boolean = false, checkCancelled: () -> Unit = {}
    ): DesktopCourseBriefReport {
        val category = data.category
        val fallback = DesktopCourseBriefReport(
            category.id, category.name,
            horizontalLengthMeters = category.lengthMeters.takeIf { it > 0 },
            climbMeters = category.climbMeters.takeIf { category.lengthMeters > 0 || it > 0 }
        )
        if (info == null || (info.route.isEmpty() && info.courseObjects.isEmpty())) return fallback.copy(
            isLocked = category.encryptedCourseInfo != null,
            notice = if (category.encryptedCourseInfo != null) "Unlock course data to calculate this report."
                else if (importedRoute) "This import supplies assignments and course facts without geographic route data."
                else "Import course locations and route data to calculate the ideal order and graphic."
        )
        val reviewedIof = info.sourceName.startsWith("IOF CourseData:") && info.appliedBindings != null
        return try {
            if (reviewedIof && info.route.isEmpty()) {
                val calculated = DesktopIofCourseAnalysis.recalculateForReport(project, data, info, elevationLookup, checkCancelled)
                return report(project, data, calculated.info, calculated.info.idealOrder, elevationLookup,
                    importedRoute, checkCancelled).let { refreshed ->
                    refreshed.copy(notice = listOfNotNull(calculated.notice,
                        refreshed.notice.takeIf { refreshed.routeMap == null }).joinToString(" "),
                        routeMap = refreshed.routeMap?.copy(title = "Calculated ideal route"))
                }
            }
            val summary = DesktopCourseAnalyzer.analyze(
                project, category.id, info, order,
                elevationLookup = elevationLookup,
                controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS,
                allowFoxRenumbering = false
            )
            // When both routes match, the analyzer's calculated section intentionally contains only a note.
            val section = if (importedRoute || reviewedIof) summary.providedRouteSection else summary.calculatedRouteSection?.takeUnless { it.summaryOnly }
                ?: summary.providedRouteSection
            if (section == null) fallback.copy(notice = "The import does not contain enough geographic data to draw a route.".takeIf { importedRoute }
                ?: "Course geometry is incomplete. Review this course in Course Analyzer.")
            else DesktopCourseBriefReport(
                category.id, category.name,
                if (reviewedIof) info.lengthMeters else section.routeLengthMeters,
                if (reviewedIof) info.climbMeters else section.climbMeters,
                if (reviewedIof) info.effectiveLengthMeters() else section.effectiveLengthMeters,
                section.routeOrder.takeIf { importedRoute || reviewedIof || summary.calculatedRouteSection != null }.orEmpty(),
                section.estimatedIdealSeconds.takeIf { (importedRoute || summary.calculatedRouteSection != null) && (!reviewedIof || DesktopIofCourseAnalysis.legWarnings(info).isEmpty()) },
                section.routeMap?.copy(title = if (importedRoute) "Imported route" else if (summary.calculatedRouteSection != null) "Ideal order" else "Stored route"),
                notice = when {
                    reviewedIof -> "Showing the accepted IOF route and retained XML leg distances. " +
                        if (DesktopIofCourseAnalysis.legWarnings(info).isEmpty()) "Legs differing from straight lines by 3 m or less are treated as straight lines."
                        else "Terrain detours have unknown geometry; climb is estimated where elevations are available."
                    importedRoute -> if (section.effectiveLengthMeters == null) "Elevation data is incomplete." else null
                    summary.calculatedRouteSection == null -> "Showing the stored route; an ideal route could not be calculated."
                    section.effectiveLengthMeters == null -> "Elevation data is incomplete; the time estimate uses horizontal distance."
                    summary.hasMissingCalculatedRouteElevationData -> "Some route elevations are estimated between known points."
                    else -> null
                },
                legWarnings = if (reviewedIof) DesktopIofCourseAnalysis.legWarnings(info) else emptyList(),
                assumedPaceMinutesPerKm = 1000.0 / (60.0 * summary.speedModel.effectiveSpeedMetersPerSecond),
                elevationProfile = section.elevationProfile.takeIf { it.isNotEmpty() }?.let {
                    DesktopCourseElevationProfileSummary("Elevation profile", it, section.elevationMarkers)
                },
                // Analyzer leg rows already collapse mandatory bends into their surrounding course-object leg.
                idealRouteLegs = section.legRows.map { leg ->
                    DesktopCourseBriefLeg(leg.fromLabel, leg.toLabel, leg.lengthMeters)
                }
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            fallback.copy(notice = "Course report unavailable: ${error.message ?: error::class.simpleName}")
        }
    }
}
