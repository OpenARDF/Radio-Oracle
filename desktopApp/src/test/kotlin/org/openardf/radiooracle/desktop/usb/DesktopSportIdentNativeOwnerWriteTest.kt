package org.openardf.radiooracle.desktop.usb

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryState
import org.openardf.radiooracle.desktop.DesktopSportIdentOwnerRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStage
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWriteStopReason

class DesktopSportIdentNativeOwnerWriteTest {
    @get:Rule val temporary = TemporaryFolder()
    private val request = SportIdentOwnerNameWriteRequest(1, 554900, 2450662,
        "Daisy", "Duck", "Donald", "Duck", true)

    @Test
    fun explicitFlagAndValidRequestAreRequiredBeforeExecuting() {
        val requestFile = temporary.newFile("request.json").toPath()
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
        val badFile = temporary.newFile("bad.json").toPath()
        Files.writeString(badFile, "{}")
        var calls = 0
        val execute: (SportIdentOwnerNameWriteRequest) -> DesktopSportIdentOwnerWriteOutcome = {
            calls++
            error("Unexpected execution")
        }

        assertEquals(1, run(arrayOf(requestFile.toString()), execute))
        assertEquals(1, run(arrayOf("--execute-native-write", badFile.toString()), execute))
        assertEquals(1, run(arrayOf("--execute-native-write", temporary.root.toString()), execute))
        assertEquals(1, run(arrayOf("--execute-native-write", requestFile.toString(), "--unknown"), execute))
        assertEquals(0, calls)
    }

    @Test
    fun sevenWordTrialFlagMustBeExplicitAndCannotCombineWithStopFlag() {
        val requestFile = temporary.newFile("seven-word-request.json").toPath()
        val sevenWordRequest = request.copy(firstName = "Penny", lastName = "Popandrolopoulos-J")
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(sevenWordRequest))
        var calls = 0
        val execute: (SportIdentOwnerNameWriteRequest) -> DesktopSportIdentOwnerWriteOutcome = { supplied ->
            assertEquals(sevenWordRequest, supplied)
            calls++
            DesktopSportIdentOwnerWriteOutcome(SportIdentSi8OwnerWriteStage.STOPPED,
                SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP, null,
                DesktopSportIdentCardPresenceResult.MATCHING_BLOCK)
        }
        assertEquals(1, run(arrayOf("--execute-native-write", requestFile.toString(),
            "--allow-seven-word-trial", "--stop-after-first-reply"), execute))
        assertEquals(0, calls)
        assertEquals(2, run(arrayOf("--execute-native-write", requestFile.toString(),
            "--allow-seven-word-trial"), execute))
        assertEquals(2, run(arrayOf("--execute-native-write", requestFile.toString(),
            "--allow-seven-word-paced-trial"), execute))
        assertEquals(2, run(arrayOf("--execute-native-write", requestFile.toString(),
            "--allow-six-word-paced-trial"), execute))
        assertEquals(2, run(arrayOf("--execute-native-write", requestFile.toString(),
            "--allow-three-word-paced-trial"), execute))
        assertEquals(4, calls)
    }

    @Test
    fun stoppedAttemptReportsFailureAndRetainsRecoveryIntent() {
        val requestFile = temporary.newFile("request.json").toPath()
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
        val store = recoveryStore()
        val stderr = ByteArrayOutputStream()
        val status = DesktopSportIdentNativeOwnerWrite.run(
            arrayOf("--execute-native-write", requestFile.toString()),
            PrintStream(ByteArrayOutputStream()), PrintStream(stderr), store
        ) { supplied ->
            assertEquals(request, supplied)
            store.begin(supplied)
            DesktopSportIdentOwnerWriteOutcome(SportIdentSi8OwnerWriteStage.STOPPED,
                SportIdentSi8OwnerWriteStopReason.NO_REPLY, null,
                DesktopSportIdentCardPresenceResult.MATCHING_BLOCK)
        }

        assertEquals(2, status)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
        assertTrue(stderr.toString().contains("Do not retry"))
        assertTrue(stderr.toString().contains("recovery=pending card=2450662 attemptedWords=unknown"))
        assertFalse(stderr.toString().contains("recovery=Empty"))
    }

    @Test
    fun secondReplyStopRequiresItsExplicitFlagAndStillReportsUnverified() {
        val requestFile = temporary.newFile("second-stop-request.json").toPath()
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
        val store = recoveryStore()
        val output = ByteArrayOutputStream()
        val status = DesktopSportIdentNativeOwnerWrite.run(
            arrayOf("--execute-native-write", requestFile.toString(), "--stop-after-second-reply"),
            PrintStream(output), PrintStream(ByteArrayOutputStream()), store
        ) { supplied ->
            assertEquals(request, supplied)
            store.begin(supplied)
            DesktopSportIdentOwnerWriteOutcome(SportIdentSi8OwnerWriteStage.STOPPED,
                SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP, null,
                DesktopSportIdentCardPresenceResult.MATCHING_BLOCK)
        }
        assertEquals(2, status)
        assertTrue(output.toString().contains("after acknowledged word 2"))
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
    }

    @Test
    fun thirdReplyStopRequiresItsExplicitFlagAndStillReportsUnverified() {
        val requestFile = temporary.newFile("third-stop-request.json").toPath()
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
        val store = recoveryStore()
        val output = ByteArrayOutputStream()
        val status = DesktopSportIdentNativeOwnerWrite.run(
            arrayOf("--execute-native-write", requestFile.toString(), "--stop-after-third-reply"),
            PrintStream(output), PrintStream(ByteArrayOutputStream()), store
        ) { supplied ->
            assertEquals(request, supplied)
            store.begin(supplied)
            DesktopSportIdentOwnerWriteOutcome(SportIdentSi8OwnerWriteStage.STOPPED,
                SportIdentSi8OwnerWriteStopReason.INTENTIONAL_STOP, null,
                DesktopSportIdentCardPresenceResult.MATCHING_BLOCK)
        }
        assertEquals(2, status)
        assertTrue(output.toString().contains("after acknowledged word 3"))
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
    }

    @Test
    fun exceptionAfterIntentIsPersistedAlsoRetainsRecoveryIntent() {
        val requestFile = temporary.newFile("request.json").toPath()
        Files.writeString(requestFile, SportIdentOwnerNameProgramming.json.encodeToString(request))
        val store = recoveryStore()
        val status = DesktopSportIdentNativeOwnerWrite.run(
            arrayOf("--execute-native-write", requestFile.toString()),
            PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), store
        ) { supplied ->
            store.begin(supplied)
            error("Simulated serial failure")
        }

        assertEquals(1, status)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
    }

    private fun run(args: Array<String>, execute: (SportIdentOwnerNameWriteRequest) -> DesktopSportIdentOwnerWriteOutcome) =
        DesktopSportIdentNativeOwnerWrite.run(args,
            PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), recoveryStore(), execute)

    private fun recoveryStore() = DesktopSportIdentOwnerRecoveryStore(
        temporary.root.toPath().resolve("recovery.json"))
}
