package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.openardf.radiooracle.shared.event.EventProjectFile

@Composable
internal fun CourseImportReportDialog(review: DesktopCourseImportReview,
    onApply: (EventProjectFile) -> Unit, onCancel: () -> Unit) {
    var prepared by remember(review) { mutableStateOf<EventProjectFile?>(null) }
    var reports by remember(review) { mutableStateOf<List<DesktopCourseBriefReport>?>(null) }
    var elevationWarning by remember(review) { mutableStateOf<String?>(null) }
    var error by remember(review) { mutableStateOf<String?>(null) }
    LaunchedEffect(review) {
        try {
            val result = withContext(Dispatchers.Default) {
                var warning: String? = null
                val imported = if (review.fetchElevations) try {
                    DesktopCourseKmlImporter.fetchProtectedCourseElevations(
                        review.importedProject, review.categoryIds.toList(), review.password).first
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    warning = "Elevation download was unavailable. You can still apply the imported course; missing heights and time estimates are marked in the report."
                    review.importedProject
                } else review.importedProject
                val candidate = DesktopAuthoritativeCourseImport.prepare(imported, review.categoryIds, review.password)
                Triple(candidate, DesktopCourseBriefReports.imported(candidate, review.categoryIds, review.password) { ensureActive() }, warning)
            }
            prepared = result.first
            reports = result.second
            elevationWarning = result.third
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "The imported course could not be prepared."
        }
    }
    DesktopAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review imported courses", style = MaterialTheme.typography.h6) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(review.sourceName)
                Text("Apply Import applies these courses to the matched race categories. Imported numbering, locations and routes are retained. Course Analyzer is optional.")
                if (review.transaction.replacesDraft) Text("Applying this import replaces the pending course draft. Cancel keeps the current race and draft.")
                review.notes.forEach { Text(it) }
                elevationWarning?.let { Text(it) }
                if (reports == null && error == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(if (review.fetchElevations) "Preparing the report and retrieving missing elevations…" else "Preparing the course report…")
                }
                reports?.forEach { report ->
                    if (review.importedProject.raceData.categories.none { it.category.id == report.categoryId }) {
                        Text("${report.courseName}: imported course mapping; not assigned to an active race category.")
                    }
                    CourseBriefReportSection(report, importedRoute = true)
                }
                if (reports?.isEmpty() == true) Text("Control definitions will be updated. No named course was supplied.")
                error?.let { Text(it, color = MaterialTheme.colors.error) }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-course-import")) { Text("Cancel") }
        },
        confirmButton = {
            Button(enabled = prepared != null && error == null,
                modifier = Modifier.testTag("apply-course-import"), onClick = {
                    try { onApply(requireNotNull(prepared)) } catch (failure: Exception) {
                        error = failure.message ?: "The course import could not be applied."
                    }
                }) { Text("Apply Import") }
        }
    )
}
