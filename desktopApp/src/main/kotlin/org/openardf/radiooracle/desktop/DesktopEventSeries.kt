/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME
import org.openardf.radiooracle.shared.event.EVENT_SERIES_NAMED_FILE_SUFFIX
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLinkedEvent
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.EventSeriesSupport
import org.openardf.radiooracle.shared.event.EventSeriesValidationIssue
import org.openardf.radiooracle.shared.event.EventSeriesIssueSeverity
import org.openardf.radiooracle.shared.event.effectiveStartDrawSettings
import org.openardf.radiooracle.shared.event.isEventSeriesFileName
import org.openardf.radiooracle.shared.event.EventValidationIssueSeverity
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.importing.ImportValidationRules
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.prefs.Preferences
import kotlin.math.roundToInt

/** Storage boundary used by desktop Race Series session logic. */
interface EventSeriesStore {
    fun read(path: Path): EventSeriesFile
    fun write(path: Path, seriesFile: EventSeriesFile)
    fun readEvent(path: Path): EventProjectFile
    fun writeEvent(path: Path, projectFile: EventProjectFile)
    fun exists(path: Path): Boolean
    fun copyFile(source: Path, target: Path)
    fun moveManifest(source: Path, target: Path, seriesFile: EventSeriesFile) {
        write(target, seriesFile)
    }
}

/** Desktop filesystem adapter for Radio-Oracle Race Series manifests. */
object DesktopEventSeriesFiles : EventSeriesStore {
    override fun read(path: Path): EventSeriesFile =
        DesktopEventSeriesArchiveWorkspaces.workspaceFor(path)
            ?.takeIf { it.isManifestPath(path) }
            ?.readSeries()
            ?: EventSeriesFileJson.decode(Files.readString(path, StandardCharsets.UTF_8))

    override fun write(path: Path, seriesFile: EventSeriesFile) {
        DesktopEventSeriesArchiveWorkspaces.workspaceFor(path)
            ?.takeIf { it.isManifestPath(path) }
            ?.let { workspace ->
                workspace.writeSeries(seriesFile)
                return
            }
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

    override fun moveManifest(source: Path, target: Path, seriesFile: EventSeriesFile) {
        val normalizedSource = source.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        target.parent?.let { Files.createDirectories(it) }
        if (normalizedSource != normalizedTarget && Files.exists(target)) {
            error("Race Series manifest already exists at ${target.fileName}.")
        }
        if (normalizedSource != normalizedTarget && Files.exists(source)) {
            Files.move(source, target)
        }
        write(target, seriesFile)
    }
}

interface DesktopLastSeriesEventStore {
    fun lastEventPath(manifestPath: Path): Path?
    fun rememberEventPath(manifestPath: Path, eventPath: Path)
}

object DesktopLastSeriesEventPreferences : DesktopLastSeriesEventStore {
    private const val LAST_SERIES_EVENT_KEY_PREFIX = "lastSeriesEvent."
    private val preferences: Preferences =
        Preferences.userNodeForPackage(DesktopLastSeriesEventPreferences::class.java)

    override fun lastEventPath(manifestPath: Path): Path? =
        runCatching {
            val rememberedValue = preferences.get(lastEventKey(manifestPath), null)
                ?.takeIf { it.isNotBlank() }
            val workspace = DesktopEventSeriesArchiveWorkspaces.workspaceFor(manifestPath)
            if (workspace == null) {
                rememberedValue?.let(Path::of)
            } else {
                workspace.archive.seriesFile.events
                    .firstOrNull { it.seriesEventId == rememberedValue }
                    ?.let(workspace::memberPath)
            }
        }.getOrNull()

    override fun rememberEventPath(manifestPath: Path, eventPath: Path) {
        runCatching {
            val workspace = DesktopEventSeriesArchiveWorkspaces.workspaceFor(manifestPath)
            val rememberedValue = workspace
                ?.seriesEventIdForPath(eventPath)
                ?: eventPath.toAbsolutePath().normalize().toString()
            preferences.put(lastEventKey(manifestPath), rememberedValue)
        }
    }

    private fun lastEventKey(manifestPath: Path): String {
        val stablePath = DesktopEventSeriesArchiveWorkspaces.containerPathFor(manifestPath) ?: manifestPath
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stablePath.toAbsolutePath().normalize().toString().toByteArray(StandardCharsets.UTF_8))
        return LAST_SERIES_EVENT_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

object DesktopEventSeriesStartFairnessSolutionNumbers {
    fun assign(
        existingNumbers: Map<String, Int>,
        manifestPath: Path,
        solutionSignature: String
    ): DesktopEventSeriesStartFairnessSolutionNumbering {
        val manifestKey = manifestPath.toAbsolutePath().normalize().toString()
        val solutionKey = "$manifestKey|$solutionSignature"
        val existingNumber = existingNumbers[solutionKey]
        val solutionNumber = existingNumber
            ?: (existingNumbers
                .filterKeys { it.startsWith("$manifestKey|") }
                .values
                .maxOrNull()
                ?: 0) + 1
        return DesktopEventSeriesStartFairnessSolutionNumbering(
            solutionNumber = solutionNumber,
            repeatedSolution = existingNumber != null,
            solutionNumbers = if (existingNumber == null) {
                existingNumbers + (solutionKey to solutionNumber)
            } else {
                existingNumbers
            }
        )
    }
}

/** Tracks the currently open Race Series manifest and its folder. */
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
            "Cannot edit before a Race Series is open."
        }
        val updatedSeries = transform(seriesFile)
        currentSeries = updatedSeries
        hasUnsavedChanges = hasUnsavedChanges || updatedSeries != seriesFile
        return updatedSeries
    }

    fun save() {
        val path = requireNotNull(currentPath) {
            "Cannot save before a Race Series path is selected."
        }
        val seriesFile = requireNotNull(currentSeries) {
            "Cannot save before a Race Series is open."
        }
        store.write(path, seriesFile)
        hasUnsavedChanges = false
    }

    fun closeSeries(discardUnsavedChanges: Boolean = false) {
        check(discardUnsavedChanges || !hasUnsavedChanges) {
            "Cannot close while there are unsaved Race Series changes."
        }
        currentSeries = null
        currentPath = null
        hasUnsavedChanges = false
    }

    fun loadLinkedEvents(): List<EventSeriesLinkedEvent> {
        val folder = requireNotNull(currentFolder) {
            "Cannot load linked Race Files before a Race Series path is selected."
        }
        val seriesFile = requireNotNull(currentSeries) {
            "Cannot load linked Race Files before a Race Series is open."
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
                message = "Race Series path is not selected."
            )
        )
        val loadedEvents = mutableListOf<EventSeriesLinkedEvent>()
        val issues = mutableListOf<EventSeriesValidationIssue>()
        seriesFile.sortedEvents().forEach { event ->
            val path = folder.resolve(event.eventFilePath).normalize()
            if (!store.exists(path)) {
                issues += EventSeriesValidationIssue(
                    severity = EventSeriesIssueSeverity.ERROR,
                    message = "Series race '${event.displayName}' is missing its Race File.",
                    seriesEventId = event.seriesEventId
                )
            } else {
                loadedEvents += EventSeriesLinkedEvent(event, store.readEvent(path))
            }
        }
        return issues + validateLoadedEventFiles(loadedEvents) + EventSeriesSupport.validateLinkedEvents(seriesFile, loadedEvents)
    }

    private fun validateLoadedEventFiles(loadedEvents: List<EventSeriesLinkedEvent>): List<EventSeriesValidationIssue> =
        loadedEvents.flatMap { linkedEvent ->
            EventValidationRules.validateRaceData(linkedEvent.projectFile.raceData).map { issue ->
                EventSeriesValidationIssue(
                    severity = when (EventValidationRules.severity(issue)) {
                        EventValidationIssueSeverity.ERROR -> EventSeriesIssueSeverity.ERROR
                        EventValidationIssueSeverity.WARNING -> EventSeriesIssueSeverity.WARNING
                    },
                    message = "Race '${linkedEvent.event.displayName}' self-validation: " +
                        DesktopEventValidationText.messageFor(issue),
                    seriesEventId = linkedEvent.event.seriesEventId
                )
            }
        }
}

/** High-level desktop operations for Race Series membership and clean export. */
object DesktopEventSeriesActions {
    const val DEFAULT_SERIES_NAME = "New Series"

    fun findManifestNearEvent(
        eventPath: Path,
        maxAncestorDepth: Int = 6,
        exists: (Path) -> Boolean = Files::exists
    ): Path? =
        manifestCandidatesNearEvent(eventPath, maxAncestorDepth, exists)
            .firstOrNull()

    fun findManifestNearEvent(
        eventPath: Path,
        seriesLink: EventSeriesLink?,
        store: EventSeriesStore,
        maxAncestorDepth: Int = 6
    ): Path? {
        val candidates = manifestCandidatesNearEvent(eventPath, maxAncestorDepth, store::exists).toList()
        if (seriesLink != null) {
            val candidateSeries = candidates.mapNotNull { candidate ->
                runCatching { candidate to store.read(candidate) }.getOrNull()
            }
            candidateSeries.firstOrNull { (candidate, seriesFile) ->
                seriesFile.seriesId == seriesLink.seriesId &&
                    seriesFile.events.any { event ->
                        event.seriesEventId == seriesLink.seriesEventId &&
                            eventPathMatches(candidate, event.eventFilePath, eventPath)
                    }
            }?.let { return it.first }
            candidateSeries.firstOrNull { (_, seriesFile) ->
                seriesFile.seriesId == seriesLink.seriesId
            }?.let { return it.first }
        }
        return candidates.firstOrNull()
    }

    private fun eventPathMatches(manifestPath: Path, eventFilePath: String, eventPath: Path): Boolean {
        val seriesFolder = manifestPath.parent ?: return false
        val resolvedEventPath = seriesFolder.resolve(eventFilePath).normalize().toAbsolutePath().normalize()
        val openedEventPath = eventPath.toAbsolutePath().normalize()
        return resolvedEventPath == openedEventPath
    }

    private fun manifestCandidatesNearEvent(
        eventPath: Path,
        maxAncestorDepth: Int,
        exists: (Path) -> Boolean
    ): Sequence<Path> =
        generateSequence(eventPath.parent) { it.parent }
            .take(maxAncestorDepth)
            .flatMap { eventSeriesManifestCandidates(it, exists).asSequence() }

    fun manifestPathForSeriesName(
        seriesFolder: Path,
        seriesName: String,
        fallbackName: String = DEFAULT_SERIES_NAME,
        exists: (Path) -> Boolean = Files::exists
    ): Path {
        val preferredName = seriesName.trim()
            .takeUnless { it.isBlank() || it == DEFAULT_SERIES_NAME }
            ?: fallbackName
        return manifestPathForSeriesFileName(
            seriesFolder = seriesFolder,
            fileNameStem = preferredName,
            exists = exists
        )
    }

    fun manifestPathForSeriesFileName(
        seriesFolder: Path,
        fileNameStem: String,
        exists: (Path) -> Boolean = Files::exists
    ): Path {
        val stem = seriesFileStem(fileNameStem)
        var candidate = seriesFolder.resolve("$stem$EVENT_SERIES_NAMED_FILE_SUFFIX")
        var index = 2
        while (exists(candidate)) {
            candidate = seriesFolder.resolve("$stem $index$EVENT_SERIES_NAMED_FILE_SUFFIX")
            index += 1
        }
        return candidate
    }

    fun manifestFileDisplayStem(manifestPath: Path): String {
        val fileName = manifestPath.fileName.toString()
        return when {
            fileName.endsWith(
                org.openardf.radiooracle.shared.event.EVENT_SERIES_ARCHIVE_FILE_SUFFIX,
                ignoreCase = true
            ) ->
                fileName.dropLast(org.openardf.radiooracle.shared.event.EVENT_SERIES_ARCHIVE_FILE_SUFFIX.length)
            fileName.endsWith(EVENT_SERIES_NAMED_FILE_SUFFIX, ignoreCase = true) ->
                fileName.dropLast(EVENT_SERIES_NAMED_FILE_SUFFIX.length)
            fileName == EVENT_SERIES_FILE_NAME ->
                fileName.removeSuffix(".radio-oracle.json")
            else -> fileName
        }
    }

    fun renameSeriesManifestFile(
        store: EventSeriesStore,
        manifestPath: Path,
        seriesFile: EventSeriesFile,
        fileNameStem: String
    ): Path {
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = manifestPath.toAbsolutePath().normalize()
        val targetPath = manifestPathForSeriesFileName(
            seriesFolder = seriesFolder,
            fileNameStem = fileNameStem,
            exists = { candidate ->
                candidate.toAbsolutePath().normalize() != normalizedCurrentPath && store.exists(candidate)
            }
        )
        if (targetPath.toAbsolutePath().normalize() == normalizedCurrentPath) {
            return manifestPath
        }
        store.moveManifest(manifestPath, targetPath, seriesFile)
        return targetPath
    }

    fun eventPathToOpenFromManifest(
        store: EventSeriesStore,
        manifestPath: Path,
        lastSeriesEventStore: DesktopLastSeriesEventStore
    ): Path {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val memberPaths = seriesFile.sortedEvents().map { event ->
            seriesFolder.resolve(event.eventFilePath).normalize()
        }
        val memberPathKeys = memberPaths.map { it.toAbsolutePath().normalize() }.toSet()
        val rememberedPath = lastSeriesEventStore.lastEventPath(manifestPath)
            ?.toAbsolutePath()
            ?.normalize()
        if (rememberedPath != null && rememberedPath in memberPathKeys && store.exists(rememberedPath)) {
            return rememberedPath
        }
        return memberPaths.firstOrNull(store::exists)
            ?: error("Race Series contains no readable Race Files.")
    }

    fun rememberOpenedSeriesEvent(
        store: EventSeriesStore,
        eventPath: Path,
        lastSeriesEventStore: DesktopLastSeriesEventStore
    ) {
        val manifestPath = findManifestNearEvent(eventPath, exists = store::exists) ?: return
        val seriesFile = store.read(manifestPath)
        val seriesFolder = manifestPath.parent ?: return
        val openedPath = eventPath.toAbsolutePath().normalize()
        val isManifestMember = seriesFile.events.any { event ->
            seriesFolder.resolve(event.eventFilePath).normalize().toAbsolutePath().normalize() == openedPath
        }
        if (isManifestMember) {
            lastSeriesEventStore.rememberEventPath(manifestPath, eventPath)
        }
    }

    fun eventSummaries(
        store: EventSeriesStore,
        manifestPath: Path,
        currentEventPath: Path? = null
    ): List<DesktopEventSeriesEventSummary> {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = currentEventPath?.toAbsolutePath()?.normalize()
        // The Races screen should be cheap and tolerant: list manifest entries and file presence
        // without reading every Race File just to render the workflow panel.
        return seriesFile.sortedEvents().mapIndexed { index, event ->
            val resolvedPath = seriesFolder.resolve(event.eventFilePath).normalize()
            DesktopEventSeriesEventSummary(
                seriesEventId = event.seriesEventId,
                displayName = event.displayName,
                order = event.order,
                displayPosition = index + 1,
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

    fun competitorMatchingSummaries(
        store: EventSeriesStore,
        manifestPath: Path,
        currentEventPath: Path?
    ): List<DesktopEventSeriesCompetitorMatchSummary> {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = currentEventPath?.toAbsolutePath()?.normalize()
        val loadedEvents = seriesFile.sortedEvents().mapNotNull { event ->
            val resolvedPath = seriesFolder.resolve(event.eventFilePath).normalize()
            if (!store.exists(resolvedPath)) {
                null
            } else {
                EventSeriesLinkedEvent(
                    event = event,
                    projectFile = store.readEvent(resolvedPath)
                ) to resolvedPath.toAbsolutePath().normalize()
            }
        }

        return loadedEvents
            .flatMapIndexed { leftIndex, (left, leftPath) ->
                loadedEvents.drop(leftIndex + 1).map { (right, rightPath) ->
                    val report = EventSeriesSupport.matchCompetitors(seriesFile, left, right)
                    val methodCounts = report.matches.groupingBy { it.method.name }.eachCount()
                    DesktopEventSeriesCompetitorMatchSummary(
                        firstEventName = left.event.displayName,
                        firstSeriesEventId = left.event.seriesEventId,
                        firstCompetitorCount = left.projectFile.raceData.competitorData.size,
                        secondEventName = right.event.displayName,
                        secondSeriesEventId = right.event.seriesEventId,
                        secondCompetitorCount = right.projectFile.raceData.competitorData.size,
                        includesCurrentEvent = normalizedCurrentPath != null &&
                            (leftPath == normalizedCurrentPath || rightPath == normalizedCurrentPath),
                        matchCount = report.matches.size,
                        siNumberMatchCount = methodCounts["SI_NUMBER"] ?: 0,
                        bibNumberMatchCount = methodCounts["BIB_NUMBER"] ?: 0,
                        callSignMatchCount = methodCounts["CALL_SIGN"] ?: 0,
                        overrideMatchCount = methodCounts["OVERRIDE"] ?: 0,
                        issueCount = report.issues.size
                    )
                }
            }
    }

    fun competitorIdentityCoverageSummaries(
        store: EventSeriesStore,
        manifestPath: Path
    ): List<DesktopEventSeriesCompetitorIdentityCoverageSummary> {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val loadedEvents = seriesFile.sortedEvents().mapNotNull { event ->
            val resolvedPath = seriesFolder.resolve(event.eventFilePath).normalize()
            if (!store.exists(resolvedPath)) {
                null
            } else {
                EventSeriesLinkedEvent(
                    event = event,
                    projectFile = store.readEvent(resolvedPath)
                )
            }
        }
        if (loadedEvents.isEmpty()) {
            return emptyList()
        }

        val unionFind = StringUnionFind()
        val occurrences = mutableMapOf<String, DesktopEventSeriesCompetitorIdentityOccurrence>()
        val identityLabels = mutableMapOf<String, String>()
        loadedEvents.forEach { linkedEvent ->
            linkedEvent.projectFile.raceData.competitorData
                .map { it.competitorCategory.competitor }
                .forEach { competitor ->
                    val identities = EventSeriesSupport.competitorIdentities(competitor)
                    if (identities.isEmpty()) {
                        return@forEach
                    }
                    val occurrenceNode = identityOccurrenceNode(linkedEvent.event.seriesEventId, competitor.id)
                    unionFind.add(occurrenceNode)
                    occurrences[occurrenceNode] = DesktopEventSeriesCompetitorIdentityOccurrence(
                        seriesEventId = linkedEvent.event.seriesEventId,
                        eventName = linkedEvent.event.displayName,
                        competitorName = competitor.fullName()
                    )
                    identities.forEach { identity ->
                        val identityNode = identityNode(identity.key)
                        identityLabels[identityNode] = identity.label
                        unionFind.union(occurrenceNode, identityNode)
                    }
                }
        }
        seriesFile.competitorMatchOverrides.forEach { override ->
            val fromNode = identityOccurrenceNode(override.fromSeriesEventId, override.fromCompetitorId)
            val toNode = identityOccurrenceNode(override.toSeriesEventId, override.toCompetitorId)
            if (fromNode in occurrences && toNode in occurrences) {
                unionFind.union(fromNode, toNode)
            }
        }

        val eventNamesById = loadedEvents.associate { it.event.seriesEventId to it.event.displayName }
        return unionFind.nodes()
            .groupBy { unionFind.find(it) }
            .values
            .mapNotNull { componentNodes ->
                val componentOccurrences = componentNodes.mapNotNull { occurrences[it] }
                if (componentOccurrences.isEmpty()) {
                    return@mapNotNull null
                }
                val componentIdentityLabels = componentNodes.mapNotNull { identityLabels[it] }
                val presentEventIds = componentOccurrences.map { it.seriesEventId }.distinct()
                val missingEventNames = loadedEvents
                    .filterNot { it.event.seriesEventId in presentEventIds }
                    .map { it.event.displayName }
                val duplicateEventNames = componentOccurrences
                    .groupBy { it.seriesEventId }
                    .filterValues { it.size > 1 }
                    .keys
                    .mapNotNull { eventNamesById[it] }
                DesktopEventSeriesCompetitorIdentityCoverageSummary(
                    identityLabel = componentIdentityLabels
                        .sortedWith(EventSeriesSupport.competitorIdentityLabelComparator())
                        .firstOrNull()
                        ?: "Manual override",
                    competitorName = mostCommonCompetitorName(componentOccurrences),
                    presentEventCount = presentEventIds.size,
                    totalReadableEventCount = loadedEvents.size,
                    presentEventNames = presentEventIds.mapNotNull { eventNamesById[it] },
                    missingEventNames = missingEventNames,
                    occurrenceCount = componentOccurrences.size,
                    duplicateEventNames = duplicateEventNames
                )
            }
            .sortedWith(
                compareBy<DesktopEventSeriesCompetitorIdentityCoverageSummary>(
                    { it.duplicateEventNames.isEmpty() },
                    { it.missingEventNames.isEmpty() },
                    { it.identityLabel },
                    { it.competitorName }
                )
            )
    }

    fun startFairnessSummary(
        store: EventSeriesStore,
        manifestPath: Path,
        currentEventPath: Path?,
        currentProjectFile: EventProjectFile?
    ): DesktopEventSeriesStartFairnessSummary? {
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = currentEventPath?.toAbsolutePath()?.normalize() ?: return null
        val currentProject = currentProjectFile ?: return null
        val sortedEvents = seriesFile.sortedEvents()
        val eventsWithPaths = sortedEvents.map { event ->
            event to seriesFolder.resolve(event.eventFilePath).normalize()
        }
        val currentEvent = eventsWithPaths.firstOrNull { (_, path) ->
            path.toAbsolutePath().normalize() == normalizedCurrentPath
        }?.first ?: return null
        val eventStarts = eventsWithPaths
            .mapIndexedNotNull { index, (event, path) ->
                val isCurrentEvent = path.toAbsolutePath().normalize() == normalizedCurrentPath
                when {
                    isCurrentEvent -> event to startFairnessStarts(event, eventSequenceIndex = index, currentProject)
                    store.exists(path) -> event to startFairnessStarts(event, eventSequenceIndex = index, store.readEvent(path))
                    else -> null
                }
            }
        val generatedStarts = eventStarts.flatMap { it.second }
        val identifiedStarts = generatedStarts.filter { it.identityKey != null }
        val startThirdCounts = generatedStarts
            .groupingBy { it.startThird }
            .eachCount()
        val competitorHistories = identifiedStarts
            .groupBy { it.identityKey }
        val competitorFairnessRows = competitorHistories
            .map { (_, history) -> startFairnessCompetitorHistory(history) }
            .sortedWith(
                compareByDescending<DesktopEventSeriesStartFairnessCompetitorHistory> { it.isUneven }
                    .thenByDescending { it.spread }
                    .thenBy { it.competitorName }
            )
        val fairnessRating = startFairnessRating(competitorFairnessRows)
        val competitorsWithUnevenHistoryCount = competitorFairnessRows.count { it.isUneven }
        val balanceHistoryEvents = eventsWithPaths.filter { (event, _) ->
            event.seriesEventId != currentEvent.seriesEventId
        }
        val balanceHistoryRowsByEvent = balanceHistoryEvents
            .filter { (_, path) -> store.exists(path) }
            .map { (_, path) -> EventSeriesSupport.startRowsFromEventFile(store.readEvent(path)) }

        val currentCompetitors = currentProject.raceData.competitorData
            .map { it.competitorCategory.competitor }
        val balanceHistoryStartRows = balanceHistoryRowsByEvent.flatten()
        return DesktopEventSeriesStartFairnessSummary(
            seriesEventCount = sortedEvents.size,
            currentEventName = currentEvent.displayName,
            currentEventOrder = currentEvent.order,
            historyOrderDescription = if (seriesFile.usesDateTimeEventOrder()) {
                "race date/time"
            } else {
                "stored series order"
            },
            missingEventFileCount = eventsWithPaths.count { (_, path) ->
                path.toAbsolutePath().normalize() != normalizedCurrentPath && !store.exists(path)
            },
            lockedForOptimizationEventCount = eventsWithPaths.count { (_, path) ->
                when {
                    path.toAbsolutePath().normalize() == normalizedCurrentPath -> currentProject.isLockedForSeriesStartOptimization()
                    store.exists(path) -> store.readEvent(path).isLockedForSeriesStartOptimization()
                    else -> false
                }
            },
            unlockedForOptimizationEventCount = eventsWithPaths.count { (_, path) ->
                when {
                    path.toAbsolutePath().normalize() == normalizedCurrentPath -> !currentProject.isLockedForSeriesStartOptimization()
                    store.exists(path) -> !store.readEvent(path).isLockedForSeriesStartOptimization()
                    else -> false
                }
            },
            eventsWithGeneratedStartsCount = eventStarts.count { it.second.isNotEmpty() },
            eventsWithoutGeneratedStartsCount = sortedEvents.size - eventStarts.count { it.second.isNotEmpty() },
            generatedStartRowCount = generatedStarts.size,
            identifiedGeneratedStartRowCount = identifiedStarts.size,
            unidentifiedGeneratedStartRowCount = generatedStarts.size - identifiedStarts.size,
            competitorsWithIdentifiedHistoryCount = competitorHistories.size,
            competitorsWithUnevenHistoryCount = competitorsWithUnevenHistoryCount,
            fairnessNumber = fairnessRating.fairnessNumber,
            fairnessScoreCompetitorCount = fairnessRating.scoreableCompetitorCount,
            fairnessExcessSpread = fairnessRating.excessSpread,
            firstThirdStartCount = startThirdCounts[1].orZero(),
            middleThirdStartCount = startThirdCounts[2].orZero(),
            lateThirdStartCount = startThirdCounts[3].orZero(),
            competitorHistories = competitorFairnessRows,
            balanceHistoryEventCount = balanceHistoryEvents.size,
            missingBalanceHistoryEventFileCount = balanceHistoryEvents.count { (_, path) -> !store.exists(path) },
            balanceHistoryEventsWithStartsCount = balanceHistoryRowsByEvent.count { it.isNotEmpty() },
            balanceHistoryStartRowCount = balanceHistoryStartRows.size,
            identifiedBalanceHistoryStartRowCount = balanceHistoryStartRows.count {
                it.siNumber?.takeIf { value -> value > 0 } != null ||
                    it.bibNumber.isNotBlank() ||
                    ImportValidationRules.normalizedUniqueCallSign(it.callSign) != null
            },
            currentCompetitorCount = currentCompetitors.size,
            identifiedCurrentCompetitorCount = currentCompetitors.count {
                it.siNumber?.takeIf { value -> value > 0 } != null ||
                    it.bibNumber.isNotBlank() ||
                    ImportValidationRules.normalizedUniqueCallSign(it.callSign) != null
            }
        )
    }

    fun optimizeStartFairness(
        store: EventSeriesStore,
        manifestPath: Path,
        currentEventPath: Path?,
        maxPasses: Int = 3,
        candidatesPerEvent: Int = 32,
        seedSalt: String = "default"
    ): DesktopEventSeriesStartFairnessOptimizationResult {
        require(maxPasses > 0) {
            "Start fairness optimizer must run at least one pass."
        }
        require(candidatesPerEvent > 0) {
            "Start fairness optimizer must try at least one candidate per race."
        }
        val seriesFile = store.read(manifestPath)
        val seriesFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val normalizedCurrentPath = currentEventPath?.toAbsolutePath()?.normalize()
        val races = seriesFile.sortedEvents().map { event ->
            val path = seriesFolder.resolve(event.eventFilePath).normalize()
            require(store.exists(path)) {
                "Race File '${event.displayName}' is missing."
            }
            DesktopEventSeriesStartFairnessOptimizationEvent(event, path, store.readEvent(path))
        }.toMutableList()
        require(races.size >= 2) {
            "At least two Race Files are needed to optimize series start fairness."
        }

        val initialScore = startFairnessScore(races)
        var bestScore = initialScore
        val updatedIndexes = mutableSetOf<Int>()
        var acceptedCandidateCount = 0
        var attemptedCandidateCount = 0
        var completedPassCount = 0

        for (passIndex in 0 until maxPasses) {
            var passAcceptedCandidate = false
            races.indices.forEach { eventIndex ->
                val baseEvent = races[eventIndex]
                if (baseEvent.projectFile.isLockedForSeriesStartOptimization()) {
                    return@forEach
                }
                val baseStartAssignment = startAssignmentSignature(baseEvent.projectFile)
                val settings = baseEvent.projectFile.raceData.effectiveStartDrawSettings()
                var bestEvent = baseEvent
                var bestEventScore = bestScore
                var bestStartAssignment = baseStartAssignment
                repeat(candidatesPerEvent) { candidateIndex ->
                    attemptedCandidateCount++
                    val candidateSeed =
                        "series-opt-$seedSalt-${passIndex + 1}-${candidateIndex + 1}-${baseEvent.event.seriesEventId}"
                    val candidateProject = EventProjectEditor.drawStartList(
                        projectFile = baseEvent.projectFile,
                        intervalText = settings.intervalText,
                        options = settings.options.copy(seed = candidateSeed)
                    )
                    val candidateEvent = baseEvent.copy(projectFile = candidateProject)
                    val candidateScore = startFairnessScore(races.withReplacement(eventIndex, candidateEvent))
                    val candidateStartAssignment = startAssignmentSignature(candidateProject)
                    if (
                        candidateScore < bestEventScore ||
                        candidateScore == bestEventScore && candidateStartAssignment != bestStartAssignment
                    ) {
                        bestEvent = candidateEvent
                        bestEventScore = candidateScore
                        bestStartAssignment = candidateStartAssignment
                    }
                }
                if (bestEventScore < bestScore || bestEventScore == bestScore && bestStartAssignment != baseStartAssignment) {
                    races[eventIndex] = bestEvent
                    bestScore = bestEventScore
                    updatedIndexes += eventIndex
                    acceptedCandidateCount++
                    passAcceptedCandidate = true
                }
            }
            completedPassCount = passIndex + 1
            if (!passAcceptedCandidate) {
                break
            }
        }

        val updatedFiles = updatedIndexes.sorted().map { index ->
            val event = races[index]
            DesktopEventSeriesStartFairnessOptimizedEventFile(
                seriesEventId = event.event.seriesEventId,
                displayName = event.event.displayName,
                path = event.path,
                projectFile = event.projectFile,
                isCurrentEvent = normalizedCurrentPath != null && event.path.toAbsolutePath().normalize() == normalizedCurrentPath
            )
        }
        return DesktopEventSeriesStartFairnessOptimizationResult(
            initialScore = initialScore.value,
            finalScore = bestScore.value,
            initialUnevenHistoryCount = initialScore.unevenHistoryCount,
            finalUnevenHistoryCount = bestScore.unevenHistoryCount,
            initialSpreadSum = initialScore.spreadSum,
            finalSpreadSum = bestScore.spreadSum,
            attemptedCandidateCount = attemptedCandidateCount,
            acceptedCandidateCount = acceptedCandidateCount,
            completedPassCount = completedPassCount,
            optimizedEventCount = updatedFiles.size,
            updatedEventFiles = updatedFiles,
            solutionSignature = seriesStartAssignmentSignature(races)
        )
    }

    private fun startFairnessStarts(
        event: EventSeriesEvent,
        eventSequenceIndex: Int,
        projectFile: EventProjectFile
    ): List<DesktopEventSeriesStartFairnessStart> {
        val competitorsWithStarts = projectFile.raceData.competitorData
            .map { it.competitorCategory.competitor }
            .filter { it.drawnStartTimeSeconds != null }
            .sortedWith(
                compareBy<EventCompetitor>(
                    { requireNotNull(it.drawnStartTimeSeconds) },
                    { it.startNumber },
                    { it.fullName() }
                )
        )
        return competitorsWithStarts.mapIndexed { index, competitor ->
            val identity = EventSeriesSupport.primaryCompetitorIdentity(competitor)
            DesktopEventSeriesStartFairnessStart(
                identityKey = identity?.key,
                identityLabel = identity?.label ?: "Unidentified",
                competitorName = competitor.fullName(),
                eventSequenceIndex = eventSequenceIndex,
                startThird = startThirdForSlot(index, competitorsWithStarts.size)
            )
        }
    }

    private fun startThirdForSlot(slotIndex: Int, slotCount: Int): Int {
        if (slotCount <= 0) {
            return 1
        }
        return (((slotIndex * 3) / slotCount) + 1).coerceIn(1, 3)
    }

    private fun startFairnessCompetitorHistory(
        history: List<DesktopEventSeriesStartFairnessStart>
    ): DesktopEventSeriesStartFairnessCompetitorHistory {
        val firstThirdCount = history.count { it.startThird == 1 }
        val middleThirdCount = history.count { it.startThird == 2 }
        val lateThirdCount = history.count { it.startThird == 3 }
        val counts = listOf(firstThirdCount, middleThirdCount, lateThirdCount)
        val spread = counts.maxOrNull().orZero() - counts.minOrNull().orZero()
        val isUneven = history.size >= 2 && spread > 1
        return DesktopEventSeriesStartFairnessCompetitorHistory(
            competitorName = history.first().competitorName,
            identityLabel = history.first().identityLabel,
            generatedStartCount = history.size,
            thirdHistoryText = history
                .sortedBy { it.eventSequenceIndex }
                .joinToString(" ") { it.startThird.shortStartThirdLabel() },
            firstThirdCount = firstThirdCount,
            middleThirdCount = middleThirdCount,
            lateThirdCount = lateThirdCount,
            spread = spread,
            isUneven = isUneven,
            recommendation = startFairnessRecommendation(history.size, counts, isUneven)
        )
    }

    private fun startFairnessRecommendation(totalStarts: Int, counts: List<Int>, isUneven: Boolean): String {
        if (totalStarts < 2) {
            return "Needs more history"
        }
        if (!isUneven) {
            return "Balanced"
        }
        val minimum = counts.minOrNull().orZero()
        val neededThirds = listOf("early", "middle", "late")
            .filterIndexed { index, _ -> counts[index] == minimum }
            .joinToString("/")
        return "Prefer $neededThirds"
    }

    private fun startFairnessRating(
        histories: List<DesktopEventSeriesStartFairnessCompetitorHistory>
    ): DesktopEventSeriesStartFairnessRating {
        val scoreableHistories = histories.filter { it.generatedStartCount >= 2 }
        val possibleExcessSpread = scoreableHistories.sumOf {
            maximumExcessSpreadForStartCount(it.generatedStartCount)
        }
        val excessSpread = scoreableHistories.sumOf { history ->
            (history.spread - idealSpreadForStartCount(history.generatedStartCount)).coerceAtLeast(0)
        }
        val fairnessNumber = if (possibleExcessSpread <= 0) {
            0
        } else {
            (100 - ((excessSpread.toDouble() / possibleExcessSpread.toDouble()) * 100.0).roundToInt())
                .coerceIn(0, 100)
        }
        return DesktopEventSeriesStartFairnessRating(
            fairnessNumber = fairnessNumber,
            scoreableCompetitorCount = scoreableHistories.size,
            excessSpread = excessSpread
        )
    }

    private fun idealSpreadForStartCount(startCount: Int): Int =
        if (startCount % 3 == 0) 0 else 1

    private fun maximumExcessSpreadForStartCount(startCount: Int): Int =
        (startCount - idealSpreadForStartCount(startCount)).coerceAtLeast(0)

    private fun Int.shortStartThirdLabel(): String =
        when (this) {
            1 -> "E"
            2 -> "M"
            else -> "L"
        }

    private fun EventProjectFile.isLockedForSeriesStartOptimization(): Boolean =
        raceData.effectiveStartDrawSettings().lockedForSeriesOptimization

    private fun Int?.orZero(): Int = this ?: 0

    private fun startFairnessScore(
        races: List<DesktopEventSeriesStartFairnessOptimizationEvent>
    ): DesktopEventSeriesStartFairnessScore {
        val histories = races
            .flatMapIndexed { index, event ->
                startFairnessStarts(event.event, eventSequenceIndex = index, event.projectFile)
            }
            .filter { it.identityKey != null }
            .groupBy { it.identityKey }
            .values
        var unevenHistoryCount = 0
        var spreadSum = 0
        var squaredSpreadSum = 0
        histories.forEach { history ->
            val counts = (1..3).map { third -> history.count { it.startThird == third } }
            val spread = counts.maxOrNull().orZero() - counts.minOrNull().orZero()
            if (history.size >= 2 && spread > 1) {
                unevenHistoryCount++
            }
            spreadSum += spread
            squaredSpreadSum += spread * spread
        }
        return DesktopEventSeriesStartFairnessScore(
            unevenHistoryCount = unevenHistoryCount,
            spreadSum = spreadSum,
            squaredSpreadSum = squaredSpreadSum
        )
    }

    private fun <T> List<T>.withReplacement(index: Int, value: T): List<T> =
        mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

    private fun startAssignmentSignature(projectFile: EventProjectFile): List<Pair<String, Long?>> =
        projectFile.raceData.competitorData
            .map { data ->
                val competitor = data.competitorCategory.competitor
                competitor.id to competitor.drawnStartTimeSeconds
            }
            .sortedBy { it.first }

    private fun seriesStartAssignmentSignature(
        races: List<DesktopEventSeriesStartFairnessOptimizationEvent>
    ): String =
        races.joinToString("|") { event ->
            event.event.seriesEventId + ":" +
                startAssignmentSignature(event.projectFile)
                    .joinToString(",") { (competitorId, startSeconds) -> "$competitorId=${startSeconds ?: "none"}" }
        }

    fun createSeriesWithEvent(
        seriesFolder: Path,
        seriesId: String,
        seriesName: String = DEFAULT_SERIES_NAME,
        eventPath: Path,
        eventProjectFile: EventProjectFile,
        seriesEventId: String = eventProjectFile.raceData.race.id
    ): DesktopEventSeriesCreateResult {
        val manifestPath = manifestPathForSeriesName(
            seriesFolder = seriesFolder,
            seriesName = seriesName,
            fallbackName = eventProjectFile.raceData.race.name
        )
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

    /** Updates the manifest-owned name shown in Series-level desktop workflow chrome. */
    fun renameSeries(seriesFile: EventSeriesFile, seriesName: String): EventSeriesFile {
        val trimmedName = seriesName.trim()
        require(trimmedName.isNotBlank()) {
            "Series name must not be blank."
        }
        return seriesFile.copy(name = trimmedName)
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
        // Re-selecting an existing Race File refreshes its manifest metadata without changing its order.
        val updatedSeriesFile = seriesFile.copy(
            events = seriesFile.events.filterNot {
                it.seriesEventId == resolvedSeriesEventId || it.eventFilePath == relativeEventPath
            } + event
        )
        val linkedProjectFile = EventProjectEditor.updateSeriesLink(eventProjectFile, seriesFile.seriesId, resolvedSeriesEventId)
        return DesktopEventSeriesLinkResult(updatedSeriesFile, linkedProjectFile)
    }

    fun refreshLinkedEventMetadata(
        seriesFile: EventSeriesFile,
        eventPath: Path,
        seriesFolder: Path,
        eventProjectFile: EventProjectFile
    ): EventSeriesFile {
        val link = eventProjectFile.seriesLink ?: return seriesFile
        if (link.seriesId != seriesFile.seriesId) {
            return seriesFile
        }
        val relativeEventPath = relativeEventPath(seriesFolder, eventPath)
        val existingEvent = seriesFile.events.firstOrNull { it.seriesEventId == link.seriesEventId }
            ?: seriesFile.events.firstOrNull { it.eventFilePath == relativeEventPath }
            ?: return seriesFile
        val conflictingPath = seriesFile.events.firstOrNull {
            it.seriesEventId != existingEvent.seriesEventId && it.eventFilePath == relativeEventPath
        }
        require(conflictingPath == null) {
            "Cannot refresh Race Series metadata because ${relativeEventPath} is already assigned to ${conflictingPath?.displayName}."
        }
        val race = eventProjectFile.raceData.race
        val refreshedEvent = existingEvent.copy(
            eventFilePath = relativeEventPath,
            displayName = race.name,
            startDateTimeIso = race.startDateTimeIso,
            formatLabel = race.raceType.name
        )
        // The manifest carries planning metadata for the workflow, but the Race File remains the
        // race-day source of truth. Refreshing on save lets harmless Event Name/date tweaks flow into
        // series screens without asking organizers to manually edit the manifest.
        return seriesFile.copy(
            events = seriesFile.events.map { event ->
                if (event.seriesEventId == existingEvent.seriesEventId) refreshedEvent else event
            }
        )
    }

    fun removeCurrentEvent(seriesFile: EventSeriesFile, eventProjectFile: EventProjectFile): DesktopEventSeriesLinkResult {
        val link = eventProjectFile.seriesLink ?: return DesktopEventSeriesLinkResult(seriesFile, eventProjectFile)
        val updatedSeriesFile = if (link.seriesId == seriesFile.seriesId) {
            seriesFile.copy(
                events = seriesFile.events.filterNot { it.seriesEventId == link.seriesEventId },
                competitorMatchOverrides = seriesFile.competitorMatchOverrides.filter { override ->
                    override.fromSeriesEventId != link.seriesEventId &&
                        override.toSeriesEventId != link.seriesEventId
                }
            )
        } else {
            seriesFile
        }
        return DesktopEventSeriesLinkResult(updatedSeriesFile, EventProjectEditor.removeSeriesLink(eventProjectFile))
    }

    fun exportSeries(store: EventSeriesStore, manifestPath: Path, targetFolder: Path): EventSeriesExportResult {
        val seriesFile = store.read(manifestPath)
        val sourceFolder = requireNotNull(manifestPath.parent) {
            "Race Series manifest must have a parent folder."
        }
        val missingFiles = seriesFile.sortedEvents()
            .map { it to sourceFolder.resolve(it.eventFilePath).normalize() }
            .filterNot { (_, path) -> store.exists(path) }
            .map { (event, _) -> event.eventFilePath }
        require(missingFiles.isEmpty()) {
            "Cannot export Race Series because required Race Files are missing: ${missingFiles.joinToString()}"
        }

        val targetManifest = targetFolder.resolve(manifestPath.fileName)
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

    private fun eventSeriesManifestCandidates(directory: Path, exists: (Path) -> Boolean): List<Path> {
        val legacyManifest = directory.resolve(EVENT_SERIES_FILE_NAME).takeIf(exists)
        /*
         * Folder contents are not authoritative series membership. This scan only finds
         * possible manifest containers; the manifest entries still decide which Race Files
         * belong to the series, and unrelated JSON clutter is ignored.
         */
        val namedManifests = runCatching {
            val stream = Files.list(directory)
            try {
                stream.iterator().asSequence()
                    .filter { Files.isRegularFile(it) }
                    .filter { isEventSeriesFileName(it.fileName.toString()) }
                    .filterNot { it.fileName.toString() == EVENT_SERIES_FILE_NAME }
                    .sortedBy { it.fileName.toString().lowercase() }
                    .toList()
            } finally {
                stream.close()
            }
        }.getOrDefault(emptyList())
        return listOfNotNull(legacyManifest) + namedManifests
    }

    private fun seriesFileStem(name: String): String =
        name
            .trim()
            .map { character ->
                if (character.isISOControl() || character in """\/:*?"<>|""") ' ' else character
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Race Series" }

    private fun relativeEventPath(seriesFolder: Path, eventPath: Path): String {
        val normalizedFolder = seriesFolder.toAbsolutePath().normalize()
        val normalizedEventPath = eventPath.toAbsolutePath().normalize()
        require(normalizedEventPath.startsWith(normalizedFolder)) {
            "Race File must be inside the Race Series folder before it can be added to the manifest."
        }
        return normalizedFolder.relativize(normalizedEventPath).toString().replace('\\', '/')
    }

    private fun uniqueSeriesEventId(seriesFile: EventSeriesFile, preferredId: String, relativeEventPath: String): String {
        val usedIds = seriesFile.events.map { it.seriesEventId }.toSet()
        val normalizedPreferredId = preferredId.trim().ifBlank { "event" }
        if (normalizedPreferredId !in usedIds) {
            return normalizedPreferredId
        }
        // Older copied Race Files can share the same race id. In a series, membership is per
        // Race File, so duplicate race ids on different paths need distinct series race ids.
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

private data class DesktopEventSeriesCompetitorIdentityOccurrence(
    val seriesEventId: String,
    val eventName: String,
    val competitorName: String
)

private class StringUnionFind {
    private val parent = mutableMapOf<String, String>()

    fun add(value: String) {
        parent.putIfAbsent(value, value)
    }

    fun union(first: String, second: String) {
        add(first)
        add(second)
        parent[find(second)] = find(first)
    }

    fun find(value: String): String {
        add(value)
        val currentParent = parent.getValue(value)
        return if (currentParent == value) {
            value
        } else {
            find(currentParent).also { parent[value] = it }
        }
    }

    fun nodes(): Set<String> =
        parent.keys
}

private fun identityOccurrenceNode(seriesEventId: String, competitorId: String): String =
    "occurrence:$seriesEventId:$competitorId"

private fun identityNode(identityKey: String): String =
    "identity:$identityKey"

private fun mostCommonCompetitorName(occurrences: List<DesktopEventSeriesCompetitorIdentityOccurrence>): String =
    occurrences
        .groupingBy { it.competitorName }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
        ?.key
        .orEmpty()

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
    val displayPosition: Int,
    val eventFilePath: String,
    val resolvedPath: Path,
    val exists: Boolean,
    val isCurrentEvent: Boolean,
    val startDateTimeIso: String? = null,
    val formatLabel: String? = null
)

data class DesktopEventSeriesCompetitorMatchSummary(
    val firstEventName: String,
    val firstSeriesEventId: String,
    val firstCompetitorCount: Int,
    val secondEventName: String,
    val secondSeriesEventId: String,
    val secondCompetitorCount: Int,
    val includesCurrentEvent: Boolean,
    val matchCount: Int,
    val siNumberMatchCount: Int,
    val bibNumberMatchCount: Int,
    val callSignMatchCount: Int,
    val overrideMatchCount: Int,
    val issueCount: Int
)

data class DesktopEventSeriesCompetitorIdentityCoverageSummary(
    val identityLabel: String,
    val competitorName: String,
    val presentEventCount: Int,
    val totalReadableEventCount: Int,
    val presentEventNames: List<String>,
    val missingEventNames: List<String>,
    val occurrenceCount: Int,
    val duplicateEventNames: List<String>
)

private data class DesktopEventSeriesStartFairnessStart(
    val identityKey: String?,
    val identityLabel: String,
    val competitorName: String,
    val eventSequenceIndex: Int,
    val startThird: Int
)

data class DesktopEventSeriesStartFairnessCompetitorHistory(
    val competitorName: String,
    val identityLabel: String,
    val generatedStartCount: Int,
    val thirdHistoryText: String,
    val firstThirdCount: Int,
    val middleThirdCount: Int,
    val lateThirdCount: Int,
    val spread: Int,
    val isUneven: Boolean,
    val recommendation: String
)

private data class DesktopEventSeriesStartFairnessRating(
    val fairnessNumber: Int,
    val scoreableCompetitorCount: Int,
    val excessSpread: Int
)

private data class DesktopEventSeriesStartFairnessOptimizationEvent(
    val event: EventSeriesEvent,
    val path: Path,
    val projectFile: EventProjectFile
)

private data class DesktopEventSeriesStartFairnessScore(
    val unevenHistoryCount: Int,
    val spreadSum: Int,
    val squaredSpreadSum: Int
) : Comparable<DesktopEventSeriesStartFairnessScore> {
    val value: Int =
        unevenHistoryCount * 100_000 +
            spreadSum * 1_000 +
            squaredSpreadSum

    override fun compareTo(other: DesktopEventSeriesStartFairnessScore): Int =
        value.compareTo(other.value)
}

data class DesktopEventSeriesStartFairnessOptimizedEventFile(
    val seriesEventId: String,
    val displayName: String,
    val path: Path,
    val projectFile: EventProjectFile,
    val isCurrentEvent: Boolean
)

data class DesktopEventSeriesStartFairnessSolutionNumbering(
    val solutionNumber: Int,
    val repeatedSolution: Boolean,
    val solutionNumbers: Map<String, Int>
)

data class DesktopEventSeriesStartFairnessOptimizationResult(
    val initialScore: Int,
    val finalScore: Int,
    val initialUnevenHistoryCount: Int,
    val finalUnevenHistoryCount: Int,
    val initialSpreadSum: Int,
    val finalSpreadSum: Int,
    val attemptedCandidateCount: Int,
    val acceptedCandidateCount: Int,
    val completedPassCount: Int,
    val optimizedEventCount: Int,
    val updatedEventFiles: List<DesktopEventSeriesStartFairnessOptimizedEventFile>,
    val solutionSignature: String,
    val solutionNumber: Int? = null,
    val repeatedSolution: Boolean = false
) {
    val improved: Boolean
        get() = finalScore < initialScore

    val alternateSolution: Boolean
        get() = finalScore == initialScore && updatedEventFiles.isNotEmpty()
}

data class DesktopEventSeriesStartFairnessSummary(
    val seriesEventCount: Int,
    val currentEventName: String,
    val currentEventOrder: Int,
    val historyOrderDescription: String,
    val missingEventFileCount: Int,
    val lockedForOptimizationEventCount: Int,
    val unlockedForOptimizationEventCount: Int,
    val eventsWithGeneratedStartsCount: Int,
    val eventsWithoutGeneratedStartsCount: Int,
    val generatedStartRowCount: Int,
    val identifiedGeneratedStartRowCount: Int,
    val unidentifiedGeneratedStartRowCount: Int,
    val competitorsWithIdentifiedHistoryCount: Int,
    val competitorsWithUnevenHistoryCount: Int,
    val fairnessNumber: Int,
    val fairnessScoreCompetitorCount: Int,
    val fairnessExcessSpread: Int,
    val firstThirdStartCount: Int,
    val middleThirdStartCount: Int,
    val lateThirdStartCount: Int,
    val competitorHistories: List<DesktopEventSeriesStartFairnessCompetitorHistory>,
    val balanceHistoryEventCount: Int,
    val missingBalanceHistoryEventFileCount: Int,
    val balanceHistoryEventsWithStartsCount: Int,
    val balanceHistoryStartRowCount: Int,
    val identifiedBalanceHistoryStartRowCount: Int,
    val currentCompetitorCount: Int,
    val identifiedCurrentCompetitorCount: Int
)

internal fun hasDesktopEventSeriesContext(
    projectFile: EventProjectFile?,
    summaries: List<DesktopEventSeriesEventSummary>
): Boolean {
    val openProjectFile = projectFile ?: return false
    val currentSummary = summaries.firstOrNull { it.isCurrentEvent } ?: return false
    // The manifest is authoritative. Missing backlinks should not hide the Series workflow,
    // because validation and settings tools are needed to diagnose and repair that state.
    return openProjectFile.seriesLink?.seriesEventId?.let { it == currentSummary.seriesEventId } != false
}
