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

        assertEquals("No project open.", status)
        assertNull(session.currentProject)
    }

    @Test
    fun opensStartupPathAndReturnsUserFacingStatus() {
        val path = Path.of("sample.rom.json")
        val projectFile = projectFile("Startup Race")
        val session = DesktopProjectSession(StartupProjectFileStore(mapOf(path to projectFile)))

        val status = openStartupProject(session, path)

        assertEquals("Opened sample.rom.json", status)
        assertEquals(projectFile, session.currentProject)
        assertEquals(path, session.currentPath)
    }

    @Test
    fun reportsStartupOpenFailureWithoutChangingSession() {
        val path = Path.of("missing.rom.json")
        val session = DesktopProjectSession(StartupProjectFileStore(readError = IllegalArgumentException("Missing project")))

        val status = openStartupProject(session, path)

        assertEquals("Open failed: Missing project", status)
        assertNull(session.currentProject)
        assertNull(session.currentPath)
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

private class StartupProjectFileStore(
    private val projects: Map<Path, EventProjectFile> = emptyMap(),
    private val readError: RuntimeException? = null
) : ProjectFileStore {
    override fun read(path: Path): EventProjectFile =
        readError?.let { throw it } ?:
        projects.getValue(path)

    override fun write(path: Path, projectFile: EventProjectFile) = Unit
}
