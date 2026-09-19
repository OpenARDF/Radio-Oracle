package org.openardf.radiooracle.desktop.usb

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNativeReadDiff
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReferenceRead
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DesktopSportIdentOwnerVerificationTest {
    @Test fun offlineMatchAndMismatchNeverCallCaptureAndReturnDistinctExitCodes() {
        val directory = Files.createTempDirectory("si-owner-compare-")
        try {
            val sdk = directory.resolve("sdk read.json")
            val native = directory.resolve("native read.json")
            Files.writeString(native, SportIdentOwnerNameProgramming.json.encodeToString(fixture()))
            val reference = SportIdentOwnerReferenceRead(1, 593927, 2450662, "SI-Card8", "Daisy", "Duck", 0)
            Files.writeString(sdk, SportIdentOwnerNameProgramming.json.encodeToString(reference))
            val out = ByteArrayOutputStream()
            val refuseCapture: (Int, Int) -> Nothing = { _, _ -> error("Offline comparison opened a station") }
            assertEquals(0, DesktopSportIdentOwnerVerification.run(arrayOf("compare", sdk.toString(), native.toString()),
                PrintStream(out), capture = refuseCapture))
            assertTrue(out.toString().contains("\"matches\":true"))
            Files.writeString(sdk, SportIdentOwnerNameProgramming.json.encodeToString(reference.copy(firstName = "Donald")))
            assertEquals(2, DesktopSportIdentOwnerVerification.run(arrayOf("compare", sdk.toString(), native.toString()),
                PrintStream(out), capture = refuseCapture))
            assertTrue(out.toString().contains("FIRST_NAME"))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun captureRequiresPinnedIdentityAndNeverOverwritesEvidence() {
        val directory = Files.createTempDirectory("si-owner-capture-")
        try {
            val output = directory.resolve("native.json")
            val out = PrintStream(ByteArrayOutputStream())
            var captureCount = 0
            val capture = { station: Int, card: Int ->
                assertEquals(593927, station); assertEquals(2450662, card)
                captureCount++; fixture()
            }
            val args = arrayOf("capture", "593927", "2450662", output.toString())
            assertEquals(0, DesktopSportIdentOwnerVerification.run(args, out, out, capture))
            val evidence = Files.readString(output)
            assertEquals(1, DesktopSportIdentOwnerVerification.run(args, out, out, capture))
            assertEquals(1, captureCount)
            assertEquals(evidence, Files.readString(output))
            Files.delete(output)
            assertEquals(1, DesktopSportIdentOwnerVerification.run(args, out, out) { _, _ -> fixture().copy(stationNumber = 593928) })
            assertFalse(Files.exists(output))
            assertEquals(1, DesktopSportIdentOwnerVerification.run(args, out, out) { _, _ ->
                SportIdentOwnerReadVerification.capture(593927, blocks(card = 2450663))
            })
            assertFalse(Files.exists(output))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun nativeDiffUsesSavedEvidenceWithoutOpeningAStation() {
        val directory = Files.createTempDirectory("si-owner-native-diff-")
        try {
            val before = directory.resolve("before read.json")
            val after = directory.resolve("after read.json")
            Files.writeString(before, SportIdentOwnerNameProgramming.json.encodeToString(fixture()))
            val changedBlocks = blocks()
            changedBlocks[1].data[8] = 99
            Files.writeString(after, SportIdentOwnerNameProgramming.json.encodeToString(
                SportIdentOwnerReadVerification.capture(593927, changedBlocks)))
            val out = ByteArrayOutputStream()
            val refuseCapture: (Int, Int) -> Nothing = { _, _ -> error("Offline diff opened a station") }
            assertEquals(0, DesktopSportIdentOwnerVerification.run(arrayOf("diff-native", before.toString(), after.toString()),
                PrintStream(out), capture = refuseCapture))
            val diff = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNativeReadDiff>(out.toString())
            assertTrue(diff.sameCard && diff.samePunchCount)
            assertEquals(1, diff.changesOutsideOwnerRegion.size)
            assertEquals(8, diff.changesOutsideOwnerRegion.single().offset)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun planComparisonUsesSavedEvidenceWithoutOpeningAStation() {
        val directory = Files.createTempDirectory("si-owner-plan-compare-")
        try {
            val requestFile = directory.resolve("write request.json")
            val beforeFile = directory.resolve("before read.json")
            val afterFile = directory.resolve("after read.json")
            val request = SportIdentOwnerNameWriteRequest(1, 593927, 2450662, "Donald", "Duck", "Daisy", "Duck", true)
            Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
            Files.writeString(beforeFile, SportIdentOwnerNameProgramming.json.encodeToString(fixture("Donald;Duck;se;\u00ee")))
            Files.writeString(afterFile, SportIdentOwnerNameProgramming.json.encodeToString(fixture("Daisy;Duck;\u00eese;\u00ee")))
            val args = arrayOf("compare-plan", requestFile.toString(), beforeFile.toString(), afterFile.toString())
            val refuseCapture: (Int, Int) -> Nothing = { _, _ -> error("Offline plan comparison opened a station") }
            val out = ByteArrayOutputStream()
            assertEquals(0, DesktopSportIdentOwnerVerification.run(args, PrintStream(out), capture = refuseCapture))
            val match = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentSi8OwnerWritePlanComparison>(out.toString())
            assertTrue(match.matches)
            assertTrue(match.predictedVersusObserved.byteChanges.isEmpty())
            val altered = blocks(owner = "Daisy;Duck;\u00eese;\u00ee").also { it[1].data[8] = 99 }
            Files.writeString(afterFile, SportIdentOwnerNameProgramming.json.encodeToString(
                SportIdentOwnerReadVerification.capture(593927, altered)))
            assertEquals(2, DesktopSportIdentOwnerVerification.run(args, PrintStream(ByteArrayOutputStream()), capture = refuseCapture))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun malformedArgumentsAndIncompleteOrOversizedEvidenceFailWithoutHardwareAccess() {
        val directory = Files.createTempDirectory("si-owner-invalid-")
        try {
            val invalid = directory.resolve("invalid.json")
            val out = PrintStream(ByteArrayOutputStream())
            val refuseCapture: (Int, Int) -> Nothing = { _, _ -> error("Invalid arguments opened a station") }
            assertEquals(0, DesktopSportIdentOwnerVerification.run(arrayOf("--help"), out, out, refuseCapture))
            for (args in listOf(emptyArray(), arrayOf("write"), arrayOf("capture", "0", "2450662", invalid.toString()))) {
                assertEquals(1, DesktopSportIdentOwnerVerification.run(args, out, out, refuseCapture))
            }
            Files.writeString(invalid, "{}")
            assertEquals(1, DesktopSportIdentOwnerVerification.run(arrayOf("compare", invalid.toString(), invalid.toString()), out, out, refuseCapture))
            Files.writeString(invalid, "x".repeat(16_385))
            assertEquals(1, DesktopSportIdentOwnerVerification.run(arrayOf("compare", invalid.toString(), invalid.toString()), out, out, refuseCapture))
        } finally { directory.toFile().deleteRecursively() }
    }

    private fun fixture(owner: String = "Daisy;Duck;") = SportIdentOwnerReadVerification.capture(593927, blocks(owner = owner))

    private fun blocks(card: Int = 2450662, owner: String = "Daisy;Duck;"): List<SportIdentCardBlock> {
        val data = ByteArray(256)
        data[24] = 2
        data[25] = (card shr 16).toByte(); data[26] = (card shr 8).toByte(); data[27] = card.toByte()
        owner.forEachIndexed { i, c -> data[32 + i] = c.code.toByte() }
        return listOf(SportIdentCardBlock(0, data.copyOfRange(0, 128)), SportIdentCardBlock(1, data.copyOfRange(128, 256)))
    }
}
