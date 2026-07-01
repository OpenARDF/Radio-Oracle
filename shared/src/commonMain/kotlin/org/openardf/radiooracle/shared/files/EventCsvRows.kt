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

import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.time.DurationFormatter

/** A control punch rendered as a pair of SI code and already formatted time text. */
data class TimedPunchCsvField(
    val siCode: Int,
    val timeText: String
)

/** Shared CSV row formatter for race import/export formats used by Android and desktop. */
object EventCsvRows {
    /** Formats a category row in the legacy semicolon-delimited category export shape. */
    fun categoryRow(category: EventCategory): String {
        return csvRow(
            category.name,
            category.isMan.compareTo(false),
            category.maxAge ?: EventCsvFormat.Category.OPEN_MAX_AGE,
            category.lengthMeters,
            category.climbMeters,
            1,
            "",
            "",
            ""
        )
    }

    /** Formats a competitor row in the existing simple competitor CSV export shape. */
    fun competitorRow(competitor: EventCompetitor, categoryName: String): String {
        return csvRow(
            competitor.siNumber ?: "",
            competitor.startNumber ?: "",
            competitor.firstName,
            competitor.lastName,
            categoryName,
            if (competitor.isMan) 0 else 1,
            competitor.birthYear ?: "",
            competitor.club,
            competitor.index,
            competitor.drawnStartTimeSeconds?.let { DurationFormatter.secondsToFormattedString(it, useMinutes = true) } ?: "",
            if (competitor.siRent) 1 else 0,
            competitor.preferredStartGroup ?: "",
            competitor.bibNumber,
            competitor.callSign
        )
    }

    /** Formats a start-list row, using caller-provided absolute start time text when available. */
    fun competitorStartRow(
        competitor: EventCompetitor,
        categoryName: String,
        startTimeText: String?
    ): String {
        return csvRow(
            competitor.startNumber,
            competitor.lastName,
            competitor.firstName,
            categoryName,
            "",
            startTimeText,
            competitor.index,
            competitor.bibNumber,
            competitor.club,
            competitor.siNumber
        )
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
        return csvRow(cardNumber, siCode, timeText)
    }

    /** Formats one full readout row with header times followed by control code/time pairs. */
    fun readoutRow(
        siNumber: Int?,
        checkTimeText: String?,
        startTimeText: String?,
        finishTimeText: String?,
        controlPunches: List<TimedPunchCsvField>
    ): String {
        val headerFields: List<Any?> = listOf(
            siNumber ?: "",
            checkTimeText ?: "",
            startTimeText ?: "",
            finishTimeText ?: "",
            controlPunches.size
        )
        val punchFields: List<Any?> = controlPunches.flatMap { punch -> listOf(punch.siCode, punch.timeText) }

        return csvRow(*(headerFields + punchFields).toTypedArray())
    }

    /** Formats one ranked result row in the same order as the desktop Results section. */
    fun resultRow(
        placeText: String,
        competitorName: String,
        statusLabel: String,
        pointsText: String,
        runTimeText: String
    ): String =
        csvRow(placeText, competitorName, statusLabel, pointsText, runTimeText)

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
