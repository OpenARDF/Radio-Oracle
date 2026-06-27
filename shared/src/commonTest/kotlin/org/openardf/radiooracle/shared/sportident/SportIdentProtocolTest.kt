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
