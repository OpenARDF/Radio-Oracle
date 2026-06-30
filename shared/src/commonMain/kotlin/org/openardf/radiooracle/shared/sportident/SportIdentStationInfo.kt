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

data class SportIdentStationInfo(
    val serialNumber: Int,
    val extendedMode: Boolean,
    val stationCodeNumber: Int? = null,
    val stationModeCode: Int? = null,
    val firmwareVersion: String? = null,
    val modelId: Int? = null,
    val modelName: String? = null,
    val buildDate: String? = null,
    val batteryDate: String? = null,
    val memorySizeKb: Int? = null,
    val batteryVoltage: Double? = null,
    val activeTimeMinutes: Int? = null,
    val protocolByte: Int? = null
) {
    val stationModeLabel: String?
        get() = stationModeCode?.let(SportIdentStationMode::labelForModeCode)

    val isReadoutMode: Boolean?
        get() = stationModeCode?.let(SportIdentStationMode::isReadoutModeCode)

    val isDownloadCapableMode: Boolean?
        get() = stationModeCode?.let(SportIdentStationMode::isDownloadCapableModeCode)
}

object SportIdentStationMode {
    const val READOUT_MODE_CODE = 5
    const val SI_MASTER_MODE_CODE = 8

    fun isReadoutModeCode(code: Int): Boolean =
        code == READOUT_MODE_CODE || code == SI_MASTER_MODE_CODE

    fun isDownloadCapableModeCode(code: Int): Boolean =
        baseModeCode(code) == READOUT_MODE_CODE || baseModeCode(code) == SI_MASTER_MODE_CODE

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
        val baseCode = baseModeCode(code)
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

    private fun baseModeCode(code: Int): Int =
        code and MODE_CODE_MASK

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
        val stationCodeNumber = frame.data.stationCodeNumber()
        val stationModeCode = frame.data
            .getOrNull(STATION_MODE_CODE_DATA_OFFSET)
            ?.toUnsignedInt()
        val modelId = frame.data.readSystemInfoUInt16(SYS_VAL_MODEL_ID_OFFSET)

        return SportIdentStationInfo(
            serialNumber = serialNumber,
            extendedMode = extendedMode,
            stationCodeNumber = stationCodeNumber,
            stationModeCode = stationModeCode,
            firmwareVersion = frame.data.readSystemInfoAscii(SYS_VAL_FIRMWARE_OFFSET, 3),
            modelId = modelId,
            modelName = modelId?.let(SportIdentStationModel::labelForModelId),
            buildDate = frame.data.readSystemInfoDate(SYS_VAL_BUILD_DATE_OFFSET),
            batteryDate = frame.data.readSystemInfoDate(SYS_VAL_BATTERY_DATE_OFFSET),
            memorySizeKb = frame.data.readSystemInfoUInt8(SYS_VAL_MEMORY_SIZE_OFFSET),
            batteryVoltage = frame.data.readSystemInfoUInt16(SYS_VAL_BATTERY_VOLTAGE_OFFSET)
                ?.let { it * 5.0 / 65_536.0 },
            activeTimeMinutes = frame.data.readSystemInfoUInt16(SYS_VAL_ACTIVE_TIME_OFFSET)
                ?.takeIf { it in 0..MAX_ACTIVE_TIME_MINUTES },
            protocolByte = frame.data.readSystemInfoUInt8(SYS_VAL_PROTOCOL_OFFSET)
        )
    }

    private const val SERIAL_DATA_OFFSET = 3
    private const val SERIAL_BYTE_COUNT = 4
    private const val STATION_CODE_NUMBER_DATA_OFFSET = 1
    private const val BSF7_DIRECT_STATION_CODE_NUMBER_DATA_OFFSET = 17
    private const val STATION_MODE_CODE_DATA_OFFSET = 20
    private const val EXTENDED_MODE_DATA_OFFSET = 119
    private const val EXTENDED_MODE_FLAG = 0x01
    private const val SYS_VAL_DATA_OFFSET = 3
    private const val SYS_VAL_FIRMWARE_OFFSET = 0x05
    private const val SYS_VAL_BUILD_DATE_OFFSET = 0x08
    private const val SYS_VAL_MODEL_ID_OFFSET = 0x0B
    private const val SYS_VAL_MEMORY_SIZE_OFFSET = 0x0D
    private const val SYS_VAL_BATTERY_DATE_OFFSET = 0x15
    private const val SYS_VAL_BATTERY_VOLTAGE_OFFSET = 0x50
    private const val SYS_VAL_PROTOCOL_OFFSET = 0x74
    private const val SYS_VAL_ACTIVE_TIME_OFFSET = 0x7E
    private const val MAX_ACTIVE_TIME_MINUTES = 5_759

    private fun ByteArray.stationCodeNumber(): Int? {
        val primary = getOrNull(STATION_CODE_NUMBER_DATA_OFFSET)?.toUnsignedInt()
        val bsf7Direct = getOrNull(BSF7_DIRECT_STATION_CODE_NUMBER_DATA_OFFSET)?.toUnsignedInt()
        return if (
            primary != null &&
            bsf7Direct != null &&
            primary <= MAX_NON_STATION_STATUS_VALUE &&
            bsf7Direct > MAX_NON_STATION_STATUS_VALUE
        ) {
            bsf7Direct
        } else {
            primary
        }
    }

    private const val MAX_NON_STATION_STATUS_VALUE = 10

    private fun ByteArray.readSystemInfoUInt8(offset: Int): Int? =
        getOrNull(SYS_VAL_DATA_OFFSET + offset)?.toUnsignedInt()

    private fun ByteArray.readSystemInfoUInt16(offset: Int): Int? {
        val high = getOrNull(SYS_VAL_DATA_OFFSET + offset)?.toUnsignedInt() ?: return null
        val low = getOrNull(SYS_VAL_DATA_OFFSET + offset + 1)?.toUnsignedInt() ?: return null
        return (high shl 8) or low
    }

    private fun ByteArray.readSystemInfoAscii(offset: Int, length: Int): String? {
        if (size < SYS_VAL_DATA_OFFSET + offset + length) return null
        val chars = copyOfRange(SYS_VAL_DATA_OFFSET + offset, SYS_VAL_DATA_OFFSET + offset + length)
        if (chars.any { it.toUnsignedInt() !in PRINTABLE_ASCII_RANGE }) return null
        return chars.decodeToString()
    }

    private fun ByteArray.readSystemInfoDate(offset: Int): String? {
        val year = readSystemInfoUInt8(offset) ?: return null
        val month = readSystemInfoUInt8(offset + 1) ?: return null
        val day = readSystemInfoUInt8(offset + 2) ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return "20${year.toString().padStart(2, '0')}-${month.toString().padStart(2, '0')}-${
            day.toString().padStart(2, '0')
        }"
    }

    private val PRINTABLE_ASCII_RANGE = 0x20..0x7E
}

object SportIdentStationModel {
    fun labelForModelId(modelId: Int): String =
        when (modelId) {
            0x8117, 0x8118, 0x8197 -> "BSF7"
            0x8198 -> "BSF8"
            0x8187 -> "BS7-SI-Master"
            0x8188 -> "BS8-SI-Master"
            0x9197 -> "BSM7-RS232/USB"
            0x9198 -> "BSM8-USB/SRR"
            0x9D9A -> "BS11-BL"
            0xCD9B -> "BS11-BS"
            else -> "0x${modelId.toString(16).uppercase().padStart(4, '0')}"
        }
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff

private fun Int.toHexByte(): String = toString(16).uppercase().padStart(2, '0')
