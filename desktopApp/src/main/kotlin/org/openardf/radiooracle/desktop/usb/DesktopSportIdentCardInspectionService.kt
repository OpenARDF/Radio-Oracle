package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspector

/** Uses the existing station discovery, mode checks, and card-read transaction. */
class DesktopSportIdentCardInspectionService(
    private val readoutService: DesktopSportIdentReadoutService = DesktopSportIdentReadoutService(
        readCard = { DesktopSportIdentCardBlockReader(includeOwnerData = true)
            .readFirstSupportedCardAfterInsertOnOpenPort(it) }
    )
) {
    fun inspectOne(): SportIdentCardOwnerInspection {
        val download = readoutService.downloadOne()
        check(download.inserted.siNumber == download.readout.siNumber) {
            "The downloaded card number differs from the inserted card. Remove the card and try again."
        }
        return SportIdentCardOwnerInspector.inspect(download.readout, download.blocks)
    }
}
