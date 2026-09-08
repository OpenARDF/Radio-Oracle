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

import org.openardf.radiooracle.backend.shared.toEventReadoutData
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.resultCompetitorData
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.shared.toRoomRaceData
import org.openardf.radiooracle.backend.room.withFreshImportIds
import org.openardf.radiooracle.backend.shared.withPracticeDisplayNames
import org.openardf.radiooracle.backend.shared.toEventRaceData
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventProjectFile
import androidx.preference.PreferenceManager
import android.os.Looper
import org.openardf.radiooracle.R
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowToast
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
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_readout_error_sounds), false).commit()
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
    fun sameRegisteredCardRoutesToClosestCourseAndStoresResultThere() = runBlocking {
        val processor = DataProcessor.get()
        val shortCourse = raceData("Short course", listOf(31, 32), siNumber = 1001)
        val longerCourse = raceData("Longer course", listOf(31, 32, 33), siNumber = 1001)
        val series = EventSeries(seriesId = "series-routing-${UUID.randomUUID()}", name = "Routing Series")
        processor.saveRaceData(shortCourse)
        processor.saveRaceData(longerCourse)
        processor.saveEventSeries(
            series = series,
            members = listOf(
                member(series.seriesId, "short", shortCourse, order = 0),
                member(series.seriesId, "longer", longerCourse, order = 1)
            )
        )

        val selectionRequest = async {
            withTimeout(1_000) { processor.raceSelectionRequests.first() }
        }
        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(31, 32)),
            currentRace = longerCourse.race
        )

        assertEquals(true, stored)
        assertEquals(shortCourse.race.id, selectionRequest.await())
        assertEquals(
            listOf(ResultDataSummary(raceId = shortCourse.race.id, siNumber = 1001)),
            processor.resultSummaries(shortCourse.race.id)
        )
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(longerCourse.race.id))
    }

    @Test
    fun indistinguishableSeriesCardIsStillStoredInCurrentEvent() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData("Day 1", listOf(31, 32), siNumber = 1001)
        val day2 = raceData("Day 2", listOf(31, 32), siNumber = 1001)
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

        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(31, 32)),
            currentRace = day1.race
        )

        assertEquals(true, stored)
        assertEquals(
            listOf(ResultDataSummary(raceId = day1.race.id, siNumber = 1001)),
            processor.resultSummaries(day1.race.id)
        )
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(day2.race.id))
    }

    @Test
    fun unexpectedSeriesCardControlsAreStillStoredInCurrentEvent() = runBlocking {
        val processor = DataProcessor.get()
        val day1 = raceData("Day 1", listOf(31, 32), siNumber = 1001)
        val day2 = raceData("Day 2", listOf(41, 42), siNumber = 1001)
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

        val stored = processor.processCardDataForCurrentRaceOrSeries(
            cardData = card(siNumber = 1001, punches = listOf(99)),
            currentRace = day1.race
        )

        assertEquals(true, stored)
        assertEquals(
            listOf(ResultDataSummary(raceId = day1.race.id, siNumber = 1001)),
            processor.resultSummaries(day1.race.id)
        )
        assertEquals(emptyList<ResultDataSummary>(), processor.resultSummaries(day2.race.id))
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
        for (level in RaceLevel.entries.filter { it != RaceLevel.PRACTICE }) {
            val processor = DataProcessor.get()
            val day1 = raceData(
                "$level Day 1",
                listOf(31, 32),
                siNumber = 1001,
                raceLevel = level
            )
            val day2 = raceData(
                "$level Day 2",
                listOf(41, 42),
                siNumber = 1001,
                raceLevel = level
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

    @Test
    fun unchangedPracticeDownloadShowsNormalDuplicateWarningAndSound() = runBlocking {
        val processor = DataProcessor.get()
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        listOf(RaceLevel.REGIONAL, RaceLevel.PRACTICE).forEach { level ->
            val event = raceData("Duplicate feedback", listOf(31, 32), siNumber = 1001, raceLevel = level)
            val download = card(1001, listOf(31, 32))
            preferences.edit().putBoolean(context.getString(R.string.key_readout_error_sounds), false)
                .putString(context.getString(R.string.key_readout_duplicate),
                    context.getString(R.string.preferences_readout_duplicate_ignore_value)).commit()
            processor.saveRaceData(event)
            assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(download, event.race))
            val before = processor.getRaceData(event.race.id).toEventRaceData()

            listOf(true, false).forEach { soundEnabled ->
                preferences.edit().putBoolean(context.getString(R.string.key_readout_error_sounds), soundEnabled).commit()
                val players = mutableListOf<ShadowMediaPlayer>()
                ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1000, 0) }
                ShadowMediaPlayer.setCreateListener { _, player -> players.add(player) }
                ShadowToast.reset()

                assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(download, event.race))
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(context.getString(R.string.readout_si_exists, 1001), ShadowToast.getTextOfLatestToast())
                assertEquals(if (soundEnabled) listOf(R.raw.si_duplicate) else emptyList<Int>(),
                    players.map { it.sourceResId })
                assertEquals(before, processor.getRaceData(event.race.id).toEventRaceData())
                ShadowMediaPlayer.resetStaticState()
            }
        }
    }

    @Test
    fun changedPracticeDownloadKeepsNumberingWithoutDuplicateAlert() = runBlocking {
        val processor = DataProcessor.get()
        val context = RuntimeEnvironment.getApplication()
        val event = raceData("Practice changed download", listOf(31, 32), siNumber = 1001)
        val download = card(1001, listOf(31, 32))
        processor.saveRaceData(event)
        processor.processCardDataForCurrentRaceOrSeries(download, event.race)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.key_readout_error_sounds), true).commit()
        val players = mutableListOf<ShadowMediaPlayer>()
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1000, 0) }
        ShadowMediaPlayer.setCreateListener { _, player -> players.add(player) }
        ShadowToast.reset()

        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(
            download.copy(finishTime = SITime(36001)), event.race))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
        assertTrue(players.isEmpty())
        assertEquals(setOf("Runner", "Runner (2)"), processor.getRaceData(event.race.id).competitorData.withPracticeDisplayNames()
            .map { it.competitorCategory.competitor.firstName }.toSet())
        assertEquals(2, processor.resultSummaries(event.race.id).size)
        ShadowMediaPlayer.resetStaticState()
    }

    @Test
    fun raceSnapshotRetainsRegisteredCompetitorsWithoutReadouts() = runBlocking {
        val processor = DataProcessor.get()
        val event = raceData("Practice registration", listOf(31, 32), siNumber = 1001)
        processor.saveRaceData(event)

        assertEquals(event.competitorData.single().competitorCategory.competitor.id,
            processor.getRaceData(event.race.id).competitorData.single().competitorCategory.competitor.id)
        assertTrue(processor.resultSummaries(event.race.id).isEmpty())
    }

    @Test
    fun practiceCardReusesRegisteredCompetitorWhenHistoricalCardResultBelongsToAnotherEntry() = runBlocking {
        val processor = DataProcessor.get()
        val event = raceData("Practice registration", listOf(31, 32), siNumber = 1001)
        processor.saveRaceData(event)
        processor.processCardDataForCurrentRaceOrSeries(card(1001, listOf(31, 32)), event.race)
        val original = processor.getCompetitorBySINumber(1001, event.race.id)!!
        val originalResult = processor.getResultByCompetitor(original.id)!!
        processor.createOrUpdateCompetitor(original.copy(siNumber = 9991001))
        val registered = original.copy(id = UUID.randomUUID(), firstName = "Registered", startNumber = 2)
        processor.createOrUpdateCompetitor(registered)

        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(
            card(1001, listOf(31, 32)).copy(finishTime = SITime(36001)), event.race))

        val stored = processor.getRaceData(event.race.id)
        assertEquals(setOf(original.id, registered.id),
            stored.competitorData.map { it.competitorCategory.competitor.id }.toSet())
        assertEquals(registered.id, processor.getResultByCompetitor(registered.id)?.competitorId)
        assertEquals(originalResult.id, processor.getResultByCompetitor(original.id)?.id)
        assertTrue(stored.unmatchedReadoutData.isEmpty())
        assertEquals("Registered", processor.getCompetitor(registered.id)?.firstName)
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(
            card(1001, listOf(31, 32)).copy(finishTime = SITime(36002)), event.race))
        assertEquals(setOf("Runner", "Registered", "Registered (2)"),
            processor.getRaceData(event.race.id).competitorData.withPracticeDisplayNames().map { it.competitorCategory.competitor.firstName }.toSet())
    }

    @Test
    fun practiceRereadRepairsUnmatchedDownloadWithoutAddingResultOrStealingHistoricalRun() = runBlocking {
        val processor = DataProcessor.get()
        val event = raceData("Practice repair", listOf(31, 32), siNumber = 1001)
        processor.saveRaceData(event)
        processor.processCardDataForCurrentRaceOrSeries(card(1001, listOf(31, 32)), event.race)
        val original = processor.getCompetitorBySINumber(1001, event.race.id)!!
        val originalResult = processor.getResultByCompetitor(original.id)!!
        processor.createOrUpdateCompetitor(original.copy(siNumber = 9991001))
        val registered = original.copy(id = UUID.randomUUID(), firstName = "Registered", startNumber = 2)
        processor.createOrUpdateCompetitor(registered)
        val download = card(1001, listOf(31, 32)).copy(finishTime = SITime(36001))
        processor.processCardDataForCurrentRaceOrSeries(download, event.race)
        val latest = processor.getResultDataFlowByRace(event.race.id).first().last().result
        processor.createOrUpdateResult(latest.copy(competitorId = null))
        processor.createOrUpdateCompetitor(registered)

        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(download, event.race))
        assertEquals(latest.id, processor.getResultByCompetitor(registered.id)?.id)
        assertEquals("Registered", processor.getCompetitor(registered.id)?.firstName)
        assertEquals(originalResult.id, processor.getResultByCompetitor(original.id)?.id)
        assertEquals(2, processor.resultSummaries(event.race.id).size)
        assertTrue(processor.getRaceData(event.race.id).unmatchedReadoutData.isEmpty())
        assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(download, event.race))

        // Editing the canonical name must not turn an unchanged reread into a new result.
        processor.createOrUpdateCompetitor(registered.copy(firstName = "Renamed"))
        assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(download, event.race))
        assertEquals("Renamed", processor.getCompetitor(registered.id)?.firstName)
        assertEquals(2, processor.resultSummaries(event.race.id).size)

    }

    @Test
    fun practiceRepeatsPreserveAllRunsAndIgnoreIdenticalDataRegardlessOfPreference() = runBlocking {
        val processor = DataProcessor.get()
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        listOf(
            R.string.preferences_readout_duplicate_ignore_value,
            R.string.preferences_readout_duplicate_replace_value,
            R.string.preferences_readout_duplicate_new_value
        ).forEach { preference ->
            preferences.edit().putString(context.getString(R.string.key_readout_duplicate), context.getString(preference)).commit()
            val practice = raceData("Practice repeats", listOf(31, 32), siNumber = 1001)
            processor.saveRaceData(practice)
            val downloads = listOf(
                card(1001, listOf(31, 32)),
                card(1001, listOf(31, 32)).copy(startTime = SITime(9 * 3600 + 1)),
                card(1001, listOf(31, 32)).copy(finishTime = SITime(10 * 3600 + 1)),
                card(1001, listOf(31, 32)).copy(checkTime = SITime(8 * 3600)),
                card(1001, listOf(31, 32)).copy(punchData = arrayListOf(
                    PunchData(31, SITime(601)), PunchData(32, SITime(600))
                )),
                card(1001, listOf(31, 33)),
                card(1001, listOf(32, 31)),
                card(1001, listOf(31, 32, 31))
            )
            downloads.forEachIndexed { index, download ->
                assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(download, practice.race))
                val stored = processor.getRaceData(practice.race.id)
                assertEquals(index + 1, stored.competitorData.size)
                assertEquals(1, processor.getCompetitorsByCategory(practice.categories.single().category.id).size)
                assertEquals(setOf(practice.competitorData.single().competitorCategory.competitor.id),
                    stored.competitorData.map { it.readoutData!!.result.competitorId }.toSet())
                assertEquals(setOf("Runner"), stored.competitorData.map { it.competitorCategory.competitor.firstName }.toSet())
                val expectedNames = (1..index + 1).map { if (it == 1) "1001 Runner" else "1001 Runner ($it)" }.toSet()
                assertEquals(expectedNames, stored.competitorData.withPracticeDisplayNames().map { it.competitorCategory.competitor.getFullName() }.toSet())
                assertTrue(stored.competitorData.all {
                    it.readoutData?.result?.siNumber == 1001 &&
                        it.readoutData?.result?.competitorId == it.competitorCategory.competitor.id
                })
                assertTrue(stored.unmatchedReadoutData.isEmpty())
                downloads.take(index + 1).forEach { previous ->
                    assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(previous.copy(cardName = "Changed metadata"), practice.race))
                }
                assertEquals(index + 1, processor.resultSummaries(practice.race.id).size)
            }
        }
    }

    @Test
    fun practiceRaceFileRoundTripKeepsOneRoomRegistrationAndEveryResult() = runBlocking {
        val processor = DataProcessor.get()
        val event = raceData("Practice transfer", listOf(31, 32), siNumber = 1001)
        processor.saveRaceData(event)
        (1..3).forEach { attempt ->
            processor.processCardDataForCurrentRaceOrSeries(card(1001, listOf(31, 32)).copy(finishTime = SITime(36000L + attempt)), event.race)
        }
        val registered = processor.getCompetitorBySINumber(1001, event.race.id)!!
        processor.createOrUpdateCompetitor(registered.copy(firstName = "Nadia"))
        val snapshot = processor.getRaceData(event.race.id)
        assertEquals(setOf("Nadia"), snapshot.competitorData.map { it.competitorCategory.competitor.firstName }.toSet())
        val encoded = org.openardf.radiooracle.shared.event.EventProjectFileJson.encode(EventProjectFile(raceData = snapshot.toEventRaceData()))
        val decoded = org.openardf.radiooracle.shared.event.EventProjectFileJson.decode(encoded).raceData.toRoomRaceData().withFreshImportIds()
        org.openardf.radiooracle.backend.files.DataImportValidator.validateRaceDataImport(decoded, RuntimeEnvironment.getApplication())
        processor.saveRaceData(decoded)
        val reopened = processor.getRaceData(decoded.race.id)
        assertEquals(1, reopened.competitorData.map { it.competitorCategory.competitor.id }.toSet().size)
        assertEquals(1, processor.getCompetitorsByCategory(decoded.categories.single().category.id).size)
        assertEquals(3, reopened.competitorData.map { it.readoutData!!.result.id }.toSet().size)
        assertEquals(3, ResultsProcessor.getResultWrapperFlowByRace(decoded.race.id, processor).first().sumOf { it.competitorData.size })
        assertEquals(setOf("Nadia", "Nadia (2)", "Nadia (3)"), reopened.competitorData.withPracticeDisplayNames()
            .map { it.competitorCategory.competitor.firstName }.toSet())
        val newest = processor.getResultDataFlowByRace(decoded.race.id).first().last()
        val ticket = org.openardf.radiooracle.backend.prints.PrintProcessor(RuntimeEnvironment.getApplication(), processor).formatFinishTicket(newest)
        assertTrue(ticket!!.contains("Nadia (3)"))
    }

    @Test
    fun practiceSeriesRepeatsAreNumberedWithinDestinationRaceOnly() = runBlocking {
        val processor = DataProcessor.get()
        val east = raceData("East", listOf(31, 32), siNumber = 1001)
        val west = raceData("West", listOf(41, 42), siNumber = 1001)
        processor.saveRaceData(east)
        processor.saveRaceData(west)
        val series = processor.createEventSeriesFromRace(east.race.id, "Practice Series")
        processor.addRaceToEventSeries(west.race.id, series.series.seriesId)
        val eastCard = card(1001, listOf(31, 32))
        val westCard = card(1001, listOf(41, 42))
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(eastCard, east.race))
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(westCard, east.race))
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(westCard.copy(finishTime = SITime(36001)), east.race))
        assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(westCard.copy(finishTime = SITime(36002)), east.race))
        assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(westCard, east.race))
        assertEquals(listOf("1001 Runner"), processor.getRaceData(east.race.id).competitorData.map { it.competitorCategory.competitor.getFullName() })
        assertEquals(setOf("1001 Runner", "1001 Runner (2)", "1001 Runner (3)"),
            processor.getRaceData(west.race.id).competitorData.withPracticeDisplayNames().map { it.competitorCategory.competitor.getFullName() }.toSet())
        assertEquals(1, processor.resultSummaries(east.race.id).size)
        assertEquals(3, processor.resultSummaries(west.race.id).size)
    }

    @Test
    fun changedDownloadsRemainRejectedAtEveryNonPracticeRaceLevel() = runBlocking {
        val processor = DataProcessor.get()
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
            context.getString(R.string.key_readout_duplicate),
            context.getString(R.string.preferences_readout_duplicate_ignore_value)
        ).commit()
        RaceLevel.entries.filter { it != RaceLevel.PRACTICE }.forEach { level ->
            val event = raceData("Competition", listOf(31, 32), siNumber = 1001, raceLevel = level)
            processor.saveRaceData(event)
            assertEquals(true, processor.processCardDataForCurrentRaceOrSeries(card(1001, listOf(31, 32)), event.race))
            assertEquals(false, processor.processCardDataForCurrentRaceOrSeries(card(1001, listOf(32, 31)), event.race))
            assertEquals(1, processor.resultSummaries(event.race.id).size)
            assertEquals(1, processor.getRaceData(event.race.id).competitorData.size)
        }
    }

    @Test
    fun everyNonPracticeLevelKeepsConfiguredDuplicatePolicyAndRegistrationIdentity() = runBlocking {
        val processor = DataProcessor.get()
        val context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val policies = listOf(
            R.string.preferences_readout_duplicate_ignore_value,
            R.string.preferences_readout_duplicate_replace_value,
            R.string.preferences_readout_duplicate_new_value
        )
        for (level in RaceLevel.entries.filter { it != RaceLevel.PRACTICE }) {
            for (policy in policies) for (registered in listOf(true, false)) for (changed in listOf(false, true)) {
                val label = "$level policy=${context.getString(policy)} registered=$registered changed=$changed"
                preferences.edit().putString(context.getString(R.string.key_readout_duplicate), context.getString(policy)).commit()
                val event = raceData(label, listOf(31, 32), if (registered) 1001 else null, raceLevel = level)
                processor.saveRaceData(event)
                val download = card(1001, listOf(31, 32)).copy(cardName = "1001 Runner")
                assertEquals(label, true, processor.processCardDataForCurrentRaceOrSeries(download, event.race))
                val original = processor.getResultDataFlowByRace(event.race.id).first().single()
                val before = processor.getRaceData(event.race.id).toEventRaceData()
                val reread = if (changed) download.copy(finishTime = SITime(36001L)) else download
                val rejected = policy == R.string.preferences_readout_duplicate_ignore_value
                val createNew = policy == R.string.preferences_readout_duplicate_new_value
                val replaced = policy == R.string.preferences_readout_duplicate_replace_value
                assertEquals(label, !rejected, processor.processCardDataForCurrentRaceOrSeries(reread, event.race))
                val rows = processor.getResultDataFlowByRace(event.race.id).first()
                val after = processor.getRaceData(event.race.id).toEventRaceData()
                assertEquals(label, if (createNew) 2 else 1, rows.size)
                assertEquals(label, !replaced, rows.any { it.result.id == original.result.id })
                assertEquals(label, if (registered) 1 else 0,
                    processor.getCompetitorsByCategory(event.categories.single().category.id).size)
                assertEquals(label, if (registered) 1 else 0, rows.count { it.result.competitorId != null })
                assertTrue(label, rows.all { it.result.siNumber == 1001 })
                if (rejected) assertEquals(label, before, after)
                if (createNew) assertEquals(label, original.toEventReadoutData(), processor.getResultData(original.result.id).toEventReadoutData())
                if (!rejected) {
                    val added = rows.single { it.result.id != original.result.id }
                    assertEquals(label, reread.finishTime?.getSeconds(), added.result.finishTime?.getSeconds())
                    assertEquals(label, if (registered && replaced) event.competitorData.single().competitorCategory.competitor.id else null,
                        added.result.competitorId)
                }
                assertEquals(label, after.competitorData, after.resultCompetitorData())
                val displayed = ResultsProcessor.getResultWrapperFlowByRace(event.race.id, processor).first()
                    .flatMap { it.competitorData }
                assertEquals(label, if (registered) listOf("Runner") else emptyList<String>(),
                    displayed.map { it.competitorCategory.competitor.firstName })
                val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(EventProjectFile(raceData = after))).raceData
                assertEquals(label, level, reopened.race.raceLevel)
                assertEquals(label, after.competitorData.map { it.readoutData }, reopened.competitorData.map { it.readoutData })
                assertEquals(label, after.unmatchedReadoutData, reopened.unmatchedReadoutData)
                if (registered) assertEquals(label, event.competitorData.single().competitorCategory.competitor,
                    processor.getCompetitor(event.competitorData.single().competitorCategory.competitor.id))
            }
        }
    }

    @Test
    fun resultDetailsReportEachResultsOwnPlaceAtEveryRaceLevel() = runBlocking {
        val processor = DataProcessor.get()
        for (level in RaceLevel.entries) {
            val event = raceData("$level places", listOf(31, 32), siNumber = 1001, raceLevel = level)
            processor.saveRaceData(event)
            if (level != RaceLevel.PRACTICE) {
                val registration = event.competitorData.single().competitorCategory.competitor
                for (si in listOf(1002, 1003)) {
                    processor.createOrUpdateCompetitor(registration.copy(id = UUID.randomUUID(), siNumber = si, lastName = si.toString()))
                }
            }
            val expectedPlaces = mutableMapOf<UUID, Int>()
            listOf(3, 1, 2).forEachIndexed { index, place ->
                val si = if (level == RaceLevel.PRACTICE) 1001 else 1001 + index
                assertEquals(level.name, true, processor.processCardDataForCurrentRaceOrSeries(
                    card(si, listOf(31, 32)).copy(finishTime = SITime(36000L + place)), event.race))
                val added = processor.getResultDataFlowByRace(event.race.id).first().single { it.result.id !in expectedPlaces }
                expectedPlaces[added.result.id] = place
            }
            for ((resultId, place) in expectedPlaces) {
                assertEquals("$level result=$resultId", place, ResultsProcessor.getResultPlace(resultId, event.race.id, processor))
            }
        }
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
