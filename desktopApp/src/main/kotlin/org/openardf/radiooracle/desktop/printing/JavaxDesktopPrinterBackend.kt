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

import java.awt.print.PrinterJob
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.print.DocFlavor
import javax.print.PrintService
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc

class JavaxDesktopPrinterBackend(
    private val printPreference: DesktopPrintPreference = DesktopPrintPreference.fromEnvironment()
) : DesktopPrinterBackend {
    override fun listPrinters(): List<DesktopPrinterTarget> {
        val defaultPrinter = PrintServiceLookup.lookupDefaultPrintService()
        return compatiblePrintServices()
            .map { service ->
                DesktopPrinterTarget(
                    name = service.name,
                    isDefault = service.name == defaultPrinter?.name,
                    supportsRawEscPos = service.supportsRawEscPos(),
                    isLikelyThermal = isLikelyThermalPrinterName(service.name)
                )
            }
            .sortedWith(compareByDescending<DesktopPrinterTarget> { it.isDefault }.thenBy { it.name })
    }

    override fun printPlainText(printerName: String?, text: String): String {
        val service = findPrinter(printerName)
        val mode = selectDesktopPrintMode(
            isFlavorSupported = { flavor -> service.isDocFlavorSupported(flavor) },
            preferEscPos = false
        )
            ?: error("System printer '${service.name}' does not support Radio-Oracle ticket output.")
        require(mode != DesktopPrintMode.ESC_POS) {
            "System printer '${service.name}' only supports raw printer data."
        }
        submit(service, mode, markedUpText = "", plainText = text)
        return service.name
    }

    override fun printTicket(
        printerName: String?,
        markedUpText: String,
        plainText: String,
        charactersPerLine: Int
    ): String {
        val service = findPrinter(printerName)
        val rawEscPosSupported = service.supportsRawEscPos()
        val preferEscPos = when (printPreference) {
            DesktopPrintPreference.AUTO -> rawEscPosSupported && isLikelyThermalPrinterName(service.name)
            DesktopPrintPreference.SYSTEM -> false
            DesktopPrintPreference.ESC_POS -> {
                check(rawEscPosSupported) {
                    "System printer '${service.name}' does not accept raw ESC/POS data."
                }
                true
            }
        }
        val mode = selectDesktopPrintMode(
            isFlavorSupported = { flavor -> service.isDocFlavorSupported(flavor) },
            preferEscPos = preferEscPos
        ) ?: error("System printer '${service.name}' does not support Radio-Oracle ticket output.")
        check(printPreference != DesktopPrintPreference.SYSTEM || mode != DesktopPrintMode.ESC_POS) {
            "System printer '${service.name}' only accepts raw printer data."
        }
        submit(service, mode, markedUpText, plainText, charactersPerLine)
        return service.name
    }

    private fun submit(
        service: PrintService,
        mode: DesktopPrintMode,
        markedUpText: String,
        plainText: String,
        charactersPerLine: Int = DEFAULT_CHARACTERS_PER_LINE
    ) {
        if (mode == DesktopPrintMode.PRINTABLE) {
            printWithNativeGraphics(service, plainText)
            return
        }

        val doc = when (mode) {
            DesktopPrintMode.ESC_POS -> {
                val bytes = DesktopEscPosTicketEncoder.encode(markedUpText, charactersPerLine)
                when {
                    service.isDocFlavorSupported(rawByteArrayFlavor) ->
                        SimpleDoc(bytes, rawByteArrayFlavor, null)
                    service.isDocFlavorSupported(rawInputStreamFlavor) ->
                        SimpleDoc(ByteArrayInputStream(bytes), rawInputStreamFlavor, null)
                    else -> error("System printer '${service.name}' stopped accepting raw ESC/POS data.")
                }
            }

            DesktopPrintMode.PLAIN_TEXT -> {
                val bytes = plainText.toByteArray(StandardCharsets.UTF_8)
                SimpleDoc(ByteArrayInputStream(bytes), textFlavor, null)
            }

            DesktopPrintMode.PRINTABLE -> error("Printable jobs use the native graphics path.")
        }
        service.createPrintJob().print(doc, null)
    }

    private fun printWithNativeGraphics(service: PrintService, plainText: String) {
        val job = PrinterJob.getPrinterJob()
        job.printService = service
        val pageFormat = desktopPrintablePageFormat(job.defaultPage())
        job.setPrintable(DesktopPlainTextPrintable(plainText), pageFormat)
        job.print()
    }

    private fun findPrinter(printerName: String?): PrintService {
        val services = compatiblePrintServices()
        if (services.isEmpty()) {
            error("No compatible system printers were found.")
        }
        if (printerName.isNullOrBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService()
                ?.takeIf { defaultPrinter -> services.any { it.name == defaultPrinter.name } }
                ?: services.first()
        }
        return services.firstOrNull { it.name == printerName }
            ?: error("System printer '$printerName' was not found.")
    }

    private fun compatiblePrintServices(): List<PrintService> =
        PrintServiceLookup.lookupPrintServices(null, null)
            .filter { service ->
                selectDesktopPrintMode(
                    isFlavorSupported = { flavor -> service.isDocFlavorSupported(flavor) }
                ) != null
            }

    private companion object {
        val textFlavor: DocFlavor = DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8
        val printableFlavor: DocFlavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE
        val rawByteArrayFlavor: DocFlavor = DocFlavor.BYTE_ARRAY.AUTOSENSE
        val rawInputStreamFlavor: DocFlavor = DocFlavor.INPUT_STREAM.AUTOSENSE
        const val DEFAULT_CHARACTERS_PER_LINE = 32
    }
}

internal enum class DesktopPrintMode {
    ESC_POS,
    PLAIN_TEXT,
    PRINTABLE
}

enum class DesktopPrintPreference {
    AUTO,
    SYSTEM,
    ESC_POS;

    companion object {
        fun fromEnvironment(value: String? = System.getenv("RADIO_ORACLE_DESKTOP_PRINT_MODE")): DesktopPrintPreference =
            when (value?.trim()?.lowercase()) {
                null, "", "auto" -> AUTO
                "system", "plain", "printable" -> SYSTEM
                "escpos", "esc-pos", "thermal", "raw" -> ESC_POS
                else -> error(
                    "RADIO_ORACLE_DESKTOP_PRINT_MODE must be auto, system, or escpos; was '$value'."
                )
            }
    }
}

internal fun selectDesktopPrintMode(
    preferEscPos: Boolean = false,
    isFlavorSupported: (DocFlavor) -> Boolean
): DesktopPrintMode? =
    when {
        preferEscPos && supportsRawEscPos(isFlavorSupported) -> DesktopPrintMode.ESC_POS
        isFlavorSupported(DocFlavor.INPUT_STREAM.TEXT_PLAIN_UTF_8) -> DesktopPrintMode.PLAIN_TEXT
        isFlavorSupported(DocFlavor.SERVICE_FORMATTED.PRINTABLE) -> DesktopPrintMode.PRINTABLE
        supportsRawEscPos(isFlavorSupported) -> DesktopPrintMode.ESC_POS
        else -> null
    }

internal fun isLikelyThermalPrinterName(name: String): Boolean {
    val normalized = name.lowercase()
    return listOf(
        "thermal",
        "receipt",
        "esc/pos",
        "escpos",
        "pos-",
        "pos ",
        "xprinter",
        "munbyn",
        "bixolon",
        "rongta",
        "goojprt",
        "zjiang",
        "star tsp",
        "epson tm-",
        "epson tm_",
        "citizen ct-"
    ).any(normalized::contains)
}

private fun PrintService.supportsRawEscPos(): Boolean =
    supportsRawEscPos { flavor -> isDocFlavorSupported(flavor) }

private fun supportsRawEscPos(isFlavorSupported: (DocFlavor) -> Boolean): Boolean =
    isFlavorSupported(DocFlavor.BYTE_ARRAY.AUTOSENSE) ||
        isFlavorSupported(DocFlavor.INPUT_STREAM.AUTOSENSE)
