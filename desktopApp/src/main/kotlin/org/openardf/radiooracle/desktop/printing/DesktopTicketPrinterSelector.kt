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

object DesktopTicketPrinterSelector {
    fun selectPrinterName(
        printers: List<DesktopPrinterTarget>,
        requestedName: String? = System.getenv("RADIO_ORACLE_PRINTER"),
        preferredName: String = DEFAULT_PREFERRED_PRINTER_NAME,
        ignoredNames: Set<String> = DEFAULT_IGNORED_PRINTER_NAMES
    ): String? {
        val requested = requestedName?.takeUnless { it.isBlank() }
        if (requested != null) {
            return requested
        }

        printers.firstOrNull { it.name == preferredName }?.let { return it.name }
        printers.firstOrNull { it.isDefault && it.name !in ignoredNames }?.let { return it.name }
        return printers.firstOrNull { it.name !in ignoredNames }?.name
    }

    const val DEFAULT_PREFERRED_PRINTER_NAME = "EPSON ET-2720 Series"
    val DEFAULT_IGNORED_PRINTER_NAMES = setOf("DYMO LabelManager 280")
}
