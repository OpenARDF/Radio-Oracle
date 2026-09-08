package org.openardf.radiooracle.shared.sportident

import kotlin.test.*

class SportIdentCardCommandDiagnosticsTest {
    private val command = 0xef.toByte()
    private fun reply() = SportIdentProtocol.buildExtendedMessage(command, ByteArray(131) { 0x41 })
        .dropWhile { it != SportIdentProtocol.STX }.toByteArray()

    @Test fun splitPacketTracePreservesChunksTimingAndValidation() {
        val frame = reply()
        val chunks = mutableListOf(frame.copyOfRange(0, 64), frame.copyOfRange(64, frame.size))
        var clock = 100L
        var trace = emptyList<String>()
        val reader = SportIdentCardCommandReader(
            writeCommand = { _, _ -> true },
            readChunk = { clock += 7; chunks.removeAt(0) },
            sleepMillis = {}, nowMillis = { clock }, diagnostics = { trace = it })
        val result = reader.read(command, byteArrayOf(0), frame.size)
        assertContentEquals(frame, result.reply)
        assertEquals(1, result.attempts.size)
        val rx = trace.filter { " RX " in it }
        assertEquals(2, rx.size)
        assertTrue(rx[0].startsWith("+7ms RX bytes=64"))
        assertTrue(rx[0].endsWith(frame.copyOfRange(0, 64).diagnosticHex()))
        assertTrue(rx[1].endsWith(frame.copyOfRange(64, frame.size).diagnosticHex()))
        assertTrue(trace.any { "payload=00 expectedReplyBytes=${frame.size}" in it })
        assertTrue(trace.any { "crcValid=true" in it })
        assertTrue(trace.any { "outcome=OK" in it })
    }

    @Test fun invalidCrcAndRetryKeepTheirOriginalTiming() {
        val frame = reply()
        val bad = frame.copyOf().apply { this[size - 2] = (this[size - 2].toInt() xor 1).toByte() }
        var clock = 0L
        val chunks = mutableListOf(bad, frame)
        var trace = emptyList<String>()
        val result = SportIdentCardCommandReader(
            writeCommand = { _, _ -> true }, readChunk = { clock += 15; chunks.removeAt(0) },
            sleepMillis = { clock += it }, nowMillis = { clock }, diagnostics = { trace = it })
            .read(command, byteArrayOf(0), frame.size)
        assertContentEquals(frame, result.reply)
        assertEquals(2, result.attempts.size)
        assertTrue(trace.any { it.startsWith("+15ms RESULT attempt=1 outcome=INVALID_CRC") })
        assertTrue(trace.any { it.startsWith("+115ms TX attempt=2") })
    }

    @Test fun incompleteResponseIncludesRemainingBytesAndDeadline() {
        var clock = 0L
        var read = 0
        var trace = emptyList<String>()
        val partial = reply().copyOfRange(0, 64)
        SportIdentCardCommandReader(
            writeCommand = { _, _ -> true }, readChunk = { timeout ->
                if (read++ == 0) { clock++; partial } else { clock += timeout; byteArrayOf() }
            }, sleepMillis = {}, nowMillis = { clock }, maxAttempts = 1, diagnostics = { trace = it })
            .read(command, byteArrayOf(0), 137)
        assertTrue(trace.any { it.startsWith("+2000ms TIMEOUT bufferedBytes=64 bufferedHex=") })
        assertTrue(trace.any { "outcome=INVALID_FRAME" in it })
        assertTrue(trace.last().contains("emptyReads=1"))
    }

    @Test fun exceptionStillFlushesPartialTrafficAndIsRethrown() {
        val partial = reply().take(10).toByteArray()
        var reads = 0
        var trace = emptyList<String>()
        val error = assertFailsWith<IllegalStateException> {
            SportIdentCardCommandReader(writeCommand = { _, _ -> true }, readChunk = {
                if (reads++ == 0) partial else error("USB disconnected")
            }, sleepMillis = {}, nowMillis = { 0 }, diagnostics = { trace = it })
                .read(command, null, 137)
        }
        assertEquals("USB disconnected", error.message)
        assertTrue(trace.any { "RX bytes=10" in it })
        assertTrue(trace.any { "EXCEPTION" in it && "USB disconnected" in it })
    }

    @Test fun fullOrBrokenLogSinkCannotChangeSuccessfulRead() {
        val frame = reply()
        val result = SportIdentCardCommandReader(writeCommand = { _, _ -> true },
            readChunk = { frame }, sleepMillis = {}, nowMillis = { 0 },
            diagnostics = { error("Disk full") }).read(command, null, frame.size)
        assertContentEquals(frame, result.reply)
    }

    @Test fun floodCaptureHasExplicitBoundsAndRetainsFinalOutcome() {
        val trace = SportIdentCommandTrace { 0 }
        repeat(100) { trace.received(ByteArray(5000), 2000, 0) }
        trace.record("RESULT outcome=NO_COMPLETE_REPLY")
        val lines = trace.finish()
        assertTrue(lines.sumOf { it.length } < 68_000)
        assertTrue(lines.any { "truncated 904 bytes" in it })
        assertFalse(lines.last().contains("omittedEvents=0"))
        assertTrue(lines.last().contains("outcome=NO_COMPLETE_REPLY"))
    }
}
