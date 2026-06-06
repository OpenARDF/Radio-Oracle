package org.openardf.radiooracle.shared.printing

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared ESC/POS-formatted finish-ticket text, independent of printer transport. */
object FinishTicketRenderer {
    fun render(
        raceData: EventRaceData,
        resultId: String,
        charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE,
        useMinuteTimeFormat: Boolean = false,
        useAliases: Boolean = true,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): String {
        val readoutContext = raceData.findReadoutContext(resultId)
            ?: error("No readout found for result $resultId.")
        val result = readoutContext.readoutData.result
        val competitor = readoutContext.competitorCategory
        val competitorName = (
            competitor?.competitor?.fullName() ?: result.cardName ?: "?"
            ).truncate(charactersPerLine)
        val categoryName = competitor?.category?.name ?: "?"
        val siAndIndex = "SI: ${result.siNumber ?: "?"} ${competitor?.competitor?.index?.ifBlank { "?" } ?: "?"}"
        val controls = "${result.points} Controls"
        val protectedCourseInfo = competitor?.category?.id?.let { categoryId ->
            protectedCourseInfoByCategoryId?.get(categoryId)
        }
        val effectiveLengthText = protectedCourseInfo?.effectiveLengthMeters()?.let { effectiveLength ->
            "Effective length: ${effectiveLength / 1000.0} km"
        }
        val runTime = "Run time: " +
            DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinuteTimeFormat) +
            " ${result.resultStatus.toDisplayLabel()}"

        return buildString {
            append("[C]<b>${raceData.race.name}</b>\n")
            append("[L]\n")
            append("[L]$competitorName\n")
            append("[L]$siAndIndex\n")
            append("[L]$categoryName\n\n")
            append(
                readoutContext.readoutData.punches.formatPunches(
                    raceType = raceData.race.raceType,
                    controls = raceData.controls,
                    useMinuteTimeFormat = useMinuteTimeFormat,
                    useAliases = useAliases
                )
            )
            append("\n\n")
            append("[R]<b>$runTime</b>\n")
            effectiveLengthText?.let { append("[R]$it\n") }
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

    private fun List<EventAliasPunch>.formatPunches(
        raceType: RaceType,
        controls: List<EventControl>,
        useMinuteTimeFormat: Boolean,
        useAliases: Boolean
    ): String {
        val controlLabelsByCode = controls.associateBy(
            keySelector = { it.siCode },
            valueTransform = { it.publicLabel?.takeIf(String::isNotBlank) ?: it.label }
        )
        return joinToString("\n") { aliasPunch ->
            aliasPunch.format(raceType, controlLabelsByCode, useMinuteTimeFormat, useAliases)
        }
    }

    private fun EventAliasPunch.format(
        raceType: RaceType,
        controlLabelsByCode: Map<Int, String>,
        useMinuteTimeFormat: Boolean,
        useAliases: Boolean
    ): String =
        when (punch.punchType) {
            SIRecordType.START ->
                "[L]Start[R]${punch.siTimeSeconds.toTimeOfDay()}[R] "
            SIRecordType.FINISH ->
                "[L]Finish[R]${punch.siTimeSeconds.toTimeOfDay()}[R]${punch.splitSeconds.toDuration(useMinuteTimeFormat)}"
            SIRecordType.CONTROL ->
                "[L]${formatCode(raceType, controlLabelsByCode, useAliases)}[R]${punch.siTimeSeconds.toTimeOfDay()}[R]${punch.splitSeconds.toDuration(useMinuteTimeFormat)}"
            SIRecordType.CHECK -> ""
        }

    private fun EventAliasPunch.formatCode(
        raceType: RaceType,
        controlLabelsByCode: Map<Int, String>,
        useAliases: Boolean
    ): String {
        val displayCode = if (useAliases) {
            controlLabelsByCode[punch.siCode] ?: alias?.name ?: punch.siCode.toString()
        } else {
            punch.siCode.toString()
        }
        val code = if (raceType == RaceType.ORIENTEERING || useAliases) {
            "${punch.order} ($displayCode)"
        } else {
            punch.siCode.toString()
        }
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
