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
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentFrameStreamTest {
    @Test
    fun readsFrameSplitAcrossSerialChunks() {
        val message = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val port = ChunkedPort(listOf(message.copyOfRange(0, 3), message.copyOfRange(3, message.size)))
        val stream = DesktopSportIdentFrameStream(port, nowMillis = advancingClock())

        val frame = stream.nextFrame(deadlineMillis = 1_000)
        assertNotNull(frame)

        assertEquals(SportIdentProtocol.PROBE_COMMAND, frame!!.command)
        assertArrayEquals(byteArrayOf(0x4d), frame.data)
    }

    @Test
    fun returnsMultipleFramesFromOneSerialChunkInOrder() {
        val first = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(0x4d)
        )
        val second = SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = byteArrayOf(0x00, 0x07)
        )
        val port = ChunkedPort(listOf(first + second))
        val stream = DesktopSportIdentFrameStream(port, nowMillis = advancingClock())

        val firstFrame = stream.nextFrame(deadlineMillis = 1_000)
        val secondFrame = stream.nextFrame(deadlineMillis = 1_000)
        assertNotNull(firstFrame)
        assertNotNull(secondFrame)

        assertEquals(SportIdentProtocol.PROBE_COMMAND, firstFrame!!.command)
        assertEquals(SportIdentProtocol.GET_SYSTEM_INFO, secondFrame!!.command)
        assertArrayEquals(byteArrayOf(0x00, 0x07), secondFrame.data)
    }

    private class ChunkedPort(chunks: List<ByteArray>) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)

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
        override fun write(bytes: ByteArray): Int = bytes.size
        override fun read(maxBytes: Int): ByteArray =
            pending.removeFirstOrNull() ?: ByteArray(0)
    }

    private fun advancingClock(): () -> Long {
        var now = 0L
        return { ++now }
    }
}
