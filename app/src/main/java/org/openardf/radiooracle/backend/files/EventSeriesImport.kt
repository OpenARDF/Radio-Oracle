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
import org.openardf.radiooracle.shared.event.EventSeriesArchive
import org.openardf.radiooracle.shared.event.EventSeriesArchiveZipCodec
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import java.io.InputStream
import java.security.MessageDigest

/** Prepared Android import for a desktop-authored Race Series package. */
data class AndroidEventSeriesImport(
    val series: EventSeries,
    val memberImports: List<AndroidEventSeriesMemberImport>
) {
    val members: List<EventSeriesMember> get() = memberImports.map { it.member }
    val races: List<RaceData> get() = memberImports.map { it.raceData }
}

/** One imported series member Race File plus its Android-local mapping row. */
data class AndroidEventSeriesMemberImport(
    val member: EventSeriesMember,
    val raceData: RaceData
)

/** Race Series package contents extracted from a desktop-created zip file. */
data class AndroidEventSeriesPackage(
    val manifestJson: String,
    val eventFileJsonByPath: Map<String, String>
)

/** Converts a manifest plus member Race Files into Android-local race imports and series mappings. */
object EventSeriesImport {
    fun prepareZipPackage(inputStream: InputStream): AndroidEventSeriesImport {
        return prepare(EventSeriesArchiveZipCodec.read(inputStream))
    }

    fun readZipPackage(inputStream: InputStream): AndroidEventSeriesPackage {
        val archive = EventSeriesArchiveZipCodec.read(inputStream)
        return AndroidEventSeriesPackage(
            manifestJson = EventSeriesFileJson.encode(archive.seriesFile),
            eventFileJsonByPath = archive.seriesFile.events.associate { event ->
                event.eventFilePath to EventProjectFileJson.encode(archive.member(event.seriesEventId))
            }
        )
    }

    fun prepare(
        manifestJson: String,
        eventFileJsonByPath: Map<String, String>
    ): AndroidEventSeriesImport =
        prepare(
            EventSeriesArchiveZipCodec.decodeEntries(
                buildList {
                    add(
                        org.openardf.radiooracle.shared.event.EventSeriesPackageTextEntry(
                            path = org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME,
                            text = manifestJson
                        )
                    )
                    eventFileJsonByPath.forEach { (path, text) ->
                        add(
                            org.openardf.radiooracle.shared.event.EventSeriesPackageTextEntry(
                                path = path,
                                text = text
                            )
                        )
                    }
                }
            )
        )

    fun prepare(archive: EventSeriesArchive): AndroidEventSeriesImport {
        val seriesFile = archive.seriesFile
        val memberImports = seriesFile.sortedEvents().map { event ->
            val projectFile = archive.member(event.seriesEventId)
            val eventJson = EventProjectFileJson.encode(projectFile)
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
            "Race File '${event.displayName}' links to a different Race Series member."
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
