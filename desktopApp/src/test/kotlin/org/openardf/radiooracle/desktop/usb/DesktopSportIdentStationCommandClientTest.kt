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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentStationCommandClientTest {
    @Test
    fun sendsExtendedCommandAndReturnsMatchingReply() {
        val reply = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x01, 0x02, 0x03)
        )
        val port = ChunkedPort(readChunks = listOf(reply))
        val client = DesktopSportIdentStationCommandClient(nowMillis = advancingClock())

        val frame = client.sendCommand(
            port = port,
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )

        assertEquals(SportIdentProtocol.GET_SYSTEM_INFO, frame?.command)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), frame?.data)
        assertArrayEquals(
            SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.GET_SYSTEM_INFO,
                data = byteArrayOf(0x00, 0x07)
            ),
            port.writeRequests.single()
        )
    }

    @Test
    fun ignoresUnrelatedFramesUntilMatchingReplyArrives() {
        val unrelated = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val reply = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )
        val port = ChunkedPort(readChunks = listOf(unrelated + reply))
        val client = DesktopSportIdentStationCommandClient(nowMillis = advancingClock())

        val frame = client.sendCommand(
            port = port,
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )

        assertEquals(SportIdentProtocol.GET_SYSTEM_INFO, frame?.command)
        assertArrayEquals(byteArrayOf(0x00, 0x07), frame?.data)
    }

    @Test
    fun canWaitForStandardAckReply() {
        val port = ChunkedPort(readChunks = listOf(SportIdentProtocol.buildAckMessage()))
        val client = DesktopSportIdentStationCommandClient(nowMillis = advancingClock())

        val frame = client.sendCommand(
            port = port,
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07),
            replyCommand = SportIdentProtocol.ACK
        )

        assertEquals(SportIdentProtocol.ACK, frame?.command)
        assertArrayEquals(byteArrayOf(), frame?.data)
    }

    @Test
    fun returnsNullWhenWriteIsIncomplete() {
        val port = ChunkedPort(writeLimit = 3)
        val client = DesktopSportIdentStationCommandClient(nowMillis = advancingClock())

        val frame = client.sendCommand(
            port = port,
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )

        assertNull(frame)
    }

    @Test
    fun reportsRawNegativeAcknowledgementDistinctFromNoReply() {
        val port = ChunkedPort(readChunks = listOf(byteArrayOf(SportIdentProtocol.NAK)))
        val client = DesktopSportIdentStationCommandClient(nowMillis = advancingClock())

        val result = client.sendCommandResult(
            port = port,
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )

        assertEquals(SportIdentCommandResult.NegativeAcknowledgement, result)
    }

    private class ChunkedPort(
        readChunks: List<ByteArray> = emptyList(),
        private val writeLimit: Int? = null
    ) : DesktopSerialPort {
        private val pending = ArrayDeque(readChunks)
        val writeRequests = mutableListOf<ByteArray>()

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
            writeRequests += bytes
            return writeLimit ?: bytes.size
        }

        override fun read(maxBytes: Int): ByteArray =
            pending.removeFirstOrNull() ?: ByteArray(0)
    }

    private fun advancingClock(): () -> Long {
        var now = 0L
        return { ++now }
    }
}
