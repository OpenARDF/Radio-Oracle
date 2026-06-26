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
