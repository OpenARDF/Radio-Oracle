package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLinkedEvent
import org.openardf.radiooracle.shared.event.EventSeriesSupport
import org.openardf.radiooracle.shared.event.EventSeriesValidationIssue
import org.openardf.radiooracle.shared.event.EventSeriesIssueSeverity
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Storage boundary used by desktop Event Series session logic. */
interface EventSeriesStore {
    fun read(path: Path): EventSeriesFile
    fun write(path: Path, seriesFile: EventSeriesFile)
    fun readEvent(path: Path): EventProjectFile
    fun writeEvent(path: Path, projectFile: EventProjectFile)
    fun exists(path: Path): Boolean
    fun copyFile(source: Path, target: Path)
}

/** Desktop filesystem adapter for Radio-Oracle Event Series manifests. */
object DesktopEventSeriesFiles : EventSeriesStore {
    override fun read(path: Path): EventSeriesFile =
        EventSeriesFileJson.decode(Files.readString(path, StandardCharsets.UTF_8))

    override fun write(path: Path, seriesFile: EventSeriesFile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, EventSeriesFileJson.encode(seriesFile), StandardCharsets.UTF_8)
    }

    override fun readEvent(path: Path): EventProjectFile =
        DesktopProjectFiles.read(path)

    override fun writeEvent(path: Path, projectFile: EventProjectFile) {
        DesktopProjectFiles.write(path, projectFile)
    }

    override fun exists(path: Path): Boolean =
        Files.exists(path)

    override fun copyFile(source: Path, target: Path) {
        target.parent?.let { Files.createDirectories(it) }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Tracks the currently open Event Series manifest and its folder. */
class DesktopEventSeriesSession(private val store: EventSeriesStore) {
    var currentSeries: EventSeriesFile? = null
        private set

    var currentPath: Path? = null
        private set

    var hasUnsavedChanges: Boolean = false
        private set

    val currentFolder: Path?
        get() = currentPath?.parent

    fun open(path: Path): EventSeriesFile {
        val seriesFile = store.read(path)
        currentSeries = seriesFile
        currentPath = path
        hasUnsavedChanges = false
        return seriesFile
    }

    fun newSeries(path: Path, seriesFile: EventSeriesFile): EventSeriesFile {
        currentSeries = seriesFile
        currentPath = path
        hasUnsavedChanges = true
        return seriesFile
    }

    fun updateCurrentSeries(transform: (EventSeriesFile) -> EventSeriesFile): EventSeriesFile {
        val seriesFile = requireNotNull(currentSeries) {
            "Cannot edit before an Event Series is open."
        }
        val updatedSeries = transform(seriesFile)
        currentSeries = updatedSeries
        hasUnsavedChanges = hasUnsavedChanges || updatedSeries != seriesFile
        return updatedSeries
    }

    fun save() {
        val path = requireNotNull(currentPath) {
            "Cannot save before an Event Series path is selected."
        }
        val seriesFile = requireNotNull(currentSeries) {
            "Cannot save before an Event Series is open."
        }
        store.write(path, seriesFile)
        hasUnsavedChanges = false
    }

    fun closeSeries(discardUnsavedChanges: Boolean = false) {
        check(discardUnsavedChanges || !hasUnsavedChanges) {
            "Cannot close while there are unsaved Event Series changes."
        }
        currentSeries = null
        currentPath = null
        hasUnsavedChanges = false
    }

    fun loadLinkedEvents(): List<EventSeriesLinkedEvent> {
        val folder = requireNotNull(currentFolder) {
            "Cannot load linked Event Files before an Event Series path is selected."
        }
        val seriesFile = requireNotNull(currentSeries) {
            "Cannot load linked Event Files before an Event Series is open."
        }
        return seriesFile.sortedEvents().map { event ->
            EventSeriesLinkedEvent(
                event = event,
                projectFile = store.readEvent(folder.resolve(event.eventFilePath).normalize())
            )
        }
    }

    fun validateLinkedEvents(): List<EventSeriesValidationIssue> {
        val seriesFile = currentSeries ?: return emptyList()
        val folder = currentFolder ?: return listOf(
            EventSeriesValidationIssue(
                severity = EventSeriesIssueSeverity.ERROR,
                message = "Event Series path is not selected."
            )
        )
        val loadedEvents = mutableListOf<EventSeriesLinkedEvent>()
        val issues = mutableListOf<EventSeriesValidationIssue>()
        seriesFile.sortedEvents().forEach { event ->
            val path = folder.resolve(event.eventFilePath).normalize()
            if (!store.exists(path)) {
                issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Series event '${event.displayName}' is missing its Event File.",
                    seriesEventId = event.seriesEventId
                )
            } else {
                loadedEvents += EventSeriesLinkedEvent(event, store.readEvent(path))
            }
        }
        return issues + EventSeriesSupport.validateLinkedEvents(seriesFile, loadedEvents)
    }
}

/** High-level desktop operations for Event Series membership and clean export. */
object DesktopEventSeriesActions {
    fun findManifestNearEvent(
        eventPath: Path,
        maxAncestorDepth: Int = 6,
        exists: (Path) -> Boolean = Files::exists
    ): Path? =
        generateSequence(eventPath.parent) { it.parent }
            .take(maxAncestorDepth)
            .map { it.resolve(EVENT_SERIES_FILE_NAME) }
            .firstOrNull(exists)

    fun eventSummaries(
        store: EventSeriesStore,
        manifestPath: Path,
        currentEventPath: Path? = null
    ): List<DesktopEventSeriesEventSummary> {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Event Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = currentEventPath?.toAbsolutePath()?.normalize()
        // The Events screen should be cheap and tolerant: list manifest entries and file presence
        // without reading every Event File just to render the workflow panel.
        return seriesFile.sortedEvents().map { event ->
            val resolvedPath = seriesFolder.resolve(event.eventFilePath).normalize()
            DesktopEventSeriesEventSummary(
                seriesEventId = event.seriesEventId,
                displayName = event.displayName,
                order = event.order,
                eventFilePath = event.eventFilePath,
                resolvedPath = resolvedPath,
                exists = store.exists(resolvedPath),
                isCurrentEvent = normalizedCurrentPath != null &&
                    resolvedPath.toAbsolutePath().normalize() == normalizedCurrentPath,
                startDateTimeIso = event.startDateTimeIso,
                formatLabel = event.formatLabel
            )
        }
    }

    fun createSeriesWithEvent(
        seriesFolder: Path,
        seriesId: String,
        seriesName: String,
        eventPath: Path,
        eventProjectFile: EventProjectFile,
        seriesEventId: String = eventProjectFile.raceData.race.id
    ): DesktopEventSeriesCreateResult {
        val manifestPath = seriesFolder.resolve(EVENT_SERIES_FILE_NAME)
        val relativeEventPath = relativeEventPath(seriesFolder, eventPath)
        val event = EventSeriesEvent(
            seriesEventId = seriesEventId,
            eventFilePath = relativeEventPath,
            order = 0,
            displayName = eventProjectFile.raceData.race.name,
            startDateTimeIso = eventProjectFile.raceData.race.startDateTimeIso,
            formatLabel = eventProjectFile.raceData.race.raceType.name
        )
        val seriesFile = EventSeriesFile(
            seriesId = seriesId,
            name = seriesName,
            events = listOf(event)
        )
        val linkedProjectFile = EventProjectEditor.updateSeriesLink(eventProjectFile, seriesId, seriesEventId)
        return DesktopEventSeriesCreateResult(manifestPath, seriesFile, eventPath, linkedProjectFile)
    }

    fun linkCurrentEvent(
        seriesFile: EventSeriesFile,
        eventPath: Path,
        seriesFolder: Path,
        eventProjectFile: EventProjectFile,
        seriesEventId: String = eventProjectFile.raceData.race.id
    ): DesktopEventSeriesLinkResult =
        addEventToSeries(
            seriesFile = seriesFile,
            eventPath = eventPath,
            seriesFolder = seriesFolder,
            eventProjectFile = eventProjectFile,
            seriesEventId = seriesEventId
        )

    fun addEventToSeries(
        seriesFile: EventSeriesFile,
        eventPath: Path,
        seriesFolder: Path,
        eventProjectFile: EventProjectFile,
        seriesEventId: String = eventProjectFile.raceData.race.id
    ): DesktopEventSeriesLinkResult {
        val relativeEventPath = relativeEventPath(seriesFolder, eventPath)
        val existingEvent = seriesFile.events.firstOrNull { it.eventFilePath == relativeEventPath }
        val resolvedSeriesEventId = existingEvent?.seriesEventId
            ?: uniqueSeriesEventId(seriesFile, seriesEventId, relativeEventPath)
        val nextOrder = existingEvent?.order ?: (seriesFile.events.maxOfOrNull { it.order } ?: -1) + 1
        val event = EventSeriesEvent(
            seriesEventId = resolvedSeriesEventId,
            eventFilePath = relativeEventPath,
            order = nextOrder,
            displayName = eventProjectFile.raceData.race.name,
            startDateTimeIso = eventProjectFile.raceData.race.startDateTimeIso,
            formatLabel = eventProjectFile.raceData.race.raceType.name
        )
        // Re-selecting an existing Event File refreshes its manifest metadata without changing its order.
        val updatedSeriesFile = seriesFile.copy(
            events = seriesFile.events.filterNot {
                it.seriesEventId == resolvedSeriesEventId || it.eventFilePath == relativeEventPath
            } + event
        )
        val linkedProjectFile = EventProjectEditor.updateSeriesLink(eventProjectFile, seriesFile.seriesId, resolvedSeriesEventId)
        return DesktopEventSeriesLinkResult(updatedSeriesFile, linkedProjectFile)
    }

    fun removeCurrentEvent(seriesFile: EventSeriesFile, eventProjectFile: EventProjectFile): DesktopEventSeriesLinkResult {
        val link = eventProjectFile.seriesLink ?: return DesktopEventSeriesLinkResult(seriesFile, eventProjectFile)
        val updatedSeriesFile = if (link.seriesId == seriesFile.seriesId) {
            seriesFile.copy(events = seriesFile.events.filterNot { it.seriesEventId == link.seriesEventId })
        } else {
            seriesFile
        }
        return DesktopEventSeriesLinkResult(updatedSeriesFile, EventProjectEditor.removeSeriesLink(eventProjectFile))
    }

    fun exportSeries(store: EventSeriesStore, manifestPath: Path, targetFolder: Path): EventSeriesExportResult {
        val seriesFile = store.read(manifestPath)
        val sourceFolder = requireNotNull(manifestPath.parent) {
            "Event Series manifest must have a parent folder."
        }
        val missingFiles = seriesFile.sortedEvents()
            .map { it to sourceFolder.resolve(it.eventFilePath).normalize() }
            .filterNot { (_, path) -> store.exists(path) }
            .map { (event, _) -> event.eventFilePath }
        require(missingFiles.isEmpty()) {
            "Cannot export Event Series because required Event Files are missing: ${missingFiles.joinToString()}"
        }

        val targetManifest = targetFolder.resolve(EVENT_SERIES_FILE_NAME)
        store.write(targetManifest, seriesFile)
        seriesFile.sortedEvents().forEach { event ->
            val source = sourceFolder.resolve(event.eventFilePath).normalize()
            val target = targetFolder.resolve(event.eventFilePath).normalize()
            store.copyFile(source, target)
        }
        return EventSeriesExportResult(
            manifestPath = targetManifest,
            eventFilePaths = seriesFile.sortedEvents().map { targetFolder.resolve(it.eventFilePath).normalize() }
        )
    }

    private fun relativeEventPath(seriesFolder: Path, eventPath: Path): String {
        val normalizedFolder = seriesFolder.toAbsolutePath().normalize()
        val normalizedEventPath = eventPath.toAbsolutePath().normalize()
        require(normalizedEventPath.startsWith(normalizedFolder)) {
            "Event File must be inside the Event Series folder before it can be added to the manifest."
        }
        return normalizedFolder.relativize(normalizedEventPath).toString().replace('\\', '/')
    }

    private fun uniqueSeriesEventId(seriesFile: EventSeriesFile, preferredId: String, relativeEventPath: String): String {
        val usedIds = seriesFile.events.map { it.seriesEventId }.toSet()
        val normalizedPreferredId = preferredId.trim().ifBlank { "event" }
        if (normalizedPreferredId !in usedIds) {
            return normalizedPreferredId
        }
        // Older copied Event Files can share the same race id. In a series, membership is per
        // Event File, so duplicate race ids on different paths need distinct series event ids.
        val fileName = relativeEventPath.substringAfterLast('/')
        val fileNameWithoutProjectExtension = fileName
            .removeSuffix(".rom.json")
            .removeSuffix(".json")
        val fileStem = fileNameWithoutProjectExtension.substringBeforeLast('.', fileNameWithoutProjectExtension)
        val slug = fileStem
            .lowercase()
            .map { character -> if (character.isLetterOrDigit()) character else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "event" }
        val baseId = "$normalizedPreferredId-$slug"
        if (baseId !in usedIds) {
            return baseId
        }
        return generateSequence(2) { it + 1 }
            .map { "$baseId-$it" }
            .first { it !in usedIds }
    }
}

data class DesktopEventSeriesCreateResult(
    val manifestPath: Path,
    val seriesFile: EventSeriesFile,
    val eventPath: Path,
    val eventProjectFile: EventProjectFile
)

data class DesktopEventSeriesLinkResult(
    val seriesFile: EventSeriesFile,
    val eventProjectFile: EventProjectFile
)

data class EventSeriesExportResult(
    val manifestPath: Path,
    val eventFilePaths: List<Path>
)

data class DesktopEventSeriesEventSummary(
    val seriesEventId: String,
    val displayName: String,
    val order: Int,
    val eventFilePath: String,
    val resolvedPath: Path,
    val exists: Boolean,
    val isCurrentEvent: Boolean,
    val startDateTimeIso: String? = null,
    val formatLabel: String? = null
)
