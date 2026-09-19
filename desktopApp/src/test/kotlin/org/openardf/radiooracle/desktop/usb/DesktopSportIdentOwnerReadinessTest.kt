package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadoutParser
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentOwnerReadinessTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)

    @Test
    fun exactRequestChecksFreshCardAndPresenceWithoutAnOwnerWordOrRecoveryRecord() {
        val before = download()
        val port = FakePort(listOf(blockReply(before.blocks.first().data)))

        val report = readiness(port, before).inspect(request)

        assertTrue(report.ready)
        assertEquals(request.stationNumber, report.stationNumber)
        assertEquals(request.cardNumber, report.cardNumber)
        assertEquals("Daisy", report.storedFirstName)
        assertEquals("Duck", report.storedLastName)
        assertEquals(1, port.writes.size)
        assertArrayEquals(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.GET_SI_CARD8_9_SIAC, byteArrayOf(0)), port.writes.single())
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store().load())
        assertEquals(1, port.closeCount)
        assertFalse(port.isOpen)
    }

    @Test
    fun removedCardCannotBeDeclaredReadyAndLeavesNoRecoveryRecord() {
        val port = FakePort(listOf(byteArrayOf(SportIdentProtocol.NAK)))

        val report = readiness(port, download()).inspect(request)

        assertFalse(report.ready)
        assertEquals(DesktopSportIdentCardPresenceResult.NEGATIVE_ACKNOWLEDGEMENT, report.blockZeroRecheck)
        assertEquals(1, port.writes.size)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store().load())
        assertEquals(1, port.closeCount)
    }

    @Test
    fun pendingRecoveryBlocksReadinessBeforeOpeningThePort() {
        store().begin(request)
        val port = FakePort(emptyList())

        assertThrows(IllegalStateException::class.java) { readiness(port, download()).inspect(request) }

        assertEquals(0, port.openCount)
        assertTrue(port.writes.isEmpty())
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store().load())
    }

    private fun readiness(port: FakePort, download: DesktopSportIdentCardBlockDownload): DesktopSportIdentOwnerReadiness {
        val provider = object : DesktopSerialPortProvider {
            override fun listPorts() = listOf(port)
            override fun getPort(systemPortPath: String) = port
        }
        val preflight = DesktopSportIdentOwnerWritePreflight(
            portSelector = DesktopSportIdentPortSelector(provider),
            connectStation = { opened ->
                opened.open(0)
                DesktopSportIdentStationConnection(38400, byteArrayOf(),
                    SportIdentStationInfo(request.stationNumber, true, stationModeCode = 8))
            },
            readCard = { download }
        )
        var now = 0L
        val probe = DesktopSportIdentCardPresenceProbe(DesktopSportIdentStationCommandClient(
            readTimeoutMs = 50, nowMillis = { ++now }
        ))
        return DesktopSportIdentOwnerReadiness(preflight, store(), probe)
    }

    private fun store() = DesktopSportIdentOwnerRecoveryStore(
        temporary.root.toPath().resolve("recovery.json"))

    private fun download(): DesktopSportIdentCardBlockDownload {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        "Daisy;Duck;".forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128)
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + block1))
        return DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, request.cardNumber),
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1)), readout)
    }

    private fun blockReply(block: ByteArray) = SportIdentProtocol.buildExtendedMessage(
        SportIdentProtocol.GET_SI_CARD8_9_SIAC, byteArrayOf(0, 0, 0) + block)

    private class FakePort(chunks: List<ByteArray>) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)
        override val info = DesktopSerialPortInfo("/dev/cu.fake", "Fake SPORTident",
            SportIdentUsbDevice.VENDOR_ID, SportIdentUsbDevice.PRODUCT_ID, "fake")
        override var isOpen = false
        var openCount = 0
        var closeCount = 0
        val writes = mutableListOf<ByteArray>()
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean {
            isOpen = true
            openCount++
            return true
        }
        override fun close() {
            isOpen = false
            closeCount++
        }
        override fun write(bytes: ByteArray): Int {
            writes += bytes.copyOf()
            return bytes.size
        }
        override fun read(maxBytes: Int) = pending.removeFirstOrNull() ?: ByteArray(0)
    }
}
