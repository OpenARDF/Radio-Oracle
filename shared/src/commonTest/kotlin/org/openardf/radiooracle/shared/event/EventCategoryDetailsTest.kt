package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCategoryDetailsTest {
    @Test
    fun buildsDisplayRowsUsingRaceDefaultsAndCategoryOverrides() {
        val rows = EventCategoryDetails.from(raceData())

        assertEquals(2, rows.size)
        assertEquals("W21", rows[0].id)
        assertEquals("W21", rows[0].name)
        assertEquals("5000", rows[0].lengthMetersText)
        assertEquals("100", rows[0].climbMetersText)
        assertEquals("Sprint", rows[0].raceTypeLabel)
        assertEquals("2m", rows[0].raceBandLabel)
        assertEquals("60:00", rows[0].timeLimitText)
        assertEquals("31 32", rows[0].controlPointsText)

        assertEquals("M21", rows[1].id)
        assertEquals("M21", rows[1].name)
        assertEquals("Classic", rows[1].raceTypeLabel)
        assertEquals("80m", rows[1].raceBandLabel)
        assertEquals("120:00", rows[1].timeLimitText)
    }

    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Category Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                categoryData(
                    name = "M21",
                    order = 2,
                    differentProperties = false,
                    raceType = null,
                    raceBand = null,
                    timeLimitSeconds = null
                ),
                categoryData(
                    name = "W21",
                    order = 1,
                    differentProperties = true,
                    raceType = RaceType.SPRINT,
                    raceBand = RaceBand.M2,
                    timeLimitSeconds = 3_600
                )
            ),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun categoryData(
        name: String,
        order: Int,
        differentProperties: Boolean,
        raceType: RaceType?,
        raceBand: RaceBand?,
        timeLimitSeconds: Long?
    ): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = name,
                raceId = "race",
                name = name,
                isMan = name.startsWith("M"),
                maxAge = null,
                lengthMeters = 5_000,
                climbMeters = 100,
                order = order,
                differentProperties = differentProperties,
                raceType = raceType,
                raceBand = raceBand,
                timeLimitSeconds = timeLimitSeconds,
                controlPointsString = "31 32"
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )
}
