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

import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

internal class DesktopSportIdentStationCommandClient(
    private val readTimeoutMs: Int = 1200,
    private val maxReplyBytes: Int = 256,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun sendCommand(
        port: DesktopSerialPort,
        command: Byte,
        data: ByteArray = byteArrayOf(),
        replyCommand: Byte = command
    ): SportIdentFrame? {
        val request = SportIdentProtocol.buildExtendedMessage(command, data)
        if (port.write(request) != request.size) {
            return null
        }

        val deadline = nowMillis() + readTimeoutMs
        val stream = DesktopSportIdentFrameStream(
            port = port,
            maxReadBytes = maxReplyBytes,
            nowMillis = nowMillis
        )
        while (nowMillis() < deadline) {
            val frame = stream.nextFrame(deadline) ?: return null
            if (frame.command == replyCommand) {
                return frame
            }
        }
        return null
    }
}
