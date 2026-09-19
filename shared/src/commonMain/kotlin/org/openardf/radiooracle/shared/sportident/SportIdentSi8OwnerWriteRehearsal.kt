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
    READBACK_MISMATCH,
    INVALID_READBACK,
    TRANSPORT_FAILURE,
    CANCELLED
}

/**
 * Transport-free rehearsal of the one observed SI-Card8 owner-write sequence.
 * Handing out a frame consumes that attempt: an absent or unfamiliar reply
 * permanently stops this session, and no reply alone proves a card write.
 */
class SportIdentSi8OwnerWriteRehearsal(
    private val request: SportIdentOwnerNameWriteRequest,
    private val before: SportIdentOwnerReadFixture
) {
    private val frames = SportIdentSi8OwnerWordWritePlanner.plan(request, before)
    private val replies = SportIdentSi8OwnerWordWriteReplySequence()
    private var nextFrameIndex = 0

    var stage: SportIdentSi8OwnerWriteStage = SportIdentSi8OwnerWriteStage.READY_FOR_WORD
        private set
    var stopReason: SportIdentSi8OwnerWriteStopReason? = null
        private set

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

    /** A short write, serial failure, or unsolicited input leaves the outcome uncertain. */
    fun abortTransport() {
        check(stage != SportIdentSi8OwnerWriteStage.VERIFIED) { "Verified rehearsal is already complete." }
        if (stage != SportIdentSi8OwnerWriteStage.STOPPED) stop(SportIdentSi8OwnerWriteStopReason.TRANSPORT_FAILURE)
    }

    private fun stop(reason: SportIdentSi8OwnerWriteStopReason) {
        stage = SportIdentSi8OwnerWriteStage.STOPPED
        stopReason = reason
    }
}
