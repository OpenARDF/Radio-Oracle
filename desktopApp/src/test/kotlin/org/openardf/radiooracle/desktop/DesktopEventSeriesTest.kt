package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesLink
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

        assertEquals(Path.of("/work/championship/series.radio-oracle.json"), result.manifestPath)
        assertEquals("day-1.rom.json", result.seriesFile.events.single().eventFilePath)
        assertEquals(EventSeriesLink("series-1", "day-1"), result.eventProjectFile.seriesLink)
    }

    @Test
    fun exportSeriesCopiesOnlyManifestListedFiles() {
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
                Path.of("/source/events/day-1.rom.json") to projectFile("Day 1"),
                Path.of("/source/events/day-2.rom.json") to projectFile("Day 2"),
                Path.of("/source/draft-trash.rom.json") to projectFile("Trash")
            )
        )

        val result = DesktopEventSeriesActions.exportSeries(store, manifestPath, Path.of("/backup"))

        assertEquals(Path.of("/backup/series.radio-oracle.json"), result.manifestPath)
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

    private fun seriesFile(events: List<EventSeriesEvent> = listOf(EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"))): EventSeriesFile =
        EventSeriesFile(seriesId = "series-1", name = "Championship", events = events)

    private fun projectFile(name: String): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = name,
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-06-01T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
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
}
