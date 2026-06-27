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

package org.openardf.radiooracle.backend.files

import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesPackageEntryKind
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Prepared Android import for a desktop-authored Event Series package. */
data class AndroidEventSeriesImport(
    val series: EventSeries,
    val memberImports: List<AndroidEventSeriesMemberImport>
) {
    val members: List<EventSeriesMember> get() = memberImports.map { it.member }
    val races: List<RaceData> get() = memberImports.map { it.raceData }
}

/** One imported series member Event File plus its Android-local mapping row. */
data class AndroidEventSeriesMemberImport(
    val member: EventSeriesMember,
    val raceData: RaceData
)

/** Event Series package contents extracted from a desktop-created zip file. */
data class AndroidEventSeriesPackage(
    val manifestJson: String,
    val eventFileJsonByPath: Map<String, String>
)

/** Converts a manifest plus member Event Files into Android-local race imports and series mappings. */
object EventSeriesImport {
    fun prepareZipPackage(inputStream: InputStream): AndroidEventSeriesImport {
        val eventSeriesPackage = readZipPackage(inputStream)
        return prepare(eventSeriesPackage.manifestJson, eventSeriesPackage.eventFileJsonByPath)
    }

    fun readZipPackage(inputStream: InputStream): AndroidEventSeriesPackage {
        var manifestEntryPath: String? = null
        var manifestJson: String? = null
        val jsonEntries = mutableMapOf<String, String>()

        ZipInputStream(inputStream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val packageEntry = EventSeriesPackageContents.classifyEntryPath(entry.name)
                    if (packageEntry.kind != EventSeriesPackageEntryKind.IGNORED) {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        when (packageEntry.kind) {
                            EventSeriesPackageEntryKind.MANIFEST -> {
                                require(manifestJson == null) {
                                    "Event Series package contains more than one manifest."
                                }
                                manifestEntryPath = packageEntry.path
                                manifestJson = text
                            }

                            EventSeriesPackageEntryKind.EVENT_FILE -> {
                                jsonEntries[packageEntry.path] = text
                            }

                            EventSeriesPackageEntryKind.IGNORED -> Unit
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        val resolvedManifestPath = manifestEntryPath
            ?: throw IllegalArgumentException("Event Series package does not contain a series manifest.")
        val manifestFolder = resolvedManifestPath.substringBeforeLast('/', missingDelimiterValue = "")
        val eventFileJsonByPath = jsonEntries.mapKeys { (entryPath, _) ->
            if (manifestFolder.isBlank()) {
                entryPath
            } else {
                entryPath.removePrefix("$manifestFolder/")
            }
        }
        return AndroidEventSeriesPackage(
            manifestJson = requireNotNull(manifestJson),
            eventFileJsonByPath = eventFileJsonByPath
        )
    }

    fun prepare(
        manifestJson: String,
        eventFileJsonByPath: Map<String, String>
    ): AndroidEventSeriesImport {
        val seriesFile = EventSeriesFileJson.decode(manifestJson)
        val eventFilesByPath = eventFileJsonByPath
            .mapKeys { (path, _) -> EventSeriesPackageContents.normalizedPackagePath(path) }
        val memberImports = seriesFile.sortedEvents().map { event ->
            val eventJson = eventFilesByPath[EventSeriesPackageContents.normalizedPackagePath(event.eventFilePath)]
                ?: throw IllegalArgumentException("Missing Event File for series event '${event.displayName}'.")
            val projectFile = EventProjectFileJson.decode(eventJson)
            validateSeriesLink(projectFile, seriesFile.seriesId, event)
            val raceData = projectFile.raceData
                .toRoomRaceData()
                .withSeriesImportIdentity(
                    seriesId = seriesFile.seriesId,
                    seriesEventId = event.seriesEventId,
                    eventFileRaceId = projectFile.raceData.race.id,
                    fingerprint = eventJson.sha256Hex()
                )
                .withFreshImportIds()
            AndroidEventSeriesMemberImport(
                member = EventSeriesMember(
                    seriesId = seriesFile.seriesId,
                    seriesEventId = event.seriesEventId,
                    localRaceId = raceData.race.id,
                    eventFilePath = event.eventFilePath,
                    eventOrder = event.order,
                    displayName = event.displayName,
                    startDateTimeIso = event.startDateTimeIso,
                    formatLabel = event.formatLabel
                ),
                raceData = raceData
            )
        }

        return AndroidEventSeriesImport(
            series = EventSeries(
                seriesId = seriesFile.seriesId,
                name = seriesFile.name
            ),
            memberImports = memberImports
        )
    }

    private fun validateSeriesLink(
        projectFile: EventProjectFile,
        seriesId: String,
        event: EventSeriesEvent
    ) {
        val link = projectFile.seriesLink ?: return
        require(link.seriesId == seriesId && link.seriesEventId == event.seriesEventId) {
            "Event File '${event.displayName}' links to a different Event Series member."
        }
    }
}

private fun RaceData.withSeriesImportIdentity(
    seriesId: String,
    seriesEventId: String,
    eventFileRaceId: String,
    fingerprint: String
): RaceData {
    race.importSourceId = "event-series:$seriesId:$seriesEventId:event-file:$eventFileRaceId"
    race.importFingerprint = fingerprint
    return this
}

private fun String.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
