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
    fun inspectReportsConnectedStationWithoutEnablingWritesYet() {
        val port = FakePort()
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
            }
        )

        val inspection = service.inspectDownloadStation()

        assertEquals("SI station 554896 connected in SI MASTER mode.", inspection.statusText)
        assertEquals(SportIdentProtocol.BAUDRATE_HIGH, inspection.baudRate)
        assertEquals(554896, inspection.stationInfo?.serialNumber)
        assertFalse(inspection.canSyncTime)
        assertEquals("Time sync write support is pending SPORTident protocol validation.", inspection.disabledReason)
    }

    @Test
    fun syncTimeIsExplicitlyUnsupportedUntilWriteProtocolIsImplemented() {
        val service = DesktopSportIdentTimeSyncService(portProvider = FakePortProvider(emptyList()))

        val error = assertThrows(UnsupportedOperationException::class.java) {
            service.syncTime(LocalDateTime.parse("2026-06-25T12:34:56"))
        }

        assertEquals(
            "SPORTident time sync write support is not implemented yet. Source time was 2026-06-25T12:34:56.",
            error.message
        )
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
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime, tick = 0x05),
                applyReply(),
                stationTimeReply(targetTime, tick = 0x06),
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
        assertEquals(
            listOf(
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
            SportIdentFrameParser.firstFrame(port.writeRequests[4], requireValidCrc = true)?.data
        )
    }

    @Test
    fun writeTimeWithReadBackFailsWhenReadBackIsOutsideTolerance() {
        val targetTime = LocalDateTime.parse("2026-06-27T03:10:18")
        val port = FakePort(
            readChunks = listOf(
                remoteModeReply(),
                remoteModeReply(),
                systemInfoReply(),
                stationTimeReply(targetTime.minusMinutes(1), tick = 0x01),
                stationWriteReply(targetTime, tick = 0x05),
                applyReply(),
                stationTimeReply(targetTime.plusSeconds(5), tick = 0x06),
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

    private class FakePort(
        readChunks: List<ByteArray> = emptyList()
    ) : DesktopSerialPort {
        private val pending = ArrayDeque(readChunks)
        val writeRequests = mutableListOf<ByteArray>()
        private var open = false

        override val info = DesktopSerialPortInfo(
            systemPortPath = "/dev/cu.fake",
            descriptivePortName = "Fake SPORTident",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID,
            serialNumber = "fake"
        )

        override val isOpen: Boolean
            get() = open

        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean {
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

        fun normalModeReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.PROBE_COMMAND,
                data = byteArrayOf(0x00, 0x0F, 0x4D)
            )

        fun systemInfoReply(): ByteArray {
            val data = ByteArray(21)
            data[1] = 0x01
            data[3] = 0x00
            data[4] = 0x08
            data[5] = 0x77
            data[6] = 0x90.toByte()
            data[20] = 0x08
            return SportIdentProtocol.buildExtendedMessage(
                command = SportIdentProtocol.GET_SYSTEM_INFO,
                data = data
            )
        }

        fun stationTimeReply(time: LocalDateTime, tick: Int): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, 0x01) + stationTimePayload(time, tick)
            )

        fun stationWriteReply(time: LocalDateTime, tick: Int): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, 0x01) + stationTimePayload(time, tick)
            )

        fun applyReply(): ByteArray =
            SportIdentProtocol.buildExtendedMessage(
                command = DesktopSportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND,
                data = byteArrayOf(0x00, 0x01, 0x01)
            )

        private fun stationTimePayload(time: LocalDateTime, tick: Int): ByteArray =
            DesktopSportIdentStationTimeCodec.encodePayload(time).copyOf().also { it[6] = tick.toByte() }
    }
}
