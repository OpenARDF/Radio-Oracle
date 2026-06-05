package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.toDisplayLabel
import org.openardf.radiooracle.shared.time.DurationFormatter

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
            category.maxAge ?: EventCsvFormat.Category.OPEN_MAX_AGE,
            category.lengthMeters,
            category.climbMeters,
            followsRacePresets,
            category.raceType?.name ?: "",
            category.timeLimitSeconds?.div(60) ?: "",
            category.raceBand?.toDisplayLabel() ?: ""
        ).joinToString(EventCsvFormat.DELIMITER.toString())
    }

    /** Formats a competitor row in the existing simple competitor CSV export shape. */
    fun competitorRow(competitor: EventCompetitor, categoryName: String): String {
        return listOf(
            competitor.siNumber ?: "",
            competitor.startNumber,
            competitor.firstName,
            competitor.lastName,
            categoryName,
            if (competitor.isMan) 0 else 1,
            competitor.birthYear ?: "",
            competitor.club,
            competitor.index,
            competitor.drawnStartTimeSeconds?.let { DurationFormatter.secondsToFormattedString(it, useMinutes = true) } ?: "",
            if (competitor.siRent) 1 else 0,
            competitor.preferredStartGroup ?: ""
        ).joinToString(EventCsvFormat.DELIMITER.toString()) { it.toString().csvField() }
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

    fun robisStartListRow(
        competitor: EventCompetitor,
        categoryName: String,
        startTimeText: String?
    ): String =
        "\"\";${competitor.lastName.csvField()};${competitor.firstName.csvField()};" +
                "${categoryName.csvField()};\"\";${(startTimeText ?: "").csvField()};" +
                "${competitor.index.csvField()};\"\";\"CZE\";${(competitor.siNumber ?: "").toString().csvField()}"

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

    fun ardfEventResultRow(
        categoryName: String,
        placeText: String,
        competitorName: String,
        index: String,
        runTimeText: String,
        pointsText: String,
        statusLabel: String,
        controlOrderText: String
    ): String =
        csvRow(categoryName, placeText, competitorName, index, runTimeText, pointsText, statusLabel, controlOrderText)

    private fun csvRow(vararg fields: Any?): String =
        fields.joinToString(EventCsvFormat.DELIMITER.toString()) { (it ?: "").toString().csvField() }

    private fun String.csvField(): String =
        if (any { it == EventCsvFormat.DELIMITER || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }
}
