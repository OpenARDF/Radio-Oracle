package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import java.nio.file.Files

class DesktopProjectFilesTest {
    @Test
    fun writesAndReadsSharedProjectFiles() {
        val directory = Files.createTempDirectory("rom-desktop-project")
        val path = directory.resolve("sample.rom.json")
        val projectFile = EventProjectFile(raceData = raceData())

        DesktopProjectFiles.write(path, projectFile)

        assertEquals(projectFile, DesktopProjectFiles.read(path))
    }

    @Test
    fun exportsResultsCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-results")
        val path = directory.resolve("results.csv")

        DesktopProjectFiles.exportResultsCsv(path, EventProjectFile(raceData = raceData()))

        assertEquals("", Files.readString(path))
    }

    @Test
    fun exportsArdfJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-ardf-json")
        val path = directory.resolve("event.ardf.json")

        DesktopProjectFiles.exportArdfJson(path, EventProjectFile(raceData = raceData()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"format_version\": 1"))
        assertTrue(exported.contains("\"event_name\": \"Desktop File Race\""))
        assertTrue(exported.contains("\"race_name\": \"Desktop File Race\""))
    }

    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Desktop File Race",
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
}
