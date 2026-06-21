package org.openardf.radiooracle.desktop

import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.system.exitProcess
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinter
import org.openardf.radiooracle.desktop.usb.DesktopSerialPortProvider
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventSeriesIssueSeverity
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.files.CompetitorCsvImportProfile
import org.openardf.radiooracle.shared.files.EventCsvImports

fun main(args: Array<String>) {
    exitProcess(DesktopAutomationCli.run(args))
}

/**
 * Command-line hooks for automated desktop checks and local debugging.
 *
 * This is intentionally separate from the Compose UI. It gives CI and Codex a
 * stable app-control surface without adding a remote-control listener to the
 * beta desktop app.
 */
object DesktopAutomationCli {
    fun run(
        args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err,
        serialPortProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
        printerDiagnostics: (() -> DesktopPrinterDiagnostics)? = null
    ): Int {
        val command = args.firstOrNull()
        val commandArgs = args.drop(1)
        return when (command) {
            null,
            "help",
            "--help",
            "-h" -> {
                out.println(helpText())
                0
            }
            "version" -> {
                out.println(
                    jsonObject(
                        "command" to "version",
                        "baseVersion" to DesktopBuildInfo.baseVersion,
                        "buildSuffix" to DesktopBuildInfo.buildSuffix,
                        "displayVersion" to DesktopBuildInfo.displayVersion,
                        "windowTitle" to DesktopBuildInfo.windowTitle
                    )
                )
                0
            }
            "paths" -> {
                out.println(
                    jsonObject(
                        "command" to "paths",
                        "defaultEventFileDirectory" to DesktopEventFileLocations.defaultEventFileDirectory().toString(),
                        "preferredEventFileDirectory" to DesktopEventFileLocations.preferredEventFileDirectory().toString(),
                        "appDataDirectory" to DesktopAppDirectories.appDataDirectory().toString(),
                        "logDirectory" to DesktopAppDirectories.logDirectory().toString()
                    )
                )
                0
            }
            "logs" -> {
                DesktopDebugLog.initialize()
                out.println(
                    jsonObject(
                        "command" to "logs",
                        "logDirectory" to DesktopDebugLog.logDirectory().toString(),
                        "files" to DesktopDebugLog.logFiles().map { it.toString() }
                    )
                )
                0
            }
            "log-test" -> {
                DesktopDebugLog.initialize()
                DesktopDebugLog.info("Automation", commandArgs.joinToString(" ").ifBlank { "desktop automation log test" })
                out.println(
                    jsonObject(
                        "command" to "log-test",
                        "logDirectory" to DesktopDebugLog.logDirectory().toString(),
                        "files" to DesktopDebugLog.logFiles().map { it.toString() }
                    )
                )
                0
            }
            "open-event-file" -> openEventFile(commandArgs, out, err)
            "import-android-event-file" -> importAndroidEventFile(commandArgs, out, err)
            "export-android-event-file" -> exportAndroidEventFile(commandArgs, out, err)
            "import-competitors-csv" -> importCompetitorsCsv(commandArgs, out, err)
            "event-series-list" -> eventSeriesList(commandArgs, out, err)
            "event-series-add-event" -> eventSeriesAddEvent(commandArgs, out, err)
            "event-series-validate" -> eventSeriesValidate(commandArgs, out, err)
            "event-series-match" -> eventSeriesMatch(commandArgs, out, err)
            "event-series-start-fairness" -> eventSeriesStartFairness(commandArgs, out, err)
            "event-series-optimize-start-fairness" -> eventSeriesOptimizeStartFairness(commandArgs, out, err)
            "readiness-summary" -> readinessSummary(commandArgs, out, err)
            "recalculate-results" -> recalculateResults(commandArgs, out, err)
            "export-public-results-site" -> exportPublicResultsSite(commandArgs, out, err)
            "preview-public-results-site" -> previewPublicResultsSite(commandArgs, out, err)
            "nav-availability" -> navAvailability(commandArgs, out, err)
            "nav-select" -> navSelect(commandArgs, out, err)
            "nav-tree" -> navTree(commandArgs, out, err)
            "nav-audit" -> navAudit(commandArgs, out, err)
            "si-status" -> siStatus(commandArgs, out, err, serialPortProvider)
            "printer-status" -> printerStatus(commandArgs, out, err, printerDiagnostics)
            else -> {
                err.println("Unknown desktop automation command: $command")
                err.println(helpText())
                64
            }
        }
    }

    private fun recalculateResults(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull { !it.startsWith("--") }
        if (pathText.isNullOrBlank()) {
            err.println("recalculate-results requires an Event File path.")
            return 64
        }
        val writeChanges = "--write" in args
        return runCatching {
            val path = Path.of(pathText)
            val projectFile = DesktopProjectFiles.read(path)
            val outcome = EventProjectEditor.recalculateResults(projectFile)
            if (writeChanges) {
                DesktopProjectFiles.write(path, outcome.projectFile)
            }
            out.println(
                jsonObject(
                    "command" to "recalculate-results",
                    "path" to path.toAbsolutePath().normalize().toString(),
                    "write" to writeChanges,
                    "recalculatedCount" to outcome.recalculatedCount,
                    "changedCount" to outcome.changedCount,
                    "skippedStatusOnlyCount" to outcome.skippedStatusOnlyCount
                )
            )
            0
        }.getOrElse { error ->
            err.println("Result recalculation failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun exportPublicResultsSite(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val eventFileText = args.getOrNull(0)
        val siteRootText = args.getOrNull(1)
        if (eventFileText.isNullOrBlank() || siteRootText.isNullOrBlank()) {
            err.println("export-public-results-site requires Event File and public site root paths.")
            return 64
        }
        return runCatching {
            DesktopDebugLog.initialize()
            val eventFile = Path.of(eventFileText)
            val siteRoot = Path.of(siteRootText)
            val projectFile = DesktopProjectFiles.read(eventFile)
            val paths = DesktopProjectFiles.exportPublicResultsSite(siteRoot, projectFile)
            DesktopDebugLog.info(
                "PublicResults",
                "CLI generated public results site event=${paths.eventPath} root=${paths.directory} eventDirectory=${paths.eventDirectory}"
            )
            out.println(
                jsonObject(
                    "command" to "export-public-results-site",
                    "eventFile" to eventFile.toAbsolutePath().normalize().toString(),
                    "siteRoot" to paths.directory.toAbsolutePath().normalize().toString(),
                    "eventPath" to paths.eventPath,
                    "eventDirectory" to paths.eventDirectory.toAbsolutePath().normalize().toString(),
                    "rootIndexHtml" to paths.rootIndexHtml.toAbsolutePath().normalize().toString(),
                    "indexHtml" to paths.indexHtml.toAbsolutePath().normalize().toString(),
                    "publicResultsJson" to paths.publicResultsJson.toAbsolutePath().normalize().toString()
                )
            )
            0
        }.getOrElse { error ->
            DesktopDebugLog.error("PublicResults", "CLI public results export failed: ${error.message ?: error::class.simpleName}")
            err.println("Public results site export failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun previewPublicResultsSite(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val siteRootText = args.getOrNull(0)
        if (siteRootText.isNullOrBlank()) {
            err.println("preview-public-results-site requires a public site root path.")
            return 64
        }
        return runCatching {
            DesktopDebugLog.initialize()
            val siteRoot = Path.of(siteRootText)
            val requestedEventPath = args.getOrNull(1)
                ?.trim()
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
            val server = DesktopPublicResultSitePreviewServer(siteRoot)
            try {
                val rootUrl = server.start()
                val previewUrl = requestedEventPath?.let { "$rootUrl$it/" } ?: rootUrl
                val connection = URL(previewUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                DesktopDebugLog.info(
                    "PublicResults",
                    "CLI checked public results preview url=$previewUrl status=${connection.responseCode}"
                )
                out.println(
                    jsonObject(
                        "command" to "preview-public-results-site",
                        "siteRoot" to siteRoot.toAbsolutePath().normalize().toString(),
                        "eventPath" to requestedEventPath,
                        "url" to previewUrl,
                        "responseCode" to connection.responseCode,
                        "contentType" to connection.contentType,
                        "bodyBytes" to body.toByteArray(Charsets.UTF_8).size
                    )
                )
                0
            } finally {
                server.stop()
            }
        }.getOrElse { error ->
            DesktopDebugLog.error("PublicResults", "CLI public results preview failed: ${error.message ?: error::class.simpleName}")
            err.println("Public results site preview failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun navAvailability(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull { !it.startsWith("--") }
        return runCatching {
            val projectFile = pathText?.let { DesktopProjectFiles.read(Path.of(it)) }
            val readiness = DesktopNavigationReadiness.from(projectFile)
            val workflowItems = DesktopWorkflow.bottomBarEntries(readiness).map { workflow ->
                val enabled = DesktopNavigation.isWorkflowEnabled(workflow, readiness)
                val longClickOverride = DesktopNavigation.canLongClickOverrideDisabledWorkflow(workflow, readiness)
                mapOf(
                    "kind" to "workflow",
                    "workflow" to workflow.label,
                    "label" to workflow.shortLabel,
                    "enabled" to enabled,
                    "longClickOverride" to longClickOverride,
                    "disabledReason" to DesktopNavigation.disabledWorkflowReasonWithOverrideHint(workflow, readiness)
                )
            }
            val menuItems = DesktopWorkflow.entries.flatMap { workflow ->
                DesktopNavigation.rootItems(workflow).map { item ->
                    val enabled = DesktopNavigation.isItemEnabled(item, readiness)
                    val longClickOverride = DesktopNavigation.canLongClickOverrideDisabledMenu(item, readiness)
                    mapOf(
                        "kind" to "menu",
                        "workflow" to workflow.label,
                        "label" to item.label,
                        "enabled" to enabled,
                        "longClickOverride" to longClickOverride,
                        "disabledReason" to DesktopNavigation.disabledItemReasonWithMenuOverrideHint(item, readiness)
                    )
                }
            }
            val items = workflowItems + menuItems
            out.println(
                jsonObject(
                    "command" to "nav-availability",
                    "eventFile" to pathText,
                    "hasEventFile" to readiness.hasEventFile,
                    "longClickOverrideCount" to items.count { it["longClickOverride"] == true },
                    "items" to items
                )
            )
            0
        }.getOrElse { error ->
            err.println("Navigation availability failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun openEventFile(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull()
        if (pathText.isNullOrBlank()) {
            err.println("open-event-file requires an Event File path.")
            return 64
        }
        return runCatching {
            val path = Path.of(pathText)
            val projectFile = DesktopProjectFiles.read(path)
            val validationErrors = EventValidationRules.validateRaceData(projectFile.raceData)
            out.println(
                jsonObject(
                    "command" to "open-event-file",
                    "path" to path.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "categoryCount" to projectFile.raceData.categories.size,
                    "competitorCount" to projectFile.raceData.competitorData.size,
                    "unmatchedReadoutCount" to projectFile.raceData.unmatchedReadoutData.size,
                    "validationErrorCount" to validationErrors.size,
                    "validationErrors" to validationErrors
                )
            )
            0
        }.getOrElse { error ->
            err.println("Failed to open Event File: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun importAndroidEventFile(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val sourceText = args.getOrNull(0)
        val targetText = args.getOrNull(1)
        if (sourceText.isNullOrBlank() || targetText.isNullOrBlank()) {
            err.println("import-android-event-file requires Android Event File and target desktop Event File paths.")
            return 64
        }
        return runCatching {
            val source = Path.of(sourceText)
            val target = Path.of(targetText)
            val projectFile = DesktopProjectFiles.importAndroidRaceBackupJson(source) {
                UUID.randomUUID().toString()
            }
            DesktopProjectFiles.write(target, projectFile)
            val validationErrors = EventValidationRules.validateRaceData(projectFile.raceData)
            out.println(
                jsonObject(
                    "command" to "import-android-event-file",
                    "source" to source.toAbsolutePath().normalize().toString(),
                    "target" to target.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "categoryCount" to projectFile.raceData.categories.size,
                    "competitorCount" to projectFile.raceData.competitorData.size,
                    "unmatchedReadoutCount" to projectFile.raceData.unmatchedReadoutData.size,
                    "validationErrorCount" to validationErrors.size,
                    "validationErrors" to validationErrors
                )
            )
            0
        }.getOrElse { error ->
            err.println("Failed to import Android Event File: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun exportAndroidEventFile(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val sourceText = args.getOrNull(0)
        val targetText = args.getOrNull(1)
        if (sourceText.isNullOrBlank() || targetText.isNullOrBlank()) {
            err.println("export-android-event-file requires desktop Event File and target Android Event File paths.")
            return 64
        }
        return runCatching {
            val source = Path.of(sourceText)
            val target = Path.of(targetText)
            val projectFile = DesktopProjectFiles.read(source)
            DesktopProjectFiles.exportAndroidRaceBackupJson(target, projectFile)
            val validationErrors = EventValidationRules.validateRaceData(projectFile.raceData)
            out.println(
                jsonObject(
                    "command" to "export-android-event-file",
                    "source" to source.toAbsolutePath().normalize().toString(),
                    "target" to target.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "categoryCount" to projectFile.raceData.categories.size,
                    "competitorCount" to projectFile.raceData.competitorData.size,
                    "unmatchedReadoutCount" to projectFile.raceData.unmatchedReadoutData.size,
                    "validationErrorCount" to validationErrors.size,
                    "validationErrors" to validationErrors
                )
            )
            0
        }.getOrElse { error ->
            err.println("Failed to export Android Event File: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun importCompetitorsCsv(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val eventFileText = args.getOrNull(0)
        val csvText = args.getOrNull(1)
        if (eventFileText.isNullOrBlank() || csvText.isNullOrBlank()) {
            err.println("import-competitors-csv requires Event File and competitors CSV paths.")
            return 64
        }
        return runCatching {
            val eventFilePath = Path.of(eventFileText)
            val csvPath = Path.of(csvText)
            val originalProjectFile = DesktopProjectFiles.read(eventFilePath)
            val csv = Files.readString(csvPath)
            val profile = EventCsvImports.detectCompetitorProfile(csv)
            val result = EventCsvImports.parseAndroidCompetitorRows(csv)
            val updateExisting = "--update-existing" in args
            val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
                projectFile = originalProjectFile,
                rows = result.rows,
                competitorIdFactory = { UUID.randomUUID().toString() },
                categoryIdFactory = { UUID.randomUUID().toString() },
                duplicatePolicy = when {
                    profile == CompetitorCsvImportProfile.ARDF_EVENT_REGISTRATION ->
                        CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_INDEX
                    updateExisting ->
                        CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY
                    else ->
                        CompetitorCsvImportDuplicatePolicy.REJECT_DUPLICATES
                }
            )
            DesktopProjectFiles.write(eventFilePath, outcome.projectFile)
            val validationErrors = EventValidationRules.validateRaceData(outcome.projectFile.raceData)
            out.println(
                jsonObject(
                    "command" to "import-competitors-csv",
                    "eventFile" to eventFilePath.toAbsolutePath().normalize().toString(),
                    "csv" to csvPath.toAbsolutePath().normalize().toString(),
                    "profile" to profile.name,
                    "updateExisting" to updateExisting,
                    "raceName" to outcome.projectFile.raceData.race.name,
                    "importedCount" to outcome.importedCount,
                    "updatedCount" to outcome.updatedCount,
                    "invalidRowCount" to result.invalidLines.size,
                    "warnings" to outcome.warnings,
                    "categoryCount" to outcome.projectFile.raceData.categories.size,
                    "competitorCount" to outcome.projectFile.raceData.competitorData.size,
                    "validationErrorCount" to validationErrors.size,
                    "validationErrors" to validationErrors
                )
            )
            0
        }.getOrElse { error ->
            err.println("Failed to import Competitors CSV: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesList(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        if (manifestText.isNullOrBlank()) {
            err.println("event-series-list requires an Event Series manifest path.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val currentEventPath = optionValue(args, "--current-event")?.let(Path::of)
            val summaries = DesktopEventSeriesActions.eventSummaries(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath,
                currentEventPath = currentEventPath
            )
            out.println(
                jsonObject(
                    "command" to "event-series-list",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "eventCount" to summaries.size,
                    "missingCount" to summaries.count { !it.exists },
                    "events" to summaries.map { summary ->
                        mapOf(
                            "seriesEventId" to summary.seriesEventId,
                            "displayName" to summary.displayName,
                            "order" to summary.order,
                            "eventFilePath" to summary.eventFilePath,
                            "resolvedPath" to summary.resolvedPath.toAbsolutePath().normalize().toString(),
                            "exists" to summary.exists,
                            "current" to summary.isCurrentEvent,
                            "startDateTimeIso" to summary.startDateTimeIso,
                            "formatLabel" to summary.formatLabel
                        )
                    }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series list failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesAddEvent(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val eventFileText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || eventFileText.isNullOrBlank()) {
            err.println("event-series-add-event requires Event Series manifest and Event File paths.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val eventPath = Path.of(eventFileText)
            val seriesFolder = requireNotNull(manifestPath.parent) {
                "Event Series manifest has no parent folder."
            }
            val seriesFile = DesktopEventSeriesFiles.read(manifestPath)
            val eventProjectFile = DesktopEventSeriesFiles.readEvent(eventPath)
            val result = DesktopEventSeriesActions.addEventToSeries(
                seriesFile = seriesFile,
                eventPath = eventPath,
                seriesFolder = seriesFolder,
                eventProjectFile = eventProjectFile
            )
            DesktopEventSeriesFiles.write(manifestPath, result.seriesFile)
            DesktopEventSeriesFiles.writeEvent(eventPath, result.eventProjectFile)
            val link = requireNotNull(result.eventProjectFile.seriesLink) {
                "Added Event File did not receive an Event Series backlink."
            }
            val manifestEvent = result.seriesFile.events.first { it.seriesEventId == link.seriesEventId }
            out.println(
                jsonObject(
                    "command" to "event-series-add-event",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "eventFile" to eventPath.toAbsolutePath().normalize().toString(),
                    "seriesId" to link.seriesId,
                    "seriesEventId" to link.seriesEventId,
                    "eventFilePath" to manifestEvent.eventFilePath,
                    "eventCount" to result.seriesFile.events.size
                )
            )
            0
        }.getOrElse { error ->
            err.println("Add Event to Series failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesValidate(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        if (manifestText.isNullOrBlank()) {
            err.println("event-series-validate requires an Event Series manifest path.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val session = DesktopEventSeriesSession(DesktopEventSeriesFiles)
            session.open(manifestPath)
            val issues = session.validateLinkedEvents()
            out.println(
                jsonObject(
                    "command" to "event-series-validate",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "issueCount" to issues.size,
                    "errorCount" to issues.count { it.severity == EventSeriesIssueSeverity.ERROR },
                    "warningCount" to issues.count { it.severity == EventSeriesIssueSeverity.WARNING },
                    "issues" to issues.map { issue ->
                        mapOf(
                            "severity" to issue.severity.name,
                            "seriesEventId" to issue.seriesEventId,
                            "message" to issue.message
                        )
                    }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series validation failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesMatch(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val currentEventText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || currentEventText.isNullOrBlank()) {
            err.println("event-series-match requires Event Series manifest and current Event File paths.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val currentEventPath = Path.of(currentEventText)
            val summaries = DesktopEventSeriesActions.competitorMatchingSummaries(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath,
                currentEventPath = currentEventPath
            )
            out.println(
                jsonObject(
                    "command" to "event-series-match",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                    "comparisonRowCount" to summaries.size,
                    "comparedEventCount" to summaries.size,
                    "matchCount" to summaries.sumOf { it.matchCount },
                    "issueCount" to summaries.sumOf { it.issueCount },
                    "events" to summaries.map { summary ->
                        mapOf(
                            "firstSeriesEventId" to summary.firstSeriesEventId,
                            "firstEventName" to summary.firstEventName,
                            "firstCompetitorCount" to summary.firstCompetitorCount,
                            "secondSeriesEventId" to summary.secondSeriesEventId,
                            "secondEventName" to summary.secondEventName,
                            "secondCompetitorCount" to summary.secondCompetitorCount,
                            "includesCurrentEvent" to summary.includesCurrentEvent,
                            "matchCount" to summary.matchCount,
                            "siNumberMatchCount" to summary.siNumberMatchCount,
                            "bibNumberMatchCount" to summary.bibNumberMatchCount,
                            "callSignMatchCount" to summary.callSignMatchCount,
                            "overrideMatchCount" to summary.overrideMatchCount,
                            "issueCount" to summary.issueCount
                        )
                    }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series competitor matching failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesStartFairness(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val currentEventText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || currentEventText.isNullOrBlank()) {
            err.println("event-series-start-fairness requires Event Series manifest and current Event File paths.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val currentEventPath = Path.of(currentEventText)
            val currentProjectFile = DesktopProjectFiles.read(currentEventPath)
            val summary = DesktopEventSeriesActions.startFairnessSummary(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath,
                currentEventPath = currentEventPath,
                currentProjectFile = currentProjectFile
            )
            out.println(
                jsonObject(
                    "command" to "event-series-start-fairness",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                    "available" to (summary != null),
                    "seriesEventCount" to summary?.seriesEventCount,
                    "currentEventName" to summary?.currentEventName,
                    "currentEventOrder" to summary?.currentEventOrder,
                    "historyOrderDescription" to summary?.historyOrderDescription,
                    "missingEventFileCount" to summary?.missingEventFileCount,
                    "eventsWithGeneratedStartsCount" to summary?.eventsWithGeneratedStartsCount,
                    "eventsWithoutGeneratedStartsCount" to summary?.eventsWithoutGeneratedStartsCount,
                    "generatedStartRowCount" to summary?.generatedStartRowCount,
                    "identifiedGeneratedStartRowCount" to summary?.identifiedGeneratedStartRowCount,
                    "unidentifiedGeneratedStartRowCount" to summary?.unidentifiedGeneratedStartRowCount,
                    "competitorsWithIdentifiedHistoryCount" to summary?.competitorsWithIdentifiedHistoryCount,
                    "competitorsWithUnevenHistoryCount" to summary?.competitorsWithUnevenHistoryCount,
                    "fairnessNumber" to summary?.fairnessNumber,
                    "fairnessScoreCompetitorCount" to summary?.fairnessScoreCompetitorCount,
                    "fairnessExcessSpread" to summary?.fairnessExcessSpread,
                    "firstThirdStartCount" to summary?.firstThirdStartCount,
                    "middleThirdStartCount" to summary?.middleThirdStartCount,
                    "lateThirdStartCount" to summary?.lateThirdStartCount,
                    "competitorHistories" to summary?.competitorHistories?.map { history ->
                        mapOf(
                            "competitorName" to history.competitorName,
                            "identityLabel" to history.identityLabel,
                            "generatedStartCount" to history.generatedStartCount,
                            "thirdHistoryText" to history.thirdHistoryText,
                            "firstThirdCount" to history.firstThirdCount,
                            "middleThirdCount" to history.middleThirdCount,
                            "lateThirdCount" to history.lateThirdCount,
                            "spread" to history.spread,
                            "isUneven" to history.isUneven,
                            "recommendation" to history.recommendation
                        )
                    },
                    "balanceHistoryEventCount" to summary?.balanceHistoryEventCount,
                    "missingBalanceHistoryEventFileCount" to summary?.missingBalanceHistoryEventFileCount,
                    "balanceHistoryEventsWithStartsCount" to summary?.balanceHistoryEventsWithStartsCount,
                    "balanceHistoryStartRowCount" to summary?.balanceHistoryStartRowCount,
                    "identifiedBalanceHistoryStartRowCount" to summary?.identifiedBalanceHistoryStartRowCount,
                    "currentCompetitorCount" to summary?.currentCompetitorCount,
                    "identifiedCurrentCompetitorCount" to summary?.identifiedCurrentCompetitorCount
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series start fairness summary failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesOptimizeStartFairness(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val currentEventText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || currentEventText.isNullOrBlank()) {
            err.println("event-series-optimize-start-fairness requires Event Series manifest and current Event File paths.")
            return 64
        }
        val writeChanges = "--write" in args
        val seedSalt = optionValue(args, "--seed-salt") ?: "cli-default"
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val currentEventPath = Path.of(currentEventText)
            val result = DesktopEventSeriesActions.optimizeStartFairness(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath,
                currentEventPath = currentEventPath,
                seedSalt = seedSalt
            )
            if (writeChanges) {
                // CLI writes every changed file directly because it has no open desktop session.
                result.updatedEventFiles.forEach { updated ->
                    DesktopProjectFiles.write(updated.path, updated.projectFile)
                }
            }
            out.println(
                jsonObject(
                    "command" to "event-series-optimize-start-fairness",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                    "write" to writeChanges,
                    "seedSalt" to seedSalt,
                    "improved" to result.improved,
                    "alternateSolution" to result.alternateSolution,
                    "solutionSignature" to result.solutionSignature,
                    "initialScore" to result.initialScore,
                    "finalScore" to result.finalScore,
                    "initialUnevenHistoryCount" to result.initialUnevenHistoryCount,
                    "finalUnevenHistoryCount" to result.finalUnevenHistoryCount,
                    "initialSpreadSum" to result.initialSpreadSum,
                    "finalSpreadSum" to result.finalSpreadSum,
                    "attemptedCandidateCount" to result.attemptedCandidateCount,
                    "acceptedCandidateCount" to result.acceptedCandidateCount,
                    "completedPassCount" to result.completedPassCount,
                    "optimizedEventCount" to result.optimizedEventCount,
                    "updatedEvents" to result.updatedEventFiles.map { updated ->
                        mapOf(
                            "seriesEventId" to updated.seriesEventId,
                            "displayName" to updated.displayName,
                            "path" to updated.path.toAbsolutePath().normalize().toString(),
                            "current" to updated.isCurrentEvent
                        )
                    }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series start fairness optimization failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun readinessSummary(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull { !it.startsWith("--") }
        if (pathText.isNullOrBlank()) {
            err.println("readiness-summary requires an Event File path.")
            return 64
        }
        val requireReady = "--require-ready" in args
        return runCatching {
            val path = Path.of(pathText)
            val projectFile = DesktopProjectFiles.read(path)
            val diagnostics = DesktopProjectDiagnostics.from(projectFile)
            val lockedProtectedCourseCount = projectFile.raceData.categories.count {
                it.category.encryptedCourseInfo?.isNotBlank() == true
            }
            val activeReadinessIssues = diagnostics.readinessIssues
                .filterNot { it.contains("has course data but no competitors") }
            out.println(
                jsonObject(
                    "command" to "readiness-summary",
                    "path" to path.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "validationIssueCount" to diagnostics.validationIssues.size,
                    "readinessIssueCount" to diagnostics.readinessIssues.size,
                    "activeReadinessIssueCount" to activeReadinessIssues.size,
                    "lockedProtectedCourseCount" to lockedProtectedCourseCount,
                    "validationIssues" to diagnostics.validationIssues,
                    "readinessIssues" to diagnostics.readinessIssues
                )
            )
            if (requireReady && (diagnostics.validationIssues.isNotEmpty() || activeReadinessIssues.isNotEmpty())) {
                69
            } else {
                0
            }
        }.getOrElse { error ->
            err.println("Readiness summary failed: ${error.message ?: error::class.simpleName}")
            65
        }
    }

    private fun navSelect(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isEmpty()) {
            err.println("nav-select requires one or more menu labels.")
            return 64
        }
        val simulateDefaultDraft = args.firstOrNull() == "--default-draft"
        val simulateEditedDraft = args.firstOrNull() == "--draft" || args.firstOrNull() == "--edited-draft"
        val labels = navSelectLabels(if (simulateDefaultDraft || simulateEditedDraft) args.drop(1) else args)
        var state = DesktopNavState()
        var action: DesktopNavAction? = null
        var hasDefaultUnsavedNewEventDraft = false
        var hasEditedUnsavedNewEventDraft = false
        val selectedLabels = mutableListOf<String>()
        labels.forEach { label ->
            val currentItem = DesktopNavigation.findCurrentItemByLabel(state, label)
            val workflow = DesktopWorkflow.entries
                .firstOrNull { currentItem == null && (it.label == label || it.shortLabel == label) }
            if (workflow != null) {
                val nextState = state.switchWorkflow(workflow)
                if (
                    DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                        state,
                        nextState,
                        hasEditedUnsavedNewEventDraft
                    )
                ) {
                    out.println(navSelectJson(selectedLabels + label, state, action, guarded = true))
                    return 0
                }
                if (hasDefaultUnsavedNewEventDraft && DesktopNavigation.isLeavingNewEventFilePage(state, nextState)) {
                    hasDefaultUnsavedNewEventDraft = false
                }
                state = nextState
                action = null
                selectedLabels += label
                return@forEach
            }
            if (label == "< Back") {
                val nextState = state.back()
                if (
                    DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                        state,
                        nextState,
                        hasEditedUnsavedNewEventDraft
                    )
                ) {
                    out.println(navSelectJson(selectedLabels + label, state, action, guarded = true))
                    return 0
                }
                if (hasDefaultUnsavedNewEventDraft && DesktopNavigation.isLeavingNewEventFilePage(state, nextState)) {
                    hasDefaultUnsavedNewEventDraft = false
                }
                state = nextState
                action = null
                selectedLabels += label
                return@forEach
            }
            if (label == "Save Event") {
                action = DesktopNavAction.SaveEventFile
                selectedLabels += label
                return@forEach
            }
            val item = currentItem
            if (item == null) {
                err.println("Menu item '$label' is not available from ${DesktopNavigation.breadcrumb(state)}.")
                return 66
            }
            val selection = DesktopNavigation.selectItem(state, item)
            if (
                selection.action != DesktopNavAction.SaveEventFile &&
                DesktopNavigation.shouldGuardUnsavedNewEventDraft(
                    state,
                    selection.state,
                    hasEditedUnsavedNewEventDraft
                )
            ) {
                out.println(navSelectJson(selectedLabels + label, state, action, guarded = true))
                return 0
            }
            if (
                selection.action != DesktopNavAction.SaveEventFile &&
                hasDefaultUnsavedNewEventDraft &&
                DesktopNavigation.isLeavingNewEventFilePage(state, selection.state)
            ) {
                hasDefaultUnsavedNewEventDraft = false
            }
            state = selection.state
            action = selection.action
            if (simulateDefaultDraft && action == DesktopNavAction.NewEventFile) {
                hasDefaultUnsavedNewEventDraft = true
            }
            if (simulateEditedDraft && action == DesktopNavAction.NewEventFile) {
                hasEditedUnsavedNewEventDraft = true
            }
            action?.let {
                state = DesktopNavigation.returnToParentMenuAfterAction(state, it)
            }
            selectedLabels += label
        }
        out.println(navSelectJson(selectedLabels, state, action, guarded = false))
        return 0
    }

    private fun navSelectJson(
        selectedLabels: List<String>,
        state: DesktopNavState,
        action: DesktopNavAction?,
        guarded: Boolean
    ): String =
        jsonObject(
            "command" to "nav-select",
            "selectedLabels" to selectedLabels,
            "workflow" to state.workflow.label,
            "breadcrumb" to DesktopNavigation.breadcrumb(state),
            "selectedSection" to state.selectedSection.label,
            "selectedItemId" to state.selectedItemId,
            "action" to action?.name,
            "guarded" to guarded
        )

    private fun navSelectLabels(args: List<String>): List<String> =
        args.joinToString(" ")
            .split(">")
            .map(String::trim)
            .filter(String::isNotEmpty)

    private fun navTree(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val workflowName = optionValue(args, "--workflow")
        val workflows = if (workflowName.isNullOrBlank()) {
            DesktopWorkflow.entries
        } else {
            val workflow = workflowByCliName(workflowName) ?: run {
                err.println("Unknown workflow for nav-tree: $workflowName")
                return 64
            }
            listOf(workflow)
        }
        val items = workflows.flatMap { workflow ->
            flattenNavItems(DesktopNavigation.rootItems(workflow)).map { item ->
                mapOf(
                    "workflow" to workflow.label,
                    "id" to item.id,
                    "label" to item.label,
                    "path" to navPath(workflow, item.id),
                    "section" to item.section?.label,
                    "action" to item.action?.name,
                    "requiresEventFile" to item.requiresEventFile,
                    "childCount" to item.children.size
                )
            }
        }
        out.println(
            jsonObject(
                "command" to "nav-tree",
                "workflow" to workflowName,
                "itemCount" to items.size,
                "items" to items
            )
        )
        return 0
    }

    private fun navAudit(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val requireClean = "--require-clean" in args
        val issues = DesktopWorkflow.entries.flatMap { workflow ->
            navAuditIssuesForItems(workflow, DesktopNavigation.rootItems(workflow))
        }
        out.println(
            jsonObject(
                "command" to "nav-audit",
                "issueCount" to issues.size,
                "issues" to issues
            )
        )
        return if (requireClean && issues.isNotEmpty()) 69 else 0
    }

    private fun navAuditIssuesForItems(
        workflow: DesktopWorkflow,
        items: List<DesktopNavItem>
    ): List<Map<String, String?>> =
        items.flatMap { item ->
            val repeatedLabelIssues = item.children
                .filter { child -> child.label == item.label }
                .map { child ->
                    mapOf(
                        "type" to "duplicate-child-label",
                        "workflow" to workflow.label,
                        "path" to navPath(workflow, child.id),
                        "parentId" to item.id,
                        "childId" to child.id,
                        "label" to child.label
                    )
                }
            val redundantViewIssues = if (item.id == "setup.event-file.settings") {
                emptyList()
            } else {
                item.children
                    .filter { child ->
                        item.section != null &&
                            child.action == null &&
                            child.children.isEmpty() &&
                            child.section == item.section
                    }
                    .map { child ->
                        mapOf(
                            "type" to "redundant-view-child",
                            "workflow" to workflow.label,
                            "path" to navPath(workflow, child.id),
                            "parentId" to item.id,
                            "childId" to child.id,
                            "label" to child.label
                        )
                    }
            }
            repeatedLabelIssues + redundantViewIssues + navAuditIssuesForItems(workflow, item.children)
        }

    private fun flattenNavItems(items: List<DesktopNavItem>): List<DesktopNavItem> =
        items + items.flatMap { flattenNavItems(it.children) }

    private fun navPath(workflow: DesktopWorkflow, itemId: String): String {
        val labels = mutableListOf(workflow.label)
        fun visit(items: List<DesktopNavItem>): Boolean {
            items.forEach { item ->
                labels += item.label
                if (item.id == itemId) {
                    return true
                }
                if (visit(item.children)) {
                    return true
                }
                labels.removeAt(labels.lastIndex)
            }
            return false
        }
        visit(DesktopNavigation.rootItems(workflow))
        return labels.joinToString(" > ")
    }

    private fun workflowByCliName(name: String): DesktopWorkflow? =
        DesktopWorkflow.entries.firstOrNull { workflow ->
            workflow.label.equals(name, ignoreCase = true) ||
                workflow.shortLabel.equals(name, ignoreCase = true) ||
                workflow.name.equals(name, ignoreCase = true)
        }

    private fun siStatus(
        args: List<String>,
        out: PrintStream,
        err: PrintStream,
        serialPortProvider: DesktopSerialPortProvider
    ): Int {
        val requireStation = "--require" in args
        val requestedPort = optionValue(args, "--port")
        val ports = serialPortProvider.listPorts()
        val port = runCatching {
            if (requestedPort.isNullOrBlank()) {
                ports.firstOrNull { it.info.matchesSportIdent() }
            } else {
                serialPortProvider.getPort(requestedPort)
            }
        }.getOrElse { error ->
            err.println("SI serial port selection failed: ${error.message ?: error::class.simpleName}")
            return if (requireStation) 69 else 0
        }
        if (port == null) {
            out.println(
                jsonObject(
                    "command" to "si-status",
                    "severity" to "DISCONNECTED",
                    "status" to "SI station disconnected",
                    "detectedPorts" to ports.map { it.info.describe() }
                )
            )
            return if (requireStation) 69 else 0
        }

        return runCatching {
            val connection = DesktopSportIdentStationProbe().connect(port)
            val stationInfo = connection.stationInfo
            val severity = if (stationInfo.isReadoutMode == false) "WARNING" else "CONNECTED"
            out.println(
                jsonObject(
                    "command" to "si-status",
                    "severity" to severity,
                    "status" to "SI station ${stationInfo.serialNumber} connected",
                    "port" to port.info.describe(),
                    "baudRate" to connection.baudRate,
                    "serialNumber" to stationInfo.serialNumber,
                    "stationMode" to (stationInfo.stationModeLabel ?: "unknown"),
                    "stationModeCode" to stationInfo.stationModeCode,
                    "isReadoutMode" to stationInfo.isReadoutMode
                )
            )
            0
        }.getOrElse { error ->
            err.println("SI station probe failed: ${error.message ?: error::class.simpleName}")
            if (requireStation) 69 else 0
        }
    }

    private fun printerStatus(
        args: List<String>,
        out: PrintStream,
        err: PrintStream,
        printerDiagnostics: (() -> DesktopPrinterDiagnostics)?
    ): Int {
        val requirePrinter = "--require" in args
        return runCatching {
            val diagnostics = printerDiagnostics?.invoke()
                ?: DesktopPrinterDiagnostics.from(DesktopTicketPrinter().listPrinters())
            out.println(
                jsonObject(
                    "command" to "printer-status",
                    "selectedPrinterName" to diagnostics.selectedPrinterName,
                    "detectedPrinterNames" to diagnostics.detectedPrinterNames,
                    "readinessText" to diagnostics.readinessText
                )
            )
            if (requirePrinter && diagnostics.selectedPrinterName == null) 69 else 0
        }.getOrElse { error ->
            err.println("Printer status failed: ${error.message ?: error::class.simpleName}")
            if (requirePrinter) 69 else 0
        }
    }

    private fun optionValue(args: List<String>, name: String): String? {
        val index = args.indexOf(name)
        return if (index >= 0) args.getOrNull(index + 1) else null
    }

    private fun helpText(): String = """
        Radio-Oracle desktop automation

        Commands:
          version                         Print desktop build metadata as JSON.
          paths                           Print Event File, app-data, and log paths as JSON.
          logs                            Initialize logging and print current log files as JSON.
          log-test [message]              Write a desktop automation log entry.
          open-event-file <path>          Decode and validate an Event File.
          import-android-event-file <android-path> <desktop-path>
                                          Convert an Android Event File into a desktop Event File.
          export-android-event-file <desktop-path> <android-path>
                                          Save a desktop Event File as an Android Event File.
          import-competitors-csv <event-path> <csv-path> [--update-existing]
                                          Import competitors CSV into an Event File.
          event-series-list <manifest-path> [--current-event <event-path>]
                                          List series manifest events as JSON.
          event-series-add-event <manifest-path> <event-path>
                                          Add an Event File to a series manifest and write its backlink.
          event-series-validate <manifest-path>
                                          Validate a series manifest and linked Event Files.
          event-series-match <manifest-path> <current-event-path>
                                          Print competitor matching diagnostics for the current series event.
          event-series-start-fairness <manifest-path> <current-event-path>
                                          Print start fairness input diagnostics for the current series event.
          event-series-optimize-start-fairness <manifest-path> <current-event-path> [--write] [--seed-salt <text>]
                                          Try to improve or preserve series start fairness with randomized candidates; writes changed Event Files only with --write.
          readiness-summary [--require-ready] <event-path>
                                          Print validation and readiness issues as JSON.
          recalculate-results [--write] <event-path>
                                          Re-evaluate stored readouts against current courses.
          export-public-results-site <event-file> <site-root>
                                          Generate the Cloudflare Pages-ready public site root.
          preview-public-results-site <site-root> [event-path]
                                          Start the preview server, verify a URL, and stop it.
          nav-availability [event-path]   Print enabled/disabled menu state and long-click overrides.
          nav-select [--default-draft|--draft] <path>
                                          Simulate menu selection, using > between labels. Supports < Back.
          nav-tree [--workflow <name>]     Print the navigation tree as JSON for menu review.
          nav-audit [--require-clean]      Report duplicate and redundant navigation items as JSON.
          si-status [--require] [--port]  Probe attached SI station state.
          printer-status [--require]      Inspect desktop printer selection state.
    """.trimIndent()

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:${jsonValue(value)}"
        }

    private fun jsonValue(value: Any?): String =
        when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { entry ->
                "${jsonString(entry.key.toString())}:${jsonValue(entry.value)}"
            }
            else -> jsonString(value.toString())
        }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
}
