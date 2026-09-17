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
    /** Formats a category row in the category export shape. */
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
            competitor.callSign,
            competitor.email,
            competitor.cellPhone,
            competitor.usaChampEligible?.let { if (it) 1 else 0 } ?: "",
            competitor.region2ChampEligible?.let { if (it) 1 else 0 } ?: ""
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
            competitor.siNumber,
            competitor.corridor
        )
    }

    /** Dedicated ROBIS compatibility profile: retain its semicolon dialect and column layout. */
    fun robisStartListRow(
        competitor: EventCompetitor,
        categoryName: String,
        startTimeText: String?
    ): String =
        "\"\";${competitor.lastName.legacyCsvField()};${competitor.firstName.legacyCsvField()};" +
                "${categoryName.legacyCsvField()};\"\";${(startTimeText ?: "").legacyCsvField()};" +
                "${competitor.index.legacyCsvField()};\"\";\"CZE\";${(competitor.siNumber ?: "").toString().legacyCsvField()}"

    fun readoutColumns(punchColumnCount: Int): List<String> =
        listOf("si_number", "check_time", "start_time", "finish_time", "control_count") +
            (1..punchColumnCount).flatMap { listOf("control_${it}_code", "control_${it}_time") }

    fun readoutHeader(punchColumnCount: Int): String = CsvCodec.row(readoutColumns(punchColumnCount))

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
        controlPunches: List<TimedPunchCsvField>,
        punchColumnCount: Int = controlPunches.size
    ): String {
        val headerFields: List<Any?> = listOf(
            siNumber ?: "",
            checkTimeText ?: "",
            startTimeText ?: "",
            finishTimeText ?: "",
            controlPunches.size
        )
        val punchFields: List<Any?> = controlPunches.flatMap { punch -> listOf(punch.siCode, punch.timeText) }

        val padding = List((punchColumnCount - controlPunches.size).coerceAtLeast(0) * 2) { "" }
        return csvRow(*(headerFields + punchFields + padding).toTypedArray())
    }

    /** Formats one ranked result row in the same order as the desktop Results section. */
    fun resultRow(
        placeText: String,
        competitorName: String,
        statusLabel: String,
        pointsText: String,
        runTimeText: String,
        usaAwardText: String? = null,
        region2AwardText: String? = null
    ): String {
        val fields = mutableListOf<Any?>(placeText, competitorName, statusLabel, pointsText, runTimeText)
        if (usaAwardText != null || region2AwardText != null) {
            fields += usaAwardText.orEmpty()
            fields += region2AwardText.orEmpty()
        }
        return csvRow(*fields.toTypedArray())
    }

    /** Dedicated ARDFEvent compatibility profile; standard results use comma-separated CSV. */
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
        CsvCodec.row(listOf(categoryName, placeText, competitorName, index, runTimeText, pointsText, statusLabel, controlOrderText), ';')

    private fun csvRow(vararg fields: Any?): String = CsvCodec.row(fields.asList())

    private fun String.legacyCsvField(): String = CsvCodec.field(this, ';')
}
