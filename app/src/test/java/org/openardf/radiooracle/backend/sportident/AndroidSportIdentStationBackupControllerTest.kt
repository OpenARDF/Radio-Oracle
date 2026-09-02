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

package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncCommandStep

class AndroidSportIdentStationBackupControllerTest {
    @Test
    fun readsCoupledPunchRecordsWithoutSendingAnyDestructiveCommand() {
        val transport = FakeTransport()
        val progress = mutableListOf<Pair<Int, Int>>()
        val controller = AndroidSportIdentStationBackupController(
            transport = transport,
            readerStationInfo = {
                SportIdentStationInfo(
                    serialNumber = 554896,
                    extendedMode = true,
                    stationCodeNumber = 14,
                    stationModeCode = 8
                )
            },
            sleepMillis = {}
        )

        val snapshot = controller.readBackup { completed, total -> progress += completed to total }

        assertEquals(575_853, snapshot.stationInfo.serialNumber)
        assertEquals(45, snapshot.stationInfo.stationCodeNumber)
        assertEquals(listOf(575_914, 1_234_567), snapshot.records.map { it.cardNumber })
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals(2, transport.steps.count { it.command == SportIdentProtocol.GET_BACKUP })
        assertFalse(transport.steps.any { it.command == 0xF5.toByte() })
        assertEquals(1, transport.wakePulseCount)
        assertTrue(transport.steps.first().payload.contentEquals(byteArrayOf(0x53)))
        assertTrue(transport.steps.last().payload.contentEquals(byteArrayOf(0x4D)))
    }

    @Test
    fun retriesWakeAndRemoteSelectionBeforeReadingSleepingCoupledStation() {
        val transport = FakeTransport(failedMetadataReplies = 3)
        val sleeps = mutableListOf<Long>()
        val controller = AndroidSportIdentStationBackupController(
            transport = transport,
            readerStationInfo = {
                SportIdentStationInfo(
                    serialNumber = 554896,
                    extendedMode = true,
                    stationCodeNumber = 14,
                    stationModeCode = 8
                )
            },
            sleepMillis = { sleeps += it }
        )

        val snapshot = controller.readBackup()

        assertEquals(2, transport.remoteSelectionAttempts)
        assertEquals(2, transport.wakePulseCount)
        assertTrue(sleeps.count { it == 250L } >= 3)
        assertEquals(2, sleeps.count { it == 150L })
        assertEquals(listOf(575_914, 1_234_567), snapshot.records.map { it.cardNumber })
        assertFalse(transport.steps.any { it.command == 0xF5.toByte() })
    }

    private class FakeTransport(
        private val failedRemoteSelections: Int = 0,
        private val failedMetadataReplies: Int = 0
    ) : AndroidSportIdentCommandTransport {
        val steps = mutableListOf<SportIdentTimeSyncCommandStep>()
        var wakePulseCount = 0
        var remoteSelectionAttempts = 0
        var metadataAttempts = 0

        override fun sendWakePulse(): Boolean {
            wakePulseCount += 1
            return true
        }

        override fun sendCommand(step: SportIdentTimeSyncCommandStep): SportIdentCommandResult {
            steps += step
            if (
                step.command == SportIdentProtocol.PROBE_COMMAND &&
                step.payload.contentEquals(byteArrayOf(0x53))
            ) {
                remoteSelectionAttempts += 1
                if (remoteSelectionAttempts <= failedRemoteSelections) {
                    return SportIdentCommandResult.NoReply
                }
            }
            if (step.command == SportIdentProtocol.GET_SYSTEM_INFO) {
                metadataAttempts += 1
                if (metadataAttempts <= failedMetadataReplies) {
                    return SportIdentCommandResult.NegativeAcknowledgement
                }
            }
            val frame = when (step.command) {
                SportIdentProtocol.GET_SYSTEM_INFO -> systemInfoFrame()
                SportIdentProtocol.GET_BACKUP -> backupFrame(step.payload)
                else -> frame(step.command, byteArrayOf())
            }
            return SportIdentCommandResult.Reply(frame)
        }
    }

    private companion object {
        fun systemInfoFrame(): SportIdentFrame {
            val data = ByteArray(131)
            data[1] = 45
            val serial = 575_853
            data[3] = (serial ushr 24).toByte()
            data[4] = (serial ushr 16).toByte()
            data[5] = (serial ushr 8).toByte()
            data[6] = serial.toByte()
            data[20] = 2
            data[3 + 0x0D] = 128.toByte()
            data[3 + 0x1C] = 0
            data[3 + 0x1D] = 0
            data[3 + 0x21] = 0x01
            data[3 + 0x22] = 0x10
            data[3 + 0x3D] = 0
            data[3 + 0x71] = 2
            data[3 + 0x74] = 1
            data[119] = 1
            return frame(SportIdentProtocol.GET_SYSTEM_INFO, data)
        }

        fun backupFrame(payload: ByteArray): SportIdentFrame {
            val address = ((payload[0].toInt() and 0xff) shl 16) or
                ((payload[1].toInt() and 0xff) shl 8) or
                (payload[2].toInt() and 0xff)
            val record = when (address) {
                0x100 -> byteArrayOf(
                    0x08, 0xC9.toByte(), 0xAA.toByte(), 0x6A, 0x43, 0x0E, 0x8B.toByte(), 0
                )
                else -> byteArrayOf(
                    0x12, 0xD6.toByte(), 0x87.toByte(), 0x6A, 0x43, 0x0E, 0x8C.toByte(), 0
                )
            }
            return frame(
                SportIdentProtocol.GET_BACKUP,
                byteArrayOf(0, 45, payload[0], payload[1], payload[2]) + record
            )
        }

        fun frame(command: Byte, data: ByteArray): SportIdentFrame =
            SportIdentFrame(
                command = command,
                data = data,
                raw = byteArrayOf(),
                extended = true,
                crcValid = true
            )
    }
}
