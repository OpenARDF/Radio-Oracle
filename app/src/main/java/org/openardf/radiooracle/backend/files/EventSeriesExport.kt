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

import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.EventSeriesPackageContents
import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFile
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes Android-local Event Series data as a desktop-importable series package. */
object EventSeriesExport {
    fun packageBytes(
        seriesData: EventSeriesData,
        raceDataById: Map<UUID, RaceData>
    ): ByteArray {
        val members = seriesData.orderedMembers()
        require(members.isNotEmpty()) {
            "Event Series export requires at least one event."
        }
        val seriesFile = EventSeriesFile(
            seriesId = seriesData.series.seriesId,
            name = seriesData.series.name,
            events = members.map { member -> member.toEventSeriesEvent() }
        )

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                val eventFiles = members.map { member ->
                    val raceData = raceDataById[member.localRaceId]
                        ?: throw IllegalArgumentException("Missing Event data for '${member.displayName}'.")
                    EventSeriesPackageEventFile(
                        event = member.toEventSeriesEvent(),
                        projectFile =
                            EventProjectFile(
                                raceData = raceData.toEventRaceData(),
                                seriesLink = EventSeriesLink(
                                    seriesId = member.seriesId,
                                    seriesEventId = member.seriesEventId
                                )
                            )
                    )
                }
                EventSeriesPackageContents.build(
                    seriesFile = seriesFile,
                    eventFiles = eventFiles,
                    manifestEntryPath = EVENT_SERIES_FILE_NAME,
                    packageFileNameStem = seriesData.series.name
                ).entries.forEach { entry ->
                    zip.writeTextEntry(entry.path, entry.text)
                }
            }
            output.toByteArray()
        }
    }

    private fun EventSeriesMember.toEventSeriesEvent(): EventSeriesEvent =
        EventSeriesEvent(
            seriesEventId = seriesEventId,
            eventFilePath = eventFilePath,
            order = eventOrder,
            displayName = displayName,
            startDateTimeIso = startDateTimeIso,
            formatLabel = formatLabel
        )

    private fun ZipOutputStream.writeTextEntry(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
