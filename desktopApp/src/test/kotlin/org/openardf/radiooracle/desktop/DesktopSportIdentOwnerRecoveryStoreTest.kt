package org.openardf.radiooracle.desktop

import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openardf.radiooracle.shared.sportident.*

class DesktopSportIdentOwnerRecoveryStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val original = SportIdentCardOwnerInspection(2450662, SportIdentCardFamily.SI8,
        SportIdentCardHolder("Mickey", "Mouse", null), SportIdentOwnerDataStatus.READ)
    private val request = SportIdentOwnerNameProgramming.prepare(original, 593927, "Minnie", "Mouse", true)
    private val verified = SportIdentOwnerNameWriteResult(1, 593927, 2450662, "SI-Card8", "Minnie", "Mouse",
        11, 11, true, true, true)

    @Test fun savedIntentionSurvivesRecreatingTheStoreAndCannotBeOverwritten() {
        val file = temporary.root.toPath().resolve("data/recovery.json")
        DesktopSportIdentOwnerRecoveryStore(file).begin(request)
        val restarted = DesktopSportIdentOwnerRecoveryStore(file)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), restarted.load())
        refused { restarted.begin(request.copy(firstName = "Mortimer")) }
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), restarted.load())
        assertEquals(listOf(file), Files.list(file.parent).use { it.toList() })
    }

    @Test fun onlyACompleteMatchingReadCanBeAcknowledged() {
        val store = store()
        store.begin(request)
        refused { store.acknowledge(request, original.copy(siNumber = 2450663)) }
        refused { store.acknowledge(request, original.copy(status = SportIdentOwnerDataStatus.INCOMPLETE)) }
        refused { store.acknowledge(request, original.copy(holder = null)) }
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
        store.acknowledge(request, original)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store.load())
    }

    @Test fun fullVerificationCanClearTheRecordButIncompletePreservationCannot() {
        val store = store()
        store.begin(request)
        refused { store.completeVerified(request, verified.copy(punchesPreserved = false)) }
        refused { store.completeVerified(request, verified.copy(cardNumber = 2450663)) }
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
        store.completeVerified(request, verified)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store.load())
    }

    @Test fun nativeFullBlockMatchClearsOnlyItsOwnVerifiedRequest() {
        val nativeRequest = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
            "Daisy", "Duck", "Donald", "Duck", true)
        val before = nativeFixture("Daisy;Duck;")
        val matching = SportIdentSi8OwnerWordWritePlanner.compareToObserved(
            nativeRequest, before, nativeFixture("Donald;Duck;"))
        val mismatched = SportIdentSi8OwnerWordWritePlanner.compareToObserved(
            nativeRequest, before, nativeFixture("Daisy;Duck;"))
        val store = store()
        store.begin(nativeRequest)

        refused { store.completeVerifiedNative(nativeRequest, mismatched) }
        refused { store.completeVerifiedNative(nativeRequest.copy(firstName = "Daisie"), matching) }
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(nativeRequest), store.load())
        store.completeVerifiedNative(nativeRequest, matching)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Empty, store.load())
    }

    @Test fun nativeBaselineAndEachAttemptSurviveRestartBeforeTheNextWord() {
        val file = temporary.root.toPath().resolve("native-recovery.json")
        val nativeRequest = SportIdentOwnerNameWriteRequest(1, 593927, 2450662,
            "Daisy", "Duck", "Donald", "Duck", true)
        val before = nativeFixture("Daisy;Duck;")
        val store = DesktopSportIdentOwnerRecoveryStore(file)
        store.beginNative(nativeRequest, before)
        for (count in 0..3) {
            val pending = DesktopSportIdentOwnerRecoveryStore(file).load() as DesktopSportIdentOwnerRecoveryState.Pending
            assertEquals(nativeRequest, pending.request)
            assertEquals(before, requireNotNull(pending.nativeAttempt).before)
            assertEquals(count, pending.nativeAttempt.attemptedWords)
            refused { store.begin(nativeRequest) }
            if (count < 3) store.markWordAttempt(nativeRequest, count + 1)
        }
        refused { store.markWordAttempt(nativeRequest, 3) }
        refused { store.markWordAttempt(nativeRequest.copy(firstName = "Minnie"), 4) }
    }

    @Test fun anOlderAcknowledgementCannotDeleteAnotherAttempt() {
        val store = store()
        val other = request.copy(firstName = "Mortimer")
        store.begin(other)
        refused { store.acknowledge(request, original) }
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(other), store.load())
    }

    @Test fun malformedOversizedAndInvalidRecordsBlockNewProgramming() {
        val file = temporary.root.toPath().resolve("recovery.json")
        val store = DesktopSportIdentOwnerRecoveryStore(file)
        for (text in listOf("not JSON", "x".repeat(4097),
                SportIdentOwnerNameProgramming.json.encodeToString(request.copy(acceptPossiblePunchLoss = false)))) {
            Files.writeString(file, text)
            assertEquals(DesktopSportIdentOwnerRecoveryState.Unavailable, store.load())
            refused { store.begin(request) }
            assertEquals(text, Files.readString(file))
        }
    }

    @Test fun aFailedPersistenceAttemptCannotPretendTheRecoveryRecordExists() {
        val parent = temporary.newFile("not-a-directory").toPath()
        val store = DesktopSportIdentOwnerRecoveryStore(parent.resolve("recovery.json"))
        refused { store.begin(request) }
        assertFalse(Files.isDirectory(parent))
    }

    @Test fun cancellationReloadsTheReminderAndResetsTheUiBeforeCleanupReturns() = runBlocking {
        val store = store()
        store.begin(request)
        val started = CompletableDeferred<Unit>()
        var isProgramming = true
        var recovered: DesktopSportIdentOwnerRecoveryState? = null
        val transaction = launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                finishDesktopSportIdentOwnerRecovery(store) {
                    recovered = it
                    isProgramming = false
                }
            }
        }
        started.await()
        transaction.cancelAndJoin()
        assertFalse(isProgramming)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), recovered)
        assertEquals(DesktopSportIdentOwnerRecoveryState.Pending(request), store.load())
    }

    private fun store() = DesktopSportIdentOwnerRecoveryStore(temporary.root.toPath().resolve("recovery.json"))
    private fun nativeFixture(owner: String): SportIdentOwnerReadFixture {
        val block0 = ByteArray(128)
        block0[22] = 1
        block0[24] = 2
        block0[25] = 0x25
        block0[26] = 0x64
        block0[27] = 0xe6.toByte()
        owner.forEachIndexed { index, char -> block0[32 + index] = char.code.toByte() }
        return SportIdentOwnerReadVerification.capture(593927,
            listOf(SportIdentCardBlock(0, block0), SportIdentCardBlock(1, ByteArray(128))))
    }
    private fun refused(action: () -> Unit) {
        try { action(); fail("Expected recovery refusal") } catch (_: Exception) { }
    }
}
