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
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched", charactersPerLine = 14)

        assertEquals(
            """
            [C]<b>Ticket Race</b>
            [L]
            [L]RUNNER Alice
            [L]SI: 123456 A-12
            [L]M21
            
            [L]Start[R]10:00:00[R] 
            [L]1 (Foxhole)OK[R]10:05:00[R]00:05:00
            [L]2 (32)MP[R]10:10:00[R]00:10:00
            [L]Finish[R]10:20:00[R]00:20:00
            
            [R]<b>Run time: 00:20:00 OK</b>
            [R]2 Controls
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
            [L]SI: 654321 ?
            [L]?
            
            [L]1 (41)?[R]11:05:00[R]00:00:00
            
            [R]<b>Run time: 00:00:00 No ranking</b>
            [R]0 Controls
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
        assertEquals("SI: 123456 A-12", lines[3])
        assertEquals("M21", lines[4])
        assertEquals("", lines[5])
        assertEquals("Start                   10:00:00", lines[6])
        assertEquals("1 (Foxhole)OK  10:05:00 00:05:00", lines[7])
        assertEquals("2 (32)MP       10:10:00 00:10:00", lines[8])
        assertEquals("Finish         10:20:00 00:20:00", lines[9])
        assertEquals("", lines[10])
        assertEquals("           Run time: 00:20:00 OK", lines[11])
        assertEquals("                      2 Controls", lines[12])
    }

    @Test
    fun formatsUnmatchedTicketMarkupAsPlainTextForSystemPrinters() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "unmatched")
        val plainText = FinishTicketPlainTextFormatter.format(text, charactersPerLine = 24)

        val lines = plainText.lines()
        assertEquals("      Ticket Race", lines[0])
        assertEquals("", lines[1])
        assertEquals("?", lines[2])
        assertEquals("SI: 654321 ?", lines[3])
        assertEquals("?", lines[4])
        assertEquals("", lines[5])
        assertEquals("1 (41) 11:05:00 00:00:00", lines[6])
        assertEquals("", lines[7])
        assertEquals(" Run time: 00:00:00 No ra", lines[8])
        assertEquals("              0 Controls", lines[9])
    }

    @Test
    fun rendersRadioOTicketWithRawSiCodesWhenAliasesAreDisabled() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched", useAliases = false)

        assertEquals("31OK", text.lines()[7].substringAfter("[L]").substringBefore("[R]"))
        assertEquals("32MP", text.lines()[8].substringAfter("[L]").substringBefore("[R]"))
    }

    @Test
    fun rendersRadioOTicketWithNumberedAliasesWhenAliasesAreEnabled() {
        val text = FinishTicketRenderer.render(raceData(), resultId = "matched", useAliases = true)

        assertEquals("1 (Foxhole)OK", text.lines()[7].substringAfter("[L]").substringBefore("[R]"))
        assertEquals("2 (32)MP", text.lines()[8].substringAfter("[L]").substringBefore("[R]"))
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

        assertEquals("1 (1)OK", text.lines()[7].substringAfter("[L]").substringBefore("[R]"))
        assertEquals("2 (32)MP", text.lines()[8].substringAfter("[L]").substringBefore("[R]"))
    }

    @Test
    fun leavesOrienteeringTicketControlLabelsUnchanged() {
        val text = FinishTicketRenderer.render(
            raceData(raceType = RaceType.ORIENTEERING),
            resultId = "matched",
            useAliases = true
        )

        assertEquals("1 (Foxhole)OK", text.lines()[7].substringAfter("[L]").substringBefore("[R]"))
        assertEquals("2 (32)MP", text.lines()[8].substringAfter("[L]").substringBefore("[R]"))
    }

    private fun raceData(
        longName: Boolean = false,
        raceType: RaceType = RaceType.CLASSIC,
        unmatchedCardName: String? = null,
        controls: List<EventControl> = emptyList()
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
            drawnStartTimeSeconds = null
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
