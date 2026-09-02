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

package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadFailure
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

class AndroidSportIdentCardCommandReaderTest {
    @Test
    fun joinsSplitReplyWithinOneOverallDeadline() {
        val reply = cardBlockReply()
        val fixture = Fixture(
            listOf(
                listOf(reply.copyOfRange(0, 47), reply.copyOfRange(47, reply.size))
            )
        )

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), reply.size)

        assertArrayEquals(reply, result.reply)
        assertEquals(1, result.attempts.size)
        assertEquals(null, result.attempts.single().failure)
    }

    @Test
    fun retriesNegativeAcknowledgementAndRecovers() {
        val reply = cardBlockReply()
        val fixture = Fixture(
            listOf(
                listOf(byteArrayOf(SportIdentProtocol.NAK)),
                listOf(reply)
            )
        )

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), reply.size)

        assertArrayEquals(reply, result.reply)
        assertEquals(2, fixture.writeCount)
        assertEquals(1, fixture.sleepCount)
        assertEquals(
            SportIdentCardReadFailure.NEGATIVE_ACKNOWLEDGEMENT,
            result.attempts.first().failure
        )
    }

    @Test
    fun retriesIncompleteFrameAndRecovers() {
        val reply = cardBlockReply()
        val fixture = Fixture(
            listOf(
                listOf(reply.copyOfRange(0, 19)),
                listOf(reply)
            )
        )

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), reply.size)

        assertArrayEquals(reply, result.reply)
        assertEquals(2, result.attempts.size)
        assertEquals(SportIdentCardReadFailure.INVALID_FRAME, result.attempts.first().failure)
    }

    @Test
    fun retriesInvalidCrcAndRecovers() {
        val reply = cardBlockReply()
        val invalidReply = reply.copyOf().also { it[it.lastIndex - 2] = (it[it.lastIndex - 2].toInt() xor 1).toByte() }
        val fixture = Fixture(listOf(listOf(invalidReply), listOf(reply)))

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), reply.size)

        assertArrayEquals(reply, result.reply)
        assertEquals(SportIdentCardReadFailure.INVALID_CRC, result.attempts.first().failure)
        assertEquals(2, result.attempts.size)
    }

    @Test
    fun stopsImmediatelyWhenCardIsRemoved() {
        val removal = SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.SI_CARD_REMOVED,
            ByteArray(6)
        ).copyOfRange(1, 13)
        val fixture = Fixture(listOf(listOf(removal), listOf(cardBlockReply())))

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), CARD_BLOCK_REPLY_BYTES)

        assertNull(result.reply)
        assertEquals(1, fixture.writeCount)
        assertEquals(0, fixture.sleepCount)
        assertEquals(SportIdentCardReadFailure.CARD_REMOVED, result.attempts.single().failure)
    }

    @Test
    fun cachesUnexpectedFrameAndContinuesWaitingForRequestedReply() {
        val unexpected = SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.GET_SYSTEM_INFO,
            byteArrayOf(1, 2, 3)
        ).dropWakeup()
        val reply = cardBlockReply()
        val fixture = Fixture(listOf(listOf(unexpected + reply)))

        val result = fixture.reader().read(COMMAND, byteArrayOf(0), reply.size)

        assertArrayEquals(reply, result.reply)
        assertEquals(1, fixture.cachedFrames.size)
        assertArrayEquals(unexpected, fixture.cachedFrames.single())
    }

    private class Fixture(attemptChunks: List<List<ByteArray>>) {
        private val pendingAttempts = ArrayDeque(attemptChunks.map(::ArrayDeque))
        private var activeChunks = ArrayDeque<ByteArray>()
        private var now = 0L
        val cachedFrames = mutableListOf<ByteArray>()
        var writeCount = 0
            private set
        var sleepCount = 0
            private set

        fun reader() = AndroidSportIdentCardCommandReader(
            writeCommand = { _, _ ->
                writeCount++
                activeChunks = pendingAttempts.removeFirstOrNull() ?: ArrayDeque()
                true
            },
            readChunk = { timeoutMillis ->
                activeChunks.removeFirstOrNull()?.also { now += 10 }
                    ?: ByteArray(0).also { now += timeoutMillis }
            },
            cacheUnexpectedFrame = cachedFrames::add,
            sleepMillis = {
                sleepCount++
                now += it
            },
            nowMillis = { now },
            attemptTimeoutMillis = 2_000,
            retryDelayMillis = 100,
            maxAttempts = 3
        )
    }

    private companion object {
        const val CARD_BLOCK_REPLY_BYTES = 137
        val COMMAND: Byte = SportIdentProtocol.GET_SI_CARD8_9_SIAC

        fun cardBlockReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(COMMAND, ByteArray(131)).dropWakeup()

        fun ByteArray.dropWakeup(): ByteArray = copyOfRange(1, size)
    }
}
