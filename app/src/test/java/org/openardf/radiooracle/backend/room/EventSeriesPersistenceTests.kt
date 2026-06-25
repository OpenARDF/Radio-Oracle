package org.openardf.radiooracle.backend.room

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.database.EventDatabase
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class EventSeriesPersistenceTests {
    private val database: EventDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        EventDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun eventSeriesMembersMapManifestEventsToLocalRaces() = runBlocking {
        val firstRace = race("Day 1")
        val secondRace = race("Day 2")
        database.raceDao().createRace(firstRace)
        database.raceDao().createRace(secondRace)

        val series = EventSeries(seriesId = "series-2026", name = "Championship")
        val secondMember = member(
            seriesId = series.seriesId,
            seriesEventId = "event-2",
            raceId = secondRace.id,
            order = 1,
            displayName = secondRace.name
        )
        val firstMember = member(
            seriesId = series.seriesId,
            seriesEventId = "event-1",
            raceId = firstRace.id,
            order = 0,
            displayName = firstRace.name
        )

        database.eventSeriesDao().upsertSeries(series)
        database.eventSeriesDao().upsertMember(secondMember)
        database.eventSeriesDao().upsertMember(firstMember)

        val stored = database.eventSeriesDao().getSeries(series.seriesId)!!
        val storedForRace = database.eventSeriesDao().getSeriesForRace(secondRace.id)!!

        assertEquals("Championship", stored.series.name)
        assertEquals(listOf("event-1", "event-2"), stored.orderedMembers().map { it.seriesEventId })
        assertEquals(secondRace.id, storedForRace.members.single { it.seriesEventId == "event-2" }.localRaceId)
        assertNull(database.eventSeriesDao().getSeriesForRace(UUID.randomUUID()))
    }

    @Test
    fun deletingLocalRaceRemovesSeriesMemberMapping() = runBlocking {
        val race = race("Deleted event")
        val series = EventSeries(seriesId = "series-delete", name = "Delete test")
        database.raceDao().createRace(race)
        database.eventSeriesDao().upsertSeries(series)
        database.eventSeriesDao().upsertMember(
            member(
                seriesId = series.seriesId,
                seriesEventId = "deleted-event",
                raceId = race.id,
                order = 0,
                displayName = race.name
            )
        )

        database.raceDao().deleteRace(race.id)

        val stored = database.eventSeriesDao().getSeries(series.seriesId)!!
        assertEquals(emptyList<EventSeriesMember>(), stored.members)
    }

    @Test
    fun deletingSeriesRemovesGroupingButKeepsMemberRaces() = runBlocking {
        val firstRace = race("Day 1")
        val secondRace = race("Day 2")
        val series = EventSeries(seriesId = "series-remove-grouping", name = "Remove grouping test")
        val members = listOf(
            member(series.seriesId, "day-1", firstRace.id, 0, firstRace.name),
            member(series.seriesId, "day-2", secondRace.id, 1, secondRace.name)
        )
        saveImportedSeriesRows(series, members, firstRace, secondRace)

        database.eventSeriesDao().deleteSeries(series.seriesId)

        assertNull(database.eventSeriesDao().getSeries(series.seriesId))
        assertNull(database.eventSeriesDao().getSeriesForRace(firstRace.id))
        assertEquals(firstRace, database.raceDao().getRace(firstRace.id))
        assertEquals(secondRace, database.raceDao().getRace(secondRace.id))
    }

    @Test
    fun importedSeriesCanBeFoundFromEveryLocalMemberRace() = runBlocking {
        val firstRace = race("Day 1")
        val secondRace = race("Day 2")
        val series = EventSeries(seriesId = "series-import", name = "Imported Championship")
        val members = listOf(
            member(series.seriesId, "day-1", firstRace.id, 0, firstRace.name),
            member(series.seriesId, "day-2", secondRace.id, 1, secondRace.name)
        )

        saveImportedSeriesRows(series, members, firstRace, secondRace)

        assertEquals(
            listOf("day-1", "day-2"),
            database.eventSeriesDao().getSeriesForRace(firstRace.id)!!.orderedMembers().map { it.seriesEventId }
        )
        assertEquals(
            listOf("day-1", "day-2"),
            database.eventSeriesDao().getSeriesForRace(secondRace.id)!!.orderedMembers().map { it.seriesEventId }
        )
    }

    private suspend fun saveImportedSeriesRows(
        series: EventSeries,
        members: List<EventSeriesMember>,
        vararg races: Race
    ) {
        races.forEach { race -> database.raceDao().createRace(race) }
        database.eventSeriesDao().upsertSeries(series)
        members.forEach { member -> database.eventSeriesDao().upsertMember(member) }
    }

    private fun race(name: String): Race =
        Race(
            id = UUID.randomUUID(),
            name = name,
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 18, 9, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )

    private fun member(
        seriesId: String,
        seriesEventId: String,
        raceId: UUID,
        order: Int,
        displayName: String
    ): EventSeriesMember =
        EventSeriesMember(
            seriesId = seriesId,
            seriesEventId = seriesEventId,
            localRaceId = raceId,
            eventFilePath = "$seriesEventId.rom.json",
            eventOrder = order,
            displayName = displayName,
            startDateTimeIso = "2026-06-18T09:00",
            formatLabel = "Classic"
        )
}
