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
import org.openardf.radiooracle.shared.domain.ResultStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EventInForestDetailsTest {
    @Test
    fun buildsInForestCountsAndRows() {
        val details = EventInForestDetails.from(raceData(), raceElapsedSeconds = 90 * 60)

        assertEquals(2, details.inForestCount)
        assertEquals(1, details.finishedCount)
        assertEquals(2, details.notStartedCount)
        assertEquals(1, details.unscheduledCount)

        assertEquals("late", details.inForestRows[0].competitorId)
        assertEquals("RUNNER Late (3)", details.inForestRows[0].competitorName)
        assertEquals("10:00", details.inForestRows[0].startTimeText)
        assertEquals("80:00", details.inForestRows[0].elapsedText)
        assertEquals("120:00", details.inForestRows[0].limitText)
        assertFalse(details.inForestRows[0].overLimit)

        assertEquals("active", details.inForestRows[1].competitorId)
        assertEquals("40:00", details.inForestRows[1].startTimeText)
        assertEquals("50:00", details.inForestRows[1].elapsedText)
        assertFalse(details.inForestRows[1].overLimit)
    }

    private fun raceData(): EventRaceData {
        val category = EventCategory(
            id = "cat",
            raceId = "race",
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = true,
            raceType = RaceType.CLASSIC,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 60 * 60,
            controlPointsString = ""
        )
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Forest Race",
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
                competitorData("finished", "Finished", 1, 5 * 60, readout = true),
                competitorData("future", "Future", 2, 100 * 60),
                competitorData("dns", "Dns", 6, 20 * 60, status = ResultStatus.DID_NOT_START),
                competitorData("late", "Late", 3, 10 * 60),
                competitorData("active", "Active", 4, 40 * 60),
                competitorData("unscheduled", "Unscheduled", 5, null)
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun competitorData(
        id: String,
        firstName: String,
        startNumber: Int,
        startTimeSeconds: Long?,
        readout: Boolean = false,
        status: ResultStatus = ResultStatus.OK
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
                    siNumber = startNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = startTimeSeconds
                ),
                category = null
            ),
            readoutData = if (readout || status != ResultStatus.OK) readout(id, status) else null
        )

    private fun readout(id: String, status: ResultStatus): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result-$id",
                raceId = "race",
                competitorId = id,
                siNumber = 1000,
                cardType = 6,
                checkTimeSeconds = null,
                startTimeSeconds = 600,
                finishTimeSeconds = 1200,
                readoutDateTimeIso = "2026-06-01T10:21",
                automaticStatus = true,
                resultStatus = status,
                points = 1,
                runTimeSeconds = 600,
                modified = false,
                sent = false
            ),
            punches = emptyList()
        )
}
