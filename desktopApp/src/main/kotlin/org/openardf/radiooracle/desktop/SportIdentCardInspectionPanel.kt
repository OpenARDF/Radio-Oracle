package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardInspectionWatcher
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentOwnerSnapshot
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentNativeProgrammingClient
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecoveryAssessment
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerReadVerification
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWritePhase
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus
import org.openardf.radiooracle.shared.sportident.SportIdentSi8OwnerWordWritePlanner

@Composable
internal fun SportIdentCardInspectionPanel(
    isReaderConnected: Boolean,
    isStationBusy: Boolean,
    siPortMutex: Mutex
) {
    var snapshot by remember { mutableStateOf<DesktopSportIdentOwnerSnapshot?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isProgramming by remember { mutableStateOf(false) }
    var isFinishingRecovery by remember { mutableStateOf(false) }
    var programmingPhase by remember { mutableStateOf<SportIdentOwnerWritePhase?>(null) }
    var pendingNames by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastVerifiedWrite by remember { mutableStateOf<VerifiedOwnerWriteSummary?>(null) }
    val recoveryStore = remember { DesktopSportIdentOwnerRecoveryStore() }
    var recoveryState by remember { mutableStateOf(recoveryStore.load()) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var listenerFailed by remember { mutableStateOf(false) }
    var listenerGeneration by remember { mutableStateOf(0) }
    var cardToRemoveBeforeListening by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isReaderConnected, isStationBusy, isProgramming, isFinishingRecovery, pendingNames, listenerGeneration) {
        if (!isReaderConnected || isStationBusy || isProgramming || isFinishingRecovery || pendingNames != null) return@LaunchedEffect
        val uiScope = this
        val listenerJob = checkNotNull(coroutineContext[Job])
        val initialCardToRemove = cardToRemoveBeforeListening
        cardToRemoveBeforeListening = null
        listenerFailed = false
        if (snapshot == null) {
            status = "Insert a card to read its stored names. If it is already seated, remove and reinsert it. Keep it seated until the read finishes."
            failed = false
        }
        try {
            withContext(Dispatchers.IO) {
                siPortMutex.withLock {
                    if (listenerJob.isActive) desktopCardInspectionWatcher().watch(
                        onSnapshot = { read -> uiScope.launch {
                            if (!listenerJob.isActive) return@launch
                            snapshot = read
                            isReading = false
                            failed = false
                            val freshPunchCount = read.rawRead?.let { raw ->
                                runCatching { SportIdentOwnerReadVerification.nativeRead(raw).controlPunchCount }
                                    .getOrNull()
                            }
                            val completedWrite = lastVerifiedWrite?.takeIf {
                                it.cardNumber == read.inspection.siNumber &&
                                    it.firstName == read.inspection.holder?.firstName.orEmpty() &&
                                    it.lastName == read.inspection.holder?.lastName.orEmpty() &&
                                    it.punchCount == freshPunchCount
                            }
                            if (lastVerifiedWrite?.cardNumber == read.inspection.siNumber && completedWrite == null) {
                                lastVerifiedWrite = null
                            }
                            status = when {
                                recoveryState is DesktopSportIdentOwnerRecoveryState.Pending ->
                                    "Card read. Review the stored names below to finish checking the interrupted write."
                                completedWrite != null ->
                                    "Fresh card read completed. Review the verified write result below."
                                read.inspection.family == SportIdentCardFamily.SI8 && read.rawRead != null ->
                                    "Card read. Edit the names below, then choose Write Names. Remove the card before inserting another."
                                else -> "Card read. Its owner information is shown below. Remove the card before inserting another."
                            }
                        } },
                        onCardInserted = { uiScope.launch {
                            if (listenerJob.isActive && !isProgramming && pendingNames == null) {
                                isReading = true
                                status = "Reading card. Keep it seated until the read finishes…"
                            }
                        } },
                        onCardRemoved = { uiScope.launch {
                            if (listenerJob.isActive && !isProgramming && pendingNames == null) {
                                status = if (lastVerifiedWrite?.cardNumber == snapshot?.inspection?.siNumber) {
                                    "Write complete. Insert the card again for another complete read, or review the verified result below."
                                } else "Insert a card to read it automatically, or edit the last card shown below."
                            }
                        } },
                        shouldContinue = { listenerJob.isActive },
                        initialCardToRemove = initialCardToRemove
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            listenerFailed = true
            failed = true
            status = "Card reader stopped: ${error.message ?: error::class.simpleName}. Choose Retry Reader."
            DesktopDebugLog.error("SI", status.orEmpty())
        } finally {
            isReading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val step = if (isProgramming) 3 else if (snapshot != null) 2 else 1
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("1  Read card", "2  Edit names", "3  Write and verify").forEachIndexed { index, label ->
                Text(label, fontWeight = if (index + 1 == step) FontWeight.Bold else FontWeight.Normal,
                    color = if (index + 1 == step) DesktopPalette.Black else DesktopPalette.Disconnected)
            }
        }
        when (val recovery = recoveryState) {
            is DesktopSportIdentOwnerRecoveryState.Pending -> if (!isProgramming) {
                Text("Check interrupted write · SI-Card ${recovery.request.cardNumber}",
                    fontWeight = FontWeight.Bold, color = DesktopPalette.Error)
                if (recovery.nativeAttempt != null) {
                    Text("Reinsert this card in station ${recovery.request.stationNumber} for a fresh complete read. An interrupted Kotlin write is never retried automatically.")
                    val read = snapshot?.rawRead
                    val nativeAssessment = if (read != null) runCatching {
                        SportIdentSi8OwnerWordWritePlanner.assessInterruption(recovery.request,
                            recovery.nativeAttempt.before, recovery.nativeAttempt.attemptedWords, read)
                    }.getOrNull() else null
                    if (nativeAssessment != null) {
                        if (nativeAssessment.consistentWithRecordedAttempt &&
                            snapshot?.inspection?.status == SportIdentOwnerDataStatus.READ) {
                            Text("Fresh card image matches planned word prefix " +
                                nativeAssessment.plausibleWordPrefixes.joinToString(" or ") +
                                "; no other card bytes changed. Review the stored names below before clearing this reminder.")
                            Button(enabled = !isReading && !isStationBusy && !isFinishingRecovery, onClick = {
                                val fresh = snapshot ?: return@Button
                                val fixture = fresh.rawRead ?: return@Button
                                cardToRemoveBeforeListening = fresh.inspection.siNumber
                                isFinishingRecovery = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            recoveryStore.acknowledgeNative(recovery.request, fixture,
                                                fresh.inspection.holder?.firstName.orEmpty(),
                                                fresh.inspection.holder?.lastName.orEmpty())
                                        }
                                        status = "Fresh card image accepted. Review the stored names before preparing another write."
                                        failed = false
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        failed = true
                                        status = "Recovery could not be cleared. Programming remains unavailable."
                                    } finally {
                                        finishDesktopSportIdentOwnerRecovery(recoveryStore) {
                                            recoveryState = it
                                            isFinishingRecovery = false
                                        }
                                    }
                                }
                            }) { Text("Accept Fresh Card Read") }
                        } else {
                            Text("This read does not match a safe recorded word prefix. Programming remains blocked.",
                                color = DesktopPalette.Error)
                        }
                    }
                } else {
                    Text("Read this card to check the names. Earlier punch and settings preservation was not verified.")
                }
                val assessment = snapshot?.inspection?.let { SportIdentOwnerNameRecovery.assess(recovery.request, it) }
                assessment?.let { Text(ownerNameRecoveryMessage(it)) }
                if (recovery.nativeAttempt == null && assessment in listOf(SportIdentOwnerNameRecoveryAssessment.REQUESTED_NAMES,
                        SportIdentOwnerNameRecoveryAssessment.ORIGINAL_NAMES, SportIdentOwnerNameRecoveryAssessment.DIFFERENT_NAMES)) {
                    Text("Accept this fresh read to enable another write. It does not confirm earlier punch preservation.", fontSize = 13.sp)
                    Button(enabled = !isReading && !isStationBusy && !isFinishingRecovery, onClick = {
                        val inspection = snapshot?.inspection ?: return@Button
                        cardToRemoveBeforeListening = inspection.siNumber
                        isFinishingRecovery = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { recoveryStore.acknowledge(recovery.request, inspection) }
                                status = "Recovery finished. The freshly read names are shown below; earlier punch preservation was not verified."
                                failed = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                failed = true
                                status = "The recovery reminder could not be cleared. Programming remains unavailable."
                            } finally {
                                finishDesktopSportIdentOwnerRecovery(recoveryStore) {
                                    recoveryState = it
                                    isFinishingRecovery = false
                                }
                            }
                        }
                    }) { Text("Accept Card Read") }
                }
            }
            DesktopSportIdentOwnerRecoveryState.Unavailable ->
                Text("The programming recovery record could not be loaded. Programming is unavailable until the record can be recovered.",
                    color = DesktopPalette.Error)
            DesktopSportIdentOwnerRecoveryState.Empty -> Unit
        }
        if (isStationBusy) Text("Stop the active SI readout before reading owner information.")
        else if (!isReaderConnected) Text("Connect a SPORTident download station to read a card.")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (listenerFailed && isReaderConnected && !isStationBusy && !isProgramming) {
            Button(onClick = { listenerGeneration++; failed = false; status = "Waiting for the card reader…" }) {
                Text("Retry Reader")
            }
        }
        if (isProgramming) Text("Kotlin write in progress; wait for verification or timeout.")
        }
        Surface(Modifier.fillMaxWidth(), color = if (failed) DesktopPalette.Error.copy(alpha = 0.08f)
            else DesktopPalette.Black.copy(alpha = 0.04f)) {
            Text(status ?: "Insert a card to read its stored names. Names change only after you confirm Write Names.",
                Modifier.padding(10.dp), color = if (failed) DesktopPalette.Error else DesktopPalette.Black)
        }
        lastVerifiedWrite?.takeIf { it.cardNumber == snapshot?.inspection?.siNumber }?.let { verified ->
            Surface(Modifier.fillMaxWidth(), color = DesktopPalette.Connected.copy(alpha = 0.12f)) {
                Text(verifiedWriteStatus(verified), Modifier.padding(10.dp), color = DesktopPalette.Black,
                    fontWeight = FontWeight.Bold)
            }
        }
        val currentPunchCount = snapshot?.rawRead?.let { raw ->
            runCatching { SportIdentOwnerReadVerification.nativeRead(raw).controlPunchCount }.getOrNull()
        }
        snapshot?.inspection?.let { SportIdentCardOwnerDetails(it, isProgramming, currentPunchCount) }
        snapshot?.inspection?.takeIf { it.family == SportIdentCardFamily.SI8 && it.status == SportIdentOwnerDataStatus.READ }
            ?.takeIf { recoveryState == DesktopSportIdentOwnerRecoveryState.Empty }
            ?.let { inspection ->
                SportIdentSi8OwnerNameEditor(inspection,
                    enabled = isReaderConnected && !isStationBusy && !isReading && !isProgramming && !isFinishingRecovery,
                    canProgram = snapshot?.rawRead != null,
                    onWrite = { pendingNames = it.firstName to it.lastName })
            }
    }
    pendingNames?.let { names ->
        val target = snapshot ?: return@let
        val request = remember(names, target) {
            runCatching {
                SportIdentOwnerNameProgramming.prepare(target.inspection, target.stationNumber,
                    names.first, names.second, true)
            }.getOrNull()
        }
        val nativeAvailable = request != null && target.rawRead?.let { read ->
            runCatching { SportIdentSi8OwnerWordWritePlanner.plan(request, read).size in 1..7 }
                .getOrDefault(false)
        } == true
        DesktopAlertDialog(
            onDismissRequest = {
                cardToRemoveBeforeListening = target.inspection.siNumber
                pendingNames = null
            },
            title = { Text("Write Names to SI-Card ${target.inspection.siNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("First name: ${names.first.ifEmpty { "Not stored" }}")
                    Text("Last name: ${names.second.ifEmpty { "Not stored" }}")
                    Text("After confirming, remove and insert this card to start writing. Keep it seated during the write. When prompted, remove and reinsert it for verification.")
                    Text("The Kotlin writer changes only SI-Card8 owner words. It reports success only after a fresh two-block read confirms the requested names and preserves all punches and other card data.")
                    if (!nativeAvailable) Text("A complete raw SI-Card8 read is required before writing.",
                        color = DesktopPalette.Error)
                }
            },
            confirmButton = {
                TextButton(enabled = nativeAvailable && isReaderConnected &&
                    !isStationBusy && !isReading && !isProgramming,
                    onClick = {
                        pendingNames = null
                        isProgramming = true
                        lastVerifiedWrite = null
                        programmingPhase = null
                        failed = false
                        status = "Checking the station before programming…"
                        scope.launch {
                            try {
                                val prepared = checkNotNull(request)
                                val onPhase: (SportIdentOwnerWritePhase) -> Unit = { phase ->
                                    scope.launch {
                                        if (isProgramming) {
                                            programmingPhase = phase
                                            status = ownerWritePhaseStatus(phase, prepared.cardNumber)
                                        }
                                    }
                                }
                                val outcome = siPortMutex.withLock {
                                    withContext(NonCancellable + Dispatchers.IO) {
                                        DesktopSportIdentNativeProgrammingClient(recoveryStore)
                                            .write(prepared, onPhase)
                                    }
                                }
                                check(outcome.verified) { "The Kotlin write was not verified." }
                                val punchCount = requireNotNull(outcome.comparison)
                                    .predictedVersusObserved.after.controlPunchCount
                                snapshot = target.copy(inspection = target.inspection.copy(holder = SportIdentCardHolder(
                                    prepared.firstName.ifEmpty { null }, prepared.lastName.ifEmpty { null }, null)),
                                    rawRead = null)
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                val punches = if (punchCount == 1) "control punch" else "control punches"
                                status = "Write complete. Remove the card before the next read; the verified result is shown below."
                                lastVerifiedWrite = VerifiedOwnerWriteSummary(prepared.cardNumber,
                                    prepared.firstName, prepared.lastName, punchCount)
                                DesktopDebugLog.info("SI", "SI-Card ${prepared.cardNumber} owner names written and " +
                                    "complete card image verified; $punchCount $punches preserved")
                            } catch (cancelled: CancellationException) {
                                snapshot = null
                                failed = true
                                status = "Kotlin write interrupted. Reinsert SI-Card ${target.inspection.siNumber} for a fresh recovery read before another write."
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                throw cancelled
                            } catch (error: Exception) {
                                snapshot = null
                                failed = true
                                status = "Kotlin write was not verified. Remove and reinsert SI-Card ${target.inspection.siNumber} for a fresh full-card recovery read. Do not repeat the write yet."
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                DesktopDebugLog.error("SI", "$status ${error.message ?: error::class.simpleName}")
                            } finally {
                                finishDesktopSportIdentOwnerRecovery(recoveryStore) {
                                    recoveryState = it
                                    isProgramming = false
                                    programmingPhase = null
                                }
                            }
                        }
                    }) { Text("Write Names") }
            },
            dismissButton = {
                TextButton(onClick = {
                    cardToRemoveBeforeListening = target.inspection.siNumber
                    pendingNames = null
                }) { Text("Cancel") }
            }
        )
    }
}

private data class VerifiedOwnerWriteSummary(
    val cardNumber: Int,
    val firstName: String,
    val lastName: String,
    val punchCount: Int
)

private fun verifiedWriteStatus(verified: VerifiedOwnerWriteSummary): String {
    val punches = if (verified.punchCount == 1) "control punch" else "control punches"
    return "Last name write verified: ${verified.punchCount} $punches preserved and no other card data changed."
}

private fun ownerWritePhaseStatus(phase: SportIdentOwnerWritePhase, cardNumber: Int): String = when (phase) {
    SportIdentOwnerWritePhase.WAITING_FOR_CARD ->
        "Remove and insert SI-Card $cardNumber to start writing. Keep it seated until prompted; this step waits up to 45 seconds."
    SportIdentOwnerWritePhase.WRITING ->
        "Writing names to SI-Card $cardNumber. Keep the card seated…"
    SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK ->
        "Owner-word replies received. Remove and reinsert SI-Card $cardNumber for a fresh complete-card comparison. Success is not confirmed yet."
}

private fun ownerNameRecoveryMessage(assessment: SportIdentOwnerNameRecoveryAssessment): String = when (assessment) {
    SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD -> "This is a different card. Read the card from the interrupted attempt."
    SportIdentOwnerNameRecoveryAssessment.UNREADABLE -> "The target card's owner information is incomplete or unsupported. Read it again."
    SportIdentOwnerNameRecoveryAssessment.REQUESTED_NAMES -> "The requested names are stored on the card."
    SportIdentOwnerNameRecoveryAssessment.ORIGINAL_NAMES -> "The card still has its original names."
    SportIdentOwnerNameRecoveryAssessment.DIFFERENT_NAMES -> "The stored names differ from both the original and requested names. Review them below."
}

@Composable
private fun SportIdentCardOwnerDetails(
    inspection: SportIdentCardOwnerInspection,
    isProgramming: Boolean,
    controlPunchCount: Int?
) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${inspection.family.label} · SI-Card ${inspection.siNumber}", fontWeight = FontWeight.Bold)
            if (inspection.status == SportIdentOwnerDataStatus.READ) {
                val storedNames = listOfNotNull(inspection.holder?.firstName, inspection.holder?.lastName)
                    .filter(String::isNotEmpty).joinToString(" ").ifEmpty { "Not stored" }
                Text("${if (isProgramming) "Before write" else "Stored names"}: $storedNames")
                controlPunchCount?.let {
                    Text("Complete card read: $it ${if (it == 1) "control punch" else "control punches"}")
                }
                if (inspection.family.supportsClub) {
                    Text("Club: ${inspection.holder?.club ?: "Not stored"}")
                }
            }
            if (inspection.status != SportIdentOwnerDataStatus.READ) Text(when (inspection.status) {
                SportIdentOwnerDataStatus.READ -> ""
                SportIdentOwnerDataStatus.NOT_SUPPORTED -> "This card type does not store owner information."
                SportIdentOwnerDataStatus.INCOMPLETE -> "Owner information could not be read completely."
                SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING ->
                    "The card uses an unsupported name encoding (character set ${inspection.characterSet})."
            })
        }
    }
}

private fun desktopCardInspectionWatcher() = DesktopSportIdentCardInspectionWatcher(
    DesktopSportIdentPortSelector(discoverySettings = DesktopAppSettingsPreferences)
)
