package org.openardf.radiooracle.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.StoredClassicRouteAnalysis
import java.nio.file.Files
import java.nio.file.Path

/** Metadata only: never saves unsaved readouts, passwords, or course coordinates. */
internal class DesktopClassicRouteRecovery(
    private val directory: Path = DesktopVenueElevationCache.cacheDirectory().resolve("route-analysis-recovery")
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private fun path(project: EventProjectFile, eventPath: Path?): Path {
        // Materialized series member paths change on every app launch. Anchor checkpoints to
        // the authoritative archive and member identity so restart recovery can find them.
        val workspace = eventPath?.let(DesktopEventSeriesArchiveWorkspaces::workspaceFor)
        val identity = if (workspace != null) {
            "${workspace.containerPath}|${workspace.seriesEventIdForPath(requireNotNull(eventPath))}"
        } else eventPath?.toAbsolutePath()?.normalize().toString()
        return directory.resolve(DesktopClassicRouteAnalysis.sha256("$identity|${project.raceData.race.id}") + ".json")
    }
    fun write(project: EventProjectFile, eventPath: Path?, metadata: StoredClassicRouteAnalysis) =
        writeDesktopTextAtomically(path(project, eventPath), json.encodeToString(metadata))

    fun recover(project: EventProjectFile, eventPath: Path?): EventProjectFile {
        val file = path(project, eventPath)
        if (!Files.isRegularFile(file)) return project
        val recovered = json.decodeFromString<StoredClassicRouteAnalysis>(Files.readString(file))
        if (recovered.version != 1) return project
        val fingerprint = DesktopClassicRouteAnalysis.courseFingerprint(project)
        val nativeReady = DesktopClassicRouteAnalysis.projection(project).keys
        val valid = recovered.results.filter { (id, snapshot) ->
            recovered.contexts[snapshot.contextId]?.let { it.method == DesktopClassicRouteAnalysis.METHOD && it.courseFingerprint == fingerprint } == true &&
                id !in nativeReady
        }
        return if (valid.isEmpty()) project else mergeClassicRouteAnalysis(project, project, recovered.copy(results = valid))
    }
}
