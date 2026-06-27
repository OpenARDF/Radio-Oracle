/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
