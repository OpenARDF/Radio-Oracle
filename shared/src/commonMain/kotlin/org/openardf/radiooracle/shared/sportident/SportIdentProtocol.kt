package org.openardf.radiooracle.shared.sportident

/** Minimal SPORTident wire-protocol helpers shared by future platform reader ports. */
object SportIdentProtocol {
    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03
    const val ACK: Byte = 0x06
    const val DLE: Byte = 0x10
    const val NAK: Byte = 0x15
    const val WAKEUP: Byte = 0xFF.toByte()
    const val ZERO: Byte = 0x00
    const val GET_SYSTEM_INFO: Byte = 0x83.toByte()
    const val GET_SI_CARD8_9_SIAC: Byte = 0xEF.toByte()
    const val PROBE_COMMAND: Byte = 0xF0.toByte()
    const val BAUDRATE_LOW = 4800
    const val BAUDRATE_HIGH = 38400
    const val SI_CARD5: Byte = 0xE5.toByte()
    const val SI_CARD6: Byte = 0xE6.toByte()
    const val SI_CARD8_9_SIAC: Byte = 0xE8.toByte()
    const val SI_CARD_REMOVED: Byte = 0xE7.toByte()
    const val SI_CARD_BLOCK_SIZE = 128

    private const val POLYNOM = 0x8005

    fun buildExtendedMessage(command: Byte, data: ByteArray = byteArrayOf()): ByteArray {
        val buffer = ByteArray(data.size + 7)
        buffer[0] = WAKEUP
        buffer[1] = STX
        buffer[2] = command
        buffer[3] = data.size.toByte()
        data.copyInto(buffer, destinationOffset = 4)

        val crc = calculateCrc(data.size + 2, buffer.copyOfRange(2, buffer.size))
        buffer[data.size + 4] = ((crc and 0xff00) shr 8).toByte()
        buffer[data.size + 5] = (crc and 0xff).toByte()
        buffer[data.size + 6] = ETX
        return buffer
    }

    fun buildAckMessage(): ByteArray =
        byteArrayOf(WAKEUP, STX, ACK, ETX)

    fun calculateCrc(count: Int, data: ByteArray): Int {
        if (count < 2) return 0
        require(data.size >= count) { "CRC data must contain at least count bytes." }

        var index = 0
        var tmp = data[index++].toUnsignedInt()
        tmp = (tmp shl 8) + data[index++].toUnsignedInt()
        if (count == 2) return tmp

        for (remainingWord in count shr 1 downTo 1) {
            var value = if (remainingWord > 1) {
                val high = data[index++].toUnsignedInt()
                val low = data[index++].toUnsignedInt()
                (high shl 8) + low
            } else if (count and 1 == 1) {
                data[index].toUnsignedInt() shl 8
            } else {
                0
            }

            repeat(16) {
                tmp = if (tmp and 0x8000 == 0x8000) {
                    ((tmp shl 1) + if (value and 0x8000 == 0x8000) 1 else 0) xor POLYNOM
                } else {
                    (tmp shl 1) + if (value and 0x8000 == 0x8000) 1 else 0
                }
                value = value shl 1
            }
        }
        return tmp and 0xffff
    }
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
