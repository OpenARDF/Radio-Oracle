package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteResult
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecoveryAssessment
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerNativeAttempt
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification

internal sealed interface DesktopSportIdentOwnerRecoveryState {
    data object Empty : DesktopSportIdentOwnerRecoveryState
    data class Pending(val request: SportIdentOwnerNameWriteRequest,
        val nativeAttempt: SportIdentSi8OwnerNativeAttempt? = null) : DesktopSportIdentOwnerRecoveryState
    data object Unavailable : DesktopSportIdentOwnerRecoveryState
}

/** Keep both the disk reload and the UI reset alive when the transaction is cancelled. */
internal suspend fun finishDesktopSportIdentOwnerRecovery(
    store: DesktopSportIdentOwnerRecoveryStore,
    onFinished: (DesktopSportIdentOwnerRecoveryState) -> Unit
) = withContext(NonCancellable) {
    val state = withContext(Dispatchers.IO) { store.load() }
    onFinished(state)
}

/** Retain an incomplete attempt across page changes and app restarts; never replay it. */
internal class DesktopSportIdentOwnerRecoveryStore(
    private val file: Path = DesktopAppDirectories.appDataDirectory().resolve("si-owner-recovery.json")
) {
    @Synchronized fun load(): DesktopSportIdentOwnerRecoveryState = try {
        if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            DesktopSportIdentOwnerRecoveryState.Empty
        } else {
            check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 8192)
            val text = Files.readString(file)
            if (SportIdentOwnerNameProgramming.json.parseToJsonElement(text).jsonObject.containsKey("attemptedWords")) {
                val attempt = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentSi8OwnerNativeAttempt>(text)
                validateNative(attempt)
                DesktopSportIdentOwnerRecoveryState.Pending(attempt.request, attempt)
            } else {
                val request = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteRequest>(text)
                SportIdentOwnerNameRecovery.validate(request)
                DesktopSportIdentOwnerRecoveryState.Pending(request)
            }
        }
    } catch (_: Exception) { DesktopSportIdentOwnerRecoveryState.Unavailable }

    /** Must finish successfully before starting the SDK child. */
    @Synchronized fun begin(request: SportIdentOwnerNameWriteRequest) {
        SportIdentOwnerNameRecovery.validate(request)
        check(load() == DesktopSportIdentOwnerRecoveryState.Empty)
        val text = SportIdentOwnerNameProgramming.json.encodeToString(request)
        require(text.toByteArray(Charsets.UTF_8).size <= 4096)
        writeDesktopTextAtomically(file, text)
    }

    /** Save the complete baseline before any native word can be sent. */
    @Synchronized fun beginNative(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture) {
        check(load() == DesktopSportIdentOwnerRecoveryState.Empty)
        saveNative(SportIdentSi8OwnerNativeAttempt(1, request, before, 0))
    }

    /** Called immediately before port.write; a failed save prevents transmission. */
    @Synchronized fun markWordAttempt(request: SportIdentOwnerNameWriteRequest, wordNumber: Int) {
        val pending = load() as? DesktopSportIdentOwnerRecoveryState.Pending
            ?: error("Native owner-write recovery record is missing or unreadable.")
        val attempt = pending.nativeAttempt ?: error("Native owner-write baseline is missing.")
        check(pending.request == request && wordNumber == attempt.attemptedWords + 1)
        saveNative(attempt.copy(attemptedWords = wordNumber))
    }

    private fun saveNative(attempt: SportIdentSi8OwnerNativeAttempt) {
        validateNative(attempt)
        val text = SportIdentOwnerNameProgramming.json.encodeToString(attempt)
        require(text.toByteArray(Charsets.UTF_8).size <= 8192)
        writeDesktopTextAtomically(file, text)
        check(load() == DesktopSportIdentOwnerRecoveryState.Pending(attempt.request, attempt))
    }

    private fun validateNative(attempt: SportIdentSi8OwnerNativeAttempt) {
        require(attempt.schemaVersion == 1 && attempt.attemptedWords in 0..3)
        SportIdentOwnerNameRecovery.validate(attempt.request)
        require(attempt.before.stationNumber == attempt.request.stationNumber)
        require(SportIdentSi8OwnerWordWritePlanner.plan(attempt.request, attempt.before).size == 3)
    }

    @Synchronized fun completeVerified(request: SportIdentOwnerNameWriteRequest, result: SportIdentOwnerNameWriteResult) {
        SportIdentOwnerNameProgramming.verify(request, result)
        clear(request)
    }

    /** Clear a native attempt only after its predicted image matches a fresh complete card read. */
    @Synchronized fun completeVerifiedNative(request: SportIdentOwnerNameWriteRequest,
        comparison: SportIdentSi8OwnerWritePlanComparison) {
        check(comparison.matches)
        val predicted = comparison.predictedVersusObserved.before
        val observed = comparison.predictedVersusObserved.after
        check(predicted.stationNumber == request.stationNumber && observed.stationNumber == request.stationNumber &&
            predicted.cardNumber == request.cardNumber && observed.cardNumber == request.cardNumber &&
            predicted.firstName == request.firstName && observed.firstName == request.firstName &&
            predicted.lastName == request.lastName && observed.lastName == request.lastName)
        clear(request)
    }

    /** Acknowledgement checks identity/readiness but never claims punch preservation. */
    @Synchronized fun acknowledge(request: SportIdentOwnerNameWriteRequest, inspection: SportIdentCardOwnerInspection) {
        val pending = load() as? DesktopSportIdentOwnerRecoveryState.Pending
            ?: error("Owner-write recovery record is missing or unreadable.")
        check(pending.request == request && pending.nativeAttempt == null) {
            "A native owner-write attempt requires complete two-block recovery evidence."
        }
        val assessment = SportIdentOwnerNameRecovery.assess(request, inspection)
        check(assessment != SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD &&
            assessment != SportIdentOwnerNameRecoveryAssessment.UNREADABLE)
        clear(request)
    }

    /** Native acknowledgement requires a compatible fresh two-block card image. */
    @Synchronized fun acknowledgeNative(request: SportIdentOwnerNameWriteRequest,
        fresh: SportIdentOwnerReadFixture, observedFirstName: String, observedLastName: String) {
        val pending = load() as? DesktopSportIdentOwnerRecoveryState.Pending
            ?: error("Native owner-write recovery record is missing or unreadable.")
        val attempt = pending.nativeAttempt ?: error("Native owner-write baseline is missing.")
        check(pending.request == request)
        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(
            request, attempt.before, attempt.attemptedWords, fresh)
        check(assessment.consistentWithRecordedAttempt) {
            "Fresh card bytes do not match a possible word prefix without other changes."
        }
        val read = SportIdentOwnerReadVerification.nativeRead(fresh)
        check(read.cardNumber == request.cardNumber && read.firstName == observedFirstName &&
            read.lastName == observedLastName)
        clear(request)
    }

    private fun clear(request: SportIdentOwnerNameWriteRequest) {
        check((load() as? DesktopSportIdentOwnerRecoveryState.Pending)?.request == request)
        Files.delete(file)
    }
}
