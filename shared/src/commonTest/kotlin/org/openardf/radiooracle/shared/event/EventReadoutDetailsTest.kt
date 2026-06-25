package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventReadoutDetailsTest {
    @Test
    fun buildsDisplayRowsForMatchedAndUnmatchedReadouts() {
        val rows = EventReadoutDetails.from(raceData())

        assertEquals(2, rows.size)
        assertEquals("matched", rows[0].id)
        assertEquals("123456", rows[0].siNumberText)
        assertEquals("RUNNER Alice", rows[0].competitorName)
        assertEquals(ResultStatus.OK, rows[0].resultStatus)
        assertEquals(true, rows[0].automaticStatus)
        assertEquals("OK", rows[0].statusLabel)
        assertEquals("3", rows[0].pointsText)
        assertEquals("00:20:00", rows[0].runTimeText)
        assertEquals("Foxhole 32", rows[0].punchCodesText)
        assertEquals(false, rows[0].hasWarning)

        assertEquals("unmatched", rows[1].id)
        assertEquals("654321", rows[1].siNumberText)
        assertEquals("", rows[1].competitorName)
        assertEquals(ResultStatus.NO_RANKING, rows[1].resultStatus)
        assertEquals(true, rows[1].automaticStatus)
        assertEquals("No ranking", rows[1].statusLabel)
        assertEquals("41", rows[1].punchCodesText)
    }

    @Test
    fun hidesScoreAndRunTimeForErrorReadouts() {
        val rows = EventReadoutDetails.from(
            raceData(
                matchedResultStatus = ResultStatus.OK,
                matchedStartTimeSeconds = 1_000,
                matchedFinishTimeSeconds = 500,
                matchedRunTimeSeconds = -110L * 3_600L - 12L
            )
        )

        assertEquals("", rows[0].pointsText)
        assertEquals("ERR", rows[0].runTimeText)
        assertEquals(true, rows[0].hasWarning)
    }

    @Test
    fun buildsRadioOReadoutRowsWithRawSiCodesWhenAliasesAreDisabled() {
        val rows = EventReadoutDetails.from(raceData(), useAliases = false)

        assertEquals("31 32", rows[0].punchCodesText)
    }

    @Test
    fun prefersGlobalControlLabelsOverLegacyAliases() {
        val rows = EventReadoutDetails.from(
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
            )
        )

        assertEquals("1 32", rows[0].punchCodesText)
    }

    @Test
    fun usesCardNameForUnmatchedReadouts() {
        val rows = EventReadoutDetails.from(raceData(unmatchedCardName = "Runner Alice"))

        assertEquals("Runner Alice", rows[1].competitorName)
    }

    @Test
    fun leavesOrienteeringReadoutRowsUnchanged() {
        val rows = EventReadoutDetails.from(raceData(raceType = RaceType.ORIENTEERING), useAliases = true)

        assertEquals("31 32", rows[0].punchCodesText)
    }

    private fun raceData(
        raceType: RaceType = RaceType.CLASSIC,
        unmatchedCardName: String? = null,
        controls: List<EventControl> = emptyList(),
        matchedResultStatus: ResultStatus = ResultStatus.OK,
        matchedStartTimeSeconds: Long = 600,
        matchedFinishTimeSeconds: Long = 1_800,
        matchedRunTimeSeconds: Long = 1_200
    ): EventRaceData {
        val alias = EventAlias(
            id = "alias",
            raceId = "race",
            siCode = 31,
            name = "Foxhole"
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = "race",
            categoryId = null,
            firstName = "Alice",
            lastName = "Runner",
            club = "",
            index = "",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Readout Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = raceType,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = listOf(alias),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category = null),
                    readoutData = readout(
                        "matched",
                        competitor.id,
                        123456,
                        matchedResultStatus,
                        listOf(31, 32),
                        alias,
                        startTimeSeconds = matchedStartTimeSeconds,
                        finishTimeSeconds = matchedFinishTimeSeconds,
                        runTimeSeconds = matchedRunTimeSeconds
                    )
                )
            ),
            unmatchedReadoutData = listOf(
                readout(
                    "unmatched",
                    competitorId = null,
                    siNumber = 654321,
                    ResultStatus.NO_RANKING,
                    listOf(41),
                    cardName = unmatchedCardName
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
        controlCodes: List<Int>,
        alias: EventAlias? = null,
        cardName: String? = null,
        startTimeSeconds: Long = 600,
        finishTimeSeconds: Long = 1_800,
        runTimeSeconds: Long = 1_200
    ): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = competitorId,
                siNumber = siNumber,
                cardType = 10,
                checkTimeSeconds = null,
                startTimeSeconds = startTimeSeconds,
                finishTimeSeconds = finishTimeSeconds,
                readoutDateTimeIso = "2026-05-31T11:00",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = 3,
                runTimeSeconds = runTimeSeconds,
                modified = false,
                sent = false,
                cardName = cardName
            ),
            punches = controlCodes.mapIndexed { index, siCode ->
                EventAliasPunch(
                    punch = EventPunch(
                        id = "punch-$index",
                        raceId = "race",
                        resultId = id,
                        cardNumber = siNumber,
                        siCode = siCode,
                        siTimeSeconds = 700L + index,
                        originalSiTimeSeconds = 700L + index,
                        punchType = SIRecordType.CONTROL,
                        order = index,
                        punchStatus = PunchStatus.UNKNOWN,
                        splitSeconds = 0
                    ),
                    alias = alias?.takeIf { it.siCode == siCode }
                )
            }
        )
}
