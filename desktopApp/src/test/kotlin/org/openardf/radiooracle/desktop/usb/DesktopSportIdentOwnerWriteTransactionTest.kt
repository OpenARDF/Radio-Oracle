package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentOwnerWriteTransactionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)
    private val replies = listOf(
        "02 ea 03 00 0a 08 00 2e 03".bytes(),
        "02 ea 03 00 0a 09 01 2e 03".bytes(),
        "02 ea 03 00 0a 0a 02 2e 03".bytes()
    )

    @Test
    fun runsReadWordsRemovalAndIndependentReadOnOnePort() {
        val port = readyPort(replies)
        val steps = mutableListOf<String>()
        val transaction = transaction(port,
            readCard = { opened ->
                assertTrue(opened === port && opened.isOpen)
                steps += "before-read"
                download("Daisy;Duck;")
            },
            verifier = DesktopSportIdentOwnerReadbackVerifier(
                awaitTargetRemoval = { opened, card ->
                    assertTrue(opened === port && opened.isOpen)
                    assertEquals(request.cardNumber, card)
                    assertEquals(3, port.ownerWordWrites.size)
                    steps += "remove"
                    true
                },
                readAfterReinsertion = { opened ->
                    assertTrue(opened === port && opened.isOpen)
                    steps += "after-read"
                    download("Donald;Duck;")
                }
            ))

        val outcome = transaction.execute(request)

        assertEquals(listOf("before-read", "remove", "after-read"), steps)
        assertTrue(outcome.verified)
        assertEquals(SportIdentSi8OwnerWriteStage.VERIFIED, outcome.stage)
        assertNull(outcome.stopReason)
        assertEquals(DesktopSportIdentCardPresenceResult.MATCHING_BLOCK, outcome.prewritePresence)
        assertTrue(requireNotNull(outcome.comparison).matches)
        val expected = SportIdentSi8OwnerWordWritePlanner.plan(request,
            SportIdentOwnerReadVerification.capture(request.stationNumber, download("Daisy;Duck;").blocks))
        expected.forEachIndexed { index, bytes -> assertArrayEquals(bytes, port.ownerWordWrites[index]) }
        assertEquals(4, port.writeRequests.size)
        assertEquals(1, port.closeCount)
        assertFalse(port.isOpen)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, recoveryStore().load())
    }

    @Test
    fun missingFirstReplyStopsAfterOneWordAndNeverStartsReadback() {
        val port = readyPort(emptyList())
        val transaction = transaction(port, verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> error("Read-back started after a missing word reply") },
            readAfterReinsertion = { error("Read-back started after a missing word reply") }
        ))

        val outcome = transaction.execute(request)

        assertFalse(outcome.verified)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, outcome.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.NO_REPLY, outcome.stopReason)
        assertNull(outcome.comparison)
        assertEquals(1, port.ownerWordWrites.size)
        assertEquals(1, port.closeCount)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), recoveryStore().load())

        val retryPort = readyPort(replies)
        assertThrows(IllegalStateException::class.java) { transaction(retryPort).execute(request) }
        assertTrue(retryPort.writeRequests.isEmpty())
        assertEquals(0, retryPort.closeCount)
    }

    @Test
    fun missingRemovalCannotReportVerificationAfterAllThreeReplies() {
        val port = readyPort(replies)
        val transaction = transaction(port, verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> false },
            readAfterReinsertion = { error("Read-back started without card removal") }
        ))

        val outcome = transaction.execute(request)

        assertFalse(outcome.verified)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_NOT_OBSERVED, outcome.stopReason)
        assertEquals(3, port.ownerWordWrites.size)
        assertEquals(1, port.closeCount)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), recoveryStore().load())
    }

    @Test
    fun changedNonOwnerByteLeavesWholeTransactionStopped() {
        val port = readyPort(replies)
        val transaction = transaction(port, verifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> true },
            readAfterReinsertion = { download("Donald;Duck;", changedPunchByte = 99) }
        ))

        val outcome = transaction.execute(request)

        assertFalse(outcome.verified)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_MISMATCH, outcome.stopReason)
        assertEquals(1, requireNotNull(outcome.comparison).predictedVersusObserved.byteChanges.size)
        assertEquals(3, port.ownerWordWrites.size)
        assertEquals(1, port.closeCount)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), recoveryStore().load())
    }

    @Test
    fun rejectedPreflightAndTransportExceptionClosePortWithoutRetry() {
        val wrongCardPort = readyPort(replies)
        assertThrows(IllegalArgumentException::class.java) {
            transaction(wrongCardPort, readCard = { download("Daisy;Duck;").copy(
                inserted = SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, 2450663)
            ) }).execute(request)
        }
        assertTrue(wrongCardPort.writeRequests.isEmpty())
        assertEquals(1, wrongCardPort.closeCount)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, recoveryStore().load())

        val brokenPort = readyPort(replies, failAfterReads = 1)
        assertThrows(IllegalStateException::class.java) { transaction(brokenPort).execute(request) }
        assertEquals(1, brokenPort.ownerWordWrites.size)
        assertEquals(1, brokenPort.closeCount)
        assertFalse(brokenPort.isOpen)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), recoveryStore().load())
    }

    @Test
    fun removedCardStopsBeforeAnyOwnerWord() {
        val port = FakePort(listOf(byteArrayOf(SportIdentProtocol.NAK)) + replies)

        val outcome = transaction(port).execute(request)

        assertFalse(outcome.verified)
        assertEquals(SportIdentSi8OwnerWriteStopReason.CARD_NOT_CONFIRMED, outcome.stopReason)
        assertEquals(DesktopSportIdentCardPresenceResult.NEGATIVE_ACKNOWLEDGEMENT, outcome.prewritePresence)
        assertTrue(port.ownerWordWrites.isEmpty())
        assertEquals(1, port.writeRequests.size)
        assertEquals(1, port.closeCount)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, recoveryStore().load())
    }

    @Test
    fun unavailableRecoveryRecordBlocksEvenThePreflight() {
        val port = readyPort(replies)
        val file = temporary.newFolder("recovery.json").toPath()
        val unavailable = DesktopSportIdentOwnerRecoveryStore(file)

        assertThrows(IllegalStateException::class.java) {
            transaction(port, recoveryStore = unavailable).execute(request)
        }

        assertEquals(DesktopSportIdentOwnerRecoveryState.Unavailable, unavailable.load())
        assertTrue(port.writeRequests.isEmpty())
        assertEquals(0, port.closeCount)
    }

    private fun transaction(
        port: FakePort,
        readCard: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = { download("Daisy;Duck;") },
        recoveryStore: DesktopSportIdentOwnerRecoveryStore = recoveryStore(),
        verifier: DesktopSportIdentOwnerReadbackVerifier = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { _, _ -> true },
            readAfterReinsertion = { download("Donald;Duck;") }
        )
    ): DesktopSportIdentOwnerWriteTransaction {
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
            readCard = readCard
        )
        var presenceNow = 0L
        var wordNow = 0L
        val presenceProbe = DesktopSportIdentCardPresenceProbe(DesktopSportIdentStationCommandClient(
            readTimeoutMs = 50, nowMillis = { ++presenceNow }
        ))
        return DesktopSportIdentOwnerWriteTransaction(preflight, verifier, recoveryStore, presenceProbe) { opened ->
            DesktopSportIdentOwnerWordTransport(opened, readTimeoutMs = 4, nowMillis = { ++wordNow })
        }
    }

    private fun recoveryStore() = DesktopSportIdentOwnerRecoveryStore(
        temporary.root.toPath().resolve("recovery.json")
    )

    private fun readyPort(replies: List<ByteArray>, failAfterReads: Int? = null) =
        FakePort(listOf(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.GET_SI_CARD8_9_SIAC,
            byteArrayOf(0, 0, 0) + download("Daisy;Duck;").blocks.first().data
        )) + replies, failAfterReads)

    private fun download(owner: String, changedPunchByte: Byte = 0): DesktopSportIdentCardBlockDownload {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        val block1 = ByteArray(128).also { it[8] = changedPunchByte }
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(block0 + block1))
        return DesktopSportIdentCardBlockDownload(
            SportIdentCardEvent.Inserted(SportIdentProtocol.SI_CARD8_9_SIAC, request.cardNumber),
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, block1)), readout)
    }

    private class FakePort(chunks: List<ByteArray>, private val failAfterReads: Int? = null) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)
        override val info = DesktopSerialPortInfo("/dev/cu.fake", "Fake SPORTident",
            SportIdentUsbDevice.VENDOR_ID, SportIdentUsbDevice.PRODUCT_ID, "fake")
        override var isOpen = false
        var closeCount = 0
        var readCount = 0
        val writeRequests = mutableListOf<ByteArray>()
        val ownerWordWrites: List<ByteArray>
            get() = writeRequests.filter { it.size > 2 && it[2] == SportIdentProtocol.WRITE_SI_CARD_WORD }
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int): Boolean {
            isOpen = true
            return true
        }
        override fun close() {
            closeCount++
            isOpen = false
        }
        override fun write(bytes: ByteArray): Int {
            writeRequests += bytes.copyOf()
            return bytes.size
        }
        override fun read(maxBytes: Int): ByteArray {
            if (failAfterReads != null && readCount >= failAfterReads) error("Serial read failed")
            readCount++
            return pending.removeFirstOrNull() ?: ByteArray(0)
        }
    }

    private fun String.bytes() = split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
