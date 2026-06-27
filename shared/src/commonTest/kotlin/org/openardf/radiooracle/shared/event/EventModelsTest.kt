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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventModelsTest {
    @Test
    fun categoryUsesRacePropertiesWhenNotDifferent() {
        val race = race()
        val category = category(
            differentProperties = false,
            raceType = RaceType.SPRINT,
            raceBand = RaceBand.M2,
            timeLimitSeconds = 60
        )

        assertEquals(RaceType.CLASSIC, category.effectiveRaceType(race))
        assertEquals(RaceBand.M80, category.effectiveRaceBand(race))
        assertEquals(7_200, category.effectiveTimeLimitSeconds(race))
    }

    @Test
    fun categoryIgnoresLegacyOverridesWhenDifferent() {
        val race = race()
        val category = category(
            differentProperties = true,
            raceType = RaceType.SPRINT,
            raceBand = RaceBand.M2,
            timeLimitSeconds = 3_600
        )

        assertEquals(RaceType.CLASSIC, category.effectiveRaceType(race))
        assertEquals(RaceBand.M80, category.effectiveRaceBand(race))
        assertEquals(7_200, category.effectiveTimeLimitSeconds(race))
    }

    @Test
    fun competitorFormatsNames() {
        val competitor = EventCompetitor(
            id = "competitor-1",
            raceId = "race-1",
            categoryId = "category-1",
            firstName = "Pavel",
            lastName = "Kolsky",
            club = "OK",
            index = "OK001",
            isMan = true,
            birthYear = 1980,
            siNumber = 123456,
            siRent = false,
            startNumber = 42,
            drawnStartTimeSeconds = null
        )

        assertEquals("KOLSKY Pavel", competitor.fullName())
        assertEquals("KOLSKY Pavel (42)", competitor.nameWithStartNumber())
    }

    private fun race(): EventRace =
        EventRace(
            id = "race-1",
            name = "Test",
            apiKey = "",
            startDateTimeIso = "2026-05-30T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )

    private fun category(
        differentProperties: Boolean,
        raceType: RaceType?,
        raceBand: RaceBand?,
        timeLimitSeconds: Long?
    ): EventCategory =
        EventCategory(
            id = "category-1",
            raceId = "race-1",
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = differentProperties,
            raceType = raceType,
            raceBand = raceBand,
            timeLimitSeconds = timeLimitSeconds,
            controlPointsString = "31 32"
        )
}
