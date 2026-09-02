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

package org.openardf.radiooracle.desktop.usb

import java.time.LocalDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentTimeSyncServiceTest {
    @Test
    fun inspectReportsDisconnectedWhenNoSportIdentPortExists() {
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(emptyList()))

        val inspection = service.inspectDownloadStation()

        assertEquals("No SPORTident USB station detected.", inspection.statusText)
        assertFalse(inspection.canSyncTime)
        assertNull(inspection.portInfo)
        assertEquals("Connect a SPORTident download station before syncing time.", inspection.disabledReason)
    }

    @Test
    fun inspectReportsConnectedSiMasterAsTimeSyncCapable() {
        val stationTime = LocalDateTime.now().plusSeconds(2).withNano(0)
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(stationTime, tick = 0x00),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 1,
                        stationModeCode = 8
                    )
                )
            },
            sleepMillis = {}
        )

        val inspection = service.inspectDownloadStation()

        assertEquals("SI station 554896 connected in SI MASTER mode.", inspection.statusText)
        assertEquals(SportIdentProtocol.BAUDRATE_HIGH, inspection.baudRate)
        assertEquals(554896, inspection.stationInfo?.serialNumber)
        assertTrue(inspection.canSyncTime)
        assertNull(inspection.disabledReason)
        assertTrue(inspection.requiresCoupledStation)
        assertEquals(554896, inspection.coupledStationClock?.stationInfo?.serialNumber)
        assertEquals(1, inspection.coupledStationClock?.stationInfo?.stationCodeNumber)
        assertEquals(stationTime, inspection.coupledStationClock?.stationTime)
        assertTrue((inspection.coupledStationClock?.stationMinusComputerMillis ?: Long.MAX_VALUE) in -5_000L..5_000L)
        assertNull(inspection.coupledStationInspectionError)
        assertTrue(port.writeRequests.any { it.contentEquals(byteArrayOf(SportIdentProtocol.WAKEUP)) })
        assertFalse(port.writeRequests.any { it.contentEquals(DesktopSportIdentTimeSyncProtocol.REMOTE_POWER_OFF_BYTES) })
        assertFalse(port.writeRequests.any {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command ==
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND
        })
    }

    @Test
    fun inspectReadsDirectStationClockForNonMasterStation() {
        val stationTime = LocalDateTime.now().plusSeconds(1).withNano(0)
        val port = FakePort(
            readChunks = listOf(
                normalModeReply(),
                directSystemInfoReply(stationCodeNumber = 32),
                stationTimeReply(stationTime, tick = 0x00)
            ),
            info = ftdiPortInfo()
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 5
                    )
                )
            }
        )

        val inspection = service.inspectDownloadStation()

        assertEquals("SI station 106128 connected in READOUT mode.", inspection.statusText)
        assertTrue(inspection.canSyncTime)
        assertNull(inspection.disabledReason)
        assertFalse(inspection.requiresCoupledStation)
        assertEquals(stationTime, inspection.coupledStationClock?.stationTime)
        assertEquals(32, inspection.coupledStationClock?.stationInfo?.stationCodeNumber)
        assertNull(inspection.coupledStationInspectionError)
        assertArrayEquals(byteArrayOf(SportIdentProtocol.WAKEUP), port.writeRequests.first())
        assertFalse(port.writeRequests.any { it.contentEquals(DesktopSportIdentTimeSyncProtocol.REMOTE_POWER_OFF_BYTES) })
        assertFalse(port.writeRequests.any {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command ==
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND
        })
    }

    @Test
    fun inspectRetriesCoupledStationReadAfterWakeMissesWithoutDisablingReaderDiagnostics() {
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(LocalDateTime.parse("2026-06-27T10:00:00"), tick = 0x00),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 1,
                        stationModeCode = 8
                    )
                )
            },
            sleepMillis = {}
        )

        val inspection = service.inspectDownloadStation()

        assertEquals("SI station 554896 connected in SI MASTER mode.", inspection.statusText)
        assertTrue(inspection.canSyncTime)
        assertTrue(inspection.requiresCoupledStation)
        assertEquals(554896, inspection.coupledStationClock?.stationInfo?.serialNumber)
        assertNull(inspection.coupledStationInspectionError)
        assertEquals(4, port.writeRequests.count { it.contentEquals(byteArrayOf(SportIdentProtocol.WAKEUP)) })
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertFalse(writtenCommands.contains(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND))
    }

    @Test
    fun inspectStillReportsCoupledStationReadFailureAfterWakeRetryMisses() {
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    normalModeReply()
                ),
                listOf(
                    remoteModeReply(),
                    remoteModeReply(),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 1,
                        stationModeCode = 8
                    )
                )
            },
            sleepMillis = {}
        )

        val inspection = service.inspectDownloadStation()

        assertEquals("SI station 554896 connected in SI MASTER mode.", inspection.statusText)
        assertTrue(inspection.canSyncTime)
        assertTrue(inspection.requiresCoupledStation)
        assertNull(inspection.coupledStationClock)
        assertEquals("No coupled station found.", inspection.coupledStationInspectionError)
        assertEquals(4, port.writeRequests.count { it.contentEquals(byteArrayOf(SportIdentProtocol.WAKEUP)) })
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertFalse(writtenCommands.contains(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND))
    }

    @Test
    fun syncTimeRunsValidatedWritePath() {
        val targetTime = LocalDateTime.now().plusSeconds(1).withNano(0)
        val port = FakePort(
            readChunks = listOf(
                remoteModeReply(),
                normalModeReply(),
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime, tick = 0x05),
                applyReply(),
                stationTimeReply(targetTime, tick = 0x05),
                normalModeReply()
            )
        )
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(listOf(port)))

        val result = service.syncTime(targetTime)

        assertEquals(554896, result.stationInfo.serialNumber)
        assertEquals(targetTime.minusMinutes(1), result.beforeTime)
        assertEquals(targetTime, result.confirmedTime)
        assertEquals(targetTime, result.sourceTime)
        assertTrue(result.computerTimeAfterSync != null)
        assertTrue((result.confirmedStationMinusComputerMillis ?: Long.MAX_VALUE) in -5_000L..5_000L)
        assertEquals(0L, result.currentTimeOffsetMillis)
        assertEquals(1, result.attempts)
        assertEquals(
            DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
            port.firstWrittenFrame(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND).command
        )
    }

    @Test
    fun syncTimeUsesCurrentComputerTimeWithFractionalTickByDefault() {
        val targetTime = LocalDateTime.parse("2026-06-27T10:00:00.775")
        val currentTimes = ArrayDeque(
            listOf(
                LocalDateTime.parse("2026-06-27T10:00:00.775"),
                LocalDateTime.parse("2026-06-27T10:00:00.775")
            )
        )
        val port = FakePort(
            readChunks = listOf(
                remoteModeReply(),
                normalModeReply(),
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime, tick = 0xC6),
                applyReply(),
                stationTimeReply(targetTime, tick = 0xC6),
                normalModeReply()
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            currentTime = { currentTimes.removeFirst() },
            sleepMillis = {}
        )

        val result = service.syncTime()

        assertEquals(targetTime, result.sourceTime)
        assertEquals(DesktopSportIdentTimeSyncService.DEFAULT_CURRENT_TIME_OFFSET_MILLIS, result.currentTimeOffsetMillis)
        assertNull(result.secondBoundaryLeadMillis)
        assertEquals(1, result.attempts)
        assertEquals(0L, result.secondBoundaryWaitMillis)
        assertArrayEquals(
            DesktopSportIdentStationTimeCodec.encodePayload(targetTime),
            port.firstWrittenFrame(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND).data
        )
    }

    @Test
    fun syncTimeCanStillUseMeasuredDeltaToCorrectSecondBoundaryAttemptWhenEnabled() {
        val targetTime1 = LocalDateTime.parse("2026-06-27T10:00:01")
        val targetTime2 = LocalDateTime.parse("2026-06-27T10:00:03")
        val currentTimes = ArrayDeque(
            listOf(
                LocalDateTime.parse("2026-06-27T10:00:00.250"),
                LocalDateTime.parse("2026-06-27T10:00:00.900"),
                LocalDateTime.parse("2026-06-27T10:00:01.900"),
                LocalDateTime.parse("2026-06-27T10:00:02"),
                LocalDateTime.parse("2026-06-27T10:00:02.001"),
                LocalDateTime.parse("2026-06-27T10:00:03.100")
            )
        )
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime1.minusMinutes(1), tick = 0x01),
                    stationWriteReply(targetTime1, tick = 0x05),
                    applyReply(),
                    stationTimeReply(targetTime1, tick = 0x00),
                    normalModeReply()
                ),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime1, tick = 0x01),
                    stationWriteReply(targetTime2, tick = 0x00),
                    applyReply(),
                    stationTimeReply(targetTime2, tick = 0x00),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            currentTime = { currentTimes.removeFirst() },
            sleepMillis = {}
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = null,
            writeEnabled = true,
            toleranceSeconds = 0,
            secondBoundaryLeadMillis = 100,
            maxAttempts = 2,
            correctionThresholdMillis = 100,
            alignToSecondBoundary = true
        )

        assertEquals(2, result.attempts)
        assertEquals(999L, result.secondBoundaryLeadMillis)
        assertEquals(targetTime2, result.sourceTime)
        assertEquals(-100L, result.confirmedStationMinusComputerMillis)
        assertEquals(1L, result.secondBoundaryWaitMillis)
        val stationWriteRequests = port.writeRequests.mapNotNull {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)
                ?.takeIf { frame -> frame.command == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND }
                ?.data
        }
        assertEquals(2, stationWriteRequests.size)
        assertArrayEquals(DesktopSportIdentStationTimeCodec.encodePayload(targetTime1), stationWriteRequests[0])
        assertArrayEquals(DesktopSportIdentStationTimeCodec.encodePayload(targetTime2), stationWriteRequests[1])
    }

    @Test
    fun syncTimeFallsBackToSeparateStationReadEstimateWhenPostApplyReadFails() {
        val targetTime = LocalDateTime.parse("2026-06-27T10:00:01")
        val fallbackEstimateTime = LocalDateTime.parse("2026-06-27T10:00:01.100")
        val currentTimes = ArrayDeque(
            listOf(
                LocalDateTime.parse("2026-06-27T10:00:00.250"),
                LocalDateTime.parse("2026-06-27T10:00:00.900"),
                LocalDateTime.parse("2026-06-27T10:00:01.900"),
                fallbackEstimateTime
            )
        )
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(
                    normalModeReply(marker = 0x06),
                    directSystemInfoReply(stationCodeNumber = 32),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01, replyMarker = 0x06),
                    stationWriteReply(targetTime, tick = 0x00, replyMarker = 0x06),
                    applyReply()
                ),
                listOf(
                    normalModeReply(marker = 0x06),
                    directSystemInfoReply(stationCodeNumber = 32),
                    stationTimeReply(targetTime, tick = 0x00, replyMarker = 0x06)
                )
            ),
            info = ftdiPortInfo()
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 8
                    )
                )
            },
            currentTime = { currentTimes.removeFirstOrNull() ?: fallbackEstimateTime },
            sleepMillis = {}
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = null,
            writeEnabled = true,
            toleranceSeconds = 0,
            secondBoundaryLeadMillis = 100,
            maxAttempts = 1,
            correctionThresholdMillis = 100,
            alignToSecondBoundary = true
        )

        assertEquals(1, result.attempts)
        assertEquals(targetTime, result.sourceTime)
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(-100L, result.confirmedStationMinusComputerMillis)
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND })
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND })
        assertEquals(3, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND })
    }

    @Test
    fun dryRunBuildsCapturedConfigPlusSequence() {
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(emptyList()))

        val dryRun = service.dryRun(LocalDateTime.parse("2026-06-27T16:50:12"))

        assertEquals(LocalDateTime.parse("2026-06-27T16:50:12"), dryRun.sourceTime)
        assertEquals(6, dryRun.configPlusSequence.size)
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 F0 01 53 73 0A 03"),
            dryRun.configPlusSequence[0].frameBytes
        )
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 83 02 00 80 BF 17 03"),
            dryRun.configPlusSequence[1].frameBytes
        )
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 F7 00 F7 00 03"),
            dryRun.configPlusSequence[2].frameBytes
        )
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 F6 07 1A 06 1B 0D 44 04 00 10 91 03"),
            dryRun.configPlusSequence[3].frameBytes
        )
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 F9 01 01 17 0A 03"),
            dryRun.configPlusSequence[4].frameBytes
        )
        assertArrayEquals(
            DesktopSportIdentCaptureAnalyzer.hexToBytes("FF 02 F0 01 4D 6D 0A 03"),
            dryRun.configPlusSequence[5].frameBytes
        )
        assertEquals(7, dryRun.validatedWriteSequence.size)
        assertEquals(DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND, dryRun.validatedWriteSequence[5].command)
        assertEquals(SportIdentProtocol.PROBE_COMMAND, dryRun.validatedWriteSequence[6].command)
    }

    @Test
    fun writeTimeWithReadBackRequiresExplicitOptIn() {
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(emptyList()))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.writeTimeWithReadBack(
                sourceTime = LocalDateTime.parse("2026-06-27T03:10:18"),
                writeEnabled = false
            )
        }

        assertEquals("SPORTident time sync writes require explicit hardware opt-in.", error.message)
    }

    @Test
    fun writeTimeWithReadBackSendsWriteAndRequiresMatchingReadBack() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunks = listOf(
                remoteModeReply(),
                normalModeReply(),
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime, tick = 0x05),
                applyReply(),
                stationTimeReply(targetTime, tick = 0x05),
                normalModeReply()
            )
        )
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(listOf(port)))

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0
        )

        assertEquals(554896, result.stationInfo.serialNumber)
        assertEquals(targetTime.minusMinutes(1), result.beforeTime)
        assertEquals(targetTime, result.confirmedTime)
        assertEquals(targetTime, result.sourceTime)
        assertEquals(0L, result.currentTimeOffsetMillis)
        assertEquals(1, result.attempts)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertArrayEquals(
            DesktopSportIdentStationTimeCodec.encodePayload(targetTime),
            port.firstWrittenFrame(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND).data
        )
    }

    @Test
    fun writeTimeWithReadBackUsesDirectBsf7SequenceForFtdiStation() {
        val targetTime = LocalDateTime.parse("2026-06-28T20:32:49")
        val port = FakePort(
            readChunks = listOf(
                normalModeReply(marker = 0x06),
                directSystemInfoReply(stationCodeNumber = 32),
                stationTimeReply(targetTime.minusSeconds(1), tick = 0x04, replyMarker = 0x06),
                stationWriteReply(targetTime, tick = 0x00, replyMarker = 0x06),
                applyReply(),
                stationTimeReply(targetTime, tick = 0x00, replyMarker = 0x06)
            ),
            info = ftdiPortInfo()
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 8
                    )
                )
            }
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0
        )

        assertEquals(106128, result.stationInfo.serialNumber)
        assertEquals(32, result.stationInfo.stationCodeNumber)
        assertEquals(targetTime, result.confirmedTime)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertArrayEquals(
            byteArrayOf(0x4D),
            SportIdentFrameParser.firstFrame(port.writeRequests[0], requireValidCrc = true)?.data
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x80.toByte()),
            SportIdentFrameParser.firstFrame(port.writeRequests[1], requireValidCrc = true)?.data
        )
        assertArrayEquals(
            DesktopSportIdentStationTimeCodec.encodePayload(targetTime),
            SportIdentFrameParser.firstFrame(port.writeRequests[3], requireValidCrc = true)?.data
        )
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
        assertNull(result.stationPowerStateWrite)
    }

    @Test
    fun writeTimeWithReadBackCanPutDirectFtdiStationToSleepAfterSync() {
        val targetTime = LocalDateTime.parse("2026-06-28T20:32:49")
        val port = FakePort(
            readChunks = listOf(
                normalModeReply(marker = 0x06),
                directSystemInfoReply(stationCodeNumber = 32),
                stationTimeReply(targetTime.minusSeconds(1), tick = 0x04, replyMarker = 0x06),
                stationWriteReply(targetTime, tick = 0x00, replyMarker = 0x06),
                applyReply(),
                stationTimeReply(targetTime, tick = 0x00, replyMarker = 0x06),
                powerOffReply()
            ),
            info = ftdiPortInfo()
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 8
                    )
                )
            }
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0,
            putStationToSleepAfterSync = true
        )

        assertEquals(true, result.stationPowerStateWrite?.confirmed)
        assertEquals("Station sleep command confirmed.", result.stationPowerStateWrite?.message)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
    }

    @Test
    fun sleepStationUsesDirectFtdiStationPowerOffSequence() {
        val port = FakePort(
            readChunks = listOf(
                normalModeReply(marker = 0x06),
                powerOffReply()
            ),
            info = ftdiPortInfo()
        )
        val service = DesktopSportIdentTimeSyncService(
            portSelector = ftdiPortSelector(port),
            connectStation = {
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 8
                    )
                )
            }
        )

        val result = service.sleepStation()

        assertEquals(true, result.confirmed)
        assertEquals("Station sleep command confirmed.", result.message)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
    }

    @Test
    fun writeTimeWithReadBackCanPutRemoteStationToSleepAfterSync() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x00),
                    stationWriteReply(targetTime, tick = 0x00),
                    applyReply(),
                    stationTimeReply(targetTime, tick = 0x00),
                    normalModeReply()
                ),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    powerOffReply(),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(listOf(port)))

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0,
            putStationToSleepAfterSync = true
        )

        assertEquals(true, result.stationPowerStateWrite?.confirmed)
        assertEquals("Remote station sleep command confirmed.", result.stationPowerStateWrite?.message)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.GET_SYSTEM_INFO,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertEquals(
            DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
            port.firstWrittenFrame(DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND).command
        )
        assertFalse(port.writeRequests.any { it.contentEquals(DesktopSportIdentTimeSyncProtocol.REMOTE_POWER_OFF_BYTES) })
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
    }

    @Test
    fun sleepStationUsesRemoteStationPowerOffSequence() {
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    powerOffReply(),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(listOf(port)))

        val result = service.sleepStation()

        assertEquals(true, result.confirmed)
        assertEquals("Remote station sleep command confirmed.", result.message)
        assertEquals(
            listOf(
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                SportIdentProtocol.PROBE_COMMAND,
                DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
                SportIdentProtocol.PROBE_COMMAND
            ),
            port.writeRequests.map { SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command }
        )
        assertFalse(port.writeRequests.any { it.contentEquals(DesktopSportIdentTimeSyncProtocol.REMOTE_POWER_OFF_BYTES) })
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
    }

    @Test
    fun writeTimeWithReadBackRetriesUnconfirmedRemoteStationSleepAfterSync() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x00),
                    stationWriteReply(targetTime, tick = 0x00),
                    applyReply(),
                    stationTimeReply(targetTime, tick = 0x00),
                    normalModeReply()
                ),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(remoteModeReply()),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    powerOffReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            sleepMillis = {}
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0,
            putStationToSleepAfterSync = true
        )

        assertEquals(true, result.stationPowerStateWrite?.confirmed)
        assertEquals(
            "Remote station sleep command confirmed. Succeeded on attempt 2.",
            result.stationPowerStateWrite?.message
        )
        val powerOffRequests = port.writeRequests.mapNotNull {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)
                ?.takeIf { frame -> frame.command == DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND }
        }
        assertEquals(2, powerOffRequests.size)
        assertTrue(
            port.writeRequests
                .mapNotNull { SportIdentFrameParser.firstFrame(it, requireValidCrc = true) }
                .count { frame ->
                    frame.command == SportIdentProtocol.PROBE_COMMAND &&
                        frame.data.contentEquals(DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep().payload)
                } >= 2
        )
        assertFalse(port.writeRequests.any { it.contentEquals(DesktopSportIdentTimeSyncProtocol.REMOTE_POWER_OFF_BYTES) })
        assertTrue(port.writeRequests.all { it.firstOrNull() == SportIdentProtocol.WAKEUP })
    }

    @Test
    fun writeTimeWithReadBackReportsSleepingCoupledStationBeforeWritingTime() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(remoteModeReply()),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(remoteModeReply())
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            commandClient = fastCommandClient()
        )

        val error = assertThrows(IllegalStateException::class.java) {
            service.writeTimeWithReadBack(
                sourceTime = targetTime,
                writeEnabled = true,
                toleranceSeconds = 0
            )
        }

        assertEquals(
            "Remote/coupled SPORTident station did not answer system-info read. " +
                "Confirm the target station is awake and coupled to the download station.",
            error.message
        )
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertFalse(writtenCommands.contains(DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND))
    }

    @Test
    fun writeTimeWithReadBackRetriesOnceWhenCoupledStationDoesNotAnswerBeforeWrite() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(remoteModeReply()),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                    stationWriteReply(targetTime, tick = 0x05),
                    applyReply(),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            commandClient = fastCommandClient()
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0
        )

        assertEquals(2, result.attempts)
        assertEquals(targetTime, result.confirmedTime)
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND })
    }

    @Test
    fun writeTimeWithReadBackRetriesExplicitWriteNakInFreshVerifiedTransaction() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                    byteArrayOf(SportIdentProtocol.NAK),
                    normalModeReply()
                ),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                    stationWriteReply(targetTime, tick = 0x05),
                    applyReply(),
                    stationTimeReply(targetTime, tick = 0x05),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            commandClient = fastCommandClient(),
            sleepMillis = {}
        )

        val result = service.writeTimeWithReadBack(
            sourceTime = targetTime,
            writeEnabled = true,
            toleranceSeconds = 0
        )

        assertEquals(2, result.attempts)
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(2, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND })
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND })
    }

    @Test
    fun writeTimeWithReadBackDoesNotRetryAmbiguousWriteNoReply() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01)
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            commandClient = fastCommandClient(),
            sleepMillis = {}
        )

        val error = assertThrows(IllegalStateException::class.java) {
            service.writeTimeWithReadBack(
                sourceTime = targetTime,
                writeEnabled = true,
                toleranceSeconds = 0
            )
        }

        assertEquals("SPORTident station did not reply to Write station time.", error.message)
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND })
        assertFalse(writtenCommands.contains(DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND))
    }

    @Test
    fun writeTimeWithReadBackRefusesChangedStationOnExplicitNakRetry() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunksByOpen = listOf(
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(serialNumber = 554896),
                    stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                    byteArrayOf(SportIdentProtocol.NAK),
                    normalModeReply()
                ),
                listOf(remoteModeReply(), normalModeReply()),
                listOf(
                    remoteModeReply(),
                    systemInfoReply(serialNumber = 575748),
                    normalModeReply()
                )
            )
        )
        val service = DesktopSportIdentTimeSyncService(
            portProvider = FakePortProvider(listOf(port)),
            commandClient = fastCommandClient(),
            sleepMillis = {}
        )

        val error = assertThrows(IllegalStateException::class.java) {
            service.writeTimeWithReadBack(
                sourceTime = targetTime,
                writeEnabled = true,
                toleranceSeconds = 0
            )
        }

        assertEquals(
            "The connected target changed during time sync: expected station 554896 but found 575748. " +
                "Inspect again before syncing.",
            error.message
        )
        val writtenCommands = port.writeRequests.map {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(1, writtenCommands.count { it == DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND })
        assertFalse(writtenCommands.contains(DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND))
    }

    @Test
    fun writeTimeWithReadBackFailsWhenReadBackIsOutsideTolerance() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunks = listOf(
                remoteModeReply(),
                normalModeReply(),
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime.plusSeconds(5), tick = 0x05),
                normalModeReply()
            )
        )
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(listOf(port)))

        val error = assertThrows(IllegalStateException::class.java) {
            service.writeTimeWithReadBack(
                sourceTime = targetTime,
                writeEnabled = true,
                toleranceSeconds = 2
            )
        }

        assertEquals(
            "SPORTident station time read-back 2026-06-27T03:10:23 differed from requested " +
                "2026-06-27T03:10:18 by 5s, tolerance 2s.",
            error.message
        )
    }

    private class FakePortProvider(private val ports: List<DesktopSerialPort>) : DesktopSerialPortProvider {
        override fun listPorts(): List<DesktopSerialPort> = ports
        override fun getPort(systemPortPath: String): DesktopSerialPort =
            ports.first { it.info.systemPortPath == systemPortPath }
    }

    private class FakeDiscoverySettings(
        private val mode: DesktopSportIdentPortDiscoveryMode
    ) : DesktopSportIdentPortDiscoverySettings {
        override fun sportIdentPortDiscoveryMode(): DesktopSportIdentPortDiscoveryMode = mode
        override fun rememberedSportIdentFtdiPortPath(): String? = null
        override fun rememberSportIdentFtdiPortPath(portPath: String) = Unit
    }

    private class FakePort(
        readChunks: List<ByteArray> = emptyList(),
        readChunksByOpen: List<List<ByteArray>>? = null,
        override val info: DesktopSerialPortInfo = sportIdentUsbPortInfo()
    ) : DesktopSerialPort {
        private var pending = ArrayDeque(readChunks)
        private val pendingByOpen = readChunksByOpen
        private var openCount = 0
        val writeRequests = mutableListOf<ByteArray>()
        private var open = false

        override val isOpen: Boolean
            get() = open

        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean {
            pendingByOpen?.let { queues ->
                pending = ArrayDeque(queues.getOrElse(openCount) { emptyList() })
                openCount += 1
            }
            open = true
            return true
        }
        override fun close() {
            open = false
        }
        override fun write(bytes: ByteArray): Int {
            writeRequests += bytes
            return bytes.size
        }
        override fun read(maxBytes: Int): ByteArray =
            pending.removeFirstOrNull() ?: byteArrayOf()
    }

    private companion object {
        fun remoteModeReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.PROBE_COMMAND,
                data = byteArrayOf(0x00, 0x0F, 0x53)
            )

        fun normalModeReply(marker: Byte = 0x0F): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.PROBE_COMMAND,
                data = byteArrayOf(0x00, marker, 0x4D)
            )

        fun systemInfoReply(serialNumber: Int = 554896): ByteArray {
            val data = ByteArray(21)
            data[1] = 0x01
            data[3] = ((serialNumber ushr 24) and 0xff).toByte()
            data[4] = ((serialNumber ushr 16) and 0xff).toByte()
            data[5] = ((serialNumber ushr 8) and 0xff).toByte()
            data[6] = (serialNumber and 0xff).toByte()
            data[20] = 0x08
            return SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.GET_SYSTEM_INFO,
                data = data
            )
        }

        fun directSystemInfoReply(stationCodeNumber: Int): ByteArray {
            val data = ByteArray(131)
            data[1] = 0x06
            data[3] = 0x00
            data[4] = 0x01
            data[5] = 0x9E.toByte()
            data[6] = 0x90.toByte()
            data[17] = stationCodeNumber.toByte()
            data[20] = 0x08
            data[119] = 0x01
            return SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.GET_SYSTEM_INFO,
                data = data
            )
        }

        fun stationTimeReply(time: LocalDateTime, tick: Int, replyMarker: Byte = 0x01): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, replyMarker) + stationTimePayload(time, tick)
            )

        fun stationWriteReply(time: LocalDateTime, tick: Int, replyMarker: Byte = 0x01): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, replyMarker) + stationTimePayload(time, tick)
            )

        fun applyReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, 0x01, 0x01)
            )

        fun powerOffReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.POWER_OFF_COMMAND,
                data = byteArrayOf()
            )

        fun FakePort.firstWrittenFrame(command: Byte) =
            writeRequests.asSequence()
                .mapNotNull { SportIdentFrameParser.firstFrame(it, requireValidCrc = true) }
                .first { it.command == command }

        fun fastCommandClient(): DesktopSportIdentStationCommandClient {
            var now = 0L
            return DesktopSportIdentStationCommandClient(
                readTimeoutMs = 10,
                nowMillis = {
                    now += 1
                    now
                }
            )
        }

        private fun stationTimePayload(time: LocalDateTime, tick: Int): ByteArray =
            DesktopSportIdentStationTimeCodec.encodePayload(time).copyOf().also { it[6] = tick.toByte() }

        private fun ftdiPortSelector(port: DesktopSerialPort): DesktopSportIdentPortSelector =
            DesktopSportIdentPortSelector(
                portProvider = FakePortProvider(listOf(port)),
                discoverySettings = FakeDiscoverySettings(DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS),
                probeSportIdentStation = { true }
            )

        private fun sportIdentUsbPortInfo(): DesktopSerialPortInfo =
            DesktopSerialPortInfo(
                systemPortPath = "/dev/cu.fake",
                descriptivePortName = "Fake SPORTident",
                vendorId = SportIdentUsbDevice.VENDOR_ID,
                productId = SportIdentUsbDevice.PRODUCT_ID,
                serialNumber = "fake"
            )

        private fun ftdiPortInfo(): DesktopSerialPortInfo =
            DesktopSerialPortInfo(
                systemPortPath = "/dev/cu.usbserial-110",
                descriptivePortName = "FTDI USB Serial",
                vendorId = 0x0403,
                productId = 0x6001,
                serialNumber = "ftdi"
            )
    }
}
