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

import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.PublicResultsPublicationStatus
import kotlin.math.round

/** Landscape-letter split report that can be generated on both desktop and Android. */
object SplitResultPdfExports {
    private const val PageWidth = 792.0
    private const val PageHeight = 612.0
    private const val Left = 36.0
    private const val Right = 36.0
    private const val Top = 576.0
    private const val Bottom = 38.0
    private const val SummaryHeaderHeight = 16.0
    private const val SummaryRowHeight = 17.0
    private const val SplitHeaderHeight = 15.0
    private const val SplitRowHeight = 14.0
    private val SummaryColumns = listOf(
        PdfColumn("Pl", 30.0),
        PdfColumn("Bib", 40.0),
        PdfColumn("Competitor", 190.0),
        PdfColumn("Club", 80.0),
        PdfColumn("SI", 60.0),
        PdfColumn("Status", 45.0),
        PdfColumn("Pts", 32.0),
        PdfColumn("Total", 60.0),
        PdfColumn("Transmitters", 183.0)
    )
    private val SplitColumns = listOf(
        PdfColumn("#", 30.0),
        PdfColumn("From", 185.0),
        PdfColumn("Control", 185.0),
        PdfColumn("Punch", 55.0),
        PdfColumn("Leg", 75.0),
        PdfColumn("Total", 75.0),
        PdfColumn("Leg pl", 55.0)
    )

    fun pdf(
        raceData: EventRaceData,
        awardDisplayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD,
        publicationStatus: PublicResultsPublicationStatus? = null
    ): ByteArray = pdf(SplitResultExports.model(raceData, awardDisplayMode, publicationStatus))

    fun pdf(report: SplitResultReport): ByteArray =
        SimpleSplitPdf.bytes(pageContents(report), PageWidth, PageHeight)

    private fun pageContents(report: SplitResultReport): List<String> {
        val pages = mutableListOf<StringBuilder>()
        var page = StringBuilder()
        var y = Top

        fun newPage(categoryName: String? = null, continued: Boolean = false) {
            page = StringBuilder()
            pages += page
            y = Top
            page.appendPdfText(Left, y, 16, report.raceName.ifBlank { "Split Results" }, bold = true)
            y -= 19.0
            page.appendPdfText(
                Left,
                y,
                9,
                "Split Results   Start: ${report.startDateTimeIso}   Level: ${report.raceLevel}"
            )
            y -= 15.0
            report.publicationNotice?.takeIf(String::isNotBlank)?.let { notice ->
                page.appendPdfText(Left, y, 9, fitText(notice, PageWidth - Left - Right, 9), bold = true)
                y -= 15.0
            }
            categoryName?.let { name ->
                page.appendPdfText(Left, y, 13, name + if (continued) " (continued)" else "", bold = true)
                y -= 18.0
                page.appendTableHeader(Left, y, SummaryColumns)
                y -= SummaryHeaderHeight
            }
        }

        fun ensureCategorySpace(categoryName: String, space: Double, continued: Boolean = true) {
            if (y - space < Bottom) {
                newPage(categoryName, continued)
            }
        }

        fun drawSummary(result: SplitResultCompetitor, continued: Boolean = false) {
            page.appendTableRow(
                x = Left,
                y = y,
                height = SummaryRowHeight,
                columns = SummaryColumns,
                values = listOf(
                    result.placeText,
                    result.bibNumber,
                    result.name + if (continued) " (continued)" else "",
                    result.club,
                    result.siNumber,
                    result.statusText,
                    result.pointsText,
                    result.runTimeText,
                    result.transmittersText
                ),
                shaded = true
            )
            y -= SummaryRowHeight
        }

        if (report.categories.isEmpty()) {
            newPage()
            page.appendPdfText(Left, y, 10, "No result data is available.")
            y -= 14.0
        }

        report.categories.forEach { category ->
            newPage(category.name)
            category.results.forEach { result ->
                ensureCategorySpace(
                    categoryName = category.name,
                    space = SummaryRowHeight + SplitHeaderHeight + SplitRowHeight + 8.0
                )
                drawSummary(result)
                val splitX = Left + 30.0
                page.appendTableHeader(splitX, y, SplitColumns)
                y -= SplitHeaderHeight

                if (result.splits.isEmpty()) {
                    page.appendTableRow(
                        x = splitX,
                        y = y,
                        height = SplitRowHeight,
                        columns = SplitColumns,
                        values = listOf("", "", "No split details available", "", "", "", "")
                    )
                    y -= SplitRowHeight
                } else {
                    result.splits.forEachIndexed { index, split ->
                        if (y - SplitRowHeight < Bottom) {
                            newPage(category.name, continued = true)
                            drawSummary(result, continued = true)
                            page.appendTableHeader(splitX, y, SplitColumns)
                            y -= SplitHeaderHeight
                        }
                        page.appendTableRow(
                            x = splitX,
                            y = y,
                            height = SplitRowHeight,
                            columns = SplitColumns,
                            values = listOf(
                                (index + 1).toString(),
                                split.from.label,
                                split.control.label,
                                split.punchStatusText,
                                split.legTime,
                                split.cumulativeTime,
                                split.legPlaceText
                            )
                        )
                        y -= SplitRowHeight
                    }
                }
                y -= 8.0
            }
        }

        pages.forEachIndexed { index, content ->
            content.appendPdfText(PageWidth - Right - 70.0, 22.0, 8, "Page ${index + 1} of ${pages.size}")
        }
        return pages.map(StringBuilder::toString)
    }

    private fun StringBuilder.appendTableHeader(x: Double, y: Double, columns: List<PdfColumn>) {
        val height = if (columns === SummaryColumns) SummaryHeaderHeight else SplitHeaderHeight
        val width = columns.sumOf(PdfColumn::width)
        val bottom = y - height
        appendLine("0.90 0.92 0.95 rg")
        appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re f")
        appendLine("0.35 0.40 0.46 RG")
        appendLine("0.6 w")
        appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re S")
        var columnX = x
        columns.forEach { column ->
            appendPdfText(columnX + 3.0, y - 11.0, 8, fitText(column.title, column.width, 8), bold = true)
            appendLine("${pdfNumber(columnX)} ${pdfNumber(bottom)} m ${pdfNumber(columnX)} ${pdfNumber(y)} l S")
            columnX += column.width
        }
        appendLine("${pdfNumber(columnX)} ${pdfNumber(bottom)} m ${pdfNumber(columnX)} ${pdfNumber(y)} l S")
    }

    private fun StringBuilder.appendTableRow(
        x: Double,
        y: Double,
        height: Double,
        columns: List<PdfColumn>,
        values: List<String>,
        shaded: Boolean = false
    ) {
        val bottom = y - height
        val width = columns.sumOf(PdfColumn::width)
        if (shaded) {
            appendLine("0.97 0.98 0.99 rg")
            appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re f")
        }
        appendLine("0.72 0.74 0.78 RG")
        appendLine("0.35 w")
        appendLine("${pdfNumber(x)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re S")
        var columnX = x
        columns.zip(values).forEach { (column, value) ->
            appendPdfText(columnX + 3.0, y - 10.5, 8, fitText(value, column.width, 8))
            appendLine("${pdfNumber(columnX)} ${pdfNumber(bottom)} m ${pdfNumber(columnX)} ${pdfNumber(y)} l S")
            columnX += column.width
        }
        appendLine("${pdfNumber(columnX)} ${pdfNumber(bottom)} m ${pdfNumber(columnX)} ${pdfNumber(y)} l S")
    }

    private fun StringBuilder.appendPdfText(x: Double, y: Double, size: Int, value: String, bold: Boolean = false) {
        appendLine("BT")
        appendLine("${if (bold) "/F2" else "/F1"} $size Tf")
        appendLine("0 0 0 rg")
        appendLine("1 0 0 1 ${pdfNumber(x)} ${pdfNumber(y)} Tm")
        appendLine("(${pdfText(value)}) Tj")
        appendLine("ET")
    }

    private fun fitText(text: String, width: Double, fontSize: Int): String {
        val maxCharacters = (width / (fontSize * 0.52)).toInt().coerceAtLeast(3)
        return if (text.length <= maxCharacters) text else text.take(maxCharacters - 3).trimEnd() + "..."
    }

    private fun pdfText(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                in ' '..'~' -> append(character)
                else -> append('?')
            }
        }
    }

    private fun pdfNumber(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }

    private data class PdfColumn(val title: String, val width: Double)

    private object SimpleSplitPdf {
        fun bytes(pageContents: List<String>, pageWidth: Double, pageHeight: Double): ByteArray {
            val safePages = pageContents.ifEmpty { listOf("") }
            val objects = mutableListOf<String>()
            objects += "<< /Type /Catalog /Pages 2 0 R >>"
            objects += "<< /Type /Pages /Kids ${safePages.indices.joinToString(" ", "[", "]") { "${4 + it * 2} 0 R" }} /Count ${safePages.size} >>"
            objects += "<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> /F2 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> >> >>"
            safePages.forEachIndexed { index, content ->
                val pageObjectId = 4 + index * 2
                val contentObjectId = pageObjectId + 1
                objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pdfNumber(pageWidth)} ${pdfNumber(pageHeight)}] /Resources 3 0 R /Contents $contentObjectId 0 R >>"
                objects += "<< /Length ${content.length} >>\nstream\n$content\nendstream"
            }

            val output = StringBuilder("%PDF-1.4\n")
            val offsets = mutableListOf<Int>()
            objects.forEachIndexed { index, value ->
                offsets += output.length
                output.append("${index + 1} 0 obj\n")
                output.append(value)
                output.append("\nendobj\n")
            }
            val xrefOffset = output.length
            output.append("xref\n0 ${objects.size + 1}\n")
            output.append("0000000000 65535 f \n")
            offsets.forEach { offset ->
                output.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
            }
            output.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
            output.append("startxref\n$xrefOffset\n%%EOF\n")
            return output.toString().encodeToByteArray()
        }
    }
}
