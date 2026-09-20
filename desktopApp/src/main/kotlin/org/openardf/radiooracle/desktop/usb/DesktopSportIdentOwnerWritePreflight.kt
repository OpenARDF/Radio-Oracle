package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

/**
 * Internal same-port preparation for the experimental Kotlin owner writer.
 * Its callback runs only after a fresh card read.
 * A completed read cannot prove that the card remains inserted afterward.
 */
internal class DesktopSportIdentOwnerWritePreflight(
    private val portSelector: DesktopSportIdentPortSelector = DesktopSportIdentPortSelector(),
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connectKeepingPortOpen(it)
    },
    private val readCard: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = {
        DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(it)
    }
) {
    /** Port ownership stays here, so success, rejection, and callback failure all close it. */
    fun <T> withFreshRead(request: SportIdentOwnerNameWriteRequest,
        onReady: (DesktopSerialPort, SportIdentSi8OwnerWriteRehearsal, SportIdentOwnerReadFixture) -> T): T {
        SportIdentOwnerNameRecovery.validate(request)
        val port = portSelector.selectPortForStation(request.stationNumber)
            ?: error("No uniquely identifiable SPORTident USB port for station ${request.stationNumber}.")
        try {
            val station = connectStation(port).stationInfo
            require(port.isOpen && station.serialNumber == request.stationNumber &&
                station.extendedMode && station.isDownloadCapableMode == true &&
                station.stationCodeNumber?.let { it in 0..255 } == true) {
                "The connected station does not match the requested extended-mode download station or has no supported code."
            }
            val download = readCard(port)
            require(port.isOpen && download.inserted.cardType == SportIdentProtocol.SI_CARD8_9_SIAC &&
                download.inserted.siNumber == request.cardNumber &&
                download.readout.siNumber == request.cardNumber && download.readout.series == 2) {
                "The freshly inserted card is not the requested SI-Card8."
            }
            val before = SportIdentOwnerReadVerification.capture(station.serialNumber, download.blocks)
            val read = SportIdentOwnerReadVerification.nativeRead(before)
            require(read.cardNumber == request.cardNumber && read.controlPunchCount == download.readout.punches.size) {
                "The inserted card and its complete block read do not agree."
            }
            val rehearsal = SportIdentSi8OwnerWriteRehearsal(request,
                requireNotNull(station.stationCodeNumber), before)
            try {
                return onReady(port, rehearsal, before)
            } catch (error: Exception) {
                if (rehearsal.stage != SportIdentSi8OwnerWriteStage.VERIFIED) rehearsal.abortTransport()
                throw error
            }
        } finally {
            if (port.isOpen) port.close()
        }
    }
}
