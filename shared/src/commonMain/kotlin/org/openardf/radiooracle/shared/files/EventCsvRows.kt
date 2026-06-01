package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.toDisplayLabel

/** A control punch rendered as a pair of SI code and already formatted time text. */
data class TimedPunchCsvField(
    val siCode: Int,
    val timeText: String
)

/** Shared CSV row formatter for event import/export formats used by Android and desktop. */
object EventCsvRows {
    /** Formats a category row in the legacy semicolon-delimited category export shape. */
    fun categoryRow(category: EventCategory): String {
        val followsRacePresets = if (category.differentProperties) 0 else 1
        return listOf(
            category.name,
            category.isMan.compareTo(false),
            category.maxAge ?: 0,
            category.lengthMeters,
            category.climbMeters,
            followsRacePresets,
            category.raceType?.name ?: "",
            category.timeLimitSeconds?.div(60) ?: "",
            category.raceBand?.toDisplayLabel() ?: ""
        ).joinToString(";")
    }

    /** Formats a competitor row in the existing simple competitor CSV export shape. */
    fun competitorRow(competitor: EventCompetitor, categoryName: String): String {
        return "${competitor.siNumber ?: ""};${competitor.firstName};${competitor.lastName};" +
                "$categoryName;${competitor.isMan.compareTo(false)};${competitor.birthYear};;" +
                "${competitor.club};;${competitor.startNumber};${competitor.index}"
    }

    /** Formats a start-list row, using caller-provided absolute start time text when available. */
    fun competitorStartRow(
        competitor: EventCompetitor,
        categoryName: String,
        startTimeText: String?
    ): String {
        return "${competitor.startNumber};${competitor.lastName};${competitor.firstName};" +
                "$categoryName;;${startTimeText ?: ""};${competitor.index};;" +
                "${competitor.club};${competitor.siNumber ?: ""}"
    }

    /** Formats one raw punch row for readout debugging/export. */
    fun punchRow(cardNumber: Int?, siCode: Int, timeText: String): String {
        return "${cardNumber ?: ""};$siCode;$timeText"
    }

    /** Formats one full readout row with header times followed by control code/time pairs. */
    fun readoutRow(
        siNumber: Int?,
        checkTimeText: String?,
        startTimeText: String?,
        finishTimeText: String?,
        controlPunches: List<TimedPunchCsvField>
    ): String {
        val punchFields = controlPunches.joinToString(";") { punch ->
            "${punch.siCode};${punch.timeText}"
        }

        val header = listOf(
            siNumber ?: "",
            checkTimeText ?: "",
            startTimeText ?: "",
            finishTimeText ?: "",
            controlPunches.size
        ).joinToString(";")

        return header + if (punchFields.isNotEmpty()) ";$punchFields" else ""
    }

    /** Formats one ranked result row in the same order as the desktop Results section. */
    fun resultRow(
        placeText: String,
        competitorName: String,
        statusLabel: String,
        pointsText: String,
        runTimeText: String
    ): String =
        "$placeText;$competitorName;$statusLabel;$pointsText;$runTimeText"
}
