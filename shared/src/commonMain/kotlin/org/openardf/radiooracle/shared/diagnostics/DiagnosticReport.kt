package org.openardf.radiooracle.shared.diagnostics

object DiagnosticReport {
    const val PRIVACY_NOTICE = "Logs may contain SI card numbers, punch times, cardholder data stored on cards, and file or device details. Share them only with someone you trust."
    const val SHARE_MESSAGE = "Radio-Oracle diagnostic logs attached. Please describe what happened, the approximate time, and the SI card number (if relevant)."

    /** Deliberate allowlist: never attach databases, preferences, credentials, or race files. */
    fun entries(logs: Map<String, ByteArray>, platform: String, version: String,
                build: String, createdUtc: String): Map<String, ByteArray> {
        val selected = logs.filterKeys { it.matches(Regex("(?:debug|sportident)\\.log(?:\\.[1-3])?")) }
        val readme = buildString {
            appendLine("Radio-Oracle diagnostic report")
            appendLine("Version: $version")
            appendLine("Build (UTC): $build")
            appendLine("Platform: $platform")
            appendLine("Exported (UTC): $createdUtc")
            appendLine("Diagnostic format: 1; SPORTident parser: preserve incomplete frames")
            appendLine()
            appendLine(PRIVACY_NOTICE)
            appendLine("Includes retained operational logs and SPORTident packet traces. No event database or settings files are attached.")
            appendLine("Packet traces use per-read IDs and monotonic elapsed times; raw RX entries preserve chunk boundaries.")
            appendLine("Traces are bounded: END reports omitted events; oversized chunks explicitly report truncation.")
            appendLine("Please include what happened, the approximate time (and time zone), SI number, and station/card model.")
            appendLine()
            selected.forEach { (name, data) -> appendLine("$name: ${data.size} bytes") }
            if (selected.isEmpty()) appendLine("No retained logs were available.")
        }
        return mapOf("README.txt" to readme.encodeToByteArray()) + selected
    }
}
