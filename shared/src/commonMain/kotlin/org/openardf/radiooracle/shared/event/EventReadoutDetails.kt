package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
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
    val punchCodesText: String
) {
    companion object {
        /** Builds readout display rows for competitor-linked and unmatched readouts. */
        fun from(raceData: EventRaceData): List<EventReadoutDetails> {
            val matched = raceData.competitorData.mapNotNull { competitorData ->
                val readoutData = competitorData.readoutData ?: return@mapNotNull null
                fromReadout(
                    readoutData = readoutData,
                    competitorName = competitorData.competitorCategory.competitor.fullName(),
                    matched = true
                )
            }
            val unmatched = raceData.unmatchedReadoutData.map { readoutData ->
                fromReadout(readoutData = readoutData, competitorName = "", matched = false)
            }
            return matched + unmatched
        }

        private fun fromReadout(
            readoutData: EventReadoutData,
            competitorName: String,
            matched: Boolean
        ): EventReadoutDetails {
            val result = readoutData.result
            return EventReadoutDetails(
                id = result.id,
                siNumberText = result.siNumber?.toString() ?: "",
                competitorName = competitorName,
                matched = matched,
                resultStatus = result.resultStatus,
                automaticStatus = result.automaticStatus,
                statusLabel = result.resultStatus.toDisplayLabel(),
                pointsText = result.points.toString(),
                runTimeText = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false),
                punchCodesText = readoutData.punches
                    .map { it.punch }
                    .filter { it.punchType == SIRecordType.CONTROL }
                    .joinToString(" ") { it.siCode.toString() }
            )
        }
    }
}

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
