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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentCardPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentTime
import java.nio.file.Path
import java.time.LocalDateTime

class DesktopSeriesSportIdentReadoutRouterTest {
    @Test
    fun selectsOnlySeriesEventWhoseControlsContainAllReadoutPunches() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val eastPath = Path.of("/series/east.rom.json")
        val westPath = Path.of("/series/west.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("east", "east.rom.json", "East"),
            event("west", "west.rom.json", "West")
        )
        store.eventFiles[eastPath] = project("east-race", 31, 32, 33)
        store.eventFiles[westPath] = project("west-race", 41, 42, 43)

        val match = DesktopSeriesSportIdentReadoutRouter.matchingEventForReadout(
            store = store,
            manifestPath = manifestPath,
            readout = readout(42, 43)
        )

        assertEquals("west", match?.event?.seriesEventId)
        assertEquals(westPath, match?.eventPath)
    }

    @Test
    fun returnsNoMatchWhenReadoutHasNoControlPunches() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val eventPath = Path.of("/series/event.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(event("event", "event.rom.json", "Event"))
        store.eventFiles[eventPath] = project("race", 31)

        val match = DesktopSeriesSportIdentReadoutRouter.matchingEventForReadout(
            store = store,
            manifestPath = manifestPath,
            readout = readout()
        )

        assertNull(match)
    }

    @Test
    fun returnsNoMatchWhenMultipleSeriesEventsContainAllPunches() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[Path.of("/series/first.rom.json")] = project("first-race", 31, 32)
        store.eventFiles[Path.of("/series/second.rom.json")] = project("second-race", 31, 32, 33)

        val match = DesktopSeriesSportIdentReadoutRouter.matchingEventForReadout(
            store = store,
            manifestPath = manifestPath,
            readout = readout(31, 32)
        )

        assertNull(match)
    }

    @Test
    fun returnsNoMatchWhenPunchesDoNotAllBelongToOneEvent() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[Path.of("/series/first.rom.json")] = project("first-race", 31, 32)
        store.eventFiles[Path.of("/series/second.rom.json")] = project("second-race", 41, 42)

        val match = DesktopSeriesSportIdentReadoutRouter.matchingEventForReadout(
            store = store,
            manifestPath = manifestPath,
            readout = readout(31, 42)
        )

        assertNull(match)
    }

    @Test
    fun blankPracticeCardStartsCompetitorInForestAcrossEverySeriesEvent() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val firstPath = Path.of("/series/first.rom.json")
        val secondPath = Path.of("/series/second.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[firstPath] = project("first-race", 31)
        store.eventFiles[secondPath] = project("second-race", 41)

        val update = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForestAcrossSeries(
            store = store,
            manifestPath = manifestPath,
            readout = blankReadout(),
            readoutDateTime = LocalDateTime.parse("2026-06-23T18:05:00")
        )

        assertEquals(setOf(firstPath, secondPath), update.updatedEventPaths)
        assertEquals(2, update.updatedCompetitorCount)
        assertEquals(
            listOf(300L, 300L),
            listOf(firstPath, secondPath).map { path ->
                store.eventFiles.getValue(path).raceData.competitorData.single()
                    .competitorCategory.competitor.drawnStartTimeSeconds
            }
        )
        assertEquals(
            listOf("Runner", "Runner"),
            listOf(firstPath, secondPath).map { path ->
                store.eventFiles.getValue(path).raceData.competitorData.single()
                    .competitorCategory.competitor.lastName
            }
        )
        assertEquals(
            listOf("M21", "M21"),
            listOf(firstPath, secondPath).map { path ->
                store.eventFiles.getValue(path).raceData.competitorData.single()
                    .competitorCategory.category?.name
            }
        )
    }

    @Test
    fun resultCardClearsBlankPracticeStartAcrossSeriesBeforeResultIsRecorded() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val firstPath = Path.of("/series/first.rom.json")
        val secondPath = Path.of("/series/second.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[firstPath] = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForest(
            project("first-race", 31),
            blankReadout(),
            LocalDateTime.parse("2026-06-23T18:05:00")
        )
        store.eventFiles[secondPath] = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForest(
            project("second-race", 41),
            blankReadout(),
            LocalDateTime.parse("2026-06-23T18:05:00")
        )

        val update = DesktopSeriesSportIdentReadoutRouter.clearPracticeCompetitorInForestAcrossSeries(
            store = store,
            manifestPath = manifestPath,
            siNumber = 2005010
        )

        assertEquals(setOf(firstPath, secondPath), update.updatedEventPaths)
        assertEquals(2, update.updatedCompetitorCount)
        assertEquals(
            listOf(null, null),
            listOf(firstPath, secondPath).map { path ->
                store.eventFiles.getValue(path).raceData.competitorData.single()
                    .competitorCategory.competitor.drawnStartTimeSeconds
            }
        )
    }

    @Test
    fun resultCardMatchingStillSelectsEventAfterBlankStartsAreCleared() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val firstPath = Path.of("/series/first.rom.json")
        val secondPath = Path.of("/series/second.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[firstPath] = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForest(
            project("first-race", 31),
            blankReadout(),
            LocalDateTime.parse("2026-06-23T18:05:00")
        )
        store.eventFiles[secondPath] = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForest(
            project("second-race", 41),
            blankReadout(),
            LocalDateTime.parse("2026-06-23T18:05:00")
        )

        DesktopSeriesSportIdentReadoutRouter.clearPracticeCompetitorInForestAcrossSeries(
            store = store,
            manifestPath = manifestPath,
            siNumber = 2005010
        )
        val match = DesktopSeriesSportIdentReadoutRouter.matchingEventForReadout(
            store = store,
            manifestPath = manifestPath,
            readout = readout(41)
        )

        assertEquals("second", match?.event?.seriesEventId)
        assertEquals(secondPath, match?.eventPath)
    }

    @Test
    fun blankPracticeCardStartsOnlyPracticeEventsInMixedRaceLevelSeries() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val firstPath = Path.of("/series/first.rom.json")
        val secondPath = Path.of("/series/second.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[firstPath] = project("first-race", 31, raceLevel = RaceLevel.PRACTICE)
        store.eventFiles[secondPath] = project("second-race", 41, raceLevel = RaceLevel.REGIONAL)

        val update = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForestAcrossSeries(
            store = store,
            manifestPath = manifestPath,
            readout = blankReadout(),
            readoutDateTime = LocalDateTime.parse("2026-06-23T18:05:00")
        )

        assertEquals(setOf(firstPath), update.updatedEventPaths)
        assertEquals(1, update.updatedCompetitorCount)
        assertEquals(1, store.eventFiles.getValue(firstPath).raceData.competitorData.size)
        assertEquals(emptyList<EventProjectFile>(), listOf(store.eventFiles.getValue(secondPath)).filter { it.raceData.competitorData.isNotEmpty() })
    }

    @Test
    fun blankPracticeCardDoesNotDisturbAlreadyRecordedSeriesResults() {
        val store = FakeSeriesStore()
        val manifestPath = Path.of("/series/events.radio-oracle.json")
        val firstPath = Path.of("/series/first.rom.json")
        val secondPath = Path.of("/series/second.rom.json")
        store.seriesFiles[manifestPath] = seriesFile(
            event("first", "first.rom.json", "First"),
            event("second", "second.rom.json", "Second")
        )
        store.eventFiles[firstPath] = recordedResultProject(project("first-race", 31))
        store.eventFiles[secondPath] = project("second-race", 41)
        val originalRecordedCompetitor = store.eventFiles.getValue(firstPath).raceData.competitorData.single()
        val originalReadout = originalRecordedCompetitor.readoutData

        val update = DesktopSeriesSportIdentReadoutRouter.startPracticeCompetitorInForestAcrossSeries(
            store = store,
            manifestPath = manifestPath,
            readout = blankReadout(),
            readoutDateTime = LocalDateTime.parse("2026-06-23T18:05:00")
        )

        val recordedCompetitor = store.eventFiles.getValue(firstPath).raceData.competitorData.single()
        val newlyStartedCompetitor = store.eventFiles.getValue(secondPath).raceData.competitorData.single()
        assertEquals(setOf(secondPath), update.updatedEventPaths)
        assertEquals(1, update.updatedCompetitorCount)
        assertEquals(originalReadout, recordedCompetitor.readoutData)
        assertEquals(null, recordedCompetitor.competitorCategory.competitor.drawnStartTimeSeconds)
        assertEquals(300L, newlyStartedCompetitor.competitorCategory.competitor.drawnStartTimeSeconds)
    }

    private class FakeSeriesStore : EventSeriesStore {
        val seriesFiles = mutableMapOf<Path, EventSeriesFile>()
        val eventFiles = mutableMapOf<Path, EventProjectFile>()

        override fun read(path: Path): EventSeriesFile = seriesFiles.getValue(path)
        override fun write(path: Path, seriesFile: EventSeriesFile) {
            seriesFiles[path] = seriesFile
        }
        override fun readEvent(path: Path): EventProjectFile = eventFiles.getValue(path)
        override fun writeEvent(path: Path, projectFile: EventProjectFile) {
            eventFiles[path] = projectFile
        }
        override fun exists(path: Path): Boolean = path in seriesFiles || path in eventFiles
        override fun copyFile(source: Path, target: Path) = Unit
    }

    private fun seriesFile(vararg events: EventSeriesEvent): EventSeriesFile =
        EventSeriesFile(
            seriesId = "series",
            name = "Series",
            events = events.mapIndexed { index, event -> event.copy(order = index) }
        )

    private fun event(seriesEventId: String, path: String, name: String): EventSeriesEvent =
        EventSeriesEvent(
            seriesEventId = seriesEventId,
            eventFilePath = path,
            order = 0,
            displayName = name
        )

    private fun project(
        raceId: String,
        vararg siCodes: Int,
        raceLevel: RaceLevel = RaceLevel.PRACTICE
    ): EventProjectFile {
        val project = EventProjectFactory.createEmptyProject(
            raceId = raceId,
            raceName = raceId,
            startDateTimeIso = "2026-06-23T18:00"
        )
        val controls = siCodes.mapIndexed { index, siCode ->
            EventControl(
                id = "control-$raceId-$siCode",
                raceId = raceId,
                label = "C${index + 1}",
                siCode = siCode,
                type = ControlPointType.CONTROL
            )
        }
        val category = EventCategory(
            id = "category-$raceId",
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 4_000,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        return project.copy(
            raceData = project.raceData.copy(
                race = project.raceData.race.copy(raceLevel = raceLevel),
                controls = controls,
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = controls.mapIndexed { index, control ->
                            EventControlPoint(
                                id = "category-control-$raceId-$index",
                                categoryId = category.id,
                                siCode = control.siCode,
                                type = control.type,
                                order = index,
                                controlId = control.id
                            )
                        },
                        competitors = emptyList()
                    )
                )
            )
        )
    }

    private fun blankReadout(): SportIdentCardReadout =
        SportIdentCardReadout(
            siNumber = 2005010,
            series = 9,
            checkTime = null,
            startTime = null,
            finishTime = null,
            punches = emptyList(),
            cardHolder = SportIdentCardHolder(firstName = "Alice", lastName = "Runner", club = "OK Test")
        )

    private fun recordedResultProject(projectFile: EventProjectFile): EventProjectFile =
        EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = projectFile,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = readout(31),
            readoutDateTimeIso = "2026-06-23T18:45:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

    private fun readout(vararg siCodes: Int): SportIdentCardReadout =
        SportIdentCardReadout(
            siNumber = 2005010,
            series = 9,
            checkTime = null,
            startTime = SportIdentTime(10, 0, 0),
            finishTime = SportIdentTime(10, 30, 0),
            punches = siCodes.mapIndexed { index, siCode ->
                SportIdentCardPunch(siCode, SportIdentTime(10, index + 1, 0))
            }
        )
}
