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

package org.openardf.radiooracle.backend

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DataProcessorEventSeriesMembershipTests {
    @Before
    fun initializeBackend() {
        val context = RuntimeEnvironment.getApplication()
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun createAddAndRemoveSeriesMembershipKeepsEventsOnDevice() = runBlocking {
        val processor = DataProcessor.get()
        val dayOne = raceData(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            name = "Membership Day 1"
        )
        val dayTwo = raceData(
            id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            name = "Membership Day 2"
        )
        processor.saveRaceData(dayOne)
        processor.saveRaceData(dayTwo)

        val created = processor.createEventSeriesFromRace(dayOne.race.id, "Membership Series")
        val complete = processor.addRaceToEventSeries(dayTwo.race.id, created.series.seriesId)

        assertEquals(listOf(dayOne.race.id, dayTwo.race.id), complete.orderedMembers().map { it.localRaceId })
        assertEquals(complete.series.seriesId, processor.getEventSeriesForRace(dayTwo.race.id)?.series?.seriesId)

        val afterRemovingDayTwo = processor.removeRaceFromEventSeries(dayTwo.race.id)

        assertNotNull(afterRemovingDayTwo)
        assertEquals(listOf(dayOne.race.id), afterRemovingDayTwo!!.orderedMembers().map { it.localRaceId })
        assertNull(processor.getEventSeriesForRace(dayTwo.race.id))
        assertNotNull(processor.getRace(dayTwo.race.id))

        val afterRemovingFinalEvent = processor.removeRaceFromEventSeries(dayOne.race.id)

        assertNull(afterRemovingFinalEvent)
        assertNull(processor.getEventSeries(complete.series.seriesId))
        assertNotNull(processor.getRace(dayOne.race.id))
        assertNotNull(processor.getRace(dayTwo.race.id))
    }

    @Test
    fun renameSeriesKeepsExistingMembers() = runBlocking {
        val processor = DataProcessor.get()
        val dayOne = raceData(
            id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            name = "Rename Day 1"
        )
        val dayTwo = raceData(
            id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            name = "Rename Day 2"
        )
        processor.saveRaceData(dayOne)
        processor.saveRaceData(dayTwo)

        val created = processor.createEventSeriesFromRace(dayOne.race.id, "Old Name")
        processor.addRaceToEventSeries(dayTwo.race.id, created.series.seriesId)

        val renamed = processor.renameEventSeries(created.series.seriesId, "  New Name  ")

        assertEquals("New Name", renamed.series.name)
        assertEquals(listOf(dayOne.race.id, dayTwo.race.id), renamed.orderedMembers().map { it.localRaceId })
        assertEquals("New Name", processor.getEventSeries(created.series.seriesId)?.series?.name)
    }

    @Test
    fun seriesResultLabelsFollowResultRaceInsteadOfStaleMemberName() = runBlocking {
        val processor = DataProcessor.get()
        val classic = raceDataWithResult(
            id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
            name = "2m Classic"
        )
        val sprint = raceDataWithResult(
            id = UUID.fromString("66666666-6666-6666-6666-666666666666"),
            name = "Sprint Practice"
        )
        processor.saveRaceData(classic)
        processor.saveRaceData(sprint)

        val created = processor.createEventSeriesFromRace(classic.race.id, "Practice Series")
        val complete = processor.addRaceToEventSeries(sprint.race.id, created.series.seriesId)
        processor.saveEventSeries(
            complete.series,
            complete.orderedMembers().map { member ->
                member.copy(
                    displayName = if (member.localRaceId == classic.race.id) {
                        "Sprint Practice"
                    } else {
                        "2m Classic"
                    }
                )
            }
        )

        val selectorMembers = processor.getEventSeries().first().single().orderedMembers()
        val wrappers = processor.getSeriesResultWrapperFlowForRace(classic.race.id)!!.first()

        assertEquals(listOf("2m Classic", "Sprint Practice"), selectorMembers.map { it.displayName })
        assertEquals(listOf(classic.race.id, sprint.race.id), selectorMembers.map { it.localRaceId })
        assertEquals(listOf("2m Classic - M21", "Sprint Practice - M21"), wrappers.map { it.displayLabel })
        assertEquals(
            listOf(classic.race.id, sprint.race.id),
            wrappers.map { it.competitorData.single().readoutData!!.result.raceId }
        )
    }

    @Test
    fun selectedRaceViewModelShowsOnlySelectedRaceResultsWithinSeries() = runBlocking {
        val processor = DataProcessor.get()
        val dayOne = raceDataWithResult(
            id = UUID.fromString("77777777-7777-7777-7777-777777777777"),
            name = "Results Day 1"
        )
        val dayTwo = raceDataWithResult(
            id = UUID.fromString("88888888-8888-8888-8888-888888888888"),
            name = "Results Day 2"
        )
        processor.saveRaceData(dayOne)
        processor.saveRaceData(dayTwo)
        val series = processor.createEventSeriesFromRace(dayOne.race.id, "Results Series")
        processor.addRaceToEventSeries(dayTwo.race.id, series.series.seriesId)

        val viewModel = SelectedRaceViewModel()
        try {
            viewModel.setRace(dayOne.race.id)

            val wrappers = withTimeout(5_000) {
                viewModel.resultWrappers.first { it.isNotEmpty() }
            }

            assertEquals(1, wrappers.size)
            assertEquals(
                dayOne.race.id,
                wrappers.single().competitorData.single().readoutData!!.result.raceId
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun raceData(id: UUID, name: String): RaceData =
        RaceData(
            race = Race(
                id = id,
                name = name,
                apiKey = "",
                startDateTime = LocalDateTime.of(2026, 6, 18, 9, 0),
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimit = Duration.ofHours(2)
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun raceDataWithResult(id: UUID, name: String): RaceData {
        val category = Category(
            id = UUID.randomUUID(),
            raceId = id,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = 5_000,
            climb = 100,
            order = 0,
            controlPointsString = ""
        )
        val competitor = Competitor(
            id = UUID.randomUUID(),
            raceId = id,
            categoryId = category.id,
            firstName = "Test",
            lastName = "Runner",
            club = "",
            index = "",
            startNumber = 1
        )
        val result = Result().copy(
            id = UUID.randomUUID(),
            raceId = id,
            competitorId = competitor.id,
            resultStatus = ResultStatus.OK,
            runTime = Duration.ofMinutes(30)
        )
        return RaceData(
            race = raceData(id, name).race,
            categories = listOf(CategoryData(category, emptyList(), listOf(competitor))),
            aliases = emptyList(),
            competitorData = listOf(
                CompetitorData(
                    competitorCategory = CompetitorCategory(competitor, category),
                    readoutData = ReadoutData(result, emptyList())
                )
            ),
            unmatchedReadoutData = emptyList()
        )
    }
}
