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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentStationBackupServiceTest {
    @Test
    fun readsCoupledPunchRecordsAndNeverSendsEraseCommand() {
        val port = FakePort(
            listOf(
                reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x53)),
                systemInfoReply(),
                backupReply(0x100, modernRecord(575_914, second = 3)),
                backupReply(0x108, modernRecord(1_234_567, second = 4)),
                reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x4D))
            )
        )
        val selector = DesktopSportIdentPortSelector(FakePortProvider(port))
        val service = DesktopSportIdentStationBackupService(
            portSelector = selector,
            connectStation = {
                it.open(0)
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 14,
                        stationModeCode = 8
                    )
                )
            },
            commandClient = fastCommandClient()
        )

        val snapshot = service.readBackup()

        assertEquals(575_853, snapshot.stationInfo.serialNumber)
        assertEquals(listOf(575_914, 1_234_567), snapshot.records.map { it.cardNumber })
        val commands = port.writes.mapNotNull {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(2, commands.count { it == SportIdentProtocol.GET_BACKUP })
        assertFalse(commands.contains(0xF5.toByte()))
        assertTrue(commands.first() == SportIdentProtocol.PROBE_COMMAND)
        assertTrue(commands.last() == SportIdentProtocol.PROBE_COMMAND)
        assertTrue(port.writes.first().contentEquals(byteArrayOf(SportIdentProtocol.WAKEUP)))
    }

    @Test
    fun retriesFullWakeCycleWhenCoupledStationRejectsMetadataUntilReady() {
        val noReply = List(8) { byteArrayOf() }
        val sleeps = mutableListOf<Long>()
        val port = FakePort(
            listOf(reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x53))) +
                noReply + noReply + noReply + listOf(
                reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x4D)),
                reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x53)),
                systemInfoReply(),
                backupReply(0x100, modernRecord(575_914, second = 3)),
                backupReply(0x108, modernRecord(1_234_567, second = 4)),
                reply(SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0F, 0x4D))
            )
        )
        val service = DesktopSportIdentStationBackupService(
            portSelector = DesktopSportIdentPortSelector(FakePortProvider(port)),
            connectStation = {
                it.open(0)
                DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = byteArrayOf(),
                    stationInfo = SportIdentStationInfo(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 14,
                        stationModeCode = 8
                    )
                )
            },
            commandClient = fastCommandClient(),
            sleepMillis = { sleeps += it }
        )

        val snapshot = service.readBackup()

        assertEquals(listOf(575_914, 1_234_567), snapshot.records.map { it.cardNumber })
        assertEquals(
            2,
            port.writes.count { it.contentEquals(byteArrayOf(SportIdentProtocol.WAKEUP)) }
        )
        val commands = port.writes.mapNotNull {
            SportIdentFrameParser.firstFrame(it, requireValidCrc = true)?.command
        }
        assertEquals(4, commands.count { it == SportIdentProtocol.GET_SYSTEM_INFO })
        assertTrue(sleeps.count { it == 250L } >= 3)
        assertEquals(2, sleeps.count { it == 150L })
        assertFalse(commands.contains(0xF5.toByte()))
    }

    private class FakePortProvider(private val port: DesktopSerialPort) : DesktopSerialPortProvider {
        override fun listPorts(): List<DesktopSerialPort> = listOf(port)
        override fun getPort(systemPortPath: String): DesktopSerialPort = port
    }

    private class FakePort(readChunks: List<ByteArray>) : DesktopSerialPort {
        private val pending = ArrayDeque(readChunks)
        private var open = false
        val writes = mutableListOf<ByteArray>()
        override val info = DesktopSerialPortInfo(
            systemPortPath = "/dev/cu.fake",
            descriptivePortName = "Fake SPORTident",
            vendorId = SportIdentUsbDevice.VENDOR_ID,
            productId = SportIdentUsbDevice.PRODUCT_ID,
            serialNumber = "fake"
        )
        override val isOpen: Boolean get() = open
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean {
            open = true
            return true
        }
        override fun close() {
            open = false
        }
        override fun write(bytes: ByteArray): Int {
            writes += bytes
            return bytes.size
        }
        override fun read(maxBytes: Int): ByteArray = pending.removeFirstOrNull() ?: byteArrayOf()
    }

    private companion object {
        fun systemInfoReply(): ByteArray {
            val data = ByteArray(131)
            data[1] = 45
            val serial = 575_853
            data[3] = (serial ushr 24).toByte()
            data[4] = (serial ushr 16).toByte()
            data[5] = (serial ushr 8).toByte()
            data[6] = serial.toByte()
            data[20] = 2
            data[3 + 0x0D] = 128.toByte()
            data[3 + 0x21] = 0x01
            data[3 + 0x22] = 0x10
            data[3 + 0x71] = 2
            data[3 + 0x74] = 1
            data[119] = 1
            return reply(SportIdentProtocol.GET_SYSTEM_INFO, data)
        }

        fun backupReply(address: Int, record: ByteArray): ByteArray =
            reply(
                SportIdentProtocol.GET_BACKUP,
                byteArrayOf(
                    0,
                    45,
                    (address ushr 16).toByte(),
                    (address ushr 8).toByte(),
                    address.toByte()
                ) + record
            )

        fun modernRecord(cardNumber: Int, second: Int): ByteArray =
            byteArrayOf(
                (cardNumber ushr 16).toByte(),
                (cardNumber ushr 8).toByte(),
                cardNumber.toByte(),
                0x6A,
                0x43,
                0,
                second.toByte(),
                0
            )

        fun reply(command: Byte, data: ByteArray): ByteArray =
            SportIdentProtocol.buildExtendedMessage(command, data)

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
    }
}
