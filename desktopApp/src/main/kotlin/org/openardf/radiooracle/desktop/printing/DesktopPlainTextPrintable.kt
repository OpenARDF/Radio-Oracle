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

package org.openardf.radiooracle.desktop.printing

import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import kotlin.math.floor

/** Renders fixed-width ticket text through the native Java/OS graphics print path. */
internal class DesktopPlainTextPrintable(text: String) : Printable {
    internal val lines: List<String> = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        if (pageIndex < 0 || pageFormat.imageableWidth <= 0.0 || pageFormat.imageableHeight <= 0.0) {
            return Printable.NO_SUCH_PAGE
        }

        val graphics2D = graphics.create() as Graphics2D
        try {
            graphics2D.translate(pageFormat.imageableX, pageFormat.imageableY)
            graphics2D.font = fittedFont(graphics2D, pageFormat.imageableWidth)
            val metrics = graphics2D.fontMetrics
            val linesPerPage = floor(pageFormat.imageableHeight / metrics.height).toInt().coerceAtLeast(1)
            val pageLines = linesForPage(pageIndex, linesPerPage)
            if (pageLines.isEmpty()) {
                return Printable.NO_SUCH_PAGE
            }

            var baseline = metrics.ascent
            pageLines.forEach { line ->
                graphics2D.drawString(line, 0, baseline)
                baseline += metrics.height
            }
            return Printable.PAGE_EXISTS
        } finally {
            graphics2D.dispose()
        }
    }

    internal fun linesForPage(pageIndex: Int, linesPerPage: Int): List<String> {
        if (pageIndex < 0 || linesPerPage <= 0) return emptyList()
        val startIndex = pageIndex.toLong() * linesPerPage
        if (startIndex >= lines.size) return emptyList()
        val start = startIndex.toInt()
        return lines.subList(start, (start + linesPerPage).coerceAtMost(lines.size))
    }

    private fun fittedFont(graphics: Graphics2D, imageableWidth: Double): Font {
        val baseFont = Font(Font.MONOSPACED, Font.PLAIN, PreferredFontSize)
        val longestLine = lines.maxByOrNull { it.length }.orEmpty()
        if (longestLine.isEmpty()) return baseFont

        val lineWidth = graphics.getFontMetrics(baseFont).stringWidth(longestLine)
        if (lineWidth <= imageableWidth || lineWidth == 0) return baseFont

        val fittedSize = (PreferredFontSize * imageableWidth / lineWidth)
            .toFloat()
            .coerceAtLeast(MinimumFontSize)
        return baseFont.deriveFont(fittedSize)
    }

    private companion object {
        const val PreferredFontSize = 9
        const val MinimumFontSize = 5f
    }
}

/**
 * Java printer drivers commonly apply letter-sized margins even to receipt rolls. Reduce those
 * margins only when the selected driver reports narrow media, leaving normal office pages alone.
 */
internal fun desktopPrintablePageFormat(pageFormat: PageFormat): PageFormat {
    val adjusted = pageFormat.clone() as PageFormat
    if (adjusted.width > MaximumReceiptWidthPoints || adjusted.width <= 0.0 || adjusted.height <= 0.0) {
        return adjusted
    }

    val paper = adjusted.paper.clone() as Paper
    val horizontalMargin = ReceiptMarginPoints.coerceAtMost(paper.width / 4.0)
    val verticalMargin = ReceiptMarginPoints.coerceAtMost(paper.height / 4.0)
    paper.setImageableArea(
        horizontalMargin,
        verticalMargin,
        (paper.width - horizontalMargin * 2.0).coerceAtLeast(1.0),
        (paper.height - verticalMargin * 2.0).coerceAtLeast(1.0)
    )
    adjusted.paper = paper
    return adjusted
}

private const val PointsPerInch = 72.0
private const val MaximumReceiptWidthPoints = 4.0 * PointsPerInch
private const val ReceiptMarginPoints = 6.0
