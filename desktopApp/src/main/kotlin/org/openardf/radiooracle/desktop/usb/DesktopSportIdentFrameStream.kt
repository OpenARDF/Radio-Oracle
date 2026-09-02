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

import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

/**
 * Buffered SPORTident frame reader for desktop serial ports.
 *
 * Serial reads are not packet boundaries: a SPORTident frame can arrive split
 * across multiple reads, and more than one frame can arrive in a single read.
 * This class keeps unread bytes between reads so card insert events and card
 * block responses are parsed from complete SPORTident frames instead of from
 * whichever byte chunk jSerialComm happened to return.
 */
internal class DesktopSportIdentFrameStream(
    private val port: DesktopSerialPort,
    private val maxReadBytes: Int = DEFAULT_MAX_READ_BYTES,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var buffered = ByteArray(0)
    var lastRawRead: ByteArray? = null
        private set

    fun nextFrame(
        deadlineMillis: Long,
        requireValidCrc: Boolean = true
    ): SportIdentFrame? {
        while (nowMillis() < deadlineMillis) {
            nextBufferedFrame(requireValidCrc)?.let { return it }
            trimBufferedNoise()

            val raw = port.read(maxReadBytes)
            if (raw.isEmpty()) {
                trace("quiet serial read")
                continue
            }
            lastRawRead = raw
            trace("raw ${raw.toHexString()}")
            buffered += raw
        }
        return nextBufferedFrame(requireValidCrc)
    }

    fun nextCommandResult(
        deadlineMillis: Long,
        requireValidCrc: Boolean = true
    ): SportIdentCommandResult {
        while (nowMillis() < deadlineMillis) {
            nextBufferedCommandResult(requireValidCrc)?.let { return it }
            trimBufferedCommandNoise()

            val raw = port.read(maxReadBytes)
            if (raw.isEmpty()) {
                trace("quiet serial read")
                continue
            }
            lastRawRead = raw
            trace("raw ${raw.toHexString()}")
            buffered += raw
        }
        return nextBufferedCommandResult(requireValidCrc) ?: SportIdentCommandResult.NoReply
    }

    private fun nextBufferedFrame(requireValidCrc: Boolean): SportIdentFrame? {
        val frame = SportIdentFrameParser.firstFrame(
            buffered,
            requireValidCrc = requireValidCrc
        ) ?: return null
        trace(
            "frame command=${frame.command.toHexString()} dataBytes=${frame.data.size} " +
                "raw=${frame.raw.toHexString()}"
        )
        discardThrough(frame.raw)
        return frame
    }

    private fun nextBufferedCommandResult(requireValidCrc: Boolean): SportIdentCommandResult? {
        val frameStart = buffered.indexOfFirst { it == SportIdentProtocol.STX }
        val nakStart = buffered.indexOfFirst { it == SportIdentProtocol.NAK }
        if (nakStart >= 0 && (frameStart < 0 || nakStart < frameStart)) {
            trace("negative acknowledgement")
            buffered = buffered.copyOfRange(nakStart + 1, buffered.size)
            return SportIdentCommandResult.NegativeAcknowledgement
        }

        val frame = SportIdentFrameParser.firstFrame(
            buffered,
            requireValidCrc = requireValidCrc
        ) ?: return null
        trace(
            "frame command=${frame.command.toHexString()} dataBytes=${frame.data.size} " +
                "raw=${frame.raw.toHexString()}"
        )
        discardThrough(frame.raw)
        return if (frame.command == SportIdentProtocol.NAK) {
            SportIdentCommandResult.NegativeAcknowledgement
        } else {
            SportIdentCommandResult.Reply(frame)
        }
    }

    private fun discardThrough(rawFrame: ByteArray) {
        val start = buffered.indexOf(rawFrame)
        if (start < 0) {
            buffered = ByteArray(0)
            return
        }
        buffered = buffered.copyOfRange(start + rawFrame.size, buffered.size)
    }

    private fun trimBufferedNoise() {
        val start = buffered.indexOfFirst { it == SportIdentProtocol.STX }
        buffered = when {
            start < 0 -> ByteArray(0)
            start > 0 -> buffered.copyOfRange(start, buffered.size)
            buffered.size > MAX_BUFFER_BYTES -> buffered.takeLast(MAX_BUFFER_BYTES).toByteArray()
            else -> buffered
        }
    }

    private fun trimBufferedCommandNoise() {
        val start = buffered.indexOfFirst {
            it == SportIdentProtocol.STX || it == SportIdentProtocol.NAK
        }
        buffered = when {
            start < 0 -> ByteArray(0)
            start > 0 -> buffered.copyOfRange(start, buffered.size)
            buffered.size > MAX_BUFFER_BYTES -> buffered.takeLast(MAX_BUFFER_BYTES).toByteArray()
            else -> buffered
        }
    }

    private fun trace(message: String) {
        if (traceFrames) {
            System.err.println("SI frame trace: $message")
        }
    }

    private companion object {
        const val DEFAULT_MAX_READ_BYTES = 512
        const val MAX_BUFFER_BYTES = 4096
        val traceFrames: Boolean =
            System.getenv("RADIO_ORACLE_SI_TRACE_FRAMES")?.equals("1") == true ||
                System.getenv("RADIO_ORACLE_SI_TRACE_FRAMES")?.equals("true", ignoreCase = true) == true
    }
}

private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) {
        return -1
    }
    for (candidate in 0..(size - needle.size)) {
        var matches = true
        for (offset in needle.indices) {
            if (this[candidate + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) {
            return candidate
        }
    }
    return -1
}

private fun Byte.toHexString(): String =
    "0x%02x".format(toInt() and 0xff)

private fun ByteArray.toHexString(): String =
    joinToString(" ") { it.toHexString() }
