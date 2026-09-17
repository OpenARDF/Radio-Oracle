package org.openardf.radiooracle.shared.files

/** CSV syntax shared by desktop, Android and report exports. */
object CsvCodec {
    fun row(fields: Iterable<Any?>, delimiter: Char = ','): String =
        fields.joinToString(delimiter.toString()) { field(it, delimiter) }

    fun field(value: Any?, delimiter: Char = ','): String {
        val text = value?.toString().orEmpty()
        return if (text.any { it == delimiter || it == '"' || it == '\r' || it == '\n' })
            "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    data class Record(val lineIndex: Int, val fields: List<String>, val error: String? = null)

    /** Reads logical records, including quoted newlines, retaining physical line numbers for errors. */
    fun records(text: String, delimiter: Char = ','): List<Record> {
        val records = mutableListOf<Record>()
        val fields = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var afterQuote = false
        var recordStarted = false
        var error: String? = null
        var line = 0
        var recordLine = 0
        var index = if (text.startsWith('\uFEFF')) 1 else 0
        fun finishRecord() {
            fields += cell.toString()
            if (recordStarted) records += Record(recordLine, fields.toList(), error)
            fields.clear()
            cell.clear()
            recordStarted = false
            afterQuote = false
            error = null
        }
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' && inQuotes && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' && inQuotes -> { inQuotes = false; afterQuote = true }
                ch == '"' && cell.isEmpty() && !afterQuote -> { inQuotes = true; recordStarted = true }
                ch == delimiter && !inQuotes -> {
                    fields += cell.toString()
                    cell.clear()
                    afterQuote = false
                    recordStarted = true
                }
                (ch == '\r' || ch == '\n') -> {
                    val crlf = ch == '\r' && text.getOrNull(index + 1) == '\n'
                    if (inQuotes) {
                        cell.append(ch)
                        if (crlf) cell.append('\n')
                    } else finishRecord()
                    if (crlf) index++
                    line++
                    if (!inQuotes) recordLine = line
                }
                else -> {
                    if (afterQuote || ch == '"') error = "Unexpected character outside quoted field"
                    cell.append(ch)
                    if (!ch.isWhitespace()) recordStarted = true
                }
            }
            index++
        }
        if (inQuotes) error = "Unclosed quoted field"
        finishRecord()
        return records
    }
}
