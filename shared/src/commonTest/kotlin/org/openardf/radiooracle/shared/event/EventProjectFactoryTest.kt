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
import kotlin.test.assertFailsWith

class EventProjectFactoryTest {
    @Test
    fun createsEmptyProjectWithAndroidCompatibleRaceDefaults() {
        val projectFile = EventProjectFactory.createEmptyProject(
            raceId = "race-1",
            raceName = " New Race ",
            startDateTimeIso = "2026-05-31T14:30:00"
        )

        val race = projectFile.raceData.race
        assertEquals("race-1", race.id)
        assertEquals("New Race", race.name)
        assertEquals("", race.apiKey)
        assertEquals("2026-05-31T14:30:00", race.startDateTimeIso)
        assertEquals(RaceType.CLASSIC, race.raceType)
        assertEquals(RaceLevel.PRACTICE, race.raceLevel)
        assertEquals(RaceBand.M80, race.raceBand)
        assertEquals(7_200, race.timeLimitSeconds)
        assertEquals(emptyList(), projectFile.raceData.categories)
        assertEquals(emptyList(), projectFile.raceData.aliases)
        assertEquals(emptyList(), projectFile.raceData.competitorData)
        assertEquals(emptyList(), projectFile.raceData.unmatchedReadoutData)
        assertEquals(emptyList(), projectFile.raceData.controls)
    }

    @Test
    fun rejectsInvalidNewProjectInputs() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("", "Event", "2026-05-31T14:30:00")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("race-1", " ", "2026-05-31T14:30:00")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectFactory.createEmptyProject("race-1", "Event", " ")
        }
    }
}
