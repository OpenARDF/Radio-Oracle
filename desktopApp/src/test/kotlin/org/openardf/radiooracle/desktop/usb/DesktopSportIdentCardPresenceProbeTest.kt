package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentCardPresenceProbeTest {
    private val expected = ByteArray(128).also { it[27] = 0x42 }

    @Test
    fun matchingBlockIsReportedOnlyForAnExactReadOnlyReply() {
        val port = FakePort(listOf(reply(expected)))

        assertEquals(DesktopSportIdentCardPresenceResult.MATCHING_BLOCK, probe().check(port, expected))
        assertEquals(1, port.writes.size)
        assertArrayEquals(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.GET_SI_CARD8_9_SIAC, byteArrayOf(0)), port.writes.single())
    }

    @Test
    fun changedBlockNakAndSilenceNeverCountAsPresence() {
        val changed = expected.copyOf().also { it[60] = 1 }
        assertEquals(DesktopSportIdentCardPresenceResult.DIFFERENT_BLOCK,
            probe().check(FakePort(listOf(reply(changed))), expected))
        assertEquals(DesktopSportIdentCardPresenceResult.NEGATIVE_ACKNOWLEDGEMENT,
            probe().check(FakePort(listOf(byteArrayOf(SportIdentProtocol.NAK))), expected))
        assertEquals(DesktopSportIdentCardPresenceResult.NO_REPLY,
            probe().check(FakePort(emptyList()), expected))
    }

    @Test
    fun incompleteBlockAndClosedPortAreRejected() {
        val port = FakePort(listOf(reply(expected)))
        assertThrows(IllegalArgumentException::class.java) { probe().check(port, ByteArray(127)) }
        port.close()
        assertThrows(IllegalStateException::class.java) { probe().check(port, expected) }
        assertEquals(0, port.writes.size)
    }

    private fun probe(): DesktopSportIdentCardPresenceProbe {
        var now = 0L
        return DesktopSportIdentCardPresenceProbe(DesktopSportIdentStationCommandClient(
            readTimeoutMs = 50, nowMillis = { ++now }
        ))
    }

    private fun reply(block: ByteArray): ByteArray = SportIdentProtocol.buildExtendedMessage(
        SportIdentProtocol.GET_SI_CARD8_9_SIAC, byteArrayOf(0, 0, 0) + block)

    private class FakePort(chunks: List<ByteArray>) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)
        override val info = DesktopSerialPortInfo("/dev/cu.fake", "Fake SPORTident",
            SportIdentUsbDevice.VENDOR_ID, SportIdentUsbDevice.PRODUCT_ID, "fake")
        override var isOpen = true
        val writes = mutableListOf<ByteArray>()
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int) = true
        override fun close() { isOpen = false }
        override fun write(bytes: ByteArray): Int {
            writes += bytes.copyOf()
            return bytes.size
        }
        override fun read(maxBytes: Int) = pending.removeFirstOrNull() ?: ByteArray(0)
    }
}
