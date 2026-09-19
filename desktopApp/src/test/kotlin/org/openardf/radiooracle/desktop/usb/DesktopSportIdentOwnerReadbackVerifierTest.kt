package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentOwnerReadbackVerifierTest {
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)
    private val replies = listOf(
        "02 ea 03 00 0a 08 00 2e 03",
        "02 ea 03 00 0a 09 01 2e 03",
        "02 ea 03 00 0a 0a 02 2e 03"
    )

    @Test
    fun requiresTargetRemovalThenFreshInsertAndWholeCardMatch() {
        val port = FakePort()
        val rehearsal = completedRehearsal()
        val steps = mutableListOf<String>()
        val verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { opened, card ->
                assertTrue(opened === port && opened.isOpen)
                assertEquals(request.cardNumber, card)
                steps += "remove"
                true
            },
            readAfterReinsertion = { opened ->
                assertTrue(opened === port && opened.isOpen)
                steps += "insert-and-read"
                download("Donald;Duck;")
            }
        )

        val comparison = requireNotNull(verifier.verify(port, rehearsal))

        assertEquals(listOf("remove", "insert-and-read"), steps)
        assertTrue(comparison.matches)
        assertEquals(SportIdentSi8OwnerWriteStage.VERIFIED, rehearsal.stage)
        assertTrue(port.writes.isEmpty())
    }

    @Test
    fun missingRemovalStopsBeforeAnotherRead() {
        val rehearsal = completedRehearsal()
        val verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> false },
            readAfterReinsertion = { error("Read attempted without removal") }
        )

        assertNull(verifier.verify(FakePort(), rehearsal))
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_NOT_OBSERVED, rehearsal.stopReason)
    }

    @Test
    fun wrongInsertAndInvalidBlocksCannotVerify() {
        val right = download("Donald;Duck;")
        val differentRawCard = right.blocks.first().data.copyOf().also { it[27]++ }
        listOf(
            right.copy(inserted = right.inserted.copy(siNumber = 2450663)),
            right.copy(readout = right.readout.copy(siNumber = 2450663)),
            right.copy(blocks = listOf(SportIdentCardBlock(0, differentRawCard), right.blocks[1]))
        ).forEach { wrong ->
            val rehearsal = completedRehearsal()
            val verifier = verifierFor(wrong)
            assertNull(verifier.verify(FakePort(), rehearsal))
            assertEquals(SportIdentSi8OwnerWriteStopReason.INVALID_READBACK, rehearsal.stopReason)
        }

        val incomplete = completedRehearsal()
        val verifier = verifierFor(right.copy(blocks = right.blocks.take(1)))
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(FakePort(), incomplete)
        }
        assertEquals(SportIdentSi8OwnerWriteStopReason.INVALID_READBACK, incomplete.stopReason)
    }

    @Test
    fun changedPunchByteFailsWholeCardComparison() {
        val changed = download("Donald;Duck;", block1Byte = 99)
        val rehearsal = completedRehearsal()
        val comparison = requireNotNull(verifierFor(changed).verify(FakePort(), rehearsal))

        assertFalse(comparison.matches)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_MISMATCH, rehearsal.stopReason)
        assertEquals(1, comparison.predictedVersusObserved.byteChanges.size)
    }

    @Test
    fun missingInsertionAndPrematureVerificationAreRejected() {
        val rehearsal = completedRehearsal()
        val verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> true },
            readAfterReinsertion = { error("No new card insert event") }
        )
        assertThrows(IllegalStateException::class.java) { verifier.verify(FakePort(), rehearsal) }
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_NOT_OBSERVED, rehearsal.stopReason)

        val premature = SportIdentSi8OwnerWriteRehearsal(request, before())
        assertThrows(IllegalStateException::class.java) { verifier.verify(FakePort(), premature) }
        assertEquals(SportIdentSi8OwnerWriteStage.READY_FOR_WORD, premature.stage)
    }

    private fun verifierFor(download: DesktopSportIdentCardBlockDownload) =
        DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> true },
            readAfterReinsertion = { download }
        )

    private fun completedRehearsal() = SportIdentSi8OwnerWriteRehearsal(request, before()).also { rehearsal ->
        replies.forEach { hex ->
            rehearsal.takeNextWordFrame()
            val frame = requireNotNull(SportIdentFrameParser.firstFrame(hex.bytes()))
            rehearsal.acceptWordResult(SportIdentCommandResult.Reply(frame))
        }
        assertEquals(SportIdentSi8OwnerWriteStage.REQUIRES_READBACK, rehearsal.stage)
    }

    private fun before() = SportIdentOwnerReadVerification.capture(request.stationNumber,
        download("Daisy;Duck;").blocks)

    private fun download(owner: String, block1Byte: Byte = 0): DesktopSportIdentCardBlockDownload {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128).also { it[8] = block1Byte }
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + block1))
        return DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, request.cardNumber),
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1)), readout)
    }

    private class FakePort : DesktopSerialPort {
        override val info = DesktopSerialPortInfo("/dev/cu.fake", "Fake SPORTident",
            SportIdentUsbDevice.VENDOR_ID, SportIdentUsbDevice.PRODUCT_ID, "fake")
        override val isOpen = true
        val writes = mutableListOf<ByteArray>()
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int) = true
        override fun close() = Unit
        override fun write(bytes: ByteArray): Int {
            writes += bytes
            return bytes.size
        }
        override fun read(maxBytes: Int) = ByteArray(0)
    }

    private fun String.bytes() = split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
