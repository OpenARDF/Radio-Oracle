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
            val frame = parseAt(bytes, start, requireValidCrc)
            if (frame == null) {
                index = start + 1
                continue
            }
            if (commandFilter == null || frame.command == commandFilter) {
                return frame
            }
            index = start + frame.raw.size
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

    private fun parseAt(bytes: ByteArray, start: Int, requireValidCrc: Boolean): SportIdentFrame? {
        if (bytes.size <= start + 1 || bytes[start] != SportIdentProtocol.STX) {
            return null
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
    ): SportIdentFrame? {
        if (bytes.size <= start + 2) {
            return null
        }

        val dataLength = bytes[start + 2].toUnsignedInt()
        val totalLength = dataLength + EXTENDED_FRAME_OVERHEAD
        if (bytes.size < start + totalLength) {
            return null
        }

        val endIndex = start + totalLength - 1
        if (bytes[endIndex] != SportIdentProtocol.ETX) {
            return null
        }

        val crcStart = start + 3 + dataLength
        val expectedCrc = (bytes[crcStart].toUnsignedInt() shl 8) + bytes[crcStart + 1].toUnsignedInt()
        val crcPayload = bytes.copyOfRange(start + 1, start + 3 + dataLength)
        val actualCrc = SportIdentProtocol.calculateCrc(dataLength + 2, crcPayload)
        val crcValid = actualCrc == expectedCrc
        if (requireValidCrc && !crcValid) {
            return null
        }

        return SportIdentFrame(
            command = command,
            data = bytes.copyOfRange(start + 3, start + 3 + dataLength),
            raw = bytes.copyOfRange(start, start + totalLength),
            extended = true,
            crcValid = crcValid
        )
    }

    private fun parseStandard(bytes: ByteArray, start: Int, command: Byte): SportIdentFrame? {
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
                return SportIdentFrame(
                    command = command,
                    data = bytes.copyOfRange(start + 2, index),
                    raw = bytes.copyOfRange(start, index + 1),
                    extended = false,
                    crcValid = null
                )
            }
        }
        return null
    }

    private const val EXTENDED_COMMAND_MIN = 0x80
    private const val EXTENDED_FRAME_OVERHEAD = 6
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
