package org.openardf.radiooracle.backend.files

import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.shared.event.EVENT_SERIES_FILE_NAME
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLink
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
        require(members.size >= 2) {
            "Event Series export requires at least two events."
        }
        val seriesFile = EventSeriesFile(
            seriesId = seriesData.series.seriesId,
            name = seriesData.series.name,
            events = members.map { member -> member.toEventSeriesEvent() }
        )

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeTextEntry(EVENT_SERIES_FILE_NAME, EventSeriesFileJson.encode(seriesFile))
                members.forEach { member ->
                    val raceData = raceDataById[member.localRaceId]
                        ?: throw IllegalArgumentException("Missing Event data for '${member.displayName}'.")
                    zip.writeTextEntry(
                        member.eventFilePath,
                        EventProjectFileJson.encode(
                            EventProjectFile(
                                raceData = raceData.toEventRaceData(),
                                seriesLink = EventSeriesLink(
                                    seriesId = member.seriesId,
                                    seriesEventId = member.seriesEventId
                                )
                            )
                        )
                    )
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
