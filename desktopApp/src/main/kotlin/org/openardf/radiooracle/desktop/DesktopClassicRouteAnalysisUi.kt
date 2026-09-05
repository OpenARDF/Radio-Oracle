package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

/** Merge only still-current inputs; corrections and course changes never inherit old estimates. */
internal fun mergeClassicRouteAnalysis(
    current: EventProjectFile,
    captured: EventProjectFile,
    calculated: StoredClassicRouteAnalysis
): EventProjectFile {
    if (current.raceData.race.id != captured.raceData.race.id ||
        DesktopClassicRouteAnalysis.courseFingerprint(current) != DesktopClassicRouteAnalysis.courseFingerprint(captured)) return current
    val inputs = current.raceData.competitorData.mapNotNull { data ->
        data.readoutData?.result?.id?.let { it to DesktopClassicRouteAnalysis.inputFingerprint(data) }
    }.toMap()
    val accepted = calculated.results.filter { (id, snapshot) -> inputs[id] == snapshot.inputFingerprint }
    val results = current.desktopRouteAnalysis?.results.orEmpty().filterKeys { it in inputs } + accepted
    val contexts = current.desktopRouteAnalysis?.contexts.orEmpty() + calculated.contexts
    return current.copy(desktopRouteAnalysis = StoredClassicRouteAnalysis(
        contexts = contexts.filterKeys { key -> results.values.any { it.contextId == key } }, results = results
    ))
}

internal val LocalClassicRouteAnalysis = staticCompositionLocalOf<ClassicRouteAnalysisUi?> { null }

internal class ClassicRouteAnalysisUi(
    private val scope: CoroutineScope,
    private val loadSurface: ((() -> Unit) -> DesktopFrozenElevationSurface)? = null,
    private val recovery: DesktopClassicRouteRecovery = DesktopClassicRouteRecovery()
) {
    var status by mutableStateOf("Not started. Estimates use straight-line navigation and cached terrain.")
    var running by mutableStateOf(false)
    var continuous by mutableStateOf(false)
    var showDialog by mutableStateOf(false)
    var notification by mutableStateOf<String?>(null)
    var exportChoice by mutableStateOf(false)
    var exportSnapshot: EventProjectFile? = null
        private set
    private data class WaitingExport(val captured: EventProjectFile, val path: Path?, val run: () -> Unit)
    private var waitingExport: WaitingExport? = null

    fun shouldWaitForExport(project: EventProjectFile?): Boolean = running &&
        project?.raceData?.race?.raceType == RaceType.CLASSIC && project.raceData.race.id in ownedRaceIds

    fun requestExport(project: EventProjectFile, run: () -> Unit) {
        waitingExport = WaitingExport(project, session?.currentPath, run)
        exportChoice = true
    }

    fun cancelExport() { waitingExport = null; exportChoice = false }

    fun exportAvailable() {
        val request = waitingExport ?: return
        cancelExport()
        exportSnapshot = request.captured
        try { request.run() } finally { exportSnapshot = null }
    }

    fun waitForExport() {
        exportChoice = false
        status = "Waiting for the results present at the export request; later downloads do not extend this wait."
        completeWaitingExport()
    }

    private fun completeWaitingExport() {
        val request = waitingExport?.takeUnless { exportChoice } ?: return
        val current = session?.currentProject
        val captured = request.captured
        val inputs = captured.raceData.competitorData.filter { it.readoutData != null }
        val currentById = current?.raceData?.competitorData.orEmpty().associateBy { it.readoutData?.result?.id }
        if (current == null || session?.currentPath != request.path ||
            DesktopClassicRouteAnalysis.courseFingerprint(current) != DesktopClassicRouteAnalysis.courseFingerprint(captured) ||
            inputs.any { old -> currentById[old.readoutData?.result?.id]?.let {
                DesktopClassicRouteAnalysis.inputFingerprint(it) == DesktopClassicRouteAnalysis.inputFingerprint(old)
            } != true }) {
            cancelExport()
            notification = "Export needs refreshing: its race, course, or captured results changed. Request the export again."
            return
        }
        val ready = DesktopClassicRouteAnalysis.projection(current).keys
        val allComplete = inputs.all { data ->
            val id = requireNotNull(data.readoutData).result.id
            id in ready || current.desktopRouteAnalysis?.results?.get(id)?.let { snapshot ->
                snapshot.unavailableReason != null && snapshot.inputFingerprint == DesktopClassicRouteAnalysis.inputFingerprint(data) &&
                    current.desktopRouteAnalysis?.contexts?.get(snapshot.contextId)?.courseFingerprint == DesktopClassicRouteAnalysis.courseFingerprint(current)
            } == true
        }
        if (!allComplete) return
        waitingExport = null
        exportSnapshot = captured.copy(desktopRouteAnalysis = current.desktopRouteAnalysis)
        try { request.run() } finally { exportSnapshot = null }
    }
    private var job: Job? = null
    private var generation = 0L
    private var password: String? = null
    private var surface: DesktopFrozenElevationSurface? = null
    private var calculationCache = DesktopClassicRouteAnalysis.CalculationCache()
    private var ownedPaths = emptySet<Path?>()
    private var ownedRaceIds = emptySet<String>()
    private val pending = linkedMapOf<String, Pair<Path?, EventProjectFile>>()
    private val attempted = mutableMapOf<String, String>()
    var session: DesktopProjectSession? = null
    var changed: (EventProjectFile) -> Unit = {}
    internal suspend fun awaitIdle() { job?.join() }

    private fun key(project: EventProjectFile) = DesktopClassicRouteAnalysis.courseFingerprint(project) +
        project.raceData.competitorData.mapNotNull { data -> data.readoutData?.result?.id?.let {
            it + DesktopClassicRouteAnalysis.inputFingerprint(data)
        } }.sorted().joinToString("|")

    fun observe(project: EventProjectFile?, path: Path?) {
        completeWaitingExport()
        if ((running || continuous) && (project == null || path !in ownedPaths || project.raceData.race.id !in ownedRaceIds)) {
            cancel()
            return
        }
        if (continuous && project?.raceData?.race?.raceType == RaceType.CLASSIC &&
            attempted[project.raceData.race.id] != key(project)) enqueue(path, project)
    }

    fun start(project: EventProjectFile, path: Path?, racePassword: String, watch: Boolean, series: Boolean) {
        if (running) return
        password = racePassword.takeIf { it.isNotBlank() }
        continuous = watch
        surface = null
        calculationCache = DesktopClassicRouteAnalysis.CalculationCache()
        attempted.clear()
        pending.clear()
        ownedPaths = setOf(path)
        ownedRaceIds = setOf(project.raceData.race.id)
        try {
        if (series && path != null) {
            DesktopEventSeriesArchiveWorkspaces.workspaceFor(path)?.memberPaths.orEmpty().forEach { member ->
                val other = if (member == path) project else DesktopProjectFiles.read(member)
                ownedPaths = ownedPaths + member
                ownedRaceIds = ownedRaceIds + other.raceData.race.id
                if (other.raceData.race.raceType == RaceType.CLASSIC) {
                    require(pending[other.raceData.race.id]?.first.let { it == null || it == member }) { "Selected races share a race identity; correct the series before analysis." }
                    pending[other.raceData.race.id] = member to other
                }
            }
        }
        pending[project.raceData.race.id] = path to project
        drain()
        } catch (e: Exception) {
            cancel()
            status = "Unable to start route analysis: ${e.message}"
            notification = status
        }
    }

    private fun enqueue(path: Path?, project: EventProjectFile) {
        pending[project.raceData.race.id] = path to project
        if (!running) drain()
    }

    fun cancel() {
        if (waitingExport != null) notification = "Pending export cancelled because route processing stopped. Completed estimates can still be exported."
        cancelExport()
        continuous = false
        pending.clear()
        job?.cancel()
        generation++
        running = false
        password = null
        surface = null
        calculationCache = DesktopClassicRouteAnalysis.CalculationCache()
        status = "Stopped. Completed estimates remain available; save any unsaved Race File changes."
    }

    private fun drain() {
        val runGeneration = ++generation
        running = true
        job = scope.launch {
            val outcomes = linkedMapOf<String, Triple<Int, Int, Int>>()
            val unsaved = mutableSetOf<String>()
            try {
                while (pending.isNotEmpty()) {
                    val (id, target) = pending.entries.first().let { it.key to it.value }
                    pending.remove(id)
                    val (path, captured) = target
                    attempted[id] = key(captured)
                    val initiallyClean = session?.currentProject == captured && session?.hasUnsavedChanges == false
                    status = "Preparing cached terrain and verifying ideal Classic orders…"
                    val context = coroutineContext
                    val surfaceProjects = listOf(captured) + pending.values.map { it.second }
                    var lastCheckpoint = 0L
                    val calculated = withContext(Dispatchers.Default) {
                        val frozen = surface ?: (loadSurface?.invoke { context.ensureActive() } ?: run {
                            val surfaceInfos = surfaceProjects.flatMap { it.raceData.categories + it.raceData.courseMappings }.mapNotNull {
                                context.ensureActive()
                                it.category.storedCourseInfo(password)
                            }
                            DesktopVenueElevationCache.freezeForRouteAnalysis({ context.ensureActive() }, DesktopClassicRouteAnalysis.terrainBounds(surfaceInfos))
                        })
                            .also { context.ensureActive(); surface = it }
                        val infos = (captured.raceData.categories + captured.raceData.courseMappings).mapNotNull { category ->
                            context.ensureActive()
                            category.category.storedCourseInfo(password)?.let { category.category.id to it }
                        }.toMap()
                        DesktopClassicRouteAnalysis.calculate(captured, infos, frozen,
                            checkCancelled = { context.ensureActive() },
                            onPartial = { result ->
                                if (System.nanoTime() - lastCheckpoint > 1_000_000_000L) {
                                    recovery.write(captured, path, result)
                                    lastCheckpoint = System.nanoTime()
                                }
                                withContext(context) {
                                    context.ensureActive()
                                    if (session?.currentPath == path) session?.currentProject?.let { current ->
                                        val merged = mergeClassicRouteAnalysis(current, captured, result)
                                        if (merged != current) changed(requireNotNull(session).updateCurrentProject { merged })
                                    }
                                    completeWaitingExport()
                                    status = "Classic route estimates: ${result.results.size} processed. Exports include completed, current estimates."
                                }
                            }, cacheState = calculationCache)
                    }
                    coroutineContext.ensureActive()
                    withContext(Dispatchers.IO) { recovery.write(captured, path, calculated) }
                    var saved = false
                    var exportProject: EventProjectFile? = null
                    if (session?.currentPath == path && session?.currentProject?.raceData?.race?.id == id) {
                        val activeSession = requireNotNull(session)
                        val current = requireNotNull(activeSession.currentProject)
                        val merged = mergeClassicRouteAnalysis(current, captured, calculated)
                        exportProject = merged
                        changed(activeSession.updateCurrentProject { merged })
                        if (initiallyClean && path != null && merged.copy(desktopRouteAnalysis = null) == captured.copy(desktopRouteAnalysis = null) &&
                            DesktopProjectFiles.read(path).copy(desktopRouteAnalysis = null) == captured.copy(desktopRouteAnalysis = null)) {
                            activeSession.save()
                            changed(requireNotNull(activeSession.currentProject))
                            saved = true
                        }
                    } else if (path != null && ownedPaths.size > 1 && session?.currentPath in ownedPaths &&
                        session?.currentProject?.raceData?.race?.id in ownedRaceIds) {
                        val current = DesktopProjectFiles.read(path)
                        val merged = mergeClassicRouteAnalysis(current, captured, calculated)
                        exportProject = merged
                        if (merged != current) DesktopProjectFiles.write(path, merged)
                        saved = merged.desktopRouteAnalysis == calculated
                    }
                    val ready = exportProject?.let { DesktopClassicRouteAnalysis.projection(it).size } ?: 0
                    val unavailable = calculated.results.values.count { it.length == null }
                    val stale = (calculated.results.size - ready - unavailable).coerceAtLeast(0)
                    outcomes[id] = Triple(ready, unavailable, stale)
                    if (saved) unsaved.remove(id) else unsaved.add(id)
                    status = "Classic route analysis complete: $ready current estimates, $unavailable unavailable, $stale changed inputs requiring recalculation. " +
                        if (saved) "Saved with results." else "Save the Race File to persist completed estimates."
                    completeWaitingExport()
                }
                status = "Classic route analysis complete for ${outcomes.size} races: ${outcomes.values.sumOf { it.first }} current estimates, " +
                    "${outcomes.values.sumOf { it.second }} unavailable, ${outcomes.values.sumOf { it.third }} changed inputs requiring recalculation. " +
                    if (unsaved.isEmpty()) "Saved with results." else "Save ${unsaved.size} Race File(s) to persist their completed estimates; recovery checkpoints are retained."
                if (!continuous) notification = status
                else status += " Caught up; watching for new results."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cancelExport()
                continuous = false
                pending.clear()
                status = "Route analysis stopped: ${e.message ?: "Unexpected error"}. Completed estimates are retained."
                notification = status
            } finally {
                if (runGeneration == generation) {
                    running = false
                    if (!continuous) { password = null; surface = null; calculationCache = DesktopClassicRouteAnalysis.CalculationCache() }
                }
            }
        }
    }
}

@Composable
internal fun DesktopClassicRouteAnalysisHost(
    project: EventProjectFile?, session: DesktopProjectSession,
    state: ClassicRouteAnalysisUi,
    onChanged: (EventProjectFile) -> Unit, content: @Composable () -> Unit
) {
    state.session = session
    state.changed = onChanged
    LaunchedEffect(project, session.currentPath) { state.observe(project, session.currentPath) }
    LaunchedEffect(project?.raceData?.race?.id, session.currentPath) {
        val captured = project ?: return@LaunchedEffect
        val path = session.currentPath
        val recovered = withContext(Dispatchers.IO) { runCatching { DesktopClassicRouteRecovery().recover(captured, path) } }
        recovered.onSuccess { restored ->
            if (restored != captured && session.currentProject == captured && session.currentPath == path) {
                onChanged(session.updateCurrentProject { restored })
                state.status = "Recovered completed route estimates for unchanged inputs. Save the Race File to retain them."
            }
        }.onFailure { state.status = "Route-analysis recovery could not be read. Native saved results are unchanged." }
    }
    DisposableEffect(Unit) { onDispose { state.cancel() } }
    CompositionLocalProvider(LocalClassicRouteAnalysis provides state, content = content)
    if (state.exportChoice) {
        AlertDialog(onDismissRequest = state::cancelExport, title = { Text("Route estimates are still processing") },
            text = { Text("Export completed values now (missing estimates remain blank), or wait in the background for the results already present at this request. New downloads will not delay that export; corrections require a refreshed request.") },
            confirmButton = { TextButton(onClick = state::waitForExport) { Text("Wait for captured results") } },
            dismissButton = { Row { TextButton(onClick = state::exportAvailable) { Text("Export available now") }; TextButton(onClick = state::cancelExport) { Text("Cancel") } } })
    }
    state.notification?.let { message ->
        AlertDialog(onDismissRequest = { state.notification = null }, title = { Text("Classic route analysis") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { state.notification = null }) { Text("OK") } })
    }
    if (state.showDialog && project != null) {
        var password by remember { mutableStateOf("") }
        var watch by remember { mutableStateOf(false) }
        var series by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { state.showDialog = false }, title = { Text("Estimate effective route lengths") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Desktop Classic only. Verifies the ideal order using the same cached terrain and algorithm as the downloaded routes. No changes to scoring or the course design. Exports use completed estimates only; wait for completion for full coverage.")
                    OutlinedTextField(password, { password = it }, label = { Text("Race Password (if protected)") }, visualTransformation = PasswordVisualTransformation())
                    Row { Checkbox(watch, { watch = it }); Text("Also process new or corrected downloads while this app is open") }
                    if (session.currentPath?.let { DesktopEventSeriesArchiveWorkspaces.workspaceFor(it) } != null) {
                        Row { Checkbox(series, { series = it }); Text("Process all Classic races in this series") }
                    }
                    Text("Cached terrain sources are retained locally for provenance. Android does not calculate or export these estimates; transfer results here for desktop processing.")
                }
            }, confirmButton = { TextButton(onClick = {
                state.showDialog = false
                state.start(project, session.currentPath, password, watch, series)
                password = ""
            }) { Text("Start in background") } }, dismissButton = { TextButton(onClick = { state.showDialog = false }) { Text("Cancel") } })
    }
}

@Composable
internal fun ClassicRouteAnalysisPanel(project: EventProjectFile) {
    if (project.raceData.race.raceType != RaceType.CLASSIC) return
    val state = LocalClassicRouteAnalysis.current ?: return
    val savedSummary = remember(project) {
        val ready = DesktopClassicRouteAnalysis.projection(project)
        val changedReferences = project.desktopRouteAnalysis?.results.orEmpty()
            .filterKeys { it in ready }.values.mapNotNull { project.desktopRouteAnalysis?.contexts?.get(it.contextId) }
            .distinctBy { it.categoryId }.count { it.previousEffectiveMeters != null && it.previousEffectiveMeters != it.effectiveMeters }
        ResultRouteLength.coverage(ready.size, project.raceData.competitorData.count { it.readoutData != null }) to changedReferences
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(state.status)
        if (project.desktopRouteAnalysis != null) {
            Text(savedSummary.first)
            if (savedSummary.second > 0) Text("The verified analysis ideal differs from the saved course-design length for ${savedSummary.second} categories. Comparisons use the analysis ideal; the course design is unchanged.")
        }
        Text("Available in desktop result CSV, TXT, HTML, PDF, reports and website exports. Android, ARDF, IOF and ROBIS formats are unchanged.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.showDialog = true }, enabled = !state.running) { Text("Estimate effective route lengths…") }
            if (state.running || state.continuous) TextButton(onClick = state::cancel) { Text("Stop route processing") }
        }
        if (state.continuous) Text("Automatic processing is enabled for new or corrected Classic results in this app session.")
    }
}
