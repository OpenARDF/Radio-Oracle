package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner

/** Terminal outcome of one native word exchange; a stopped attempt must never be retried blindly. */
internal data class DesktopSportIdentOwnerWriteOutcome(
    val stage: SportIdentSi8OwnerWriteStage,
    val stopReason: SportIdentSi8OwnerWriteStopReason?,
    val comparison: SportIdentSi8OwnerWritePlanComparison?,
    val prewritePresence: DesktopSportIdentCardPresenceResult
) {
    val verified: Boolean get() = stage == SportIdentSi8OwnerWriteStage.VERIFIED && comparison?.matches == true
}

/**
 * Same-port preflight, one-shot word transport, and independent read-back.
 * Used by the experimental CLI and the guarded desktop owner-name writer.
 */
internal class DesktopSportIdentOwnerWriteTransaction(
    private val preflight: DesktopSportIdentOwnerWritePreflight,
    private val readbackVerifier: DesktopSportIdentOwnerReadbackVerifier,
    private val recoveryStore: DesktopSportIdentOwnerRecoveryStore,
    private val presenceProbe: DesktopSportIdentCardPresenceProbe = DesktopSportIdentCardPresenceProbe(),
    private val stopAfterAcknowledgedWord: Int? = null,
    private val experimentalWordCount: Int? = null,
    private val allowVariableLength: Boolean = false,
    private val onBeforeWordExchange: (SportIdentOwnerNameWriteRequest) -> Unit = {},
    private val makeWordTransport: (DesktopSerialPort) -> DesktopSportIdentOwnerWordTransport =
        { port -> DesktopSportIdentOwnerWordTransport(port) }
) {
    init {
        require(experimentalWordCount == null || experimentalWordCount in 1..7)
        require(!allowVariableLength || experimentalWordCount == null)
    }

    fun execute(request: SportIdentOwnerNameWriteRequest): DesktopSportIdentOwnerWriteOutcome {
        check(recoveryStore.load() == DesktopSportIdentOwnerRecoveryState.Empty) {
            "Resolve the pending SI-card owner-write attempt before starting another."
        }
        return preflight.withFreshRead(request) { port, rehearsal, before ->
            require(when {
                experimentalWordCount != null -> rehearsal.wordCount == experimentalWordCount
                allowVariableLength -> rehearsal.wordCount in 1..7
                else -> SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(request, before)
            }) {
                if (experimentalWordCount != null)
                    "The opt-in trial requires exactly $experimentalWordCount owner words."
                else if (allowVariableLength)
                    "The SI-Card8 owner write must contain between one and seven owner words."
                else "Direct SI-Card8 writes are limited to the previously verified 11/12-byte transitions."
            }
            val presence = presenceProbe.check(port, SportIdentOwnerReadVerification.blockBytes(before, 0))
            if (presence != DesktopSportIdentCardPresenceResult.MATCHING_BLOCK) {
                rehearsal.stopForUnconfirmedCard()
            } else {
                // Persistence must succeed before any owner-word frame can be sent.
                recoveryStore.beginNative(request, before)
                onBeforeWordExchange(request)
                // One durable upper bound avoids a disk sync between replies
                // and the next word. Fresh readback still identifies the actual prefix.
                recoveryStore.reserveNativeWordSequence(request)
                makeWordTransport(port).exchange(rehearsal,
                    stopAfterAcknowledgedWord = stopAfterAcknowledgedWord)
            }
            val comparison = if (rehearsal.stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) {
                readbackVerifier.verify(port, rehearsal)
            } else null
            check(rehearsal.stage == SportIdentSi8OwnerWriteStage.VERIFIED ||
                rehearsal.stage == SportIdentSi8OwnerWriteStage.STOPPED) {
                "The SI-Card8 owner-write transaction did not reach a terminal state."
            }
            if (rehearsal.stage == SportIdentSi8OwnerWriteStage.VERIFIED) {
                recoveryStore.completeVerifiedNative(request, requireNotNull(comparison))
            }
            DesktopSportIdentOwnerWriteOutcome(rehearsal.stage, rehearsal.stopReason, comparison, presence)
        }
    }
}
