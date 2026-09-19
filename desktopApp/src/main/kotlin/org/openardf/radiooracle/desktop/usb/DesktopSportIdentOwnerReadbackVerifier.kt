package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteRehearsal
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

/** Internal read-back boundary; no application or CLI path invokes it yet. */
internal class DesktopSportIdentOwnerReadbackVerifier(
    private val awaitTargetRemoval: (DesktopSerialPort, Int) -> Boolean = { port, card ->
        DesktopSportIdentCardEventMonitor().waitForRemoveEventOnOpenPort(
            port, card, System.currentTimeMillis() + DesktopSportIdentCardEventMonitor.defaultMaxWaitMs
        ) != null
    },
    private val readAfterReinsertion: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = { port ->
        DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(port)
    }
) {
    /** A fresh removal and insertion must precede comparison; null means no valid read-back. */
    fun verify(port: DesktopSerialPort,
        rehearsal: SportIdentSi8OwnerWriteRehearsal): SportIdentSi8OwnerWritePlanComparison? {
        check(port.isOpen && rehearsal.stage == SportIdentSi8OwnerWriteStage.REQUIRES_READBACK) {
            "The SI-Card8 word exchange must finish on an open port before read-back."
        }
        val removed = try {
            awaitTargetRemoval(port, rehearsal.targetCardNumber)
        } catch (error: Exception) {
            rehearsal.stopForMissingReadback()
            throw error
        }
        if (!removed) {
            rehearsal.stopForMissingReadback()
            return null
        }
        val download = try {
            readAfterReinsertion(port)
        } catch (error: Exception) {
            rehearsal.stopForMissingReadback()
            throw error
        }
        if (!port.isOpen || download.inserted.cardType != SportIdentProtocol.SI_CARD8_9_SIAC ||
            download.inserted.siNumber != rehearsal.targetCardNumber ||
            download.readout.siNumber != rehearsal.targetCardNumber || download.readout.series != 2) {
            rehearsal.rejectReadback()
            return null
        }
        val after = try {
            SportIdentOwnerReadVerification.capture(rehearsal.targetStationNumber, download.blocks)
        } catch (error: Exception) {
            rehearsal.rejectReadback()
            throw error
        }
        val native = SportIdentOwnerReadVerification.nativeRead(after)
        if (native.cardNumber != rehearsal.targetCardNumber || native.controlPunchCount != download.readout.punches.size) {
            rehearsal.rejectReadback()
            return null
        }
        return rehearsal.compareIndependentRead(after)
    }
}
