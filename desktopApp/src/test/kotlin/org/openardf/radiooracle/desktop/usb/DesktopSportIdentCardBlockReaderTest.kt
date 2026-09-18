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
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspector

class DesktopSportIdentCardBlockReaderTest {
    @Test
    fun ownerInspectionReadsExtraModernBlocksBeforeAckAndPreservesPunchParsing() {
        val blockOrder = listOf(0, 4, 5, 6, 7, 1, 2, 3)
        val port = modernPort(blockOrder)
        val download = reader(includeOwnerData = true).readFirstSupportedCardAfterInsertOnOpenPort(port)

        assertEquals(blockOrder, download.blocks.map { it.blockNumber })
        assertEquals(listOf(41), download.readout.punches.map { it.siCode })
        assertEquals("Test Club", SportIdentCardOwnerInspector.inspect(download.readout, download.blocks).holder?.club)
        assertEquals(blockOrder, port.requestedBlocks())
        assertEquals(SportIdentProtocol.ACK, port.writtenCommands().last())
    }

    @Test
    fun normalRaceDownloadKeepsItsExistingModernBlockSequence() {
        val blockOrder = listOf(0, 4, 5, 6, 7)
        val port = modernPort(blockOrder)
        val download = reader().readFirstSupportedCardAfterInsertOnOpenPort(port)
        assertEquals(blockOrder, port.requestedBlocks())
        assertEquals(listOf(41), download.readout.punches.map { it.siCode })
    }

    @Test
    fun removalDuringExtraOwnerReadDoesNotAckAnIncompleteInspection() {
        val port = modernPort(listOf(0, 4, 5, 6, 7), removedFrame(MODERN_CARD_NUMBER))
        assertThrows(IllegalStateException::class.java) {
            reader(includeOwnerData = true).readFirstSupportedCardAfterInsertOnOpenPort(port)
        }
        assertEquals(0, port.writtenCommands().count { it == SportIdentProtocol.ACK })
    }

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

    private fun reader(onProgress: (String) -> Unit = {}, includeOwnerData: Boolean = false) =
        DesktopSportIdentCardBlockReader(
            postAckSettleMs = 0,
            onProgress = onProgress,
            sleepMillis = {},
            nowMillis = advancingClock(),
            includeOwnerData = includeOwnerData
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

        fun requestedBlocks(): List<Int> = writes.mapNotNull { bytes ->
            SportIdentFrameParser.firstFrame(bytes, requireValidCrc = true)
                ?.takeIf { it.command == SportIdentProtocol.GET_SI_CARD8_9_SIAC }
                ?.data?.singleOrNull()?.toInt()
        }
    }

    private companion object {
        const val CARD_NUMBER = 234_567
        const val MODERN_CARD_NUMBER = 8_000_001

        fun modernPort(blockOrder: List<Int>, afterBlocks: ByteArray? = null): ChunkedPort {
            val data = ByteArray(1024) { 0xEE.toByte() }
            data[22] = 1
            data[24] = 15
            data[25] = (MODERN_CARD_NUMBER ushr 16).toByte()
            data[26] = (MODERN_CARD_NUMBER ushr 8).toByte()
            data[27] = MODERN_CARD_NUMBER.toByte()
            val ownerText = "A".repeat(70) + ";Runner;F;2000;Test Club;"
            ownerText.forEachIndexed { index, c -> data[32 + index] = c.code.toByte() }
            data[447] = 1
            data[512] = 0
            data[513] = 41
            data[514] = 0
            data[515] = 60
            val inserted = SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.SI_CARD8_9_SIAC,
                byteArrayOf(0, 0, 0, data[25], data[26], data[27])).dropWakeup()
            val replies = blockOrder.map { number ->
                SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.GET_SI_CARD8_9_SIAC,
                    byteArrayOf(0, 0, number.toByte()) + data.copyOfRange(number * 128, (number + 1) * 128))
                    .dropWakeup()
            }
            return ChunkedPort(listOf(inserted) + replies + listOfNotNull(afterBlocks))
        }

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
