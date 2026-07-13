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

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopTicketPrinterTest {
    @Test
    fun listsPrintersFromBackend() {
        val printer = DesktopTicketPrinter(
            FakePrinterBackend(
                printers = listOf(
                    DesktopPrinterTarget("EPSON_ET_2720_Series", isDefault = true)
                )
            )
        )

        assertEquals(
            listOf(DesktopPrinterTarget("EPSON_ET_2720_Series", isDefault = true)),
            printer.listPrinters()
        )
    }

    @Test
    fun printsPlainTextFinishTicketThroughBackend() {
        val backend = FakePrinterBackend(
            printers = listOf(DesktopPrinterTarget("EPSON_ET_2720_Series", isDefault = true))
        )
        val printer = DesktopTicketPrinter(backend)

        val result = printer.printFinishTicket(
            markedUpTicketText = "[C]<b>Radio-Oracle</b>\n[L]Runner[R]00:10:00\n",
            printerName = "EPSON_ET_2720_Series",
            charactersPerLine = 24
        )

        assertEquals("EPSON_ET_2720_Series", result.printerName)
        assertEquals("      Radio-Oracle\nRunner          00:10:00\n", result.plainText)
        assertEquals("EPSON_ET_2720_Series", backend.printedPrinterName)
        assertEquals("[C]<b>Radio-Oracle</b>\n[L]Runner[R]00:10:00\n", backend.printedMarkedUpText)
        assertEquals(24, backend.printedCharactersPerLine)
        assertEquals(result.plainText, backend.printedText)
    }

    @Test
    fun selectsPreferredEpsonPrinterBeforeIgnoredSystemPrinters() {
        val selected = DesktopTicketPrinterSelector.selectPrinterName(
            printers = listOf(
                DesktopPrinterTarget("DYMO LabelManager 280", isDefault = true),
                DesktopPrinterTarget("EPSON ET-2720 Series", isDefault = false)
            ),
            requestedName = null
        )

        assertEquals("EPSON ET-2720 Series", selected)
    }

    @Test
    fun selectsRawThermalPrinterBeforePreferredOfficePrinter() {
        val selected = DesktopTicketPrinterSelector.selectPrinterName(
            printers = listOf(
                DesktopPrinterTarget("EPSON ET-2720 Series", isDefault = true),
                DesktopPrinterTarget(
                    "POS-80 USB Receipt Printer",
                    isDefault = false,
                    supportsRawEscPos = true,
                    isLikelyThermal = true
                )
            ),
            requestedName = null
        )

        assertEquals("POS-80 USB Receipt Printer", selected)
    }

    @Test
    fun honorsRequestedPrinterName() {
        val selected = DesktopTicketPrinterSelector.selectPrinterName(
            printers = listOf(DesktopPrinterTarget("EPSON ET-2720 Series", isDefault = true)),
            requestedName = "Operator Printer"
        )

        assertEquals("Operator Printer", selected)
    }

    @Test
    fun fallsBackToNonIgnoredDefaultPrinter() {
        val selected = DesktopTicketPrinterSelector.selectPrinterName(
            printers = listOf(
                DesktopPrinterTarget("Office Printer", isDefault = true),
                DesktopPrinterTarget("EPSON Backup", isDefault = false)
            ),
            requestedName = null
        )

        assertEquals("Office Printer", selected)
    }

    @Test
    fun reportsPrinterDiagnosticsWithSelectedTarget() {
        val diagnostics = DesktopPrinterDiagnostics.from(
            printers = listOf(
                DesktopPrinterTarget("DYMO LabelManager 280", isDefault = true),
                DesktopPrinterTarget("EPSON ET-2720 Series", isDefault = false)
            ),
            requestedPrinterName = null
        )

        assertEquals("EPSON ET-2720 Series", diagnostics.selectedPrinterName)
        assertEquals("Ready: EPSON ET-2720 Series", diagnostics.readinessText)
        assertEquals(
            listOf("DYMO LabelManager 280 (default)", "EPSON ET-2720 Series"),
            diagnostics.detectedPrinterNames
        )
    }

    @Test
    fun reportsPrinterDiagnosticsWhenNoTargetIsAvailable() {
        val diagnostics = DesktopPrinterDiagnostics.from(
            printers = emptyList(),
            requestedPrinterName = null
        )

        assertEquals(null, diagnostics.selectedPrinterName)
        assertEquals("No system printers detected", diagnostics.readinessText)
        assertEquals(emptyList<String>(), diagnostics.detectedPrinterNames)
    }

    private class FakePrinterBackend(
        private val printers: List<DesktopPrinterTarget>
    ) : DesktopPrinterBackend {
        var printedPrinterName: String? = null
        var printedText: String? = null
        var printedMarkedUpText: String? = null
        var printedCharactersPerLine: Int? = null

        override fun listPrinters(): List<DesktopPrinterTarget> =
            printers

        override fun printPlainText(printerName: String?, text: String): String {
            printedPrinterName = printerName
            printedText = text
            return printerName ?: printers.first().name
        }

        override fun printTicket(
            printerName: String?,
            markedUpText: String,
            plainText: String,
            charactersPerLine: Int
        ): String {
            printedMarkedUpText = markedUpText
            printedCharactersPerLine = charactersPerLine
            return printPlainText(printerName, plainText)
        }
    }
}
