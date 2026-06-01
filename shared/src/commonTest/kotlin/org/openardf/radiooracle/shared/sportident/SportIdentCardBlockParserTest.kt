package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SportIdentCardBlockParserTest {
    @Test
    fun parsesSi6CardBlockWithStationPrefix() {
        val data = ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { it.toByte() }
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SI_CARD6,
                    data = byteArrayOf(0x00, 0x11, 0x00) + data
                ),
                commandFilter = SportIdentProtocol.GET_SI_CARD6
            )
        )

        val block = assertNotNull(SportIdentCardBlockParser.si6Block(6, frame))

        assertEquals(6, block.blockNumber)
        assertContentEquals(data, block.data)
    }

    @Test
    fun parsesSi8Or9OrSiacCardBlock() {
        val data = ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { it.toByte() }
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    data = data
                ),
                commandFilter = SportIdentProtocol.GET_SI_CARD8_9_SIAC
            )
        )

        val block = assertNotNull(SportIdentCardBlockParser.si8Or9OrSiacBlock(0, frame))

        assertEquals(0, block.blockNumber)
        assertContentEquals(data, block.data)
    }

    @Test
    fun parsesSi8Or9OrSiacCardBlockWithStationPrefix() {
        val data = ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { it.toByte() }
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    data = byteArrayOf(0x00, 0x11, 0x00) + data
                ),
                commandFilter = SportIdentProtocol.GET_SI_CARD8_9_SIAC
            )
        )

        val block = assertNotNull(SportIdentCardBlockParser.si8Or9OrSiacBlock(0, frame))

        assertEquals(0, block.blockNumber)
        assertContentEquals(data, block.data)
    }

    @Test
    fun rejectsUnexpectedBlockLength() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    data = byteArrayOf(0x01)
                ),
                commandFilter = SportIdentProtocol.GET_SI_CARD8_9_SIAC
            )
        )

        assertNull(SportIdentCardBlockParser.si8Or9OrSiacBlock(0, frame))
    }
}
