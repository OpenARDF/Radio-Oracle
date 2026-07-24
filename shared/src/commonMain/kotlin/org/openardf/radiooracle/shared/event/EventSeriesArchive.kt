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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.shared.event

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val EVENT_SERIES_ARCHIVE_FILE_SUFFIX = ".roseries"
const val EVENT_SERIES_ARCHIVE_CONTENT_TYPE = "application/vnd.openardf.radio-oracle-series+zip"
const val LEGACY_EVENT_SERIES_ARCHIVE_CONTENT_TYPE = "application/zip"

fun isEventSeriesArchiveFileName(fileName: String): Boolean =
    fileName.endsWith(EVENT_SERIES_ARCHIVE_FILE_SUFFIX, ignoreCase = true)

/**
 * Complete, portable Race Series aggregate.
 *
 * Consumers address races by stable series-event id. Entry paths and ZIP mechanics stay inside
 * this aggregate and its codec, so desktop and Android apply the same membership rules.
 */
data class EventSeriesArchive(
    val seriesFile: EventSeriesFile,
    val membersBySeriesEventId: Map<String, EventProjectFile>,
    val manifestEntryPath: String = EVENT_SERIES_FILE_NAME
) {
    init {
        EventSeriesPackageContents.normalizedPackagePath(manifestEntryPath)
        require(isEventSeriesFileName(manifestEntryPath.substringAfterLast('/'))) {
            "Race Series archive manifest must use a Radio-Oracle series manifest name."
        }
        val expectedIds = seriesFile.events.mapTo(linkedSetOf()) { it.seriesEventId }
        require(expectedIds.size == seriesFile.events.size) {
            "Race Series archive manifest contains duplicate race ids."
        }
        val normalizedEventPaths = seriesFile.events.map { event ->
            EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)
        }
        require(normalizedEventPaths.toSet().size == normalizedEventPaths.size) {
            "Race Series archive manifest contains duplicate Race File paths."
        }
        val manifestFolder = manifestEntryPath.substringBeforeLast('/', missingDelimiterValue = "")
        require(normalizedEventPaths.none { eventPath ->
            val archivePath = if (manifestFolder.isBlank()) eventPath else "$manifestFolder/$eventPath"
            EventSeriesPackageContents.normalizedPackagePath(archivePath) ==
                EventSeriesPackageContents.normalizedPackagePath(manifestEntryPath)
        }) {
            "Race Series archive Race File path conflicts with its manifest."
        }
        val actualIds = membersBySeriesEventId.keys
        require(actualIds == expectedIds) {
            val missing = expectedIds - actualIds
            val extra = actualIds - expectedIds
            buildString {
                append("Race Series archive membership does not match its manifest.")
                if (missing.isNotEmpty()) append(" Missing: ${missing.joinToString()}.")
                if (extra.isNotEmpty()) append(" Unexpected: ${extra.joinToString()}.")
            }
        }
        seriesFile.events.forEach { event ->
            EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)
            val projectFile = requireNotNull(membersBySeriesEventId[event.seriesEventId])
            val link = projectFile.seriesLink
            require(
                link == null ||
                    (link.seriesId == seriesFile.seriesId && link.seriesEventId == event.seriesEventId)
            ) {
                "Race File '${event.displayName}' links to a different Race Series member."
            }
        }
    }

    val memberCount: Int get() = seriesFile.events.size

    fun member(seriesEventId: String): EventProjectFile =
        requireNotNull(membersBySeriesEventId[seriesEventId]) {
            "Race Series member not found: $seriesEventId"
        }

    /** Returns a storage-normalized archive with an exact backlink in every member Race File. */
    fun normalizedForStorage(): EventSeriesArchive {
        val normalizedMembers = seriesFile.events.associate { event ->
            event.seriesEventId to EventProjectFileJson.normalizedForStorage(
                EventProjectEditor.updateSeriesLink(
                    member(event.seriesEventId),
                    seriesFile.seriesId,
                    event.seriesEventId
                )
            )
        }
        return copy(
            membersBySeriesEventId = normalizedMembers,
            manifestEntryPath = EventSeriesPackageContents.normalizedPackagePath(manifestEntryPath)
        )
    }

    fun updateMember(seriesEventId: String, projectFile: EventProjectFile): EventSeriesArchive {
        member(seriesEventId)
        val linkedProjectFile = EventProjectEditor.updateSeriesLink(
            projectFile,
            seriesFile.seriesId,
            seriesEventId
        )
        val existingEvent = requireNotNull(seriesFile.events.firstOrNull { it.seriesEventId == seriesEventId })
        val race = linkedProjectFile.raceData.race
        val updatedEvent = existingEvent.copy(
            displayName = race.name,
            startDateTimeIso = race.startDateTimeIso,
            formatLabel = race.raceType.name
        )
        return copy(
            seriesFile = seriesFile.copy(
                events = seriesFile.events.map { event ->
                    if (event.seriesEventId == seriesEventId) updatedEvent else event
                }
            ),
            membersBySeriesEventId = membersBySeriesEventId + (seriesEventId to linkedProjectFile)
        )
    }

    fun addMember(event: EventSeriesEvent, projectFile: EventProjectFile): EventSeriesArchive {
        require(seriesFile.events.none { it.seriesEventId == event.seriesEventId }) {
            "Race Series already contains race id ${event.seriesEventId}."
        }
        require(seriesFile.events.none { it.eventFilePath == event.eventFilePath }) {
            "Race Series already contains Race File path ${event.eventFilePath}."
        }
        val linkedProjectFile = EventProjectEditor.updateSeriesLink(
            projectFile,
            seriesFile.seriesId,
            event.seriesEventId
        )
        return copy(
            seriesFile = seriesFile.copy(events = seriesFile.events + event),
            membersBySeriesEventId = membersBySeriesEventId + (event.seriesEventId to linkedProjectFile)
        )
    }

    /**
     * Detaches one member as a standalone Race File. Removing the final member deletes the logical
     * series, represented by a null [remainingArchive].
     */
    fun removeMember(seriesEventId: String): EventSeriesArchiveMemberRemoval {
        val removedEvent = requireNotNull(seriesFile.events.firstOrNull { it.seriesEventId == seriesEventId }) {
            "Race Series member not found: $seriesEventId"
        }
        val detachedProjectFile = EventProjectEditor.removeSeriesLink(member(seriesEventId))
        val remainingEvents = seriesFile.events.filterNot { it.seriesEventId == seriesEventId }
        val remainingArchive = if (remainingEvents.isEmpty()) {
            null
        } else {
            val remainingEventIds = remainingEvents.mapTo(hashSetOf()) { it.seriesEventId }
            copy(
                seriesFile = seriesFile.copy(
                    events = remainingEvents,
                    competitorMatchOverrides = seriesFile.competitorMatchOverrides.filter { override ->
                        override.fromSeriesEventId in remainingEventIds &&
                            override.toSeriesEventId in remainingEventIds
                    }
                ),
                membersBySeriesEventId = membersBySeriesEventId - seriesEventId
            )
        }
        return EventSeriesArchiveMemberRemoval(
            remainingArchive = remainingArchive,
            removedEvent = removedEvent,
            detachedProjectFile = detachedProjectFile
        )
    }

    fun packageContent(packageFileNameStem: String = seriesFile.name): EventSeriesPackageContent {
        val normalizedArchive = normalizedForStorage()
        val manifestFolder = normalizedArchive.manifestEntryPath.substringBeforeLast('/', missingDelimiterValue = "")
        val entries = buildList {
            add(
                EventSeriesPackageTextEntry(
                    path = normalizedArchive.manifestEntryPath,
                    text = EventSeriesFileJson.encode(normalizedArchive.seriesFile)
                )
            )
            normalizedArchive.seriesFile.sortedEvents().forEach { event ->
                val relativePath = EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)
                val entryPath = if (manifestFolder.isBlank()) {
                    relativePath
                } else {
                    "$manifestFolder/$relativePath"
                }
                add(
                    EventSeriesPackageTextEntry(
                        path = EventSeriesPackageContents.normalizedPackagePath(entryPath),
                        text = EventProjectFileJson.encode(normalizedArchive.member(event.seriesEventId))
                    )
                )
            }
        }
        return EventSeriesPackageContent(
            fileName = "${EventSeriesPackageContents.safePackageFileStem(packageFileNameStem)}$EVENT_SERIES_ARCHIVE_FILE_SUFFIX",
            entries = entries
        )
    }
}

data class EventSeriesArchiveMemberRemoval(
    val remainingArchive: EventSeriesArchive?,
    val removedEvent: EventSeriesEvent,
    val detachedProjectFile: EventProjectFile
)

/** Shared ZIP codec used by both desktop and Android Race Series persistence adapters. */
object EventSeriesArchiveZipCodec {
    private const val MAX_ENTRY_COUNT = 512
    private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L
    private const val MAX_ARCHIVE_TEXT_BYTES = 256L * 1024L * 1024L

    fun encode(archive: EventSeriesArchive): ByteArray =
        ByteArrayOutputStream().use { output ->
            write(archive, output)
            output.toByteArray()
        }

    fun write(archive: EventSeriesArchive, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            archive.packageContent().entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.path))
                zip.write(entry.text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    fun decode(bytes: ByteArray): EventSeriesArchive =
        ByteArrayInputStream(bytes).use(::read)

    fun read(input: InputStream): EventSeriesArchive {
        val entries = mutableListOf<EventSeriesPackageTextEntry>()
        val normalizedPaths = hashSetOf<String>()
        var totalBytes = 0L
        var entryCount = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= MAX_ENTRY_COUNT) {
                    "Race Series archive contains too many files."
                }
                if (!entry.isDirectory) {
                    val packageEntry = EventSeriesPackageContents.classifyEntryPath(entry.name)
                    if (packageEntry.kind != EventSeriesPackageEntryKind.IGNORED) {
                        require(normalizedPaths.add(packageEntry.path)) {
                            "Race Series archive contains duplicate path ${packageEntry.path}."
                        }
                        val textBytes = zip.readEntryBytes(MAX_ENTRY_BYTES)
                        totalBytes += textBytes.size
                        require(totalBytes <= MAX_ARCHIVE_TEXT_BYTES) {
                            "Race Series archive expands beyond the supported size."
                        }
                        entries += EventSeriesPackageTextEntry(
                            path = packageEntry.path,
                            text = textBytes.toString(Charsets.UTF_8)
                        )
                    }
                }
                zip.closeEntry()
            }
        }
        return decodeEntries(entries)
    }

    fun decodeEntries(entries: List<EventSeriesPackageTextEntry>): EventSeriesArchive {
        val manifestEntries = entries.filter {
            EventSeriesPackageContents.classifyEntryPath(it.path).kind == EventSeriesPackageEntryKind.MANIFEST
        }
        require(manifestEntries.size == 1) {
            if (manifestEntries.isEmpty()) {
                "Race Series archive does not contain a series manifest."
            } else {
                "Race Series archive contains more than one series manifest."
            }
        }
        val manifestEntry = manifestEntries.single()
        val manifestPath = EventSeriesPackageContents.normalizedPackagePath(manifestEntry.path)
        val manifestFolder = manifestPath.substringBeforeLast('/', missingDelimiterValue = "")
        val seriesFile = EventSeriesFileJson.decode(manifestEntry.text)
        val eventEntryList = entries
            .filter {
                EventSeriesPackageContents.classifyEntryPath(it.path).kind == EventSeriesPackageEntryKind.EVENT_FILE
            }
        val eventEntries = eventEntryList.associateBy {
            EventSeriesPackageContents.normalizedPackagePath(it.path)
        }
        require(eventEntries.size == eventEntryList.size) {
            "Race Series archive contains duplicate Race File paths."
        }
        val usedEventPaths = hashSetOf<String>()
        val members = seriesFile.events.associate { event ->
            val relativePath = EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)
            val nestedPath = if (manifestFolder.isBlank()) relativePath else "$manifestFolder/$relativePath"
            val normalizedNestedPath = EventSeriesPackageContents.normalizedPackagePath(nestedPath)
            val entryPath = when {
                normalizedNestedPath in eventEntries -> normalizedNestedPath
                relativePath in eventEntries -> relativePath
                else -> null
            }
            val entry = entryPath?.let(eventEntries::get)
                ?: throw IllegalArgumentException(
                    "Missing Race File for series race '${event.displayName}'."
                )
            usedEventPaths += requireNotNull(entryPath)
            event.seriesEventId to EventProjectFileJson.decode(entry.text)
        }
        require(usedEventPaths.size == eventEntries.size) {
            val unexpectedPaths = eventEntries.keys - usedEventPaths
            "Race Series archive contains unlisted Race Files: ${unexpectedPaths.joinToString()}."
        }
        return EventSeriesArchive(
            seriesFile = seriesFile,
            membersBySeriesEventId = members,
            manifestEntryPath = manifestPath
        )
    }

    private fun InputStream.readEntryBytes(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) {
                "Race Series archive entry expands beyond the supported size."
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
