package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspector
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification

data class DesktopSportIdentOwnerSnapshot(
    val inspection: SportIdentCardOwnerInspection,
    val portPath: String,
    val stationNumber: Int,
    val rawRead: SportIdentOwnerReadFixture? = null
) {
    companion object {
        fun fromDownload(download: DesktopSportIdentCardBlockDownload,
            portPath: String, stationNumber: Int): DesktopSportIdentOwnerSnapshot {
            val inspection = SportIdentCardOwnerInspector.inspect(download.readout, download.blocks)
            val rawRead = if (inspection.family == SportIdentCardFamily.SI8) {
                runCatching { SportIdentOwnerReadVerification.captureRaw(stationNumber, download.blocks) }.getOrNull()
            } else null
            return DesktopSportIdentOwnerSnapshot(inspection, portPath, stationNumber, rawRead)
        }
    }
}

/** Uses the existing station discovery, mode checks, and card-read transaction. */
class DesktopSportIdentCardInspectionService(
    private val readoutService: DesktopSportIdentReadoutService = DesktopSportIdentReadoutService(
        readCard = { DesktopSportIdentCardBlockReader(includeOwnerData = true)
            .readFirstSupportedCardAfterInsertOnOpenPort(it) }
    )
) {
    fun inspectOne(): SportIdentCardOwnerInspection = inspectOneBound().inspection

    fun inspectOneBound(): DesktopSportIdentOwnerSnapshot {
        var stationNumber: Int? = null
        var portPath: String? = null
        val download = readoutService.downloadOne { port, station ->
            stationNumber = station.serialNumber
            portPath = port.systemPortPath
        }
        check(download.inserted.siNumber == download.readout.siNumber) {
            "The downloaded card number differs from the inserted card. Remove the card and try again."
        }
        return DesktopSportIdentOwnerSnapshot.fromDownload(download, checkNotNull(portPath), checkNotNull(stationNumber))
    }
}
