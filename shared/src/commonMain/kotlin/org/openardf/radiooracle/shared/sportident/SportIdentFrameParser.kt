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

data class SportIdentFrame(
    val command: Byte,
    val data: ByteArray,
    val raw: ByteArray,
    val extended: Boolean,
    val crcValid: Boolean?
)

object SportIdentFrameParser {
    fun firstFrame(
        bytes: ByteArray,
        commandFilter: Byte? = null,
        requireValidCrc: Boolean = true
    ): SportIdentFrame? {
        var index = 0
        while (index < bytes.size) {
            val start = nextStartIndex(bytes, index) ?: return null
            when (val parsed = parseAt(bytes, start, requireValidCrc)) {
                // A USB read can end anywhere inside a frame. Its payload is not a
                // place to resynchronize: embedded STX/ETX bytes are ordinary data.
                ParseResult.Incomplete -> return null
                is ParseResult.Invalid -> index = start + parsed.bytesToSkip
                is ParseResult.Complete -> {
                    val frame = parsed.frame
                    if (commandFilter == null || frame.command == commandFilter) {
                        return frame
                    }
                    index = start + frame.raw.size
                }
            }
        }
        return null
    }

    private fun nextStartIndex(bytes: ByteArray, fromIndex: Int): Int? {
        for (index in fromIndex until bytes.size) {
            val byte = bytes[index]
            if (byte == SportIdentProtocol.WAKEUP || byte == SportIdentProtocol.ZERO) {
                continue
            }
            if (byte == SportIdentProtocol.STX) {
                return index
            }
        }
        return null
    }

    private fun parseAt(bytes: ByteArray, start: Int, requireValidCrc: Boolean): ParseResult {
        if (bytes.size <= start + 1) {
            return ParseResult.Incomplete
        }
        val command = bytes[start + 1]
        return if (command.toUnsignedInt() > EXTENDED_COMMAND_MIN) {
            parseExtended(bytes, start, command, requireValidCrc)
        } else {
            parseStandard(bytes, start, command)
        }
    }

    private fun parseExtended(
        bytes: ByteArray,
        start: Int,
        command: Byte,
        requireValidCrc: Boolean
    ): ParseResult {
        if (bytes.size <= start + 2) {
            return ParseResult.Incomplete
        }

        val dataLength = bytes[start + 2].toUnsignedInt()
        val totalLength = dataLength + EXTENDED_FRAME_OVERHEAD
        if (bytes.size < start + totalLength) {
            return ParseResult.Incomplete
        }

        val endIndex = start + totalLength - 1
        if (bytes[endIndex] != SportIdentProtocol.ETX) {
            return ParseResult.Invalid(bytesToSkip = 1)
        }

        val crcStart = start + 3 + dataLength
        val expectedCrc = (bytes[crcStart].toUnsignedInt() shl 8) + bytes[crcStart + 1].toUnsignedInt()
        val crcPayload = bytes.copyOfRange(start + 1, start + 3 + dataLength)
        val actualCrc = SportIdentProtocol.calculateCrc(dataLength + 2, crcPayload)
        val crcValid = actualCrc == expectedCrc
        if (requireValidCrc && !crcValid) {
            // This complete frame is corrupt. Skip its payload as a unit so it
            // cannot be mistaken for an unrelated standard frame without a CRC.
            return ParseResult.Invalid(bytesToSkip = totalLength)
        }

        return ParseResult.Complete(SportIdentFrame(
            command = command,
            data = bytes.copyOfRange(start + 3, start + 3 + dataLength),
            raw = bytes.copyOfRange(start, start + totalLength),
            extended = true,
            crcValid = crcValid
        ))
    }

    private fun parseStandard(bytes: ByteArray, start: Int, command: Byte): ParseResult {
        var escaped = false
        for (index in start + 2 until bytes.size) {
            val byte = bytes[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (byte == SportIdentProtocol.DLE) {
                escaped = true
                continue
            }
            if (byte == SportIdentProtocol.ETX) {
                return ParseResult.Complete(SportIdentFrame(
                    command = command,
                    data = bytes.copyOfRange(start + 2, index),
                    raw = bytes.copyOfRange(start, index + 1),
                    extended = false,
                    crcValid = null
                ))
            }
        }
        return ParseResult.Incomplete
    }

    private sealed interface ParseResult {
        data object Incomplete : ParseResult
        data class Invalid(val bytesToSkip: Int) : ParseResult
        data class Complete(val frame: SportIdentFrame) : ParseResult
    }

    private const val EXTENDED_COMMAND_MIN = 0x80
    private const val EXTENDED_FRAME_OVERHEAD = 6
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
