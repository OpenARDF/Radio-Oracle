package org.openardf.radiooracle.backend.series

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class EventSeriesMembershipsTest {
    @Test
    fun buildsStableMemberFromRace() {
        val raceId = UUID.fromString("2fb6b7e4-10a7-47a1-b035-aa563f0b53e7")
        val member = EventSeriesMemberships.memberForRace(
            seriesId = "series-1",
            race = race(raceId, "Day 1", RaceType.SPRINT),
            eventOrder = 2
        )

        assertEquals("series-1", member.seriesId)
        assertEquals("event-2fb6b7e4-10a7-47a1-b035-aa563f0b53e7", member.seriesEventId)
        assertEquals(raceId, member.localRaceId)
        assertEquals("events/2fb6b7e4-10a7-47a1-b035-aa563f0b53e7.rom.json", member.eventFilePath)
        assertEquals(2, member.eventOrder)
        assertEquals("Day 1", member.displayName)
        assertEquals("2026-06-18T09:00", member.startDateTimeIso)
        assertEquals("Sprint", member.formatLabel)
    }

    @Test
    fun appendsRaceAfterExistingMembers() {
        val series = seriesData(
            member("series-1", "day-1", UUID.randomUUID(), 0),
            member("series-1", "day-2", UUID.randomUUID(), 3)
        )
        val appended = EventSeriesMemberships.appendRace(
            series,
            race(UUID.randomUUID(), "Day 3")
        )

        assertEquals(listOf(0, 3, 4), appended.map { it.eventOrder })
        assertEquals("Day 3", appended.last().displayName)
    }

    @Test
    fun removesRaceAndCompactsOrder() {
        val removedRaceId = UUID.randomUUID()
        val keptRaceId = UUID.randomUUID()
        val series = seriesData(
            member("series-1", "day-2", removedRaceId, 5),
            member("series-1", "day-1", keptRaceId, 2)
        )

        val remaining = EventSeriesMemberships.removeRace(series, removedRaceId)

        assertEquals(listOf(keptRaceId), remaining.map { it.localRaceId })
        assertEquals(listOf(0), remaining.map { it.eventOrder })
    }

    private fun seriesData(vararg members: EventSeriesMember): EventSeriesData =
        EventSeriesData(EventSeries("series-1", "Series 1"), members.toList())

    private fun race(
        id: UUID,
        name: String,
        raceType: RaceType = RaceType.CLASSIC
    ): Race =
        Race(
            id = id,
            name = name,
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 18, 9, 0),
            raceType = raceType,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )

    private fun member(
        seriesId: String,
        seriesEventId: String,
        raceId: UUID,
        order: Int
    ): EventSeriesMember =
        EventSeriesMember(
            seriesId = seriesId,
            seriesEventId = seriesEventId,
            localRaceId = raceId,
            eventFilePath = "events/$seriesEventId.rom.json",
            eventOrder = order,
            displayName = seriesEventId,
            startDateTimeIso = "2026-06-18T09:00",
            formatLabel = "Classic"
        )
}
