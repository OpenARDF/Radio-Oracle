package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason

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
 * Internal composition of the same-port preflight, one-shot word transport, and
 * independent read-back. No application or CLI path invokes this transaction.
 * Live use still needs card-presence and interruption/recovery validation.
 */
internal class DesktopSportIdentOwnerWriteTransaction(
    private val preflight: DesktopSportIdentOwnerWritePreflight,
    private val readbackVerifier: DesktopSportIdentOwnerReadbackVerifier,
    private val recoveryStore: DesktopSportIdentOwnerRecoveryStore,
    private val presenceProbe: DesktopSportIdentCardPresenceProbe = DesktopSportIdentCardPresenceProbe(),
    private val makeWordTransport: (DesktopSerialPort) -> DesktopSportIdentOwnerWordTransport =
        { port -> DesktopSportIdentOwnerWordTransport(port) }
) {
    fun execute(request: SportIdentOwnerNameWriteRequest): DesktopSportIdentOwnerWriteOutcome {
        check(recoveryStore.load() == DesktopSportIdentOwnerRecoveryState.Empty) {
            "Resolve the pending SI-card owner-write attempt before starting another."
        }
        return preflight.withFreshRead(request) { port, rehearsal, before ->
            val presence = presenceProbe.check(port, blockZero(before))
            if (presence != DesktopSportIdentCardPresenceResult.MATCHING_BLOCK) {
                rehearsal.stopForUnconfirmedCard()
            } else {
                // Persistence must succeed before any owner-word frame can be sent.
                recoveryStore.begin(request)
                makeWordTransport(port).exchange(rehearsal)
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

    private fun blockZero(before: SportIdentOwnerReadFixture): ByteArray {
        val hex = before.blocks.single { it.blockNumber == 0 }.hexData
        return ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
