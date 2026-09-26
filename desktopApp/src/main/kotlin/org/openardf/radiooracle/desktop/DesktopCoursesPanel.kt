package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openardf.radiooracle.shared.event.*

@Composable
internal fun DesktopCategoryCourseAssignment(project: EventProjectFile, isUnlocked: Boolean,
    onUnlock: (String) -> Boolean, onEdit: (DesktopCourseLibraryEdit) -> String?) {
    var choosing by remember(project.raceData.race.id) { mutableStateOf(false) }
    var sourceId by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var message by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var password by remember(project.raceData.race.id) { mutableStateOf("") }
    val sources = DesktopCourseLibrary.assignmentSources(project)
    val locked = project.hasEncryptedCategoryData() && !isUnlocked
    val restriction = DesktopCourseLibrary.disabledReason(project)
        ?: if (locked) "Unlock course data to assign an existing course." else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Assign an existing course to categories here. Course Analyzer is optional.")
        if (locked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text("Race Password") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            TextButton(onClick = { if (onUnlock(password)) password = "" else message = "Unable to unlock course data. Check the Race Password." }) { Text("Unlock course data") }
        }
        DisabledReasonTooltip(restriction ?: if (sources.isEmpty()) "Import or assign controls to a course first." else null) {
            Button(onClick = { choosing = true }, enabled = restriction == null && sources.isNotEmpty(),
                modifier = Modifier.testTag("categories-assign-existing-course")) { Text("Assign Existing Course…") }
        }
        message?.let { Text(it) }
    }
    if (choosing) DesktopAlertDialog(onDismissRequest = { choosing = false }, title = { Text("Choose an existing course") },
        text = { Column {
            sources.forEach { source ->
                TextButton(onClick = { sourceId = source.category.id; choosing = false },
                    modifier = Modifier.testTag("existing-course-${source.category.id}")) {
                    val locations = if (source.category.courseInfo == null && source.category.encryptedCourseInfo.isNullOrBlank()) " (no course locations)" else ""
                    Text("${source.category.name} — ${source.category.controlPointsString.ifBlank { "${source.controlPoints.size} assigned controls" }}$locations")
                }
            }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } })
    sources.singleOrNull { it.category.id == sourceId }?.let { source ->
        CourseAssignmentDialog(project, source, restriction, onCancel = { sourceId = null }, onAssign = { edit ->
            onEdit(edit)?.also { message = it } ?: run {
                sourceId = null
                message = "Course assigned. Save Race to keep these changes."
                null
            }
        })
    }
}

@Composable
internal fun DesktopCoursesPanel(project: EventProjectFile, isUnlocked: Boolean,
    onUnlock: (String) -> Boolean, onEdit: (DesktopCourseLibraryEdit) -> String?,
    courseInfos: Map<String, ProtectedCourseInfo> = emptyMap(),
    idealOrders: Map<String, String> = emptyMap()) {
    var password by remember(project.raceData.race.id) { mutableStateOf("") }
    var selectedId by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var deletingId by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var message by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    val reports = courseReports(project, courseInfos, idealOrders, includeUnassigned = true)
    val reportsById = reports?.associateBy { it.categoryId }.orEmpty()
    val unassigned = project.raceData.courseMappings.sortedWith(EventCategorySort.byDisplayName)
    val locked = project.hasEncryptedCategoryData() && !isUnlocked
    val restriction = DesktopCourseLibrary.disabledReason(project)
    // Export the same completed report snapshot rendered below; never recalculate while saving.
    val reportExportDisabledReason = when {
        reports == null -> "Course reports are still being calculated."
        reports.isEmpty() -> "No course reports are available."
        reports.any { it.isLocked } -> "Unlock course data before exporting the course reports."
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Courses", style = MaterialTheme.typography.h6)
        Text("Every imported course is listed here. Assign an unassigned course to one or more categories, create a category for it, or delete it. Use Controls to manage the shared control list.")
        message?.let { Text(it) }
        restriction?.let { Text(it, color = MaterialTheme.colors.error) }
        if (locked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text("Race Password") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            TextButton(onClick = { if (onUnlock(password)) password = "" else message = "Unable to unlock course data. Check the Race Password." }) { Text("Unlock course data") }
        }
        Text("Reports use the current control labels and Course Analyzer speed settings. Times are estimates.")
        DisabledReasonTooltip(reportExportDisabledReason) {
            Button(
                enabled = reportExportDisabledReason == null,
                modifier = Modifier.testTag("export-course-report-pdf"),
                onClick = {
                    val readyReports = reports ?: return@Button
                    DesktopFileDialogs.chooseExportCourseReportPdf(
                        DesktopCourseReportPdf.defaultFileName(project)
                    )?.let { path ->
                        runCatching { DesktopCourseReportPdf.exportPdf(path, project, readyReports) }
                            .onSuccess { message = "Exported ${path.fileName} with ${readyReports.size} course reports." }
                            .onFailure { error ->
                                message = "Course Report PDF export failed: ${error.message ?: error::class.simpleName}"
                            }
                    }
                }
            ) { Text("Export Course Report PDF...") }
        }
        Text("Unassigned courses (${unassigned.size})", style = MaterialTheme.typography.subtitle1)
        if (unassigned.isEmpty()) Text("No unassigned courses.")
        unassigned.forEach { course ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(course.category.name, style = MaterialTheme.typography.h6)
                    Text("Not assigned to a category")
                    Text("${course.controlPoints.size} assigned controls")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DisabledReasonTooltip(restriction ?: if (locked) "Unlock course data to assign this course." else null) {
                            Button(enabled = restriction == null && !locked, onClick = { selectedId = course.category.id },
                                modifier = Modifier.testTag("assign-course-${course.category.id}")) { Text("Assign to categories…") }
                        }
                        DisabledReasonTooltip(restriction) {
                            TextButton(enabled = restriction == null, onClick = { deletingId = course.category.id },
                                modifier = Modifier.testTag("delete-course-${course.category.id}")) { Text("Delete course…") }
                        }
                    }
                    reportsById[course.category.id]?.let { CourseBriefReportSection(it, showHeading = false) }
                    if (reports == null) Text("Calculating course report…")
                }
            }
        }
        Text("Category courses (${project.raceData.categories.size})", style = MaterialTheme.typography.subtitle1)
        if (project.raceData.categories.isEmpty()) Text("No categories yet. Assign a course above or add a category in Setup → Categories.")
        project.raceData.categories.sortedWith(EventCategorySort.byDisplayName).forEach { data ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(data.category.name, style = MaterialTheme.typography.h6)
                    Text("${data.controlPoints.size} assigned controls")
                    reportsById[data.category.id]?.let { CourseBriefReportSection(it, showHeading = false) }
                    if (reports == null) Text("Calculating course report…")
                }
            }
        }
    }
    unassigned.singleOrNull { it.category.id == selectedId }?.let { source ->
        CourseAssignmentDialog(project, source, restriction ?: if (locked) "Unlock course data before assigning this course." else null,
            onCancel = { selectedId = null }, onAssign = { edit ->
                onEdit(edit)?.also { message = it } ?: run { selectedId = null; message = "Course assigned. Save Race to keep these changes."; null }
            })
    }
    unassigned.singleOrNull { it.category.id == deletingId }?.let { source ->
        DesktopAlertDialog(onDismissRequest = { deletingId = null }, title = { Text("Delete ${source.category.name}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This removes the unassigned course from this race. Categories and shared controls are kept.")
                restriction?.let { Text(it, color = MaterialTheme.colors.error) }
            } },
            confirmButton = { Button(enabled = restriction == null, onClick = {
                message = onEdit(DesktopCourseLibraryEdit.Delete(source.category.id)) ?: "Course deleted. Save Race to keep these changes."
                deletingId = null
            }) { Text("Delete course") } },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } })
    }
}

@Composable
private fun CourseAssignmentDialog(project: EventProjectFile, source: EventCategoryData, restriction: String?,
    onCancel: () -> Unit, onAssign: (DesktopCourseLibraryEdit.Assign) -> String?) {
    var selected by remember(source.category.id) { mutableStateOf(emptySet<String>()) }
    var newName by remember(source.category.id) { mutableStateOf(if (project.raceData.categories.isEmpty()) source.category.name else "") }
    var error by remember(source.category.id) { mutableStateOf<String?>(null) }
    DesktopAlertDialog(onDismissRequest = onCancel, title = { Text("Assign ${source.category.name}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose existing categories and/or enter a new category name. Their assigned controls and course data will be replaced by this course. Competitors stay in their categories.")
            project.raceData.categories.filterNot { it.category.id == source.category.id }.sortedWith(EventCategorySort.byDisplayName).forEach { data ->
                Row {
                    Checkbox(checked = data.category.id in selected, onCheckedChange = { checked ->
                        selected = if (checked) selected + data.category.id else selected - data.category.id
                    }, modifier = Modifier.testTag("course-target-${data.category.id}"))
                    Text(data.category.name)
                }
            }
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New category name (optional)") }, singleLine = true)
            (restriction ?: error)?.let { Text(it, color = MaterialTheme.colors.error) }
        }
    }, confirmButton = {
        Button(enabled = restriction == null && (selected.isNotEmpty() || newName.isNotBlank()), onClick = {
            error = onAssign(DesktopCourseLibraryEdit.Assign(source.category.id, selected, newName))
        }) { Text("Assign course") }
    }, dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
