package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportIdentSi8OwnerWordWriteReplySequenceTest {
    private val capturedReplies = listOf(
        "02 ea 03 00 0a 08 00 2e 03",
        "02 ea 03 00 0a 09 01 2e 03",
        "02 ea 03 00 0a 0a 02 2e 03"
    )
    private val macReplies = listOf(
        "02 ea 03 00 0e 08 80 35 03",
        "02 ea 03 00 0e 09 81 35 03",
        "02 ea 03 00 0e 0a 82 35 03"
    )

    @Test
    fun matchesAllThreeCrcValidConfigPlusRepliesInOrder() {
        val sequence = SportIdentSi8OwnerWordWriteReplySequence(10)
        assertEquals(0x08, sequence.nextExpectedWordAddress)
        capturedReplies.forEachIndexed { index, hex ->
            val frame = parse(hex)
            assertEquals(true, frame.crcValid)
            sequence.accept(frame)
            assertEquals(if (index == 2) null else 0x09 + index, sequence.nextExpectedWordAddress)
        }
        assertTrue(sequence.allObservedRepliesMatched)
        assertNull(sequence.nextExpectedWordAddress)
        assertFailsWith<IllegalArgumentException> { sequence.accept(parse(capturedReplies.last())) }
    }

    @Test
    fun refusesWrongStationOutOfOrderDuplicateAndCorruptRepliesWithoutAdvancing() {
        val sequence = SportIdentSi8OwnerWordWriteReplySequence(10)
        val first = parse(capturedReplies[0])
        val second = parse(capturedReplies[1])
        val wrongStation = parse(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.WRITE_SI_CARD_WORD, byteArrayOf(1, 0x0a, 0x08)))
        val wrongCommand = parse(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.PROBE_COMMAND, byteArrayOf(0, 0x0a, 0x08)))
        val wrongLength = parse(SportIdentProtocol.buildExtendedMessage(
            SportIdentProtocol.WRITE_SI_CARD_WORD, byteArrayOf(0, 0x08)))
        val corrupt = capturedReplies[0].hexBytes().also { it[it.lastIndex - 1] = 0x2f }
        val corruptFrame = assertNotNull(SportIdentFrameParser.firstFrame(corrupt, requireValidCrc = false))
        assertFalse(corruptFrame.crcValid ?: true)

        listOf(second, wrongStation, wrongCommand, wrongLength, corruptFrame).forEach { frame ->
            assertFailsWith<IllegalArgumentException> { sequence.accept(frame) }
            assertEquals(0x08, sequence.nextExpectedWordAddress)
        }
        sequence.accept(first)
        assertFailsWith<IllegalArgumentException> { sequence.accept(first) }
        assertEquals(0x09, sequence.nextExpectedWordAddress)
        assertFalse(sequence.allObservedRepliesMatched)
    }

    @Test
    fun matchesConnectedStationCodeInsteadOfCapturedStationCode() {
        val station14 = SportIdentSi8OwnerWordWriteReplySequence(14)
        val station10 = SportIdentSi8OwnerWordWriteReplySequence(10)
        val reply = parse(macReplies.first())

        assertFailsWith<IllegalArgumentException> { station10.accept(reply) }
        assertEquals(0x08, station10.nextExpectedWordAddress)
        station14.accept(reply)
        assertEquals(0x09, station14.nextExpectedWordAddress)
        macReplies.drop(1).forEach { station14.accept(parse(it)) }
        assertTrue(station14.allObservedRepliesMatched)
    }

    private fun parse(hex: String): SportIdentFrame = parse(hex.hexBytes())

    private fun parse(bytes: ByteArray): SportIdentFrame =
        assertNotNull(SportIdentFrameParser.firstFrame(bytes))

    private fun String.hexBytes(): ByteArray = split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
