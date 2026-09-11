package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.*
import org.openardf.radiooracle.shared.event.*

internal val LocalCourseDesign = staticCompositionLocalOf<DesktopCourseDesignUi?> { null }

internal class DesktopCourseDesignUi {
    var analysisSource by mutableStateOf(DesktopCourseRouteSource.Applied)
    var project by mutableStateOf<EventProjectFile?>(null)
    var courseState by mutableStateOf<DesktopProtectedCourseState?>(null)
    var error by mutableStateOf<String?>(null)
    var pendingApplication by mutableStateOf<DesktopCourseCalculatedRouteApplication?>(null)
    var cancelDraft: () -> Unit = {}
    fun routeSource(applied: EventProjectFile?) = if (applied?.raceData?.courseDraft != null) analysisSource else DesktopCourseRouteSource.Applied
    fun analysisProject(applied: EventProjectFile) = if (routeSource(applied) == DesktopCourseRouteSource.Draft)
        EventCourseDrafts.candidate(applied) else EventCourseDrafts.cancel(applied)
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
        DesktopCourseApplyFlow(project, ui.project, ui.courseState, ui.error, application, password,
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
                onChanged(updated, "Applied course changes to all ${prepared.changes.map { it.categoryName }.distinct().size} courses in this race. Save Race to write it to disk.")
            })
    }
}

@Composable
private fun DesktopCourseApplyFlow(
    applied: EventProjectFile, candidate: EventProjectFile?, state: DesktopProtectedCourseState?, loadError: String?,
    application: DesktopCourseCalculatedRouteApplication, password: String?,
    onDismiss: () -> Unit, onCopy: () -> Unit, onApply: (DesktopPreparedCourseDesign) -> Unit
) {
    val choices = remember(candidate, state) { runCatching {
        courseStationChoices(candidate, state?.protectedCourseInfoByCategoryId.orEmpty())
    } }
    val rows = choices.getOrDefault(emptyList())
    var bindings by remember(candidate, state) { mutableStateOf(rows.associate { it.key to it.controlId.orEmpty() }) }
    val unresolved = remember(candidate, state) { rows.filter { it.controlId == null }.map { it.key }.toSet() }
    var error by remember(candidate, application) { mutableStateOf<String?>(null) }
    var notice by remember(candidate, application) { mutableStateOf<String?>(null) }
    var busy by remember(candidate, application) { mutableStateOf(false) }
    var editBindings by remember(candidate, application) { mutableStateOf(false) }
    var attempt by remember(candidate, application) { mutableStateOf(0) }
    val recorded = EventCourseDrafts.hasRecordedActivity(applied.raceData)
    val ready = candidate != null && state != null && loadError == null && choices.isSuccess && !recorded &&
        bindings.isNotEmpty() && bindings.values.none(String::isBlank)

    // The Apply action is authorization. Known bindings require no further review or confirmation.
    // Cancel/disposal cancels preparation; a late completion cannot commit after cancellation.
    LaunchedEffect(candidate, state, application, attempt) {
        if (!ready) return@LaunchedEffect
        busy = true
        error = null
        try {
            val prepared = withContext(Dispatchers.Default) {
                val byCategory = bindings.entries.groupBy { it.key.first }
                    .mapValues { (_, entries) -> entries.associate { it.key.second to it.value } }
                DesktopCourseAnalysisApplier.prepareAll(applied,
                    DesktopCourseRouteSelection(state!!.protectedCourseInfoByCategoryId.getValue(application.categoryId),
                        application, byCategory.getValue(application.categoryId)), byCategory, password,
                    elevationLookup = DesktopVenueElevationCache::elevationMeters, checkCancelled = { ensureActive() })
            }
            ensureActive()
            onApply(prepared)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "Course changes could not be applied. Analyze the current draft again."
        } finally { busy = false }
    }
    val problem = loadError ?: choices.exceptionOrNull()?.message ?: error ?:
        "No course stations are available. Import the complete course and analyze it again."
            .takeIf { candidate != null && state != null && rows.isEmpty() }
    val visibleRows = rows.filter { editBindings || it.key in unresolved }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.width(720.dp).heightIn(max = if (!recorded && !busy && visibleRows.isNotEmpty()) 720.dp else 300.dp)
            .fillMaxHeight(0.9f).testTag("course-apply-flow"),
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    recorded -> "Course changes blocked by readouts"
                    busy -> "Applying course changes"
                    visibleRows.isNotEmpty() -> "Assign stations to course locations"
                    problem != null -> "Course changes could not be applied"
                    else -> "Checking course changes"
                }, style = MaterialTheme.typography.h6, modifier = Modifier.testTag("course-review-title"))
                DesktopWorkspaceScroll(Modifier.weight(1f).fillMaxWidth()) {
                    if (recorded) {
                        Text("This race contains SI-card readouts. Changing its courses would change the meaning of those results. Create a revised race copy without readouts, then open that copy to apply your draft.")
                        TextButton(onClick = { runCatching(onCopy).onFailure { error = it.message } }) { Text("Create revised race copy…") }
                    } else if (busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Validating stations, routes and distances for all race courses. Your accepted numbering is retained.")
                    } else if (candidate == null || state == null) {
                        Text(if (loadError == null) "Loading the course draft…" else "Unlock or reload the current course draft to continue.")
                    }
                    problem?.let { Text(it, color = MaterialTheme.colors.error) }
                    notice?.let { Text(it) }
                    if (!recorded && !busy && candidate != null && state != null) {
                        if (visibleRows.isNotEmpty()) {
                            Text("Choose the physical SI station for each listed fox. Use the diagram below or export these labeled locations to KML for a map viewer.")
                            if (candidate.hasEncryptedCategoryData()) Text("The exported KML contains unencrypted course locations.")
                            TextButton(onClick = {
                                runCatching {
                                    val path = DesktopFileDialogs.chooseExportCreateCourseKml("Course station locations.kml")
                                    if (path != null) {
                                        DesktopCourseAnalysisExports.exportKmlFolders(path,
                                            courseStationPreviewFolders(candidate, state.protectedCourseInfoByCategoryId, bindings, application))
                                        notice = "Exported course locations to ${path.fileName}."
                                    }
                                }.onFailure { notice = "Location export failed: ${it.message}" }
                            }) { Text("Export locations to KML…") }
                            visibleRows.groupBy { it.categoryId }.forEach { (categoryId, categoryRows) ->
                                categoryRows.forEach { row ->
                                    CourseStationPicker("${row.categoryName} — ${row.label}", bindings[row.key].orEmpty(),
                                        candidate.raceData.controls.filter { it.type == row.role }, true) { id ->
                                        bindings = bindings + (row.key to id)
                                    }
                                }
                                val preview = courseStationPreviewFolders(candidate,
                                    state.protectedCourseInfoByCategoryId.filterKeys { it == categoryId }, bindings, application).single()
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val map = remember(preview) { runCatching { courseStationPreviewMap(preview) }.getOrNull() }
                                    if (map != null) CourseAnalysisRouteMap(map, mapWidth = maxWidth,
                                        mapHeight = 220.dp, showWaypointLabels = false)
                                    else Text("Use the KML export to view this location on a map.")
                                }

                            }
                        }
                        if (problem != null && rows.isNotEmpty() && !editBindings) {
                            TextButton(onClick = { editBindings = true }) { Text("Edit station assignments") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().testTag("course-review-actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!recorded && !busy && (visibleRows.isNotEmpty() || problem != null)) {
                        Button(onClick = { attempt++ }, enabled = ready, modifier = Modifier.testTag("course-apply-all")) {
                            Text("Apply changes to all race courses")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseStationPicker(label: String, selectedId: String, controls: List<EventControl>, enabled: Boolean, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.testTag("course-station-picker")) {
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
