/*
 * MIT License
 *
 * Copyright (c) 2026 OpenARDF
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

package org.openardf.radiooracle.desktop.printing

import java.nio.charset.Charset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopEscPosTicketEncoderTest {
    @Test
    fun emitsAndroidCompatibleInitializationAlignmentBoldAndCodePage() {
        val bytes = DesktopEscPosTicketEncoder.encode(
            "[C]<b>Radio-Oracle</b>\n[L]Žofie\n[R]Status: OK\n"
        )

        assertArrayEquals(
            byteArrayOf(0x1b, 0x40, 0x1b, 0x74, 72),
            bytes.copyOfRange(0, 5)
        )
        assertTrue(bytes.containsSequence(0x1b, 0x61, 1))
        assertTrue(bytes.containsSequence(0x1b, 0x45, 1))
        assertTrue(bytes.containsSequence(0x1b, 0x61, 2))
        assertTrue(bytes.containsSequence(*"Žofie".toByteArray(Charset.forName("windows-1250")).toIntArray()))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("[C]"))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("<b>"))
    }

    @Test
    fun convertsMixedAlignmentLineToFixedWidthText() {
        val bytes = DesktopEscPosTicketEncoder.encode(
            markedUpText = "[L]Runner[R]00:10:00\n",
            charactersPerLine = 24
        )
        val printableText = bytes.toString(Charsets.ISO_8859_1)

        assertTrue(printableText.contains("Runner          00:10:00"))
        assertTrue(bytes.containsSequence(0x1b, 0x61, 0))
    }

    private fun ByteArray.containsSequence(vararg expected: Int): Boolean =
        indexOfSequence(*expected) >= 0

    private fun ByteArray.indexOfSequence(vararg expected: Int): Int {
        if (expected.isEmpty()) return 0
        return indices.firstOrNull { start ->
            start + expected.size <= size && expected.indices.all { offset ->
                (this[start + offset].toInt() and 0xff) == (expected[offset] and 0xff)
            }
        } ?: -1
    }

    private fun ByteArray.toIntArray(): IntArray = map { it.toInt() and 0xff }.toIntArray()
}
