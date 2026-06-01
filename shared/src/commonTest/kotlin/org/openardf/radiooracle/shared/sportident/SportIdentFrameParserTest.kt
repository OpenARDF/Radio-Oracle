package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportIdentFrameParserTest {
    @Test
    fun parsesKnownProbeReplyFrame() {
        val reply = byteArrayOf(
            0x02,
            0xf0.toByte(),
            0x03,
            0x00,
            0x11,
            0x4d,
            0x8d.toByte(),
            0x72,
            0x03
        )

        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(reply, commandFilter = SportIdentProtocol.PROBE_COMMAND)
        )

        assertEquals(SportIdentProtocol.PROBE_COMMAND, frame.command)
        assertTrue(frame.extended)
        assertEquals(true, frame.crcValid)
        assertContentEquals(byteArrayOf(0x00, 0x11, 0x4d), frame.data)
        assertContentEquals(reply, frame.raw)
    }

    @Test
    fun skipsWakeupAndZeroBytesBeforeFrame() {
        val message = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val bytes = byteArrayOf(0x00, SportIdentProtocol.WAKEUP) + message

        val frame = assertNotNull(SportIdentFrameParser.firstFrame(bytes))

        assertEquals(SportIdentProtocol.PROBE_COMMAND, frame.command)
        assertContentEquals(byteArrayOf(0x4d), frame.data)
    }

    @Test
    fun rejectsExtendedFrameWithInvalidCrcByDefault() {
        val message = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val invalid = message.copyOf()
        invalid[invalid.size - 3] = 0x00

        assertNull(SportIdentFrameParser.firstFrame(invalid))
    }

    @Test
    fun canReturnExtendedFrameWithInvalidCrcForDiagnostics() {
        val message = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val invalid = message.copyOf()
        invalid[invalid.size - 3] = 0x00

        val frame = assertNotNull(SportIdentFrameParser.firstFrame(invalid, requireValidCrc = false))

        assertFalse(frame.crcValid ?: true)
    }
}
