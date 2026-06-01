package org.openardf.radiooracle.shared.printing

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared ESC/POS-formatted finish-ticket text, independent of printer transport. */
object FinishTicketRenderer {
    fun render(
        raceData: EventRaceData,
        resultId: String,
        charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE,
        useMinuteTimeFormat: Boolean = false
    ): String {
        val readoutContext = raceData.findReadoutContext(resultId)
            ?: error("No readout found for result $resultId.")
        val result = readoutContext.readoutData.result
        val competitor = readoutContext.competitorCategory
        val competitorName = competitor?.competitor?.fullName()?.truncate(charactersPerLine) ?: "?"
        val categoryName = competitor?.category?.name ?: "?"
        val siAndIndex = "SI: ${result.siNumber ?: "?"} ${competitor?.competitor?.index?.ifBlank { "?" } ?: "?"}"
        val controls = "${result.points} Controls"
        val runTime = "Run time: " +
            DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinuteTimeFormat) +
            " ${result.resultStatus.toDisplayLabel()}"

        return buildString {
            append("[C]<b>${raceData.race.name}</b>\n")
            append("[L]\n")
            append("[L]$competitorName\n")
            append("[L]$siAndIndex\n")
            append("[L]$categoryName\n\n")
            append(readoutContext.readoutData.punches.formatPunches(useMinuteTimeFormat))
            append("\n\n")
            append("[R]<b>$runTime</b>\n")
            append("[R]$controls\n")
        }
    }

    private fun EventRaceData.findReadoutContext(resultId: String): ReadoutContext? {
        competitorData.forEach { competitorData ->
            val readoutData = competitorData.readoutData
            if (readoutData?.result?.id == resultId) {
                return ReadoutContext(readoutData, competitorData.competitorCategory)
            }
        }
        unmatchedReadoutData.forEach { readoutData ->
            if (readoutData.result.id == resultId) {
                return ReadoutContext(readoutData, null)
            }
        }
        return null
    }

    private fun List<EventAliasPunch>.formatPunches(useMinuteTimeFormat: Boolean): String =
        joinToString("\n") { aliasPunch -> aliasPunch.format(useMinuteTimeFormat) }

    private fun EventAliasPunch.format(useMinuteTimeFormat: Boolean): String =
        when (punch.punchType) {
            SIRecordType.START ->
                "[L]Start[R]${punch.siTimeSeconds.toTimeOfDay()}[R] "
            SIRecordType.FINISH ->
                "[L]Finish[R]${punch.siTimeSeconds.toTimeOfDay()}[R]${punch.splitSeconds.toDuration(useMinuteTimeFormat)}"
            SIRecordType.CONTROL ->
                "[L]${formatCode()}[R]${punch.siTimeSeconds.toTimeOfDay()}[R]${punch.splitSeconds.toDuration(useMinuteTimeFormat)}"
            SIRecordType.CHECK -> ""
        }

    private fun EventAliasPunch.formatCode(): String {
        val code = "${punch.order} (${alias?.name ?: punch.siCode})"
        return "$code${punch.punchStatus.toTicketSuffix()}"
    }

    private fun PunchStatus.toTicketSuffix(): String =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "+"
            PunchStatus.UNKNOWN -> "?"
        }

    private fun Long.toDuration(useMinuteTimeFormat: Boolean): String =
        DurationFormatter.secondsToFormattedString(this, useMinuteTimeFormat)

    private fun Long.toTimeOfDay(): String {
        val secondsInDay = 24 * 60 * 60
        val normalized = ((this % secondsInDay) + secondsInDay) % secondsInDay
        val hours = normalized / 3600
        val minutes = (normalized % 3600) / 60
        val seconds = normalized % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun String.truncate(maxLength: Int): String =
        if (maxLength > 0 && length > maxLength) take(maxLength) else this

    private data class ReadoutContext(
        val readoutData: EventReadoutData,
        val competitorCategory: EventCompetitorCategory?
    )

    private const val DEFAULT_CHARACTERS_PER_LINE = 32
}
