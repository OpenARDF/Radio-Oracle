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
import androidx.compose.material.Checkbox
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardInspectionWatcher
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentOwnerSnapshot
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentProgrammingClient
import org.openardf.radiooracle.desktop.usb.discoverDesktopSportIdentSdk
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecoveryAssessment
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerWritePhase
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus

@Composable
internal fun SportIdentCardInspectionPanel(
    isReaderConnected: Boolean,
    isStationBusy: Boolean,
    siPortMutex: Mutex
) {
    var snapshot by remember { mutableStateOf<DesktopSportIdentOwnerSnapshot?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var isProgramming by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }
    var isFinishingRecovery by remember { mutableStateOf(false) }
    var programmingJob by remember { mutableStateOf<Job?>(null) }
    var programmingPhase by remember { mutableStateOf<SportIdentOwnerWritePhase?>(null) }
    var pendingNames by remember { mutableStateOf<Pair<String, String>?>(null) }
    val recoveryStore = remember { DesktopSportIdentOwnerRecoveryStore() }
    var recoveryState by remember { mutableStateOf(recoveryStore.load()) }
    val sdkConfiguration = remember { discoverDesktopSportIdentSdk() }
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
                            status = when {
                                recoveryState is DesktopSportIdentOwnerRecoveryState.Pending ->
                                    "Card read. Review the stored names below to finish checking the interrupted write."
                                read.inspection.family == SportIdentCardFamily.SI8 && sdkConfiguration != null ->
                                    "Card read. Edit the names below, then choose Write Names. Remove the card before inserting another."
                                else -> "Card read. Its owner information is shown below. Remove the card before inserting another."
                            }
                        } },
                        onCardInserted = { uiScope.launch {
                            if (listenerJob.isActive) {
                                isReading = true
                                status = "Reading card. Keep it seated until the read finishes…"
                            }
                        } },
                        onCardRemoved = { uiScope.launch {
                            if (listenerJob.isActive && !isProgramming && pendingNames == null) {
                                status = "Insert a card to read it automatically, or edit the last card shown below."
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
                    Text("This native write requires a fresh complete card read and recovery assessment before another write. The names shown here do not clear its recovery record.")
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
        if (isProgramming) {
            Button(enabled = !isCancelling && programmingPhase != SportIdentOwnerWritePhase.WRITING, onClick = {
                if (!isCancelling && programmingPhase != SportIdentOwnerWritePhase.WRITING) {
                    isCancelling = true
                    status = "Stopping programming; wait for the station to be released…"
                    programmingJob?.cancel()
                }
            }) { Text(if (isCancelling) "Stopping…" else if (programmingPhase == SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK)
                "Stop Verification" else "Cancel Programming") }
        }
        }
        Surface(Modifier.fillMaxWidth(), color = if (failed) DesktopPalette.Error.copy(alpha = 0.08f)
            else DesktopPalette.Black.copy(alpha = 0.04f)) {
            Text(status ?: "Insert a card to read its stored names. Names change only after you confirm Write Names.",
                Modifier.padding(10.dp), color = if (failed) DesktopPalette.Error else DesktopPalette.Black)
        }
        snapshot?.inspection?.let { SportIdentCardOwnerDetails(it, isProgramming) }
        snapshot?.inspection?.takeIf { it.family == SportIdentCardFamily.SI8 && it.status == SportIdentOwnerDataStatus.READ }
            ?.takeIf { recoveryState == DesktopSportIdentOwnerRecoveryState.Empty }
            ?.let { inspection ->
                SportIdentSi8OwnerNameEditor(inspection,
                    enabled = isReaderConnected && !isStationBusy && !isReading && !isProgramming && !isFinishingRecovery &&
                        recoveryState == DesktopSportIdentOwnerRecoveryState.Empty,
                    canProgram = sdkConfiguration != null,
                    onWrite = { pendingNames = it.firstName to it.lastName })
            }
    }
    pendingNames?.let { names ->
        var accepted by remember(names) { mutableStateOf(false) }
        val target = snapshot ?: return@let
        DesktopAlertDialog(onDismissRequest = {
            cardToRemoveBeforeListening = target.inspection.siNumber
            pendingNames = null
        },
            title = { Text("Write Names to SI-Card ${target.inspection.siNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("First name: ${names.first.ifEmpty { "Not stored" }}")
                    Text("Last name: ${names.second.ifEmpty { "Not stored" }}")
                    Text("After confirming, remove and insert this card to start writing. Keep it seated during the write, then reinsert it again so the app can check the stored names and compare its punches.")
                    Text("Programming may erase existing punches.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                        Text("I accept possible loss of existing punches.")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = accepted && isReaderConnected && !isStationBusy && !isReading && !isProgramming,
                    onClick = {
                        pendingNames = null
                        isProgramming = true
                        isCancelling = false
                        programmingPhase = null
                        failed = false
                        status = "Checking the station before programming…"
                        programmingJob = scope.launch {
                            try {
                                val request = SportIdentOwnerNameProgramming.prepare(target.inspection,
                                    target.stationNumber, names.first, names.second, true)
                                val result = siPortMutex.withLock {
                                    withContext(Dispatchers.IO) { recoveryStore.begin(request) }
                                    DesktopSportIdentProgrammingClient(checkNotNull(sdkConfiguration)).write(target, request) { phase ->
                                        programmingPhase = phase
                                        status = when (phase) {
                                            SportIdentOwnerWritePhase.WAITING_FOR_CARD ->
                                                "Remove and insert SI-Card ${request.cardNumber} to start writing. A fresh insertion confirms the target card; this step waits up to 45 seconds."
                                            SportIdentOwnerWritePhase.WRITING -> "Writing names to SI-Card ${request.cardNumber}. Keep the card seated…"
                                            SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK ->
                                                "Write completed. Reinsert SI-Card ${request.cardNumber} to check its stored names and compare punches. Success is not confirmed yet; this step waits up to 45 seconds."
                                        }
                                    }
                                }
                                withContext(Dispatchers.IO) { recoveryStore.completeVerified(request, result) }
                                snapshot = target.copy(inspection = target.inspection.copy(holder = SportIdentCardHolder(
                                    result.firstName.ifEmpty { null }, result.lastName.ifEmpty { null }, null)))
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                val punches = if (result.controlPunchCountAfter == 1) "control punch" else "control punches"
                                status = "Names written and verified. ${result.controlPunchCountAfter} $punches preserved. Remove the card before the next read."
                            } catch (cancelled: CancellationException) {
                                snapshot = null
                                failed = true
                                status = if (programmingPhase == SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK)
                                    "Verification stopped after writing. Reinsert SI-Card ${target.inspection.siNumber} to check its stored names. Earlier punch preservation was not verified."
                                else "Programming stopped. Reinsert SI-Card ${target.inspection.siNumber} to check its stored names."
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                throw cancelled
                            } catch (error: Exception) {
                                snapshot = null
                                failed = true
                                status = when (programmingPhase) {
                                    SportIdentOwnerWritePhase.WAITING_FOR_CARD ->
                                        "The target card could not be confirmed for writing. Reinsert it to check its stored names before trying again."
                                    SportIdentOwnerWritePhase.WRITING ->
                                        "Write completion was not confirmed. Reinsert the card to check its stored names before trying again."
                                    SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK ->
                                        "Writing completed, but verification did not finish. Reinsert the card to check the stored names. Earlier punch preservation was not verified."
                                    null -> "Programming did not finish. Reinsert the card to check what is stored before trying another write."
                                }
                                cardToRemoveBeforeListening = target.inspection.siNumber
                                DesktopDebugLog.error("SI", status.orEmpty())
                            } finally {
                                finishDesktopSportIdentOwnerRecovery(recoveryStore) {
                                    recoveryState = it
                                    isProgramming = false
                                    isCancelling = false
                                    programmingPhase = null
                                    programmingJob = null
                                }
                            }
                        }
                    }) { Text("Write Names") }
            },
            dismissButton = { TextButton(onClick = {
                cardToRemoveBeforeListening = target.inspection.siNumber
                pendingNames = null
            }) { Text("Cancel") } })
    }
}

private fun ownerNameRecoveryMessage(assessment: SportIdentOwnerNameRecoveryAssessment): String = when (assessment) {
    SportIdentOwnerNameRecoveryAssessment.NOT_TARGET_CARD -> "This is a different card. Read the card from the interrupted attempt."
    SportIdentOwnerNameRecoveryAssessment.UNREADABLE -> "The target card's owner information is incomplete or unsupported. Read it again."
    SportIdentOwnerNameRecoveryAssessment.REQUESTED_NAMES -> "The requested names are stored on the card."
    SportIdentOwnerNameRecoveryAssessment.ORIGINAL_NAMES -> "The card still has its original names."
    SportIdentOwnerNameRecoveryAssessment.DIFFERENT_NAMES -> "The stored names differ from both the original and requested names. Review them below."
}

@Composable
private fun SportIdentCardOwnerDetails(inspection: SportIdentCardOwnerInspection, isProgramming: Boolean) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${inspection.family.label} · SI-Card ${inspection.siNumber}", fontWeight = FontWeight.Bold)
            if (inspection.status == SportIdentOwnerDataStatus.READ) {
                val storedNames = listOfNotNull(inspection.holder?.firstName, inspection.holder?.lastName)
                    .filter(String::isNotEmpty).joinToString(" ").ifEmpty { "Not stored" }
                Text("${if (isProgramming) "Before write" else "Stored names"}: $storedNames")
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
