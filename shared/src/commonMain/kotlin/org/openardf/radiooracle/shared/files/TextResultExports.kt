package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared plain-text result exports matching the Android TXT result-export surface. */
object TextResultExports {
    private const val RULE = "--------------------------------------------------------------------------------------------------------------------------------------------------"

    fun results(raceData: EventRaceData, appVersion: String = "Desktop"): String {
        val placedByCategory = raceData.competitorData
            .groupBy { it.competitorCategory.category?.id ?: it.competitorCategory.competitor.categoryId }
            .mapValues { (_, categoryCompetitors) -> EventResultPlacement.sortByPlace(categoryCompetitors) }

        return buildString {
            appendLine(RULE)
            appendLine("Results")
            appendLine(RULE)
            appendLine("Race: ${raceData.race.name}")
            appendLine("Date/time: ${raceData.race.startDateTimeIso}")
            appendLine("Level: ${raceData.race.raceLevel.name}")
            appendLine()
            appendLine("Place\tName\tIndex\tRun time\tPoints\tControls")
            appendLine(RULE)
            appendCategoryRows(raceData, placedByCategory, includeSplits = false)
            appendLine(RULE)
            appendLine("Splits")
            appendLine(RULE)
            appendCategoryRows(raceData, placedByCategory, includeSplits = true)
            appendLine("===========================================================================")
            appendLine("Generated with Radio-Oracle $appVersion")
        }
    }

    private fun StringBuilder.appendCategoryRows(
        raceData: EventRaceData,
        placedByCategory: Map<String?, List<EventCompetitorData>>,
        includeSplits: Boolean
    ) {
        raceData.categories
            .sortedWith(compareBy({ it.category.order }, { it.category.name }))
            .forEach { categoryData ->
                appendCategoryRows(categoryData, placedByCategory[categoryData.category.id] ?: emptyList(), raceData, includeSplits)
            }
    }

    private fun StringBuilder.appendCategoryRows(
        categoryData: EventCategoryData,
        competitors: List<EventCompetitorData>,
        raceData: EventRaceData,
        includeSplits: Boolean
    ) {
        val resultCompetitors = competitors.filter { it.readoutData != null }
        if (resultCompetitors.isEmpty()) return
        val controlLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)

        appendLine("Category ${categoryData.category.name}\tLimit: ${categoryData.category.effectiveTimeLimitSeconds(raceData.race) / 60}\tLength: ${categoryData.category.lengthMeters / 1000.0} km\tControls: ${categoryData.category.controlPointsString}")
        appendLine(RULE)
        resultCompetitors.forEach { competitorData ->
            appendCompetitorRow(competitorData, includeSplits, controlLabelsByCode)
        }
        appendLine()
    }

    private fun StringBuilder.appendCompetitorRow(
        competitorData: EventCompetitorData,
        includeSplits: Boolean,
        controlLabelsByCode: Map<Int, String>
    ) {
        val competitor = competitorData.competitorCategory.competitor
        val readoutData = competitorData.readoutData ?: return
        val result = readoutData.result
        append(result.placeText())
        append('\t')
        append(competitor.fullName())
        append('\t')
        append(competitor.index)
        append('\t')
        append(DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false))
        append('\t')
        append(result.points)
        append('\t')
        append(readoutData.punches.toControlText(controlLabelsByCode))
        if (includeSplits) {
            append('\t')
            append(readoutData.punches.toSplitText())
        }
        appendLine()
    }

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

    private fun List<EventAliasPunch>.toSplitText(): String =
        filter { it.punch.punchType != SIRecordType.START }
            .joinToString(separator = " ") {
                DurationFormatter.secondsToFormattedString(it.punch.splitSeconds, useMinutes = false)
            }
}
