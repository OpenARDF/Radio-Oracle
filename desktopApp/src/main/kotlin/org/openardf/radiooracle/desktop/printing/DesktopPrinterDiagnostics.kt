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

data class DesktopPrinterDiagnostics(
    val selectedPrinterName: String?,
    val detectedPrinterNames: List<String>,
    val readinessText: String
) {
    companion object {
        fun from(
            printers: List<DesktopPrinterTarget>,
            requestedPrinterName: String? = System.getenv("RADIO_ORACLE_PRINTER")
        ): DesktopPrinterDiagnostics {
            val selectedPrinterName = DesktopTicketPrinterSelector.selectPrinterName(
                printers = printers,
                requestedName = requestedPrinterName
            )
            return DesktopPrinterDiagnostics(
                selectedPrinterName = selectedPrinterName,
                detectedPrinterNames = printers.map { target ->
                    if (target.isDefault) "${target.name} (default)" else target.name
                },
                readinessText = when {
                    selectedPrinterName != null -> "Ready: $selectedPrinterName"
                    printers.isEmpty() -> "No system printers detected"
                    else -> "No non-ignored system printer selected"
                }
            )
        }
    }
}
