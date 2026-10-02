package org.openardf.radiooracle.backend.sportident

import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadFixture
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWriteRecoveryStore
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerNativeAttempt
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWritePlanComparison

sealed interface AndroidSportIdentOwnerRecoveryState {
    data object Empty : AndroidSportIdentOwnerRecoveryState
    data class Pending(
        val attempt: SportIdentSi8OwnerNativeAttempt
    ) : AndroidSportIdentOwnerRecoveryState
    data object Unavailable : AndroidSportIdentOwnerRecoveryState
}

/** Durable Android recovery boundary; an incomplete write is never replayed. */
class AndroidSportIdentOwnerRecoveryStore(
    private val file: File
) : SportIdentOwnerWriteRecoveryStore {
    @Synchronized
    fun load(): AndroidSportIdentOwnerRecoveryState = try {
        if (!file.exists()) {
            AndroidSportIdentOwnerRecoveryState.Empty
        } else {
            check(file.isFile && file.length() in 1L..MAX_RECORD_BYTES)
            val attempt = SportIdentOwnerNameProgramming.json.decodeFromString<
                SportIdentSi8OwnerNativeAttempt
            >(file.readText())
            validate(attempt)
            AndroidSportIdentOwnerRecoveryState.Pending(attempt)
        }
    } catch (_: Exception) {
        AndroidSportIdentOwnerRecoveryState.Unavailable
    }

    override fun isClear(): Boolean = load() == AndroidSportIdentOwnerRecoveryState.Empty

    @Synchronized
    override fun begin(
        request: SportIdentOwnerNameWriteRequest,
        before: SportIdentOwnerReadFixture
    ) {
        check(isClear())
        save(SportIdentSi8OwnerNativeAttempt(1, request, before, 0))
    }

    @Synchronized
    override fun reserveWordSequence(request: SportIdentOwnerNameWriteRequest) {
        val pending = load() as? AndroidSportIdentOwnerRecoveryState.Pending
            ?: error("Android SI-card recovery record is missing or unreadable.")
        check(pending.attempt.request == request && pending.attempt.attemptedWords == 0)
        val words = SportIdentSi8OwnerWordWritePlanner.plan(
            request,
            pending.attempt.before
        ).size
        save(pending.attempt.copy(attemptedWords = words))
    }

    @Synchronized
    override fun completeVerified(
        request: SportIdentOwnerNameWriteRequest,
        comparison: SportIdentSi8OwnerWritePlanComparison
    ) {
        check(comparison.matches)
        val pending = load() as? AndroidSportIdentOwnerRecoveryState.Pending
            ?: error("Android SI-card recovery record is missing or unreadable.")
        check(pending.attempt.request == request)
        val observed = comparison.predictedVersusObserved.after
        check(
            observed.stationNumber == request.stationNumber &&
                observed.cardNumber == request.cardNumber &&
                observed.firstName == request.firstName &&
                observed.lastName == request.lastName
        )
        clear()
    }

    /** Clear only after a fresh image matches a possible recorded write prefix. */
    @Synchronized
    fun acknowledge(
        fresh: SportIdentOwnerReadFixture,
        observedFirstName: String,
        observedLastName: String
    ) {
        val pending = load() as? AndroidSportIdentOwnerRecoveryState.Pending
            ?: error("Android SI-card recovery record is missing or unreadable.")
        val attempt = pending.attempt
        val assessment = SportIdentSi8OwnerWordWritePlanner.assessInterruption(
            attempt.request,
            attempt.before,
            attempt.attemptedWords,
            fresh
        )
        check(assessment.consistentWithRecordedAttempt) {
            "Fresh card bytes do not match a possible recorded word prefix without other changes."
        }
        val read = SportIdentOwnerReadVerification.nativeRead(fresh)
        check(
            read.cardNumber == attempt.request.cardNumber &&
                read.firstName == observedFirstName &&
                read.lastName == observedLastName
        )
        clear()
    }

    private fun save(attempt: SportIdentSi8OwnerNativeAttempt) {
        validate(attempt)
        val bytes = SportIdentOwnerNameProgramming.json.encodeToString(attempt).encodeToByteArray()
        require(bytes.size <= MAX_RECORD_BYTES)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(temporary.renameTo(file)) { "Could not replace the SI-card recovery record." }
        check(load() == AndroidSportIdentOwnerRecoveryState.Pending(attempt))
    }

    private fun validate(attempt: SportIdentSi8OwnerNativeAttempt) {
        require(attempt.schemaVersion == 1)
        SportIdentOwnerNameRecovery.validate(attempt.request)
        require(attempt.before.stationNumber == attempt.request.stationNumber)
        require(
            attempt.attemptedWords in 0..SportIdentSi8OwnerWordWritePlanner.plan(
                attempt.request,
                attempt.before
            ).size
        )
    }

    private fun clear() {
        check(file.delete()) { "Could not clear the SI-card recovery record." }
        check(load() == AndroidSportIdentOwnerRecoveryState.Empty)
    }

    companion object {
        const val FILE_NAME = "si-owner-recovery.json"
        private const val MAX_RECORD_BYTES = 8_192L
    }
}
