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
import org.openardf.radiooracle.shared.event.resultCategories
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared plain-text result exports matching the Android TXT result-export surface. */
object TextResultExports {
    private const val RULE = "--------------------------------------------------------------------------------------------------------------------------------------------------"

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
            appendLine(RULE)
            appendLine("Results")
            appendLine(RULE)
            appendLine("Race: ${raceData.race.name}")
            appendLine("Date/time: ${raceData.race.startDateTimeIso}")
            appendLine("Level: ${raceData.race.raceLevel.name}")
            awards.publicationNotice?.let { appendLine(it) }
            appendLine()
            appendLine("Place\tName\tPerson ID\tRun time\tPoints\tControls")
            appendLine(RULE)
            appendCategoryRows(raceData, placedByCategory, includeSplits = false, protectedCourseInfoByCategoryId)
            appendLine(RULE)
            appendLine("Splits")
            appendLine(RULE)
            appendCategoryRows(raceData, placedByCategory, includeSplits = true, protectedCourseInfoByCategoryId)
            appendAwards(awards)
            appendLine("===========================================================================")
            appendLine("Generated with Radio-Oracle $appVersion")
        }
    }

    private fun StringBuilder.appendCategoryRows(
        raceData: EventRaceData,
        placedByCategory: Map<String?, List<EventCompetitorData>>,
        includeSplits: Boolean,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ) {
        raceData.resultCategories()
            .forEach { categoryData ->
                appendCategoryRows(
                    categoryData,
                    placedByCategory[categoryData.category.id] ?: emptyList(),
                    raceData,
                    includeSplits,
                    protectedCourseInfoByCategoryId
                )
            }
    }

    private fun StringBuilder.appendCategoryRows(
        categoryData: EventCategoryData,
        competitors: List<EventCompetitorData>,
        raceData: EventRaceData,
        includeSplits: Boolean,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ) {
        val resultCompetitors = competitors.filter { it.readoutData != null }
        if (resultCompetitors.isEmpty()) return
        val controlLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        val protectedCourseInfo = protectedCourseInfoByCategoryId?.get(categoryData.category.id)

        append("Category ${categoryData.category.name}\tLimit: ${categoryData.category.effectiveTimeLimitSeconds(raceData.race) / 60}")
        if (protectedCourseInfoByCategoryId == null) {
            append("\tLength: ${categoryData.category.lengthMeters / 1000.0} km")
        } else {
            protectedCourseInfo?.effectiveLengthMeters()?.let { effectiveLength ->
                append("\tEffective length: ${effectiveLength / 1000.0} km")
            }
        }
        appendLine("\tControls: ${categoryData.category.controlPointsString}")
        appendLine(RULE)
        resultCompetitors.forEach { competitorData ->
            appendCompetitorRow(competitorData, includeSplits, controlLabelsByCode)
        }
        appendLine()
    }

    private fun StringBuilder.appendAwards(awards: EventAwardDetails) {
        if (!awards.hasAwards) {
            return
        }
        appendLine(RULE)
        appendLine("Championship Awards")
        appendLine(RULE)
        awards.categories.forEach { category ->
            appendLine("Category ${category.categoryName}")
            awards.awardScopes.forEach { scope ->
                appendAwardRows(scope.displayLabel, category.awardsForScope(scope))
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendAwardRows(
        heading: String,
        winners: List<EventAwardWinnerDetails>
    ) {
        if (winners.isEmpty()) {
            return
        }
        appendLine(heading)
        appendLine("Award\tAward place\tOverall place\tName\tPerson ID\tRun time\tPoints")
        winners.forEach { winner ->
            appendLine(
                listOf(
                    winner.awardLevel,
                    winner.awardPlace.toString(),
                    winner.overallPlace?.toString().orEmpty(),
                    winner.competitorName,
                    winner.personId,
                    winner.runTimeText,
                    winner.pointsText
                ).joinToString("\t")
            )
        }
    }

    private fun EventCompetitorData.resultCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

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
        ResultSplitRows.from(this)
            .joinToString(separator = " ") { split ->
                DurationFormatter.secondsToFormattedString(split.splitSeconds, useMinutes = false)
            }
}
