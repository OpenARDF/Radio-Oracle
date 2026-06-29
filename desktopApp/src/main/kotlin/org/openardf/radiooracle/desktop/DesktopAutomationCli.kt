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

import java.io.PrintStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess
import org.openardf.radiooracle.desktop.printing.DesktopPrinterDiagnostics
import org.openardf.radiooracle.desktop.printing.DesktopTicketPrinter
import org.openardf.radiooracle.desktop.usb.DesktopSerialPortProvider
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationProbe
import org.openardf.radiooracle.desktop.usb.JSerialCommDesktopSerialPortProvider
import org.openardf.radiooracle.shared.event.CompetitorCsvImportDuplicatePolicy
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesIssueSeverity
import org.openardf.radiooracle.shared.event.EventSeriesPackageFingerprints
import org.openardf.radiooracle.shared.event.EventStartListDetails
import org.openardf.radiooracle.shared.event.EventStartListRuleSeverity
import org.openardf.radiooracle.shared.event.EventValidationRules
import org.openardf.radiooracle.shared.event.StartDrawSettings
import org.openardf.radiooracle.shared.event.effectiveStartDrawSettings
import org.openardf.radiooracle.shared.files.CompetitorCsvImportProfile
import org.openardf.radiooracle.shared.files.EventCsvImports

fun main(args: Array<String>) {
    exitProcess(DesktopAutomationCli.run(args))
}

private data class EventStartListVerificationResult(
    val totalOrderCount: Long,
    val nonRedOrderCount: Long,
    val perfectOrderCount: Long,
    val currentOrderScore: Int,
    val currentOrderPerfect: Boolean,
    val currentOrderSignature: String,
    val currentOrderInPerfectSet: Boolean,
    val samplePerfectOrderSignatures: List<String>,
    val samplePerfectOrders: List<List<String>>,
    val generatorSampleCount: Int,
    val generatedUniqueOrderCount: Int,
    val generatedPerfectOrderCount: Int,
    val generatedPerfectOrderSignatures: List<String>
)

private data class SeriesStartFairnessVerificationEvent(
    val event: EventSeriesEvent,
    val path: Path,
    val projectFile: EventProjectFile,
    val lockedForOptimization: Boolean,
    val signatures: List<SeriesStartFairnessThirdSignature>,
    val exhaustedOrderCount: Long,
    val eventPerfectOrderCount: Long
)

private data class SeriesStartFairnessThirdSignature(
    val identityThirds: Map<String, Int>,
    val signature: String
)

private data class SeriesStartFairnessVerificationScore(
    val unevenHistoryCount: Int,
    val spreadSum: Int,
    val squaredSpreadSum: Int
) : Comparable<SeriesStartFairnessVerificationScore> {
    val value: Int =
        unevenHistoryCount * 100_000 +
            spreadSum * 1_000 +
            squaredSpreadSum

    override fun compareTo(other: SeriesStartFairnessVerificationScore): Int =
        compareValuesBy(
            this,
            other,
            SeriesStartFairnessVerificationScore::unevenHistoryCount,
            SeriesStartFairnessVerificationScore::spreadSum,
            SeriesStartFairnessVerificationScore::squaredSpreadSum
        )
}

private data class SeriesStartFairnessExhaustiveResult(
    val bestScore: SeriesStartFairnessVerificationScore,
    val optimalCombinationCount: Long,
    val sampleOptimalCombinationSignatures: List<String>
)

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
            "remove-category" -> removeCategory(commandArgs, out, err)
            "event-series-list" -> eventSeriesList(commandArgs, out, err)
            "event-series-add-event" -> eventSeriesAddEvent(commandArgs, out, err)
            "event-series-validate" -> eventSeriesValidate(commandArgs, out, err)
            "event-series-export" -> eventSeriesExport(commandArgs, out, err)
            "event-series-package-fingerprint" -> eventSeriesPackageFingerprint(commandArgs, out, err)
            "event-series-match" -> eventSeriesMatch(commandArgs, out, err)
            "event-series-start-fairness" -> eventSeriesStartFairness(commandArgs, out, err)
            "event-series-optimize-start-fairness" -> eventSeriesOptimizeStartFairness(commandArgs, out, err)
            "event-series-start-fairness-verify" -> eventSeriesStartFairnessVerify(commandArgs, out, err)
            "event-start-list-verify" -> eventStartListVerify(commandArgs, out, err)
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

    private fun eventStartListVerify(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val pathText = args.firstOrNull { !it.startsWith("--") }
        if (pathText.isNullOrBlank()) {
            err.println("event-start-list-verify requires an Event File path.")
            return 64
        }
        val maxCompetitors = optionValue(args, "--max-competitors")?.toIntOrNull() ?: 10
        val sampleLimit = optionValue(args, "--sample-limit")?.toIntOrNull() ?: 12
        val generatorSamples = optionValue(args, "--generator-samples")?.toIntOrNull() ?: 0
        return runCatching {
            val path = Path.of(pathText)
            val projectFile = DesktopProjectFiles.read(path)
            val settings = projectFile.raceData.effectiveStartDrawSettings()
            val competitorData = projectFile.raceData.competitorData
                .filter { data ->
                    val competitor = data.competitorCategory.competitor
                    val categoryId = data.competitorCategory.category?.id ?: competitor.categoryId
                    categoryId != null
                }
                .sortedWith(
                    compareBy<EventCompetitorData> { it.competitorCategory.competitor.startNumber }
                        .thenBy { it.competitorCategory.competitor.id }
                )
            require(maxCompetitors >= 1) {
                "--max-competitors must be at least 1."
            }
            require(competitorData.size <= maxCompetitors) {
                "Event has ${competitorData.size} drawable competitors; refusing exhaustive ${competitorData.size}! search. " +
                    "Pass a larger --max-competitors value if this is intentional."
            }

            val result = verifyEventStartLists(
                projectFile = projectFile,
                competitorData = competitorData,
                settings = settings,
                sampleLimit = sampleLimit.coerceAtLeast(0),
                generatorSamples = generatorSamples.coerceAtLeast(0)
            )
            out.println(
                jsonObject(
                    "command" to "event-start-list-verify",
                    "path" to path.toAbsolutePath().normalize().toString(),
                    "raceName" to projectFile.raceData.race.name,
                    "drawableCompetitorCount" to competitorData.size,
                    "totalOrderCount" to result.totalOrderCount,
                    "nonRedOrderCount" to result.nonRedOrderCount,
                    "perfectOrderCount" to result.perfectOrderCount,
                    "currentOrderScore" to result.currentOrderScore,
                    "currentOrderPerfect" to result.currentOrderPerfect,
                    "currentOrderSignature" to result.currentOrderSignature,
                    "currentOrderInPerfectSet" to result.currentOrderInPerfectSet,
                    "generatorSampleCount" to result.generatorSampleCount,
                    "generatedUniqueOrderCount" to result.generatedUniqueOrderCount,
                    "generatedPerfectOrderCount" to result.generatedPerfectOrderCount,
                    "generatedPerfectOrderSignatures" to result.generatedPerfectOrderSignatures,
                    "settings" to mapOf(
                        "intervalSeconds" to settings.intervalSeconds,
                        "clubHandling" to settings.options.clubHandling.name,
                        "startersPerStartTime" to settings.options.startersPerStartTime,
                        "startGroupMode" to settings.options.startGroupMode.name
                    ),
                    "samplePerfectOrderSignatures" to result.samplePerfectOrderSignatures,
                    "samplePerfectOrders" to result.samplePerfectOrders
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event start-list verification failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun verifyEventStartLists(
        projectFile: EventProjectFile,
        competitorData: List<EventCompetitorData>,
        settings: StartDrawSettings,
        sampleLimit: Int,
        generatorSamples: Int
    ): EventStartListVerificationResult {
        val currentQuality = EventStartListDetails.from(projectFile.raceData).quality
        val currentSignature = drawnOrderSignature(projectFile, competitorData)
        val mutableOrder = competitorData.toMutableList()
        var totalOrderCount = 0L
        var nonRedOrderCount = 0L
        var perfectOrderCount = 0L
        var currentOrderInPerfectSet = false
        val samplePerfectOrderSignatures = mutableListOf<String>()
        val samplePerfectOrders = mutableListOf<List<String>>()

        fun visit(index: Int) {
            if (index == mutableOrder.size) {
                totalOrderCount += 1
                val orderedIds = mutableOrder.map { it.competitorCategory.competitor.id }
                val candidate = projectFile.withDrawnOrder(orderedIds, settings)
                val quality = EventStartListDetails.from(candidate.raceData).quality
                val signature = drawnOrderSignature(candidate, competitorData)
                if (quality.severity != EventStartListRuleSeverity.RED) {
                    nonRedOrderCount += 1
                }
                if (quality.score == 100) {
                    perfectOrderCount += 1
                    if (signature == currentSignature) {
                        currentOrderInPerfectSet = true
                    }
                    if (samplePerfectOrderSignatures.size < sampleLimit) {
                        samplePerfectOrderSignatures += signature
                        samplePerfectOrders += mutableOrder.map { data ->
                            val competitor = data.competitorCategory.competitor
                            "${competitor.startNumber} ${competitor.firstName} ${competitor.lastName}".trim()
                        }
                    }
                }
                return
            }
            for (candidateIndex in index until mutableOrder.size) {
                mutableOrder.swap(index, candidateIndex)
                visit(index + 1)
                mutableOrder.swap(index, candidateIndex)
            }
        }

        visit(0)
        val generatedSignatures = linkedSetOf<String>()
        val generatedPerfectSignatures = linkedSetOf<String>()
        val generatorOptions = settings.options.forEventStartListGeneration()
        repeat(generatorSamples) { index ->
            val generated = EventProjectEditor.drawStartList(
                projectFile = projectFile,
                intervalText = settings.intervalText,
                options = generatorOptions.copy(seed = "verify-generator-${index + 1}")
            )
            val signature = drawnOrderSignature(generated, competitorData)
            generatedSignatures += signature
            if (EventStartListDetails.from(generated.raceData).quality.score == 100) {
                generatedPerfectSignatures += signature
            }
        }
        return EventStartListVerificationResult(
            totalOrderCount = totalOrderCount,
            nonRedOrderCount = nonRedOrderCount,
            perfectOrderCount = perfectOrderCount,
            currentOrderScore = currentQuality.score,
            currentOrderPerfect = currentQuality.score == 100,
            currentOrderSignature = currentSignature,
            currentOrderInPerfectSet = currentOrderInPerfectSet,
            samplePerfectOrderSignatures = samplePerfectOrderSignatures,
            samplePerfectOrders = samplePerfectOrders,
            generatorSampleCount = generatorSamples,
            generatedUniqueOrderCount = generatedSignatures.size,
            generatedPerfectOrderCount = generatedPerfectSignatures.size,
            generatedPerfectOrderSignatures = generatedPerfectSignatures.take(sampleLimit)
        )
    }

    private fun verifySeriesEventStartSignatures(
        event: EventSeriesEvent,
        path: Path,
        projectFile: EventProjectFile,
        maxEventCompetitors: Int,
        maxEventOrders: Long
    ): SeriesStartFairnessVerificationEvent {
        val locked = projectFile.raceData.effectiveStartDrawSettings().lockedForSeriesOptimization
        if (locked) {
            return SeriesStartFairnessVerificationEvent(
                event = event,
                path = path,
                projectFile = projectFile,
                lockedForOptimization = true,
                signatures = listOf(currentSeriesThirdSignature(projectFile)),
                exhaustedOrderCount = 0,
                eventPerfectOrderCount = 0
            )
        }
        val settings = projectFile.raceData.effectiveStartDrawSettings()
        val competitorData = drawableCompetitorData(projectFile)
        require(competitorData.size <= maxEventCompetitors) {
            "Event '${event.displayName}' has ${competitorData.size} drawable competitors; refusing exhaustive search above " +
                "--max-event-competitors $maxEventCompetitors."
        }
        val possibleOrderCount = factorial(competitorData.size)
        require(possibleOrderCount <= maxEventOrders) {
            "Event '${event.displayName}' has $possibleOrderCount possible start orders; refusing exhaustive search above " +
                "--max-event-orders $maxEventOrders."
        }

        val mutableOrder = competitorData.toMutableList()
        val signatures = linkedMapOf<String, SeriesStartFairnessThirdSignature>()
        var exhaustedOrderCount = 0L
        var eventPerfectOrderCount = 0L

        fun visit(index: Int) {
            if (index == mutableOrder.size) {
                exhaustedOrderCount++
                val orderedIds = mutableOrder.map { it.competitorCategory.competitor.id }
                val candidate = projectFile.withDrawnOrder(orderedIds, settings)
                if (EventStartListDetails.from(candidate.raceData).quality.score == 100) {
                    eventPerfectOrderCount++
                    val signature = currentSeriesThirdSignature(candidate)
                    signatures.putIfAbsent(signature.signature, signature)
                }
                return
            }
            for (candidateIndex in index until mutableOrder.size) {
                mutableOrder.swap(index, candidateIndex)
                visit(index + 1)
                mutableOrder.swap(index, candidateIndex)
            }
        }

        visit(0)
        return SeriesStartFairnessVerificationEvent(
            event = event,
            path = path,
            projectFile = projectFile,
            lockedForOptimization = false,
            signatures = signatures.values.toList(),
            exhaustedOrderCount = exhaustedOrderCount,
            eventPerfectOrderCount = eventPerfectOrderCount
        )
    }

    private fun exhaustiveSeriesStartFairnessSearch(
        events: List<SeriesStartFairnessVerificationEvent>,
        sampleLimit: Int
    ): SeriesStartFairnessExhaustiveResult {
        var bestScore: SeriesStartFairnessVerificationScore? = null
        var optimalCombinationCount = 0L
        val selected = mutableListOf<SeriesStartFairnessThirdSignature>()
        val samples = mutableListOf<String>()

        fun visit(eventIndex: Int) {
            if (eventIndex == events.size) {
                val score = seriesVerificationScore(selected)
                val currentBest = bestScore
                if (currentBest == null || score < currentBest) {
                    bestScore = score
                    optimalCombinationCount = 1
                    samples.clear()
                    if (samples.size < sampleLimit) {
                        samples += selected.joinToString("|") { it.signature }
                    }
                } else if (score == currentBest) {
                    optimalCombinationCount++
                    if (samples.size < sampleLimit) {
                        samples += selected.joinToString("|") { it.signature }
                    }
                }
                return
            }
            events[eventIndex].signatures.forEach { signature ->
                selected += signature
                visit(eventIndex + 1)
                selected.removeAt(selected.lastIndex)
            }
        }

        visit(0)
        return SeriesStartFairnessExhaustiveResult(
            bestScore = requireNotNull(bestScore) { "No series start-third combinations were available." },
            optimalCombinationCount = optimalCombinationCount,
            sampleOptimalCombinationSignatures = samples
        )
    }

    private fun seriesVerificationEventSummary(event: SeriesStartFairnessVerificationEvent): Map<String, Any?> =
        mapOf(
            "seriesEventId" to event.event.seriesEventId,
            "displayName" to event.event.displayName,
            "lockedForOptimization" to event.lockedForOptimization,
            "exhaustedOrderCount" to event.exhaustedOrderCount,
            "eventPerfectOrderCount" to event.eventPerfectOrderCount,
            "uniqueThirdSignatureCount" to event.signatures.size
        )

    private fun seriesVerificationScore(
        signatures: List<SeriesStartFairnessThirdSignature>
    ): SeriesStartFairnessVerificationScore {
        val histories = signatures
            .flatMap { signature -> signature.identityThirds.map { (identity, third) -> identity to third } }
            .groupBy({ it.first }, { it.second })
            .values
        var unevenHistoryCount = 0
        var spreadSum = 0
        var squaredSpreadSum = 0
        histories.forEach { history ->
            val counts = (1..3).map { third -> history.count { it == third } }
            val spread = counts.maxOrNull().orZero() - counts.minOrNull().orZero()
            if (history.size >= 2 && spread > 1) {
                unevenHistoryCount++
            }
            spreadSum += spread
            squaredSpreadSum += spread * spread
        }
        return SeriesStartFairnessVerificationScore(
            unevenHistoryCount = unevenHistoryCount,
            spreadSum = spreadSum,
            squaredSpreadSum = squaredSpreadSum
        )
    }

    private fun currentSeriesThirdSignature(projectFile: EventProjectFile): SeriesStartFairnessThirdSignature {
        val scheduled = projectFile.raceData.competitorData
            .mapNotNull { data ->
                val competitor = data.competitorCategory.competitor
                val startSeconds = competitor.drawnStartTimeSeconds ?: return@mapNotNull null
                startSeconds to competitor
            }
            .sortedWith(compareBy({ it.first }, { it.second.startNumber }, { it.second.id }))
        val identityThirds = scheduled.mapIndexedNotNull { index, (_, competitor) ->
            val identity = seriesStartFairnessIdentityKey(competitor) ?: return@mapIndexedNotNull null
            identity to startThirdForSlot(index, scheduled.size)
        }.toMap()
        val signature = identityThirds.toSortedMap()
            .entries
            .joinToString(",") { "${it.key}:${it.value}" }
        return SeriesStartFairnessThirdSignature(identityThirds, signature)
    }

    private fun drawableCompetitorData(projectFile: EventProjectFile): List<EventCompetitorData> =
        projectFile.raceData.competitorData
            .filter { data ->
                val competitor = data.competitorCategory.competitor
                val categoryId = data.competitorCategory.category?.id ?: competitor.categoryId
                categoryId != null
            }
            .sortedWith(
                compareBy<EventCompetitorData> { it.competitorCategory.competitor.startNumber }
                    .thenBy { it.competitorCategory.competitor.id }
            )

    private fun seriesStartFairnessIdentityKey(competitor: EventCompetitor): String? =
        competitor.siNumber?.takeIf { it > 0 }?.let { "si:$it" }
            ?: competitor.bibNumber.trim().takeIf { it.isNotEmpty() }?.let { "bib:${it.uppercase()}" }
            ?: competitor.callSign.trim().takeIf { it.isNotEmpty() }?.let { "call:${it.uppercase()}" }

    private fun startThirdForSlot(slotIndex: Int, slotCount: Int): Int =
        if (slotCount <= 0) {
            1
        } else {
            (((slotIndex * 3) / slotCount) + 1).coerceIn(1, 3)
        }

    private fun factorial(value: Int): Long =
        (2..value).fold(1L) { product, item -> product * item }

    private fun EventProjectFile.withDrawnOrder(
        orderedCompetitorIds: List<String>,
        settings: StartDrawSettings
    ): EventProjectFile {
        val startSecondsByCompetitorId = orderedCompetitorIds.withIndex().associate { (index, competitorId) ->
            competitorId to (index / settings.options.startersPerStartTime).toLong() * settings.intervalSeconds
        }
        return copy(
            raceData = raceData.copy(
                competitorData = raceData.competitorData.map { data ->
                    val competitor = data.competitorCategory.competitor
                    val startSeconds = startSecondsByCompetitorId[competitor.id]
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = competitor.copy(drawnStartTimeSeconds = startSeconds)
                        )
                    )
                }
            )
        )
    }

    private fun drawnOrderSignature(projectFile: EventProjectFile, competitorData: List<EventCompetitorData>): String {
        val competitorById = competitorData.associateBy { it.competitorCategory.competitor.id }
        return projectFile.raceData.competitorData
            .mapNotNull { data ->
                val competitor = data.competitorCategory.competitor
                competitor.drawnStartTimeSeconds?.let { startSeconds ->
                    startSeconds to competitor
                }
            }
            .sortedWith(compareBy({ it.first }, { it.second.startNumber }, { it.second.id }))
            .mapNotNull { (_, competitor) ->
                competitorById[competitor.id]?.competitorCategory?.competitor?.id
            }
            .joinToString(">")
    }

    private fun <T> MutableList<T>.swap(firstIndex: Int, secondIndex: Int) {
        if (firstIndex == secondIndex) return
        val first = this[firstIndex]
        this[firstIndex] = this[secondIndex]
        this[secondIndex] = first
    }

    private fun Int?.orZero(): Int = this ?: 0

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
                        CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_PERSON_ID
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

    private fun removeCategory(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val eventFileText = args.getOrNull(0)
        val categoryText = args.getOrNull(1)
        if (eventFileText.isNullOrBlank() || categoryText.isNullOrBlank()) {
            err.println("remove-category requires Event File path and category ID or unique category name.")
            return 64
        }
        val deleteCompetitors = "--delete-competitors" in args
        val writeChanges = "--write" in args
        return runCatching {
            DesktopDebugLog.initialize()
            val eventFilePath = Path.of(eventFileText)
            val projectFile = DesktopProjectFiles.read(eventFilePath)
            val result = DesktopCategoryActions.removeCategory(
                projectFile = projectFile,
                categoryIdOrName = categoryText,
                deleteCompetitors = deleteCompetitors
            )
            if (writeChanges) {
                DesktopProjectFiles.write(eventFilePath, result.projectFile)
            }
            DesktopDebugLog.info(
                "Category",
                "CLI removed category id=${result.categoryId} name=${result.categoryName} " +
                    "deleteCompetitors=$deleteCompetitors write=$writeChanges " +
                    "removedCompetitors=${result.removedCompetitorCount} " +
                    "hadProtectedCourseData=${result.hadProtectedCourseData}"
            )
            out.println(
                jsonObject(
                    "command" to "remove-category",
                    "eventFile" to eventFilePath.toAbsolutePath().normalize().toString(),
                    "write" to writeChanges,
                    "categoryId" to result.categoryId,
                    "categoryName" to result.categoryName,
                    "deleteCompetitors" to deleteCompetitors,
                    "hadProtectedCourseData" to result.hadProtectedCourseData,
                    "beforeCategoryCount" to result.beforeCategoryIds.size,
                    "afterCategoryCount" to result.afterCategoryIds.size,
                    "beforeCategoryIds" to result.beforeCategoryIds,
                    "afterCategoryIds" to result.afterCategoryIds,
                    "beforeCompetitorCount" to result.beforeCompetitorIds.size,
                    "afterCompetitorCount" to result.afterCompetitorIds.size,
                    "removedCompetitorCount" to result.removedCompetitorCount
                )
            )
            0
        }.getOrElse { error ->
            DesktopDebugLog.error("Category", "CLI remove category failed: ${error.message ?: error::class.simpleName}")
            err.println("Failed to remove category: ${error.message ?: error::class.simpleName}")
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
        val requireClean = "--require-clean" in args
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val session = DesktopEventSeriesSession(DesktopEventSeriesFiles)
            session.open(manifestPath)
            val issues = session.validateLinkedEvents()
            val errorCount = issues.count { it.severity == EventSeriesIssueSeverity.ERROR }
            val warningCount = issues.count { it.severity == EventSeriesIssueSeverity.WARNING }
            out.println(
                jsonObject(
                    "command" to "event-series-validate",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "issueCount" to issues.size,
                    "errorCount" to errorCount,
                    "warningCount" to warningCount,
                    "requireClean" to requireClean,
                    "issues" to issues.map { issue ->
                        mapOf(
                            "severity" to issue.severity.name,
                            "seriesEventId" to issue.seriesEventId,
                            "message" to issue.message
                        )
                    }
                )
            )
            if (requireClean && issues.isNotEmpty()) 69 else 0
        }.getOrElse { error ->
            err.println("Event Series validation failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesExport(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val targetFolderText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || targetFolderText.isNullOrBlank()) {
            err.println("event-series-export requires Event Series manifest and target folder paths.")
            return 64
        }
        return runCatching {
            val manifestPath = Path.of(manifestText)
            val targetFolder = Path.of(targetFolderText)
            val result = DesktopEventSeriesActions.exportSeries(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath,
                targetFolder = targetFolder
            )
            out.println(
                jsonObject(
                    "command" to "event-series-export",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "targetFolder" to targetFolder.toAbsolutePath().normalize().toString(),
                    "exportedManifest" to result.manifestPath.toAbsolutePath().normalize().toString(),
                    "eventFileCount" to result.eventFilePaths.size,
                    "eventFiles" to result.eventFilePaths.map { it.toAbsolutePath().normalize().toString() }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series export failed: ${error.message ?: error::class.simpleName}")
            66
        }
    }

    private fun eventSeriesPackageFingerprint(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val packageText = args.getOrNull(0)
        if (packageText.isNullOrBlank()) {
            err.println("event-series-package-fingerprint requires an Event Series package ZIP path.")
            return 64
        }
        return runCatching {
            val packagePath = Path.of(packageText)
            val fingerprint = EventSeriesPackageFingerprints.fromTextEntries(zipTextEntries(packagePath))
            out.println(
                jsonObject(
                    "command" to "event-series-package-fingerprint",
                    "package" to packagePath.toAbsolutePath().normalize().toString(),
                    "seriesId" to fingerprint.seriesId,
                    "name" to fingerprint.name,
                    "memberCount" to fingerprint.events.size,
                    "competitorMatchOverrideCount" to fingerprint.competitorMatchOverrides.size,
                    "events" to fingerprint.events.map { event ->
                        mapOf(
                            "seriesEventId" to event.seriesEventId,
                            "eventFilePath" to event.eventFilePath,
                            "order" to event.order,
                            "displayName" to event.displayName,
                            "startDateTimeIso" to event.startDateTimeIso,
                            "formatLabel" to event.formatLabel,
                            "raceName" to event.raceName,
                            "raceStartDateTimeIso" to event.raceStartDateTimeIso,
                            "raceType" to event.raceType,
                            "raceLevel" to event.raceLevel,
                            "raceBand" to event.raceBand,
                            "timeLimitSeconds" to event.timeLimitSeconds,
                            "seriesLink" to event.seriesLink?.let { link ->
                                mapOf("seriesId" to link.seriesId, "seriesEventId" to link.seriesEventId)
                            }
                        )
                    }
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series package fingerprint failed: ${error.message ?: error::class.simpleName}")
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
            val identityCoverageSummaries = DesktopEventSeriesActions.competitorIdentityCoverageSummaries(
                store = DesktopEventSeriesFiles,
                manifestPath = manifestPath
            )
            val comparedEventCount = summaries
                .flatMap { listOf(it.firstSeriesEventId, it.secondSeriesEventId) }
                .distinct()
                .size
            out.println(
                jsonObject(
                    "command" to "event-series-match",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                    "comparisonRowCount" to summaries.size,
                    "comparedEventCount" to comparedEventCount,
                    "identityCoverageRowCount" to identityCoverageSummaries.size,
                    "allEventIdentityCount" to identityCoverageSummaries.count {
                        it.presentEventCount == it.totalReadableEventCount && it.duplicateEventNames.isEmpty()
                    },
                    "partialIdentityCount" to identityCoverageSummaries.count { it.missingEventNames.isNotEmpty() },
                    "duplicateIdentityCount" to identityCoverageSummaries.count { it.duplicateEventNames.isNotEmpty() },
                    "matchCount" to summaries.sumOf { it.matchCount },
                    "issueCount" to summaries.sumOf { it.issueCount },
                    "identityCoverage" to identityCoverageSummaries.map { summary ->
                        mapOf(
                            "identityLabel" to summary.identityLabel,
                            "competitorName" to summary.competitorName,
                            "presentEventCount" to summary.presentEventCount,
                            "totalReadableEventCount" to summary.totalReadableEventCount,
                            "presentEventNames" to summary.presentEventNames,
                            "missingEventNames" to summary.missingEventNames,
                            "occurrenceCount" to summary.occurrenceCount,
                            "duplicateEventNames" to summary.duplicateEventNames
                        )
                    },
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
                    "lockedForOptimizationEventCount" to summary?.lockedForOptimizationEventCount,
                    "unlockedForOptimizationEventCount" to summary?.unlockedForOptimizationEventCount,
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

    private fun eventSeriesStartFairnessVerify(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val manifestText = args.getOrNull(0)
        val currentEventText = args.getOrNull(1)
        if (manifestText.isNullOrBlank() || currentEventText.isNullOrBlank()) {
            err.println("event-series-start-fairness-verify requires Event Series manifest and current Event File paths.")
            return 64
        }
        val maxEvents = optionValue(args, "--max-events")?.toIntOrNull() ?: 4
        val maxEventCompetitors = optionValue(args, "--max-event-competitors")?.toIntOrNull() ?: 9
        val maxEventOrders = optionValue(args, "--max-event-orders")?.toLongOrNull() ?: 500_000L
        val maxCombinations = optionValue(args, "--max-combinations")?.toLongOrNull() ?: 1_000_000L
        val optimizerSamples = optionValue(args, "--optimizer-samples")?.toIntOrNull() ?: 0
        val optimizerPasses = optionValue(args, "--optimizer-passes")?.toIntOrNull() ?: 3
        val optimizerCandidates = optionValue(args, "--optimizer-candidates")?.toIntOrNull() ?: 32
        val sampleLimit = optionValue(args, "--sample-limit")?.toIntOrNull() ?: 12
        return runCatching {
            require(maxEvents >= 1) { "--max-events must be at least 1." }
            require(maxEventCompetitors >= 1) { "--max-event-competitors must be at least 1." }
            require(maxEventOrders >= 1L) { "--max-event-orders must be at least 1." }
            require(maxCombinations >= 1L) { "--max-combinations must be at least 1." }
            require(optimizerSamples >= 0) { "--optimizer-samples must be zero or greater." }
            require(optimizerPasses >= 1) { "--optimizer-passes must be at least 1." }
            require(optimizerCandidates >= 1) { "--optimizer-candidates must be at least 1." }

            val manifestPath = Path.of(manifestText)
            val currentEventPath = Path.of(currentEventText)
            val selectedSeriesEventIds = optionValues(args, "--series-event-id").distinct()
            val sourceSeriesFile = DesktopEventSeriesFiles.read(manifestPath)
            val sourceSortedEvents = sourceSeriesFile.sortedEvents()
            val sortedEvents = if (selectedSeriesEventIds.isEmpty()) {
                sourceSortedEvents
            } else {
                val selected = sourceSortedEvents.filter { it.seriesEventId in selectedSeriesEventIds }
                val missing = selectedSeriesEventIds.filterNot { requestedId ->
                    sourceSortedEvents.any { it.seriesEventId == requestedId }
                }
                require(missing.isEmpty()) {
                    "Series event id(s) not found: ${missing.joinToString()}."
                }
                selected
            }
            val verificationSeriesFile = sourceSeriesFile.copy(events = sortedEvents)
            val seriesFolder = requireNotNull(manifestPath.parent) {
                "Event Series manifest must have a parent folder."
            }
            require(sortedEvents.size <= maxEvents) {
                "Series has ${sortedEvents.size} events; refusing exhaustive search above --max-events $maxEvents."
            }
            val verificationEvents = sortedEvents.map { event ->
                val eventPath = seriesFolder.resolve(event.eventFilePath).normalize()
                require(DesktopEventSeriesFiles.exists(eventPath)) {
                    "Event File '${event.displayName}' is missing."
                }
                val projectFile = DesktopEventSeriesFiles.readEvent(eventPath)
                verifySeriesEventStartSignatures(
                    event = event,
                    path = eventPath,
                    projectFile = projectFile,
                    maxEventCompetitors = maxEventCompetitors,
                    maxEventOrders = maxEventOrders
                )
            }
            val combinationCount = verificationEvents.fold(BigInteger.ONE) { product, event ->
                product * BigInteger.valueOf(event.signatures.size.coerceAtLeast(1).toLong())
            }
            val currentScore = seriesVerificationScore(
                verificationEvents.map { currentSeriesThirdSignature(it.projectFile) }
            )
            if (combinationCount > BigInteger.valueOf(maxCombinations)) {
                // The verifier is meant for small, exact checks. Large real series should still
                // return the measured per-event search space so we can choose useful tighter bounds.
                out.println(
                    jsonObject(
                        "command" to "event-series-start-fairness-verify",
                        "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                        "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                        "eventCount" to verificationEvents.size,
                        "selectedSeriesEventIds" to selectedSeriesEventIds,
                        "exhaustiveSearchComplete" to false,
                        "verificationLimitReason" to "Series signature search would exceed --max-combinations $maxCombinations.",
                        "combinationCount" to combinationCount,
                        "maxCombinations" to maxCombinations,
                        "currentScore" to currentScore.value,
                        "currentUnevenHistoryCount" to currentScore.unevenHistoryCount,
                        "currentSpreadSum" to currentScore.spreadSum,
                        "events" to verificationEvents.map(::seriesVerificationEventSummary)
                    )
                )
                return 0
            }
            val exhaustive = exhaustiveSeriesStartFairnessSearch(
                events = verificationEvents,
                sampleLimit = sampleLimit.coerceAtLeast(0)
            )
            val optimizerResults = (1..optimizerSamples).map { sampleIndex ->
                val verificationStore = if (selectedSeriesEventIds.isEmpty()) {
                    DesktopEventSeriesFiles
                } else {
                    /*
                     * Subset verification is for proving optimizer reach on bounded real-world
                     * cases. The wrapper keeps Event File reads on disk but limits the manifest
                     * that the optimizer sees to the exact same selected events being exhausted.
                     */
                    object : EventSeriesStore by DesktopEventSeriesFiles {
                        override fun read(path: Path): EventSeriesFile =
                            if (path.toAbsolutePath().normalize() == manifestPath.toAbsolutePath().normalize()) {
                                verificationSeriesFile
                            } else {
                                DesktopEventSeriesFiles.read(path)
                            }
                    }
                }
                val result = DesktopEventSeriesActions.optimizeStartFairness(
                    store = verificationStore,
                    manifestPath = manifestPath,
                    currentEventPath = currentEventPath,
                    maxPasses = optimizerPasses,
                    candidatesPerEvent = optimizerCandidates,
                    seedSalt = "verify-$sampleIndex"
                )
                val optimizedProjectsByEventId = result.updatedEventFiles
                    .associate { it.seriesEventId to it.projectFile }
                val optimizedSignatures = verificationEvents.map { event ->
                    currentSeriesThirdSignature(optimizedProjectsByEventId[event.event.seriesEventId] ?: event.projectFile)
                }
                seriesVerificationScore(optimizedSignatures)
            }
            val bestOptimizerScore = optimizerResults.minOrNull()
            val optimizerFoundOptimalCount = optimizerResults.count { it == exhaustive.bestScore }
            out.println(
                jsonObject(
                    "command" to "event-series-start-fairness-verify",
                    "manifest" to manifestPath.toAbsolutePath().normalize().toString(),
                    "currentEvent" to currentEventPath.toAbsolutePath().normalize().toString(),
                    "eventCount" to verificationEvents.size,
                    "selectedSeriesEventIds" to selectedSeriesEventIds,
                    "exhaustiveSearchComplete" to true,
                    "combinationCount" to combinationCount,
                    "currentScore" to currentScore.value,
                    "currentUnevenHistoryCount" to currentScore.unevenHistoryCount,
                    "currentSpreadSum" to currentScore.spreadSum,
                    "bestPossibleScore" to exhaustive.bestScore.value,
                    "bestPossibleUnevenHistoryCount" to exhaustive.bestScore.unevenHistoryCount,
                    "bestPossibleSpreadSum" to exhaustive.bestScore.spreadSum,
                    "optimalCombinationCount" to exhaustive.optimalCombinationCount,
                    "currentIsOptimal" to (currentScore == exhaustive.bestScore),
                    "optimizerSampleCount" to optimizerSamples,
                    "optimizerFoundOptimalCount" to optimizerFoundOptimalCount,
                    "optimizerFoundOptimal" to (optimizerFoundOptimalCount > 0),
                    "bestOptimizerScore" to bestOptimizerScore?.value,
                    "bestOptimizerUnevenHistoryCount" to bestOptimizerScore?.unevenHistoryCount,
                    "bestOptimizerSpreadSum" to bestOptimizerScore?.spreadSum,
                    "events" to verificationEvents.map(::seriesVerificationEventSummary),
                    "sampleOptimalCombinationSignatures" to exhaustive.sampleOptimalCombinationSignatures
                )
            )
            0
        }.getOrElse { error ->
            err.println("Event Series start fairness verification failed: ${error.message ?: error::class.simpleName}")
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

    private fun optionValues(args: List<String>, name: String): List<String> =
        args.withIndex()
            .filter { (_, value) -> value == name }
            .mapNotNull { (index, _) -> args.getOrNull(index + 1) }

    private fun zipTextEntries(path: Path): Map<String, String> =
        ZipInputStream(Files.newInputStream(path).buffered()).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    }
                    zip.closeEntry()
                }
            }
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
          remove-category <event-path> <category-id-or-name> [--delete-competitors] [--write]
                                          Remove one category and report before/after counts; writes only with --write.
          event-series-list <manifest-path> [--current-event <event-path>]
                                          List series manifest events as JSON.
          event-series-add-event <manifest-path> <event-path>
                                          Add an Event File to a series manifest and write its backlink.
          event-series-validate <manifest-path> [--require-clean]
                                          Validate a series manifest and linked Event Files; fail when issues exist with --require-clean.
          event-series-export <manifest-path> <target-folder>
                                          Copy the manifest and only manifest-listed Event Files to a clean folder.
          event-series-package-fingerprint <zip-path>
                                          Print a stable semantic fingerprint for an Event Series package ZIP.
          event-series-match <manifest-path> <current-event-path>
                                          Print competitor matching diagnostics for the current series event.
          event-series-start-fairness <manifest-path> <current-event-path>
                                          Print start fairness input diagnostics for the current series event.
          event-series-optimize-start-fairness <manifest-path> <current-event-path> [--write] [--seed-salt <text>]
                                          Try to improve or preserve series start fairness with randomized candidates; writes changed Event Files only with --write.
          event-series-start-fairness-verify <manifest-path> <current-event-path> [--series-event-id <id>]... [--max-events <n>] [--max-event-competitors <n>] [--max-event-orders <n>] [--max-combinations <n>] [--optimizer-samples <n>]
                                          Exhaustively verify small series start-third combinations and compare optimizer reach.
          event-start-list-verify <event-path> [--max-competitors <n>] [--sample-limit <n>] [--generator-samples <n>]
                                          Exhaustively count event start orders that score 100/100.
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
