package org.openardf.radiooracle.desktop.usb

/** Keeps one station connection while the SI Card page is visible. */
class DesktopSportIdentCardInspectionWatcher(
    private val portSelector: DesktopSportIdentPortSelector = DesktopSportIdentPortSelector()
) {
    fun watch(
        onSnapshot: (DesktopSportIdentOwnerSnapshot) -> Unit,
        onCardInserted: () -> Unit,
        onCardRemoved: () -> Unit,
        shouldContinue: () -> Boolean,
        initialCardToRemove: Int? = null
    ) {
        var stationNumber: Int? = null
        var portPath: String? = null
        val service = DesktopSportIdentReadoutService(
            portSelector = portSelector,
            readCard = { port ->
                DesktopSportIdentCardBlockReader(includeOwnerData = true, onCardInserted = onCardInserted)
                    .readFirstSupportedCardAfterInsertOnOpenPort(port, shouldContinue)
            },
            waitAfterSuccessfulCard = { port, download ->
                DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
                    port, download.readout.siNumber, Long.MAX_VALUE, shouldContinue
                )
                if (shouldContinue()) onCardRemoved()
            }
        )
        service.downloadUntilTimeout(
            maxCards = Int.MAX_VALUE,
            onDownload = { download ->
                check(download.inserted.siNumber == download.readout.siNumber) {
                    "The downloaded card number differs from the inserted card. Remove the card and try again."
                }
                onSnapshot(DesktopSportIdentOwnerSnapshot.fromDownload(download,
                    checkNotNull(portPath), checkNotNull(stationNumber)))
            },
            shouldContinue = shouldContinue,
            continueAfterTimeout = true,
            onStationConnected = { port, station ->
                portPath = port.systemPortPath
                stationNumber = station.serialNumber
            },
            beforeFirstRead = { port ->
                if (initialCardToRemove != null) {
                    // A card may already have been removed after read-back; a bounded
                    // guard avoids consuming its next insertion as a removal wait.
                    DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
                        port, initialCardToRemove, System.currentTimeMillis() + 2_500L, shouldContinue
                    )
                    if (shouldContinue()) onCardRemoved()
                }
            }
        )
    }
}
