package org.openardf.radiooracle.desktop

import java.nio.file.Files
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

    private fun store() = DesktopSportIdentOwnerRecoveryStore(temporary.root.toPath().resolve("recovery.json"))
    private fun refused(action: () -> Unit) {
        try { action(); fail("Expected recovery refusal") } catch (_: Exception) { }
    }
}
