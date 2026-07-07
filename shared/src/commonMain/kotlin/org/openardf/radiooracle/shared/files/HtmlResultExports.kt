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
import org.openardf.radiooracle.shared.event.EventAwardCategoryDetails
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

/** Shared printable HTML exports for desktop and non-Android result workflows. */
object HtmlResultExports {
    fun results(
        raceData: EventRaceData,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): String {
        val placedByCategory = raceData.competitorData
            .groupBy { it.resultCategoryId() }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }
        val awards = EventAwardDetails.from(raceData, awardDisplayMode)

        return buildString {
            append("<!doctype html>\n")
            append("<html><head><meta charset=\"utf-8\">")
            append("<title>")
            appendHtml("${raceData.race.name} results")
            append("</title>")
            append("<style>")
            append("body{font-family:Arial,sans-serif;margin:24px;color:#111}")
            append("h1{font-size:24px;margin:0 0 8px}h2{font-size:18px;margin:28px 0 8px}")
            append("table{border-collapse:collapse;width:100%;margin-bottom:20px}")
            append("th,td{border-bottom:1px solid #ddd;padding:6px 8px;text-align:left;vertical-align:top}")
            append("th{background:#f2f2f2}.num{text-align:right}.splits{font-size:12px;line-height:1.45}")
            append(".meta{margin-bottom:18px;color:#444}.notice{margin:12px 0 18px;padding:10px 12px;background:#fff4d6;border:1px solid #e4b64d;font-weight:bold}.generated{margin-top:24px;color:#666;font-size:12px}")
            append("</style></head><body>")
            append("<h1>")
            appendHtml(raceData.race.name)
            append("</h1>")
            append("<div class=\"meta\">Start: ")
            appendHtml(raceData.race.startDateTimeIso)
            append(" | Level: ")
            appendHtml(raceData.race.raceLevel.name)
            append("</div>")
            awards.publicationNotice?.let { notice ->
                append("<div class=\"notice\">")
                appendHtml(notice)
                append("</div>")
            }
            raceData.categories
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .forEach { categoryData ->
                    appendCategoryResults(
                        categoryData,
                        placedByCategory[categoryData.category.id] ?: emptyList(),
                        raceData,
                        protectedCourseInfoByCategoryId
                    )
                }
            appendAwards(awards)
            append("<div class=\"generated\">Generated with Radio-Oracle ")
            appendHtml(appVersion)
            append("</div>")
            append("</body></html>\n")
        }
    }

    private fun StringBuilder.appendCategoryResults(
        categoryData: EventCategoryData,
        competitors: List<EventCompetitorData>,
        raceData: EventRaceData,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ) {
        val resultCompetitors = competitors.filter { it.readoutData != null }
        if (resultCompetitors.isEmpty()) return
        val controlLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        val effectiveLength = protectedCourseInfoByCategoryId
            ?.get(categoryData.category.id)
            ?.effectiveLengthMeters()

        append("<h2>")
        appendHtml(categoryData.category.name)
        append("</h2>")
        if (effectiveLength != null) {
            append("<p class=\"meta\">Effective length: ")
            appendHtml("${effectiveLength / 1000.0} km")
            append("</p>")
        }
        append("<table><thead><tr>")
        listOf("Place", "Name", "Club", "Person ID", "Points", "Run time", "Splits").forEach { heading ->
            append("<th>")
            appendHtml(heading)
            append("</th>")
        }
        append("</tr></thead><tbody>")
        resultCompetitors.forEach { competitorData ->
            appendCompetitorResult(competitorData, controlLabelsByCode)
        }
        append("</tbody></table>")
    }

    private fun StringBuilder.appendCompetitorResult(
        competitorData: EventCompetitorData,
        controlLabelsByCode: Map<Int, String>
    ) {
        val competitor = competitorData.competitorCategory.competitor
        val readoutData = competitorData.readoutData ?: return
        val result = readoutData.result
        append("<tr><td class=\"num\">")
        appendHtml(result.placeText())
        append("</td><td>")
        appendHtml(competitor.fullName())
        append("</td><td>")
        appendHtml(competitor.club)
        append("</td><td>")
        appendHtml(competitor.index)
        append("</td><td class=\"num\">")
        appendHtml(result.points.toString())
        append("</td><td>")
        appendHtml(DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false))
        append("</td><td class=\"splits\">")
        appendHtml(readoutData.punches.toSplitText(controlLabelsByCode))
        append("</td></tr>")
    }

    private fun StringBuilder.appendAwards(awards: EventAwardDetails) {
        if (!awards.hasAwards) {
            return
        }
        append("<h2>Championship Awards</h2>")
        awards.categories.forEach { category ->
            append("<h3>")
            appendHtml(category.categoryName)
            append("</h3>")
            awards.awardScopes.forEach { scope ->
                appendAwardTable(scope.displayLabel, category.awardsForScope(scope))
            }
        }
    }

    private fun StringBuilder.appendAwardTable(
        heading: String,
        winners: List<EventAwardWinnerDetails>
    ) {
        if (winners.isEmpty()) {
            return
        }
        append("<h4>")
        appendHtml(heading)
        append("</h4>")
        append("<table><thead><tr>")
        listOf("Award", "Award place", "Overall place", "Name", "Club", "Person ID", "Points", "Run time").forEach { column ->
            append("<th>")
            appendHtml(column)
            append("</th>")
        }
        append("</tr></thead><tbody>")
        winners.forEach { winner ->
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

    private fun List<EventAliasPunch>.toSplitText(controlLabelsByCode: Map<Int, String>): String =
        filter { it.punch.punchType == SIRecordType.CONTROL }
            .joinToString(separator = " ") { aliasPunch ->
                val code = controlLabelsByCode[aliasPunch.punch.siCode] ?: aliasPunch.alias?.name ?: aliasPunch.punch.siCode.toString()
                val splitTime = DurationFormatter.secondsToFormattedString(aliasPunch.punch.splitSeconds, useMinutes = false)
                "$code - $splitTime"
            }

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
}
