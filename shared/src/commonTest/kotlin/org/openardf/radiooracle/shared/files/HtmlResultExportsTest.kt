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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlResultExportsTest {
    @Test
    fun exportsPrintableHtmlResultsByCategory() {
        val html = HtmlResultExports.results(raceData(), appVersion = "1.0")

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<title>HTML &amp; Result Race results</title>"))
        assertTrue(html.contains("<h1>HTML &amp; Result Race</h1>"))
        assertTrue(html.contains("<h2>M21</h2>"))
        assertTrue(html.contains("<th>Place</th>"))
        assertTrue(html.contains("<td class=\"num\">1.</td>"))
        assertTrue(html.contains("<td>RUNNER Alice</td>"))
        assertTrue(html.contains("<td>OK &amp; Test</td>"))
        assertTrue(html.contains("<td class=\"num\">2</td>"))
        assertTrue(html.contains("<td>00:45:00</td>"))
        assertTrue(html.contains("31 - 00:10:00 32 - 00:25:00"))
        assertTrue(html.contains("Generated with Radio-Oracle 1.0"))
    }

    @Test
    fun usesShortStatusInsteadOfPlaceForNonOkResults() {
        val html = HtmlResultExports.results(raceData(resultStatus = ResultStatus.MISPUNCHED))

        assertTrue(html.contains("<td class=\"num\">MP</td>"))
        assertFalse(html.contains("<td class=\"num\">1.</td>"))
    }

    @Test
    fun exportsGlobalControlLabelsInSplits() {
        val html = HtmlResultExports.results(
            raceData(
                controls = listOf(
                    EventControl("control-31", "race", "F1", 31, org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL)
                )
            )
        )

        assertTrue(html.contains("F1 - 00:10:00 32 - 00:25:00"))
    }

    private fun raceData(
        resultStatus: ResultStatus = ResultStatus.OK,
        controls: List<EventControl> = emptyList()
    ): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "HTML & Result Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Alice",
            lastName = "Runner",
            club = "OK & Test",
            index = "IDX",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventRaceData(
            race = race,
            categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
            aliases = emptyList(),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout(resultStatus)
                )
            ),
            unmatchedReadoutData = emptyList(),
            controls = controls
        )
    }

    private fun readout(resultStatus: ResultStatus): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = "race",
                competitorId = "competitor",
                siNumber = 123456,
                cardType = 5,
                checkTimeSeconds = null,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 38_700,
                readoutDateTimeIso = "2026-06-01T10:46:00",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = 2,
                runTimeSeconds = 2_700,
                modified = false,
                sent = false,
                place = 0
            ),
            punches = listOf(
                punch(code = 31, splitSeconds = 600),
                punch(code = 32, splitSeconds = 1_500)
            )
        )

    private fun punch(code: Int, splitSeconds: Long): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = "punch-$code",
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = code,
                siTimeSeconds = 36_000 + splitSeconds,
                originalSiTimeSeconds = 36_000 + splitSeconds,
                punchType = SIRecordType.CONTROL,
                order = code,
                punchStatus = PunchStatus.VALID,
                splitSeconds = splitSeconds
            ),
            alias = null
        )
}
