package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.StartDrawClubHandling
import org.openardf.radiooracle.shared.event.StartDrawOptions
import org.openardf.radiooracle.shared.event.StartDrawSettings
import java.nio.file.Files
import java.nio.file.Path

class DesktopEventSeriesTest {
    @Test
    fun seriesSessionDirtyStateIsIndependentFromProjectSession() {
        val projectStore = InMemorySeriesProjectFileStore(mapOf(Path.of("event.rom.json") to projectFile("Event")))
        val projectSession = DesktopProjectSession(projectStore)
        val seriesSession = DesktopEventSeriesSession(InMemoryEventSeriesStore())

        projectSession.open(Path.of("event.rom.json"))
        seriesSession.newSeries(Path.of("series/series.radio-oracle.json"), seriesFile())

        assertEquals(false, projectSession.hasUnsavedChanges)
        assertEquals(true, seriesSession.hasUnsavedChanges)
    }

    @Test
    fun opensAndClosesSeriesManifest() {
        val path = Path.of("series/series.radio-oracle.json")
        val seriesFile = seriesFile()
        val session = DesktopEventSeriesSession(InMemoryEventSeriesStore(seriesFiles = mapOf(path to seriesFile)))

        session.open(path)

        assertEquals(seriesFile, session.currentSeries)
        assertEquals(path, session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)

        session.closeSeries()

        assertNull(session.currentSeries)
        assertNull(session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun validatesMissingLinkedEventFiles() {
        val path = Path.of("series/series.radio-oracle.json")
        val session = DesktopEventSeriesSession(InMemoryEventSeriesStore(seriesFiles = mapOf(path to seriesFile())))

        session.open(path)
        val issues = session.validateLinkedEvents()

        assertEquals(listOf("Series event 'Day 1' is missing its Event File."), issues.map { it.message })
    }

    @Test
    fun createSeriesWithEventReturnsLinkedManifestAndEventFile() {
        val eventPath = Path.of("/work/championship/day-1.rom.json")
        val result = DesktopEventSeriesActions.createSeriesWithEvent(
            seriesFolder = Path.of("/work/championship"),
            seriesId = "series-1",
            seriesName = "Championship",
            eventPath = eventPath,
            eventProjectFile = projectFile("Day 1"),
            seriesEventId = "day-1"
        )

        assertEquals(Path.of("/work/championship/Championship.series.radio-oracle.json"), result.manifestPath)
        assertEquals("day-1.rom.json", result.seriesFile.events.single().eventFilePath)
        assertEquals(EventSeriesLink("series-1", "day-1"), result.eventProjectFile.seriesLink)
    }

    @Test
    fun createSeriesWithEventDefaultsToNeutralSeriesName() {
        val result = DesktopEventSeriesActions.createSeriesWithEvent(
            seriesFolder = Path.of("/work/championship"),
            seriesId = "series-1",
            eventPath = Path.of("/work/championship/day-1.rom.json"),
            eventProjectFile = projectFile("Day 1"),
            seriesEventId = "day-1"
        )

        assertEquals(DesktopEventSeriesActions.DEFAULT_SERIES_NAME, result.seriesFile.name)
        assertEquals(Path.of("/work/championship/Day 1.series.radio-oracle.json"), result.manifestPath)
    }

    @Test
    fun renameSeriesManifestFileUsesIndependentFileStem() {
        val manifestPath = Path.of("/work/championship/series.radio-oracle.json")
        val renamedManifestPath = Path.of("/work/championship/2026 USA and R2 Champs.series.radio-oracle.json")
        val seriesFile = DesktopEventSeriesActions.renameSeries(
            seriesFile(),
            "25th USA and 13th IARU Region 2 Radio Orienteering Championships"
        )
        val store = InMemoryEventSeriesStore(seriesFiles = mapOf(manifestPath to seriesFile))

        val result = DesktopEventSeriesActions.renameSeriesManifestFile(
            store = store,
            manifestPath = manifestPath,
            seriesFile = seriesFile,
            fileNameStem = "2026 USA and R2 Champs"
        )

        assertEquals(renamedManifestPath, result)
        assertEquals(
            "25th USA and 13th IARU Region 2 Radio Orienteering Championships",
            store.read(renamedManifestPath).name
        )
        assertFalse(store.exists(manifestPath))
    }

    @Test
    fun manifestFileDisplayStemRemovesOnlyManifestSuffix() {
        assertEquals(
            "2026 USA and R2 Champs",
            DesktopEventSeriesActions.manifestFileDisplayStem(
                Path.of("/work/championship/2026 USA and R2 Champs.series.radio-oracle.json")
            )
        )
        assertEquals(
            "series",
            DesktopEventSeriesActions.manifestFileDisplayStem(Path.of("/work/championship/series.radio-oracle.json"))
        )
    }

    @Test
    fun findManifestNearEventRecognizesNamedSeriesManifest() {
        val folder = Files.createTempDirectory("radio-oracle-named-series")
        val eventPath = folder.resolve("day-1.rom.json")
        val manifestPath = folder.resolve("Championship Week.series.radio-oracle.json")
        Files.writeString(eventPath, "{}")
        Files.writeString(manifestPath, "{}")

        assertEquals(manifestPath, DesktopEventSeriesActions.findManifestNearEvent(eventPath))
    }

    @Test
    fun findManifestNearEventPrefersManifestMatchingEventSeriesLink() {
        val folder = Files.createTempDirectory("radio-oracle-multi-series")
        val eventPath = folder.resolve("Sprint Practice.json")
        val otherManifestPath = folder.resolve("2m Classic Practice.series.radio-oracle.json")
        val linkedManifestPath = folder.resolve("Umstead West Practices.series.radio-oracle.json")
        Files.writeString(eventPath, "{}")
        Files.writeString(
            otherManifestPath,
            EventSeriesFileJson.encode(
                EventSeriesFile(
                    seriesId = "other-series",
                    name = "2m Classic Practice",
                    events = listOf(EventSeriesEvent("other-event", "2m Classic Practice.json", 0, "2m Classic Practice"))
                )
            )
        )
        Files.writeString(
            linkedManifestPath,
            EventSeriesFileJson.encode(
                EventSeriesFile(
                    seriesId = "linked-series",
                    name = "Umstead West Practices",
                    events = listOf(EventSeriesEvent("sprint-event", "Sprint Practice.json", 0, "Sprint Practice"))
                )
            )
        )

        val manifestPath = DesktopEventSeriesActions.findManifestNearEvent(
            eventPath = eventPath,
            seriesLink = EventSeriesLink("linked-series", "sprint-event"),
            store = DesktopEventSeriesFiles
        )

        assertEquals(linkedManifestPath, manifestPath)
    }

    @Test
    fun renameSeriesTrimsNameAndRejectsBlank() {
        val renamed = DesktopEventSeriesActions.renameSeries(seriesFile(), "  Championship Week  ")

        assertEquals("Championship Week", renamed.name)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopEventSeriesActions.renameSeries(seriesFile(), "   ")
        }
    }

    @Test
    fun addEventToSeriesAppendsManifestEntryAndBacklink() {
        val result = DesktopEventSeriesActions.addEventToSeries(
            seriesFile = seriesFile(),
            seriesFolder = Path.of("/work/championship"),
            eventPath = Path.of("/work/championship/day-2.rom.json"),
            eventProjectFile = projectFile("Day 2"),
            seriesEventId = "day-2"
        )

        assertEquals(listOf("day-1", "day-2"), result.seriesFile.sortedEvents().map { it.seriesEventId })
        assertEquals("day-2.rom.json", result.seriesFile.sortedEvents().last().eventFilePath)
        assertEquals(1, result.seriesFile.sortedEvents().last().order)
        assertEquals(EventSeriesLink("series-1", "day-2"), result.eventProjectFile.seriesLink)
    }

    @Test
    fun addEventToSeriesRefreshesExistingEventWithoutChangingOrder() {
        val originalSeries = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Old Day 2")
            )
        )

        val result = DesktopEventSeriesActions.addEventToSeries(
            seriesFile = originalSeries,
            seriesFolder = Path.of("/work/championship"),
            eventPath = Path.of("/work/championship/day-2.rom.json"),
            eventProjectFile = projectFile("Updated Day 2"),
            seriesEventId = "day-2"
        )

        assertEquals(2, result.seriesFile.events.size)
        assertEquals("Updated Day 2", result.seriesFile.events.single { it.seriesEventId == "day-2" }.displayName)
        assertEquals(1, result.seriesFile.events.single { it.seriesEventId == "day-2" }.order)
    }

    @Test
    fun addEventToSeriesAppendsCopiedEventFileWithDuplicateRaceId() {
        val originalSeries = seriesFile(
            events = listOf(EventSeriesEvent("race-id", "day-1.rom.json", 0, "Day 1"))
        )

        val result = DesktopEventSeriesActions.addEventToSeries(
            seriesFile = originalSeries,
            seriesFolder = Path.of("/work/championship"),
            eventPath = Path.of("/work/championship/day-2.rom.json"),
            eventProjectFile = projectFile("Copied Day 2", eventId = "race-id")
        )

        assertEquals(listOf("race-id", "race-id-day-2"), result.seriesFile.sortedEvents().map { it.seriesEventId })
        assertEquals("day-2.rom.json", result.seriesFile.sortedEvents().last().eventFilePath)
        assertEquals(EventSeriesLink("series-1", "race-id-day-2"), result.eventProjectFile.seriesLink)
    }

    @Test
    fun refreshLinkedEventMetadataUpdatesDisplayFieldsWithoutChangingOrder() {
        val originalSeries = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent(
                    seriesEventId = "day-2",
                    eventFilePath = "day-2.rom.json",
                    order = 1,
                    displayName = "Imported Name",
                    startDateTimeIso = "2026-06-02T10:00",
                    formatLabel = RaceType.CLASSIC.name
                )
            )
        )
        val linkedProject = projectFile("Printable Day 2", eventId = "race-id")
            .copy(seriesLink = EventSeriesLink("series-1", "day-2"))
            .let { project ->
                project.copy(
                    raceData = project.raceData.copy(
                        race = project.raceData.race.copy(
                            startDateTimeIso = "2026-06-05T11:30",
                            raceType = RaceType.SPRINT
                        )
                    )
                )
            }

        val updatedSeries = DesktopEventSeriesActions.refreshLinkedEventMetadata(
            seriesFile = originalSeries,
            seriesFolder = Path.of("/work/championship"),
            eventPath = Path.of("/work/championship/day-2.rom.json"),
            eventProjectFile = linkedProject
        )
        val day2 = updatedSeries.events.single { it.seriesEventId == "day-2" }

        assertEquals("Printable Day 2", day2.displayName)
        assertEquals("2026-06-05T11:30", day2.startDateTimeIso)
        assertEquals(RaceType.SPRINT.name, day2.formatLabel)
        assertEquals(1, day2.order)
    }

    @Test
    fun refreshLinkedEventMetadataRejectsPathCollision() {
        val originalSeries = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )
        val linkedProject = projectFile("Printable Day 1")
            .copy(seriesLink = EventSeriesLink("series-1", "day-1"))

        assertThrows(IllegalArgumentException::class.java) {
            DesktopEventSeriesActions.refreshLinkedEventMetadata(
                seriesFile = originalSeries,
                seriesFolder = Path.of("/work/championship"),
                eventPath = Path.of("/work/championship/day-2.rom.json"),
                eventProjectFile = linkedProject
            )
        }
    }

    @Test
    fun addEventToSeriesRequiresEventFileInsideSeriesFolder() {
        assertThrows(IllegalArgumentException::class.java) {
            DesktopEventSeriesActions.addEventToSeries(
                seriesFile = seriesFile(),
                seriesFolder = Path.of("/work/championship"),
                eventPath = Path.of("/work/other/day-2.rom.json"),
                eventProjectFile = projectFile("Day 2"),
                seriesEventId = "day-2"
            )
        }
    }

    @Test
    fun eventSummariesReportCurrentAndMissingFilesWithoutLoadingEvents() {
        val manifestPath = Path.of("/source/Championship Week.series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(Path.of("/source/events/day-2.rom.json") to projectFile("Day 2"))
        )

        val summaries = DesktopEventSeriesActions.eventSummaries(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = Path.of("/source/events/day-2.rom.json")
        )

        assertEquals(listOf("day-1", "day-2"), summaries.map { it.seriesEventId })
        assertEquals(false, summaries.first { it.seriesEventId == "day-1" }.exists)
        assertEquals(true, summaries.first { it.seriesEventId == "day-2" }.exists)
        assertEquals(true, summaries.first { it.seriesEventId == "day-2" }.isCurrentEvent)
    }

    @Test
    fun eventSummariesUseDateOrderWhenEverySeriesEventHasADate() {
        val manifestPath = Path.of("/source/Championship Week.series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-2", "events/day-2.rom.json", 0, "Day 2", startDateTimeIso = "2026-06-02T10:00"),
                EventSeriesEvent("day-1", "events/day-1.rom.json", 1, "Day 1", startDateTimeIso = "2026-06-01T10:00")
            )
        )
        val store = InMemoryEventSeriesStore(seriesFiles = mapOf(manifestPath to seriesFile))

        val summaries = DesktopEventSeriesActions.eventSummaries(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = null
        )

        assertEquals(listOf("day-1", "day-2"), summaries.map { it.seriesEventId })
        assertEquals(listOf(1, 2), summaries.map { it.displayPosition })
        assertEquals(listOf(1, 0), summaries.map { it.order })
    }

    @Test
    fun competitorMatchingSummariesCompareAllSeriesEventPairs() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "events/day-3.rom.json", 2, "Day 3")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to projectFile(
                    name = "Day 1",
                    competitors = listOf(
                        competitorData(id = "day-1-alice", startNumber = 11, siNumber = 123456),
                        competitorData(id = "day-1-bob", startNumber = 22, siNumber = null, bibNumber = "B-22"),
                        competitorData(id = "day-1-cara", startNumber = 33, siNumber = null, callSign = "K0ABC")
                    )
                ),
                Path.of("/source/events/day-2.rom.json") to projectFile(
                    name = "Day 2",
                    competitors = listOf(
                        competitorData(id = "day-2-alice", startNumber = 99, siNumber = 123456),
                        competitorData(id = "day-2-bob", startNumber = 88, siNumber = null, bibNumber = "B-22"),
                        competitorData(id = "day-2-cara", startNumber = 77, siNumber = null, callSign = "k0abc")
                    )
                ),
                Path.of("/source/events/day-3.rom.json") to projectFile(
                    name = "Day 3",
                    competitors = listOf(
                        competitorData(id = "day-3-unmatched", startNumber = 1, siNumber = null)
                    )
                )
            )
        )

        val summaries = DesktopEventSeriesActions.competitorMatchingSummaries(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = Path.of("/source/events/day-3.rom.json")
        )

        val matchedPair = summaries.single { it.firstSeriesEventId == "day-1" && it.secondSeriesEventId == "day-2" }
        assertEquals(3, summaries.size)
        assertEquals("Day 1", matchedPair.firstEventName)
        assertEquals("Day 2", matchedPair.secondEventName)
        assertEquals(3, matchedPair.firstCompetitorCount)
        assertEquals(3, matchedPair.secondCompetitorCount)
        assertEquals(false, matchedPair.includesCurrentEvent)
        assertEquals(3, matchedPair.matchCount)
        assertEquals(1, matchedPair.siNumberMatchCount)
        assertEquals(1, matchedPair.bibNumberMatchCount)
        assertEquals(1, matchedPair.callSignMatchCount)
        assertEquals(0, matchedPair.overrideMatchCount)
        assertEquals(0, matchedPair.issueCount)
        assertEquals(0, summaries.single { it.firstSeriesEventId == "day-1" && it.secondSeriesEventId == "day-3" }.matchCount)
        assertEquals(true, summaries.single { it.firstSeriesEventId == "day-1" && it.secondSeriesEventId == "day-3" }.includesCurrentEvent)
        assertEquals(0, summaries.single { it.firstSeriesEventId == "day-2" && it.secondSeriesEventId == "day-3" }.matchCount)
    }

    @Test
    fun competitorIdentityCoverageSummariesReportSeriesWidePresence() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "events/day-3.rom.json", 2, "Day 3")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to projectFile(
                    name = "Day 1",
                    competitors = listOf(
                        competitorData(id = "day-1-alice", startNumber = 11, siNumber = 123456),
                        competitorData(id = "day-1-bob", startNumber = 22, siNumber = null, bibNumber = "B-22")
                    )
                ),
                Path.of("/source/events/day-2.rom.json") to projectFile(
                    name = "Day 2",
                    competitors = listOf(
                        competitorData(id = "day-2-alice", startNumber = 99, siNumber = 123456),
                        competitorData(id = "day-2-bob", startNumber = 88, siNumber = null, bibNumber = "B-22")
                    )
                ),
                Path.of("/source/events/day-3.rom.json") to projectFile(
                    name = "Day 3",
                    competitors = listOf(
                        competitorData(id = "day-3-alice", startNumber = 1, siNumber = 123456),
                        competitorData(id = "day-3-unidentified", startNumber = 2, siNumber = null)
                    )
                )
            )
        )

        val summaries = DesktopEventSeriesActions.competitorIdentityCoverageSummaries(
            store = store,
            manifestPath = manifestPath
        )

        val alice = summaries.single { it.identityLabel == "SI 123456" }
        val bob = summaries.single { it.identityLabel == "Bib B-22" }
        assertEquals(2, summaries.size)
        assertEquals(3, alice.presentEventCount)
        assertEquals(3, alice.totalReadableEventCount)
        assertEquals(emptyList<String>(), alice.missingEventNames)
        assertEquals(2, bob.presentEventCount)
        assertEquals(listOf("Day 3"), bob.missingEventNames)
    }

    @Test
    fun startFairnessSummaryReportsPriorStartsAndUsableIdentity() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "events/day-3.rom.json", 2, "Day 3")
            )
        )
        val currentProject = projectFile(
            name = "Day 3",
            competitors = listOf(
                competitorData(id = "current-si", startNumber = 1, siNumber = 111),
                competitorData(id = "current-bib", startNumber = 2, siNumber = null, bibNumber = "B-2"),
                competitorData(id = "current-unidentified", startNumber = 3, siNumber = null)
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to projectFile(
                    name = "Day 1",
                    competitors = listOf(
                        competitorData(id = "prior-si", startNumber = 1, siNumber = 111, drawnStartTimeSeconds = 0)
                    )
                ),
                Path.of("/source/events/day-2.rom.json") to projectFile(
                    name = "Day 2",
                    competitors = listOf(
                        competitorData(id = "prior-bib", startNumber = 2, siNumber = null, drawnStartTimeSeconds = 600, bibNumber = "B-2"),
                        competitorData(id = "prior-unidentified", startNumber = 3, siNumber = null, drawnStartTimeSeconds = 1200)
                    )
                ),
                Path.of("/source/events/day-3.rom.json") to currentProject
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/events/day-3.rom.json"),
                currentProjectFile = currentProject
            )
        )

        assertEquals(3, summary.seriesEventCount)
        assertEquals(0, summary.missingEventFileCount)
        assertEquals(2, summary.eventsWithGeneratedStartsCount)
        assertEquals(1, summary.eventsWithoutGeneratedStartsCount)
        assertEquals(3, summary.generatedStartRowCount)
        assertEquals(2, summary.identifiedGeneratedStartRowCount)
        assertEquals(1, summary.unidentifiedGeneratedStartRowCount)
        assertEquals(2, summary.competitorsWithIdentifiedHistoryCount)
        assertEquals(0, summary.competitorsWithUnevenHistoryCount)
        assertEquals(2, summary.firstThirdStartCount)
        assertEquals(1, summary.middleThirdStartCount)
        assertEquals(0, summary.lateThirdStartCount)
        assertEquals(2, summary.balanceHistoryEventCount)
        assertEquals(0, summary.missingBalanceHistoryEventFileCount)
        assertEquals(2, summary.balanceHistoryEventsWithStartsCount)
        assertEquals(3, summary.balanceHistoryStartRowCount)
        assertEquals(2, summary.identifiedBalanceHistoryStartRowCount)
        assertEquals(3, summary.currentCompetitorCount)
        assertEquals(2, summary.identifiedCurrentCompetitorCount)
        val siHistory = summary.competitorHistories.single { it.identityLabel == "SI 111" }
        assertEquals("RUNNER prior-si", siHistory.competitorName)
        assertEquals(1, siHistory.generatedStartCount)
        assertEquals("E", siHistory.thirdHistoryText)
        assertEquals(1, siHistory.firstThirdCount)
        assertEquals(0, siHistory.middleThirdCount)
        assertEquals(0, siHistory.lateThirdCount)
        assertEquals("Needs more history", siHistory.recommendation)
    }

    @Test
    fun startFairnessSummarySplitsGeneratedCompetitorOrderIntoEqualThirds() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val competitors = (1..9).map { index ->
            competitorData(
                id = "competitor-$index",
                startNumber = index,
                siNumber = 1000 + index,
                drawnStartTimeSeconds = 0
            )
        }
        val event = projectFile(name = "Day 1", competitors = competitors)
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(
                manifestPath to seriesFile(
                    events = listOf(EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"))
                )
            ),
            eventFiles = mapOf(Path.of("/source/day-1.rom.json") to event)
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/day-1.rom.json"),
                currentProjectFile = event
            )
        )

        assertEquals(9, summary.generatedStartRowCount)
        assertEquals(3, summary.firstThirdStartCount)
        assertEquals(3, summary.middleThirdStartCount)
        assertEquals(3, summary.lateThirdStartCount)
    }

    @Test
    fun startFairnessSummaryFlagsSeriesWithNoGeneratedStarts() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val dayOne = projectFile(name = "Day 1", competitors = listOf(competitorData(id = "day-1", startNumber = 1, siNumber = 111)))
        val dayTwo = projectFile(name = "Day 2", competitors = listOf(competitorData(id = "day-2", startNumber = 2, siNumber = 222)))
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(
                manifestPath to seriesFile(
                    events = listOf(
                        EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                        EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
                    )
                )
            ),
            eventFiles = mapOf(
                Path.of("/source/day-1.rom.json") to dayOne,
                Path.of("/source/day-2.rom.json") to dayTwo
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/day-2.rom.json"),
                currentProjectFile = dayTwo
            )
        )

        assertEquals(2, summary.seriesEventCount)
        assertEquals(0, summary.eventsWithGeneratedStartsCount)
        assertEquals(2, summary.eventsWithoutGeneratedStartsCount)
        assertEquals(0, summary.generatedStartRowCount)
        assertEquals(0, summary.identifiedGeneratedStartRowCount)
        assertEquals(0, summary.firstThirdStartCount)
        assertEquals(0, summary.middleThirdStartCount)
        assertEquals(0, summary.lateThirdStartCount)
        assertEquals(0, summary.fairnessNumber)
        assertEquals(0, summary.fairnessScoreCompetitorCount)
    }

    @Test
    fun startFairnessSummaryRatesBalancedThirdHistoryAsOneHundred() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "day-3.rom.json", 2, "Day 3")
            )
        )
        val dayOne = projectWithTargetThird("Day 1", targetThird = 1)
        val dayTwo = projectWithTargetThird("Day 2", targetThird = 2)
        val dayThree = projectWithTargetThird("Day 3", targetThird = 3)
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/day-1.rom.json") to dayOne,
                Path.of("/source/day-2.rom.json") to dayTwo,
                Path.of("/source/day-3.rom.json") to dayThree
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/day-3.rom.json"),
                currentProjectFile = dayThree
            )
        )

        assertEquals(100, summary.fairnessNumber)
        assertEquals(1, summary.fairnessScoreCompetitorCount)
        assertEquals(0, summary.fairnessExcessSpread)
        assertEquals("E M L", summary.competitorHistories.single { it.identityLabel == "SI 111" }.thirdHistoryText)
    }

    @Test
    fun startFairnessSummaryRatesRepeatedSameThirdHistoryAsLow() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )
        val dayOne = projectWithTargetThird("Day 1", targetThird = 1)
        val dayTwo = projectWithTargetThird("Day 2", targetThird = 1)
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/day-1.rom.json") to dayOne,
                Path.of("/source/day-2.rom.json") to dayTwo
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/day-2.rom.json"),
                currentProjectFile = dayTwo
            )
        )

        assertEquals(0, summary.fairnessNumber)
        assertEquals(1, summary.fairnessScoreCompetitorCount)
        assertEquals(1, summary.fairnessExcessSpread)
        assertEquals(1, summary.competitorsWithUnevenHistoryCount)
    }

    @Test
    fun startFairnessSummaryUsesOpenCurrentProjectStartsBeforeTheyAreSaved() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val currentEventPath = Path.of("/source/day-2.rom.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )
        val dayOne = projectWithTargetThird("Day 1", targetThird = 1)
        val staleSavedDayTwo = projectWithTargetThird("Day 2 saved", targetThird = 1)
        val unsavedOpenDayTwo = projectWithTargetThird("Day 2 open", targetThird = 2)
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/day-1.rom.json") to dayOne,
                currentEventPath to staleSavedDayTwo
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = currentEventPath,
                currentProjectFile = unsavedOpenDayTwo
            )
        )

        assertEquals(100, summary.fairnessNumber)
        assertEquals("E M", summary.competitorHistories.single { it.identityLabel == "SI 111" }.thirdHistoryText)
    }

    @Test
    fun startFairnessHistoryUsesDateOrderWhenEverySeriesEventHasADate() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-2", "day-2.rom.json", 0, "Day 2", startDateTimeIso = "2026-06-02T10:00"),
                EventSeriesEvent("day-1", "day-1.rom.json", 1, "Day 1", startDateTimeIso = "2026-06-01T10:00")
            )
        )
        val dayOne = projectFile(
            name = "Day 1",
            competitors = (1..9).map { index ->
                competitorData(
                    id = "day-1-$index",
                    startNumber = index,
                    siNumber = if (index == 9) 111 else 1000 + index,
                    drawnStartTimeSeconds = ((index - 1) * 60L)
                )
            }
        )
        val dayTwo = projectFile(
            name = "Day 2",
            competitors = (1..9).map { index ->
                competitorData(
                    id = "day-2-$index",
                    startNumber = index,
                    siNumber = if (index == 1) 111 else 2000 + index,
                    drawnStartTimeSeconds = ((index - 1) * 60L)
                )
            }
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/day-1.rom.json") to dayOne,
                Path.of("/source/day-2.rom.json") to dayTwo
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/day-2.rom.json"),
                currentProjectFile = dayTwo
            )
        )

        val history = summary.competitorHistories.single { it.identityLabel == "SI 111" }
        assertEquals("event date/time", summary.historyOrderDescription)
        assertEquals("L E", history.thirdHistoryText)
    }

    @Test
    fun optimizeStartFairnessImprovesSeriesByRegeneratingEventStarts() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "events/day-3.rom.json", 2, "Day 3"),
                EventSeriesEvent("day-4", "events/day-4.rom.json", 3, "Day 4")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to startOptimizableProject("Day 1", "day-1"),
                Path.of("/source/events/day-2.rom.json") to startOptimizableProject("Day 2", "day-2"),
                Path.of("/source/events/day-3.rom.json") to startOptimizableProject("Day 3", "day-3"),
                Path.of("/source/events/day-4.rom.json") to startOptimizableProject("Day 4", "day-4")
            )
        )

        val result = DesktopEventSeriesActions.optimizeStartFairness(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = Path.of("/source/events/day-4.rom.json"),
            maxPasses = 2,
            candidatesPerEvent = 24
        )

        assertTrue(result.improved)
        assertTrue(result.finalScore < result.initialScore)
        assertTrue(result.finalUnevenHistoryCount < result.initialUnevenHistoryCount || result.finalSpreadSum < result.initialSpreadSum)
        assertTrue(result.attemptedCandidateCount > 0)
        assertTrue(result.acceptedCandidateCount > 0)
        assertTrue(result.updatedEventFiles.isNotEmpty())
    }

    @Test
    fun optimizeStartFairnessSkipsLockedEventFiles() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-3", "events/day-3.rom.json", 2, "Day 3"),
                EventSeriesEvent("day-4", "events/day-4.rom.json", 3, "Day 4")
            )
        )
        val lockedDayOne = startOptimizableProject("Day 1", "day-1", lockedForSeriesOptimization = true)
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to lockedDayOne,
                Path.of("/source/events/day-2.rom.json") to startOptimizableProject("Day 2", "day-2"),
                Path.of("/source/events/day-3.rom.json") to startOptimizableProject("Day 3", "day-3"),
                Path.of("/source/events/day-4.rom.json") to startOptimizableProject("Day 4", "day-4")
            )
        )

        val summary = requireNotNull(
            DesktopEventSeriesActions.startFairnessSummary(
                store = store,
                manifestPath = manifestPath,
                currentEventPath = Path.of("/source/events/day-4.rom.json"),
                currentProjectFile = startOptimizableProject("Day 4", "day-4")
            )
        )
        val result = DesktopEventSeriesActions.optimizeStartFairness(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = Path.of("/source/events/day-4.rom.json"),
            maxPasses = 2,
            candidatesPerEvent = 24
        )

        assertEquals(1, summary.lockedForOptimizationEventCount)
        assertEquals(3, summary.unlockedForOptimizationEventCount)
        assertFalse(result.updatedEventFiles.any { it.seriesEventId == "day-1" })
        assertTrue(result.updatedEventFiles.isNotEmpty())
    }

    @Test
    fun optimizeStartFairnessCanAcceptAlternateSameScoreStartAssignments() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to startOptimizableProject("Day 1", "day-1", siBase = 1000),
                Path.of("/source/events/day-2.rom.json") to startOptimizableProject("Day 2", "day-2", siBase = 2000)
            )
        )

        val result = DesktopEventSeriesActions.optimizeStartFairness(
            store = store,
            manifestPath = manifestPath,
            currentEventPath = Path.of("/source/events/day-2.rom.json"),
            maxPasses = 1,
            candidatesPerEvent = 12,
            seedSalt = "alternate-test"
        )

        assertFalse(result.improved)
        assertTrue(result.alternateSolution)
        assertEquals(result.initialScore, result.finalScore)
        assertTrue(result.acceptedCandidateCount > 0)
        assertTrue(result.updatedEventFiles.isNotEmpty())
    }

    @Test
    fun solutionNumberingNumbersUniqueSeriesSolutionsAndFlagsRepeats() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val first = DesktopEventSeriesStartFairnessSolutionNumbers.assign(
            existingNumbers = emptyMap(),
            manifestPath = manifestPath,
            solutionSignature = "solution-a"
        )
        val repeated = DesktopEventSeriesStartFairnessSolutionNumbers.assign(
            existingNumbers = first.solutionNumbers,
            manifestPath = manifestPath,
            solutionSignature = "solution-a"
        )
        val second = DesktopEventSeriesStartFairnessSolutionNumbers.assign(
            existingNumbers = repeated.solutionNumbers,
            manifestPath = manifestPath,
            solutionSignature = "solution-b"
        )

        assertEquals(1, first.solutionNumber)
        assertFalse(first.repeatedSolution)
        assertEquals(1, repeated.solutionNumber)
        assertTrue(repeated.repeatedSolution)
        assertEquals(2, second.solutionNumber)
        assertFalse(second.repeatedSolution)
    }

    @Test
    fun startListDrawNumberingNumbersUniqueEventStartOrdersAndFlagsRepeats() {
        val eventPath = Path.of("/source/day-1.rom.json")
        val firstProject = projectWithTargetThird("Day 1", targetThird = 1)
        val repeatedProject = projectWithTargetThird("Day 1", targetThird = 1)
        val secondProject = projectWithTargetThird("Day 1", targetThird = 2)

        val first = DesktopStartListDrawNumbers.assign(
            existingNumbers = emptyMap(),
            eventPath = eventPath,
            projectFile = firstProject
        )
        val repeated = DesktopStartListDrawNumbers.assign(
            existingNumbers = first.orderNumbers,
            eventPath = eventPath,
            projectFile = repeatedProject
        )
        val second = DesktopStartListDrawNumbers.assign(
            existingNumbers = repeated.orderNumbers,
            eventPath = eventPath,
            projectFile = secondProject
        )

        assertEquals(1, first.orderNumber)
        assertFalse(first.repeatedOrder)
        assertEquals(1, repeated.orderNumber)
        assertTrue(repeated.repeatedOrder)
        assertEquals(2, second.orderNumber)
        assertFalse(second.repeatedOrder)
        assertEquals(
            2,
            DesktopStartListDrawNumbers.knownOrderCount(
                existingNumbers = second.orderNumbers,
                eventPath = eventPath,
                projectFile = firstProject
            )
        )
    }

    @Test
    fun startListDrawNumberingIsScopedByEventFile() {
        val firstEvent = DesktopStartListDrawNumbers.assign(
            existingNumbers = emptyMap(),
            eventPath = Path.of("/source/day-1.rom.json"),
            projectFile = projectWithTargetThird("Day 1", targetThird = 1)
        )
        val secondEvent = DesktopStartListDrawNumbers.assign(
            existingNumbers = firstEvent.orderNumbers,
            eventPath = Path.of("/source/day-2.rom.json"),
            projectFile = projectWithTargetThird("Day 2", targetThird = 1)
        )

        assertEquals(1, firstEvent.orderNumber)
        assertEquals(1, secondEvent.orderNumber)
        assertFalse(secondEvent.repeatedOrder)
    }

    @Test
    fun startListDrawNumberingIsScopedByDrawSettings() {
        val eventPath = Path.of("/source/day-1.rom.json")
        val avoidClubsContext = DesktopStartListDrawNumbers.drawContextKey(
            "05:00",
            StartDrawOptions(clubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK)
        )
        val ignoreClubsContext = DesktopStartListDrawNumbers.drawContextKey(
            "05:00",
            StartDrawOptions(clubHandling = StartDrawClubHandling.IGNORE)
        )
        val firstProject = projectWithTargetThird("Day 1", targetThird = 1)
        val secondProject = projectWithTargetThird("Day 1", targetThird = 2)

        val avoidFirst = DesktopStartListDrawNumbers.assign(
            existingNumbers = emptyMap(),
            eventPath = eventPath,
            projectFile = firstProject,
            drawContextKey = avoidClubsContext
        )
        val avoidSecond = DesktopStartListDrawNumbers.assign(
            existingNumbers = avoidFirst.orderNumbers,
            eventPath = eventPath,
            projectFile = secondProject,
            drawContextKey = avoidClubsContext
        )
        val ignoreFirst = DesktopStartListDrawNumbers.assign(
            existingNumbers = avoidSecond.orderNumbers,
            eventPath = eventPath,
            projectFile = firstProject,
            drawContextKey = ignoreClubsContext
        )

        assertEquals(2, avoidSecond.orderNumber)
        assertEquals(1, ignoreFirst.orderNumber)
        assertEquals(
            2,
            DesktopStartListDrawNumbers.knownOrderCount(
                existingNumbers = ignoreFirst.orderNumbers,
                eventPath = eventPath,
                projectFile = firstProject,
                drawContextKey = avoidClubsContext
            )
        )
    }

    @Test
    fun seriesContextIsAvailableForManifestListedCurrentEventWithoutBacklink() {
        assertEquals(
            true,
            hasDesktopEventSeriesContext(
                projectFile = projectFile("Day 2"),
                summaries = listOf(eventSummary("day-2", isCurrentEvent = true))
            )
        )
    }

    @Test
    fun seriesContextRejectsCurrentEventWithMismatchedBacklink() {
        assertEquals(
            false,
            hasDesktopEventSeriesContext(
                projectFile = projectFile("Day 2").copy(seriesLink = EventSeriesLink("series-1", "day-1")),
                summaries = listOf(eventSummary("day-2", isCurrentEvent = true))
            )
        )
    }

    @Test
    fun seriesContextIsUnavailableWhenNoManifestEntryIsCurrent() {
        assertEquals(
            false,
            hasDesktopEventSeriesContext(
                projectFile = projectFile("Day 2").copy(seriesLink = EventSeriesLink("series-1", "day-2")),
                summaries = listOf(eventSummary("day-2", isCurrentEvent = false))
            )
        )
    }

    @Test
    fun exportSeriesCopiesOnlyManifestListedFiles() {
        val manifestPath = Path.of("/source/Championship Week.series.radio-oracle.json")
        val seriesFile = seriesFile(
            events = listOf(
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2")
            )
        )
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(manifestPath to seriesFile),
            eventFiles = mapOf(
                Path.of("/source/events/day-1.rom.json") to projectFile("Day 1"),
                Path.of("/source/events/day-2.rom.json") to projectFile("Day 2"),
                Path.of("/source/draft-trash.rom.json") to projectFile("Trash")
            )
        )

        val result = DesktopEventSeriesActions.exportSeries(store, manifestPath, Path.of("/backup"))

        assertEquals(Path.of("/backup/Championship Week.series.radio-oracle.json"), result.manifestPath)
        assertEquals(
            listOf(Path.of("/backup/events/day-1.rom.json"), Path.of("/backup/events/day-2.rom.json")),
            result.eventFilePaths
        )
        assertEquals(
            listOf(
                Path.of("/source/events/day-1.rom.json") to Path.of("/backup/events/day-1.rom.json"),
                Path.of("/source/events/day-2.rom.json") to Path.of("/backup/events/day-2.rom.json")
            ),
            store.copiedFiles
        )
        assertFalse(store.copiedFiles.any { it.first.fileName.toString() == "draft-trash.rom.json" })
    }

    @Test
    fun exportSeriesRejectsMissingEssentialEventFile() {
        val manifestPath = Path.of("/source/series.radio-oracle.json")
        val store = InMemoryEventSeriesStore(seriesFiles = mapOf(manifestPath to seriesFile()))

        assertThrows(IllegalArgumentException::class.java) {
            DesktopEventSeriesActions.exportSeries(store, manifestPath, Path.of("/backup"))
        }
    }

    @Test
    fun openingSeriesManifestUsesRememberedMemberEvent() {
        val manifestPath = Path.of("/work/championship/series.radio-oracle.json")
        val dayOnePath = Path.of("/work/championship/day-1.rom.json")
        val dayTwoPath = Path.of("/work/championship/day-2.rom.json")
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(
                manifestPath to seriesFile(
                    events = listOf(
                        EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                        EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
                    )
                )
            ),
            eventFiles = mapOf(
                dayOnePath to projectFile("Day 1"),
                dayTwoPath to projectFile("Day 2")
            )
        )
        val lastSeriesEvents = InMemoryLastSeriesEventStore(mapOf(manifestPath to dayTwoPath))

        assertEquals(
            dayTwoPath,
            DesktopEventSeriesActions.eventPathToOpenFromManifest(store, manifestPath, lastSeriesEvents)
        )
    }

    @Test
    fun openingSeriesManifestFallsBackToFirstExistingSortedMemberEvent() {
        val manifestPath = Path.of("/work/championship/series.radio-oracle.json")
        val dayTwoPath = Path.of("/work/championship/day-2.rom.json")
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(
                manifestPath to seriesFile(
                    events = listOf(
                        EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                        EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
                    )
                )
            ),
            eventFiles = mapOf(dayTwoPath to projectFile("Day 2"))
        )

        assertEquals(
            dayTwoPath,
            DesktopEventSeriesActions.eventPathToOpenFromManifest(store, manifestPath, InMemoryLastSeriesEventStore())
        )
    }

    @Test
    fun openingSeriesMemberRemembersItForTheManifest() {
        val manifestPath = Path.of("/work/championship/series.radio-oracle.json")
        val dayTwoPath = Path.of("/work/championship/day-2.rom.json")
        val lastSeriesEvents = InMemoryLastSeriesEventStore()
        val store = InMemoryEventSeriesStore(
            seriesFiles = mapOf(
                manifestPath to seriesFile(
                    events = listOf(
                        EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                        EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
                    )
                )
            ),
            eventFiles = mapOf(dayTwoPath to projectFile("Day 2"))
        )

        DesktopEventSeriesActions.rememberOpenedSeriesEvent(store, dayTwoPath, lastSeriesEvents)

        assertEquals(dayTwoPath, lastSeriesEvents.lastEventPath(manifestPath))
    }

    private fun seriesFile(events: List<EventSeriesEvent> = listOf(EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"))): EventSeriesFile =
        EventSeriesFile(seriesId = "series-1", name = "Championship", events = events)

    private fun eventSummary(seriesEventId: String, isCurrentEvent: Boolean): DesktopEventSeriesEventSummary =
        DesktopEventSeriesEventSummary(
            seriesEventId = seriesEventId,
            displayName = seriesEventId,
            order = 0,
            displayPosition = 1,
            eventFilePath = "$seriesEventId.rom.json",
            resolvedPath = Path.of("/work/championship/$seriesEventId.rom.json"),
            exists = true,
            isCurrentEvent = isCurrentEvent
        )

    private fun projectFile(
        name: String,
        eventId: String = name,
        competitors: List<EventCompetitorData> = emptyList(),
        categories: List<EventCategoryData> = emptyList()
    ): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = eventId,
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-06-01T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7200
                ),
                categories = categories,
                aliases = emptyList(),
                competitorData = competitors,
                unmatchedReadoutData = emptyList()
            )
        )

    private fun startOptimizableProject(
        name: String,
        eventId: String,
        siBase: Int = 1000,
        lockedForSeriesOptimization: Boolean = false
    ): EventProjectFile {
        val category = eventCategory(id = "cat-$eventId", raceId = eventId)
        val competitors = (1..9).map { index ->
            competitorData(
                id = "$eventId-competitor-$index",
                startNumber = index,
                siNumber = siBase + index,
                drawnStartTimeSeconds = ((index - 1) * 60L),
                category = category,
                raceId = eventId
            )
        }
        val project = projectFile(
            name = name,
            eventId = eventId,
            competitors = competitors,
            categories = listOf(
                EventCategoryData(
                    category = category,
                    controlPoints = emptyList(),
                    competitors = competitors.map { it.competitorCategory.competitor }
                )
            )
        )
        return project.copy(
            raceData = project.raceData.copy(
                startDrawSettings = StartDrawSettings(
                    lockedForSeriesOptimization = lockedForSeriesOptimization
                )
            )
        )
    }

    private fun projectWithTargetThird(name: String, targetThird: Int): EventProjectFile {
        val targetIndex = (targetThird - 1).coerceIn(0, 2)
        val competitors = (0..2).map { index ->
            val isTarget = index == targetIndex
            competitorData(
                id = if (isTarget) "$name-target" else "$name-other-$index",
                startNumber = index + 1,
                siNumber = if (isTarget) 111 else null,
                drawnStartTimeSeconds = index * 60L
            )
        }
        return projectFile(name = name, competitors = competitors)
    }

    private fun eventCategory(id: String, raceId: String): EventCategory =
        EventCategory(
            id = id,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun competitorData(
        id: String,
        startNumber: Int,
        siNumber: Int?,
        drawnStartTimeSeconds: Long? = null,
        bibNumber: String = "",
        callSign: String = "",
        category: EventCategory? = null,
        raceId: String = "race"
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = raceId,
                    categoryId = null,
                    firstName = id,
                    lastName = "Runner",
                    club = "OPEN",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = siNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = drawnStartTimeSeconds,
                    bibNumber = bibNumber,
                    callSign = callSign
                ),
                category = category
            ),
            readoutData = null
        )
}

private class InMemorySeriesProjectFileStore(
    initialProjects: Map<Path, EventProjectFile> = emptyMap()
) : ProjectFileStore {
    private val projects = initialProjects.toMutableMap()

    override fun read(path: Path): EventProjectFile =
        projects.getValue(path)

    override fun write(path: Path, projectFile: EventProjectFile) {
        projects[path] = projectFile
    }
}

private class InMemoryEventSeriesStore(
    seriesFiles: Map<Path, EventSeriesFile> = emptyMap(),
    eventFiles: Map<Path, EventProjectFile> = emptyMap()
) : EventSeriesStore {
    private val seriesFiles = seriesFiles.toMutableMap()
    private val eventFiles = eventFiles.toMutableMap()
    val copiedFiles = mutableListOf<Pair<Path, Path>>()

    override fun read(path: Path): EventSeriesFile =
        seriesFiles.getValue(path)

    override fun write(path: Path, seriesFile: EventSeriesFile) {
        seriesFiles[path] = seriesFile
    }

    override fun readEvent(path: Path): EventProjectFile =
        eventFiles.getValue(path)

    override fun writeEvent(path: Path, projectFile: EventProjectFile) {
        eventFiles[path] = projectFile
    }

    override fun exists(path: Path): Boolean =
        seriesFiles.containsKey(path) || eventFiles.containsKey(path)

    override fun copyFile(source: Path, target: Path) {
        copiedFiles += source to target
    }

    override fun moveManifest(source: Path, target: Path, seriesFile: EventSeriesFile) {
        seriesFiles.remove(source)
        seriesFiles[target] = seriesFile
    }
}

private class InMemoryLastSeriesEventStore(
    initialEvents: Map<Path, Path> = emptyMap()
) : DesktopLastSeriesEventStore {
    private val eventsByManifestPath = initialEvents.toMutableMap()

    override fun lastEventPath(manifestPath: Path): Path? =
        eventsByManifestPath[manifestPath]

    override fun rememberEventPath(manifestPath: Path, eventPath: Path) {
        eventsByManifestPath[manifestPath] = eventPath
    }
}
