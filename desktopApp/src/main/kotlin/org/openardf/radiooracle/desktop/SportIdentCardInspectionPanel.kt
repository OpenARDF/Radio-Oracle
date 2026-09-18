package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Text
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
import org.openardf.radiooracle.shared.sportident.SportIdentCardOwnerInspection
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus

@Composable
internal fun SportIdentCardInspectionPanel(
    isReaderConnected: Boolean,
    isStationBusy: Boolean,
    siPortMutex: Mutex
) {
    var inspection by remember { mutableStateOf<SportIdentCardOwnerInspection?>(null) }
    var isReading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SI Card Owner Information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Choose Read Card, then insert the card into the download station. Keep it seated until the read finishes.")
        Text("This tool reads owner information without changing the card or adding a race result.", fontSize = 13.sp)
        if (isStationBusy) Text("Stop the active SI readout before reading owner information.")
        else if (!isReaderConnected) Text("Connect a SPORTident download station to read a card.")
        Button(enabled = isReaderConnected && !isStationBusy && !isReading, onClick = {
            isReading = true
            inspection = null
            failed = false
            status = "Waiting for an SI card; keep it seated until the read finishes…"
            scope.launch {
                try {
                    inspection = withContext(Dispatchers.IO) {
                        siPortMutex.withLock { desktopCardInspectionService().inspectOne() }
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
        inspection?.let { SportIdentCardOwnerDetails(it) }
        inspection?.takeIf { it.family == SportIdentCardFamily.SI8 && it.status == SportIdentOwnerDataStatus.READ }
            ?.let { SportIdentSi8OwnerNameEditor(it) }
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
