/*
 * MIT License
 *
 * Copyright (c) 2026 OpenARDF
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

package org.openardf.radiooracle.shared.printing

import org.openardf.radiooracle.shared.domain.PunchStatus

/** Fixed-width finish-ticket row formatting shared by desktop and ESC/POS printers. */
object FinishTicketTimeRowFormatter {
    fun format(label: String, time: String, split: String?, charactersPerLine: Int): String {
        val timeWidth = 8
        val splitWidth = split?.length?.coerceAtLeast(8) ?: 8
        val labelWidth = charactersPerLine - timeWidth - splitWidth - 2
        if (labelWidth < 1) {
            return listOfNotNull(label, time, split).joinToString(" ").truncate(charactersPerLine)
        }

        val fixedLabel = label.truncate(labelWidth).padEnd(labelWidth)
        val fixedTime = time.takeLast(timeWidth).padStart(timeWidth)
        if (split == null) {
            return "$fixedLabel $fixedTime"
        }

        return "$fixedLabel $fixedTime ${split.padStart(splitWidth)}"
    }

    // ESC/POS code pages do not reliably print UI glyphs; valid punches stay unmarked.
    fun statusSuffix(status: PunchStatus): String =
        when (status) {
            PunchStatus.VALID -> ""
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "+"
            PunchStatus.UNKNOWN -> "?"
        }

    private fun String.truncate(maxLength: Int): String =
        if (maxLength > 0 && length > maxLength) take(maxLength) else this
}
