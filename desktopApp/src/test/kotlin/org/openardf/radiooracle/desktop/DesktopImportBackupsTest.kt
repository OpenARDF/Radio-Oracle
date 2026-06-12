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
import java.nio.file.Path
import java.time.LocalDateTime

class DesktopImportBackupsTest {
    @Test
    fun writesReadableBackupForSavedEventFile() {
        val directory = Files.createTempDirectory("radio-oracle-import-backup")
        val projectFile = EventProjectFile(raceData = raceData("Oregon Sprint"))

        val backupPath = DesktopImportBackups.writeBackup(
            projectFile = projectFile,
            currentEventPath = Path.of("Course Data", "Sprint.rom.json"),
            importTitle = "controls/route KML/KMZ import Sprint.kml",
            appDataDirectory = directory,
            now = LocalDateTime.of(2026, 6, 11, 17, 45, 0)
        )

        assertEquals(
            directory.resolve("import-backups")
                .resolve("sprint-before-controls-route-kml-kmz-import-sprint-kml-20260611-174500.rom.json"),
            backupPath
        )
        assertEquals(projectFile, DesktopProjectFiles.read(backupPath))
    }

    @Test
    fun writesReadableBackupForUnsavedEventFileUsingRaceName() {
        val directory = Files.createTempDirectory("radio-oracle-import-backup-unsaved")
        val projectFile = EventProjectFile(raceData = raceData("Late Import / Practice Event"))

        val backupPath = DesktopImportBackups.writeBackup(
            projectFile = projectFile,
            currentEventPath = null,
            importTitle = "competitors CSV import competitors.csv",
            appDataDirectory = directory,
            now = LocalDateTime.of(2026, 6, 11, 18, 0, 0)
        )

        assertTrue(backupPath.fileName.toString().startsWith("late-import-practice-event-before-competitors-csv-import"))
        assertEquals(projectFile, DesktopProjectFiles.read(backupPath))
    }

    private fun raceData(name: String): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race-id",
                name = name,
                apiKey = "",
                startDateTimeIso = "2026-06-11T10:00",
                raceType = RaceType.SPRINT,
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
