package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventResultSendingTest {
    @Test
    fun returnsCompetitorIdsWithUnsentReadouts() {
        val results = listOf(
            competitor("missing", readout = null),
            competitor("sent", readout = result(sent = true)),
            competitor("unsent", readout = result(sent = false))
        )

        assertEquals(setOf("unsent"), EventResultSending.unsentCompetitorIds(results))
    }

    @Test
    fun buildsSendPlanForMatchedUnsentResults() {
        val plan = EventResultSending.plan(
            raceData(
                listOf(
                    competitor("missing", readout = null),
                    competitor("sent", readout = result(id = "sent-result", sent = true, siNumber = 101)),
                    competitor("unsent", readout = result(id = "unsent-result", sent = false, siNumber = 102))
                ),
                unmatchedReadouts = listOf(result(id = "unmatched-result", sent = false, siNumber = 999))
            )
        )

        assertTrue(plan.hasCandidates)
        assertEquals(1, plan.candidateCount)
        assertEquals("unsent", plan.candidates.single().competitorId)
        assertEquals("unsent-result", plan.candidates.single().resultId)
        assertEquals(102, plan.candidates.single().siNumber)
        assertEquals(1, plan.alreadySentCount)
        assertEquals(1, plan.missingReadoutCount)
        assertEquals(1, plan.unmatchedReadoutCount)
    }

    @Test
    fun reportsNoCandidatesWhenAllMatchedReadoutsAreSent() {
        val plan = EventResultSending.plan(
            raceData(
                listOf(
                    competitor("sent-one", readout = result(sent = true)),
                    competitor("sent-two", readout = result(sent = true))
                )
            )
        )

        assertFalse(plan.hasCandidates)
        assertEquals(0, plan.candidateCount)
        assertEquals(2, plan.alreadySentCount)
        assertEquals(0, plan.missingReadoutCount)
        assertEquals(0, plan.unmatchedReadoutCount)
    }

    private fun competitor(id: String, readout: EventResult?): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = null,
                    firstName = id,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    startNumber = 1,
                    drawnStartTimeSeconds = null
                ),
                category = null
            ),
            readoutData = readout?.let { EventReadoutData(result = it, punches = emptyList()) }
        )

    private fun raceData(
        competitors: List<EventCompetitorData>,
        unmatchedReadouts: List<EventResult> = emptyList()
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Live Race",
                apiKey = "",
                startDateTimeIso = "2026-05-30T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = competitors,
            unmatchedReadoutData = unmatchedReadouts.map { EventReadoutData(result = it, punches = emptyList()) }
        )

    private fun result(
        id: String = "result",
        sent: Boolean,
        siNumber: Int? = null
    ): EventResult =
        EventResult(
            id = id,
            raceId = "race",
            competitorId = null,
            siNumber = siNumber,
            cardType = 5,
            checkTimeSeconds = null,
            startTimeSeconds = null,
            finishTimeSeconds = null,
            readoutDateTimeIso = "2026-05-30T10:00",
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 0,
            runTimeSeconds = 0,
            modified = false,
            sent = sent
        )
}
