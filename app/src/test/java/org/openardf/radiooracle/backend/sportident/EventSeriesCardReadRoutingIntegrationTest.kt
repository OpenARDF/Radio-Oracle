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

package org.openardf.radiooracle.backend.sportident

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.sportident.SIConstants.SI_CARD6
import org.openardf.radiooracle.backend.sportident.SIPort.CardData
import org.openardf.radiooracle.backend.sportident.SIPort.PunchData
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventSeriesCardReadRoutingIntegrationTest {
    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun persistedSeriesMembersProvideRoutingCandidatesForCurrentEvent() = runBlocking {
        val day1 = raceData("Series Routing Day 1", listOf(31, 32), siNumber = 1001)
        val day2 = raceData("Series Routing Day 2", listOf(41, 42), siNumber = 1001)
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")

        DataProcessor.get().saveRaceData(day1)
        DataProcessor.get().saveRaceData(day2)
        DataProcessor.get().saveEventSeries(
            series = series,
            members = listOf(
                member(series.seriesId, "day-1", day1, order = 0),
                member(series.seriesId, "day-2", day2, order = 1)
            )
        )

        val routeCandidates = DataProcessor.get().getEventSeriesForRace(day1.race.id)!!
            .orderedMembers()
            .map { member ->
                EventSeriesReadoutMemberData(
                    member = member,
                    raceData = DataProcessor.get().getRaceData(member.localRaceId)
                )
            }
        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 1001, punches = listOf(41, 42)),
            members = routeCandidates
        )

        assertTrue(route is EventSeriesReadoutRoute.Matched)
        assertEquals(listOf("day-1", "day-2"), routeCandidates.map { it.member.seriesEventId })
        assertSame(routeCandidates[1], (route as EventSeriesReadoutRoute.Matched).memberData)
        assertEquals(day2.race.id, route.memberData.raceData.race.id)
    }

    @Test
    fun practiceSeriesCardReadStoresResultInMatchedEventAndRequestsSelection() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData("Practice Day 1", listOf(31, 32), siNumber = 1001)
        val day2 = raceData("Practice Day 2", listOf(41, 42), siNumber = 1001)
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(day1)
        processor.saveRaceData(day2)
        processor.saveEventSeries(
            series = series,
            members = listOf(
                member(series.seriesId, "day-1", day1, order = 0),
                member(series.seriesId, "day-2", day2, order = 1)
            )
        )

        val selectionRequest = async {
            withTimeout(1_000) { processor.raceSelectionRequests.first() }
        }
        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(41, 42)),
            currentRace = day1.race
        )

        assertEquals(true, stored)
        assertEquals(day2.race.id, selectionRequest.await())
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(day1.race.id))
        assertEquals(
            listOf(ResultDataSummary(raceId = day2.race.id, siNumber = 1001)),
            processor.resultSummaries(day2.race.id)
        )
    }

    @Test
    fun unknownPracticeSeriesCardCreatesCategorizedCompetitorInEveryRace() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData("Practice Day 1", listOf(31, 32), siNumber = null)
        val day2 = raceData("Practice Day 2", listOf(41, 42), siNumber = null)
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(day1)
        processor.saveRaceData(day2)
        processor.saveEventSeries(
            series,
            listOf(
                member(series.seriesId, "day-1", day1, 0),
                member(series.seriesId, "day-2", day2, 1)
            )
        )

        val selectionRequest = async { withTimeout(1_000) { processor.raceSelectionRequests.first() } }
        assertEquals(
            true,
            processor.processCardDataForCurrentRaceOrSeries(
                card(2002, listOf(41, 42)).copy(cardName = "Runner Alice"),
                day1.race
            )
        )
        assertEquals(day2.race.id, selectionRequest.await())

        listOf(day1, day2).forEach { raceData ->
            val competitor = processor.getCompetitorBySINumber(2002, raceData.race.id)!!
            assertEquals("M21", processor.getCategory(competitor.categoryId!!)!!.name)
            assertEquals("Alice", competitor.firstName)
            assertEquals("Runner", competitor.lastName)
        }
        assertEquals(2002, processor.resultSummaries(day2.race.id).single().siNumber)
    }

    @Test
    fun clearedPracticeSeriesCardStartsCategorizedCompetitorInForestWithoutResult() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData("Practice Day 1", listOf(31), siNumber = null, courseLength = 3_000)
        val day2 = raceData("Practice Day 2", listOf(41), siNumber = null, courseLength = 3_000)
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(day1)
        processor.saveRaceData(day2)
        processor.saveEventSeries(
            series,
            listOf(
                member(series.seriesId, "day-1", day1, 0),
                member(series.seriesId, "day-2", day2, 1)
            )
        )
        val blankCard = CardData(
            cardType = SI_CARD6,
            siNumber = 2003,
            cardName = "Runner Bob",
            punchData = arrayListOf()
        )

        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(blankCard, day1.race))

        listOf(day1, day2).forEach { raceData ->
            val competitor = processor.getCompetitorBySINumber(2003, raceData.race.id)!!
            assertEquals("M21", processor.getCategory(competitor.categoryId!!)!!.name)
            assertTrue(competitor.drawnRelativeStartTime != null)
            assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(raceData.race.id))
        }

        val selectionRequest = async { withTimeout(1_000) { processor.raceSelectionRequests.first() } }
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(card(2003, listOf(41)), day1.race))
        assertEquals(day2.race.id, selectionRequest.await())
        listOf(day1, day2).forEach { raceData ->
            val competitor = processor.getCompetitorBySINumber(2003, raceData.race.id)!!
            assertEquals(null, competitor.drawnRelativeStartTime)
        }
    }

    @Test
    fun nonPracticeSeriesCardReadStoresResultInMatchedEventAndRequestsSelection() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData(
            "Regional Day 1",
            listOf(31, 32),
            siNumber = 1001,
            raceLevel = RaceLevel.REGIONAL
        )
        val day2 = raceData(
            "Regional Day 2",
            listOf(41, 42),
            siNumber = 1001,
            raceLevel = RaceLevel.REGIONAL
        )
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(day1)
        processor.saveRaceData(day2)
        processor.saveEventSeries(
            series = series,
            members = listOf(
                member(series.seriesId, "day-1", day1, order = 0),
                member(series.seriesId, "day-2", day2, order = 1)
            )
        )

        val selectionRequest = async {
            withTimeout(1_000) { processor.raceSelectionRequests.first() }
        }
        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(41, 42)),
            currentRace = day1.race
        )

        assertEquals(true, stored)
        assertEquals(day2.race.id, selectionRequest.await())
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(day1.race.id))
        assertEquals(
            listOf(ResultDataSummary(raceId = day2.race.id, siNumber = 1001)),
            processor.resultSummaries(day2.race.id)
        )
    }

    @Test
    fun mixedLevelSeriesCardReadRoutesFromChampionshipToPracticeEvent() = runBlocking {
        val processor = DataProcessor.get()
        val championship = raceData(
            "Championship",
            listOf(31, 32),
            siNumber = 1001,
            raceLevel = RaceLevel.REGIONAL
        )
        val practice = raceData(
            "Practice",
            listOf(41, 42),
            siNumber = 1001,
            raceLevel = RaceLevel.PRACTICE
        )
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(championship)
        processor.saveRaceData(practice)
        processor.saveEventSeries(
            series = series,
            members = listOf(
                member(series.seriesId, "championship", championship, order = 0),
                member(series.seriesId, "practice", practice, order = 1)
            )
        )

        val selectionRequest = async {
            withTimeout(1_000) { processor.raceSelectionRequests.first() }
        }
        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(41, 42)),
            currentRace = championship.race
        )

        assertEquals(true, stored)
        assertEquals(practice.race.id, selectionRequest.await())
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(championship.race.id))
        assertEquals(
            listOf(ResultDataSummary(raceId = practice.race.id, siNumber = 1001)),
            processor.resultSummaries(practice.race.id)
        )
    }

    private suspend fun DataProcessor.resultSummaries(raceId: UUID): List<ResultDataSummary> =
        getResultDataFlowByRace(raceId).first().map { resultData ->
            ResultDataSummary(
                raceId = resultData.result.raceId,
                siNumber = resultData.result.siNumber
            )
        }

    private data class ResultDataSummary(
        val raceId: UUID,
        val siNumber: Int?
    )

    private fun raceData(
        name: String,
        controls: List<Int>,
        siNumber: Int?,
        raceLevel: RaceLevel = RaceLevel.PRACTICE,
        courseLength: Int = 0
    ): RaceData {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val category = Category(
            id = categoryId,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = courseLength,
            climb = 0,
            order = 0,
            controlPointsString = controls.joinToString(",")
        )
        return RaceData(
            race = Race(
                id = raceId,
                name = name,
                apiKey = "",
                startDateTime = LocalDateTime.of(2026, 6, 20, 9, 0),
                raceType = RaceType.CLASSIC,
                raceLevel = raceLevel,
                raceBand = RaceBand.M80,
                timeLimit = Duration.ofHours(2)
            ),
            categories = listOf(
                CategoryData(
                    category = category,
                    controlPoints = controls.mapIndexed { index, siCode ->
                        ControlPoint(
                            id = UUID.randomUUID(),
                            categoryId = categoryId,
                            siCode = siCode,
                            type = ControlPointType.CONTROL,
                            order = index + 1
                        )
                    },
                    competitors = emptyList()
                )
            ),
            aliases = emptyList(),
            competitorData = if (siNumber == null) emptyList() else listOf(
                CompetitorData(
                    competitorCategory = CompetitorCategory(
                        competitor = Competitor(
                            id = UUID.randomUUID(),
                            raceId = raceId,
                            categoryId = categoryId,
                            firstName = "Runner",
                            lastName = siNumber.toString(),
                            club = "",
                            index = "",
                            isMan = true,
                            birthYear = null,
                            siNumber = siNumber,
                            siRent = false,
                            startNumber = 0
                        ),
                        category = category
                    ),
                    readoutData = null
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }

    private fun member(seriesId: String, seriesEventId: String, raceData: RaceData, order: Int): EventSeriesMember =
        EventSeriesMember(
            seriesId = seriesId,
            seriesEventId = seriesEventId,
            localRaceId = raceData.race.id,
            eventFilePath = "$seriesEventId.rom.json",
            eventOrder = order,
            displayName = raceData.race.name,
            startDateTimeIso = raceData.race.startDateTime.toString(),
            formatLabel = "Classic"
        )

    private fun card(siNumber: Int, punches: List<Int>): CardData =
        CardData(
            cardType = SI_CARD6,
            siNumber = siNumber,
            startTime = SITime(9 * 60 * 60),
            finishTime = SITime(10 * 60 * 60),
            punchData = ArrayList(punches.map { PunchData(it, SITime(10 * 60)) })
        )

}
