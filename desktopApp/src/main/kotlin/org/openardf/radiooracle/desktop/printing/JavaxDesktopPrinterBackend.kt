package org.openardf.radiooracle.desktop.printing

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

class JavaxDesktopPrinterBackend : DesktopPrinterBackend {
    override fun listPrinters(): List<DesktopPrinterTarget> {
        val defaultPrinter = PrintServiceLookup.lookupDefaultPrintService()
        return PrintServiceLookup.lookupPrintServices(textFlavor, null)
            .map { service ->
                DesktopPrinterTarget(
                    name = service.name,
                    isDefault = service.name == defaultPrinter?.name
                )
            }
            .sortedWith(compareByDescending<DesktopPrinterTarget> { it.isDefault }.thenBy { it.name })
    }

    override fun printPlainText(printerName: String?, text: String): String {
        val service = findPrinter(printerName)
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val doc = SimpleDoc(ByteArrayInputStream(bytes), textFlavor, null)
        service.createPrintJob().print(doc, null)
        return service.name
    }

    private fun findPrinter(printerName: String?): PrintService {
        val services = PrintServiceLookup.lookupPrintServices(textFlavor, null).toList()
        if (services.isEmpty()) {
            error("No system printers that accept plain text were found.")
        }
        if (printerName.isNullOrBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService()
                ?.takeIf { defaultPrinter -> services.any { it.name == defaultPrinter.name } }
                ?: services.first()
        }
        return services.firstOrNull { it.name == printerName }
            ?: error("System printer '$printerName' was not found.")
    }

    private companion object {
        val textFlavor: DocFlavor = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8
    }
}
