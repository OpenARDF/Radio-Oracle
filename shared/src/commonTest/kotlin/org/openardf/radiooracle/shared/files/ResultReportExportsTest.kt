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

import org.openardf.radiooracle.shared.domain.ControlPointType
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultReportExportsTest {
    @Test
    fun modelUsesSameRowsForHtmlAndXmlReports() {
        val report = ResultReportExports.model(raceData())

        assertEquals("Posting & Result Race", report.raceName)
        assertEquals(listOf("M21"), report.categories.map { it.name })
        val row = report.categories.single().results.single()
        assertEquals("1.", row.placeText)
        assertEquals("RUNNER Alice", row.name)
        assertEquals("OK & Test", row.club)
        assertEquals("IDX", row.personId)
        assertEquals("7", row.bibNumber)
        assertEquals("123456", row.siNumber)
        assertEquals("OK", row.statusText)
        assertEquals("2", row.pointsText)
        assertEquals("00:45:00", row.runTimeText)
        assertEquals("Fox 1 32", row.controlsText)
        assertEquals("Fox 1 - 00:10:00 32 - 00:25:00 Finish - 00:10:00", row.splitsText)
    }

    @Test
    fun exportsResultsPostingHtmlReport() {
        val html = ResultReportExports.html(raceData(), appVersion = "1.0")

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("<title>Posting &amp; Result Race results report</title>"))
        assertTrue(html.contains("<h1>Posting &amp; Result Race</h1>"))
        assertTrue(html.contains("<th>Bib #</th>"))
        assertTrue(html.contains("<td>7</td>"))
        assertTrue(html.contains("<td>123456</td>"))
        assertTrue(html.contains("<td>Fox 1 32</td>"))
        assertTrue(html.contains("Fox 1 - 00:10:00 32 - 00:25:00 Finish - 00:10:00"))
        assertTrue(html.contains("Generated with Radio-Oracle 1.0"))
    }

    @Test
    fun exportsSelfDescribingResultsPostingXmlReport() {
        val xml = ResultReportExports.xml(raceData(), appVersion = "1.0")

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""<ResultsReport format="radio-oracle-results-report-v1" creator="Radio-Oracle 1.0">"""))
        assertTrue(xml.contains("<RaceName>Posting &amp; Result Race</RaceName>"))
        assertTrue(xml.contains("""<Category id="category" name="M21">"""))
        assertTrue(xml.contains("<Place>1.</Place>"))
        assertTrue(xml.contains("<Name>RUNNER Alice</Name>"))
        assertTrue(xml.contains("<Club>OK &amp; Test</Club>"))
        assertTrue(xml.contains("<BibNumber>7</BibNumber>"))
        assertTrue(xml.contains("<SiNumber>123456</SiNumber>"))
        assertTrue(xml.contains("<Controls>Fox 1 32</Controls>"))
        assertTrue(xml.contains("<Splits>Fox 1 - 00:10:00 32 - 00:25:00 Finish - 00:10:00</Splits>"))
    }

    @Test
    fun usesShortStatusInsteadOfPlaceForNonOkResults() {
        val xml = ResultReportExports.xml(raceData(resultStatus = ResultStatus.MISPUNCHED))

        assertTrue(xml.contains("<Place>MP</Place>"))
        assertTrue(xml.contains("<Status>MP</Status>"))
        assertFalse(xml.contains("<Place>1.</Place>"))
    }

    private fun raceData(resultStatus: ResultStatus = ResultStatus.OK): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Posting & Result Race",
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
            controlPointsString = "1 2"
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
            drawnStartTimeSeconds = null,
            bibNumber = "7"
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
            controls = listOf(EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"))
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
                punch(code = 32, splitSeconds = 1_500),
                punch(code = 0, splitSeconds = 600, punchType = SIRecordType.FINISH)
            )
        )

    private fun punch(
        code: Int,
        splitSeconds: Long,
        punchType: SIRecordType = SIRecordType.CONTROL
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = "punch-${punchType.name}-$code",
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = code,
                siTimeSeconds = 36_000 + splitSeconds,
                originalSiTimeSeconds = 36_000 + splitSeconds,
                punchType = punchType,
                order = code,
                punchStatus = PunchStatus.VALID,
                splitSeconds = splitSeconds
            ),
            alias = null
        )
}
