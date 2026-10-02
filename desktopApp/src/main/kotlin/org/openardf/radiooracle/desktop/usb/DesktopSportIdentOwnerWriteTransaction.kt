package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteTransaction
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteTransactionOutcome
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteWordCountPolicy
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison

/** Keep the existing desktop API while sharing the safety-critical ordering. */
internal typealias DesktopSportIdentOwnerWriteOutcome =
    SportIdentOwnerWriteTransactionOutcome<DesktopSportIdentCardPresenceResult>

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

    fun execute(request: SportIdentOwnerNameWriteRequest): DesktopSportIdentOwnerWriteOutcome =
        SportIdentOwnerWriteTransaction(
            withFreshRead = preflight::withFreshRead,
            recoveryStore = object : SportIdentOwnerWriteRecoveryStore {
                override fun isClear(): Boolean =
                    recoveryStore.load() == DesktopSportIdentOwnerRecoveryState.Empty

                override fun begin(
                    request: SportIdentOwnerNameWriteRequest,
                    before: SportIdentOwnerReadFixture
                ) = recoveryStore.beginNative(request, before)

                override fun reserveWordSequence(request: SportIdentOwnerNameWriteRequest) =
                    recoveryStore.reserveNativeWordSequence(request)

                override fun completeVerified(
                    request: SportIdentOwnerNameWriteRequest,
                    comparison: SportIdentSi8OwnerWritePlanComparison
                ) = recoveryStore.completeVerifiedNative(request, comparison)
            },
            checkPresence = presenceProbe::check,
            isMatchingPresence = {
                it == DesktopSportIdentCardPresenceResult.MATCHING_BLOCK
            },
            exchangeWords = { port, rehearsal ->
                makeWordTransport(port).exchange(
                    rehearsal,
                    stopAfterAcknowledgedWord = stopAfterAcknowledgedWord
                )
            },
            verifyReadback = readbackVerifier::verify,
            wordCountPolicy = when {
                experimentalWordCount != null ->
                    SportIdentOwnerWriteWordCountPolicy.Exact(experimentalWordCount)
                allowVariableLength -> SportIdentOwnerWriteWordCountPolicy.AnySupportedShape
                else -> SportIdentOwnerWriteWordCountPolicy.PreviouslyVerifiedDirectShape
            },
            onBeforeWordExchange = onBeforeWordExchange
        ).execute(request)
}
