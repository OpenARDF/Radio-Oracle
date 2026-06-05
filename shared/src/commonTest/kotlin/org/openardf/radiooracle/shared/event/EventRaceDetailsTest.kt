package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventRaceDetailsTest {
    @Test
    fun buildsDisplayDetailsFromRaceMetadata() {
        val details = EventRaceDetails.from(race())

        assertEquals("Desktop Test Race", details.name)
        assertEquals("2026-05-31T10:00", details.startDateTimeIso)
        assertEquals(RaceType.CLASSIC, details.raceType)
        assertEquals("Classic", details.raceTypeLabel)
        assertEquals(RaceLevel.PRACTICE, details.raceLevel)
        assertEquals("Practice", details.raceLevelLabel)
        assertEquals(RaceBand.M80, details.raceBand)
        assertEquals("80m", details.raceBandLabel)
        assertEquals("120", details.timeLimitMinutesText)
        assertEquals("120:00", details.timeLimitText)
    }

    @Test
    fun nationalRaceLevelDefaultsTo180MinuteLimit() {
        assertEquals(180L, RaceLevel.NATIONAL.defaultTimeLimitMinutes())
        assertEquals(null, RaceLevel.PRACTICE.defaultTimeLimitMinutes())
    }

    private fun race(): EventRace =
        EventRace(
            id = "race",
            name = "Desktop Test Race",
            apiKey = "secret",
            startDateTimeIso = "2026-05-31T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
}
