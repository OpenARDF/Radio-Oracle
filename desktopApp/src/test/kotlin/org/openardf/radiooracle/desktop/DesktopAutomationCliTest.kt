package org.openardf.radiooracle.desktop

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.usb.DesktopSerialPort
import org.openardf.radiooracle.desktop.usb.DesktopSerialPortProvider
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
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

    private fun projectFile(name: String, eventId: String = "race"): EventProjectFile =
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
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            )
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
