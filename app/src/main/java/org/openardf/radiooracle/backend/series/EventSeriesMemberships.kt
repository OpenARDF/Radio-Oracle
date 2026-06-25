package org.openardf.radiooracle.backend.series

import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.shared.event.toDisplayLabel
import java.util.UUID

object EventSeriesMemberships {
    fun memberForRace(seriesId: String, race: Race, eventOrder: Int): EventSeriesMember =
        EventSeriesMember(
            seriesId = seriesId,
            seriesEventId = seriesEventId(race.id),
            localRaceId = race.id,
            eventFilePath = eventFilePath(race.id),
            eventOrder = eventOrder,
            displayName = race.name,
            startDateTimeIso = race.startDateTime.toString(),
            formatLabel = race.raceType.toDisplayLabel()
        )

    fun appendRace(seriesData: EventSeriesData, race: Race): List<EventSeriesMember> {
        val nextOrder = (seriesData.members.maxOfOrNull { it.eventOrder } ?: -1) + 1
        return seriesData.members + memberForRace(seriesData.series.seriesId, race, nextOrder)
    }

    fun removeRace(seriesData: EventSeriesData, raceId: UUID): List<EventSeriesMember> =
        seriesData.orderedMembers()
            .filterNot { it.localRaceId == raceId }
            .mapIndexed { index, member -> member.copy(eventOrder = index) }

    private fun seriesEventId(raceId: UUID): String =
        "event-$raceId"

    private fun eventFilePath(raceId: UUID): String =
        "events/$raceId.rom.json"
}
