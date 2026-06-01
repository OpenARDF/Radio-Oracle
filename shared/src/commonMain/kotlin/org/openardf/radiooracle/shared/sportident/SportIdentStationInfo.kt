package org.openardf.radiooracle.shared.sportident

data class SportIdentStationInfo(
    val serialNumber: Int,
    val extendedMode: Boolean,
    val stationCode: Int? = null
) {
    val stationModeLabel: String?
        get() = stationCode?.let(SportIdentStationMode::labelForCode)

    val isReadoutMode: Boolean?
        get() = stationCode?.let(SportIdentStationMode::isReadoutCode)
}

object SportIdentStationMode {
    const val READOUT_CODE = 5
    const val SI_MASTER_CODE = 8

    fun isReadoutCode(code: Int): Boolean =
        code == READOUT_CODE || code == SI_MASTER_CODE

    fun labelForCode(code: Int): String =
        when (code) {
            1 -> "CLEAR"
            2 -> "CHECK"
            3 -> "START"
            4 -> "FINISH"
            READOUT_CODE -> "READOUT"
            SI_MASTER_CODE -> "SI MASTER"
            in 31..511 -> "CONTROL $code"
            else -> "CODE $code"
        }
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
        val stationCode = frame.data
            .getOrNull(STATION_CODE_DATA_OFFSET)
            ?.toUnsignedInt()

        return SportIdentStationInfo(
            serialNumber = serialNumber,
            extendedMode = extendedMode,
            stationCode = stationCode
        )
    }

    private const val SERIAL_DATA_OFFSET = 3
    private const val SERIAL_BYTE_COUNT = 4
    private const val STATION_CODE_DATA_OFFSET = 20
    private const val EXTENDED_MODE_DATA_OFFSET = 119
    private const val EXTENDED_MODE_FLAG = 0x01
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
