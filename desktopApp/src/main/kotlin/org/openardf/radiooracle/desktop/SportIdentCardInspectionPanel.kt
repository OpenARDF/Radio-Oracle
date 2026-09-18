package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.AlertDialog
import androidx.compose.material.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardBlockReader
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentCardInspectionService
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentReadoutService
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentOwnerSnapshot
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentProgrammingClient
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentSdkConfiguration
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
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
    var pendingNames by remember { mutableStateOf<Pair<String, String>?>(null) }
    val sdkConfiguration = remember { DesktopSportIdentSdkConfiguration.fromEnvironment() }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SI Card Owner Information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Choose Read Card, then insert the card into the download station. Keep it seated until the read finishes.")
        Text("Read Card leaves the card unchanged and does not add a race result.", fontSize = 13.sp)
        if (isStationBusy) Text("Stop the active SI readout before reading owner information.")
        else if (!isReaderConnected) Text("Connect a SPORTident download station to read a card.")
        Button(enabled = isReaderConnected && !isStationBusy && !isReading && !isProgramming, onClick = {
            isReading = true
            snapshot = null
            failed = false
            status = "Waiting for an SI card; keep it seated until the read finishes…"
            scope.launch {
                try {
                    snapshot = withContext(Dispatchers.IO) {
                        siPortMutex.withLock { desktopCardInspectionService().inspectOneBound() }
                    }
                    status = "Card read finished. You can remove the card."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed = true
                    status = "Card read failed: ${error.message ?: error::class.simpleName}"
                    DesktopDebugLog.error("SI", status.orEmpty())
                } finally {
                    isReading = false
                }
            }
        }) { Text(if (isReading) "Reading Card" else "Read Card") }
        status?.let { Text(it, color = if (failed) DesktopPalette.Error else DesktopPalette.Disconnected) }
        snapshot?.inspection?.let { SportIdentCardOwnerDetails(it) }
        snapshot?.inspection?.takeIf { it.family == SportIdentCardFamily.SI8 && it.status == SportIdentOwnerDataStatus.READ }
            ?.let { inspection ->
                SportIdentSi8OwnerNameEditor(inspection,
                    enabled = isReaderConnected && !isStationBusy && !isReading && !isProgramming,
                    canProgram = sdkConfiguration != null,
                    onWrite = { pendingNames = it.firstName to it.lastName })
            }
    }
    pendingNames?.let { names ->
        var accepted by remember(names) { mutableStateOf(false) }
        val target = snapshot ?: return@let
        AlertDialog(onDismissRequest = { pendingNames = null },
            title = { Text("Write Names to SI-Card ${target.inspection.siNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("First name: ${names.first.ifEmpty { "Not stored" }}")
                    Text("Last name: ${names.second.ifEmpty { "Not stored" }}")
                    Text("Programming may erase existing punches. Keep the card seated during writing. You will be asked to reinsert it for verification.")
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
                        failed = false
                        status = "Checking the station before programming…"
                        scope.launch {
                            try {
                                val request = SportIdentOwnerNameProgramming.prepare(target.inspection,
                                    target.stationNumber, names.first, names.second, true)
                                val result = siPortMutex.withLock {
                                    DesktopSportIdentProgrammingClient(checkNotNull(sdkConfiguration)).write(target, request) { phase ->
                                        status = when (phase) {
                                            SportIdentOwnerWritePhase.WAITING_FOR_CARD ->
                                                "Remove and insert SI-Card ${request.cardNumber}; keep it seated for writing…"
                                            SportIdentOwnerWritePhase.WRITING -> "Writing names; keep the card seated…"
                                            SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK ->
                                                "Names written. Remove and reinsert SI-Card ${request.cardNumber} for verification…"
                                        }
                                    }
                                }
                                snapshot = target.copy(inspection = target.inspection.copy(holder = SportIdentCardHolder(
                                    result.firstName.ifEmpty { null }, result.lastName.ifEmpty { null }, null)))
                                status = "Names written and verified. ${result.controlPunchCountAfter} control punches preserved. You can remove the card."
                            } catch (cancelled: CancellationException) {
                                snapshot = null
                                throw cancelled
                            } catch (error: Exception) {
                                snapshot = null
                                failed = true
                                status = "The write could not be verified. Read the card again before trying another write."
                                DesktopDebugLog.error("SI", status.orEmpty())
                            } finally { isProgramming = false }
                        }
                    }) { Text("Write Names") }
            },
            dismissButton = { TextButton(onClick = { pendingNames = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SportIdentCardOwnerDetails(inspection: SportIdentCardOwnerInspection) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SI-Card number: ${inspection.siNumber}")
            Text("Card type: ${inspection.family.label}")
            if (inspection.status == SportIdentOwnerDataStatus.READ) {
                Text("First name: ${inspection.holder?.firstName ?: "Not stored"}")
                Text("Last name: ${inspection.holder?.lastName ?: "Not stored"}")
                if (inspection.family.supportsClub) {
                    Text("Club: ${inspection.holder?.club ?: "Not stored"}")
                }
            }
            Text(when (inspection.status) {
                SportIdentOwnerDataStatus.READ -> "Owner information read successfully."
                SportIdentOwnerDataStatus.NOT_SUPPORTED -> "This card type does not store owner information."
                SportIdentOwnerDataStatus.INCOMPLETE -> "Owner information could not be read completely."
                SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING ->
                    "The card uses an unsupported name encoding (character set ${inspection.characterSet})."
            })
        }
    }
}

private fun desktopCardInspectionService() = DesktopSportIdentCardInspectionService(
    DesktopSportIdentReadoutService(
        portSelector = DesktopSportIdentPortSelector(discoverySettings = DesktopAppSettingsPreferences),
        readCard = { DesktopSportIdentCardBlockReader(includeOwnerData = true)
            .readFirstSupportedCardAfterInsertOnOpenPort(it) }
    )
)
