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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming
import org.openardf.radiooracle.shared.sportident.SportIdentRunTimingStatus
import org.openardf.radiooracle.shared.sportident.SportIdentTimingIssue
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared read-only readout row for matched and unmatched SI-card data. */
data class EventReadoutDetails(
    val id: String,
    val siNumberText: String,
    val competitorName: String,
    val matched: Boolean,
    val resultStatus: ResultStatus,
    val automaticStatus: Boolean,
    val statusLabel: String,
    val pointsText: String,
    val runTimeText: String,
    val punchCodesText: String,
    val hasWarning: Boolean,
    val issueExplanation: String?
) {
    companion object {
        /** Builds readout display rows for competitor-linked and unmatched readouts. */
        fun from(raceData: EventRaceData, useAliases: Boolean = true): List<EventReadoutDetails> {
            val controlLabelsByCode = raceData.controls.associateBy(
                keySelector = { it.siCode },
                valueTransform = { it.publicLabel?.takeIf(String::isNotBlank) ?: it.label }
            )
            val matched = raceData.competitorData.mapNotNull { competitorData ->
                val readoutData = competitorData.readoutData ?: return@mapNotNull null
                fromReadout(
                    readoutData = readoutData,
                    competitorName = competitorData.competitorCategory.competitor.fullName(),
                    matched = true,
                    raceType = raceData.race.raceType,
                    useAliases = useAliases,
                    controlLabelsByCode = controlLabelsByCode
                )
            }
            val unmatched = raceData.unmatchedReadoutData.map { readoutData ->
                fromReadout(
                    readoutData = readoutData,
                    competitorName = readoutData.result.cardName ?: "",
                    matched = false,
                    raceType = raceData.race.raceType,
                    useAliases = useAliases,
                    controlLabelsByCode = controlLabelsByCode
                )
            }
            return matched + unmatched
        }

        private fun fromReadout(
            readoutData: EventReadoutData,
            competitorName: String,
            matched: Boolean,
            raceType: RaceType,
            useAliases: Boolean,
            controlLabelsByCode: Map<Int, String>
        ): EventReadoutDetails {
            val result = readoutData.result
            val blocksScoreAndRunTime = readoutData.blocksScoreAndRunTimeDisplay()
            return EventReadoutDetails(
                id = result.id,
                siNumberText = result.siNumber?.toString() ?: "",
                competitorName = competitorName,
                matched = matched,
                resultStatus = result.resultStatus,
                automaticStatus = result.automaticStatus,
                statusLabel = result.resultStatus.toDisplayLabel(),
                pointsText = if (blocksScoreAndRunTime) "" else result.points.toString(),
                runTimeText = if (blocksScoreAndRunTime) {
                    readoutData.blockedRunTimeStatusCode()
                } else {
                    DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false)
                },
                punchCodesText = readoutData.punches
                    .filter { it.punch.punchType == SIRecordType.CONTROL }
                    .joinToString(" ") { aliasPunch ->
                        if (raceType != RaceType.ORIENTEERING && useAliases) {
                            controlLabelsByCode[aliasPunch.punch.siCode]
                                ?: aliasPunch.alias?.name
                                ?: aliasPunch.punch.siCode.toString()
                        } else {
                            aliasPunch.punch.siCode.toString()
                        }
                    },
                hasWarning = readoutData.hasReadoutWarning(),
                issueExplanation = readoutData.readoutIssueExplanation()
            )
        }
    }
}

internal fun EventReadoutData.blocksScoreAndRunTimeDisplay(): Boolean =
    result.resultStatus == ResultStatus.ERROR || (hasTimingOrPunches() && readoutTiming().blocksResult)

internal fun EventReadoutData.hasReadoutWarning(): Boolean =
    blocksScoreAndRunTimeDisplay() ||
        (hasTimingOrPunches() && readoutTiming().issues.isNotEmpty()) ||
        punches.any { it.punch.punchStatus == PunchStatus.INVALID }

internal fun EventReadoutData.blockedRunTimeStatusCode(): String =
    if (hasTimingOrPunches() && readoutTiming().blocksResult) {
        ResultStatus.ERROR.toResultStatusCode()
    } else {
        result.resultStatus.toResultStatusCode()
    }

fun EventReadoutData.readoutIssueExplanation(): String? {
    val explanations = buildList {
        if (hasTimingOrPunches()) {
            readoutTiming().issues.mapTo(this) { it.toDisplayExplanation() }
        }
        val invalidPunchCount = punches.count { it.punch.punchStatus == PunchStatus.INVALID }
        if (invalidPunchCount > 0) {
            add(
                if (invalidPunchCount == 1) {
                    "One control punch is marked invalid for this course."
                } else {
                    "$invalidPunchCount control punches are marked invalid for this course."
                }
            )
        }
        if (isEmpty() && result.resultStatus == ResultStatus.ERROR) {
            add("The result status is set to Error manually.")
        }
    }
    return explanations.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

private fun EventReadoutData.hasTimingOrPunches(): Boolean =
    result.startTimeSeconds != null ||
        result.finishTimeSeconds != null ||
        punches.isNotEmpty()

private fun EventReadoutData.readoutTiming() =
    SportIdentReadoutTiming.calculate(
        startSeconds = result.startTimeSeconds,
        finishSeconds = result.finishTimeSeconds,
        controlSeconds = punches
            .filter { it.punch.punchType == SIRecordType.CONTROL }
            .map { it.punch.siTimeSeconds }
    )

private fun SportIdentTimingIssue.toDisplayExplanation(): String =
    when (status) {
        SportIdentRunTimingStatus.VALID -> "The readout timing is valid."
        SportIdentRunTimingStatus.MISSING_START_OR_FINISH -> "The card is missing a start or finish time."
        SportIdentRunTimingStatus.FINISH_BEFORE_START -> "The finish time is before the start time."
        SportIdentRunTimingStatus.CONTROL_NOT_AFTER_START -> "Control punch ${displayControlIndex()} is not after the start time."
        SportIdentRunTimingStatus.CONTROL_NOT_AFTER_PREVIOUS_CONTROL ->
            "Control punch ${displayControlIndex()} is not after the previous control punch."
        SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL -> "The finish time is before control punch ${displayControlIndex()}."
    }

private fun SportIdentTimingIssue.displayControlIndex(): Int =
    (controlIndex ?: 0) + 1

/** English result-status labels matching the existing Android default resources. */
fun ResultStatus.toDisplayLabel(): String =
    when (this) {
        ResultStatus.OK -> "OK"
        ResultStatus.MISPUNCHED -> "Mispunched"
        ResultStatus.NO_RANKING -> "No ranking"
        ResultStatus.DISQUALIFIED -> "Disqualified"
        ResultStatus.DID_NOT_START -> "Did not start"
        ResultStatus.DID_NOT_FINISH -> "Did not finish"
        ResultStatus.OVER_TIME_LIMIT -> "Over time limit"
        ResultStatus.UNOFFICIAL -> "Unofficial"
        ResultStatus.ERROR -> "Error"
    }
