package org.openardf.radiooracle.shared.sportident

/** Supported owner-word shapes. Experimental exact counts remain explicit. */
sealed interface SportIdentOwnerWriteWordCountPolicy {
    data object PreviouslyVerifiedDirectShape : SportIdentOwnerWriteWordCountPolicy
    data object AnySupportedShape : SportIdentOwnerWriteWordCountPolicy
    data class Exact(val words: Int) : SportIdentOwnerWriteWordCountPolicy {
        init {
            require(words in 1..7) { "An SI-Card8 owner write contains between one and seven words." }
        }
    }
}

/** Platform persistence must durably record an attempt before word transmission. */
interface SportIdentOwnerWriteRecoveryStore {
    fun isClear(): Boolean
    fun begin(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture)
    fun reserveWordSequence(request: SportIdentOwnerNameWriteRequest)
    fun completeVerified(
        request: SportIdentOwnerNameWriteRequest,
        comparison: SportIdentSi8OwnerWritePlanComparison
    )
}

/** Terminal result shared by desktop and Android guarded owner writers. */
data class SportIdentOwnerWriteTransactionOutcome<Presence>(
    val stage: SportIdentSi8OwnerWriteStage,
    val stopReason: SportIdentSi8OwnerWriteStopReason?,
    val comparison: SportIdentSi8OwnerWritePlanComparison?,
    val prewritePresence: Presence
) {
    val verified: Boolean
        get() = stage == SportIdentSi8OwnerWriteStage.VERIFIED && comparison?.matches == true
}

/**
 * Coordinates one guarded SI-Card8 owner-name attempt without owning platform I/O.
 * The platform retains its existing connection, persistence, and readback code;
 * this class enforces their safety-critical order and terminal-state checks.
 */
class SportIdentOwnerWriteTransaction<Connection, Presence>(
    private val withFreshRead: (
        request: SportIdentOwnerNameWriteRequest,
        onReady: (
            connection: Connection,
            rehearsal: SportIdentSi8OwnerWriteRehearsal,
            before: SportIdentOwnerReadFixture
        ) -> SportIdentOwnerWriteTransactionOutcome<Presence>
    ) -> SportIdentOwnerWriteTransactionOutcome<Presence>,
    private val recoveryStore: SportIdentOwnerWriteRecoveryStore,
    private val checkPresence: (Connection, ByteArray) -> Presence,
    private val isMatchingPresence: (Presence) -> Boolean,
    private val exchangeWords: (Connection, SportIdentSi8OwnerWriteRehearsal) -> Unit,
    private val verifyReadback: (
        Connection,
        SportIdentSi8OwnerWriteRehearsal
    ) -> SportIdentSi8OwnerWritePlanComparison?,
    private val wordCountPolicy: SportIdentOwnerWriteWordCountPolicy =
        SportIdentOwnerWriteWordCountPolicy.PreviouslyVerifiedDirectShape,
    private val onBeforeWordExchange: (SportIdentOwnerNameWriteRequest) -> Unit = {}
) {
    fun execute(
        request: SportIdentOwnerNameWriteRequest
    ): SportIdentOwnerWriteTransactionOutcome<Presence> {
        check(recoveryStore.isClear()) {
            "Resolve the pending SI-card owner-write attempt before starting another."
        }
        return withFreshRead(request) { connection, rehearsal, before ->
            validateWordCountPolicy(request, before, rehearsal.wordCount)
            val presence = checkPresence(
                connection,
                SportIdentOwnerReadVerification.blockBytes(before, 0)
            )
            if (!isMatchingPresence(presence)) {
                rehearsal.stopForUnconfirmedCard()
            } else {
                // The complete baseline and conservative word bound must be
                // durable before the first non-idempotent frame is exposed.
                recoveryStore.begin(request, before)
                onBeforeWordExchange(request)
                recoveryStore.reserveWordSequence(request)
                exchangeWords(connection, rehearsal)
            }
            val comparison = if (
                rehearsal.stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK
            ) {
                verifyReadback(connection, rehearsal)
            } else {
                null
            }
            check(
                rehearsal.stage == SportIdentSi8OwnerWriteStage.VERIFIED ||
                    rehearsal.stage == SportIdentSi8OwnerWriteStage.STOPPED
            ) { "The SI-Card8 owner-write transaction did not reach a terminal state." }
            if (rehearsal.stage == SportIdentSi8OwnerWriteStage.VERIFIED) {
                recoveryStore.completeVerified(request, requireNotNull(comparison))
            }
            SportIdentOwnerWriteTransactionOutcome(
                rehearsal.stage,
                rehearsal.stopReason,
                comparison,
                presence
            )
        }
    }

    private fun validateWordCountPolicy(
        request: SportIdentOwnerNameWriteRequest,
        before: SportIdentOwnerReadFixture,
        wordCount: Int
    ) {
        val valid = when (val policy = wordCountPolicy) {
            SportIdentOwnerWriteWordCountPolicy.PreviouslyVerifiedDirectShape ->
                SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(request, before)
            SportIdentOwnerWriteWordCountPolicy.AnySupportedShape -> wordCount in 1..7
            is SportIdentOwnerWriteWordCountPolicy.Exact -> wordCount == policy.words
        }
        require(valid) {
            when (val policy = wordCountPolicy) {
                SportIdentOwnerWriteWordCountPolicy.PreviouslyVerifiedDirectShape ->
                    "Direct SI-Card8 writes are limited to the previously verified 11/12-byte transitions."
                SportIdentOwnerWriteWordCountPolicy.AnySupportedShape ->
                    "The SI-Card8 owner write must contain between one and seven owner words."
                is SportIdentOwnerWriteWordCountPolicy.Exact ->
                    "The opt-in trial requires exactly ${policy.words} owner words."
            }
        }
    }
}
