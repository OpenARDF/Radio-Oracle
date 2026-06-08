package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared printable HTML exports for desktop and non-Android result workflows. */
object HtmlResultExports {
    fun results(
        raceData: EventRaceData,
        appVersion: String = "Desktop",
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): String {
        val placedByCategory = raceData.competitorData
            .groupBy { it.resultCategoryId() }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }

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
            append(".meta{margin-bottom:18px;color:#444}.generated{margin-top:24px;color:#666;font-size:12px}")
            append("</style></head><body>")
            append("<h1>")
            appendHtml(raceData.race.name)
            append("</h1>")
            append("<div class=\"meta\">Start: ")
            appendHtml(raceData.race.startDateTimeIso)
            append(" | Level: ")
            appendHtml(raceData.race.raceLevel.name)
            append("</div>")
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
        listOf("Place", "Name", "Club", "Index", "Points", "Run time", "Splits").forEach { heading ->
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
