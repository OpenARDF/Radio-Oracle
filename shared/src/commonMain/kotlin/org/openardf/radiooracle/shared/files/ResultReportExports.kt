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

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventAwardDetails
import org.openardf.radiooracle.shared.event.EventAwardWinnerDetails
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.awardsForScope
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Printable results reports for users who exchange HTML, XML, and PDF posting files. */
object ResultReportExports {
    fun model(
        raceData: EventRaceData,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): ResultReport {
        val placedByCategory = raceData.competitorData
            .groupBy { it.resultCategoryId() }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }
        val controlLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        val awards = EventAwardDetails.from(raceData, awardDisplayMode)

        return ResultReport(
            raceName = raceData.race.name,
            startDateTimeIso = raceData.race.startDateTimeIso,
            raceLevel = raceData.race.raceLevel.name,
            publicationNotice = awards.publicationNotice,
            categories = raceData.categories
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .mapNotNull { categoryData ->
                    categoryReport(
                        categoryData = categoryData,
                        competitors = placedByCategory[categoryData.category.id].orEmpty(),
                        controlLabelsByCode = controlLabelsByCode,
                        protectedCourseInfo = protectedCourseInfoByCategoryId?.get(categoryData.category.id),
                        includePublicCourseStats = protectedCourseInfoByCategoryId == null
                    )
                },
            awardCategories = awardCategories(awards)
        )
    }

    fun html(
        raceData: EventRaceData,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): String =
        html(model(raceData, protectedCourseInfoByCategoryId, awardDisplayMode), appVersion)

    fun xml(
        raceData: EventRaceData,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): String =
        xml(model(raceData, protectedCourseInfoByCategoryId, awardDisplayMode), appVersion)

    fun html(report: ResultReport, appVersion: String = "Desktop"): String =
        buildString {
            append("<!doctype html>\n")
            append("<html><head><meta charset=\"utf-8\">")
            append("<title>")
            appendHtml("${report.raceName} results report")
            append("</title>")
            append("<style>")
            append("body{font-family:Arial,sans-serif;margin:24px;color:#111}")
            append("h1{font-size:24px;margin:0 0 8px}h2{font-size:18px;margin:28px 0 8px}h3{font-size:15px;margin:18px 0 6px}")
            append("table{border-collapse:collapse;width:100%;margin-bottom:18px}")
            append("th,td{border:1px solid #cfcfcf;padding:5px 7px;text-align:left;vertical-align:top}")
            append("th{background:#e9e9e9}.num{text-align:right}.splits{font-size:12px;line-height:1.45}")
            append(".meta{margin-bottom:18px;color:#444}.notice{margin:12px 0 18px;padding:10px 12px;background:#fff4d6;border:1px solid #e4b64d;font-weight:bold}.generated{margin-top:24px;color:#666;font-size:12px}")
            append("</style></head><body>")
            append("<h1>")
            appendHtml(report.raceName)
            append("</h1>")
            append("<div class=\"meta\">Start: ")
            appendHtml(report.startDateTimeIso)
            append(" | Level: ")
            appendHtml(report.raceLevel)
            append("</div>")
            report.publicationNotice?.let { notice ->
                append("<div class=\"notice\">")
                appendHtml(notice)
                append("</div>")
            }
            report.categories.forEach { category ->
                appendCategoryHtml(category)
            }
            appendAwardHtml(report.awardCategories)
            append("<div class=\"generated\">Generated with Radio-Oracle ")
            appendHtml(appVersion)
            append("</div>")
            append("</body></html>\n")
        }

    fun xml(report: ResultReport, appVersion: String = "Desktop"): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("""<ResultsReport format="radio-oracle-results-report-v1" creator="Radio-Oracle ${appVersion.xmlEscaped()}">""")
            append('\n')
            appendTextElement("RaceName", report.raceName, "  ")
            appendTextElement("StartDateTime", report.startDateTimeIso, "  ")
            appendTextElement("RaceLevel", report.raceLevel, "  ")
            report.publicationNotice?.let { appendTextElement("PublicationNotice", it, "  ") }
            append("  <Categories>\n")
            report.categories.forEach { category ->
                append("    <Category id=\"${category.id.xmlEscaped()}\" name=\"${category.name.xmlEscaped()}\">\n")
                category.timeLimitMinutes?.let { appendTextElement("TimeLimitMinutes", it.toString(), "      ") }
                category.lengthKmText?.let { appendTextElement("Length", it, "      ") }
                category.effectiveLengthKmText?.let { appendTextElement("EffectiveLength", it, "      ") }
                category.climbMeters?.let { appendTextElement("Climb", it.toString(), "      ") }
                appendTextElement("Controls", category.controlsText, "      ")
                append("      <Results>\n")
                category.results.forEach { result ->
                    append("        <Result>\n")
                    appendTextElement("Place", result.placeText, "          ")
                    appendTextElement("Name", result.name, "          ")
                    appendTextElement("Club", result.club, "          ")
                    appendTextElement("PersonId", result.personId, "          ")
                    appendTextElement("BibNumber", result.bibNumber, "          ")
                    appendTextElement("SiNumber", result.siNumber, "          ")
                    appendTextElement("Status", result.statusText, "          ")
                    appendTextElement("Points", result.pointsText, "          ")
                    appendTextElement("RunTime", result.runTimeText, "          ")
                    appendTextElement("Controls", result.controlsText, "          ")
                    appendTextElement("Splits", result.splitsText, "          ")
                    append("        </Result>\n")
                }
                append("      </Results>\n")
                append("    </Category>\n")
            }
            append("  </Categories>\n")
            appendAwardsXml(report.awardCategories)
            append("</ResultsReport>\n")
        }

    private fun categoryReport(
        categoryData: EventCategoryData,
        competitors: List<EventCompetitorData>,
        controlLabelsByCode: Map<Int, String>,
        protectedCourseInfo: ProtectedCourseInfo?,
        includePublicCourseStats: Boolean
    ): ResultReportCategory? {
        val resultCompetitors = competitors.filter { it.readoutData != null }
        if (resultCompetitors.isEmpty()) return null
        val category = categoryData.category
        val effectiveLengthMeters = protectedCourseInfo?.effectiveLengthMeters()
        return ResultReportCategory(
            id = category.id,
            name = category.name,
            timeLimitMinutes = category.timeLimitSeconds?.div(60),
            lengthKmText = category.lengthMeters.takeIf { includePublicCourseStats && it > 0 }?.let(::kilometersText),
            effectiveLengthKmText = effectiveLengthMeters?.let(::kilometersText),
            climbMeters = if (includePublicCourseStats) category.climbMeters.takeIf { it > 0 } else protectedCourseInfo?.climbMeters,
            controlsText = category.controlPointsString,
            results = resultCompetitors.map { competitorData ->
                competitorReport(competitorData, controlLabelsByCode)
            }
        )
    }

    private fun competitorReport(
        competitorData: EventCompetitorData,
        controlLabelsByCode: Map<Int, String>
    ): ResultReportRow {
        val competitor = competitorData.competitorCategory.competitor
        val readoutData = requireNotNull(competitorData.readoutData)
        val result = readoutData.result
        return ResultReportRow(
            placeText = result.placeText(),
            name = competitor.fullName(),
            club = competitor.club,
            personId = competitor.index,
            bibNumber = competitor.bibNumber,
            siNumber = (result.siNumber?.takeIf { it > 0 } ?: competitor.siNumber)?.toString().orEmpty(),
            statusText = result.resultStatus.toShortLabel(),
            pointsText = result.points.toString(),
            runTimeText = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false),
            controlsText = readoutData.punches.toControlText(controlLabelsByCode),
            splitsText = readoutData.punches.toResultSplitText(controlLabelsByCode)
        )
    }

    private fun awardCategories(awards: EventAwardDetails): List<ResultReportAwardCategory> =
        if (!awards.hasAwards) {
            emptyList()
        } else {
            awards.categories.mapNotNull { category ->
                val groups = awards.awardScopes.mapNotNull { scope ->
                    val winners = category.awardsForScope(scope)
                    if (winners.isEmpty()) {
                        null
                    } else {
                        ResultReportAwardGroup(scope.displayLabel, winners)
                    }
                }
                groups.takeIf { it.isNotEmpty() }?.let {
                    ResultReportAwardCategory(category.categoryName, groups)
                }
            }
        }

    private fun StringBuilder.appendCategoryHtml(category: ResultReportCategory) {
        append("<h2>")
        appendHtml(category.name)
        append("</h2>")
        append("<div class=\"meta\">")
        appendHtml(category.metaText())
        append("</div>")
        append("<table><thead><tr>")
        listOf("Place", "Name", "Club", "Person ID", "Bib #", "SI #", "Status", "Points", "Run time", "Controls", "Splits").forEach { heading ->
            append("<th>")
            appendHtml(heading)
            append("</th>")
        }
        append("</tr></thead><tbody>")
        category.results.forEach { result ->
            append("<tr><td class=\"num\">")
            appendHtml(result.placeText)
            append("</td><td>")
            appendHtml(result.name)
            append("</td><td>")
            appendHtml(result.club)
            append("</td><td>")
            appendHtml(result.personId)
            append("</td><td>")
            appendHtml(result.bibNumber)
            append("</td><td>")
            appendHtml(result.siNumber)
            append("</td><td>")
            appendHtml(result.statusText)
            append("</td><td class=\"num\">")
            appendHtml(result.pointsText)
            append("</td><td>")
            appendHtml(result.runTimeText)
            append("</td><td>")
            appendHtml(result.controlsText)
            append("</td><td class=\"splits\">")
            appendHtml(result.splitsText)
            append("</td></tr>")
        }
        append("</tbody></table>")
    }

    private fun StringBuilder.appendAwardHtml(awardCategories: List<ResultReportAwardCategory>) {
        if (awardCategories.isEmpty()) return
        append("<h2>Championship Awards</h2>")
        awardCategories.forEach { category ->
            append("<h3>")
            appendHtml(category.categoryName)
            append("</h3>")
            category.groups.forEach { group ->
                append("<h3>")
                appendHtml(group.label)
                append("</h3>")
                append("<table><thead><tr>")
                listOf("Award", "Award place", "Overall place", "Name", "Club", "Person ID", "Points", "Run time").forEach { heading ->
                    append("<th>")
                    appendHtml(heading)
                    append("</th>")
                }
                append("</tr></thead><tbody>")
                group.winners.forEach { winner ->
                    append("<tr><td>")
                    appendHtml(winner.awardLevel)
                    append("</td><td class=\"num\">")
                    appendHtml(winner.awardPlace.toString())
                    append("</td><td class=\"num\">")
                    appendHtml(winner.overallPlace?.toString().orEmpty())
                    append("</td><td>")
                    appendHtml(winner.competitorName)
                    append("</td><td>")
                    appendHtml(winner.club)
                    append("</td><td>")
                    appendHtml(winner.personId)
                    append("</td><td class=\"num\">")
                    appendHtml(winner.pointsText)
                    append("</td><td>")
                    appendHtml(winner.runTimeText)
                    append("</td></tr>")
                }
                append("</tbody></table>")
            }
        }
    }

    private fun StringBuilder.appendAwardsXml(awardCategories: List<ResultReportAwardCategory>) {
        if (awardCategories.isEmpty()) return
        append("  <ChampionshipAwards>\n")
        awardCategories.forEach { category ->
            append("    <AwardCategory name=\"${category.categoryName.xmlEscaped()}\">\n")
            category.groups.forEach { group ->
                append("      <AwardGroup label=\"${group.label.xmlEscaped()}\">\n")
                group.winners.forEach { winner ->
                    append("        <Award>\n")
                    appendTextElement("Level", winner.awardLevel, "          ")
                    appendTextElement("AwardPlace", winner.awardPlace.toString(), "          ")
                    appendTextElement("OverallPlace", winner.overallPlace?.toString().orEmpty(), "          ")
                    appendTextElement("Name", winner.competitorName, "          ")
                    appendTextElement("Club", winner.club, "          ")
                    appendTextElement("PersonId", winner.personId, "          ")
                    appendTextElement("Points", winner.pointsText, "          ")
                    appendTextElement("RunTime", winner.runTimeText, "          ")
                    append("        </Award>\n")
                }
                append("      </AwardGroup>\n")
            }
            append("    </AwardCategory>\n")
        }
        append("  </ChampionshipAwards>\n")
    }

    private fun StringBuilder.appendTextElement(name: String, value: String, indent: String) {
        append(indent)
        append('<')
        append(name)
        append('>')
        append(value.xmlEscaped())
        append("</")
        append(name)
        append(">\n")
    }

    private fun ResultReportCategory.metaText(): String =
        listOfNotNull(
            timeLimitMinutes?.let { "Limit: $it min" },
            lengthKmText?.let { "Length: $it" },
            effectiveLengthKmText?.let { "Effective length: $it" },
            climbMeters?.let { "Climb: $it m" },
            controlsText.takeIf { it.isNotBlank() }?.let { "Controls: $it" }
        ).joinToString(" | ")

    private fun EventCompetitorData.resultCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

    private fun org.openardf.radiooracle.shared.event.EventResult.placeText(): String =
        if (resultStatus == ResultStatus.OK && place > 0) {
            "$place."
        } else {
            resultStatus.toShortLabel()
        }

    private fun ResultStatus.toShortLabel(): String =
        when (this) {
            ResultStatus.OK -> "OK"
            ResultStatus.MISPUNCHED -> "MP"
            ResultStatus.NO_RANKING -> "NR"
            ResultStatus.DISQUALIFIED -> "DSQ"
            ResultStatus.DID_NOT_START -> "DNS"
            ResultStatus.DID_NOT_FINISH -> "DNF"
            ResultStatus.OVER_TIME_LIMIT -> "OVT"
            ResultStatus.UNOFFICIAL -> "UNF"
            ResultStatus.ERROR -> "ERR"
        }

    private fun List<EventAliasPunch>.toControlText(controlLabelsByCode: Map<Int, String>): String =
        filter { it.punch.punchType == SIRecordType.CONTROL }
            .joinToString(separator = " ") { controlLabelsByCode[it.punch.siCode] ?: it.alias?.name ?: it.punch.siCode.toString() }

    private fun List<EventAliasPunch>.toResultSplitText(controlLabelsByCode: Map<Int, String>): String =
        ResultSplitRows.from(this, controlLabelsByCode)
            .joinToString(separator = " ") { split ->
                val splitTime = DurationFormatter.secondsToFormattedString(split.splitSeconds, useMinutes = false)
                "${split.label} - $splitTime"
            }

    private fun kilometersText(meters: Int): String =
        "${meters / 1000.0} km"

    private fun StringBuilder.appendHtml(value: String) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    private fun String.xmlEscaped(): String =
        buildString {
            this@xmlEscaped.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }
}

data class ResultReport(
    val raceName: String,
    val startDateTimeIso: String,
    val raceLevel: String,
    val publicationNotice: String?,
    val categories: List<ResultReportCategory>,
    val awardCategories: List<ResultReportAwardCategory>
)

data class ResultReportCategory(
    val id: String,
    val name: String,
    val timeLimitMinutes: Long?,
    val lengthKmText: String?,
    val effectiveLengthKmText: String?,
    val climbMeters: Int?,
    val controlsText: String,
    val results: List<ResultReportRow>
)

data class ResultReportRow(
    val placeText: String,
    val name: String,
    val club: String,
    val personId: String,
    val bibNumber: String,
    val siNumber: String,
    val statusText: String,
    val pointsText: String,
    val runTimeText: String,
    val controlsText: String,
    val splitsText: String
)

data class ResultReportAwardCategory(
    val categoryName: String,
    val groups: List<ResultReportAwardGroup>
)

data class ResultReportAwardGroup(
    val label: String,
    val winners: List<EventAwardWinnerDetails>
)
