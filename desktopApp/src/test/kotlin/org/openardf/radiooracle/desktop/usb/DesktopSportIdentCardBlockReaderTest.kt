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

package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentCardBlockReaderTest {
    @Test
    fun retriesRejectedSi5RequestAndCompletesDownload() {
        val port = ChunkedPort(
            listOf(
                insertedSi5Frame(CARD_NUMBER),
                byteArrayOf(SportIdentProtocol.NAK),
                si5Reply(CARD_NUMBER)
            )
        )
        val progress = mutableListOf<String>()
        val reader = reader(onProgress = progress::add)

        val download = reader.readFirstSupportedCardAfterInsertOnOpenPort(port)

        assertEquals(CARD_NUMBER, download.readout.siNumber)
        assertEquals(2, port.writtenCommands().count { it == SportIdentProtocol.GET_SI_CARD5 })
        assertEquals(1, port.writtenCommands().count { it == SportIdentProtocol.ACK })
        assertTrue(progress.any { it.contains("Recovered SI5 card payload on attempt 2") })
    }

    @Test
    fun doesNotRetryAfterCardRemoval() {
        val port = ChunkedPort(
            listOf(
                insertedSi5Frame(CARD_NUMBER),
                removedFrame(CARD_NUMBER),
                si5Reply(CARD_NUMBER)
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            reader().readFirstSupportedCardAfterInsertOnOpenPort(port)
        }

        assertTrue(error.message.orEmpty().contains("was removed"))
        assertEquals(1, port.writtenCommands().count { it == SportIdentProtocol.GET_SI_CARD5 })
    }

    private fun reader(onProgress: (String) -> Unit = {}) =
        DesktopSportIdentCardBlockReader(
            postAckSettleMs = 0,
            onProgress = onProgress,
            sleepMillis = {},
            nowMillis = advancingClock()
        )

    private class ChunkedPort(chunks: List<ByteArray>) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)
        private val writes = mutableListOf<ByteArray>()

        override val info = DesktopSerialPortInfo(
            systemPortPath = "/dev/cu.fake",
            descriptivePortName = "Fake SPORTident",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID,
            serialNumber = "fake"
        )
        override val isOpen: Boolean = true

        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean = true
        override fun close() = Unit

        override fun write(bytes: ByteArray): Int {
            writes += bytes
            return bytes.size
        }

        override fun read(maxBytes: Int): ByteArray =
            pending.removeFirstOrNull() ?: ByteArray(0)

        fun writtenCommands(): List<Byte> =
            writes.mapNotNull { bytes ->
                SportIdentFrameParser.firstFrame(bytes, requireValidCrc = false)?.command
                    ?: bytes.singleOrNull()
            }
    }

    private companion object {
        const val CARD_NUMBER = 234_567

        fun advancingClock(): () -> Long {
            var now = 0L
            return { ++now }
        }

        fun insertedSi5Frame(cardNumber: Int): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                SportIdentProtocol.SI_CARD5,
                ByteArray(6).also { data ->
                    data[3] = ((cardNumber ushr 16) and 0xff).toByte()
                    data[4] = ((cardNumber ushr 8) and 0xff).toByte()
                    data[5] = (cardNumber and 0xff).toByte()
                }
            ).dropWakeup()

        fun removedFrame(cardNumber: Int): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                SportIdentProtocol.SI_CARD_REMOVED,
                ByteArray(6).also { data ->
                    data[2] = ((cardNumber ushr 24) and 0xff).toByte()
                    data[3] = ((cardNumber ushr 16) and 0xff).toByte()
                    data[4] = ((cardNumber ushr 8) and 0xff).toByte()
                    data[5] = (cardNumber and 0xff).toByte()
                }
            ).dropWakeup()

        fun si5Reply(cardNumber: Int): ByteArray {
            val data = ByteArray(130) { 0xEE.toByte() }
            val cardDataOffset = 2
            val lowNumber = cardNumber % 100_000
            data[cardDataOffset + 4] = ((lowNumber ushr 8) and 0xff).toByte()
            data[cardDataOffset + 5] = (lowNumber and 0xff).toByte()
            data[cardDataOffset + 6] = (cardNumber / 100_000).toByte()
            data[cardDataOffset + 23] = 0
            return SportIdentProtocol.buildExtendedMessage(
                SportIdentProtocol.GET_SI_CARD5,
                data
            ).dropWakeup()
        }

        fun ByteArray.dropWakeup(): ByteArray = copyOfRange(1, size)
    }
}
