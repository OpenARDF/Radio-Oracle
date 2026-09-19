package org.openardf.radiooracle.desktop.usb

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.serialization.decodeFromString
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(DesktopSportIdentNativeOwnerRecovery.run(args))
}

/** Explicit offline equivalent of accepting a fresh card read in the app UI. */
internal object DesktopSportIdentNativeOwnerRecovery {
    fun run(args: Array<String>,
        out: PrintStream = System.out,
        err: PrintStream = System.err,
        store: DesktopSportIdentOwnerRecoveryStore = DesktopSportIdentOwnerRecoveryStore()
    ): Int {
        if (args.size != 4 || args[0] != "--acknowledge-native-read") {
            err.println("Usage: --acknowledge-native-read <fresh-native-read-json> <observed-first-name> <observed-last-name>")
            return 1
        }
        return try {
            val pending = store.load() as? DesktopSportIdentOwnerRecoveryState.Pending
                ?: error("No readable pending owner-write recovery record exists.")
            val file = Path.of(args[1])
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 16_384) {
                "Native read evidence must be a regular file of at most 16384 bytes."
            }
            val fixture = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReadFixture>(
                Files.readString(file))
            val read = SportIdentOwnerReadVerification.nativeRead(fixture)
            require(read.stationNumber == pending.request.stationNumber && read.cardNumber == pending.request.cardNumber &&
                read.firstName == args[2] && read.lastName == args[3]) {
                "The complete native read differs from the pending card, station, or observed names."
            }
            val inspection = SportIdentCardOwnerInspection(read.cardNumber, SportIdentCardFamily.SI8,
                SportIdentCardHolder(read.firstName, read.lastName, null), SportIdentOwnerDataStatus.READ)
            store.acknowledge(pending.request, inspection)
            check(store.load() == DesktopSportIdentOwnerRecoveryState.Empty)
            out.println("Accepted native read of SI-Card8 ${read.cardNumber}: '${read.firstName} ${read.lastName}', " +
                "${read.controlPunchCount} punches. Recovery reminder cleared; preservation is assessed separately.")
            0
        } catch (error: Exception) {
            err.println("Recovery acknowledgement failed: ${error.message}. The pending record was not accepted.")
            1
        }
    }
}
