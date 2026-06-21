package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventStartListDetailsTest {
    @Test
    fun buildsSortedStartListRows() {
        val details = EventStartListDetails.from(raceData())

        assertEquals(3, details.scheduledCount)
        assertEquals(1, details.unscheduledCount)
        assertEquals(listOf("early", "late", "same-time", "unscheduled"), details.rows.map { it.competitorId })
        assertEquals(listOf("1", "2", "", ""), details.rows.map { it.startSequenceText })

        assertEquals("05:00", details.rows[0].startTimeText)
        assertEquals("1", details.rows[0].startNumberText)
        assertEquals("RUNNER Early", details.rows[0].competitorName)
        assertEquals("M21", details.rows[0].categoryName)
        assertEquals("1111", details.rows[0].siNumberText)
        assertEquals("", details.rows.last().startTimeText)
        assertEquals("", details.rows.last().startSequenceText)
        assertEquals(
            true,
            details.quality.messages.any { it == "Consecutive starts include M21 competitors at 1 and 2." }
        )
    }

    private fun raceData(): EventRaceData {
        val category = EventCategory(
            id = "cat",
            raceId = "race",
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
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Start Race",
                apiKey = "",
                startDateTimeIso = "2026-06-01T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 120 * 60
            ),
            categories = listOf(EventCategoryData(category, emptyList(), emptyList())),
            aliases = emptyList(),
            competitorData = listOf(
                competitorData("late", "Late", 2, 15 * 60),
                competitorData("unscheduled", "Unscheduled", null, null),
                competitorData("early", "Early", 1, 5 * 60),
                competitorData("same-time", "Same", 2, 15 * 60)
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun competitorData(
        id: String,
        firstName: String,
        startNumber: Int?,
        startTimeSeconds: Long?
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = "cat",
                    firstName = firstName,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = startNumber?.let { 1110 + it },
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = startTimeSeconds
                ),
                category = null
            ),
            readoutData = null
        )
}
