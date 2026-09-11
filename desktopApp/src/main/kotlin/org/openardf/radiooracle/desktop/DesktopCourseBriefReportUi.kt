package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val reports by produceState<List<DesktopCourseBriefReport>?>(null, project, courseInfos, idealOrders) {
        value = null
        value = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            DesktopCourseBriefReports.build(project, courseInfos, idealOrders,
                elevationLookup = DesktopVenueElevationCache::elevationMeters,
                checkCancelled = { context.ensureActive() })
        }
    }
    val ready = reports
    if (ready == null) Text("Calculating active course reports…")
    else if (ready.isEmpty()) Text("No active courses. Activate a category for this race to include its course here.")
    else {
        Text("Ideal routes use the current control labels and Course Analyzer speed settings. Times are estimates.")
        if (ready.any { it.isLocked }) Button(onClick = onUnlock) { Text("Unlock Course Data") }
        ready.forEach { CourseBriefReportSection(it) }
    }
}

@Composable
internal fun CourseBriefReportSection(report: DesktopCourseBriefReport, importedRoute: Boolean = false) {
    Column(Modifier.fillMaxWidth().testTag("course-report-${report.categoryId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Divider()
        Text(report.courseName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.h6)
        Text("Horizontal length: ${DesktopCourseAnalyzer.summaryLengthText(report.horizontalLengthMeters)}")
        Text("Total climb: ${DesktopCourseAnalyzer.summaryClimbText(report.climbMeters)}")
        Text("Effective length: ${DesktopCourseAnalyzer.summaryLengthText(report.effectiveLengthMeters)}")
        Text("${if (importedRoute) "Imported order" else "Ideal order"}: ${report.idealOrder.takeIf { it.isNotEmpty() }?.joinToString(" → ") ?: "Unavailable"}")
        Text("${if (importedRoute) "Estimated time" else "Estimated ideal time"}: ${DesktopCourseAnalyzer.summaryDurationText(report.estimatedIdealSeconds)}")
        report.notice?.let { Text(it, color = DesktopPalette.Disconnected) }
        report.routeMap?.let { map ->
            // Leave room for the existing map renderer's scale labels below the map frame.
            Box(Modifier.padding(bottom = 24.dp)) {
                CourseAnalysisRouteMap(map, mapWidth = 420.dp, mapHeight = 260.dp, showWaypointLabels = true)
            }
        } ?: Text("Course graphic unavailable.")
    }
}
