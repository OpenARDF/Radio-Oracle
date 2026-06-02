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
