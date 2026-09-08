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

    @Test
    fun incompleteCardReplyNeverExposesMessagesInsideItsPayload() {
        val nestedMessages = listOf(
            byteArrayOf(SportIdentProtocol.STX, 45, 65, SportIdentProtocol.ETX),
            SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.SI_CARD_REMOVED, ByteArray(6))
                .drop(1).toByteArray()
        )
        for (nested in nestedMessages) {
            val message = cardReplyContaining(nested)
            for (length in 1 until message.size) {
                val partial = message.copyOfRange(0, length)
                for (requireCrc in listOf(false, true)) {
                    assertNull(
                        SportIdentFrameParser.firstFrame(partial, requireValidCrc = requireCrc),
                        "Partial reply length=$length requireCrc=$requireCrc"
                    )
                    assertNull(SportIdentFrameParser.firstFrame(
                        partial, commandFilter = nested[1], requireValidCrc = requireCrc
                    ))
                }
            }
            assertContentEquals(message, assertNotNull(SportIdentFrameParser.firstFrame(message)).raw)
        }
    }

    @Test
    fun invalidCrcSkipsTheWholeFrameBeforeFindingTheNextMessage() {
        val invalid = cardReplyContaining(byteArrayOf(2, 45, 65, 3)).also {
            it[it.lastIndex - 1] = (it[it.lastIndex - 1].toInt() xor 1).toByte()
        }
        val following = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(77))
            .drop(1).toByteArray()

        assertNull(SportIdentFrameParser.firstFrame(invalid))
        assertContentEquals(following, assertNotNull(SportIdentFrameParser.firstFrame(invalid + following)).raw)
        val diagnostic = assertNotNull(SportIdentFrameParser.firstFrame(invalid, requireValidCrc = false))
        assertEquals(false, diagnostic.crcValid)
        assertContentEquals(invalid, diagnostic.raw)
    }

    @Test
    fun malformedTerminatorCanResynchronizeToFollowingFrame() {
        val invalid = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(77))
            .also { it[it.lastIndex] = 0 }
        val following = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.GET_SYSTEM_INFO, byteArrayOf(77))
        val frame = assertNotNull(SportIdentFrameParser.firstFrame(invalid + following))
        assertEquals(SportIdentProtocol.GET_SYSTEM_INFO, frame.command)
    }

    @Test
    fun standardFrameWaitsForUnescapedTerminator() {
        val message = byteArrayOf(2, 45, 16, 2, 16, 3, 65, 3)
        for (length in 1 until message.size) {
            assertNull(SportIdentFrameParser.firstFrame(message.copyOfRange(0, length)))
        }
        assertContentEquals(message, assertNotNull(SportIdentFrameParser.firstFrame(message)).raw)
    }

    @Test
    fun commandFilterSkipsCompleteMessagesButWaitsForPartialMessages() {
        val first = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(77))
        val cardReply = cardReplyContaining(byteArrayOf(2, 45, 65, 3))
        assertNull(SportIdentFrameParser.firstFrame(
            first + cardReply.copyOfRange(0, 64), commandFilter = SportIdentProtocol.GET_SI_CARD8_9_SIAC
        ))
        assertContentEquals(cardReply, assertNotNull(SportIdentFrameParser.firstFrame(
            first + cardReply, commandFilter = SportIdentProtocol.GET_SI_CARD8_9_SIAC
        )).raw)
    }

    private fun cardReplyContaining(nested: ByteArray): ByteArray {
        val data = ByteArray(131) { 65 }
        nested.copyInto(data, destinationOffset = 20)
        return SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.GET_SI_CARD8_9_SIAC, data)
            .drop(1).toByteArray()
    }
}
