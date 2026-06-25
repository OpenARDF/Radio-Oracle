package org.openardf.radiooracle.backend.sportident

import kotlinx.coroutines.runBlocking
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

    private fun raceData(name: String, controls: List<Int>, siNumber: Int): RaceData {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val category = Category(
            id = categoryId,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = 0,
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
                raceLevel = RaceLevel.PRACTICE,
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
            competitorData = listOf(
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
