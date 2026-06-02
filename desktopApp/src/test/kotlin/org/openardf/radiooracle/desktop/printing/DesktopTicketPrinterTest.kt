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

    private class FakePrinterBackend(
        private val printers: List<DesktopPrinterTarget>
    ) : DesktopPrinterBackend {
        var printedPrinterName: String? = null
        var printedText: String? = null

        override fun listPrinters(): List<DesktopPrinterTarget> =
            printers

        override fun printPlainText(printerName: String?, text: String): String {
            printedPrinterName = printerName
            printedText = text
            return printerName ?: printers.first().name
        }
    }
}
