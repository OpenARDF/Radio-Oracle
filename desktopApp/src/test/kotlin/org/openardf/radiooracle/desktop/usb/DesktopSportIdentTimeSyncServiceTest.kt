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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
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

    private class FakePortProvider(private val ports: List<DesktopSerialPort>) : DesktopSerialPortProvider {
        override fun listPorts(): List<DesktopSerialPort> = ports
        override fun getPort(systemPortPath: String): DesktopSerialPort =
            ports.first { it.info.systemPortPath == systemPortPath }
    }

    private class FakePort : DesktopSerialPort {
        override val info = DesktopSerialPortInfo(
            systemPortPath = "/dev/cu.fake",
            descriptivePortName = "Fake SPORTident",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID,
            serialNumber = "fake"
        )

        override val isOpen: Boolean = false
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean = true
        override fun close() = Unit
        override fun write(bytes: ByteArray): Int = bytes.size
        override fun read(maxBytes: Int): ByteArray = byteArrayOf()
    }
}
