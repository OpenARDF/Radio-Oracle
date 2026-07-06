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

package org.openardf.radiooracle.shared.printing

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinishTicketRendererTest {
    @Test
    fun rendersMatchedFinishTicket() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched")

        assertEquals(
            """
            [C]<b>Ticket Race</b>
            [L]
            [L]RUNNER Alice
            [L]SI: 123456
            [L]Bib: A-12
            [L]Category: M21
            
            [L]Start          10:00:00
            [L]1 (Foxhole)OK  10:05:00 00:05:00
            [L]2 (32)MP       10:10:00 00:10:00
            [L]Finish         10:20:00 00:20:00
            
            [R]<b>Run time: 00:20:00</b>
            [R]Score: 2
            [R]Status: OK
            """.trimIndent() + "\n",
            text
        )
    }

    @Test
    fun rendersUnmatchedFinishTicketWithUnknownCompetitorFields() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "unmatched")

        assertEquals(
            """
            [C]<b>Ticket Race</b>
            [L]
            [L]?
            [L]SI: 654321
            [L]Category: ?
            
            [L]1 (41)?        11:05:00 00:00:00
            
            [R]<b>Run time: 00:00:00</b>
            [R]Score: 0
            [R]Status: NR
            """.trimIndent() + "\n",
            text
        )
    }

    @Test
    fun rendersUnmatchedFinishTicketWithCardName() {
        val text = FinishTicketRenderer.render(
            raceData(unmatchedCardName = "Runner Alice"),
            resultId = "unmatched"
        )

        assertEquals("[L]Runner Alice", text.lines()[2])
    }

    @Test
    fun rendersProtectedEffectiveLengthWhenProvided() {
        val text = FinishTicketRenderer.render(
            raceData(),
            resultId = "matched",
            protectedCourseInfoByCategoryId = mapOf(
                "category" to ProtectedCourseInfo(lengthMeters = 5_000, climbMeters = 120)
            )
        )

        assertTrue(text.contains("[R]Effective length: 6.2 km"))
    }

    @Test
    fun truncatesLongCompetitorNamesToPrinterWidth() {
        val text = FinishTicketRenderer.render(raceData(longName = true), resultId = "matched", charactersPerLine = 10)

        assertEquals("[L]VERYLONGNA", text.lines()[2])
    }

    @Test
    fun failsWhenResultCannotBeFound() {
        val error = assertFailsWith<IllegalStateException> {
            FinishTicketRenderer.render(raceData(), resultId = "missing")
        }

        assertEquals("No readout found for result missing.", error.message)
    }

    @Test
    fun formatsTicketMarkupAsPlainTextForSystemPrinters() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched")
        val plainText = FinishTicketPlainTextFormatter.format(text)

        val lines = plainText.lines()
        assertEquals("          Ticket Race", lines[0])
        assertEquals("", lines[1])
        assertEquals("RUNNER Alice", lines[2])
        assertEquals("SI: 123456", lines[3])
        assertEquals("Bib: A-12", lines[4])
        assertEquals("Category: M21", lines[5])
        assertEquals("", lines[6])
        assertEquals("Start          10:00:00", lines[7])
        assertEquals(lines[8].indexOf("10:05:00"), lines[7].indexOf("10:00:00"))
        assertEquals("1 (Foxhole)OK  10:05:00 00:05:00", lines[8])
        assertEquals("2 (32)MP       10:10:00 00:10:00", lines[9])
        assertEquals("Finish         10:20:00 00:20:00", lines[10])
        assertEquals("", lines[11])
        assertEquals("Run time: 00:20:00".padStart(32), lines[12])
        assertEquals("Score: 2".padStart(32), lines[13])
        assertEquals("Status: OK".padStart(32), lines[14])
    }

    @Test
    fun formatsUnmatchedTicketMarkupAsPlainTextForSystemPrinters() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "unmatched")
        val plainText = FinishTicketPlainTextFormatter.format(text)

        val lines = plainText.lines()
        assertEquals("          Ticket Race", lines[0])
        assertEquals("", lines[1])
        assertEquals("?", lines[2])
        assertEquals("SI: 654321", lines[3])
        assertEquals("Category: ?", lines[4])
        assertEquals("", lines[5])
        assertEquals("1 (41)?        11:05:00 00:00:00", lines[6])
        assertEquals("", lines[7])
        assertEquals("Run time: 00:00:00".padStart(32), lines[8])
        assertEquals("Score: 0".padStart(32), lines[9])
        assertEquals("Status: NR".padStart(32), lines[10])
    }

    @Test
    fun fixedWidthRowsKeepTimesAlignedAcrossStatusSuffixes() {
        val rows = listOf(
            FinishTicketTimeRowFormatter.format("Start", "10:00:00", null, 32),
            FinishTicketTimeRowFormatter.format("Finish", "10:20:00", "00:20:00", 32),
            FinishTicketTimeRowFormatter.format("1FOK", "10:05:00", "00:05:00", 32),
            FinishTicketTimeRowFormatter.format("2F?", "10:10:00", "00:10:00", 32),
            FinishTicketTimeRowFormatter.format("3F+", "10:15:00", "00:15:00", 32)
        )

        val timeColumn = rows.first().indexOf("10:00:00")
        assertTrue(rows.all { it.length <= 32 })
        assertEquals(
            listOf(timeColumn),
            rows.map { row -> row.indexOf(Regex("\\d\\d:\\d\\d:\\d\\d").find(row)!!.value) }.distinct()
        )
    }

    @Test
    fun omitsBlankBibNumberFromTicketHeader() {
        val text = FinishTicketRenderer.render(raceData(bibNumber = ""), resultId = "matched")

        assertTrue("Bib:" !in text)
        assertEquals("[L]Category: M21", text.lines()[4])
    }

    @Test
    fun rendersRadioOTicketWithRawSiCodesWhenAliasesAreDisabled() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched", useAliases = false)

        assertEquals("31OK", text.lines()[8].substringAfter("[L]").take(14).trimEnd())
        assertEquals("32MP", text.lines()[9].substringAfter("[L]").take(14).trimEnd())
    }

    @Test
    fun rendersRadioOTicketWithNumberedAliasesWhenAliasesAreEnabled() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched", useAliases = true)

        assertEquals("1 (Foxhole)OK", text.lines()[8].substringAfter("[L]").take(14).trimEnd())
        assertEquals("2 (32)MP", text.lines()[9].substringAfter("[L]").take(14).trimEnd())
    }

    @Test
    fun rendersRadioOTicketWithGlobalControlLabelsBeforeLegacyAliases() {
        val text = FinishTicketRenderer.render(
            raceData(
                controls = listOf(
                    EventControl(
                        id = "control-31",
                        raceId = "race",
                        label = "F1",
                        siCode = 31,
                        type = ControlPointType.CONTROL,
                        publicLabel = "1"
                    )
                )
            ),
            resultId = "matched",
            useAliases = true
        )

        assertEquals("1 (1)OK", text.lines()[8].substringAfter("[L]").take(14).trimEnd())
        assertEquals("2 (32)MP", text.lines()[9].substringAfter("[L]").take(14).trimEnd())
    }

    @Test
    fun leavesOrienteeringTicketControlLabelsUnchanged() {
        val text = FinishTicketRenderer.render(
            raceData(raceType = RaceType.ORIENTEERING),
            resultId = "matched",
            useAliases = true
        )

        assertEquals("1 (Foxhole)OK", text.lines()[8].substringAfter("[L]").take(14).trimEnd())
        assertEquals("2 (32)MP", text.lines()[9].substringAfter("[L]").take(14).trimEnd())
    }

    private fun raceData(
        longName: Boolean = false,
        raceType: RaceType = RaceType.CLASSIC,
        unmatchedCardName: String? = null,
        controls: List<EventControl> = emptyList(),
        bibNumber: String = "A-12"
    ): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Ticket Race",
            apiKey = "",
            startDateTimeIso = "2026-05-31T10:00",
            raceType = raceType,
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
            controlPointsString = "31 32"
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = if (longName) "Longfirstname" else "Alice",
            lastName = if (longName) "Verylongname" else "Runner",
            club = "",
            index = "A-12",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null,
            bibNumber = bibNumber
        )
        val alias = EventAlias(
            id = "alias",
            raceId = race.id,
            siCode = 31,
            name = "Foxhole"
        )
        return EventRaceData(
            race = race,
            categories = emptyList(),
            aliases = listOf(alias),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout(
                        id = "matched",
                        competitorId = competitor.id,
                        siNumber = 123456,
                        resultStatus = ResultStatus.OK,
                        points = 2,
                        punches = listOf(
                            punch("start", 0, 0, SIRecordType.START, PunchStatus.VALID, 36_000, 0, null),
                            punch("p1", 31, 1, SIRecordType.CONTROL, PunchStatus.VALID, 36_300, 300, alias),
                            punch("p2", 32, 2, SIRecordType.CONTROL, PunchStatus.INVALID, 36_600, 600, null),
                            punch("finish", 0, 0, SIRecordType.FINISH, PunchStatus.VALID, 37_200, 1_200, null)
                        )
                    )
                )
            ),
            unmatchedReadoutData = listOf(
                readout(
                    id = "unmatched",
                    competitorId = null,
                    siNumber = 654321,
                    resultStatus = ResultStatus.NO_RANKING,
                    points = 0,
                    runTimeSeconds = 0,
                    cardName = unmatchedCardName,
                    punches = listOf(
                        punch("u1", 41, 1, SIRecordType.CONTROL, PunchStatus.UNKNOWN, 39_900, 0, null)
                    )
                )
            ),
            controls = controls
        )
    }

    private fun readout(
        id: String,
        competitorId: String?,
        siNumber: Int,
        resultStatus: ResultStatus,
        points: Int,
        runTimeSeconds: Long = 1_200,
        cardName: String? = null,
        punches: List<EventAliasPunch>
    ): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = competitorId,
                siNumber = siNumber,
                cardType = 10,
                checkTimeSeconds = null,
                startTimeSeconds = null,
                finishTimeSeconds = null,
                readoutDateTimeIso = "2026-05-31T11:00",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = points,
                runTimeSeconds = runTimeSeconds,
                modified = false,
                sent = false,
                cardName = cardName
            ),
            punches = punches
        )

    private fun punch(
        id: String,
        siCode: Int,
        order: Int,
        type: SIRecordType,
        status: PunchStatus,
        timeSeconds: Long,
        splitSeconds: Long,
        alias: EventAlias?
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = "race",
                resultId = "matched",
                cardNumber = 123456,
                siCode = siCode,
                siTimeSeconds = timeSeconds,
                originalSiTimeSeconds = timeSeconds,
                punchType = type,
                order = order,
                punchStatus = status,
                splitSeconds = splitSeconds
            ),
            alias = alias
        )
}
