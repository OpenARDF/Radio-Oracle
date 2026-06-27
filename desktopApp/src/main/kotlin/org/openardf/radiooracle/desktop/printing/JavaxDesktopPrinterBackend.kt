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
