package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

class DesktopSportIdentCaptureAnalyzerTest {
    @Test
    fun parsesHexCopiedFromLogs() {
        assertArrayEquals(
            byteArrayOf(0x02, 0xF0.toByte(), 0x03),
            DesktopSportIdentCaptureAnalyzer.hexToBytes("02 f0 03")
        )
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x02, 0x06, 0x03),
            DesktopSportIdentCaptureAnalyzer.hexToBytes("0xff, 0x02, 0x06, 0x03")
        )
    }

    @Test
    fun extractsMultipleFramesAndKeepsCrcStatus() {
        val probe = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val info = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )

        val frames = DesktopSportIdentCaptureAnalyzer.framesFrom(byteArrayOf(0x55) + probe + info)

        assertEquals(2, frames.size)
        assertEquals(SportIdentProtocol.PROBE_COMMAND, frames[0].command)
        assertEquals(true, frames[0].crcValid)
        assertEquals(SportIdentProtocol.GET_SYSTEM_INFO, frames[1].command)
    }

    @Test
    fun describesStandardAckAndExtendedFrame() {
        val ack = DesktopSportIdentCaptureAnalyzer.framesFrom(SportIdentProtocol.buildAckMessage()).single()
        val probe = DesktopSportIdentCaptureAnalyzer.framesFrom(
            SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.PROBE_COMMAND,
                data = byteArrayOf(0x4d)
            )
        ).single()

        assertEquals("#1 command=0x06 ACK extended=false crc=n/a dataLen=0", DesktopSportIdentCaptureAnalyzer.describeFrame(1, ack))
        assertTrue(DesktopSportIdentCaptureAnalyzer.describeFrame(2, probe).contains("command=0xF0 PROBE"))
        assertTrue(DesktopSportIdentCaptureAnalyzer.describeFrame(2, probe).contains("data=4D"))
    }
}
