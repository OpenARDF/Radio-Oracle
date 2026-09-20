package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.Surface
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
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecovery
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameRecoveryAssessment
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
    var isFinishingRecovery by remember { mutableStateOf(false) }
    val recoveryStore = remember { DesktopSportIdentOwnerRecoveryStore() }
    var recoveryState by remember { mutableStateOf(recoveryStore.load()) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var listenerFailed by remember { mutableStateOf(false) }
    var listenerGeneration by remember { mutableStateOf(0) }
    var cardToRemoveBeforeListening by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isReaderConnected, isStationBusy, isFinishingRecovery, listenerGeneration) {
        if (!isReaderConnected || isStationBusy || isFinishingRecovery) return@LaunchedEffect
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
                                read.inspection.family == SportIdentCardFamily.SI8 ->
                                    "Card read. You can preview names below. Remove the card before inserting another."
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
                            if (listenerJob.isActive) {
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
        val step = if (snapshot != null) 2 else 1
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("1  Read card", "2  Preview names").forEachIndexed { index, label ->
                Text(label, fontWeight = if (index + 1 == step) FontWeight.Bold else FontWeight.Normal,
                    color = if (index + 1 == step) DesktopPalette.Black else DesktopPalette.Disconnected)
            }
        }
        when (val recovery = recoveryState) {
            is DesktopSportIdentOwnerRecoveryState.Pending -> {
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
        if (listenerFailed && isReaderConnected && !isStationBusy) {
            Button(onClick = { listenerGeneration++; failed = false; status = "Waiting for the card reader…" }) {
                Text("Retry Reader")
            }
        }
        Surface(Modifier.fillMaxWidth(), color = if (failed) DesktopPalette.Error.copy(alpha = 0.08f)
            else DesktopPalette.Black.copy(alpha = 0.04f)) {
            Text(status ?: "Insert a card to read its stored names. Name writing is under validation.",
                Modifier.padding(10.dp), color = if (failed) DesktopPalette.Error else DesktopPalette.Black)
        }
        snapshot?.inspection?.let { SportIdentCardOwnerDetails(it) }
        snapshot?.inspection?.takeIf { it.family == SportIdentCardFamily.SI8 && it.status == SportIdentOwnerDataStatus.READ }
            ?.takeIf { recoveryState == DesktopSportIdentOwnerRecoveryState.Empty }
            ?.let { inspection ->
                SportIdentSi8OwnerNameEditor(inspection,
                    enabled = isReaderConnected && !isStationBusy && !isReading && !isFinishingRecovery)
            }
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
private fun SportIdentCardOwnerDetails(inspection: SportIdentCardOwnerInspection) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${inspection.family.label} · SI-Card ${inspection.siNumber}", fontWeight = FontWeight.Bold)
            if (inspection.status == SportIdentOwnerDataStatus.READ) {
                val storedNames = listOfNotNull(inspection.holder?.firstName, inspection.holder?.lastName)
                    .filter(String::isNotEmpty).joinToString(" ").ifEmpty { "Not stored" }
                Text("Stored names: $storedNames")
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
