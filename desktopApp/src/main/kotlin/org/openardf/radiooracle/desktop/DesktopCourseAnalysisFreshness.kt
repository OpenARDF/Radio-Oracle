package org.openardf.radiooracle.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.openardf.radiooracle.shared.event.EventCourseDrafts
import org.openardf.radiooracle.shared.event.EventProjectFile

/** The report, export actions and application actions must all describe the current input design. */
@Composable
internal fun currentCourseAnalysisResult(
    project: EventProjectFile?,
    completed: DesktopCourseAnalysisSummary?,
    routeSource: DesktopCourseRouteSource = DesktopCourseRouteSource.forProject(project)
): DesktopCourseAnalysisSummary? {
    val currentHash = remember(project?.raceData) {
        project?.let {
            runCatching {
                EventCourseDrafts.requireCurrent(it)
                EventCourseDrafts.snapshotHash(EventCourseDrafts.candidate(it))
            }.getOrNull()
        }
    }
    // Check on every completion too: a calculation started before an import may finish afterward.
    return completed?.takeIf { currentHash != null && it.sourceSnapshotHash == currentHash && it.routeSource == routeSource }
}
