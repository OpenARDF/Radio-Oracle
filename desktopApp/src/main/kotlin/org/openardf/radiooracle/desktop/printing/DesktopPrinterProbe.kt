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

    val result = printer.printFinishTicket(
        markedUpTicketText = testTicketText,
        printerName = requestedPrinter
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
