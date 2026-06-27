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
import kotlin.test.assertTrue

class EventLastReadoutDetailsTest {
    @Test
    fun returnsEmptyDetailsWhenThereAreNoReadouts() {
        val details = EventLastReadoutDetails.from(raceData(emptyList(), emptyList()))

        assertFalse(details.hasReadout)
        assertEquals("", details.siNumberText)
        assertEquals(EventLastReadoutSeverity.None, details.severity)
    }

    @Test
    fun returnsMostRecentMatchedReadoutDetails() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = listOf(
                    competitorData("comp-1", "Alice", readout("old", "comp-1", 1111, "2026-06-01T10:00")),
                    competitorData(
                        "comp-2",
                        "Bob",
                        readout("new", "comp-2", 2222, "2026-06-01T10:05", ResultStatus.MISPUNCHED)
                    )
                ),
                unmatchedReadouts = listOf(readout("unmatched", null, 3333, "2026-06-01T10:03"))
            )
        )

        assertTrue(details.hasReadout)
        assertEquals("2026-06-01T10:05", details.readoutDateTimeIso)
        assertEquals("2222", details.siNumberText)
        assertEquals("RUNNER Bob", details.competitorName)
        assertEquals("Mispunched", details.statusLabel)
        assertEquals(EventLastReadoutSeverity.Warning, details.severity)
    }

    @Test
    fun returnsMostRecentUnmatchedReadoutDetails() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = listOf(
                    competitorData("comp-1", "Alice", readout("old", "comp-1", 1111, "2026-06-01T10:00"))
                ),
                unmatchedReadouts = listOf(readout("unmatched", null, 3333, "2026-06-01T10:10"))
            )
        )

        assertTrue(details.hasReadout)
        assertEquals("3333", details.siNumberText)
        assertEquals("", details.competitorName)
        assertEquals("Unmatched", details.statusLabel)
        assertEquals(EventLastReadoutSeverity.Error, details.severity)
    }

    @Test
    fun returnsCardNameForMostRecentUnmatchedReadoutWhenAvailable() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = emptyList(),
                unmatchedReadouts = listOf(
                    readout(
                        id = "unmatched",
                        competitorId = null,
                        siNumber = 3333,
                        readoutDateTimeIso = "2026-06-01T10:10",
                        cardName = "Runner Alice"
                    )
                )
            )
        )

        assertTrue(details.hasReadout)
        assertEquals("Runner Alice", details.competitorName)
        assertEquals("Unmatched", details.statusLabel)
        assertEquals(EventLastReadoutSeverity.Error, details.severity)
    }

    @Test
    fun keepsUnmatchedNonOkStatusVisible() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = emptyList(),
                unmatchedReadouts = listOf(
                    readout(
                        id = "unmatched",
                        competitorId = null,
                        siNumber = 3333,
                        readoutDateTimeIso = "2026-06-01T10:10",
                        status = ResultStatus.MISPUNCHED
                    )
                )
            )
        )

        assertEquals("Unmatched: Mispunched", details.statusLabel)
        assertEquals(EventLastReadoutSeverity.Error, details.severity)
    }

    @Test
    fun marksOkMatchedReadoutNormal() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = listOf(
                    competitorData("comp-1", "Alice", readout("ok", "comp-1", 1111, "2026-06-01T10:00"))
                ),
                unmatchedReadouts = emptyList()
            )
        )

        assertEquals(EventLastReadoutSeverity.Normal, details.severity)
    }

    @Test
    fun marksErrorStatusAsError() {
        val details = EventLastReadoutDetails.from(
            raceData(
                competitorReadouts = listOf(
                    competitorData(
                        "comp-1",
                        "Alice",
                        readout("error", "comp-1", 1111, "2026-06-01T10:00", ResultStatus.ERROR)
                    )
                ),
                unmatchedReadouts = emptyList()
            )
        )

        assertEquals(EventLastReadoutSeverity.Error, details.severity)
    }

    private fun raceData(
        competitorReadouts: List<EventCompetitorData>,
        unmatchedReadouts: List<EventReadoutData>
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Last Read Race",
                apiKey = "",
                startDateTimeIso = "2026-06-01T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 120 * 60
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = competitorReadouts,
            unmatchedReadoutData = unmatchedReadouts
        )

    private fun competitorData(id: String, firstName: String, readoutData: EventReadoutData): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = null,
                    firstName = firstName,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = readoutData.result.siNumber,
                    siRent = false,
                    startNumber = readoutData.result.siNumber ?: 0,
                    drawnStartTimeSeconds = null
                ),
                category = null
            ),
            readoutData = readoutData
        )

    private fun readout(
        id: String,
        competitorId: String?,
        siNumber: Int?,
        readoutDateTimeIso: String,
        status: ResultStatus = ResultStatus.OK,
        cardName: String? = null
    ): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = competitorId,
                siNumber = siNumber,
                cardType = 6,
                checkTimeSeconds = null,
                startTimeSeconds = 600,
                finishTimeSeconds = 1200,
                readoutDateTimeIso = readoutDateTimeIso,
                automaticStatus = true,
                resultStatus = status,
                points = 0,
                runTimeSeconds = 600,
                modified = false,
                sent = false,
                cardName = cardName
            ),
            punches = emptyList()
        )
}
