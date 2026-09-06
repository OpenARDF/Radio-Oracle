package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.openardf.radiooracle.shared.event.*

internal val LocalCourseDesign = staticCompositionLocalOf<DesktopCourseDesignUi?> { null }

internal class DesktopCourseDesignUi {
    var project by mutableStateOf<EventProjectFile?>(null)
    var courseState by mutableStateOf<DesktopProtectedCourseState?>(null)
    var error by mutableStateOf<String?>(null)
    var pendingApplication by mutableStateOf<DesktopCourseCalculatedRouteApplication?>(null)
    var cancelDraft: () -> Unit = {}
}

/** Separate loaded candidate state: results keep the session's applied race and its unlocked cache. */
@Composable
internal fun DesktopCourseDesignHost(
    project: EventProjectFile?, password: String?, session: DesktopProjectSession,
    ui: DesktopCourseDesignUi, onChanged: (EventProjectFile, String) -> Unit, content: @Composable () -> Unit
) {
    LaunchedEffect(project, password) {
        ui.project = null
        ui.courseState = null
        ui.error = null
        if (project != null) {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    EventCourseDrafts.requireCurrent(project)
                    val candidate = EventCourseDrafts.candidate(project)
                    candidate to decryptedProtectedCourseState(candidate, password.orEmpty())
                }
                ui.project = loaded.first
                ui.courseState = loaded.second
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ui.error = error.message
            }
        }
    }
    ui.cancelDraft = {
        val updated = session.updateCurrentProject(EventCourseDrafts::cancel)
        ui.pendingApplication = null
        onChanged(updated, "Course draft discarded. The applied courses are unchanged. Save Race to store this change.")
    }
    CompositionLocalProvider(LocalCourseDesign provides ui, content = content)
    val application = ui.pendingApplication
    if (project != null && application != null) {
        DesktopCourseApplyReview(project, ui.project, ui.courseState, ui.error, application, password,
            onDismiss = { ui.pendingApplication = null },
            onCopy = {
                val name = "${project.raceData.race.name} revised"
                val path = DesktopFileDialogs.chooseSaveProject(name)
                if (path != null) {
                    require(path.toAbsolutePath().normalize() != session.currentPath?.toAbsolutePath()?.normalize()) { "Choose a different file for the revised race." }
                    val copy = EventProjectFactory.copyForCourseRedesign(project, java.util.UUID.randomUUID().toString(), name, project.raceData.race.startDateTimeIso)
                    DesktopProjectFiles.write(path, copy)
                    onChanged(project, "Exported a revised race without readouts to ${path.fileName}. Open that copy to continue design. The current race remains open.")
                }
            },
            onApply = { prepared ->
                val updated = session.updateCurrentProject { DesktopCourseAnalysisApplier.commit(it, prepared) }
                ui.pendingApplication = null
                onChanged(updated, "Applied the reviewed design to all ${prepared.changes.map { it.categoryName }.distinct().size} courses in this race. Save Race to write it to disk.")
            })
    }
}

@Composable
private fun DesktopCourseApplyReview(
    applied: EventProjectFile, candidate: EventProjectFile?, state: DesktopProtectedCourseState?, loadError: String?,
    application: DesktopCourseCalculatedRouteApplication, password: String?,
    onDismiss: () -> Unit, onCopy: () -> Unit, onApply: (DesktopPreparedCourseDesign) -> Unit
) {
    val scope = rememberCoroutineScope()
    var prepared by remember(candidate, application) { mutableStateOf<DesktopPreparedCourseDesign?>(null) }
    var error by remember(candidate, application) { mutableStateOf<String?>(null) }
    var busy by remember(candidate, application) { mutableStateOf(false) }
    var job by remember(candidate, application) { mutableStateOf<Job?>(null) }
    DisposableEffect(candidate, application) { onDispose { job?.cancel() } }
    val rows = remember(candidate, state) { runCatching {
        state?.protectedCourseInfoByCategoryId.orEmpty().flatMap { (categoryId, info) ->
            info.validatedPlacements().values.mapNotNull { point ->
                point.type.controlRole()?.let { role -> CourseBindingReviewRow(categoryId, point.id, point.label, role, point.latitude, point.longitude) }
            }
        }
    } }
    var bindings by remember(candidate, state) { mutableStateOf(rows.getOrDefault(emptyList()).associate { row ->
        val info = state!!.protectedCourseInfoByCategoryId.getValue(row.categoryId)
        val explicit = info.appliedBindings?.controls?.singleOrNull { it.placementId == row.placementId }?.controlId
        val exact = candidate?.raceData?.controls?.singleOrNull { it.id == row.placementId && it.type == row.role }?.id
        (row.categoryId to row.placementId) to (explicit ?: exact).orEmpty()
    }) }
    fun dismiss() { job?.cancel(); onDismiss() }
    AlertDialog(onDismissRequest = ::dismiss, modifier = Modifier.width(720.dp).testTag("course-apply-review"),
        title = { Text("Review and apply all race courses") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scope: this race, including inactive course mappings. Confirm the physical SI station for each placement. Accepted fox numbering will update Controls and every course together.")
                if (EventCourseDrafts.hasRecordedActivity(applied.raceData)) {
                    Text("This race has recorded activity. Export a new race copy without readouts, then open the copy to continue design.")
                    TextButton(onClick = { runCatching(onCopy).onFailure { error = it.message } }) { Text("Export revised race copy…") }
                }
                (loadError ?: rows.exceptionOrNull()?.message ?: error)?.let { Text(it, color = MaterialTheme.colors.error) }
                if (candidate == null || state == null) Text("Unlock or reload the current course draft to continue.")
                if (prepared == null) {
                    rows.getOrDefault(emptyList()).forEach { row ->
                        val categoryName = (candidate?.raceData?.categories.orEmpty() + candidate?.raceData?.courseMappings.orEmpty())
                            .singleOrNull { it.category.id == row.categoryId }?.category?.name.orEmpty()
                        CourseStationPicker("$categoryName: ${row.label} at ${row.latitude}, ${row.longitude}", bindings[row.categoryId to row.placementId].orEmpty(),
                            candidate?.raceData?.controls.orEmpty().filter { it.type == row.role }, !busy) { id ->
                            bindings = bindings + ((row.categoryId to row.placementId) to id)
                        }
                    }
                } else {
                    Text("Prepared changes: labels and SI stations below, complete routes, assignments, and metrics for every listed course.")
                    prepared!!.changes.groupBy { it.categoryName }.forEach { (category, changes) ->
                        Text("$category: ${changes.joinToString { "${it.label} (SI ${it.siCode})" }}")
                    }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Calculating and validating every course…") }
            }
        },
        confirmButton = {
            Button(enabled = !busy && candidate != null && state != null && loadError == null && rows.isSuccess &&
                bindings.isNotEmpty() && bindings.values.none(String::isBlank) && !EventCourseDrafts.hasRecordedActivity(applied.raceData),
                modifier = Modifier.testTag(if (prepared == null) "course-prepare-all" else "course-apply-all"), onClick = {
                    val ready = prepared
                    if (ready != null) {
                        runCatching { onApply(ready) }.onFailure { error = it.message; prepared = null }
                    } else {
                        busy = true
                        job = scope.launch {
                            try {
                                prepared = withContext(Dispatchers.Default) {
                                    val byCategory = bindings.entries.groupBy { it.key.first }.mapValues { (_, entries) -> entries.associate { it.key.second to it.value } }
                                    val info = state!!.protectedCourseInfoByCategoryId.getValue(application.categoryId)
                                    DesktopCourseAnalysisApplier.prepareAll(applied, DesktopCourseRouteSelection(info, application,
                                        byCategory.getValue(application.categoryId)), byCategory, password,
                                        elevationLookup = { DesktopVenueElevationCache.elevationMeters(it) }, checkCancelled = { ensureActive() })
                                }
                            } catch (failure: Exception) {
                                if (failure is CancellationException) throw failure
                                error = failure.message
                            } finally { busy = false }
                        }
                    }
                }) { Text(if (prepared == null) "Prepare all courses" else "Apply reviewed courses") }
        }, dismissButton = { TextButton(onClick = ::dismiss) { Text("Cancel") } })
}

private data class CourseBindingReviewRow(val categoryId: String, val placementId: String, val label: String,
                                         val role: org.openardf.radiooracle.shared.domain.ControlPointType, val latitude: Double, val longitude: Double)

@Composable
private fun CourseStationPicker(label: String, selectedId: String, controls: List<EventControl>, enabled: Boolean, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(controls.singleOrNull { it.id == selectedId }?.let { "${it.publicLabel ?: it.label} — SI ${it.siCode}" } ?: "Choose SI station")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                controls.forEach { control -> DropdownMenuItem(onClick = { onSelected(control.id); expanded = false }) {
                    Text("${control.publicLabel ?: control.label} — SI ${control.siCode}")
                } }
            }
        }
    }
}
