package org.openardf.radiooracle.shared.sportident

enum class SportIdentSi8OwnerWriteStage {
    READY_FOR_WORD,
    AWAITING_WORD_REPLY,
    REQUIRES_READBACK,
    VERIFIED,
    STOPPED
}

enum class SportIdentSi8OwnerWriteStopReason {
    NO_REPLY,
    NEGATIVE_ACKNOWLEDGEMENT,
    UNEXPECTED_REPLY,
    READBACK_NOT_OBSERVED,
    READBACK_MISMATCH,
    INVALID_READBACK,
    CARD_NOT_CONFIRMED,
    TRANSPORT_FAILURE,
    INTENTIONAL_STOP,
    CANCELLED
}

/**
 * Transport-free rehearsal of the one observed SI-Card8 owner-write sequence.
 * Handing out a frame consumes that attempt: an absent or unfamiliar reply
 * permanently stops this session, and no reply alone proves a card write.
 */
class SportIdentSi8OwnerWriteRehearsal(
    private val request: SportIdentOwnerNameWriteRequest,
    stationCode: Int,
    private val before: SportIdentOwnerReadFixture
) {
    private val frames = SportIdentSi8OwnerWordWritePlanner.plan(request, before)
    private val replies = SportIdentSi8OwnerWordWriteReplySequence(stationCode)
    private var nextFrameIndex = 0

    var stage: SportIdentSi8OwnerWriteStage = SportIdentSi8OwnerWriteStage.READY_FOR_WORD
        private set
    var stopReason: SportIdentSi8OwnerWriteStopReason? = null
        private set

    val targetStationNumber: Int get() = request.stationNumber
    val targetCardNumber: Int get() = request.cardNumber

    /** Returns only the next planned frame; it may not be requested again. */
    fun takeNextWordFrame(): ByteArray {
        check(stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) { "No SI-Card8 word frame is ready." }
        stage = SportIdentSi8OwnerWriteStage.AWAITING_WORD_REPLY
        return frames[nextFrameIndex].copyOf()
    }

    /** A matching reply permits the next word, but never verifies a card write. */
    fun acceptWordResult(result: SportIdentCommandResult) {
        check(stage == SportIdentSi8OwnerWriteStage.AWAITING_WORD_REPLY) { "No SI-Card8 word reply is pending." }
        val failure = when (result) {
            SportIdentCommandResult.NoReply -> SportIdentSi8OwnerWriteStopReason.NO_REPLY
            SportIdentCommandResult.NegativeAcknowledgement -> SportIdentSi8OwnerWriteStopReason.NEGATIVE_ACKNOWLEDGEMENT
            is SportIdentCommandResult.Reply -> {
                try {
                    replies.accept(result.frame)
                    null
                } catch (_: IllegalArgumentException) {
                    SportIdentSi8OwnerWriteStopReason.UNEXPECTED_REPLY
                }
            }
        }
        if (failure != null) {
            stop(failure)
            return
        }
        nextFrameIndex++
        stage = if (nextFrameIndex == frames.size) SportIdentSi8OwnerWriteStage.REQUIRES_READBACK
        else SportIdentSi8OwnerWriteStage.READY_FOR_WORD
    }

    /** The caller must supply a genuinely fresh independent read; a fixture cannot prove freshness. */
    fun compareIndependentRead(after: SportIdentOwnerReadFixture): SportIdentSi8OwnerWritePlanComparison {
        check(stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) { "All word replies must precede read-back." }
        // An invalid read must also leave the session terminal. Never resume or retry the words.
        stop(SportIdentSi8OwnerWriteStopReason.INVALID_READBACK)
        val comparison = SportIdentSi8OwnerWordWritePlanner.compareToObserved(request, before, after)
        if (comparison.matches) {
            stopReason = null
            stage = SportIdentSi8OwnerWriteStage.VERIFIED
        } else {
            stopReason = SportIdentSi8OwnerWriteStopReason.READBACK_MISMATCH
        }
        return comparison
    }

    fun cancel() {
        check(stage != SportIdentSi8OwnerWriteStage.VERIFIED) { "Verified rehearsal is already complete." }
        if (stage != SportIdentSi8OwnerWriteStage.STOPPED) stop(SportIdentSi8OwnerWriteStopReason.CANCELLED)
    }

    /** Experimental interruption after an acknowledged prefix; the card still needs a fresh read. */
    fun stopAfterAcknowledgedWord() {
        check(stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD && nextFrameIndex in 1 until frames.size)
        stop(SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP)
    }

    /** Experimental stop after the last reply, before requesting any read-back. */
    fun stopBeforeReadback() {
        check(stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK && nextFrameIndex == frames.size)
        stop(SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP)
    }

    /** A short write, serial failure, or unsolicited input leaves the outcome uncertain. */
    fun abortTransport() {
        check(stage != SportIdentSi8OwnerWriteStage.VERIFIED) { "Verified rehearsal is already complete." }
        if (stage != SportIdentSi8OwnerWriteStage.STOPPED) stop(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE)
    }

    fun stopForMissingReadback() {
        check(stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) { "No SI-Card8 read-back is pending." }
        stop(SportIdentSi8OwnerWriteStopReason.READBACK_NOT_OBSERVED)
    }

    fun rejectReadback() {
        check(stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) { "No SI-Card8 read-back is pending." }
        stop(SportIdentSi8OwnerWriteStopReason.INVALID_READBACK)
    }

    /** A read-only recheck failed before the first owner word was attempted. */
    fun stopForUnconfirmedCard() {
        check(stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD && nextFrameIndex == 0) {
            "The SI-Card8 pre-write card check must precede every owner word."
        }
        stop(SportIdentSi8OwnerWriteStopReason.CARD_NOT_CONFIRMED)
    }

    private fun stop(reason: SportIdentSi8OwnerWriteStopReason) {
        stage = SportIdentSi8OwnerWriteStage.STOPPED
        stopReason = reason
    }
}
