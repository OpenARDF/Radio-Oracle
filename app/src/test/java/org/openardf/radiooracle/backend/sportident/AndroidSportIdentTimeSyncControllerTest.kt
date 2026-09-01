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

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationTimeCodec
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncCommandStep
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncProtocol

class AndroidSportIdentTimeSyncControllerTest {
    @Test
    fun inspectionUsesRemoteModeForConnectedDownloadStationAndReadsTargetClock() {
        val computerTime = LocalDateTime.parse("2026-09-01T10:00:00")
        val stationTime = computerTime.plusSeconds(2)
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND, stationTime, tick = 0),
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(transport, listOf(computerTime))

        val inspection = controller.inspectDownloadStation()

        assertEquals(781234, inspection.stationInfo.serialNumber)
        assertEquals(45, inspection.stationInfo.stationCodeNumber)
        assertEquals(stationTime, inspection.stationTime)
        assertEquals(2_000L, inspection.stationMinusComputerMillis)
        assertTrue(inspection.requiresCoupledStation)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            transport.commands
        )
    }

    @Test
    fun syncRequiresExplicitConfirmationBeforeSendingAnyCommand() {
        val transport = FakeTransport()
        val controller = controller(transport, emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            controller.syncTime(writeEnabled = false)
        }

        assertEquals("SPORTident time sync writes require explicit user confirmation.", error.message)
        assertTrue(transport.commands.isEmpty())
    }

    @Test
    fun syncWritesAppliesVerifiesAndSleepsBeforeLeavingRemoteMode() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00.500")
        val computerAfter = LocalDateTime.parse("2026-09-01T10:00:00.550")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            stationTimeFrame(SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND, targetTime, tick = 128),
            frame(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND),
            stationTimeFrame(SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND, targetTime, tick = 128),
            frame(SportIdentProtocol.PROBE_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            frame(SportIdentTimeSyncProtocol.POWER_OFF_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(transport, listOf(targetTime, computerAfter))

        val result = controller.syncTime(
            writeEnabled = true,
            putStationToSleepAfterSync = true
        )

        assertEquals(targetTime, result.sourceTime)
        assertEquals(targetTime.minusMinutes(1).withNano(0), result.beforeTime)
        assertEquals(targetTime.withNano(0), result.confirmedTime)
        assertEquals(-50L, result.confirmedStationMinusComputerMillis)
        assertEquals(true, result.stationPowerStateWrite?.confirmed)
        assertEquals(1, result.attempts)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            transport.commands
        )
    }

    @Test
    fun syncUsesFreshReadbackAndSeparateSleepWhenImmediatePostApplyReadTimesOut() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00.500")
        val computerAfterApply = LocalDateTime.parse("2026-09-01T10:00:00.525")
        val fallbackComputerTime = LocalDateTime.parse("2026-09-01T10:00:00.550")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            stationTimeFrame(SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND, targetTime, tick = 128),
            frame(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND),
            null,
            frame(SportIdentProtocol.PROBE_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND, targetTime, tick = 128),
            frame(SportIdentProtocol.PROBE_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            frame(SportIdentTimeSyncProtocol.POWER_OFF_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(
            transport,
            listOf(targetTime, computerAfterApply, fallbackComputerTime)
        )

        val result = controller.syncTime(
            writeEnabled = true,
            putStationToSleepAfterSync = true,
            expectedStationSerialNumber = 781234
        )

        assertEquals(-50L, result.confirmedStationMinusComputerMillis)
        assertEquals(true, result.stationPowerStateWrite?.confirmed)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            transport.commands
        )
    }

    @Test
    fun syncStillSucceedsWhenImmediateAndFallbackPostApplyReadsTimeOut() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00.500")
        val computerAfterApply = LocalDateTime.parse("2026-09-01T10:00:00.525")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            stationTimeFrame(SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND, targetTime, tick = 128),
            frame(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND),
            null,
            null
        )
        val controller = controller(
            transport = transport,
            currentTimes = listOf(targetTime, computerAfterApply),
            readerStationModeCode = 5
        )

        val result = controller.syncTime(
            writeEnabled = true,
            putStationToSleepAfterSync = false,
            expectedStationSerialNumber = 781234
        )

        assertEquals(targetTime.withNano(0), result.confirmedTime)
        assertEquals(null, result.confirmedStationMinusComputerMillis)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            transport.commands
        )
    }

    @Test
    fun syncDoesNotApplyWhenWriteAcknowledgementIsOutsideTolerance() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                targetTime.plusMinutes(1),
                tick = 0
            ),
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(transport, listOf(targetTime))

        assertThrows(IllegalStateException::class.java) {
            controller.syncTime(writeEnabled = true, putStationToSleepAfterSync = false)
        }

        assertFalse(transport.commands.contains(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND))
        assertEquals(SportIdentProtocol.PROBE_COMMAND, transport.commands.last())
    }

    @Test
    fun syncReportsExplicitWriteNegativeAcknowledgementWithoutRetrying() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00.125")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            frame(SportIdentProtocol.PROBE_COMMAND),
            negativeAcknowledgementCommandIndices = setOf(3)
        )
        val controller = controller(transport, listOf(targetTime))

        val error = assertThrows(IllegalStateException::class.java) {
            controller.syncTime(
                writeEnabled = true,
                putStationToSleepAfterSync = false,
                expectedStationSerialNumber = 781234
            )
        }

        assertTrue(error.message.orEmpty().contains("Reseat the station on the coupling stick"))
        assertEquals(
            1,
            transport.commands.count { it == SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND }
        )
        assertFalse(transport.commands.contains(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND))
    }

    @Test
    fun syncDoesNotRetryAmbiguousWriteTimeout() {
        val targetTime = LocalDateTime.parse("2026-09-01T10:00:00.125")
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 781234, stationCode = 45),
            stationTimeFrame(
                SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                targetTime.minusMinutes(1),
                tick = 0
            ),
            null,
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(transport, listOf(targetTime))

        val error = assertThrows(IllegalStateException::class.java) {
            controller.syncTime(
                writeEnabled = true,
                putStationToSleepAfterSync = false,
                expectedStationSerialNumber = 781234
            )
        }

        assertEquals("SPORTident station did not reply to Write station time.", error.message)
        assertEquals(
            1,
            transport.commands.count { it == SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND }
        )
        assertFalse(transport.commands.contains(SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND))
    }

    @Test
    fun syncRefusesToWriteWhenTargetChangedAfterInspection() {
        val transport = FakeTransport(
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 999999, stationCode = 46),
            frame(SportIdentProtocol.PROBE_COMMAND),
            frame(SportIdentProtocol.PROBE_COMMAND),
            systemInfoFrame(serial = 999999, stationCode = 46),
            frame(SportIdentProtocol.PROBE_COMMAND)
        )
        val controller = controller(transport, emptyList())

        val error = assertThrows(IllegalStateException::class.java) {
            controller.syncTime(
                writeEnabled = true,
                putStationToSleepAfterSync = false,
                expectedStationSerialNumber = 781234
            )
        }

        assertTrue(error.message.orEmpty().contains("target changed after inspection"))
        assertFalse(transport.commands.contains(SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND))
    }

    private fun controller(
        transport: FakeTransport,
        currentTimes: List<LocalDateTime>,
        readerStationModeCode: Int = 8
    ): AndroidSportIdentTimeSyncController {
        val times = ArrayDeque(currentTimes)
        return AndroidSportIdentTimeSyncController(
            transport = transport,
            readerStationInfo = {
                SportIdentStationInfo(
                    serialNumber = 554896,
                    extendedMode = true,
                    stationCodeNumber = 1,
                    stationModeCode = readerStationModeCode
                )
            },
            currentTime = { times.removeFirst() },
            sleepMillis = {}
        )
    }

    private class FakeTransport(
        vararg replies: SportIdentFrame?,
        private val negativeAcknowledgementCommandIndices: Set<Int> = emptySet()
    ) : AndroidSportIdentCommandTransport {
        private val replies = replies.toMutableList()
        val commands = mutableListOf<Byte>()

        override fun sendWakePulse(): Boolean = true

        override fun sendCommand(step: SportIdentTimeSyncCommandStep): AndroidSportIdentCommandResult {
            val commandIndex = commands.size
            commands += step.command
            if (commandIndex in negativeAcknowledgementCommandIndices) {
                return AndroidSportIdentCommandResult.NegativeAcknowledgement
            }
            val frame = if (replies.isEmpty()) null else replies.removeAt(0)
            return frame?.let(AndroidSportIdentCommandResult::Reply)
                ?: AndroidSportIdentCommandResult.NoReply
        }
    }

    private companion object {
        fun frame(command: Byte, data: ByteArray = byteArrayOf()) =
            SportIdentFrame(command, data, byteArrayOf(), extended = true, crcValid = true)

        fun systemInfoFrame(serial: Int, stationCode: Int): SportIdentFrame {
            val data = ByteArray(21)
            data[1] = stationCode.toByte()
            data[3] = ((serial ushr 24) and 0xff).toByte()
            data[4] = ((serial ushr 16) and 0xff).toByte()
            data[5] = ((serial ushr 8) and 0xff).toByte()
            data[6] = (serial and 0xff).toByte()
            data[20] = 0x05
            return frame(SportIdentProtocol.GET_SYSTEM_INFO, data)
        }

        fun stationTimeFrame(
            command: Byte,
            time: LocalDateTime,
            tick: Int
        ): SportIdentFrame {
            val payload = SportIdentStationTimeCodec.encodePayload(time).also {
                it[6] = tick.toByte()
            }
            return frame(command, byteArrayOf(0x00, 0x01) + payload)
        }
    }
}
