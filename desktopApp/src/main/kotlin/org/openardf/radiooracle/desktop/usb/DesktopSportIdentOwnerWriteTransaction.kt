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
 * Used by the experimental CLI; the desktop owner-name page is read-only.
 */
internal class DesktopSportIdentOwnerWriteTransaction(
    private val preflight: DesktopSportIdentOwnerWritePreflight,
    private val readbackVerifier: DesktopSportIdentOwnerReadbackVerifier,
    private val recoveryStore: DesktopSportIdentOwnerRecoveryStore,
    private val presenceProbe: DesktopSportIdentCardPresenceProbe = DesktopSportIdentCardPresenceProbe(),
    private val stopAfterAcknowledgedWord: Int? = null,
    private val allowSevenWordExperiment: Boolean = false,
    private val onBeforeWordExchange: (SportIdentOwnerNameWriteRequest) -> Unit = {},
    private val makeWordTransport: (DesktopSerialPort) -> DesktopSportIdentOwnerWordTransport =
        { port -> DesktopSportIdentOwnerWordTransport(port) }
) {
    fun execute(request: SportIdentOwnerNameWriteRequest): DesktopSportIdentOwnerWriteOutcome {
        check(recoveryStore.load() == DesktopSportIdentOwnerRecoveryState.Empty) {
            "Resolve the pending SI-card owner-write attempt before starting another."
        }
        return preflight.withFreshRead(request) { port, rehearsal, before ->
            require(if (allowSevenWordExperiment) rehearsal.wordCount == 7 else
                SportIdentSi8OwnerWordWritePlanner.hasPreviouslyVerifiedDirectShape(request, before)) {
                if (allowSevenWordExperiment) "The opt-in trial requires exactly seven owner words."
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
