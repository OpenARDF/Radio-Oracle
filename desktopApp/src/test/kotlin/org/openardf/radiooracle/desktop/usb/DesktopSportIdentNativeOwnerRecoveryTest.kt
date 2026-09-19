package org.openardf.radiooracle.desktop.usb

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentCardBlock
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification

class DesktopSportIdentNativeOwnerRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = SportIdentOwnerNameWriteRequest(1, 554900, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)

    @Test
    fun rejectsWrongObservedNameAndThenAcceptsTheCompleteTargetRead() {
        val store = DesktopSportIdentOwnerRecoveryStore(temporary.root.toPath().resolve("recovery.json"))
        store.begin(request)
        val fixture = SportIdentOwnerReadVerification.capture(request.stationNumber,
            listOf(SportIdentCardBlock(0, block0("Donald;Duck;")),
                SportIdentCardBlock(1, ByteArray(128))))
        val evidence = temporary.newFile("native.json").toPath()
        Files.writeString(evidence, SportIdentOwnerNameProgramming.json.encodeToString(fixture))
        val output = ByteArrayOutputStream()

        assertEquals(1, DesktopSportIdentNativeOwnerRecovery.run(
            arrayOf("--acknowledge-native-read", evidence.toString(), "Donay", "Duck"),
            PrintStream(output), PrintStream(ByteArrayOutputStream()), store))
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())

        assertEquals(0, DesktopSportIdentNativeOwnerRecovery.run(
            arrayOf("--acknowledge-native-read", evidence.toString(), "Donald", "Duck"),
            PrintStream(output), PrintStream(ByteArrayOutputStream()), store))
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store.load())
        assertTrue(output.toString().contains("Recovery reminder cleared"))
    }

    private fun block0(owner: String) = ByteArray(128).also { block ->
        block[22] = 1
        block[24] = 2
        block[25] = 0x25
        block[26] = 0x64
        block[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block[32 + index] = char.code.toByte() }
    }
}
