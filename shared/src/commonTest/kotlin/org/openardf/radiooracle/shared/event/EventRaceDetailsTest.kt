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
