package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.desktop.DesktopAppSettingsPreferences
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWritePhase

/** The UI uses the same one-shot transaction and durable recovery record as the native CLI. */
internal class DesktopSportIdentNativeProgrammingClient(
    private val recoveryStore: DesktopSportIdentOwnerRecoveryStore
) {
    fun write(request: SportIdentOwnerNameWriteRequest,
        onPhase: (SportIdentOwnerWritePhase) -> Unit): DesktopSportIdentOwnerWriteOutcome {
        val preflight = DesktopSportIdentOwnerWritePreflight(
            portSelector = DesktopSportIdentPortSelector(discoverySettings = DesktopAppSettingsPreferences),
            readCard = { port ->
                onPhase(SportIdentOwnerWritePhase.WAITING_FOR_CARD)
                DesktopSportIdentCardBlockReader(includeOwnerData = true)
                    .readFirstSupportedCardAfterInsertOnOpenPort(port)
            }
        )
        val readback = DesktopSportIdentOwnerReadbackVerifier(
            awaitTargetRemoval = { port, card ->
                onPhase(SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK)
                DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
                    port, card, System.currentTimeMillis() + DesktopSportIdentCardEventMonitor.defaultMaxWaitMs
                ) != null
            },
            readAfterReinsertion = { port ->
                DesktopSportIdentCardBlockReader(includeOwnerData = true)
                    .readFirstSupportedCardAfterInsertOnOpenPort(port)
            }
        )
        return DesktopSportIdentOwnerWriteTransaction(
            preflight = preflight,
            readbackVerifier = readback,
            recoveryStore = recoveryStore,
            onBeforeWordExchange = { onPhase(SportIdentOwnerWritePhase.WRITING) }
        ).execute(request)
    }
}
