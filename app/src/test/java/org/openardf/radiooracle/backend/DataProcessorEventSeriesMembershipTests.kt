package org.openardf.radiooracle.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DataProcessorEventSeriesMembershipTests {
    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun createAddAndRemoveSeriesMembershipKeepsEventsOnDevice() = runBlocking {
        val processor = DataProcessor.get()
        val dayOne = raceData(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Membership Day 1"
        )
        val dayTwo = raceData(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Membership Day 2"
        )
        processor.saveRaceData(dayOne)
        processor.saveRaceData(dayTwo)

        val created = processor.createEventSeriesFromRace(dayOne.race.id, "Membership Series")
        val complete = processor.addRaceToEventSeries(dayTwo.race.id, created.series.seriesId)

        assertEquals(listOf(dayOne.race.id, dayTwo.race.id), complete.orderedMembers().map { it.localRaceId })
        assertEquals(complete.series.seriesId, processor.getEventSeriesForRace(dayTwo.race.id)?.series?.seriesId)

        val afterRemovingDayTwo = processor.removeRaceFromEventSeries(dayTwo.race.id)

        assertNotNull(afterRemovingDayTwo)
        assertEquals(listOf(dayOne.race.id), afterRemovingDayTwo!!.orderedMembers().map { it.localRaceId })
        assertNull(processor.getEventSeriesForRace(dayTwo.race.id))
        assertNotNull(processor.getRace(dayTwo.race.id))

        val afterRemovingFinalEvent = processor.removeRaceFromEventSeries(dayOne.race.id)

        assertNull(afterRemovingFinalEvent)
        assertNull(processor.getEventSeries(complete.series.seriesId))
        assertNotNull(processor.getRace(dayOne.race.id))
        assertNotNull(processor.getRace(dayTwo.race.id))
    }

    private fun raceData(id: UUID, name: String): RaceData =
        RaceData(
            race = Race(
                id = id,
                name = name,
                apiKey = "",
                startDateTime = LocalDateTime.of(2026, 6, 18, 9, 0),
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimit = Duration.ofHours(2)
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )
}
