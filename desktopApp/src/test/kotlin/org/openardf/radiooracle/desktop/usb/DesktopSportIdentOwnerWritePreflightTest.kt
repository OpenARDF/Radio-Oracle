package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadoutParser
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentOwnerWritePreflightTest {
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)

    @Test
    fun preparesFreshMatchingCardOnTheOpenPortAndClosesAfterCallback() {
        val port = FakePort()
        val download = download()
        var reads = 0
        val preflight = preflight(port, readCard = { opened ->
            assertTrue(opened === port && opened.isOpen)
            reads++
            download
        })

        val returned = preflight.withFreshRead(request) { opened, rehearsal, before ->
            assertTrue(opened === port && opened.isOpen)
            assertEquals(SportIdentSi8OwnerWriteStage.READY_FOR_WORD, rehearsal.stage)
            assertEquals("Daisy", SportIdentOwnerReadVerification.nativeRead(before).firstName)
            assertEquals(2, before.blocks.size)
            "prepared"
        }

        assertEquals("prepared", returned)
        assertEquals(1, reads)
        assertEquals(1, port.closeCount)
        assertFalse(port.isOpen)
        assertTrue(port.writes.isEmpty())
    }

    @Test
    fun passesConnectedStationCodeToWordReplyGate() {
        val port = FakePort()
        preflight(port, station = station(code = 14)).withFreshRead(request) { _, rehearsal, _ ->
            rehearsal.takeNextWordFrame()
            val reply = requireNotNull(SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.WRITE_SI_CARD_WORD,
                    byteArrayOf(0, 14, 0x08))))
            rehearsal.acceptWordResult(SportIdentCommandResult.Reply(reply))
            assertEquals(SportIdentSi8OwnerWriteStage.READY_FOR_WORD, rehearsal.stage)
            assertEquals(null, rehearsal.stopReason)
        }
        assertEquals(1, port.closeCount)
    }

    @Test
    fun stationMismatchUnknownModeAndNonextendedStationNeverReadCard() {
        listOf(
            station(serial = 593928), station(mode = 2), station(mode = null),
            station(extended = false), station(code = null), station(code = 256)
        ).forEach { badStation ->
            val port = FakePort()
            var reads = 0
            val preflight = preflight(port, station = badStation, readCard = { reads++; download() })
            assertThrows(IllegalArgumentException::class.java) {
                preflight.withFreshRead(request) { _, _, _ -> error("Wrong station reached callback") }
            }
            assertEquals(0, reads)
            assertFalse(port.isOpen)
            assertEquals(1, port.closeCount)
        }
    }

    @Test
    fun wrongCardIncompleteBlocksAndStaleOwnerNamesNeverReachCallback() {
        val correct = download()
        val differentRawCard = correct.blocks.single { it.blockNumber == 0 }.data.copyOf().also { it[27]++ }
        listOf(
            correct.copy(inserted = correct.inserted.copy(cardType = SportIdentProtocol.SI_CARD5)),
            correct.copy(inserted = correct.inserted.copy(siNumber = 2450663)),
            correct.copy(readout = correct.readout.copy(siNumber = 2450663)),
            correct.copy(readout = correct.readout.copy(series = 9)),
            correct.copy(blocks = listOf(SportIdentCardBlock(0, differentRawCard), correct.blocks[1])),
            correct.copy(blocks = correct.blocks.take(1)),
            download(owner = "Mickey;Mouse;")
        ).forEach { badDownload ->
            val port = FakePort()
            val preflight = preflight(port, readCard = { badDownload })
            assertThrows(IllegalArgumentException::class.java) {
                preflight.withFreshRead(request) { _, _, _ -> error("Wrong card reached callback") }
            }
            assertEquals(1, port.closeCount)
            assertTrue(port.writes.isEmpty())
        }
    }

    @Test
    fun invalidRequestDoesNotOpenPortAndCallbackFailureStillClosesIt() {
        val invalidPort = FakePort()
        val invalid = preflight(invalidPort)
        assertThrows(IllegalArgumentException::class.java) {
            invalid.withFreshRead(request.copy(acceptPossiblePunchLoss = false)) { _, _, _ -> Unit }
        }
        assertEquals(0, invalidPort.openCount)

        val port = FakePort()
        val preflight = preflight(port)
        var handedOff: SportIdentSi8OwnerWriteRehearsal? = null
        assertThrows(IllegalStateException::class.java) {
            preflight.withFreshRead(request) { _, rehearsal, _ ->
                handedOff = rehearsal
                error("Callback failed")
            }
        }
        assertEquals(1, port.closeCount)
        assertFalse(port.isOpen)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, handedOff?.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, handedOff?.stopReason)
    }

    @Test
    fun failureAfterVerificationKeepsItsOriginalCauseAndClosesPort() {
        val port = FakePort()
        val replies = listOf(
            "02 ea 03 00 0a 08 00 2e 03",
            "02 ea 03 00 0a 09 01 2e 03",
            "02 ea 03 00 0a 0a 02 2e 03"
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            preflight(port).withFreshRead(request) { _, rehearsal, _ ->
                replies.forEach { hex ->
                    rehearsal.takeNextWordFrame()
                    val frame = requireNotNull(SportIdentFrameParser.firstFrame(
                        hex.split(' ').map { it.toInt(16).toByte() }.toByteArray()))
                    rehearsal.acceptWordResult(SportIdentCommandResult.Reply(frame))
                }
                assertTrue(rehearsal.compareIndependentRead(SportIdentOwnerReadVerification.capture(
                    request.stationNumber, download(owner = "Donald;Duck;").blocks)).matches)
                error("Recovery record could not be cleared")
            }
        }

        assertEquals("Recovery record could not be cleared", failure.message)
        assertEquals(1, port.closeCount)
        assertFalse(port.isOpen)
    }

    private fun preflight(
        port: FakePort,
        station: SportIdentStationInfo = station(),
        readCard: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = { download() }
    ): DesktopSportIdentOwnerWritePreflight {
        val provider = object : DesktopSerialPortProvider {
            override fun listPorts() = listOf(port)
            override fun getPort(systemPortPath: String) = port
        }
        return DesktopSportIdentOwnerWritePreflight(
            portSelector = DesktopSportIdentPortSelector(provider),
            connectStation = { opened ->
                opened.open(0)
                DesktopSportIdentStationConnection(38400, byteArrayOf(), station)
            },
            readCard = readCard
        )
    }

    private fun station(serial: Int = 593927, mode: Int? = 8, extended: Boolean = true,
                        code: Int? = 10) =
        SportIdentStationInfo(serial, extended, stationCodeNumber = code, stationModeCode = mode)

    private fun download(owner: String = "Daisy;Duck;"): DesktopSportIdentCardBlockDownload {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128)
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + block1))
        return DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, 2450662),
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1)), readout)
    }

    private class FakePort : DesktopSerialPort {
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
            writes += bytes
            return bytes.size
        }
        override fun read(maxBytes: Int) = ByteArray(0)
    }
}
