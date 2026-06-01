package org.openardf.radiooracle.shared.sportident

data class SportIdentStationInfo(
    val serialNumber: Int,
    val extendedMode: Boolean,
    val stationCodeNumber: Int? = null,
    val stationModeCode: Int? = null
) {
    val stationModeLabel: String?
        get() = stationModeCode?.let(SportIdentStationMode::labelForModeCode)

    val isReadoutMode: Boolean?
        get() = stationModeCode?.let(SportIdentStationMode::isReadoutModeCode)
}

object SportIdentStationMode {
    const val READOUT_MODE_CODE = 5
    const val SI_MASTER_MODE_CODE = 8

    fun isReadoutModeCode(code: Int): Boolean =
        code == READOUT_MODE_CODE || code == SI_MASTER_MODE_CODE

    fun labelForModeCode(code: Int): String =
        when (code) {
            1 -> "CLEAR"
            2 -> "CHECK"
            3 -> "START"
            4 -> "FINISH"
            READOUT_MODE_CODE -> "READOUT"
            SI_MASTER_MODE_CODE -> "SI MASTER"
            else -> flaggedModeLabel(code) ?: "MODE 0x${code.toHexByte()}"
        }

    private fun flaggedModeLabel(code: Int): String? {
        val baseCode = code and MODE_CODE_MASK
        val flagBits = code and MODE_FLAG_MASK
        if (flagBits == 0) {
            return null
        }
        val baseLabel = when (baseCode) {
            READOUT_MODE_CODE -> "READOUT"
            SI_MASTER_MODE_CODE -> "SI MASTER"
            else -> return null
        }
        return "$baseLabel + 0x${flagBits.toHexByte()} flag"
    }

    private const val MODE_CODE_MASK = 0x1f
    private const val MODE_FLAG_MASK = 0xe0
}

object SportIdentStationInfoParser {
    fun fromSystemInfoFrame(frame: SportIdentFrame): SportIdentStationInfo? {
        if (frame.command != SportIdentProtocol.GET_SYSTEM_INFO || frame.data.size < SERIAL_DATA_OFFSET + SERIAL_BYTE_COUNT) {
            return null
        }

        val serialNumber =
            (frame.data[SERIAL_DATA_OFFSET].toUnsignedInt() shl 24) +
                (frame.data[SERIAL_DATA_OFFSET + 1].toUnsignedInt() shl 16) +
                (frame.data[SERIAL_DATA_OFFSET + 2].toUnsignedInt() shl 8) +
                frame.data[SERIAL_DATA_OFFSET + 3].toUnsignedInt()

        val extendedMode = frame.data
            .getOrNull(EXTENDED_MODE_DATA_OFFSET)
            ?.let { (it.toUnsignedInt() and EXTENDED_MODE_FLAG) == EXTENDED_MODE_FLAG }
            ?: false
        val stationCodeNumber = frame.data
            .getOrNull(STATION_CODE_NUMBER_DATA_OFFSET)
            ?.toUnsignedInt()
        val stationModeCode = frame.data
            .getOrNull(STATION_MODE_CODE_DATA_OFFSET)
            ?.toUnsignedInt()

        return SportIdentStationInfo(
            serialNumber = serialNumber,
            extendedMode = extendedMode,
            stationCodeNumber = stationCodeNumber,
            stationModeCode = stationModeCode
        )
    }

    private const val SERIAL_DATA_OFFSET = 3
    private const val SERIAL_BYTE_COUNT = 4
    private const val STATION_CODE_NUMBER_DATA_OFFSET = 1
    private const val STATION_MODE_CODE_DATA_OFFSET = 20
    private const val EXTENDED_MODE_DATA_OFFSET = 119
    private const val EXTENDED_MODE_FLAG = 0x01
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff

private fun Int.toHexByte(): String = toString(16).uppercase().padStart(2, '0')
