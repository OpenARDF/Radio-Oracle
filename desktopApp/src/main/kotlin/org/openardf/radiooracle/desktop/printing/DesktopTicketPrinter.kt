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

import org.openardf.radiooracle.shared.printing.FinishTicketPlainTextFormatter

data class DesktopPrinterTarget(
    val name: String,
    val isDefault: Boolean,
    val supportsRawEscPos: Boolean = false,
    val isLikelyThermal: Boolean = false
)

data class DesktopPrintResult(
    val printerName: String,
    val plainText: String
) {
    fun summary(): String = "Printed finish ticket to $printerName."
}

interface DesktopPrinterBackend {
    fun listPrinters(): List<DesktopPrinterTarget>
    fun printPlainText(printerName: String?, text: String): String

    fun printTicket(
        printerName: String?,
        markedUpText: String,
        plainText: String,
        charactersPerLine: Int
    ): String =
        printPlainText(printerName, plainText)
}

/** Desktop finish-ticket printer facade. Transport-specific backends stay outside shared ticket rendering. */
class DesktopTicketPrinter(
    private val backend: DesktopPrinterBackend = JavaxDesktopPrinterBackend()
) {
    fun listPrinters(): List<DesktopPrinterTarget> =
        backend.listPrinters()

    fun printFinishTicket(
        markedUpTicketText: String,
        printerName: String? = null,
        charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE
    ): DesktopPrintResult {
        val plainText = FinishTicketPlainTextFormatter.format(markedUpTicketText, charactersPerLine)
        val selectedPrinterName = backend.printTicket(
            printerName,
            markedUpTicketText,
            plainText,
            charactersPerLine
        )
        return DesktopPrintResult(selectedPrinterName, plainText)
    }

    companion object {
        private const val DEFAULT_CHARACTERS_PER_LINE = 32
    }
}
