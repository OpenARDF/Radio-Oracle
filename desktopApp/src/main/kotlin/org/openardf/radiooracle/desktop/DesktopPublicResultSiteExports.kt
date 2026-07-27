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

import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventAwardCategoryDetails
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventAwardDetails
import org.openardf.radiooracle.shared.event.EventAwardWinnerDetails
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.supportsChampionshipAwards
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.files.FinalResultJsonExports
import org.openardf.radiooracle.shared.files.HtmlResultExports
import org.openardf.radiooracle.shared.files.IofXmlExports
import org.openardf.radiooracle.shared.files.LiveResultJsonExports
import org.openardf.radiooracle.shared.files.ResultSplitRows
import org.openardf.radiooracle.shared.time.DurationFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class DesktopPublicResultSiteExportPaths(
    val directory: Path,
    val eventDirectory: Path,
    val eventPath: String,
    val rootIndexHtml: Path,
    val indexHtml: Path,
    val publicResultsJson: Path,
    val finalResultsJson: Path,
    val liveResultsJson: Path,
    val iofResultListXml: Path,
    val printableResultsHtml: Path
)

data class DesktopPublicResultSeriesRace(
    val projectFile: EventProjectFile,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo> = emptyMap(),
    val awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
)

internal data class DesktopPublicResultCourseGraphic(
    val categoryId: String,
    val categoryName: String,
    val courseInfo: ProtectedCourseInfo
)

internal fun publicResultCoursesForGraphics(
    race: DesktopPublicResultSeriesRace
): List<DesktopPublicResultCourseGraphic> =
    EventResultDetails.from(race.projectFile.raceData)
        .mapNotNull { result ->
            val categoryId = result.categoryId ?: return@mapNotNull null
            val courseInfo = protectedCourseInfoForResultCategory(
                projectFile = race.projectFile,
                protectedCourseInfoByCategoryId = race.protectedCourseInfoByCategoryId,
                resultCategoryId = categoryId,
                resultCategoryName = result.categoryName
            ) ?: return@mapNotNull null
            DesktopPublicResultCourseGraphic(categoryId, result.categoryName, courseInfo)
        }
        .distinctBy { it.categoryId to it.categoryName }

/** Writes the static public-results site that can be uploaded to Cloudflare Pages. */
object DesktopPublicResultSiteExports {
    private data class SeriesRaceExport(
        val paths: DesktopPublicResultSiteExportPaths,
        val projectFile: EventProjectFile,
        val courseGraphicPaths: List<Path>
    )

    private data class PublicResultSplit(
        val control: String,
        val status: String,
        val legSeconds: Long,
        val legTime: String,
        val cumulativeTime: String,
        val legPlace: String = ""
    )

    fun export(
        directory: Path,
        projectFile: EventProjectFile,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD,
        generatedAt: Instant = Instant.now()
    ): DesktopPublicResultSiteExportPaths {
        val eventPath = eventPath(projectFile, generatedAt)
        val eventDirectory = directory.resolve(eventPath)
        val rootDataDirectory = directory.resolve("data")
        val assetsDirectory = eventDirectory.resolve("assets")
        val dataDirectory = eventDirectory.resolve("data")
        val downloadsDirectory = eventDirectory.resolve("downloads")
        listOf(directory, rootDataDirectory, eventDirectory, assetsDirectory, dataDirectory, downloadsDirectory)
            .forEach(Files::createDirectories)

        val hasResults = EventResultDetails.from(projectFile.raceData).isNotEmpty()

        val rootIndexPath = directory.resolve("index.html")
        val indexPath = eventDirectory.resolve("index.html")
        val publicResultsPath = dataDirectory.resolve("public-results.json")
        val finalResultsPath = downloadsDirectory.resolve("final-results.json")
        val liveResultsPath = downloadsDirectory.resolve("live-results.json")
        val iofResultsPath = downloadsDirectory.resolve("iof-result-list.xml")
        val printableResultsPath = downloadsDirectory.resolve("printable-results.html")

        val unofficialResults = projectFile.raceData.race.supportsChampionshipAwards()
        writeText(indexPath, indexHtml(projectFile.raceData.race.name, unofficialResults))
        writeText(assetsDirectory.resolve("site.css"), siteCss())
        writeText(assetsDirectory.resolve("site.js"), siteJs())
        writeText(directory.resolve("_headers"), headersText())
        writeText(publicResultsPath, publicResultsJson(projectFile, generatedAt, awardDisplayMode))
        if (hasResults) {
            val finalResultsJson = FinalResultJsonExports.results(
                projectFile.raceData,
                protectedCourseInfoByCategoryId,
                awardDisplayMode
            )
            val liveResultsJson = LiveResultJsonExports.results(projectFile.raceData)
            val iofResultListXml = IofXmlExports.resultList(projectFile.raceData)
            val printableResultsHtml = HtmlResultExports.results(
                raceData = projectFile.raceData,
                appVersion = appVersion,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                awardDisplayMode = awardDisplayMode
            )
            writeText(dataDirectory.resolve("final-results.json"), finalResultsJson)
            writeText(dataDirectory.resolve("live-results.json"), liveResultsJson)
            writeText(finalResultsPath, finalResultsJson)
            writeText(liveResultsPath, liveResultsJson)
            writeText(iofResultsPath, iofResultListXml)
            writeText(printableResultsPath, printableResultsHtml)
        } else {
            listOf(
                dataDirectory.resolve("final-results.json"),
                dataDirectory.resolve("live-results.json"),
                finalResultsPath,
                liveResultsPath,
                iofResultsPath,
                printableResultsPath
            ).forEach(Files::deleteIfExists)
        }
        val eventSummary = PublishedEventSummary(
            path = eventPath,
            name = projectFile.raceData.race.name,
            start = projectFile.raceData.race.startDateTimeIso,
            generatedAt = generatedAt.toString(),
            resultCount = EventResultDetails.from(projectFile.raceData).size,
            unofficialResults = unofficialResults
        )
        writeText(dataDirectory.resolve("event-summary.json"), eventSummaryJson(eventSummary))
        val events = mergedEventSummaries(rootDataDirectory.resolve("races.json"), eventSummary)
        writeText(rootDataDirectory.resolve("races.json"), eventsJson(events))
        writeText(rootIndexPath, rootIndexHtml(events))

        return DesktopPublicResultSiteExportPaths(
            directory = directory,
            eventDirectory = eventDirectory,
            eventPath = eventPath,
            rootIndexHtml = rootIndexPath,
            indexHtml = indexPath,
            publicResultsJson = publicResultsPath,
            finalResultsJson = finalResultsPath,
            liveResultsJson = liveResultsPath,
            iofResultListXml = iofResultsPath,
            printableResultsHtml = printableResultsPath
        )
    }

    fun exportSeries(
        directory: Path,
        seriesName: String,
        races: List<DesktopPublicResultSeriesRace>,
        appVersion: String = "Desktop",
        generatedAt: Instant = Instant.now()
    ): DesktopPublicResultSiteExportPaths {
        require(races.isNotEmpty()) {
            "The Race Series does not contain any races to publish."
        }
        val raceExports = races.map { race ->
            val paths = export(
                directory = directory,
                projectFile = race.projectFile,
                appVersion = appVersion,
                protectedCourseInfoByCategoryId = race.protectedCourseInfoByCategoryId,
                awardDisplayMode = race.awardDisplayMode,
                generatedAt = generatedAt
            )
            SeriesRaceExport(
                paths = paths,
                projectFile = race.projectFile,
                courseGraphicPaths = exportCourseGraphics(paths, race)
            )
        }
        val seriesPath = seriesPath(seriesName, races.first().projectFile, generatedAt)
        val seriesDirectory = directory.resolve(seriesPath)
        val assetsDirectory = seriesDirectory.resolve("assets")
        val dataDirectory = seriesDirectory.resolve("data")
        listOf(seriesDirectory, assetsDirectory, dataDirectory).forEach(Files::createDirectories)
        val indexPath = seriesDirectory.resolve("index.html")
        val publicResultsPath = dataDirectory.resolve("series-results.json")
        val unofficialResults = races.any { it.projectFile.raceData.race.supportsChampionshipAwards() }
        writeText(indexPath, seriesIndexHtml(seriesName, unofficialResults))
        writeText(assetsDirectory.resolve("site.css"), siteCss())
        writeText(assetsDirectory.resolve("series-site.js"), seriesSiteJs())
        writeText(publicResultsPath, seriesResultsJson(seriesName, raceExports, generatedAt))

        val summary = PublishedEventSummary(
            path = seriesPath,
            name = seriesName,
            start = races.minOf { it.projectFile.raceData.race.startDateTimeIso },
            generatedAt = generatedAt.toString(),
            resultCount = races.sumOf { EventResultDetails.from(it.projectFile.raceData).size },
            unofficialResults = unofficialResults
        )
        val rootDataDirectory = directory.resolve("data")
        Files.createDirectories(rootDataDirectory)
        val events = mergedEventSummaries(rootDataDirectory.resolve("races.json"), summary)
            .filterNot { event -> raceExports.any { it.paths.eventPath == event.path } }
        writeText(rootDataDirectory.resolve("races.json"), eventsJson(events))
        val rootIndexPath = directory.resolve("index.html")
        writeText(rootIndexPath, rootIndexHtml(events))

        val first = raceExports.first().paths
        return DesktopPublicResultSiteExportPaths(
            directory = directory,
            eventDirectory = seriesDirectory,
            eventPath = seriesPath,
            rootIndexHtml = rootIndexPath,
            indexHtml = indexPath,
            publicResultsJson = publicResultsPath,
            finalResultsJson = first.finalResultsJson,
            liveResultsJson = first.liveResultsJson,
            iofResultListXml = first.iofResultListXml,
            printableResultsHtml = first.printableResultsHtml
        )
    }

    private fun exportCourseGraphics(
        paths: DesktopPublicResultSiteExportPaths,
        race: DesktopPublicResultSeriesRace
    ): List<Path> {
        val graphicsDirectory = paths.eventDirectory.resolve("course-graphics")
        val resolvedCourses = publicResultCoursesForGraphics(race)
        val resolvedCategoryIds = resolvedCourses.mapTo(mutableSetOf()) { it.categoryId }
        EventResultDetails.from(race.projectFile.raceData)
            .mapNotNull { result -> result.categoryId?.let { it to result.categoryName } }
            .distinct()
            .filterNot { (categoryId, _) -> categoryId in resolvedCategoryIds }
            .forEach { (categoryId, categoryName) ->
                if (race.protectedCourseInfoByCategoryId.isNotEmpty()) {
                    DesktopDebugLog.warn(
                        "PublicResults",
                        "No unlocked course matched result category=$categoryName categoryId=$categoryId " +
                            "race=${race.projectFile.raceData.race.name}."
                    )
                }
            }
        val distinctCourses = resolvedCourses
            .groupBy { it.courseInfo }
            .map { (_, courses) ->
                courses.first().copy(
                    categoryName = courses.map { it.categoryName }.distinct().joinToString(", ")
                )
            }
        if (distinctCourses.isEmpty()) {
            return emptyList()
        }
        return distinctCourses.mapNotNull { course ->
            val (categoryId, categoryName, courseInfo) = course
            runCatching {
                val unavailableReason = DesktopCourseAnalyzer.analysisUnavailableReason(
                    race.projectFile,
                    categoryId,
                    courseInfo,
                    courseInfo.idealOrder
                )
                if (unavailableReason != null) {
                    DesktopDebugLog.warn(
                        "PublicResults",
                        "Skipped 2D course diagram race=${race.projectFile.raceData.race.name} " +
                            "category=$categoryName reason=$unavailableReason"
                    )
                    return@runCatching null
                }
                val summary = DesktopCourseAnalyzer.analyze(
                    projectFile = race.projectFile,
                    categoryId = categoryId,
                    protectedCourseInfo = courseInfo,
                    protectedIdealOrderText = courseInfo.idealOrder,
                    magneticDeclinationProvider = DesktopMagneticDeclination::result
                )
                val routeMap = summary.routeMaps.firstOrNull() ?: return@runCatching null
                val fileName = "course-${categoryId.safePathSegment()}.png"
                val path = graphicsDirectory.resolve(fileName)
                DesktopCourseGraphic.writeWebPng(path, routeMap.copy(title = "$categoryName course"))
                DesktopDebugLog.info(
                    "PublicResults",
                    "Generated 2D course diagram race=${race.projectFile.raceData.race.name} " +
                        "category=$categoryName path=$path"
                )
                path
            }.onFailure { error ->
                DesktopDebugLog.error(
                    "PublicResults",
                    "2D course diagram failed race=${race.projectFile.raceData.race.name} " +
                        "category=$categoryName: ${error.message ?: error::class.simpleName}"
                )
            }.getOrNull()
        }
    }

    private fun String.safePathSegment(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "course" }

    internal fun seriesPath(seriesName: String, firstRace: EventProjectFile, generatedAt: Instant): String {
        val date = firstRace.raceData.race.startDateTimeIso.take(10)
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?: generatedAt.toString().take(10)
        return "$date-${seriesName.safePathSegment()}-series"
    }

    internal fun eventPath(projectFile: EventProjectFile, generatedAt: Instant): String {
        val datePrefix = projectFile.raceData.race.startDateTimeIso
            .take(10)
            .takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ?: generatedAt.toString().take(10)
        val nameSlug = projectFile.raceData.race.name
            .lowercase()
            .map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    else -> '-'
                }
            }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "event" }
            .take(64)
            .trim('-')
        return "$datePrefix-$nameSlug"
    }

    private fun publicResultsJson(
        projectFile: EventProjectFile,
        generatedAt: Instant,
        awardDisplayMode: EventAwardDisplayMode
    ): String {
        val raceData = projectFile.raceData
        val results = EventResultDetails.from(raceData)
        val awards = EventAwardDetails.from(raceData, awardDisplayMode)
        val groupedResults = results.groupBy { it.categoryId.orEmpty() to it.categoryName }
        val splitsByResultId = publicResultSplitsByResultId(projectFile, results)
        return buildString {
            append("{\n")
            append("  \"event\": {\n")
            append("    \"name\": ")
            appendJsonString(raceData.race.name)
            append(",\n    \"start\": ")
            appendJsonString(raceData.race.startDateTimeIso)
            append(",\n    \"format\": ")
            appendJsonString(raceData.race.raceType.name)
            append(",\n    \"level\": ")
            appendJsonString(raceData.race.raceLevel.name)
            append("\n  },\n")
            append("  \"generatedAt\": ")
            appendJsonString(generatedAt.toString())
            append(",\n")
            append("  \"resultCount\": ${results.size},\n")
            append("  \"publicationNotice\": ")
            appendJsonString(awards.publicationNotice.takeIf { results.isNotEmpty() }.orEmpty())
            append(",\n")
            append("  \"awards\": ")
            appendAwardsJson(awards)
            append(",\n")
            append("  \"categories\": [\n")
            groupedResults.entries.forEachIndexed { categoryIndex, (category, categoryResults) ->
                if (categoryIndex > 0) append(",\n")
                append("    {\n")
                append("      \"id\": ")
                appendJsonString(category.first)
                append(",\n      \"name\": ")
                appendJsonString(category.second)
                append(",\n      \"results\": [\n")
                categoryResults.forEachIndexed { resultIndex, result ->
                    if (resultIndex > 0) append(",\n")
                    append("        {")
                    append("\"place\": ")
                    appendJsonString(result.placeText.ifBlank { result.statusLabel })
                    append(", \"competitor\": ")
                    appendJsonString(result.competitorName)
                    append(", \"status\": ")
                    appendJsonString(result.statusLabel)
                    append(", \"points\": ")
                    appendJsonString(result.pointsText)
                    append(", \"runtime\": ")
                    appendJsonString(result.runTimeText)
                    append(", \"punches\": ")
                    appendJsonString(result.punchCodesText)
                    append(", \"splits\": ")
                    appendSplitsJson(splitsByResultId[result.id].orEmpty())
                    append("}")
                }
                append("\n      ]\n")
                append("    }")
            }
            append("\n  ]\n")
            append("}\n")
        }
    }

    private fun publicResultSplitsByResultId(
        projectFile: EventProjectFile,
        results: List<EventResultDetails>
    ): Map<String, List<PublicResultSplit>> {
        val raceData = projectFile.raceData
        val controlLabelsByCode = raceData.controls.associate { control ->
            control.siCode to (control.publicLabel?.takeIf(String::isNotBlank) ?: control.label)
        }
        val readoutsByResultId = raceData.competitorData
            .mapNotNull { it.readoutData }
            .associateBy { it.result.id }

        return results
            .groupBy { it.categoryId.orEmpty() to it.categoryName }
            .values
            .flatMap { categoryResults ->
                val baseSplitsByResultId = categoryResults.associate { result ->
                    result.id to readoutsByResultId[result.id]
                        .publicSplits(
                            controlLabelsByCode = controlLabelsByCode,
                            useAliases = raceData.race.raceType != RaceType.ORIENTEERING
                        )
                }
                val legPlacesByIndex = legPlacesByIndex(baseSplitsByResultId)
                categoryResults.map { result ->
                    val splits = baseSplitsByResultId.getValue(result.id)
                    result.id to splits.mapIndexed { index, split ->
                        split.copy(legPlace = legPlacesByIndex[index]?.get(result.id).orEmpty())
                    }
                }
            }
            .toMap()
    }

    private fun EventReadoutData?.publicSplits(
        controlLabelsByCode: Map<Int, String>,
        useAliases: Boolean
    ): List<PublicResultSplit> {
        if (this == null) {
            return emptyList()
        }
        var cumulativeSeconds = 0L
        return ResultSplitRows.from(punches, controlLabelsByCode, useControlLabels = useAliases)
            .map { split ->
                cumulativeSeconds += split.splitSeconds
                PublicResultSplit(
                    control = split.label,
                    status = split.punchStatus.publicStatusLabel(),
                    legSeconds = split.splitSeconds,
                    legTime = DurationFormatter.secondsToFormattedString(split.splitSeconds, useMinutes = false),
                    cumulativeTime = DurationFormatter.secondsToFormattedString(cumulativeSeconds, useMinutes = false)
                )
            }
    }

    private fun PunchStatus.publicStatusLabel(): String =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "DP"
            PunchStatus.UNKNOWN -> "AP"
        }

    private fun legPlacesByIndex(splitsByResultId: Map<String, List<PublicResultSplit>>): Map<Int, Map<String, String>> {
        val maxSplitCount = splitsByResultId.values.maxOfOrNull { it.size } ?: 0
        return (0 until maxSplitCount).associateWith { index ->
            val rankedLegs = splitsByResultId
                .mapNotNull { (resultId, splits) ->
                    splits.getOrNull(index)?.let { split -> resultId to split.legSeconds }
                }
                .sortedBy { it.second }
            val places = mutableMapOf<String, String>()
            var previousSeconds: Long? = null
            var currentPlace = 0
            rankedLegs.forEachIndexed { position, (resultId, seconds) ->
                if (previousSeconds != seconds) {
                    currentPlace = position + 1
                    previousSeconds = seconds
                }
                places[resultId] = currentPlace.toString()
            }
            places
        }
    }

    private fun rootIndexHtml(races: List<PublishedEventSummary>): String {
        val eventLinks = if (races.isEmpty()) {
            """<p class="empty-state">No public result races have been generated yet.</p>"""
        } else {
            races.joinToString(separator = "\n") { event ->
                val publicationSummary = if (event.resultCount == 0) {
                    "${comingSoonResultsLabel(event.unofficialResults)} | Scheduled ${htmlText(event.start)}"
                } else {
                    "${event.resultCount} ${publicResultsLabel(event.unofficialResults).lowercase()} | " +
                        "Published ${htmlText(event.generatedAt)}"
                }
                """
                <a class="event-link" href="${htmlText(event.path)}/">
                  <span>
                    <strong>${htmlText(event.name)}</strong>
                    <small>$publicationSummary</small>
                  </span>
                </a>
                """.trimIndent()
            }
        }
        return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>OpenARDF Results</title>
          <style>
            :root{--bg:#f4f6f8;--panel:#fff;--text:#111827;--muted:#5f6b7a;--line:#d8dee8;--accent:#1769aa}
            *{box-sizing:border-box}
            body{margin:0;background:var(--bg);color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
            header{padding:36px min(6vw,72px) 28px;background:#fff;border-bottom:1px solid var(--line)}
            main{display:grid;gap:12px;width:min(900px,calc(100% - 32px));margin:24px auto 40px}
            h1{margin:0 0 8px;font-size:34px;line-height:1.1}.summary,.empty-state,small{color:var(--muted)}
            .event-link{display:flex;align-items:center;min-height:72px;padding:16px;border:1px solid var(--line);border-radius:8px;background:var(--panel);color:var(--text);text-decoration:none}
            .event-link:hover{border-color:var(--accent)}.event-link strong,.event-link small{display:block}.event-link small{margin-top:4px}
          </style>
        </head>
        <body>
          <header>
            <h1>OpenARDF Results</h1>
            <p class="summary">Published Radio-Oracle race results.</p>
          </header>
          <main>
            $eventLinks
          </main>
        </body>
        </html>
        """.trimIndent() + "\n"
    }

    private fun indexHtml(eventName: String, unofficialResults: Boolean): String {
        val resultsLabel = publicResultsLabel(unofficialResults)
        val comingSoonLabel = comingSoonResultsLabel(unofficialResults)
        val eyebrow = if (unofficialResults) {
            "Radio-Oracle unofficial results"
        } else {
            "Radio-Oracle public results"
        }
        val parentLink = if (unofficialResults) {
            "All published unofficial results"
        } else {
            "All published results"
        }
        val comingSoonDescription = if (unofficialResults) {
            "This page is ready for the event. Return after the race begins for unofficial results."
        } else {
            "This page is ready for the event. Return after the race begins for results."
        }
        val footer = if (unofficialResults) {
            "Unofficial results generated by Radio-Oracle."
        } else {
            "Generated by Radio-Oracle."
        }
        return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${htmlText(eventName)} $resultsLabel</title>
          <link rel="stylesheet" href="assets/site.css">
        </head>
        <body>
          <header class="page-header">
            <div>
              <p class="eyebrow">$eyebrow</p>
              <h1 id="event-name">${htmlText(eventName)}</h1>
              <p id="event-meta" class="summary">Loading results...</p>
              <a class="parent-link" href="../">$parentLink</a>
            </div>
            <nav id="download-links" class="download-links" aria-label="Downloads">
              <a href="downloads/printable-results.html">Printable HTML</a>
              <a href="downloads/final-results.json">Final JSON</a>
              <a href="downloads/iof-result-list.xml">IOF XML</a>
            </nav>
          </header>

          <main>
            <section class="overview" aria-label="Event summary">
              <div><span>$resultsLabel</span><strong id="result-count">0</strong></div>
              <div><span>Categories</span><strong id="category-count">0</strong></div>
              <div><span>Published</span><strong id="published-at">Pending</strong></div>
            </section>

            <section id="coming-soon-panel" class="panel coming-soon" hidden>
              <p class="eyebrow">$comingSoonLabel</p>
              <h2>$comingSoonLabel</h2>
              <p>$comingSoonDescription</p>
            </section>

            <section id="results-panel" class="panel">
              <div class="panel-heading">
                <div>
                  <h2>$resultsLabel</h2>
                  <p>Category results, points, runtime, status, and punch order.</p>
                </div>
                <input id="result-filter" type="search" placeholder="Filter results" aria-label="Filter results">
              </div>
              <div id="results"></div>
            </section>

            <section id="awards-panel" class="panel" hidden>
              <h2>Championship Awards</h2>
              <div id="awards"></div>
            </section>

            <section id="route-downloads-panel" class="panel">
              <h2>Route Downloads</h2>
              <p>Course route KML files and route graphics will appear here once they are generated from Course Analyzer exports.</p>
              <div id="route-downloads" class="empty-state">No route downloads in this export.</div>
            </section>
          </main>

          <footer>$footer</footer>
          <script src="assets/site.js"></script>
        </body>
        </html>
        """.trimIndent() + "\n"
    }

    private fun seriesIndexHtml(seriesName: String, unofficialResults: Boolean): String {
        val resultsLabel = publicResultsLabel(unofficialResults)
        val eyebrow = if (unofficialResults) {
            "Radio-Oracle Race Series unofficial results"
        } else {
            "Radio-Oracle Race Series results"
        }
        val parentLink = if (unofficialResults) {
            "All published unofficial results"
        } else {
            "All published results"
        }
        val footer = if (unofficialResults) {
            "Unofficial results generated by Radio-Oracle."
        } else {
            "Generated by Radio-Oracle."
        }
        return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${htmlText(seriesName)} $resultsLabel</title>
          <link rel="stylesheet" href="assets/site.css">
        </head>
        <body>
          <header class="page-header">
            <div>
              <p class="eyebrow">$eyebrow</p>
              <h1>${htmlText(seriesName)}</h1>
              <p id="series-meta" class="summary">Loading series results...</p>
              <a class="parent-link" href="../">$parentLink</a>
            </div>
          </header>
          <main id="series-races"></main>
          <footer>$footer</footer>
          <script src="assets/series-site.js"></script>
        </body>
        </html>
        """.trimIndent() + "\n"
    }

    private fun seriesResultsJson(
        seriesName: String,
        races: List<SeriesRaceExport>,
        generatedAt: Instant
    ): String =
        buildJsonObject {
            put("seriesName", seriesName)
            put("generatedAt", generatedAt.toString())
            put(
                "unofficialResults",
                races.any { it.projectFile.raceData.race.supportsChampionshipAwards() }
            )
            put(
                "races",
                buildJsonArray {
                    races.forEach { race ->
                        add(
                            buildJsonObject {
                                put("name", race.projectFile.raceData.race.name)
                                put("start", race.projectFile.raceData.race.startDateTimeIso)
                                put(
                                    "resultCount",
                                    EventResultDetails.from(race.projectFile.raceData).size
                                )
                                put(
                                    "unofficialResults",
                                    race.projectFile.raceData.race.supportsChampionshipAwards()
                                )
                                put("dataUrl", "../${race.paths.eventPath}/data/public-results.json")
                                put("downloadsUrl", "../${race.paths.eventPath}/downloads/")
                                put(
                                    "courseGraphics",
                                    buildJsonArray {
                                        race.courseGraphicPaths.forEach { graphicPath ->
                                            add(JsonPrimitive("../${race.paths.eventPath}/course-graphics/${graphicPath.fileName}"))
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }.toString() + "\n"

    private fun seriesSiteJs(): String =
        """
        function text(value){return String(value ?? "")}
        function escapeHtml(value){return text(value).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}
        function resultSearchText(result){
          const splits=(result.splits || []).map(split=>`${'$'}{split.control} ${'$'}{split.status} ${'$'}{split.legTime} ${'$'}{split.cumulativeTime}`).join(" ");
          return `${'$'}{result.place} ${'$'}{result.competitor} ${'$'}{result.status} ${'$'}{result.points} ${'$'}{result.runtime} ${'$'}{result.punches} ${'$'}{splits}`.toLowerCase()
        }
        function splitRowsHtml(result){
          const splits=result.splits || [];
          if(splits.length===0)return '<div class="empty-state">No split details available for this result.</div>';
          return `<p class="split-detail-title">Splits for ${'$'}{escapeHtml(result.competitor)}</p><table><thead><tr><th>Control</th><th>Status</th><th>Leg</th><th>Total</th><th class="number">Leg place</th></tr></thead><tbody>${'$'}{splits.map(split=>`<tr><td>${'$'}{escapeHtml(split.control)}</td><td>${'$'}{escapeHtml(split.status)}</td><td>${'$'}{escapeHtml(split.legTime)}</td><td>${'$'}{escapeHtml(split.cumulativeTime)}</td><td class="number">${'$'}{escapeHtml(split.legPlace)}</td></tr>`).join("")}</tbody></table>`
        }
        function resultRowsHtml(raceIndex,categoryIndex,resultIndex,result){
          const rowId=`split-${'$'}{raceIndex}-${'$'}{categoryIndex}-${'$'}{resultIndex}`;
          return `<tr class="result-row" tabindex="0" role="button" aria-expanded="false" aria-controls="${'$'}{rowId}" data-split-target="${'$'}{rowId}"><td class="number">${'$'}{escapeHtml(result.place)}</td><td>${'$'}{escapeHtml(result.competitor)}<small class="expand-hint">Tap for splits</small></td><td>${'$'}{escapeHtml(result.status)}</td><td class="number">${'$'}{escapeHtml(result.points)}</td><td>${'$'}{escapeHtml(result.runtime)}</td><td class="punches">${'$'}{escapeHtml(result.punches)}</td></tr><tr id="${'$'}{rowId}" class="split-row" hidden><td colspan="6"><div class="split-detail">${'$'}{splitRowsHtml(result)}</div></td></tr>`
        }
        function resultsHtml(data,raceIndex,query=""){
          const normalized=query.trim().toLowerCase();
          const sections=data.categories.map((category,categoryIndex)=>{
            const rows=category.results.filter(result=>resultSearchText(result).includes(normalized));
            if(rows.length===0)return "";
            return `<section class="category"><h3>${'$'}{escapeHtml(category.name)}</h3><table><thead><tr><th class="number">Place</th><th>Competitor</th><th>Status</th><th class="number">Points</th><th>Runtime</th><th>Punches</th></tr></thead><tbody>${'$'}{rows.map((result,resultIndex)=>resultRowsHtml(raceIndex,categoryIndex,resultIndex,result)).join("")}</tbody></table></section>`
          }).join("");
          return sections || '<div class="empty-state">No matching results.</div>'
        }
        function courseGraphicsHtml(race){
          const graphics=race.courseGraphics || [];
          if(graphics.length===0)return "";
          return `<section class="course-diagrams" aria-label="2D course diagrams"><h3>2D Course Diagram${'$'}{graphics.length===1 ? "" : "s"}</h3><div class="course-diagram-grid">${'$'}{graphics.map((url,index)=>`<figure><img src="${'$'}{escapeHtml(url)}" alt="2D course diagram ${'$'}{index+1} for ${'$'}{escapeHtml(race.name)}"></figure>`).join("")}</div></section>`
        }
        function renderRace(root,race,data,raceIndex){
          const resultsLabel=race.unofficialResults ? "Unofficial Results" : "Results";
          if(data.resultCount===0){
            const comingSoonLabel=`${'$'}{resultsLabel} Coming Soon`;
            root.innerHTML=`<section class="series-race coming-soon"><div class="race-heading"><div><p class="eyebrow">Race ${'$'}{raceIndex+1} | ${'$'}{comingSoonLabel}</p><h2>${'$'}{escapeHtml(data.event.name)}</h2><p class="summary">${'$'}{escapeHtml(data.event.format)} | ${'$'}{escapeHtml(data.event.level)} | Scheduled ${'$'}{escapeHtml(data.event.start)}</p></div></div><section class="panel"><h3>${'$'}{comingSoonLabel}</h3><p>Return after this race begins for ${'$'}{resultsLabel.toLowerCase()}.</p></section></section>`;
            return
          }
          root.innerHTML=`<section class="series-race"><div class="race-heading"><div><p class="eyebrow">Race ${'$'}{raceIndex+1}</p><h2>${'$'}{escapeHtml(data.event.name)}</h2><p class="summary">${'$'}{escapeHtml(data.event.format)} | ${'$'}{escapeHtml(data.event.level)} | Start ${'$'}{escapeHtml(data.event.start)} | ${'$'}{data.resultCount} ${'$'}{resultsLabel.toLowerCase()}</p></div><nav class="download-links" aria-label="${'$'}{escapeHtml(race.name)} downloads"><a href="${'$'}{escapeHtml(race.downloadsUrl)}printable-results.html">Printable HTML</a><a href="${'$'}{escapeHtml(race.downloadsUrl)}iof-result-list.xml">IOF XML</a></nav></div>${'$'}{courseGraphicsHtml(race)}<section class="panel"><div class="panel-heading"><div><h3>${'$'}{resultsLabel}</h3><p>Category results, points, runtime, status, and punch order.</p></div><input type="search" placeholder="Filter this race" aria-label="Filter ${'$'}{escapeHtml(race.name)} results"></div><div class="race-results">${'$'}{resultsHtml(data,raceIndex)}</div></section></section>`;
          const input=root.querySelector("input[type=search]");
          input.addEventListener("input",event=>{root.querySelector(".race-results").innerHTML=resultsHtml(data,raceIndex,event.target.value)})
        }
        function toggleResultRow(row){
          const splitRow=document.getElementById(row.dataset.splitTarget);
          if(!splitRow)return;
          const expanded=row.getAttribute("aria-expanded")==="true";
          row.setAttribute("aria-expanded",String(!expanded));
          splitRow.hidden=expanded
        }
        document.getElementById("series-races").addEventListener("click",event=>{
          const row=event.target.closest(".result-row");
          if(row)toggleResultRow(row)
        });
        document.getElementById("series-races").addEventListener("keydown",event=>{
          if(event.key!=="Enter" && event.key!==" ")return;
          const row=event.target.closest(".result-row");
          if(!row)return;
          event.preventDefault();toggleResultRow(row)
        });
        fetch("data/series-results.json",{cache:"no-store"}).then(response=>{
          if(!response.ok)throw new Error(`Unable to load series: ${'$'}{response.status}`);
          return response.json()
        }).then(async manifest=>{
          const racesWithResults=manifest.races.filter(race=>race.resultCount>0).length;
          const resultsLabel=manifest.unofficialResults ? "Unofficial Results" : "Results";
          document.getElementById("series-meta").textContent=racesWithResults===0
            ? `${'$'}{manifest.races.length} scheduled races | ${'$'}{resultsLabel} Coming Soon`
            : `${'$'}{racesWithResults} of ${'$'}{manifest.races.length} races with ${'$'}{resultsLabel.toLowerCase()} | Published ${'$'}{manifest.generatedAt}`;
          const root=document.getElementById("series-races");
          const raceData=await Promise.all(manifest.races.map(async race=>{
            const response=await fetch(race.dataUrl,{cache:"no-store"});
            if(!response.ok)throw new Error(`Unable to load ${'$'}{race.name}: ${'$'}{response.status}`);
            return response.json()
          }));
          manifest.races.forEach((race,index)=>{
            const section=document.createElement("div");root.appendChild(section);renderRace(section,race,raceData[index],index)
          })
        }).catch(error=>{document.getElementById("series-races").textContent=error.message})
        """.trimIndent() + "\n"

    private fun siteCss(): String =
        """
        :root{--bg:#f4f6f8;--panel:#fff;--text:#111827;--muted:#5f6b7a;--line:#d8dee8;--accent:#1769aa;--accent-strong:#0f4f84}
        *{box-sizing:border-box}
        body{margin:0;background:var(--bg);color:var(--text);font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
        .page-header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:36px min(6vw,72px) 28px;background:#fff;border-bottom:1px solid var(--line)}
        .eyebrow{margin:0 0 6px;color:var(--accent-strong);font-size:13px;font-weight:700;text-transform:uppercase}
        h1,h2,h3,p{margin-top:0}h1{margin-bottom:8px;font-size:34px;line-height:1.1}h2{margin-bottom:8px;font-size:20px}h3{margin:20px 0 8px;font-size:17px}
        .summary,.panel p,footer,.empty-state{color:var(--muted)}
        .notice{margin-top:12px;padding:10px 12px;border:1px solid #e4b64d;border-radius:6px;background:#fff4d6;color:#4b3600;font-weight:700}
        .parent-link{display:inline-flex;margin-top:8px;color:var(--accent-strong);font-weight:700;text-decoration:none}.parent-link:hover{text-decoration:underline}
        .download-links{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:10px}
        .download-links a{display:inline-flex;align-items:center;min-height:40px;padding:0 14px;border-radius:6px;background:var(--accent);color:#fff;font-weight:700;text-decoration:none;white-space:nowrap}
        main{display:grid;gap:18px;width:min(1180px,calc(100% - 32px));margin:24px auto 40px}
        .overview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1px;overflow:hidden;border:1px solid var(--line);border-radius:8px;background:var(--line)}
        .overview>div{min-height:86px;padding:18px;background:var(--panel)}.overview span{display:block;margin-bottom:6px;color:var(--muted);font-size:13px}.overview strong{font-size:20px}
        .panel{padding:20px;border:1px solid var(--line);border-radius:8px;background:var(--panel)}
        .coming-soon{border-color:#9bbbd3;background:#f4f9fd}.coming-soon h2,.coming-soon h3{color:var(--accent-strong)}
        .series-race{display:grid;gap:16px;padding:24px;border:1px solid var(--line);border-radius:10px;background:var(--panel)}.race-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:18px}.race-heading h2{font-size:26px}.course-diagrams{padding:18px;border:1px solid var(--line);border-radius:8px;background:#fbfcfe}.course-diagram-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,350px),700px));justify-content:start;gap:14px}.course-diagram-grid figure{margin:0}.course-diagram-grid img{display:block;width:100%;max-width:700px;height:auto;border:1px solid var(--line);border-radius:6px;background:#fff}
        .panel-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:16px}
        input[type=search]{width:min(280px,100%);height:40px;padding:0 12px;border:1px solid var(--line);border-radius:6px;font:inherit}
        table{width:100%;border-collapse:collapse}th,td{padding:9px 8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}th{color:var(--muted);font-size:13px;font-weight:700}.number{text-align:right}.punches{color:var(--muted);font-size:13px}.result-row{cursor:pointer}.result-row:hover{background:#f8fbff}.result-row[aria-expanded=true]{background:#eef6fc}.expand-hint{display:block;margin-top:2px;color:var(--muted);font-size:12px}.split-row[hidden]{display:none}.split-detail{padding:14px 16px;background:#fbfcfe;border-bottom:1px solid var(--line)}.split-detail table{margin-top:8px;background:#fff}.split-detail th,.split-detail td{font-size:13px}.split-detail-title{margin:0 0 6px;color:var(--muted);font-size:13px;font-weight:700}
        footer{width:min(1180px,calc(100% - 32px));margin:0 auto 32px;font-size:13px}
        @media (max-width:760px){.page-header,.panel-heading,.race-heading{display:block}.download-links{justify-content:flex-start;margin-top:18px}.overview{grid-template-columns:1fr}input[type=search]{margin-top:12px}.series-race{padding:14px}}
        """.trimIndent() + "\n"

    private fun siteJs(): String =
        """
        async function loadResults(){
          const response=await fetch("data/public-results.json",{cache:"no-store"});
          if(!response.ok){throw new Error(`Unable to load results: ${'$'}{response.status}`)}
          return response.json()
        }
        function text(value){return String(value ?? "")}
        function escapeHtml(value){return text(value).replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;")}
        function renderSummary(data){
          const isComingSoon=data.resultCount===0;
          document.getElementById("event-name").textContent=data.event.name;
          document.getElementById("event-meta").textContent=isComingSoon
            ? `${'$'}{data.event.format} | ${'$'}{data.event.level} | Scheduled ${'$'}{data.event.start}`
            : `${'$'}{data.event.format} | ${'$'}{data.event.level} | Start ${'$'}{data.event.start}`;
          if(!isComingSoon && data.publicationNotice){
            const notice=document.createElement("p");
            notice.className="notice";
            notice.textContent=data.publicationNotice;
            document.querySelector(".page-header>div").appendChild(notice)
          }
          document.getElementById("result-count").textContent=data.resultCount;
          document.getElementById("category-count").textContent=data.categories.length;
          document.getElementById("published-at").textContent=data.generatedAt;
          document.getElementById("coming-soon-panel").hidden=!isComingSoon;
          document.getElementById("results-panel").hidden=isComingSoon;
          document.getElementById("awards-panel").hidden=true;
          document.getElementById("route-downloads-panel").hidden=isComingSoon;
          document.getElementById("download-links").hidden=isComingSoon
        }
        function resultSearchText(result){
          const splitText=(result.splits || []).map(split=>`${'$'}{split.control} ${'$'}{split.status} ${'$'}{split.legTime} ${'$'}{split.cumulativeTime} ${'$'}{split.legPlace}`).join(" ");
          return `${'$'}{result.place} ${'$'}{result.competitor} ${'$'}{result.status} ${'$'}{result.points} ${'$'}{result.runtime} ${'$'}{result.punches} ${'$'}{splitText}`.toLowerCase()
        }
        function splitRowsHtml(result){
          const splits=result.splits || [];
          if(splits.length===0){return '<div class="empty-state">No split details available for this result.</div>'}
          return `<p class="split-detail-title">Splits for ${'$'}{escapeHtml(result.competitor)}</p><table><thead><tr><th>Control</th><th>Status</th><th>Leg</th><th>Total</th><th class="number">Leg place</th></tr></thead><tbody>${'$'}{splits.map(split=>`<tr><td>${'$'}{escapeHtml(split.control)}</td><td>${'$'}{escapeHtml(split.status)}</td><td>${'$'}{escapeHtml(split.legTime)}</td><td>${'$'}{escapeHtml(split.cumulativeTime)}</td><td class="number">${'$'}{escapeHtml(split.legPlace)}</td></tr>`).join("")}</tbody></table>`
        }
        function resultRowsHtml(categoryIndex,resultIndex,result){
          const rowId=`split-${'$'}{categoryIndex}-${'$'}{resultIndex}`;
          return `<tr class="result-row" tabindex="0" role="button" aria-expanded="false" aria-controls="${'$'}{rowId}" data-split-target="${'$'}{rowId}"><td class="number">${'$'}{escapeHtml(result.place)}</td><td>${'$'}{escapeHtml(result.competitor)}<small class="expand-hint">Tap for splits</small></td><td>${'$'}{escapeHtml(result.status)}</td><td class="number">${'$'}{escapeHtml(result.points)}</td><td>${'$'}{escapeHtml(result.runtime)}</td><td class="punches">${'$'}{escapeHtml(result.punches)}</td></tr><tr id="${'$'}{rowId}" class="split-row" hidden><td colspan="6"><div class="split-detail">${'$'}{splitRowsHtml(result)}</div></td></tr>`
        }
        function toggleResultRow(row,forceCollapsed=false){
          const targetId=row.dataset.splitTarget;
          const splitRow=document.getElementById(targetId);
          if(!splitRow)return;
          const expanded=row.getAttribute("aria-expanded")==="true";
          const nextExpanded=forceCollapsed ? false : !expanded;
          row.setAttribute("aria-expanded",String(nextExpanded));
          splitRow.hidden=!nextExpanded
        }
        function renderResults(data,query=""){
          const normalized=query.trim().toLowerCase();
          const root=document.getElementById("results");
          root.innerHTML="";
          let visible=0;
          data.categories.forEach((category,categoryIndex)=>{
            const rows=category.results.filter(result=>resultSearchText(result).includes(normalized));
            if(rows.length===0)return;
            visible+=rows.length;
            const section=document.createElement("section");
            section.className="category";
            section.innerHTML=`<h3>${'$'}{escapeHtml(category.name)}</h3><table><thead><tr><th class="number">Place</th><th>Competitor</th><th>Status</th><th class="number">Points</th><th>Runtime</th><th>Punches</th></tr></thead><tbody>${'$'}{rows.map((result,resultIndex)=>resultRowsHtml(categoryIndex,resultIndex,result)).join("")}</tbody></table>`;
            root.appendChild(section)
          });
          if(visible===0){root.innerHTML='<div class="empty-state">No matching results.</div>'}
        }
        function awardTableHtml(title,categories){
          const populated=(categories || []).filter(category=>(category.winners || []).length>0);
          if(populated.length===0)return "";
          return `<section class="category"><h3>${'$'}{escapeHtml(title)}</h3>${'$'}{populated.map(category=>`<h4>${'$'}{escapeHtml(category.name)}</h4><table><thead><tr><th>Award</th><th class="number">Award place</th><th class="number">Overall place</th><th>Competitor</th><th>Club</th><th>Person ID</th><th class="number">Points</th><th>Runtime</th></tr></thead><tbody>${'$'}{category.winners.map(winner=>`<tr><td>${'$'}{escapeHtml(winner.awardLevel)}</td><td class="number">${'$'}{escapeHtml(winner.awardPlace)}</td><td class="number">${'$'}{escapeHtml(winner.overallPlace)}</td><td>${'$'}{escapeHtml(winner.competitor)}</td><td>${'$'}{escapeHtml(winner.club)}</td><td>${'$'}{escapeHtml(winner.personId)}</td><td class="number">${'$'}{escapeHtml(winner.points)}</td><td>${'$'}{escapeHtml(winner.runtime)}</td></tr>`).join("")}</tbody></table>`).join("")}</section>`
        }
        function renderAwards(data){
          const awards=data.awards || {};
          const html=awardTableHtml("National Awards",awards.usaAwards)+awardTableHtml("Regional Awards",awards.region2Awards);
          if(!html)return;
          document.getElementById("awards-panel").hidden=false;
          document.getElementById("awards").innerHTML=html
        }
        document.getElementById("results").addEventListener("click",event=>{
          const splitRow=event.target.closest(".split-row");
          if(splitRow){
            const row=document.querySelector(`[data-split-target="${'$'}{splitRow.id}"]`);
            if(row)toggleResultRow(row,true);
            return
          }
          const row=event.target.closest(".result-row");
          if(row)toggleResultRow(row)
        });
        document.getElementById("results").addEventListener("keydown",event=>{
          if(event.key!=="Enter" && event.key!==" ")return;
          const row=event.target.closest(".result-row");
          if(!row)return;
          event.preventDefault();
          toggleResultRow(row)
        });
        loadResults().then(data=>{
          renderSummary(data);
          if(data.resultCount>0){
            renderResults(data);
            renderAwards(data);
            document.getElementById("result-filter").addEventListener("input",event=>renderResults(data,event.target.value))
          }
        }).catch(error=>{document.getElementById("results").textContent=error.message})
        """.trimIndent() + "\n"

    private fun headersText(): String =
        """
        /*
          X-Content-Type-Options: nosniff

        /data/*
          Cache-Control: no-store

        /*/data/*
          Cache-Control: no-store

        /downloads/*
          Cache-Control: public, max-age=300

        /*/downloads/*
          Cache-Control: public, max-age=300
        """.trimIndent() + "\n"

    private fun writeText(path: Path, text: String) {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun StringBuilder.appendSplitsJson(splits: List<PublicResultSplit>) {
        append("[")
        splits.forEachIndexed { index, split ->
            if (index > 0) append(", ")
            append("{\"control\": ")
            appendJsonString(split.control)
            append(", \"status\": ")
            appendJsonString(split.status)
            append(", \"legTime\": ")
            appendJsonString(split.legTime)
            append(", \"cumulativeTime\": ")
            appendJsonString(split.cumulativeTime)
            append(", \"legPlace\": ")
            appendJsonString(split.legPlace)
            append("}")
        }
        append("]")
    }

    private fun StringBuilder.appendAwardsJson(awards: EventAwardDetails) {
        append("{\"usaAwards\": ")
        appendAwardCategoriesJson(awards.categories, EventAwardCategoryDetails::usaAwards)
        append(", \"region2Awards\": ")
        appendAwardCategoriesJson(awards.categories, EventAwardCategoryDetails::region2Awards)
        append("}")
    }

    private fun StringBuilder.appendAwardCategoriesJson(
        categories: List<EventAwardCategoryDetails>,
        winners: (EventAwardCategoryDetails) -> List<EventAwardWinnerDetails>
    ) {
        append("[")
        categories.mapNotNull { category ->
            winners(category).takeIf { it.isNotEmpty() }?.let { category to it }
        }.forEachIndexed { categoryIndex, (category, categoryWinners) ->
            if (categoryIndex > 0) append(", ")
            append("{\"id\": ")
            appendJsonString(category.categoryId.orEmpty())
            append(", \"name\": ")
            appendJsonString(category.categoryName)
            append(", \"winners\": [")
            categoryWinners.forEachIndexed { winnerIndex, winner ->
                if (winnerIndex > 0) append(", ")
                append("{\"awardLevel\": ")
                appendJsonString(winner.awardLevel)
                winner.medal?.let { medal ->
                    append(", \"medal\": ")
                    appendJsonString(medal)
                }
                append(", \"awardPlace\": ")
                appendJsonString(winner.awardPlace.toString())
                append(", \"overallPlace\": ")
                appendJsonString(winner.overallPlace?.toString().orEmpty())
                append(", \"competitor\": ")
                appendJsonString(winner.competitorName)
                append(", \"club\": ")
                appendJsonString(winner.club)
                append(", \"personId\": ")
                appendJsonString(winner.personId)
                append(", \"points\": ")
                appendJsonString(winner.pointsText)
                append(", \"runtime\": ")
                appendJsonString(winner.runTimeText)
                append("}")
            }
            append("]}")
        }
        append("]")
    }

    private fun htmlText(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun publicResultsLabel(unofficialResults: Boolean): String =
        if (unofficialResults) "Unofficial Results" else "Results"

    private fun comingSoonResultsLabel(unofficialResults: Boolean): String =
        "${publicResultsLabel(unofficialResults)} Coming Soon"

    private fun eventSummaryJson(summary: PublishedEventSummary): String =
        buildJsonObject {
            put("path", summary.path)
            put("name", summary.name)
            put("start", summary.start)
            put("generatedAt", summary.generatedAt)
            put("resultCount", summary.resultCount)
            put("unofficialResults", summary.unofficialResults)
        }.toString() + "\n"

    private fun eventsJson(events: List<PublishedEventSummary>): String =
        buildJsonObject {
            put(
                "races",
                buildJsonArray {
                    events.forEach { event ->
                        add(
                            buildJsonObject {
                                put("path", event.path)
                                put("name", event.name)
                                put("start", event.start)
                                put("generatedAt", event.generatedAt)
                                put("resultCount", event.resultCount)
                                put("unofficialResults", event.unofficialResults)
                            }
                        )
                    }
                }
            )
        }.toString() + "\n"

    private fun mergedEventSummaries(eventsJsonPath: Path, current: PublishedEventSummary): List<PublishedEventSummary> {
        val existing = if (Files.exists(eventsJsonPath)) {
            runCatching {
                Json.parseToJsonElement(Files.readString(eventsJsonPath, StandardCharsets.UTF_8))
                    .jsonObject["races"]
                    ?.jsonArray
                    ?.mapNotNull { element -> element.jsonObject.toPublishedEventSummaryOrNull() }
                    ?: emptyList()
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        return (existing.filterNot { it.path == current.path } + current)
            .sortedWith(compareByDescending<PublishedEventSummary> { it.start }.thenBy { it.name })
    }

    private fun JsonObject.toPublishedEventSummaryOrNull(): PublishedEventSummary? {
        val path = stringValue("path") ?: return null
        val name = stringValue("name") ?: return null
        val start = stringValue("start") ?: ""
        val generatedAt = stringValue("generatedAt") ?: ""
        val resultCount = (this["resultCount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val unofficialResults =
            (this["unofficialResults"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        return PublishedEventSummary(path, name, start, generatedAt, resultCount, unofficialResults)
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.jsonPrimitive?.content

    private data class PublishedEventSummary(
        val path: String,
        val name: String,
        val start: String,
        val generatedAt: String,
        val resultCount: Int,
        val unofficialResults: Boolean
    )
}
