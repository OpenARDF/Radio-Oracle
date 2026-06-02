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
