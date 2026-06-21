package org.openardf.radiooracle.desktop

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.usb.DesktopSerialPort
import org.openardf.radiooracle.desktop.usb.DesktopSerialPortProvider
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
import org.openardf.radiooracle.shared.event.EventSeriesLink

class DesktopAutomationCliTest {
    @Test
    fun versionCommandPrintsJson() {
        val result = runAutomation("version")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"version\""))
        assertTrue(result.stdout.contains("\"displayVersion\":"))
    }

    @Test
    fun pathsCommandPrintsAutomationPaths() {
        val result = runAutomation("paths")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"paths\""))
        assertTrue(result.stdout.contains("\"logDirectory\":"))
        assertTrue(result.stdout.contains("Radio-Oracle"))
    }

    @Test
    fun openEventFileCommandReadsAndSummarizesEventFile() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val path = directory.resolve("Automation Event.json")
        DesktopProjectFiles.write(path, projectFile("Automation Event"))

        val result = runAutomation("open-event-file", path.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"open-event-file\""))
        assertTrue(result.stdout.contains("\"raceName\":\"Automation Event\""))
        assertTrue(result.stdout.contains("\"validationErrorCount\":0"))
    }

    @Test
    fun eventStartListVerifyCountsPerfectOrders() {
        val directory = Files.createTempDirectory("radio-oracle-automation-start-verify")
        val path = directory.resolve("Automation Event.json")
        DesktopProjectFiles.write(
            path,
            projectFile(
                "Automation Event",
                categories = listOf(
                    categoryData("cat-a", "M21", 1),
                    categoryData("cat-b", "M40", 2),
                    categoryData("cat-c", "W21", 3)
                ),
                competitors = listOf(
                    competitorData(id = "Alice", startNumber = 1, siNumber = 1111, categoryId = "cat-a", club = "A"),
                    competitorData(id = "Bob", startNumber = 2, siNumber = 2222, categoryId = "cat-b", club = "B"),
                    competitorData(id = "Cara", startNumber = 3, siNumber = 3333, categoryId = "cat-c", club = "C")
                )
            )
        )

        val result = runAutomation("event-start-list-verify", path.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-start-list-verify\""))
        assertTrue(result.stdout.contains("\"drawableCompetitorCount\":3"))
        assertTrue(result.stdout.contains("\"totalOrderCount\":6"))
        assertTrue(result.stdout.contains("\"perfectOrderCount\":6"))
    }

    @Test
    fun readinessSummaryCommandReportsReadinessJson() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val path = directory.resolve("Automation Event.json")
        DesktopProjectFiles.write(path, projectFile("Automation Event"))

        val result = runAutomation("readiness-summary", path.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"readiness-summary\""))
        assertTrue(result.stdout.contains("\"raceName\":\"Automation Event\""))
        assertTrue(result.stdout.contains("\"validationIssueCount\":0"))
        assertTrue(result.stdout.contains("\"readinessIssueCount\":0"))
    }

    @Test
    fun navAvailabilityReportsDisabledMenuLongClickOverrides() {
        val result = runAutomation("nav-availability")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"nav-availability\""))
        assertTrue(result.stdout.contains("\"kind\":\"workflow\""))
        assertTrue(result.stdout.contains("\"label\":\"Race Ops\""))
        assertTrue(result.stdout.contains("Long-click for 3 seconds to explore this workflow."))
        assertTrue(result.stdout.contains("\"label\":\"Categories\""))
        assertTrue(result.stdout.contains("\"longClickOverride\":true"))
        assertTrue(result.stdout.contains("Long-click for 3 seconds to explore this menu."))
    }

    @Test
    fun recalculateResultsCommandReportsCounts() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val path = directory.resolve("Automation Event.json")
        DesktopProjectFiles.write(path, projectFile("Automation Event"))

        val result = runAutomation("recalculate-results", "--write", path.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"recalculate-results\""))
        assertTrue(result.stdout.contains("\"write\":true"))
        assertTrue(result.stdout.contains("\"recalculatedCount\":0"))
        assertTrue(result.stdout.contains("\"changedCount\":0"))
    }

    @Test
    fun publicResultsSiteCommandsGenerateAndCheckPreview() {
        val directory = Files.createTempDirectory("radio-oracle-automation-public-site")
        val eventPath = directory.resolve("Automation Event.json")
        val siteRoot = directory.resolve("site-root")
        DesktopProjectFiles.write(eventPath, projectFile("Automation Event"))

        val exportResult = runAutomation("export-public-results-site", eventPath.toString(), siteRoot.toString())
        val previewResult = runAutomation(
            "preview-public-results-site",
            siteRoot.toString(),
            "2026-06-03-automation-event"
        )

        assertEquals(0, exportResult.exitCode)
        assertTrue(exportResult.stdout.contains("\"command\":\"export-public-results-site\""))
        assertTrue(exportResult.stdout.contains("\"eventPath\":\"2026-06-03-automation-event\""))
        assertTrue(Files.exists(siteRoot.resolve("2026-06-03-automation-event").resolve("index.html")))
        assertEquals(0, previewResult.exitCode)
        assertTrue(previewResult.stdout.contains("\"command\":\"preview-public-results-site\""))
        assertTrue(previewResult.stdout.contains("\"responseCode\":200"))
        assertTrue(previewResult.stdout.contains("\"contentType\":\"text/html; charset=utf-8\""))
    }

    @Test
    fun importAndroidEventFileCommandWritesDesktopEventFile() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val androidPath = directory.resolve("Android Event.ardfjs")
        val desktopPath = directory.resolve("Android Event.json")
        Files.writeString(
            androidPath,
            """
                {
                  "race_name": "Android Event",
                  "race_start": "2026-06-03T10:00:00",
                  "race_type": "CLASSIC",
                  "race_band": "M80",
                  "race_level": "PRACTICE",
                  "race_time_limit": "120",
                  "race_api_key": "",
                  "categories": [],
                  "aliases": [],
                  "competitors": [],
                  "unmatched_results": []
                }
            """.trimIndent()
        )

        val importResult = runAutomation(
            "import-android-event-file",
            androidPath.toString(),
            desktopPath.toString()
        )
        val openResult = runAutomation("open-event-file", desktopPath.toString())

        assertEquals(0, importResult.exitCode)
        assertTrue(importResult.stdout.contains("\"command\":\"import-android-event-file\""))
        assertTrue(importResult.stdout.contains("\"raceName\":\"Android Event\""))
        assertTrue(Files.exists(desktopPath))
        assertEquals(0, openResult.exitCode)
        assertTrue(openResult.stdout.contains("\"raceName\":\"Android Event\""))
    }

    @Test
    fun exportAndroidEventFileCommandWritesAndroidEventFile() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val desktopPath = directory.resolve("Desktop Event.json")
        val androidPath = directory.resolve("Desktop Event.ardfjs")
        DesktopProjectFiles.write(desktopPath, projectFile("Desktop Event"))

        val exportResult = runAutomation(
            "export-android-event-file",
            desktopPath.toString(),
            androidPath.toString()
        )
        val importResult = runAutomation(
            "import-android-event-file",
            androidPath.toString(),
            directory.resolve("Desktop Event Round Trip.json").toString()
        )

        assertEquals(0, exportResult.exitCode)
        assertTrue(exportResult.stdout.contains("\"command\":\"export-android-event-file\""))
        assertTrue(exportResult.stdout.contains("\"raceName\":\"Desktop Event\""))
        assertTrue(Files.exists(androidPath))
        assertTrue(Files.readString(androidPath).contains("\"race_name\": \"Desktop Event\""))
        assertEquals(0, importResult.exitCode)
        assertTrue(importResult.stdout.contains("\"raceName\":\"Desktop Event\""))
    }

    @Test
    fun importCompetitorsCsvCommandUpdatesDesktopEventFile() {
        val directory = Files.createTempDirectory("radio-oracle-automation")
        val eventFilePath = directory.resolve("Automation Event.json")
        val csvPath = directory.resolve("competitors.csv")
        DesktopProjectFiles.write(eventFilePath, projectFile("Automation Event"))
        Files.writeString(
            csvPath,
            """
                si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent
                2007001;101;Alice;Runner;M50;0;1974;BOK;BOK-101;00:00;0
            """.trimIndent()
        )

        val importResult = runAutomation("import-competitors-csv", eventFilePath.toString(), csvPath.toString())
        val openResult = runAutomation("open-event-file", eventFilePath.toString())

        assertEquals(0, importResult.exitCode)
        assertTrue(importResult.stdout.contains("\"command\":\"import-competitors-csv\""))
        assertTrue(importResult.stdout.contains("\"importedCount\":1"))
        assertTrue(importResult.stdout.contains("\"competitorCount\":1"))
        assertEquals(0, openResult.exitCode)
        assertTrue(openResult.stdout.contains("\"competitorCount\":1"))
    }

    @Test
    fun importCompetitorsCsvCommandCanUpdateExistingSiNumbersWithoutInventingStartNumbers() {
        val directory = Files.createTempDirectory("radio-oracle-automation-update-competitors")
        val eventFilePath = directory.resolve("Automation Event.json")
        val csvPath = directory.resolve("competitors.csv")
        DesktopProjectFiles.write(
            eventFilePath,
            projectFile(
                "Automation Event",
                competitors = listOf(
                    competitorData(id = "Alice", startNumber = 1, siNumber = null),
                    competitorData(id = "Bob", startNumber = 2, siNumber = 2222)
                )
            )
        )
        Files.writeString(
            csvPath,
            """
                si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent;preferred_start_group;bib_number;call_sign
                3333;2;Alice;Runner;M50;0;1974;OPEN;75;;0;;75;
            """.trimIndent()
        )

        val importResult = runAutomation("import-competitors-csv", eventFilePath.toString(), csvPath.toString(), "--update-existing")
        val updated = DesktopProjectFiles.read(eventFilePath).raceData.competitorData.map { it.competitorCategory.competitor }

        assertEquals(0, importResult.exitCode)
        assertTrue(importResult.stdout.contains("\"updateExisting\":true"))
        assertTrue(importResult.stdout.contains("\"updatedCount\":1"))
        assertEquals(3333, updated.single { it.id == "Alice" }.siNumber)
        assertEquals("75", updated.single { it.id == "Alice" }.index)
        assertEquals("75", updated.single { it.id == "Alice" }.bibNumber)
        assertEquals(listOf(null, null), updated.map { it.startNumber })
    }

    @Test
    fun eventSeriesListCommandReportsManifestEvents() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
                )
            )
        )
        DesktopProjectFiles.write(dayOnePath, projectFile("Day 1", eventId = "day-1"))
        DesktopProjectFiles.write(dayTwoPath, projectFile("Day 2", eventId = "day-2"))

        val result = runAutomation(
            "event-series-list",
            manifestPath.toString(),
            "--current-event",
            dayTwoPath.toString()
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-list\""))
        assertTrue(result.stdout.contains("\"eventCount\":2"))
        assertTrue(result.stdout.contains("\"missingCount\":0"))
        assertTrue(result.stdout.contains("\"seriesEventId\":\"day-1\""))
        assertTrue(result.stdout.contains("\"seriesEventId\":\"day-2\""))
        assertTrue(result.stdout.contains("\"current\":true"))
    }

    @Test
    fun eventSeriesStartFairnessVerifyReportsExhaustiveOptimum() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series-start-verify")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        val categories = listOf(
            categoryData("cat-a", "M21", 1),
            categoryData("cat-b", "M40", 2),
            categoryData("cat-c", "W21", 3)
        )
        fun competitorsFor(eventPrefix: String): List<EventCompetitorData> =
            listOf(
                competitorData(
                    id = "$eventPrefix-alice",
                    startNumber = 1,
                    siNumber = 1111,
                    drawnStartTimeSeconds = 0,
                    categoryId = "cat-a",
                    club = "A"
                ),
                competitorData(
                    id = "$eventPrefix-bob",
                    startNumber = 2,
                    siNumber = 2222,
                    drawnStartTimeSeconds = 60,
                    categoryId = "cat-b",
                    club = "B"
                ),
                competitorData(
                    id = "$eventPrefix-cara",
                    startNumber = 3,
                    siNumber = 3333,
                    drawnStartTimeSeconds = 120,
                    categoryId = "cat-c",
                    club = "C"
                )
            )
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
                )
            )
        )
        DesktopProjectFiles.write(dayOnePath, projectFile("Day 1", eventId = "day-1", categories = categories, competitors = competitorsFor("day-1")))
        DesktopProjectFiles.write(dayTwoPath, projectFile("Day 2", eventId = "day-2", categories = categories, competitors = competitorsFor("day-2")))

        val result = runAutomation(
            "event-series-start-fairness-verify",
            manifestPath.toString(),
            dayTwoPath.toString(),
            "--max-events",
            "2",
            "--max-event-competitors",
            "3",
            "--optimizer-samples",
            "0"
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-start-fairness-verify\""))
        assertTrue(result.stdout.contains("\"exhaustiveSearchComplete\":true"))
        assertTrue(result.stdout.contains("\"combinationCount\":36"))
        assertTrue(result.stdout.contains("\"bestPossibleScore\":3003"))
        assertTrue(result.stdout.contains("\"optimalCombinationCount\":12"))
        assertTrue(result.stdout.contains("\"currentIsOptimal\":false"))
        assertTrue(result.stdout.contains("\"uniqueThirdSignatureCount\":6"))
    }

    @Test
    fun eventSeriesAddEventCommandUpdatesManifestAndEventBacklink() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(EventSeriesEvent("day-1", "day-1.json", 0, "day-1"))
            )
        )
        DesktopProjectFiles.write(dayOnePath, projectFile("day-1", eventId = "day-1"))
        DesktopProjectFiles.write(dayTwoPath, projectFile("day-2", eventId = "day-2"))

        val result = runAutomation("event-series-add-event", manifestPath.toString(), dayTwoPath.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-add-event\""))
        assertTrue(result.stdout.contains("\"seriesId\":\"series-1\""))
        assertTrue(result.stdout.contains("\"seriesEventId\":\"day-2\""))
        assertTrue(result.stdout.contains("\"eventFilePath\":\"day-2.json\""))
        assertTrue(result.stdout.contains("\"eventCount\":2"))
        assertEquals(EventSeriesLink("series-1", "day-2"), DesktopProjectFiles.read(dayTwoPath).seriesLink)
        assertEquals(listOf("day-1", "day-2"), DesktopEventSeriesFiles.read(manifestPath).sortedEvents().map { it.seriesEventId })
    }

    @Test
    fun eventSeriesValidateCommandReportsDuplicateRaceIds() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series-validate")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
                )
            )
        )
        DesktopProjectFiles.write(
            dayOnePath,
            projectFile("Day 1", eventId = "copied-race-id", seriesLink = EventSeriesLink("series-1", "day-1"))
        )
        DesktopProjectFiles.write(
            dayTwoPath,
            projectFile("Day 2", eventId = "copied-race-id", seriesLink = EventSeriesLink("series-1", "day-2"))
        )

        val result = runAutomation("event-series-validate", manifestPath.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-validate\""))
        assertTrue(result.stdout.contains("\"issueCount\":2"))
        assertTrue(result.stdout.contains("\"warningCount\":2"))
        assertTrue(result.stdout.contains("\"errorCount\":0"))
        assertTrue(result.stdout.contains("duplicate race ID"))
    }

    @Test
    fun eventSeriesMatchCommandReportsCompetitorMatchingDiagnostics() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series-match")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
                )
            )
        )
        DesktopProjectFiles.write(
            dayOnePath,
            projectFile(
                "Day 1",
                eventId = "day-1",
                competitors = listOf(
                    competitorData(id = "day-1-alice", startNumber = 11, siNumber = 123456),
                    competitorData(id = "day-1-bob", startNumber = 22, siNumber = null, bibNumber = "B-22"),
                    competitorData(id = "day-1-cara", startNumber = 33, siNumber = null, callSign = "K0ABC")
                )
            )
        )
        DesktopProjectFiles.write(
            dayTwoPath,
            projectFile(
                "Day 2",
                eventId = "day-2",
                competitors = listOf(
                    competitorData(id = "day-2-alice", startNumber = 99, siNumber = 123456),
                    competitorData(id = "day-2-bob", startNumber = 88, siNumber = null, bibNumber = "B-22"),
                    competitorData(id = "day-2-cara", startNumber = 77, siNumber = null, callSign = "k0abc")
                )
            )
        )

        val result = runAutomation("event-series-match", manifestPath.toString(), dayTwoPath.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-match\""))
        assertTrue(result.stdout.contains("\"comparisonRowCount\":1"))
        assertTrue(result.stdout.contains("\"comparedEventCount\":1"))
        assertTrue(result.stdout.contains("\"firstEventName\":\"Day 1\""))
        assertTrue(result.stdout.contains("\"secondEventName\":\"Day 2\""))
        assertTrue(result.stdout.contains("\"includesCurrentEvent\":true"))
        assertTrue(result.stdout.contains("\"matchCount\":3"))
        assertTrue(result.stdout.contains("\"siNumberMatchCount\":1"))
        assertTrue(result.stdout.contains("\"bibNumberMatchCount\":1"))
        assertTrue(result.stdout.contains("\"callSignMatchCount\":1"))
        assertTrue(result.stdout.contains("\"issueCount\":0"))
    }

    @Test
    fun eventSeriesStartFairnessCommandReportsHistoryInputs() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series-start-fairness")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        val dayThreePath = directory.resolve("day-3.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2"),
                    EventSeriesEvent("day-3", "day-3.json", 2, "Day 3")
                )
            )
        )
        DesktopProjectFiles.write(
            dayOnePath,
            projectFile(
                "Day 1",
                eventId = "day-1",
                competitors = listOf(
                    competitorData(id = "day-1-alice", startNumber = 1, siNumber = 111, drawnStartTimeSeconds = 0)
                )
            )
        )
        DesktopProjectFiles.write(
            dayTwoPath,
            projectFile(
                "Day 2",
                eventId = "day-2",
                competitors = listOf(
                    competitorData(id = "day-2-bob", startNumber = 2, siNumber = null, drawnStartTimeSeconds = 600, bibNumber = "B-2"),
                    competitorData(id = "day-2-unidentified", startNumber = 3, siNumber = null, drawnStartTimeSeconds = 1200)
                )
            )
        )
        DesktopProjectFiles.write(
            dayThreePath,
            projectFile(
                "Day 3",
                eventId = "day-3",
                competitors = listOf(
                    competitorData(id = "day-3-alice", startNumber = 10, siNumber = 111),
                    competitorData(id = "day-3-bob", startNumber = 20, siNumber = null, bibNumber = "B-2"),
                    competitorData(id = "day-3-unidentified", startNumber = 30, siNumber = null)
                )
            )
        )

        val result = runAutomation("event-series-start-fairness", manifestPath.toString(), dayThreePath.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-start-fairness\""))
        assertTrue(result.stdout.contains("\"available\":true"))
        assertTrue(result.stdout.contains("\"seriesEventCount\":3"))
        assertTrue(result.stdout.contains("\"historyOrderDescription\":\"stored series order\""))
        assertTrue(result.stdout.contains("\"eventsWithGeneratedStartsCount\":2"))
        assertTrue(result.stdout.contains("\"eventsWithoutGeneratedStartsCount\":1"))
        assertTrue(result.stdout.contains("\"generatedStartRowCount\":3"))
        assertTrue(result.stdout.contains("\"identifiedGeneratedStartRowCount\":2"))
        assertTrue(result.stdout.contains("\"unidentifiedGeneratedStartRowCount\":1"))
        assertTrue(result.stdout.contains("\"competitorsWithIdentifiedHistoryCount\":2"))
        assertTrue(result.stdout.contains("\"fairnessNumber\":0"))
        assertTrue(result.stdout.contains("\"fairnessScoreCompetitorCount\":0"))
        assertTrue(result.stdout.contains("\"fairnessExcessSpread\":0"))
        assertTrue(result.stdout.contains("\"firstThirdStartCount\":2"))
        assertTrue(result.stdout.contains("\"middleThirdStartCount\":1"))
        assertTrue(result.stdout.contains("\"lateThirdStartCount\":0"))
        assertTrue(result.stdout.contains("\"competitorHistories\""))
        assertTrue(result.stdout.contains("\"identityLabel\":\"SI 111\""))
        assertTrue(result.stdout.contains("\"thirdHistoryText\":\"E\""))
        assertTrue(result.stdout.contains("\"recommendation\":\"Needs more history\""))
        assertTrue(result.stdout.contains("\"balanceHistoryEventCount\":2"))
        assertTrue(result.stdout.contains("\"balanceHistoryStartRowCount\":3"))
        assertTrue(result.stdout.contains("\"identifiedBalanceHistoryStartRowCount\":2"))
        assertTrue(result.stdout.contains("\"currentCompetitorCount\":3"))
        assertTrue(result.stdout.contains("\"identifiedCurrentCompetitorCount\":2"))
    }

    @Test
    fun eventSeriesOptimizeStartFairnessCommandReportsDryRunResult() {
        val directory = Files.createTempDirectory("radio-oracle-automation-series-start-optimizer")
        val manifestPath = directory.resolve("series.radio-oracle.json")
        val dayOnePath = directory.resolve("day-1.json")
        val dayTwoPath = directory.resolve("day-2.json")
        DesktopEventSeriesFiles.write(
            manifestPath,
            EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesEvent("day-1", "day-1.json", 0, "Day 1"),
                    EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
                )
            )
        )
        DesktopProjectFiles.write(
            dayOnePath,
            projectFile(
                "Day 1",
                eventId = "day-1",
                competitors = listOf(competitorData(id = "day-1-alice", startNumber = 1, siNumber = 111, drawnStartTimeSeconds = 0))
            )
        )
        DesktopProjectFiles.write(
            dayTwoPath,
            projectFile(
                "Day 2",
                eventId = "day-2",
                competitors = listOf(competitorData(id = "day-2-alice", startNumber = 1, siNumber = 111, drawnStartTimeSeconds = 0))
            )
        )

        val result = runAutomation("event-series-optimize-start-fairness", manifestPath.toString(), dayTwoPath.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"event-series-optimize-start-fairness\""))
        assertTrue(result.stdout.contains("\"write\":false"))
        assertTrue(result.stdout.contains("\"seedSalt\":\"cli-default\""))
        assertTrue(result.stdout.contains("\"alternateSolution\":false"))
        assertTrue(result.stdout.contains("\"solutionSignature\":"))
        assertTrue(result.stdout.contains("\"initialUnevenHistoryCount\":1"))
        assertTrue(result.stdout.contains("\"attemptedCandidateCount\":64"))
        assertTrue(result.stdout.contains("\"updatedEvents\":[]"))
    }

    @Test
    fun navSelectReportsNewEventFileAction() {
        val result = runAutomation("nav-select", "Event File > New Event File")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"nav-select\""))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Setup > Event File > New Event File\""))
        assertTrue(result.stdout.contains("\"selectedSection\":\"Races\""))
        assertTrue(result.stdout.contains("\"action\":\"NewEventFile\""))
    }

    @Test
    fun navSelectSupportsBackToPreviousMenu() {
        val result = runAutomation("nav-select", "Start List > Exports > < Back")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedLabels\":[\"Start List\", \"Exports\", \"< Back\"]"))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Setup > Start List\""))
        assertTrue(result.stdout.contains("\"selectedSection\":\"Start List\""))
        assertTrue(result.stdout.contains("\"selectedItemId\":\"setup.start-list\""))
        assertTrue(result.stdout.contains("\"action\":null"))
    }

    @Test
    fun navSelectReportsClearSeriesValidationPath() {
        val result = runAutomation("nav-select", "Event Series > Series Validation")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"nav-select\""))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Event Series > Series Validation\""))
        assertTrue(result.stdout.contains("\"selectedSection\":\"Series Validation\""))
        assertTrue(result.stdout.contains("\"selectedItemId\":\"series.validation\""))
        assertTrue(result.stdout.contains("\"action\":null"))
    }

    @Test
    fun navTreeReportsMenuStructureAsJson() {
        val result = runAutomation("nav-tree", "--workflow", "Event Series")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"nav-tree\""))
        assertTrue(result.stdout.contains("\"workflow\":\"Event Series\""))
        assertTrue(result.stdout.contains("\"path\":\"Event Series > Events\""))
        assertTrue(result.stdout.contains("\"path\":\"Event Series > Events > Add Event to Series...\""))
        assertTrue(result.stdout.contains("\"action\":\"AddEventToSeries\""))
        assertFalse(result.stdout.contains("Open Series Event"))
        assertFalse(result.stdout.contains("Event Series > Series Validation > Validate Series"))
    }

    @Test
    fun navAuditReportsCleanNavigationJson() {
        val result = runAutomation("nav-audit", "--require-clean")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"command\":\"nav-audit\""))
        assertTrue(result.stdout.contains("\"issueCount\":0"))
        assertTrue(result.stdout.contains("\"issues\":[]"))
    }

    @Test
    fun navSelectDraftModeReportsGuardBeforeLeavingNewEventFile() {
        val result = runAutomation("nav-select", "--draft", "Event File > New Event File > < Back")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedLabels\":[\"Event File\", \"New Event File\", \"< Back\"]"))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Setup > Event File > New Event File\""))
        assertTrue(result.stdout.contains("\"selectedItemId\":\"setup.event-file.new\""))
        assertTrue(result.stdout.contains("\"action\":\"NewEventFile\""))
        assertTrue(result.stdout.contains("\"guarded\":true"))
    }

    @Test
    fun navSelectDraftModeReportsGuardBeforeWorkflowSwitch() {
        val result = runAutomation("nav-select", "--draft", "Event File > New Event File > Race Ops")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedLabels\":[\"Event File\", \"New Event File\", \"Race Ops\"]"))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Setup > Event File > New Event File\""))
        assertTrue(result.stdout.contains("\"selectedItemId\":\"setup.event-file.new\""))
        assertTrue(result.stdout.contains("\"guarded\":true"))
    }

    @Test
    fun navSelectDefaultDraftModeDoesNotGuardBeforeWorkflowSwitch() {
        val result = runAutomation("nav-select", "--default-draft", "Event File > New Event File > Race Ops")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedLabels\":[\"Event File\", \"New Event File\", \"Race Ops\"]"))
        assertTrue(result.stdout.contains("\"workflow\":\"Race Operations\""))
        assertTrue(result.stdout.contains("\"guarded\":false"))
    }

    @Test
    fun navSelectDraftModeDoesNotGuardSaveFromNewEventFile() {
        val result = runAutomation("nav-select", "--draft", "Event File > New Event File > Save Event")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedLabels\":[\"Event File\", \"New Event File\", \"Save Event\"]"))
        assertTrue(result.stdout.contains("\"action\":\"SaveEventFile\""))
        assertTrue(result.stdout.contains("\"guarded\":false"))
    }

    @Test
    fun navSelectReportsEventFileSaveAction() {
        val result = runAutomation("nav-select", "Event File > Save Event")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Setup > Event File\""))
        assertTrue(result.stdout.contains("\"selectedSection\":\"Races\""))
        assertTrue(result.stdout.contains("\"action\":\"SaveEventFile\""))
        assertTrue(result.stdout.contains("\"guarded\":false"))
    }

    @Test
    fun navSelectReportsAndroidEventFileActionsUnderEventFile() {
        val sendResult = runAutomation("nav-select", "Event File > Android... > Send Event to Android")
        val receiveResult = runAutomation("nav-select", "Event File > Android... > Receive File from Android")
        val exportResult = runAutomation("nav-select", "Event File > Android... > Save Android Event File...")

        assertEquals(0, sendResult.exitCode)
        assertTrue(sendResult.stdout.contains("\"action\":\"SendEventFileToAndroid\""))
        assertTrue(sendResult.stdout.contains("\"breadcrumb\":\"Setup > Event File > Android...\""))
        assertTrue(sendResult.stdout.contains("\"selectedItemId\":\"setup.event-file.android\""))

        assertEquals(0, receiveResult.exitCode)
        assertTrue(receiveResult.stdout.contains("\"action\":\"ReceiveFileFromAndroid\""))
        assertTrue(receiveResult.stdout.contains("\"breadcrumb\":\"Setup > Event File > Android...\""))
        assertTrue(receiveResult.stdout.contains("\"selectedItemId\":\"setup.event-file.android\""))

        assertEquals(0, exportResult.exitCode)
        assertTrue(exportResult.stdout.contains("\"action\":\"ExportAndroidRaceBackupJson\""))
        assertTrue(exportResult.stdout.contains("\"breadcrumb\":\"Setup > Event File > Android...\""))
        assertTrue(exportResult.stdout.contains("\"selectedItemId\":\"setup.event-file.android\""))
    }

    @Test
    fun navSelectPrefersCurrentMenuItemAfterWorkflowSelection() {
        val result = runAutomation("nav-select", "Results")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"workflow\":\"Results/File Export\""))
        assertTrue(result.stdout.contains("\"breadcrumb\":\"Results/File Export\""))
        assertTrue(result.stdout.contains("\"selectedSection\":\"Results\""))
        assertTrue(result.stdout.contains("\"selectedItemId\":\"results.home\""))
    }

    @Test
    fun siStatusDoesNotFailWithoutHardwareUnlessRequired() {
        val result = runAutomation("si-status")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"severity\":\"DISCONNECTED\""))
    }

    @Test
    fun siStatusFailsWithoutHardwareWhenRequired() {
        val result = runAutomation("si-status", "--require")

        assertEquals(69, result.exitCode)
        assertTrue(result.stdout.contains("\"severity\":\"DISCONNECTED\""))
    }

    @Test
    fun siStatusHandlesBadRequestedPort() {
        val result = runAutomation("si-status", "--require", "--port", "/dev/missing")

        assertEquals(69, result.exitCode)
        assertTrue(result.stderr.contains("SI serial port selection failed"))
    }

    @Test
    fun printerStatusUsesInjectedDiagnostics() {
        val result = runAutomation(
            "printer-status",
            printerDiagnostics = {
                DesktopPrinterDiagnostics(
                    selectedPrinterName = "Ticket Printer",
                    detectedPrinterNames = listOf("Ticket Printer (default)"),
                    readinessText = "Ready: Ticket Printer"
                )
            }
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"selectedPrinterName\":\"Ticket Printer\""))
        assertTrue(result.stdout.contains("\"readinessText\":\"Ready: Ticket Printer\""))
    }

    @Test
    fun unknownCommandReturnsUsageError() {
        val result = runAutomation("nope")

        assertEquals(64, result.exitCode)
        assertTrue(result.stderr.contains("Unknown desktop automation command"))
    }

    private fun runAutomation(
        vararg args: String,
        printerDiagnostics: (() -> DesktopPrinterDiagnostics)? = null
    ): AutomationResult {
        val stdoutBytes = ByteArrayOutputStream()
        val stderrBytes = ByteArrayOutputStream()
        val exitCode = DesktopAutomationCli.run(
            args = args.toList().toTypedArray(),
            out = PrintStream(stdoutBytes),
            err = PrintStream(stderrBytes),
            serialPortProvider = EmptySerialPortProvider,
            printerDiagnostics = printerDiagnostics
        )
        return AutomationResult(
            exitCode = exitCode,
            stdout = stdoutBytes.toString(Charsets.UTF_8),
            stderr = stderrBytes.toString(Charsets.UTF_8)
        )
    }

    private fun projectFile(
        name: String,
        eventId: String = "race",
        seriesLink: EventSeriesLink? = null,
        categories: List<EventCategoryData> = emptyList(),
        competitors: List<EventCompetitorData> = emptyList()
    ): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = eventId,
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-06-03T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = categories,
                aliases = emptyList(),
                competitorData = competitors,
                unmatchedReadoutData = emptyList()
            ),
            seriesLink = seriesLink
        )

    private fun categoryData(id: String, name: String, order: Int): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = id,
                raceId = "race",
                name = name,
                isMan = true,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = order,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )

    private fun competitorData(
        id: String,
        startNumber: Int,
        siNumber: Int?,
        drawnStartTimeSeconds: Long? = null,
        bibNumber: String = "",
        callSign: String = "",
        categoryId: String? = null,
        club: String = "OPEN"
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = categoryId,
                    firstName = id,
                    lastName = "Runner",
                    club = club,
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
                category = null
            ),
            readoutData = null
        )
}

private data class AutomationResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

private object EmptySerialPortProvider : DesktopSerialPortProvider {
    override fun listPorts(): List<DesktopSerialPort> = emptyList()

    override fun getPort(systemPortPath: String): DesktopSerialPort =
        error("No fake serial ports are available.")
}
