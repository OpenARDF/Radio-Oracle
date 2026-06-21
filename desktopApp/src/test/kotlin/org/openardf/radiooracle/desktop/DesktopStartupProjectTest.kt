package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import java.nio.file.Path

class DesktopStartupProjectTest {
    @Test
    fun leavesSessionEmptyWhenNoStartupPathIsProvided() {
        val session = DesktopProjectSession(StartupProjectFileStore())

        val status = openStartupProject(session, null)

        assertEquals("No Event File open.", status)
        assertNull(session.currentProject)
    }

    @Test
    fun opensStartupPathAndReturnsUserFacingStatus() {
        val path = Path.of("sample.rom.json")
        val projectFile = projectFile("Startup Race")
        val session = DesktopProjectSession(StartupProjectFileStore(mapOf(path to projectFile)))
        var rememberedPath: Path? = null

        val status = openStartupProject(session, path) { rememberedPath = it }

        assertEquals("Opened sample.rom.json", status)
        assertEquals(projectFile, session.currentProject)
        assertEquals(path, session.currentPath)
        assertEquals(path, rememberedPath)
    }

    @Test
    fun reportsStartupOpenFailureWithoutChangingSession() {
        val path = Path.of("missing.rom.json")
        val session = DesktopProjectSession(StartupProjectFileStore(readError = IllegalArgumentException("Missing Event File")))

        val status = openStartupProject(session, path)

        assertEquals("Open failed: Missing Event File", status)
        assertNull(session.currentProject)
        assertNull(session.currentPath)
    }

    @Test
    fun startupPathUsesCommandLinePathBeforeRememberedPath() {
        val commandLinePath = Path.of("from-args.rom.json")
        val rememberedPath = Path.of("remembered.rom.json")
        val store = StartupLastEventFileStore(rememberedPath)

        assertEquals(commandLinePath, startupProjectPath(commandLinePath, store))
    }

    @Test
    fun startupPathFallsBackToRememberedPath() {
        val rememberedPath = Path.of("remembered.rom.json")
        val store = StartupLastEventFileStore(rememberedPath)

        assertEquals(rememberedPath, startupProjectPath(null, store))
    }

    @Test
    fun topBarTextShowsLoadedEventName() {
        val projectFile = projectFile("USA and IARU Region 2 Radio Orienteering 80m Classic")

        assertEquals(
            "Event: USA and IARU Region 2 Radio Orienteering 80m Classic",
            desktopTopBarEventText(projectFile)
        )
    }

    @Test
    fun topBarTextShowsEmptyEventFileState() {
        assertEquals("No event file loaded", desktopTopBarEventText(null))
    }

    @Test
    fun topBarTextShowsSeriesName() {
        assertEquals(
            "Series: USA Championships 2026",
            desktopTopBarSeriesText(EventSeriesUiContext(Path.of("series.radio-oracle.json"), "USA Championships 2026"))
        )
    }

    @Test
    fun eventFilePageShowsParentSeriesName() {
        assertEquals(
            "Parent Series: USA Championships 2026",
            desktopParentSeriesText(EventSeriesUiContext(Path.of("series.radio-oracle.json"), "USA Championships 2026"))
        )
        assertNull(desktopParentSeriesText(null))
    }

    private fun projectFile(name: String): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = "race",
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-05-31T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
        )
}

private class StartupLastEventFileStore(
    private val rememberedPath: Path?
) : DesktopLastEventFileStore {
    override fun lastEventFile(): Path? = rememberedPath

    override fun rememberEventFile(path: Path) = Unit
}

private class StartupProjectFileStore(
    private val projects: Map<Path, EventProjectFile> = emptyMap(),
    private val readError: RuntimeException? = null
) : ProjectFileStore {
    override fun read(path: Path): EventProjectFile =
        readError?.let { throw it } ?:
        projects.getValue(path)

    override fun write(path: Path, projectFile: EventProjectFile) = Unit
}
