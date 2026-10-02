package org.openardf.radiooracle.backend.sportident

import org.openardf.radiooracle.shared.sportident.SportIdentCommandResult
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

internal enum class AndroidSportIdentCardPresenceResult {
    MATCHING_BLOCK,
    DIFFERENT_BLOCK,
    NO_REPLY,
    INVALID_REPLY
}

/**
 * Android-specific physical instructions surrounding the shared guarded transaction.
 * The shared writer intentionally remains independent of USB insertion/removal UX.
 */
enum class AndroidSportIdentOwnerWriteInstruction {
    REMOVE_FOR_WRITE,
    INSERT_FOR_WRITE,
    KEEP_INSERTED_FOR_WRITE,
    REMOVE_FOR_READ_BACK,
    INSERT_FOR_READ_BACK,
    KEEP_INSERTED_FOR_READ_BACK
}

data class AndroidSportIdentOwnerWriteResult(
    val comparison: SportIdentSi8OwnerWritePlanComparison
) {
    val cardNumber: Int = comparison.predictedVersusObserved.after.cardNumber
    val controlPunchCount: Int = comparison.predictedVersusObserved.after.controlPunchCount
}

/** Reject a swapped or changed card before recovery persistence or transmission. */
internal object AndroidSportIdentOwnerWritePreflight {
    /**
     * Establishes whether the inspected card is still seated before the next insertion.
     * A matching seated card must be removed; no reply means it is already removed.
     * A changed card or invalid reply fails closed before recovery state is created.
     */
    fun prepareFreshInsertionBoundary(
        probeCurrentCard: () -> AndroidSportIdentCardPresenceResult,
        discardQueuedCardEvents: () -> Unit,
        maxProbeAttempts: Int = 2
    ): Boolean {
        require(maxProbeAttempts > 0) { "The seated-card probe count must be positive." }
        repeat(maxProbeAttempts) {
            when (probeCurrentCard()) {
                AndroidSportIdentCardPresenceResult.MATCHING_BLOCK -> {
                    // Only events older than this successful live probe are
                    // discarded; the UI removal prompt is exposed afterwards.
                    discardQueuedCardEvents()
                    return true
                }

                AndroidSportIdentCardPresenceResult.NO_REPLY -> Unit
                AndroidSportIdentCardPresenceResult.DIFFERENT_BLOCK ->
                    throw IllegalArgumentException(
                        "The seated SI-Card8 no longer matches the inspected baseline."
                    )

                AndroidSportIdentCardPresenceResult.INVALID_REPLY ->
                    throw IllegalArgumentException(
                        "The seated SI-Card8 returned an invalid preflight reply."
                    )
            }
        }
        // With no live card response, discard only lifecycle messages older
        // than this boundary and use the next insertion as the fresh preflight.
        discardQueuedCardEvents()
        return false
    }

    fun requireUnchangedTarget(
        request: SportIdentOwnerNameWriteRequest,
        expected: SportIdentOwnerReadFixture,
        fresh: SportIdentOwnerReadFixture
    ) {
        val freshRead = SportIdentOwnerReadVerification.nativeRead(fresh)
        val diff = SportIdentOwnerReadVerification.diffNative(expected, fresh)
        require(
            expected.stationNumber == request.stationNumber &&
                fresh.stationNumber == request.stationNumber &&
                freshRead.cardNumber == request.cardNumber &&
                diff.byteChanges.isEmpty()
        ) { "The freshly inserted SI-Card8 does not match the inspected baseline." }
    }
}

/** One-shot Android owner-word transport; writes are deliberately never retried. */
internal class AndroidSportIdentOwnerWordTransport(
    private val writeFrame: (ByteArray) -> Boolean,
    private val readReply: (timeoutMillis: Int) -> ByteArray?,
    private val pause: (Long) -> Unit = Thread::sleep
) {
    fun exchange(rehearsal: SportIdentSi8OwnerWriteRehearsal) {
        check(rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD)
        while (rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
            try {
                val frame = rehearsal.takeNextWordFrame()
                if (!writeFrame(frame)) {
                    rehearsal.abortTransport()
                    return
                }
                val raw = readReply(WORD_REPLY_TIMEOUT_MS)
                val result = when {
                    raw == null -> SportIdentCommandResult.NoReply
                    raw.size == 1 && raw[0] == org.openardf.radiooracle.shared.sportident.SportIdentProtocol.NAK ->
                        SportIdentCommandResult.NegativeAcknowledgement
                    else -> SportIdentFrameParser.firstFrame(raw, requireValidCrc = false)
                        ?.let(SportIdentCommandResult::Reply)
                        ?: SportIdentCommandResult.NoReply
                }
                rehearsal.acceptWordResult(result)
                if (rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD) {
                    pause(PAUSE_AFTER_REPLY_MS)
                }
            } catch (error: Exception) {
                rehearsal.abortTransport()
                throw error
            }
        }
    }

    private companion object {
        const val WORD_REPLY_TIMEOUT_MS = 1_200
        const val PAUSE_AFTER_REPLY_MS = 200L
    }
}
