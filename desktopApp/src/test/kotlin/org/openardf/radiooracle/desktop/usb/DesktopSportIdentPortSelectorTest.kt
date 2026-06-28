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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentPortSelectorTest {
    @Test
    fun usbOnlyModeDoesNotProbeFtdiPorts() {
        val settings = FakeDiscoverySettings(DesktopSportIdentPortDiscoveryMode.SPORTIDENT_USB_ONLY)
        val ftdiPort = FakePort("/dev/cu.usbserial-110", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val probedPorts = mutableListOf<String>()
        val selector = DesktopSportIdentPortSelector(
            portProvider = FakePortProvider(listOf(ftdiPort)),
            discoverySettings = settings,
            probeSportIdentStation = {
                probedPorts += it.info.systemPortPath
                true
            }
        )

        val selected = selector.selectPort()

        assertNull(selected)
        assertEquals(emptyList<String>(), probedPorts)
        assertNull(settings.rememberedSportIdentFtdiPortPath())
    }

    @Test
    fun ftdiModeProbesAdaptersAndRemembersSuccessfulStationPort() {
        val settings = FakeDiscoverySettings(DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS)
        val first = FakePort("/dev/cu.usbserial-110", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val second = FakePort("/dev/cu.usbserial-220", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val probedPorts = mutableListOf<String>()
        val selector = DesktopSportIdentPortSelector(
            portProvider = FakePortProvider(listOf(first, second)),
            discoverySettings = settings,
            probeSportIdentStation = {
                probedPorts += it.info.systemPortPath
                it.info.systemPortPath == second.info.systemPortPath
            }
        )

        val selected = selector.selectPort()

        assertEquals(second, selected)
        assertEquals(listOf(first.info.systemPortPath, second.info.systemPortPath), probedPorts)
        assertEquals(second.info.systemPortPath, settings.rememberedSportIdentFtdiPortPath())
    }

    @Test
    fun ftdiModePrioritizesRememberedPortBeforeOtherFtdiAdapters() {
        val first = FakePort("/dev/cu.usbserial-110", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val remembered = FakePort("/dev/cu.usbserial-220", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val settings = FakeDiscoverySettings(
            mode = DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS,
            rememberedPortPath = remembered.info.systemPortPath
        )
        val probedPorts = mutableListOf<String>()
        val selector = DesktopSportIdentPortSelector(
            portProvider = FakePortProvider(listOf(first, remembered)),
            discoverySettings = settings,
            probeSportIdentStation = {
                probedPorts += it.info.systemPortPath
                true
            }
        )

        val selected = selector.selectPort()

        assertEquals(remembered, selected)
        assertEquals(listOf(remembered.info.systemPortPath), probedPorts)
    }

    @Test
    fun sportIdentUsbPortIsSelectedWithoutFtdiProbeEvenInFtdiMode() {
        val settings = FakeDiscoverySettings(DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS)
        val sportIdentUsb = FakePort(
            path = "/dev/cu.SLAB_USBtoUART",
            name = "SPORTident USB to UART Bridge Controller",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID
        )
        val ftdiPort = FakePort("/dev/cu.usbserial-110", "FTDI USB Serial", FTDI_VENDOR_ID, 0x6001)
        val probedPorts = mutableListOf<String>()
        val selector = DesktopSportIdentPortSelector(
            portProvider = FakePortProvider(listOf(ftdiPort, sportIdentUsb)),
            discoverySettings = settings,
            probeSportIdentStation = {
                probedPorts += it.info.systemPortPath
                true
            }
        )

        val selected = selector.selectPort()

        assertEquals(sportIdentUsb, selected)
        assertEquals(emptyList<String>(), probedPorts)
    }

    private class FakeDiscoverySettings(
        private var mode: DesktopSportIdentPortDiscoveryMode,
        private var rememberedPortPath: String? = null
    ) : DesktopSportIdentPortDiscoverySettings {
        override fun sportIdentPortDiscoveryMode(): DesktopSportIdentPortDiscoveryMode = mode
        override fun rememberedSportIdentFtdiPortPath(): String? = rememberedPortPath
        override fun rememberSportIdentFtdiPortPath(portPath: String) {
            rememberedPortPath = portPath
        }
    }

    private class FakePortProvider(private val ports: List<DesktopSerialPort>) : DesktopSerialPortProvider {
        override fun listPorts(): List<DesktopSerialPort> = ports
        override fun getPort(systemPortPath: String): DesktopSerialPort =
            ports.first { it.info.systemPortPath == systemPortPath }
    }

    private class FakePort(
        path: String,
        name: String,
        vendorId: Int,
        productId: Int,
        serialNumber: String = path.substringAfterLast("/")
    ) : DesktopSerialPort {
        override val info: DesktopSerialPortInfo = DesktopSerialPortInfo(
            systemPortPath = path,
            descriptivePortName = name,
            vendorId = vendorId,
            productId = productId,
            serialNumber = serialNumber
        )
        override val isOpen: Boolean = false
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean = true
        override fun close() = Unit
        override fun write(bytes: ByteArray): Int = bytes.size
        override fun read(maxBytes: Int): ByteArray = byteArrayOf()
    }

    private companion object {
        const val FTDI_VENDOR_ID = 0x0403
    }
}
