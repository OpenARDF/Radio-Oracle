package org.openardf.radiooracle.desktop.usb

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReferenceRead
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(DesktopSportIdentOwnerVerification.run(args))
}

/** Explicit read-only capture or offline replay; never starts the SDK or writes a card. */
object DesktopSportIdentOwnerVerification {
    fun run(args: Array<String>, out: PrintStream = System.out, err: PrintStream = System.err,
        capture: (Int, Int) -> SportIdentOwnerReadFixture = ::captureNative): Int {
        return try {
            when (args.firstOrNull()) {
                "--help", "help" -> {
                    out.println("capture <expected-station> <expected-card> <new-output-file> | compare <sdk-read-json> <native-read-json>")
                    0
                }
                "capture" -> {
                    require(args.size == 4) { "Usage: capture <expected-station> <expected-card> <new-output-file>" }
                    val station = args[1].toInt().also { require(it > 0) }
                    val card = args[2].toInt().also { require(it > 0) }
                    val output = Path.of(args[3])
                    require(!Files.exists(output)) { "Output already exists; choose a new evidence file." }
                    err.println("Close other SPORTident connections. Reinsert the expected SI-Card8 when the station is ready.")
                    val fixture = capture(station, card)
                    val read = SportIdentOwnerReadVerification.nativeRead(fixture)
                    require(read.stationNumber == station && read.cardNumber == card) { "Capture identity differs from the target." }
                    Files.writeString(output, SportIdentOwnerNameProgramming.json.encodeToString(fixture), StandardOpenOption.CREATE_NEW)
                    out.println("Native read evidence saved to $output")
                    0
                }
                "compare" -> {
                    require(args.size == 3) { "Usage: compare <sdk-read-json> <native-read-json>" }
                    val reference = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReferenceRead>(readEvidence(args[1]))
                    val fixture = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReadFixture>(readEvidence(args[2]))
                    val comparison = SportIdentOwnerReadVerification.compare(reference, fixture)
                    out.println(SportIdentOwnerNameProgramming.json.encodeToString(comparison))
                    if (comparison.matches) 0 else 2
                }
                else -> error("Choose capture or compare; no card programming is supported by this command.")
            }
        } catch (error: Exception) {
            err.println("Owner-read verification failed: ${error.message}")
            1
        }
    }

    private fun readEvidence(path: String): String {
        val file = Path.of(path)
        require(Files.size(file) <= 16_384) { "Read evidence is too large." }
        return Files.readString(file)
    }

    private fun captureNative(expectedStation: Int, expectedCard: Int): SportIdentOwnerReadFixture {
        val download = DesktopSportIdentReadoutService().downloadOne { _, station ->
            require(station.serialNumber == expectedStation) { "Unexpected station; card read refused." }
            System.err.println("Station verified. Remove and insert SI-Card8 $expectedCard; keep it seated until completion.")
        }
        require(download.inserted.siNumber == expectedCard && download.readout.siNumber == expectedCard) {
            "Unexpected inserted or downloaded card; evidence not saved."
        }
        return SportIdentOwnerReadVerification.capture(expectedStation, download.blocks)
    }
}
