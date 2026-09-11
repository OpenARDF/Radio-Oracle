package org.openardf.radiooracle.desktop

import kotlinx.coroutines.CancellationException
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

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
    val isLocked: Boolean = false
)

/** A read-only summary of active courses, independent of the CSV's control-set grouping. */
internal object DesktopCourseBriefReports {
    fun imported(project: EventProjectFile, categoryIds: Set<String>, password: String?, checkCancelled: () -> Unit = {}): List<DesktopCourseBriefReport> =
        (project.raceData.categories + project.raceData.courseMappings).filter { it.category.id in categoryIds }.map { data ->
            checkCancelled()
            report(project, data, data.category.storedCourseInfo(password), data.category.storedIdealOrder(password),
                DesktopVenueElevationCache::elevationMeters, importedRoute = true)
        }

    fun build(
        project: EventProjectFile,
        courseInfos: Map<String, ProtectedCourseInfo>,
        idealOrders: Map<String, String> = emptyMap(),
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        checkCancelled: () -> Unit = {}
    ): List<DesktopCourseBriefReport> = project.raceData.categories.sortedBy { it.category.order }.map { data ->
        checkCancelled()
        val category = data.category
        val info = courseInfos[category.id] ?: category.courseInfo.takeIf { category.encryptedCourseInfo == null }
        val order = idealOrders[category.id] ?: category.idealOrder.takeIf { category.encryptedIdealOrder == null }
        report(project, data, info, order, elevationLookup)
    }

    private fun report(
        project: EventProjectFile, data: EventCategoryData, info: ProtectedCourseInfo?, order: String?,
        elevationLookup: (CourseGeoPoint) -> Double?, importedRoute: Boolean = false
    ): DesktopCourseBriefReport {
        val category = data.category
        val fallback = DesktopCourseBriefReport(
            category.id, category.name,
            horizontalLengthMeters = category.lengthMeters.takeIf { it > 0 },
            climbMeters = category.climbMeters.takeIf { category.lengthMeters > 0 || it > 0 }
        )
        if (info == null) return fallback.copy(
            isLocked = category.encryptedCourseInfo != null,
            notice = if (category.encryptedCourseInfo != null) "Unlock course data to calculate this report."
                else if (importedRoute) "This import supplies assignments and course facts without geographic route data."
                else "Import course locations and route data to calculate the ideal order and graphic."
        )
        return try {
            val summary = DesktopCourseAnalyzer.analyze(
                project, category.id, info, order,
                elevationLookup = elevationLookup,
                controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS,
                allowFoxRenumbering = false
            )
            // When both routes match, the analyzer's calculated section intentionally contains only a note.
            val section = if (importedRoute) summary.providedRouteSection else summary.calculatedRouteSection?.takeUnless { it.summaryOnly }
                ?: summary.providedRouteSection
            if (section == null) fallback.copy(notice = "The import does not contain enough geographic data to draw a route.".takeIf { importedRoute }
                ?: "Course geometry is incomplete. Review this course in Course Analyzer.")
            else DesktopCourseBriefReport(
                category.id, category.name, section.routeLengthMeters, section.climbMeters,
                section.effectiveLengthMeters,
                section.routeOrder.takeIf { importedRoute || summary.calculatedRouteSection != null }.orEmpty(),
                section.estimatedIdealSeconds.takeIf { importedRoute || summary.calculatedRouteSection != null },
                section.routeMap?.copy(title = if (importedRoute) "Imported route" else if (summary.calculatedRouteSection != null) "Ideal order" else "Stored route"),
                notice = when {
                    importedRoute -> if (section.effectiveLengthMeters == null) "Elevation data is incomplete." else null
                    summary.calculatedRouteSection == null -> "Showing the stored route; an ideal route could not be calculated."
                    section.effectiveLengthMeters == null -> "Elevation data is incomplete; the time estimate uses horizontal distance."
                    summary.hasMissingCalculatedRouteElevationData -> "Some route elevations are estimated between known points."
                    else -> null
                }
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            fallback.copy(notice = "Course report unavailable: ${error.message ?: error::class.simpleName}")
        }
    }
}
