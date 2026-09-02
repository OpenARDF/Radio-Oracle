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

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SportIdentStationBackupProtocolTest {
    @Test
    fun requestsCompatibleSystemInformationLength() {
        assertContentEquals(
            byteArrayOf(0x00, 0x75),
            SportIdentStationBackupProtocol.readSystemInfoStep().payload
        )
    }

    @Test
    fun recognizesFieldPunchModesAndTheirPublicLabels() {
        assertEquals("CONTROL", SportIdentStationMode.labelForModeCode(2))
        assertEquals("START", SportIdentStationMode.labelForModeCode(3))
        assertEquals("FINISH", SportIdentStationMode.labelForModeCode(4))
        assertEquals("CLEAR", SportIdentStationMode.labelForModeCode(7))
        assertEquals("CHECK", SportIdentStationMode.labelForModeCode(10))
        assertEquals(true, SportIdentStationMode.isPunchBackupModeCode(2))
        assertEquals(true, SportIdentStationMode.isPunchBackupModeCode(10))
        assertEquals(false, SportIdentStationMode.isPunchBackupModeCode(5))
    }

    @Test
    fun parsesBackupMetadataAndPlansOneReadPerRecord() {
        val metadata = assertNotNull(
            SportIdentStationBackupProtocol.parseMetadata(
                systemInfoFrame(pointer = 0x118, overflowed = false, extended = true, mode = 2)
            )
        )

        assertEquals(0x118, metadata.nextAddress)
        assertEquals(false, metadata.overflowed)
        assertEquals(128 * 1024, metadata.memorySizeBytes)
        assertEquals(8, metadata.recordSize)
        val requests = SportIdentStationBackupProtocol.readRequests(metadata)
        assertEquals(listOf(0x100, 0x108, 0x110), requests.map { it.address })
        assertEquals(listOf(8, 8, 8), requests.map { it.byteCount })
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x10, 0x08), requests.last().payload)
    }

    @Test
    fun overflowedRingStartsAtAlignedOldestRecordAndWraps() {
        val metadata = SportIdentStationBackupMetadata(
            nextAddress = 0x117,
            overflowed = true,
            memorySizeBytes = 0x130,
            extendedPunchRecords = true,
            stationModeCode = 2
        )

        val requests = SportIdentStationBackupProtocol.readRequests(metadata)

        assertEquals(listOf(0x118, 0x120, 0x128, 0x100, 0x108, 0x110), requests.map { it.address })
    }

    @Test
    fun rejectsReadoutModeBecauseItsBackupContainsCardImages() {
        val metadata = SportIdentStationBackupMetadata(
            nextAddress = 0x100,
            overflowed = false,
            memorySizeBytes = 0x20000,
            extendedPunchRecords = true,
            stationModeCode = SportIdentStationMode.READOUT_MODE_CODE
        )

        val error = assertFailsWith<IllegalArgumentException> {
            SportIdentStationBackupProtocol.readRequests(metadata)
        }

        assertEquals("Station backup history is not a punch-record format in READOUT mode.", error.message)
    }

    @Test
    fun parsesModernEightBytePunchWithDateAndSubsecondTime() {
        val record = assertNotNull(
            SportIdentStationBackupProtocol.parseExtendedRecord(
                address = 0x100,
                bytes = byteArrayOf(
                    0x12, 0xD6.toByte(), 0x87.toByte(),
                    0x6A, 0x43, 0x0E, 0x8B.toByte(), 0x80.toByte()
                )
            )
        )

        assertEquals(1_234_567, record.cardNumber)
        assertEquals(LocalDate.of(2026, 9, 1), record.recordedDate)
        assertEquals(LocalTime.of(13, 2, 3, 500_000_000), record.recordedTime)
        assertEquals(DayOfWeek.TUESDAY, record.dayOfWeek)
        assertEquals("PM", record.halfDay)
        assertNull(record.errorCode)
    }

    @Test
    fun preservesPunchErrorWithoutInventingATime() {
        val record = assertNotNull(
            SportIdentStationBackupProtocol.parseExtendedRecord(
                address = 0x108,
                bytes = byteArrayOf(0x08, 0xC9.toByte(), 0xAA.toByte(), 0x6A, 0x43, 0xFA.toByte(), 0, 0)
            )
        )

        assertEquals(575_914, record.cardNumber)
        assertEquals(LocalDate.of(2026, 9, 1), record.recordedDate)
        assertNull(record.recordedTime)
        assertEquals(10, record.errorCode)
        assertEquals("ErrA", record.errorLabel)
    }

    @Test
    fun parsesLegacySixBytePunchWithoutInventingACalendarDate() {
        val record = assertNotNull(
            SportIdentStationBackupProtocol.parseLegacyRecord(
                address = 0x100,
                bytes = byteArrayOf(0x23, 0x45, 0x0E, 0x8B.toByte(), 0x07, 0x08)
            )
        )

        assertEquals(533_317, record.cardNumber)
        assertNull(record.recordedDate)
        assertEquals(LocalTime.of(13, 2, 3), record.recordedTime)
        assertEquals(DayOfWeek.WEDNESDAY, record.dayOfWeek)
    }

    @Test
    fun validatesBackupReplyAddressBeforeDecoding() {
        val request = SportIdentStationBackupReadRequest(0x100, 8)
        val recordBytes = byteArrayOf(
            0x12, 0xD6.toByte(), 0x87.toByte(), 0x6A, 0x43, 0x0E, 0x8B.toByte(), 0x80.toByte()
        )
        val matching = frame(SportIdentProtocol.GET_BACKUP, byteArrayOf(0, 45, 0, 1, 0) + recordBytes)
        val wrongAddress = frame(SportIdentProtocol.GET_BACKUP, byteArrayOf(0, 45, 0, 1, 8) + recordBytes)

        assertNotNull(SportIdentStationBackupProtocol.parseRecordReply(request, matching, true))
        assertNull(SportIdentStationBackupProtocol.parseRecordReply(request, wrongAddress, true))
    }

    private fun systemInfoFrame(
        pointer: Int,
        overflowed: Boolean,
        extended: Boolean,
        mode: Int
    ): SportIdentFrame {
        val data = ByteArray(3 + SportIdentStationBackupProtocol.SYSTEM_INFO_LENGTH)
        data[3 + 0x0D] = 128.toByte()
        data[3 + 0x1C] = (pointer ushr 24).toByte()
        data[3 + 0x1D] = (pointer ushr 16).toByte()
        data[3 + 0x21] = (pointer ushr 8).toByte()
        data[3 + 0x22] = pointer.toByte()
        data[3 + 0x3D] = if (overflowed) 1 else 0
        data[3 + 0x71] = mode.toByte()
        data[3 + 0x74] = if (extended) 1 else 0
        return frame(SportIdentProtocol.GET_SYSTEM_INFO, data)
    }

    private fun frame(command: Byte, data: ByteArray): SportIdentFrame =
        SportIdentFrame(
            command = command,
            data = data,
            raw = byteArrayOf(),
            extended = true,
            crcValid = true
        )
}
