package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteResult
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecoveryAssessment

internal sealed interface DesktopSportIdentOwnerRecoveryState {
    data object Empty : DesktopSportIdentOwnerRecoveryState
    data class Pending(val request: SportIdentOwnerNameWriteRequest) : DesktopSportIdentOwnerRecoveryState
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
            check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 4096)
            val request = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteRequest>(Files.readString(file))
            SportIdentOwnerNameRecovery.validate(request)
            DesktopSportIdentOwnerRecoveryState.Pending(request)
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

    @Synchronized fun completeVerified(request: SportIdentOwnerNameWriteRequest, result: SportIdentOwnerNameWriteResult) {
        SportIdentOwnerNameProgramming.verify(request, result)
        clear(request)
    }

    /** Acknowledgement checks identity/readiness but never claims punch preservation. */
    @Synchronized fun acknowledge(request: SportIdentOwnerNameWriteRequest, inspection: SportIdentCardOwnerInspection) {
        val assessment = SportIdentOwnerNameRecovery.assess(request, inspection)
        check(assessment != SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD &&
            assessment != SportIdentOwnerNameRecoveryAssessment.UNREADABLE)
        clear(request)
    }

    private fun clear(request: SportIdentOwnerNameWriteRequest) {
        check(load() == DesktopSportIdentOwnerRecoveryState.Pending(request))
        Files.delete(file)
    }
}
