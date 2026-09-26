package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo

@Composable
internal fun ActiveCourseReports(
    project: EventProjectFile,
    courseInfos: Map<String, ProtectedCourseInfo>,
    idealOrders: Map<String, String>,
    onUnlock: () -> Unit
) {
    val reports = courseReports(project, courseInfos, idealOrders)
    val ready = reports
    if (ready == null) Text("Calculating active course reports…")
    else if (ready.isEmpty()) Text("No active courses. Activate a category for this race to include its course here.")
    else {
        Text("Ideal routes use the current control labels and Course Analyzer speed settings. Times are estimates.")
        if (ready.any { it.isLocked }) Button(onClick = onUnlock) { Text("Unlock Course Data") }
        ready.forEach { CourseBriefReportSection(it) }
    }
}

/** Shared asynchronous, cancelable report calculation for both report entry points. */
@Composable
internal fun courseReports(
    project: EventProjectFile,
    courseInfos: Map<String, ProtectedCourseInfo>,
    idealOrders: Map<String, String>,
    includeUnassigned: Boolean = false
): List<DesktopCourseBriefReport>? = key(project, courseInfos, idealOrders, includeUnassigned) {
    // Reset immediately when the course or protection state changes; never show a stale unlocked report.
    val reports by produceState<List<DesktopCourseBriefReport>?>(null) {
        value = null
        value = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            DesktopCourseBriefReports.build(project, courseInfos, idealOrders,
                elevationLookup = DesktopVenueElevationCache::elevationMeters,
                checkCancelled = { context.ensureActive() }, includeUnassigned = includeUnassigned)
        }
    }
    reports
}

@Composable
internal fun CourseBriefReportSection(report: DesktopCourseBriefReport, importedRoute: Boolean = false,
    showHeading: Boolean = true) {
    Column(Modifier.fillMaxWidth().testTag("course-report-${report.categoryId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showHeading) {
            Divider()
            Text(report.courseName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6)
        }
        Text("Horizontal length: ${DesktopCourseAnalyzer.summaryLengthText(report.horizontalLengthMeters)}")
        Text("Total climb: ${DesktopCourseAnalyzer.summaryClimbText(report.climbMeters)}")
        Text("Effective length: ${DesktopCourseAnalyzer.summaryLengthText(report.effectiveLengthMeters)}")
        Text("${if (importedRoute) "Imported order" else "Ideal order"}: ${report.idealOrder.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: "Unavailable"}")
        report.estimatedIdealSeconds?.let { seconds ->
            val pace = report.assumedPaceMinutesPerKm?.let { " (${String.format(Locale.ROOT, "%.1f", it)} min/km)" }.orEmpty()
            Text("${if (importedRoute) "Estimated time" else "Estimated ideal time"}: ${DesktopCourseAnalyzer.summaryDurationText(seconds)}$pace")
        }
        CourseBriefLegDistanceTable(report)
        report.legWarnings.forEach { Text(it, color = DesktopPalette.Warning) }
        report.notice?.let { Text(it, color = DesktopPalette.Disconnected) }
        CourseBriefReportGraphics(report)
    }
}

@Composable
private fun CourseBriefLegDistanceTable(report: DesktopCourseBriefReport) {
    if (report.idealRouteLegs.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().testTag("course-report-leg-table-${report.categoryId}"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("Ideal route legs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.subtitle2)
        Text(
            "Each distance follows the ideal route between the listed course objects, including any mandatory bends.",
            style = MaterialTheme.typography.caption
        )
        CourseBriefLegDistanceRow("Ideal-order leg", "Distance", header = true)
        Divider()
        report.idealRouteLegs.forEach { leg ->
            CourseBriefLegDistanceRow(
                "${leg.fromLabel} → ${leg.toLabel}",
                DesktopCourseAnalyzer.summaryLengthText(leg.distanceMeters)
            )
        }
    }
}

@Composable
private fun CourseBriefLegDistanceRow(
    leg: String,
    distance: String,
    header: Boolean = false
) {
    val weight = if (header) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(leg, Modifier.weight(3f), fontWeight = weight, style = MaterialTheme.typography.caption)
        Text(distance, Modifier.weight(1f), fontWeight = weight, style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun CourseBriefReportGraphics(report: DesktopCourseBriefReport) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val sideBySide = maxWidth >= 620.dp && report.routeMap != null && report.elevationProfile != null
        val width = if (sideBySide) minOf((maxWidth - 16.dp) / 2, 360.dp) else minOf(maxWidth, 420.dp)
        val map: @Composable () -> Unit = {
            report.routeMap?.let {
                // The shared map renderer places scale labels below its frame.
                Box(Modifier.width(width).padding(bottom = 24.dp).testTag("course-report-map-${report.categoryId}")) {
                    CourseAnalysisRouteMap(it, mapWidth = width, mapHeight = 200.dp)
                }
            } ?: Text("Course graphic unavailable.")
        }
        val profile: @Composable () -> Unit = {
            report.elevationProfile?.let {
                CourseAnalysisElevationProfile(it.title, it.profile, it.markers,
                    Modifier.width(width).testTag("course-report-profile-${report.categoryId}"),
                    chartHeight = 200.dp, markerMaxLines = Int.MAX_VALUE)
            } ?: Text("Elevation profile unavailable: route elevation data is incomplete.")
        }
        if (sideBySide) Row(Modifier.testTag("course-report-graphics-row-${report.categoryId}"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) { map(); profile() }
        else Column(Modifier.testTag("course-report-graphics-column-${report.categoryId}"),
            verticalArrangement = Arrangement.spacedBy(8.dp)) { map(); profile() }
    }
}
