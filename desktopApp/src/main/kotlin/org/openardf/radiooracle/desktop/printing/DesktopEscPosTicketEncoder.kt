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

package org.openardf.radiooracle.desktop.printing

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import org.openardf.radiooracle.shared.printing.FinishTicketPlainTextFormatter

/** Encodes the shared ticket markup as printer-native ESC/POS bytes for raw USB print queues. */
internal object DesktopEscPosTicketEncoder {
    fun encode(markedUpText: String, charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE): ByteArray {
        require(charactersPerLine > 0) { "charactersPerLine must be greater than zero." }
        val output = ByteArrayOutputStream()
        output.command(ESC, INITIALIZE)
        output.command(ESC, SELECT_CODE_PAGE, WINDOWS_1250_CODE_PAGE)

        markedUpText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .forEach { markedUpLine ->
                val line = markedUpLine.toEscPosLine(charactersPerLine)
                output.command(ESC, SELECT_ALIGNMENT, line.alignment.commandValue)
                output.command(ESC, SELECT_BOLD, if (line.bold) 1 else 0)
                output.write(line.text.toByteArray(WINDOWS_1250))
                output.command(ESC, SELECT_BOLD, 0)
                output.write(LINE_FEED)
            }

        output.command(ESC, SELECT_ALIGNMENT, Alignment.LEFT.commandValue)
        output.command(ESC, PRINT_AND_FEED_LINES, FINAL_FEED_LINES)
        return output.toByteArray()
    }

    private fun String.toEscPosLine(charactersPerLine: Int): EncodedLine {
        val alignments = ALIGNMENT_TOKEN.findAll(this).map { it.groupValues[1] }.toList()
        val usesColumnLayout = alignments.distinct().size > 1
        val alignment = if (usesColumnLayout) {
            Alignment.LEFT
        } else {
            when (alignments.firstOrNull()) {
                "C" -> Alignment.CENTER
                "R" -> Alignment.RIGHT
                else -> Alignment.LEFT
            }
        }
        val text = if (usesColumnLayout) {
            FinishTicketPlainTextFormatter.format(this + "\n", charactersPerLine).removeSuffix("\n")
        } else {
            replace(ALIGNMENT_TOKEN, "").replace(HTML_TAG, "").trimEnd()
        }
        return EncodedLine(
            alignment = alignment,
            bold = contains("<b>", ignoreCase = true),
            text = text
        )
    }

    private fun ByteArrayOutputStream.command(vararg values: Int) {
        values.forEach(::write)
    }

    private enum class Alignment(val commandValue: Int) {
        LEFT(0),
        CENTER(1),
        RIGHT(2)
    }

    private data class EncodedLine(
        val alignment: Alignment,
        val bold: Boolean,
        val text: String
    )

    private val WINDOWS_1250: Charset = Charset.forName("windows-1250")
    private val ALIGNMENT_TOKEN = Regex("\\[(L|C|R)]")
    private val HTML_TAG = Regex("<[^>]+>")
    private const val DEFAULT_CHARACTERS_PER_LINE = 32
    private const val ESC = 0x1b
    private const val INITIALIZE = 0x40
    private const val SELECT_CODE_PAGE = 0x74
    private const val WINDOWS_1250_CODE_PAGE = 72
    private const val SELECT_ALIGNMENT = 0x61
    private const val SELECT_BOLD = 0x45
    private const val PRINT_AND_FEED_LINES = 0x64
    private const val FINAL_FEED_LINES = 3
    private const val LINE_FEED = 0x0a
}
