package org.openardf.radiooracle.shared.sportident

/**
 * Matches only the three reply shapes observed in one successful Config+ SI-Card8
 * owner write. The two leading data bytes have unknown semantics, so a match is
 * not a write-success verdict. This class has no transport or retry behavior.
 */
class SportIdentSi8OwnerWordWriteReplySequence {
    private var matchedCount = 0

    val nextExpectedWordAddress: Int?
        get() = if (matchedCount < WORD_COUNT) FIRST_OWNER_WORD + matchedCount else null

    val allObservedRepliesMatched: Boolean
        get() = matchedCount == WORD_COUNT

    fun accept(frame: SportIdentFrame) {
        val expectedAddress = requireNotNull(nextExpectedWordAddress) { "All observed word replies were already matched." }
        require(frame.extended && frame.crcValid == true && frame.command == SportIdentProtocol.WRITE_SI_CARD_WORD &&
            frame.data.size == 3 && frame.data[0] == OBSERVED_FIRST_DATA_BYTE &&
            frame.data[1] == OBSERVED_SECOND_DATA_BYTE &&
            (frame.data[2].toInt() and 0xff) == expectedAddress) {
            "SI-Card8 word reply differs from the observed CRC-valid sequence."
        }
        matchedCount++
    }

    private companion object {
        const val FIRST_OWNER_WORD = 0x08
        const val WORD_COUNT = 3
        const val OBSERVED_FIRST_DATA_BYTE: Byte = 0x00
        const val OBSERVED_SECOND_DATA_BYTE: Byte = 0x0A
    }
}
