package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

class DesktopSportIdentOwnerWordTransportTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)
    private val capturedReplies = listOf(
        "02 ea 03 00 0a 08 00 2e 03".bytes(),
        "02 ea 03 00 0a 09 01 2e 03".bytes(),
        "02 ea 03 00 0a 0a 02 2e 03".bytes()
    )
    private val macReplies = listOf(
        "02 ea 03 00 0e 08 80 35 03".bytes(),
        "02 ea 03 00 0e 09 81 35 03".bytes(),
        "02 ea 03 00 0e 0a 82 35 03".bytes()
    )

    @Test
    fun sendsEachPlannedFrameOnceAndStopsForSeparateReadback() {
        val before = fixture()
        val port = FakePort(capturedReplies)
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, before)

        val transport = transport(port)
        transport.exchange(rehearsal)

        assertEquals(SportIdentSi8OwnerWriteStage.REQUIRES_READBACK, rehearsal.stage)
        assertEquals(3, port.writeRequests.size)
        SportIdentSi8OwnerWordWritePlanner.plan(request, before).forEachIndexed { index, frame ->
            assertArrayEquals(frame, port.writeRequests[index])
        }
        assertThrows(IllegalStateException::class.java) { transport.exchange(rehearsal) }
        assertEquals(3, port.writeRequests.size)
    }

    @Test
    fun transportAcceptsBothObservedStationCodesAndRejectsCrossStationReplies() {
        for ((code, replies) in listOf(10 to capturedReplies, 14 to macReplies)) {
            val port = FakePort(replies)
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, code, fixture())
            transport(port).exchange(rehearsal)
            assertEquals(SportIdentSi8OwnerWriteStage.REQUIRES_READBACK, rehearsal.stage)
            assertEquals(3, port.writeRequests.size)
        }
        val wrongStation = FakePort(capturedReplies)
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 14, fixture())
        transport(wrongStation).exchange(rehearsal)
        assertEquals(SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY, rehearsal.stopReason)
        assertEquals(1, wrongStation.writeRequests.size)
    }

    @Test
    fun intentionalSecondWordStopRetainsExactUpperBoundWithoutSendingThirdWord() {
        val before = fixture()
        val store = store("second-word.json")
        store.beginNative(request, before)
        val port = FakePort(capturedReplies)
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, before)
        transport(port).exchange(rehearsal,
            beforeWordAttempt = { store.markWordAttempt(request, it) }, stopAfterAcknowledgedWord = 2)

        assertEquals(SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP, rehearsal.stopReason)
        assertEquals(2, port.writeRequests.size)
        assertEquals(2, (store.load() as DesktopSportIdentOwnerRecoveryState.Pending).nativeAttempt?.attemptedWords)
        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(request, before, 2,
            observedPrefix(2))
        assertTrue(2 in assessment.matchingWordPrefixes)
        assertTrue(assessment.changesOutsideOwnerWords.isEmpty())
    }

    @Test
    fun crashAfterSavingAnyWordCannotSendThatWordAndLeavesRestartEvidence() {
        val before = fixture()
        for (crashAt in 1..3) {
            val store = store("crash-$crashAt.json")
            store.beginNative(request, before)
            val port = FakePort(capturedReplies)
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, before)
            assertThrows(IllegalStateException::class.java) {
                transport(port).exchange(rehearsal, beforeWordAttempt = { word ->
                    store.markWordAttempt(request, word)
                    if (word == crashAt) error("Simulated crash after saving word $word")
                })
            }
            assertEquals(crashAt - 1, port.writeRequests.size)
            assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, rehearsal.stopReason)
            val restarted = store("crash-$crashAt.json").load() as DesktopSportIdentOwnerRecoveryState.Pending
            assertEquals(before, restarted.nativeAttempt?.before)
            assertEquals(crashAt, restarted.nativeAttempt?.attemptedWords)
            val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(request, before,
                crashAt, observedPrefix(crashAt - 1))
            assertTrue(crashAt - 1 in assessment.matchingWordPrefixes)
            assertTrue(assessment.changesOutsideOwnerWords.isEmpty())
        }
    }

    @Test
    fun lostReplyAfterAnyWordKeepsBothPossibleFreshCardOutcomesAssessable() {
        val before = fixture()
        for (lostAt in 1..3) {
            val store = store("lost-$lostAt.json")
            store.beginNative(request, before)
            val port = FakePort(capturedReplies.take(lostAt - 1))
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, before)
            transport(port).exchange(rehearsal, beforeWordAttempt = { store.markWordAttempt(request, it) })
            assertEquals(SportIdentSi8OwnerWriteStopReason.NO_REPLY, rehearsal.stopReason)
            assertEquals(lostAt, port.writeRequests.size)
            val restarted = store("lost-$lostAt.json").load() as DesktopSportIdentOwnerRecoveryState.Pending
            assertEquals(lostAt, restarted.nativeAttempt?.attemptedWords)
            for (applied in lostAt - 1..lostAt) {
                val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(request, before,
                    lostAt, observedPrefix(applied))
                assertTrue(applied in assessment.matchingWordPrefixes)
                assertTrue(assessment.changesOutsideOwnerWords.isEmpty())
            }
        }
    }

    @Test
    fun negativeOrWrongStationReplyAtEachWordStopsWithNoSubsequentWrite() {
        val before = fixture()
        for (failedAt in 1..3) {
            for ((label, badReply, expectedReason) in listOf(
                Triple("nak", byteArrayOf(SportIdentProtocol.NAK),
                    SportIdentSi8OwnerWriteStopReason.NEGATIVE_ACKNOWLEDGEMENT),
                Triple("wrong-station", macReplies[failedAt - 1],
                    SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY)
            )) {
                val store = store("$label-$failedAt.json")
                store.beginNative(request, before)
                val port = FakePort(capturedReplies.take(failedAt - 1) + badReply)
                val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, before)
                transport(port).exchange(rehearsal, beforeWordAttempt = { store.markWordAttempt(request, it) })
                assertEquals(expectedReason, rehearsal.stopReason)
                assertEquals(failedAt, port.writeRequests.size)
                assertEquals(failedAt,
                    (store.load() as DesktopSportIdentOwnerRecoveryState.Pending).nativeAttempt?.attemptedWords)
            }
        }
    }

    @Test
    fun coalescedExtraReplyCannotBeMistakenForTheNextWordsReply() {
        val port = FakePort(listOf(capturedReplies[0] + capturedReplies[1]))
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())

        transport(port).exchange(rehearsal)

        assertEquals(1, port.writeRequests.size)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, rehearsal.stopReason)
    }

    @Test
    fun queuedInputStopsBeforeTheNextOwnerWord() {
        listOf(1, 2).forEach { nextWord ->
            val port = FakePort(capturedReplies,
                queuedBeforeWord = nextWord to byteArrayOf(SportIdentProtocol.STX,
                    SportIdentProtocol.SI_CARD_REMOVED))
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())

            transport(port).exchange(rehearsal)

            assertEquals(nextWord - 1, port.writeRequests.size)
            assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
            assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, rehearsal.stopReason)
        }
    }

    @Test
    fun unavailableQueueCheckStopsBeforeAnyOwnerWord() {
        val port = FakePort(capturedReplies, throwOnAvailable = true)
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())

        assertThrows(IllegalStateException::class.java) { transport(port).exchange(rehearsal) }

        assertEquals(0, port.writeRequests.size)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, rehearsal.stopReason)
    }

    @Test
    fun exposesTheFirstUnexpectedReplyForDiagnosisWithoutSendingAnotherWord() {
        val unfamiliar = SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.WRITE_SI_CARD_WORD, byteArrayOf(1, 0x0a, 0x08))
        val port = FakePort(listOf(unfamiliar))
        val observed = mutableListOf<ByteArray>()
        var now = 0L

        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())
        DesktopSportIdentOwnerWordTransport(port, readTimeoutMs = 4, nowMillis = { ++now },
            onWordResult = { word, result ->
                assertEquals(1, word)
                observed += (result as SportIdentCommandResult.Reply).frame.raw
            }).exchange(rehearsal)

        assertEquals(SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY, rehearsal.stopReason)
        assertEquals(1, port.writeRequests.size)
        assertEquals(1, observed.size)
        assertArrayEquals(unfamiliar.copyOfRange(1, unfamiliar.size), observed.single())
    }

    @Test
    fun extraBytesAfterFinalReplyCannotBeLostBeforeReadback() {
        val port = FakePort(listOf(capturedReplies[0], capturedReplies[1],
            capturedReplies[2] + byteArrayOf(SportIdentProtocol.NAK)))
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())

        transport(port).exchange(rehearsal)

        assertEquals(3, port.writeRequests.size)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, rehearsal.stopReason)
    }

    @Test
    fun wrongCommandBadCrcNakAndTimeoutStopWithoutRetry() {
        val wrongCommand = SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0x4d))
        val corrupt = capturedReplies[0].copyOf().also { it[it.lastIndex - 1] = 0x2f }
        listOf(
            listOf(wrongCommand) to SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY,
            listOf(corrupt) to SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY,
            listOf(byteArrayOf(SportIdentProtocol.NAK)) to SportIdentSi8OwnerWriteStopReason.NEGATIVE_ACKNOWLEDGEMENT,
            emptyList<ByteArray>() to SportIdentSi8OwnerWriteStopReason.NO_REPLY
        ).forEach { (chunks, reason) ->
            val port = FakePort(chunks)
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())
            transport(port).exchange(rehearsal)
            assertEquals(1, port.writeRequests.size)
            assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
            assertEquals(reason, rehearsal.stopReason)
        }
    }

    @Test
    fun shortWriteAndTransportExceptionLeaveAnUncertainStoppedAttempt() {
        val shortPort = FakePort(capturedReplies, writeLimit = 3)
        val shortRehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())
        transport(shortPort).exchange(shortRehearsal)
        assertEquals(1, shortPort.writeRequests.size)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, shortRehearsal.stopReason)

        val brokenPort = FakePort(capturedReplies, throwOnRead = true)
        val brokenRehearsal = SportIdentSi8OwnerWriteRehearsal(request, 10, fixture())
        assertThrows(IllegalStateException::class.java) { transport(brokenPort).exchange(brokenRehearsal) }
        assertEquals(1, brokenPort.writeRequests.size)
        assertEquals(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE, brokenRehearsal.stopReason)
    }

    private fun transport(port: DesktopSerialPort): DesktopSportIdentOwnerWordTransport {
        var now = 0L
        return DesktopSportIdentOwnerWordTransport(port, readTimeoutMs = 4, nowMillis = { ++now })
    }

    private fun store(name: String) = DesktopSportIdentOwnerRecoveryStore(
        temporary.root.toPath().resolve(name))

    private fun observedPrefix(words: Int) = ByteArray(128).also { block0 ->
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        val owner = listOf("Daisy;Duck;", "Donay;Duck;", "Donald;Dck;", "Donald;Duck;")[words]
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
    }.let { block0 ->
        SportIdentOwnerReadVerification.captureRaw(593927,
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, ByteArray(128))))
    }

    private fun fixture() = ByteArray(128).also { block0 ->
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        "Daisy;Duck;".forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
    }.let { block0 ->
        SportIdentOwnerReadVerification.capture(593927,
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, ByteArray(128))))
    }

    private class FakePort(
        chunks: List<ByteArray>,
        private val writeLimit: Int? = null,
        private val throwOnRead: Boolean = false,
        private val queuedBeforeWord: Pair<Int, ByteArray>? = null,
        private val throwOnAvailable: Boolean = false
    ) : DesktopSerialPort {
        private val pending = ArrayDeque(chunks)
        val writeRequests = mutableListOf<ByteArray>()
        override val info = DesktopSerialPortInfo("/dev/cu.fake", "Fake SPORTident",
            SportIdentUsbDevice.VENDOR_ID, SportIdentUsbDevice.PRODUCT_ID, "fake")
        override val isOpen = true
        override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) = Unit
        override fun open(waitTimeMillis: Int) = true
        override fun close() = Unit
        override fun write(bytes: ByteArray): Int {
            writeRequests += bytes.copyOf()
            return writeLimit ?: bytes.size
        }
        override fun read(maxBytes: Int): ByteArray {
            if (throwOnRead) error("Serial read failed")
            return pending.removeFirstOrNull() ?: ByteArray(0)
        }
        override fun readAvailable(maxBytes: Int): ByteArray {
            if (throwOnAvailable) error("Cannot inspect queued serial input")
            return queuedBeforeWord?.takeIf { it.first == writeRequests.size + 1 }
                ?.second?.copyOf() ?: byteArrayOf()
        }
    }

    private fun String.bytes() = split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
