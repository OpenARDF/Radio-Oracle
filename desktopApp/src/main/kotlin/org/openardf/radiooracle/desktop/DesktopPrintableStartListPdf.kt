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

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.time.DurationFormatter
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Printable PDF export for start crews who need a stable paper start list. */
object DesktopPrintableStartListPdf {
    private const val Left = 54.0
    private const val Top = 750.0
    private const val TableTop = 704.0
    private const val Bottom = 54.0
    private const val HeaderHeight = 22.0
    private const val RowHeight = 20.0
    private val ScheduledTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy - HH:mm", Locale.US)
    private val CenteredColumns = setOf(0, 1, 3, 4, 5, 6)

    private val Columns = listOf(
        PdfColumn("Start #", 52.0),
        PdfColumn("Start Time", 62.0),
        PdfColumn("Competitor's full name", 130.0, rowFontSize = 8),
        PdfColumn("Bib #", 40.0),
        PdfColumn("Category", 56.0),
        PdfColumn("SI #", 64.0),
        PdfColumn("Corridor", 100.0, rowFontSize = 6, minimumRowFontSize = 3)
    )

    fun defaultFileName(projectFile: EventProjectFile): String =
        DesktopProjectFilePaths.defaultPdfFileName(projectFile.raceData.race.name, "printable start list")

    fun exportPdf(path: Path, projectFile: EventProjectFile) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(projectFile))
    }

    internal fun pdfBytes(projectFile: EventProjectFile): ByteArray {
        val rows = printableRows(projectFile.raceData)
        val rowsPerPage = ((TableTop - Bottom - HeaderHeight) / RowHeight).toInt().coerceAtLeast(1)
        val pages = rows.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }
        val pageContents = pages.mapIndexed { index, pageRows ->
            pageContent(
                projectFile = projectFile,
                rows = pageRows,
                pageNumber = index + 1,
                pageCount = pages.size
            )
        }
        return pdfDocument(pageContents)
    }

    private fun printableRows(raceData: EventRaceData): List<PrintableStartListRow> {
        val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
        val sortedRows = raceData.competitorData
            .sortedWith(
                compareBy<EventCompetitorData>(
                    { it.competitorCategory.competitor.drawnStartTimeSeconds == null },
                    { it.competitorCategory.competitor.drawnStartTimeSeconds ?: Long.MAX_VALUE },
                    { categoryName(it, categoryNamesById) },
                    { it.competitorCategory.competitor.fullName() }
                )
            )

        var previousStartSeconds: Long? = null
        var startSlotNumber = 0
        val firstStartSeconds = sortedRows
            .mapNotNull { it.competitorCategory.competitor.drawnStartTimeSeconds }
            .minOrNull()
        return sortedRows.map { data ->
            val competitor = data.competitorCategory.competitor
            val startSeconds = competitor.drawnStartTimeSeconds
            val startNumberText = if (startSeconds == null) {
                ""
            } else {
                if (previousStartSeconds != startSeconds) {
                    startSlotNumber += 1
                    previousStartSeconds = startSeconds
                }
                startSlotNumber.toString()
            }
            PrintableStartListRow(
                startNumberText = startNumberText,
                startTimeText = startSeconds?.let {
                    // The header carries the scheduled race start; printed rows use start-list-relative time.
                    DurationFormatter.secondsToFormattedString(it - (firstStartSeconds ?: it), useMinutes = false)
                }.orEmpty(),
                competitorName = competitor.fullName(),
                bibNumber = competitor.bibNumber,
                categoryName = categoryName(data, categoryNamesById),
                siNumberText = competitor.siNumber?.toString().orEmpty(),
                corridor = competitor.corridor,
                shaded = startSlotNumber > 0 && startSlotNumber % 2 == 0 && startSeconds != null
            )
        }
    }

    private fun categoryName(data: EventCompetitorData, categoryNamesById: Map<String, String>): String =
        data.competitorCategory.category?.name
            ?: data.competitorCategory.competitor.categoryId?.let { categoryNamesById[it] }
            ?: ""

    private fun pageContent(
        projectFile: EventProjectFile,
        rows: List<PrintableStartListRow>,
        pageNumber: Int,
        pageCount: Int
    ): String = buildString {
        val title = projectFile.raceData.race.name.ifBlank { "Untitled Race" }
        val scheduled = scheduledTimeText(projectFile.raceData.race.startDateTimeIso)
        appendText(Left, Top, 16, title, bold = true)
        appendText(Left, Top - 20.0, 10, "Scheduled Time: $scheduled")
        appendText(Left, Top - 34.0, 10, timeLimitText(projectFile.raceData.race.timeLimitSeconds))
        appendText(DesktopPdfDocument.LetterWidth - 120.0, Top - 20.0, 9, "Page $pageNumber of $pageCount")
        appendTable(rows)
    }

    private fun StringBuilder.appendTable(rows: List<PrintableStartListRow>) {
        val tableWidth = Columns.sumOf { it.width }
        val headerBottom = TableTop - HeaderHeight
        appendLine("0.88 0.88 0.88 rg")
        appendLine("${pdfNumber(Left)} ${pdfNumber(headerBottom)} ${pdfNumber(tableWidth)} ${pdfNumber(HeaderHeight)} re f")
        appendLine("0.25 0.25 0.25 RG")
        appendLine("0.8 w")
        appendLine("${pdfNumber(Left)} ${pdfNumber(headerBottom)} ${pdfNumber(tableWidth)} ${pdfNumber(HeaderHeight)} re S")

        // Column boundaries are drawn for every row so blank Bib/SI/category cells remain visible.
        var x = Left
        Columns.forEachIndexed { index, column ->
            val text = fitText(column.title, column.width, 8)
            appendText(textXForColumn(index, x, column.width, text, 8), TableTop - 14.0, 8, text, bold = true)
            appendLine("${pdfNumber(x)} ${pdfNumber(headerBottom)} m ${pdfNumber(x)} ${pdfNumber(TableTop)} l S")
            x += column.width
        }
        appendLine("${pdfNumber(Left + tableWidth)} ${pdfNumber(headerBottom)} m ${pdfNumber(Left + tableWidth)} ${pdfNumber(TableTop)} l S")

        rows.forEachIndexed { index, row ->
            val rowTop = headerBottom - index * RowHeight
            val rowBottom = rowTop - RowHeight
            if (row.shaded) {
                appendLine("0.96 0.96 0.96 rg")
                appendLine("${pdfNumber(Left)} ${pdfNumber(rowBottom)} ${pdfNumber(tableWidth)} ${pdfNumber(RowHeight)} re f")
            }
            appendLine("0.75 0.75 0.75 RG")
            appendLine("0.4 w")
            appendLine("${pdfNumber(Left)} ${pdfNumber(rowBottom)} m ${pdfNumber(Left + tableWidth)} ${pdfNumber(rowBottom)} l S")
            appendRowText(row, rowTop - 13.0)
            appendColumnLines(rowBottom, rowTop)
        }
    }

    private fun StringBuilder.appendRowText(row: PrintableStartListRow, y: Double) {
        val values = listOf(
            row.startNumberText,
            row.startTimeText,
            row.competitorName,
            row.bibNumber,
            row.categoryName,
            row.siNumberText,
            row.corridor
        )
        var x = Left
        Columns.zip(values).forEachIndexed { index, (column, value) ->
            val fontSize = column.rowFontSizeFor(value)
            val text = fitText(value, column.width, fontSize)
            appendText(
                textXForColumn(index, x, column.width, text, fontSize),
                y,
                fontSize,
                text
            )
            x += column.width
        }
    }

    private fun StringBuilder.appendColumnLines(bottom: Double, top: Double) {
        var x = Left
        appendLine("0.85 0.85 0.85 RG")
        Columns.forEach { column ->
            appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} m ${pdfNumber(x)} ${pdfNumber(top)} l S")
            x += column.width
        }
        appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} m ${pdfNumber(x)} ${pdfNumber(top)} l S")
    }

    private fun StringBuilder.appendText(x: Double, y: Double, fontSize: Int, text: String, bold: Boolean = false) {
        appendLine("BT")
        appendLine("${if (bold) "/F2" else "/F1"} $fontSize Tf")
        appendLine("0 0 0 rg")
        appendLine("1 0 0 1 ${pdfNumber(x)} ${pdfNumber(y)} Tm")
        appendLine("(${DesktopExportPrimitives.pdfText(text)}) Tj")
        appendLine("ET")
    }

    private fun pdfDocument(pageContents: List<String>): ByteArray =
        DesktopPdfDocument.bytes(pageContents)

    private fun fitText(text: String, width: Double, fontSize: Int): String {
        val maxChars = (width / (fontSize * 0.52)).toInt().coerceAtLeast(3)
        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars - 3).trimEnd() + "..."
        }
    }

    private fun scheduledTimeText(startDateTimeIso: String): String =
        try {
            LocalDateTime.parse(startDateTimeIso).format(ScheduledTimeFormatter)
        } catch (_: DateTimeParseException) {
            startDateTimeIso.replace('T', ' ')
        }

    private fun timeLimitText(timeLimitSeconds: Long?): String =
        if (timeLimitSeconds == null) {
            "Time Limit: Not set"
        } else {
            "Time Limit: ${timeLimitSeconds / 60} minutes"
        }

    private fun centeredTextX(cellLeft: Double, cellWidth: Double, text: String, fontSize: Int): Double {
        val estimatedTextWidth = text.length * fontSize * 0.52
        return cellLeft + ((cellWidth - estimatedTextWidth) / 2.0).coerceAtLeast(3.0)
    }

    private fun centeredHelveticaTextX(cellLeft: Double, cellWidth: Double, text: String, fontSize: Int): Double {
        val estimatedTextWidth = text.sumOf(::helveticaWidthUnits) * fontSize
        return cellLeft + ((cellWidth - estimatedTextWidth) / 2.0).coerceAtLeast(3.0)
    }

    private fun textXForColumn(index: Int, cellLeft: Double, cellWidth: Double, text: String, fontSize: Int): Double =
        if (index == Columns.lastIndex && text.length > 15) {
            centeredHelveticaTextX(cellLeft, cellWidth, text, fontSize)
        } else if (index in CenteredColumns) {
            centeredTextX(cellLeft, cellWidth, text, fontSize)
        } else {
            cellLeft + 3.0
        }

    private fun pdfNumber(value: Double): String =
        DesktopPdfDocument.number(value)

    private fun PdfColumn.rowFontSizeFor(value: String): Int {
        if (value.isEmpty() || minimumRowFontSize >= rowFontSize) return rowFontSize
        val availableWidth = width - 6.0
        val fittedSize = (availableWidth / value.sumOf(::helveticaWidthUnits)).toInt()
        return fittedSize.coerceIn(minimumRowFontSize, rowFontSize)
    }

    private fun helveticaWidthUnits(character: Char): Double = when (character) {
        'W' -> 0.944
        'M', 'm' -> 0.833
        'G', 'O', 'Q' -> 0.778
        'C', 'D', 'H', 'N', 'R', 'U', 'w' -> 0.722
        'A', 'B', 'E', 'K', 'P', 'V', 'X', 'Y' -> 0.667
        'F', 'S', 'T', 'Z' -> 0.611
        'L' -> 0.556
        'J' -> 0.500
        'I' -> 0.278
        'i', 'j', 'l' -> 0.222
        in '0'..'9' -> 0.556
        else -> 0.556
    }

    private data class PdfColumn(
        val title: String,
        val width: Double,
        val rowFontSize: Int = 9,
        val minimumRowFontSize: Int = rowFontSize
    )

    private data class PrintableStartListRow(
        val startNumberText: String,
        val startTimeText: String,
        val competitorName: String,
        val bibNumber: String,
        val categoryName: String,
        val siNumberText: String,
        val corridor: String,
        val shaded: Boolean
    )
}
