package org.openardf.radiooracle.shared.sportident

/** Bounded, in-memory capture: serial I/O never waits for a file write per RX chunk. */
internal class SportIdentCommandTrace(private val nowMillis: () -> Long) {
    private val started = nowMillis()
    private val lines = mutableListOf<String>()
    private var characters = 0
    private var omitted = 0
    private var emptyReads = 0
    private var lastEvent = ""

    init { record("CLOCK monotonicMs=$started") }

    fun record(message: String) {
        val line = "+${nowMillis() - started}ms $message"
        lastEvent = line.take(1_024)
        if (characters + line.length <= 65_536) {
            lines += line
            characters += line.length
        } else {
            omitted++
        }
    }

    fun received(bytes: ByteArray, timeoutMillis: Long, durationMillis: Long) {
        if (bytes.isEmpty()) {
            emptyReads++
            return
        }
        record("RX bytes=${bytes.size} timeoutMs=$timeoutMillis durationMs=$durationMillis hex=${bytes.diagnosticHex()}")
    }

    fun flushTo(sink: ((List<String>) -> Unit)?) {
        try {
            sink?.invoke(finish())
        } catch (_: Exception) {
            // A full disk or unavailable log sink must not fail a card download.
        }
    }

    fun finish(): List<String> = lines +
        "+${nowMillis() - started}ms END emptyReads=$emptyReads omittedEvents=$omitted lastEvent=$lastEvent"
}

/** Each chunk is bounded independently; its original size is recorded alongside it. */
fun ByteArray.diagnosticHex(): String = take(4_096).joinToString(" ") {
    (it.toInt() and 0xff).toString(16).padStart(2, '0')
} + if (size > 4_096) " [truncated ${size - 4_096} bytes]" else ""
