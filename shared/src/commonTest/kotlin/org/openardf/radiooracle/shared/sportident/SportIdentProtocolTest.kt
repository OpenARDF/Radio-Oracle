package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SportIdentProtocolTest {
    @Test
    fun buildsExtendedProbeMessage() {
        val message = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )

        assertContentEquals(
            byteArrayOf(
                0xff.toByte(),
                0x02,
                0xf0.toByte(),
                0x01,
                0x4d,
                0x6d,
                0x0a,
                0x03
            ),
            message
        )
    }

    @Test
    fun calculatesSportIdentCrcForProbePayload() {
        assertEquals(
            0x6d0a,
            SportIdentProtocol.calculateCrc(3, byteArrayOf(0xf0.toByte(), 0x01, 0x4d))
        )
    }

    @Test
    fun buildsAckMessage() {
        assertContentEquals(
            byteArrayOf(
                SportIdentProtocol.WAKEUP,
                SportIdentProtocol.STX,
                SportIdentProtocol.ACK,
                SportIdentProtocol.ETX
            ),
            SportIdentProtocol.buildAckMessage()
        )
    }
}
