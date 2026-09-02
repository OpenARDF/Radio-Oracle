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

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class SportIdentStationBackupMetadata(
    val nextAddress: Int,
    val overflowed: Boolean,
    val memorySizeBytes: Int,
    val extendedPunchRecords: Boolean,
    val stationModeCode: Int
) {
    val recordSize: Int
        get() = if (extendedPunchRecords) EXTENDED_RECORD_SIZE else LEGACY_RECORD_SIZE

    companion object {
        const val LEGACY_RECORD_SIZE = 6
        const val EXTENDED_RECORD_SIZE = 8
    }
}

data class SportIdentStationBackupReadRequest(
    val address: Int,
    val byteCount: Int
) {
    val payload: ByteArray = byteArrayOf(
        (address ushr 16).toByte(),
        (address ushr 8).toByte(),
        address.toByte(),
        byteCount.toByte()
    )
}

data class SportIdentStationBackupRecord(
    val address: Int,
    val cardNumber: Int,
    val recordedDate: LocalDate?,
    val recordedTime: LocalTime?,
    val dayOfWeek: DayOfWeek?,
    val halfDay: String,
    val errorCode: Int?
) {
    val recordedAt: LocalDateTime?
        get() = if (recordedDate != null && recordedTime != null) {
            LocalDateTime.of(recordedDate, recordedTime)
        } else {
            null
        }

    val errorLabel: String?
        get() = errorCode?.let { "Err${it.toString(16).uppercase()}" }

    val errorDescription: String?
        get() = when (errorCode) {
            8 -> "backup pointer was reconstructed"
            9 -> "SI-Card was full"
            10 -> "SI-Card was removed before the punch completed"
            11 -> "station code write failed"
            12 -> "station could not confirm the written code"
            13 -> "SI-Card verification failed"
            14 -> "SI-Card was not cleared"
            15 -> "SI-Card does not support this station code"
            null -> null
            else -> "punch failed"
        }
}

data class SportIdentStationBackupSnapshot(
    val stationInfo: SportIdentStationInfo,
    val metadata: SportIdentStationBackupMetadata,
    val records: List<SportIdentStationBackupRecord>,
    val unreadableRecordAddresses: List<Int> = emptyList()
)

/** Read-only SPORTident station backup commands and decoders shared by desktop and Android. */
object SportIdentStationBackupProtocol {
    const val SYSTEM_INFO_LENGTH = 0x75
    const val MEMORY_START_ADDRESS = 0x100

    fun readSystemInfoStep() = SportIdentTimeSyncCommandStep(
        label = "Read station backup metadata",
        command = SportIdentProtocol.GET_SYSTEM_INFO,
        payload = byteArrayOf(0x00, SYSTEM_INFO_LENGTH.toByte())
    )

    fun readRecordStep(request: SportIdentStationBackupReadRequest) = SportIdentTimeSyncCommandStep(
        label = "Read station backup record at 0x${request.address.toString(16).uppercase()}",
        command = SportIdentProtocol.GET_BACKUP,
        payload = request.payload
    )

    fun parseMetadata(frame: SportIdentFrame): SportIdentStationBackupMetadata? {
        if (frame.command != SportIdentProtocol.GET_SYSTEM_INFO) return null
        val data = frame.data
        val pointerHigh = data.readSystemInfoUInt16(BACKUP_POINTER_HIGH_OFFSET) ?: return null
        val pointerLow = data.readSystemInfoUInt16(BACKUP_POINTER_LOW_OFFSET) ?: return null
        val memorySizeKb = data.readSystemInfoUInt8(MEMORY_SIZE_OFFSET) ?: return null
        val protocolByte = data.readSystemInfoUInt8(PROTOCOL_OFFSET) ?: return null
        val stationMode = data.readSystemInfoUInt8(STATION_MODE_OFFSET) ?: return null
        val memorySizeBytes = memorySizeKb * 1024
        if (memorySizeBytes <= MEMORY_START_ADDRESS) return null

        return SportIdentStationBackupMetadata(
            nextAddress = (pointerHigh shl 16) or pointerLow,
            overflowed = data.readSystemInfoUInt8(MEMORY_OVERFLOW_OFFSET)?.let { it != 0 } ?: return null,
            memorySizeBytes = memorySizeBytes,
            extendedPunchRecords = protocolByte and EXTENDED_PROTOCOL_FLAG != 0,
            stationModeCode = stationMode
        )
    }

    fun readRequests(metadata: SportIdentStationBackupMetadata): List<SportIdentStationBackupReadRequest> {
        require(SportIdentStationMode.isPunchBackupModeCode(metadata.stationModeCode)) {
            "Station backup history is not a punch-record format in " +
                SportIdentStationMode.labelForModeCode(metadata.stationModeCode) + " mode."
        }
        val recordSize = metadata.recordSize
        val memoryEndExclusive = metadata.memorySizeBytes
        require(metadata.nextAddress in MEMORY_START_ADDRESS until memoryEndExclusive) {
            "Station returned an invalid backup pointer 0x${metadata.nextAddress.toString(16).uppercase()}."
        }

        val nextRecordAddress = metadata.nextRecordAddress(recordSize)
        val addresses = if (!metadata.overflowed) {
            (MEMORY_START_ADDRESS until nextRecordAddress step recordSize).toList()
        } else {
            val usableEnd = memoryEndExclusive -
                ((memoryEndExclusive - MEMORY_START_ADDRESS) % recordSize)
            buildList {
                for (address in nextRecordAddress until usableEnd step recordSize) add(address)
                for (address in MEMORY_START_ADDRESS until nextRecordAddress step recordSize) add(address)
            }
        }
        return addresses.map { SportIdentStationBackupReadRequest(it, recordSize) }
    }

    fun parseRecordReply(
        request: SportIdentStationBackupReadRequest,
        frame: SportIdentFrame,
        extendedPunchRecords: Boolean
    ): SportIdentStationBackupRecord? {
        if (frame.command != SportIdentProtocol.GET_BACKUP || frame.data.size < RESPONSE_PREFIX_SIZE) return null
        val responseAddress = frame.data.readUInt24(RESPONSE_ADDRESS_OFFSET) ?: return null
        if (responseAddress != request.address) return null
        val recordBytes = frame.data.copyOfRange(RESPONSE_PREFIX_SIZE, frame.data.size)
        if (recordBytes.size != request.byteCount) return null
        return if (extendedPunchRecords) {
            parseExtendedRecord(request.address, recordBytes)
        } else {
            parseLegacyRecord(request.address, recordBytes)
        }
    }

    fun parseExtendedRecord(address: Int, bytes: ByteArray): SportIdentStationBackupRecord? {
        if (bytes.size != SportIdentStationBackupMetadata.EXTENDED_RECORD_SIZE || bytes.isErased()) return null
        val cardNumber = bytes.readUInt24(0) ?: return null
        if (cardNumber !in MIN_BACKUP_CARD_NUMBER..MAX_BACKUP_CARD_NUMBER) return null
        val year = 2000 + (bytes[3].toUnsignedInt() ushr 2)
        val month = ((bytes[3].toUnsignedInt() and 0x03) shl 2) or
            (bytes[4].toUnsignedInt() ushr 6)
        val day = (bytes[4].toUnsignedInt() ushr 1) and 0x1f
        val isPm = bytes[4].toUnsignedInt() and 0x01 != 0
        val date = validDate(year, month, day) ?: return null
        val timeHigh = bytes[5].toUnsignedInt()
        val errorCode = if (timeHigh >= ERROR_MARKER) timeHigh and 0x0f else null
        val time = if (errorCode == null) {
            val halfDaySeconds = (timeHigh shl 8) or bytes[6].toUnsignedInt()
            preciseTime(halfDaySeconds, bytes[7].toUnsignedInt(), isPm) ?: return null
        } else {
            null
        }
        return SportIdentStationBackupRecord(
            address = address,
            cardNumber = cardNumber,
            recordedDate = date,
            recordedTime = time,
            dayOfWeek = date.dayOfWeek,
            halfDay = if (isPm) "PM" else "AM",
            errorCode = errorCode
        )
    }

    fun parseLegacyRecord(address: Int, bytes: ByteArray): SportIdentStationBackupRecord? {
        if (bytes.size != SportIdentStationBackupMetadata.LEGACY_RECORD_SIZE || bytes.isErased()) return null
        val lowCardNumber = (bytes[0].toUnsignedInt() shl 8) or bytes[1].toUnsignedInt()
        val series = bytes[5].toUnsignedInt()
        val cardNumber = when {
            series <= 4 -> series * 100_000 + lowCardNumber
            else -> series * 65_536 + lowCardNumber
        }
        if (cardNumber !in MIN_BACKUP_CARD_NUMBER..MAX_BACKUP_CARD_NUMBER) return null
        val dayHalf = bytes[4].toUnsignedInt()
        val siDay = (dayHalf ushr 1) and 0x07
        val dayOfWeek = siDay.toDayOfWeek() ?: return null
        val isPm = dayHalf and 0x01 != 0
        val timeHigh = bytes[2].toUnsignedInt()
        val errorCode = if (timeHigh >= ERROR_MARKER) timeHigh and 0x0f else null
        val time = if (errorCode == null) {
            preciseTime(
                halfDaySeconds = (timeHigh shl 8) or bytes[3].toUnsignedInt(),
                subsecond = 0,
                isPm = isPm
            ) ?: return null
        } else {
            null
        }
        return SportIdentStationBackupRecord(
            address = address,
            cardNumber = cardNumber,
            recordedDate = null,
            recordedTime = time,
            dayOfWeek = dayOfWeek,
            halfDay = if (isPm) "PM" else "AM",
            errorCode = errorCode
        )
    }

    private fun SportIdentStationBackupMetadata.nextRecordAddress(recordSize: Int): Int {
        val alignedAsNext = (nextAddress - MEMORY_START_ADDRESS) % recordSize == 0
        val alignedAfterLast = (nextAddress + 1 - MEMORY_START_ADDRESS) % recordSize == 0
        return when {
            alignedAsNext -> nextAddress
            alignedAfterLast -> nextAddress + 1
            else -> error(
                "Station backup pointer 0x${nextAddress.toString(16).uppercase()} " +
                    "is not aligned to $recordSize-byte records."
            )
        }
    }

    private fun preciseTime(halfDaySeconds: Int, subsecond: Int, isPm: Boolean): LocalTime? {
        if (halfDaySeconds !in 0 until SECONDS_PER_HALF_DAY) return null
        val secondsOfDay = halfDaySeconds + if (isPm) SECONDS_PER_HALF_DAY else 0
        val nanos = ((subsecond * 1_000_000_000L + 128L) / 256L).toInt()
        return runCatching { LocalTime.ofSecondOfDay(secondsOfDay.toLong()).withNano(nanos) }.getOrNull()
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

    private fun ByteArray.readSystemInfoUInt8(offset: Int): Int? =
        getOrNull(SYSTEM_INFO_RESPONSE_PREFIX_SIZE + offset)?.toUnsignedInt()

    private fun ByteArray.readSystemInfoUInt16(offset: Int): Int? {
        val high = readSystemInfoUInt8(offset) ?: return null
        val low = readSystemInfoUInt8(offset + 1) ?: return null
        return (high shl 8) or low
    }

    private fun ByteArray.readUInt24(offset: Int): Int? {
        val high = getOrNull(offset)?.toUnsignedInt() ?: return null
        val middle = getOrNull(offset + 1)?.toUnsignedInt() ?: return null
        val low = getOrNull(offset + 2)?.toUnsignedInt() ?: return null
        return (high shl 16) or (middle shl 8) or low
    }

    private fun ByteArray.isErased(): Boolean =
        all { it == 0x00.toByte() || it == 0xEE.toByte() || it == 0xFF.toByte() }

    private fun Int.toDayOfWeek(): DayOfWeek? =
        when (this) {
            0 -> DayOfWeek.SUNDAY
            1 -> DayOfWeek.MONDAY
            2 -> DayOfWeek.TUESDAY
            3 -> DayOfWeek.WEDNESDAY
            4 -> DayOfWeek.THURSDAY
            5 -> DayOfWeek.FRIDAY
            6 -> DayOfWeek.SATURDAY
            else -> null
        }

    private const val SYSTEM_INFO_RESPONSE_PREFIX_SIZE = 3
    private const val BACKUP_POINTER_HIGH_OFFSET = 0x1C
    private const val BACKUP_POINTER_LOW_OFFSET = 0x21
    private const val MEMORY_SIZE_OFFSET = 0x0D
    private const val MEMORY_OVERFLOW_OFFSET = 0x3D
    private const val STATION_MODE_OFFSET = 0x71
    private const val PROTOCOL_OFFSET = 0x74
    private const val EXTENDED_PROTOCOL_FLAG = 0x01
    private const val RESPONSE_ADDRESS_OFFSET = 2
    private const val RESPONSE_PREFIX_SIZE = 5
    private const val ERROR_MARKER = 0xF0
    private const val SECONDS_PER_HALF_DAY = 12 * 60 * 60
    private const val MIN_BACKUP_CARD_NUMBER = 1
    private const val MAX_BACKUP_CARD_NUMBER = 0xFF_FFFF
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
