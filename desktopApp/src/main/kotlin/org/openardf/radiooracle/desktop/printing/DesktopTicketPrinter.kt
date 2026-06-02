package org.openardf.radiooracle.desktop.printing

import org.openardf.radiooracle.shared.printing.FinishTicketPlainTextFormatter

data class DesktopPrinterTarget(
    val name: String,
    val isDefault: Boolean
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
        val selectedPrinterName = backend.printPlainText(printerName, plainText)
        return DesktopPrintResult(selectedPrinterName, plainText)
    }

    companion object {
        private const val DEFAULT_CHARACTERS_PER_LINE = 32
    }
}
