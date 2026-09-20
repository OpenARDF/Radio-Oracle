package org.openardf.radiooracle.shared.sportident

/**
 * Matches CRC-valid owner-word replies in address order, with the connected
 * station's code replacing the captured code.
 * A match is not a write-success verdict. No transport or retry behavior.
 */
class SportIdentSi8OwnerWordWriteReplySequence(private val stationCode: Int, private val wordCount: Int = 3) {
    init {
        require(stationCode in 0..0xffff) { "Station code must fit the two reply bytes." }
        require(wordCount in 1..7) { "An SI-Card8 owner write needs one to seven words." }
    }
    private var matchedCount = 0

    val nextExpectedWordAddress: Int?
        get() = if (matchedCount < wordCount) FIRST_OWNER_WORD + matchedCount else null

    val allObservedRepliesMatched: Boolean
        get() = matchedCount == wordCount

    fun accept(frame: SportIdentFrame) {
        val expectedAddress = requireNotNull(nextExpectedWordAddress) { "All observed word replies were already matched." }
        require(frame.extended && frame.crcValid == true && frame.command == SportIdentProtocol.WRITE_SI_CARD_WORD &&
            frame.data.size == 3 &&
            (frame.data[0].toInt() and 0xff) == (stationCode shr 8) &&
            (frame.data[1].toInt() and 0xff) == (stationCode and 0xff) &&
            (frame.data[2].toInt() and 0xff) == expectedAddress) {
            "SI-Card8 word reply differs from the observed CRC-valid sequence."
        }
        matchedCount++
    }

    private companion object {
        const val FIRST_OWNER_WORD = 0x08
    }
}
