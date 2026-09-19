package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportIdentSi8OwnerWriteRehearsalTest {
    private val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)
    private val capturedFrames = listOf(
        "ff 02 ea 05 08 44 6f 6e 61 96 4e 03",
        "ff 02 ea 05 09 6c 64 3b 44 9e 90 03",
        "ff 02 ea 05 0a 75 63 6b 3b cf 84 03"
    )
    private val capturedReplies = listOf(
        "02 ea 03 00 0a 08 00 2e 03",
        "02 ea 03 00 0a 09 01 2e 03",
        "02 ea 03 00 0a 0a 02 2e 03"
    )

    @Test
    fun capturedExchangeRequiresOneReplyPerWordAndThenAnIndependentRead() {
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;"))
        assertFailsWith<IllegalStateException> { rehearsal.acceptWordResult(SportIdentCommandResult.NoReply) }
        capturedFrames.forEachIndexed { index, expected ->
            val issued = rehearsal.takeNextWordFrame()
            assertEquals(expected, issued.hex())
            assertEquals(SportIdentSi8OwnerWriteStage.AWAITING_WORD_REPLY, rehearsal.stage)
            assertFailsWith<IllegalStateException> { rehearsal.takeNextWordFrame() }
            rehearsal.acceptWordResult(SportIdentCommandResult.Reply(parse(capturedReplies[index])))
            assertEquals(if (index == 2) SportIdentSi8OwnerWriteStage.REQUIRES_READBACK
                else SportIdentSi8OwnerWriteStage.READY_FOR_WORD, rehearsal.stage)
        }
        assertFailsWith<IllegalStateException> { rehearsal.takeNextWordFrame() }
        assertFailsWith<IllegalStateException> {
            rehearsal.acceptWordResult(SportIdentCommandResult.Reply(parse(capturedReplies.last())))
        }
        val comparison = rehearsal.compareIndependentRead(fixture("Donald;Duck;"))
        assertTrue(comparison.matches)
        assertEquals(SportIdentSi8OwnerWriteStage.VERIFIED, rehearsal.stage)
        assertNull(rehearsal.stopReason)
        assertFailsWith<IllegalStateException> { rehearsal.compareIndependentRead(fixture("Donald;Duck;")) }
    }

    @Test
    fun unknownReplyTimeoutAndNegativeAcknowledgementStopWithoutAnotherFrame() {
        val badReply = SportIdentCommandResult.Reply(parse(
            SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.WRITE_SI_CARD_WORD,
                byteArrayOf(1, 0x0a, 0x08))))
        val corruptBytes = capturedReplies.first().bytes().also { it[it.lastIndex - 1] = 0x2f }
        val corruptReply = SportIdentCommandResult.Reply(
            requireNotNull(SportIdentFrameParser.firstFrame(corruptBytes, requireValidCrc = false)))
        listOf(
            SportIdentCommandResult.NoReply to SportIdentSi8OwnerWriteStopReason.NO_REPLY,
            SportIdentCommandResult.NegativeAcknowledgement to SportIdentSi8OwnerWriteStopReason.NEGATIVE_ACKNOWLEDGEMENT,
            badReply to SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY,
            corruptReply to SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY,
            SportIdentCommandResult.Reply(parse(capturedReplies[1])) to SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY
        ).forEach { (result, reason) ->
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;"))
            rehearsal.takeNextWordFrame()
            rehearsal.acceptWordResult(result)
            assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
            assertEquals(reason, rehearsal.stopReason)
            assertFailsWith<IllegalStateException> { rehearsal.takeNextWordFrame() }
            assertFailsWith<IllegalStateException> {
                rehearsal.acceptWordResult(SportIdentCommandResult.Reply(parse(capturedReplies[0])))
            }
        }
    }

    @Test
    fun failedPrewritePresenceCheckCannotIssueTheFirstWord() {
        val rehearsal = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;"))

        rehearsal.stopForUnconfirmedCard()

        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, rehearsal.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.CARD_NOT_CONFIRMED, rehearsal.stopReason)
        assertFailsWith<IllegalStateException> { rehearsal.takeNextWordFrame() }
        assertFailsWith<IllegalStateException> { rehearsal.stopForUnconfirmedCard() }
        val alreadyStarted = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;"))
        alreadyStarted.takeNextWordFrame()
        assertFailsWith<IllegalStateException> { alreadyStarted.stopForUnconfirmedCard() }
    }

    @Test
    fun cancelledMismatchedOrInvalidReadbackCannotResumeWords() {
        val cancelled = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;"))
        cancelled.takeNextWordFrame()
        cancelled.cancel()
        assertEquals(SportIdentSi8OwnerWriteStopReason.CANCELLED, cancelled.stopReason)
        assertFailsWith<IllegalStateException> { cancelled.takeNextWordFrame() }

        val mismatch = completedRehearsal()
        val comparison = mismatch.compareIndependentRead(fixture("Daisy;Duck;"))
        assertFalse(comparison.matches)
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, mismatch.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_MISMATCH, mismatch.stopReason)
        assertFailsWith<IllegalStateException> { mismatch.takeNextWordFrame() }

        val alteredPunch = fixture("Donald;Duck;").let { after ->
            val block1 = ByteArray(128).also { it[8] = 99 }
            SportIdentOwnerReadVerification.capture(after.stationNumber,
                listOf(SportIdentCardBlock(0, decode(after.blocks.single { it.blockNumber == 0 })),
                    SportIdentCardBlock(1, block1)))
        }
        val punchMismatch = completedRehearsal()
        assertFalse(punchMismatch.compareIndependentRead(alteredPunch).matches)
        assertEquals(SportIdentSi8OwnerWriteStopReason.READBACK_MISMATCH, punchMismatch.stopReason)

        val invalid = completedRehearsal()
        assertFailsWith<IllegalArgumentException> {
            invalid.compareIndependentRead(fixture("Donald;Duck;").copy(blocks = emptyList()))
        }
        assertEquals(SportIdentSi8OwnerWriteStage.STOPPED, invalid.stage)
        assertEquals(SportIdentSi8OwnerWriteStopReason.INVALID_READBACK, invalid.stopReason)
    }

    private fun completedRehearsal() = SportIdentSi8OwnerWriteRehearsal(request, fixture("Daisy;Duck;")).also {
        capturedReplies.forEach { reply ->
            it.takeNextWordFrame()
            it.acceptWordResult(SportIdentCommandResult.Reply(parse(reply)))
        }
    }

    private fun fixture(owner: String): SportIdentOwnerReadFixture {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        return SportIdentOwnerReadVerification.capture(593927,
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, ByteArray(128))))
    }

    private fun parse(hex: String) = parse(hex.bytes())
    private fun parse(bytes: ByteArray) = requireNotNull(SportIdentFrameParser.firstFrame(bytes))
    private fun decode(block: SportIdentOwnerReadBlock) = ByteArray(128) { index ->
        block.hexData.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
    private fun String.bytes() = split(' ').map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString(" ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
