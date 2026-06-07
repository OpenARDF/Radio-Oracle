package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentTime
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentReadoutServiceTest {
    @Test
    fun downloadOneFailsWhenNoSportIdentPortIsAvailable() {
        val service = DesktopSportIdentReadoutService(portProvider = FakePortProvider(emptyList()))

        val error = assertThrows(IllegalStateException::class.java) {
            service.downloadOne()
        }

        assertEquals("No SPORTident USB station found.", error.message)
    }

    @Test
    fun downloadOneAllowsFlaggedDownloadCapableStationAndClosesPort() {
        val port = FakePort()
        val expected = download(siNumber = 2005010)
        val service = DesktopSportIdentReadoutService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                it.open(0)
                connection(modeCode = 0x28)
            },
            readCard = { expected }
        )

        val actual = service.downloadOne()

        assertSame(expected, actual)
        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun downloadOneBlocksClearlyNonDownloadStationAndClosesPort() {
        val port = FakePort()
        val service = DesktopSportIdentReadoutService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                it.open(0)
                connection(modeCode = 2)
            },
            readCard = { download() }
        )

        val error = assertThrows(IllegalStateException::class.java) {
            service.downloadOne()
        }

        assertEquals("SI station 554900 is in CHECK mode instead of READOUT/SI MASTER.", error.message)
        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun downloadUntilTimeoutKeepsStationOpenAndStopsOnInsertTimeout() {
        val port = FakePort()
        val downloads = listOf(download(siNumber = 101), download(siNumber = 102))
        var readAttempts = 0
        var timeoutReported = false
        val readSis = mutableListOf<Int>()
        val service = DesktopSportIdentReadoutService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                it.open(0)
                connection(modeCode = 8)
            },
            readCard = {
                if (readAttempts < downloads.size) {
                    downloads[readAttempts++]
                } else {
                    readAttempts += 1
                    error("No SPORTident card insert event received before timeout.")
                }
            }
        )

        val count = service.downloadUntilTimeout(
            maxCards = 10,
            onDownload = { readSis.add(it.readout.siNumber) },
            onTimeout = { timeoutReported = true }
        )

        assertEquals(2, count)
        assertEquals(listOf(101, 102), readSis)
        assertEquals(3, readAttempts)
        assertTrue(timeoutReported)
        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun downloadUntilTimeoutChecksStopHookBetweenCards() {
        val port = FakePort()
        var readAttempts = 0
        val readSis = mutableListOf<Int>()
        val service = DesktopSportIdentReadoutService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                it.open(0)
                connection(modeCode = 8)
            },
            readCard = {
                readAttempts += 1
                download(siNumber = 200 + readAttempts)
            }
        )

        val count = service.downloadUntilTimeout(
            maxCards = 10,
            onDownload = { readSis.add(it.readout.siNumber) },
            shouldContinue = { readAttempts == 0 }
        )

        assertEquals(1, count)
        assertEquals(listOf(201), readSis)
        assertEquals(1, readAttempts)
        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun downloadUntilTimeoutWaitsAfterSuccessfulCardsBeforeNextRead() {
        val port = FakePort()
        val waitSis = mutableListOf<Int>()
        var readAttempts = 0
        val service = DesktopSportIdentReadoutService(
            portProvider = FakePortProvider(listOf(port)),
            connectStation = {
                it.open(0)
                connection(modeCode = 8)
            },
            readCard = {
                readAttempts += 1
                if (readAttempts == 1) {
                    download(siNumber = 2450672)
                } else {
                    error("No SPORTident card insert event received before timeout.")
                }
            },
            waitAfterSuccessfulCard = { _, download ->
                waitSis.add(download.readout.siNumber)
            }
        )

        val count = service.downloadUntilTimeout(
            maxCards = 2,
            onDownload = {}
        )

        assertEquals(1, count)
        assertEquals(listOf(2450672), waitSis)
        assertEquals(2, readAttempts)
        assertFalse(port.isOpen)
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

        override var isOpen: Boolean = false
            private set
        var closed: Boolean = false
            private set

        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit

        override fun open(waitTimeMillis: Int): Boolean {
            isOpen = true
            return true
        }

        override fun close() {
            isOpen = false
            closed = true
        }

        override fun write(bytes: ByteArray): Int = bytes.size
        override fun read(maxBytes: Int): ByteArray = byteArrayOf()
    }

    private fun connection(modeCode: Int): DesktopSportIdentStationConnection =
        DesktopSportIdentStationConnection(
            baudRate = 38400,
            probeReply = byteArrayOf(),
            stationInfo = SportIdentStationInfo(
                serialNumber = 554900,
                extendedMode = true,
                stationCodeNumber = 14,
                stationModeCode = modeCode
            )
        )

    private fun download(siNumber: Int = 123456): DesktopSportIdentCardBlockDownload =
        DesktopSportIdentCardBlockDownload(
            inserted = SportIdentCardEvent.Inserted(cardType = 0x0f, siNumber = siNumber),
            blocks = emptyList(),
            readout = SportIdentCardReadout(
                siNumber = siNumber,
                series = 5,
                checkTime = null,
                startTime = SportIdentTime(0, 10, 0),
                finishTime = SportIdentTime(0, 30, 0),
                punches = listOf(SportIdentCardPunch(31, SportIdentTime(0, 15, 0)))
            )
        )
}
