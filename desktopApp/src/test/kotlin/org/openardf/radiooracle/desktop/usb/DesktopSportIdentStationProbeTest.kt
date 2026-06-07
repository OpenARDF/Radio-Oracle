package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentStationProbeTest {
    @Test
    fun returnsHighBaudReplyWhenStationRespondsImmediately() {
        val reply = probeReply()
        val port = FakeDesktopSerialPort(repliesByBaud = mapOf(SportIdentProtocol.BAUDRATE_HIGH to reply))

        val result = DesktopSportIdentStationProbe().probe(port)

        assertEquals(SportIdentProtocol.BAUDRATE_HIGH, result.baudRate)
        assertArrayEquals(reply.dropWakeup(), result.reply)
        assertEquals(listOf(SportIdentProtocol.BAUDRATE_HIGH), port.configuredBaudRates)
        assertEquals(1, port.writeRequests.size)
        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun fallsBackToLowBaudWhenHighBaudProbeGetsNoReply() {
        val reply = probeReply(0x02)
        val port = FakeDesktopSerialPort(repliesByBaud = mapOf(SportIdentProtocol.BAUDRATE_LOW to reply))

        val result = DesktopSportIdentStationProbe().probe(port)

        assertEquals(SportIdentProtocol.BAUDRATE_LOW, result.baudRate)
        assertArrayEquals(reply.dropWakeup(), result.reply)
        assertEquals(
            listOf(SportIdentProtocol.BAUDRATE_HIGH, SportIdentProtocol.BAUDRATE_LOW),
            port.configuredBaudRates
        )
        assertEquals(2, port.writeRequests.size)
        assertFalse(port.isOpen)
    }

    @Test
    fun closesPortWhenStationDoesNotRespond() {
        val port = FakeDesktopSerialPort()

        assertThrows(IllegalStateException::class.java) {
            DesktopSportIdentStationProbe().probe(port)
        }

        assertFalse(port.isOpen)
        assertTrue(port.closed)
    }

    @Test
    fun readsStationInfoAfterSuccessfulProbe() {
        val port = FakeDesktopSerialPort(
            repliesByCommand = mapOf(
                SportIdentProtocol.PROBE_COMMAND to probeReply(),
                SportIdentProtocol.GET_SYSTEM_INFO to systemInfoReply(
                    serialNumber = 554896,
                    extendedMode = true,
                    stationModeCode = 0x28
                )
            )
        )

        val connection = DesktopSportIdentStationProbe().connect(port)

        assertEquals(SportIdentProtocol.BAUDRATE_HIGH, connection.baudRate)
        assertArrayEquals(probeReply().dropWakeup(), connection.probeReply)
        assertEquals(554896, connection.stationInfo.serialNumber)
        assertTrue(connection.stationInfo.extendedMode)
        assertEquals("SI MASTER + 0x20 flag", connection.stationInfo.stationModeLabel)
        assertFalse(connection.stationInfo.isReadoutMode!!)
        assertTrue(connection.stationInfo.isDownloadCapableMode!!)
        assertFalse(port.isOpen)
    }

    @Test
    fun readsStationInfoReplySplitAcrossSerialReads() {
        val systemInfo = systemInfoReply(
            serialNumber = 554896,
            extendedMode = true,
            stationModeCode = 0x28
        )
        val port = FakeDesktopSerialPort(
            replyChunksByCommand = mapOf(
                SportIdentProtocol.PROBE_COMMAND to listOf(probeReply()),
                SportIdentProtocol.GET_SYSTEM_INFO to listOf(
                    systemInfo.copyOfRange(0, 5),
                    systemInfo.copyOfRange(5, systemInfo.size)
                )
            )
        )

        val connection = DesktopSportIdentStationProbe().connect(port)

        assertEquals(554896, connection.stationInfo.serialNumber)
        assertEquals("SI MASTER + 0x20 flag", connection.stationInfo.stationModeLabel)
        assertFalse(port.isOpen)
    }

    @Test
    fun describesSportIdentPortWithUsbIdentity() {
        val info = DesktopSerialPortInfo(
            systemPortPath = "/dev/cu.SLAB_USBtoUART",
            descriptivePortName = "SPORTident USB to UART Bridge Controller",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID,
            serialNumber = "554896"
        )

        assertTrue(info.matchesSportIdent())
        assertEquals(
            "/dev/cu.SLAB_USBtoUART (SPORTident USB to UART Bridge Controller) VID:PID=4292:32778 serial=554896",
            info.describe()
        )
    }

    private class FakeDesktopSerialPort(
        private val openSucceeds: Boolean = true,
        private val repliesByBaud: Map<Int, ByteArray> = emptyMap(),
        private val repliesByCommand: Map<Byte, ByteArray> = emptyMap(),
        private val replyChunksByCommand: Map<Byte, List<ByteArray>> = emptyMap()
    ) : DesktopSerialPort {
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
        val configuredBaudRates = mutableListOf<Int>()
        val writeRequests = mutableListOf<ByteArray>()
        private var currentBaudRate: Int = 0
        private var lastCommand: Byte? = null
        private val commandReadIndexes = mutableMapOf<Byte, Int>()

        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) {
            currentBaudRate = baudRate
            configuredBaudRates.add(baudRate)
        }

        override fun open(waitTimeMillis: Int): Boolean {
            isOpen = openSucceeds
            return openSucceeds
        }

        override fun close() {
            isOpen = false
            closed = true
        }

        override fun write(bytes: ByteArray): Int {
            writeRequests.add(bytes)
            lastCommand = bytes.getOrNull(2)
            return bytes.size
        }

        override fun read(maxBytes: Int): ByteArray {
            val command = lastCommand
            if (command != null) {
                val chunks = replyChunksByCommand[command]
                if (chunks != null) {
                    val index = commandReadIndexes.getOrDefault(command, 0)
                    commandReadIndexes[command] = index + 1
                    return chunks.getOrNull(index) ?: byteArrayOf()
                }
                repliesByCommand[command]?.let { return it }
            }
            return repliesByBaud[currentBaudRate] ?: byteArrayOf()
        }
    }

    private fun probeReply(marker: Byte = 0x01): ByteArray =
        SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.PROBE_COMMAND,
            data = byteArrayOf(marker, 0x11, 0x4d)
        )

    private fun systemInfoReply(
        serialNumber: Int,
        extendedMode: Boolean,
        stationModeCode: Int = 5
    ): ByteArray =
        SportIdentProtocol.buildExtendedMessage(
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            data = ByteArray(120).also { data ->
                data[3] = ((serialNumber ushr 24) and 0xff).toByte()
                data[4] = ((serialNumber ushr 16) and 0xff).toByte()
                data[5] = ((serialNumber ushr 8) and 0xff).toByte()
                data[6] = (serialNumber and 0xff).toByte()
                data[20] = (stationModeCode and 0xff).toByte()
                if (extendedMode) {
                    data[119] = 0x01
                }
            }
        )

    private fun ByteArray.dropWakeup(): ByteArray =
        drop(1).toByteArray()
}
