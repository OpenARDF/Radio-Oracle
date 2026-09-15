package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.openardf.radiooracle.shared.event.*

@Composable
internal fun DesktopCoursesPanel(project: EventProjectFile, isUnlocked: Boolean,
    onUnlock: (String) -> Boolean, onEdit: (DesktopCourseLibraryEdit) -> String?) {
    var password by remember(project.raceData.race.id) { mutableStateOf("") }
    var selectedId by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var deletingId by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    var message by remember(project.raceData.race.id) { mutableStateOf<String?>(null) }
    val unassigned = project.raceData.courseMappings.sortedWith(EventCategorySort.byDisplayName)
    val locked = project.hasEncryptedCategoryData() && !isUnlocked
    val restriction = DesktopCourseLibrary.disabledReason(project)
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
                }
            }
        }
        Text("Category courses (${project.raceData.categories.size})", style = MaterialTheme.typography.subtitle1)
        if (project.raceData.categories.isEmpty()) Text("No categories yet. Assign a course above or add a category in Setup → Categories.")
        project.raceData.categories.sortedWith(EventCategorySort.byDisplayName).forEach { data ->
            Text("${data.category.name} — ${data.controlPoints.size} assigned controls")
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
            project.raceData.categories.sortedWith(EventCategorySort.byDisplayName).forEach { data ->
                Row {
                    Checkbox(checked = data.category.id in selected, onCheckedChange = { checked ->
                        selected = if (checked) selected + data.category.id else selected - data.category.id
                    })
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
