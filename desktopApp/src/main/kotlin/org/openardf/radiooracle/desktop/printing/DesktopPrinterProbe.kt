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

fun main(args: Array<String>) {
    val requestedPrinter = args.firstOrNull()?.takeUnless { it.isBlank() }
        ?: System.getenv("RADIO_ORACLE_PRINTER")
    val shouldPrint = System.getenv("RADIO_ORACLE_PRINT_TEST") == "1"
    val printer = DesktopTicketPrinter()
    val printers = printer.listPrinters()

    println("Radio-Oracle desktop printer probe")
    println("Detected system printers:")
    if (printers.isEmpty()) {
        println("- none")
    } else {
        printers.forEach { target ->
            val defaultText = if (target.isDefault) " default" else ""
            println("- ${target.name}$defaultText")
        }
    }

    if (!shouldPrint) {
        println("Set RADIO_ORACLE_PRINT_TEST=1 to submit a plain-text test ticket.")
        return
    }

    val printerName = DesktopTicketPrinterSelector.selectPrinterName(
        printers = printers,
        requestedName = requestedPrinter
    )
    val result = printer.printFinishTicket(
        markedUpTicketText = testTicketText,
        printerName = printerName
    )
    println(result.summary())
}

private val testTicketText = """
    [C]<b>Radio-Oracle</b>
    [L]
    [L]Desktop printer test
    [L]Ticket transport: system printer
    
    [L]Start[R]10:00:00[R] 
    [L]1 (31)OK[R]10:05:00[R]00:05:00
    [L]Finish[R]10:10:00[R]00:10:00
    
    [R]<b>Run time: 00:10:00 OK</b>
    [R]1 Controls
""".trimIndent() + "\n"
