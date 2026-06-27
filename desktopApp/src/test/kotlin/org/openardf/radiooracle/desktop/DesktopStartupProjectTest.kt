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

    @Test
    fun eventFilePageShowsSavedEventFileFolder() {
        assertEquals(
            "Event File Folder: /Users/example/Documents/Radio-Oracle",
            desktopEventFileFolderText(
                eventFilePath = Path.of("/Users/example/Documents/Radio-Oracle/Day 1.json"),
                workingFolder = Path.of("/Users/example/Documents/Other")
            )
        )
    }

    @Test
    fun eventFilePageShowsFirstSaveDefaultFolderForUnsavedEvent() {
        assertEquals(
            "Event File Folder: /Users/example/Documents/Radio-Oracle (first save default)",
            desktopEventFileFolderText(
                eventFilePath = null,
                workingFolder = Path.of("/Users/example/Documents/Radio-Oracle")
            )
        )
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
