package org.openardf.radiooracle.desktop.usb

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage

internal data class DesktopSportIdentOwnerReadinessReport(
    val stationNumber: Int,
    val cardNumber: Int,
    val storedFirstName: String,
    val storedLastName: String,
    val controlPunchCount: Int,
    val blockZeroRecheck: DesktopSportIdentCardPresenceResult
) {
    val ready: Boolean get() = blockZeroRecheck == DesktopSportIdentCardPresenceResult.MATCHING_BLOCK
}

/** Read-only rehearsal up to, but never including, persistence or an owner-word command. */
internal class DesktopSportIdentOwnerReadiness(
    private val preflight: DesktopSportIdentOwnerWritePreflight,
    private val recoveryStore: DesktopSportIdentOwnerRecoveryStore,
    private val presenceProbe: DesktopSportIdentCardPresenceProbe = DesktopSportIdentCardPresenceProbe()
) {
    fun inspect(request: SportIdentOwnerNameWriteRequest): DesktopSportIdentOwnerReadinessReport {
        check(recoveryStore.load() == DesktopSportIdentOwnerRecoveryState.Empty) {
            "Resolve the pending SI-card owner-write attempt before checking a new one."
        }
        return preflight.withFreshRead(request) { port, rehearsal, before ->
            check(rehearsal.stage == SportIdentSi8OwnerWriteStage.READY_FOR_WORD)
            val read = SportIdentOwnerReadVerification.nativeRead(before)
            val presence = presenceProbe.check(port, SportIdentOwnerReadVerification.blockBytes(before, 0))
            DesktopSportIdentOwnerReadinessReport(read.stationNumber, read.cardNumber,
                read.firstName, read.lastName, read.controlPunchCount, presence)
        }
    }
}

/** An exact-request hardware check that can never send a WRITE_SI_CARD_WORD frame. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: <owner-write-request-json>" }
    val file = Path.of(args[0])
    require(Files.size(file) <= 4096) { "Owner-write request is too large." }
    val request = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteRequest>(
        Files.readString(file))
    val preflight = DesktopSportIdentOwnerWritePreflight(readCard = { port ->
        println("Station ${request.stationNumber} ready. Insert SI-Card8 ${request.cardNumber} and keep it seated until this check ends.")
        DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(port)
    })
    val report = DesktopSportIdentOwnerReadiness(preflight, DesktopSportIdentOwnerRecoveryStore()).inspect(request)
    println("Read-only preflight: station=${report.stationNumber} card=${report.cardNumber} " +
        "stored='${report.storedFirstName} ${report.storedLastName}' " +
        "requested='${request.firstName} ${request.lastName}' " +
        "controlPunches=${report.controlPunchCount} block0=${report.blockZeroRecheck}")
    check(report.ready) { "The target card was not confirmed immediately before a possible write." }
    println("Read-only preflight passed. No recovery record or owner word was written. Remove the card now.")
}
