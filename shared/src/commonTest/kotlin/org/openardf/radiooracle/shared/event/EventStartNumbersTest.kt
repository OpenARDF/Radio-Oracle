package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventStartNumbersTest {
    @Test
    fun assignsStartNumbersByUniqueDrawnStartTimes() {
        val category = EventCategory(
            id = "category-1",
            raceId = "race-1",
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val alice = competitor("alice", "Alice", drawnStartTimeSeconds = 600, oldStartNumber = 99)
        val bob = competitor("bob", "Bob", drawnStartTimeSeconds = 600, oldStartNumber = 98)
        val cara = competitor("cara", "Cara", drawnStartTimeSeconds = 900, oldStartNumber = 97)
        val drew = competitor("drew", "Drew", drawnStartTimeSeconds = null, oldStartNumber = 96)

        val updated = EventStartNumbers.assignFromDrawnStartTimes(
            EventRaceData(
                race = EventRace(
                    id = "race-1",
                    name = "Test",
                    apiKey = "",
                    startDateTimeIso = "2026-06-01T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        competitors = listOf(alice, bob, cara, drew)
                    )
                ),
                competitorData = listOf(alice, bob, cara, drew).map {
                    EventCompetitorData(EventCompetitorCategory(it, category))
                }
            )
        )

        val numbers = updated.competitorData
            .associate { it.competitorCategory.competitor.id to it.competitorCategory.competitor.startNumber }
        assertEquals(1, numbers["alice"])
        assertEquals(1, numbers["bob"])
        assertEquals(2, numbers["cara"])
        assertNull(numbers["drew"])
        assertEquals(listOf(1, 1, 2, null), updated.categories.single().competitors.map { it.startNumber })
    }

    private fun competitor(
        id: String,
        firstName: String,
        drawnStartTimeSeconds: Long?,
        oldStartNumber: Int
    ): EventCompetitor =
        EventCompetitor(
            id = id,
            raceId = "race-1",
            categoryId = "category-1",
            firstName = firstName,
            lastName = "Runner",
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = null,
            siRent = false,
            startNumber = oldStartNumber,
            drawnStartTimeSeconds = drawnStartTimeSeconds
        )
}
