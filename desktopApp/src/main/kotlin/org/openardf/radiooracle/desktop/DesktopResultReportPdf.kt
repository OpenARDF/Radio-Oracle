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

import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.files.ResultReport
import org.openardf.radiooracle.shared.files.ResultReportExports
import java.nio.file.Files
import java.nio.file.Path

/** Printable PDF companion for Results report result reports. */
object DesktopResultReportPdf {
    private const val Left = 42.0
    private const val Right = 42.0
    private const val Top = 750.0
    private const val Bottom = 54.0
    private const val LineHeight = 14.0
    private const val HeaderHeight = 18.0
    private const val RowHeight = 16.0
    private val Columns = listOf(
        PdfColumn("Pl", 28.0),
        PdfColumn("Name", 132.0),
        PdfColumn("Club", 70.0),
        PdfColumn("Bib", 36.0),
        PdfColumn("SI", 54.0),
        PdfColumn("Status", 42.0),
        PdfColumn("Pts", 32.0),
        PdfColumn("Time", 58.0),
        PdfColumn("Controls", 76.0)
    )

    fun defaultFileName(projectFile: EventProjectFile): String =
        DesktopProjectFilePaths.defaultPdfFileName(projectFile.raceData.race.name, "results report")

    fun exportPdf(
        path: Path,
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(projectFile, protectedCourseInfoByCategoryId, awardDisplayMode))
    }

    internal fun pdfBytes(
        projectFile: EventProjectFile,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
    ): ByteArray {
        val report = ResultReportExports.model(
            projectFile.raceData,
            protectedCourseInfoByCategoryId,
            awardDisplayMode
        )
        return DesktopPdfDocument.bytes(pageContents(report))
    }

    private fun pageContents(report: ResultReport): List<String> {
        val pages = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        pages += current
        var y = Top

        fun newPage() {
            current = StringBuilder()
            pages += current
            y = Top
        }

        fun ensure(space: Double) {
            if (y - space < Bottom) {
                newPage()
            }
        }

        fun line(text: String, fontSize: Int = 9, bold: Boolean = false, indent: Double = 0.0) {
            ensure(LineHeight)
            current.appendPdfText(Left + indent, y, fontSize, text, bold)
            y -= LineHeight
        }

        line(report.raceName.ifBlank { "Results Report" }, fontSize = 16, bold = true)
        line("Start: ${report.startDateTimeIso}   Level: ${report.raceLevel}", fontSize = 10)
        report.publicationNotice?.let { line(it, fontSize = 10, bold = true) }
        y -= 6.0

        report.categories.forEach { category ->
            ensure(HeaderHeight + RowHeight * 2)
            line(category.name, fontSize = 13, bold = true)
            val metaText = categoryMetaText(category)
            if (metaText.isNotBlank()) {
                line(metaText, fontSize = 8)
            }
            ensure(HeaderHeight)
            current.appendTableHeader(y)
            y -= HeaderHeight
            category.results.forEach { result ->
                ensure(RowHeight)
                current.appendResultRow(y, listOf(
                    result.placeText,
                    result.name,
                    result.club,
                    result.bibNumber,
                    result.siNumber,
                    result.statusText,
                    result.pointsText,
                    result.runTimeText,
                    result.controlsText
                ))
                y -= RowHeight
            }
            y -= 8.0
        }

        if (report.awardCategories.isNotEmpty()) {
            line("Championship Awards", fontSize = 13, bold = true)
            report.awardCategories.forEach { category ->
                line(category.categoryName, fontSize = 11, bold = true)
                category.groups.forEach { group ->
                    line(group.label, fontSize = 9, bold = true, indent = 10.0)
                    group.winners.forEach { winner ->
                        line(
                            "${winner.awardLevel} ${winner.awardPlace}: ${winner.competitorName} (${winner.runTimeText}, ${winner.pointsText} pts)",
                            fontSize = 8,
                            indent = 18.0
                        )
                    }
                }
            }
        }

        pages.forEachIndexed { index, page ->
            page.appendPdfText(DesktopPdfDocument.LetterWidth - Right - 58.0, 30.0, 8, "Page ${index + 1} of ${pages.size}")
        }
        return pages.map { it.toString() }
    }

    private fun StringBuilder.appendTableHeader(y: Double) {
        val tableWidth = Columns.sumOf { it.width }
        val headerBottom = y - HeaderHeight
        appendLine("0.88 0.88 0.88 rg")
        appendLine("${pdfNumber(Left)} ${pdfNumber(headerBottom)} ${pdfNumber(tableWidth)} ${pdfNumber(HeaderHeight)} re f")
        appendLine("0.25 0.25 0.25 RG")
        appendLine("0.8 w")
        appendLine("${pdfNumber(Left)} ${pdfNumber(headerBottom)} ${pdfNumber(tableWidth)} ${pdfNumber(HeaderHeight)} re S")
        var x = Left
        Columns.forEach { column ->
            appendPdfText(x + 3.0, y - 12.0, 8, fitText(column.title, column.width, 8), bold = true)
            appendLine("${pdfNumber(x)} ${pdfNumber(headerBottom)} m ${pdfNumber(x)} ${pdfNumber(y)} l S")
            x += column.width
        }
        appendLine("${pdfNumber(Left + tableWidth)} ${pdfNumber(headerBottom)} m ${pdfNumber(Left + tableWidth)} ${pdfNumber(y)} l S")
    }

    private fun StringBuilder.appendResultRow(y: Double, values: List<String>) {
        val rowBottom = y - RowHeight
        val tableWidth = Columns.sumOf { it.width }
        appendLine("0.78 0.78 0.78 RG")
        appendLine("0.35 w")
        appendLine("${pdfNumber(Left)} ${pdfNumber(rowBottom)} m ${pdfNumber(Left + tableWidth)} ${pdfNumber(rowBottom)} l S")
        var x = Left
        Columns.zip(values).forEach { (column, value) ->
            appendPdfText(x + 3.0, y - 11.0, 8, fitText(value, column.width, 8))
            appendLine("${pdfNumber(x)} ${pdfNumber(rowBottom)} m ${pdfNumber(x)} ${pdfNumber(y)} l S")
            x += column.width
        }
        appendLine("${pdfNumber(x)} ${pdfNumber(rowBottom)} m ${pdfNumber(x)} ${pdfNumber(y)} l S")
    }

    private fun StringBuilder.appendPdfText(x: Double, y: Double, fontSize: Int, text: String, bold: Boolean = false) {
        appendLine("BT")
        appendLine("${if (bold) "/F2" else "/F1"} $fontSize Tf")
        appendLine("0 0 0 rg")
        appendLine("1 0 0 1 ${pdfNumber(x)} ${pdfNumber(y)} Tm")
        appendLine("(${DesktopExportPrimitives.pdfText(text)}) Tj")
        appendLine("ET")
    }

    private fun categoryMetaText(category: org.openardf.radiooracle.shared.files.ResultReportCategory): String =
        listOfNotNull(
            category.timeLimitMinutes?.let { "Limit: $it min" },
            category.lengthKmText?.let { "Length: $it" },
            category.effectiveLengthKmText?.let { "Effective length: $it" },
            category.climbMeters?.let { "Climb: $it m" },
            category.controlsText.takeIf { it.isNotBlank() }?.let { "Controls: $it" }
        ).joinToString("   ")

    private fun fitText(text: String, width: Double, fontSize: Int): String {
        val maxChars = (width / (fontSize * 0.52)).toInt().coerceAtLeast(3)
        return if (text.length <= maxChars) {
            text
        } else {
            text.take(maxChars - 3).trimEnd() + "..."
        }
    }

    private fun pdfNumber(value: Double): String =
        DesktopPdfDocument.number(value)

    private data class PdfColumn(
        val title: String,
        val width: Double
    )
}
