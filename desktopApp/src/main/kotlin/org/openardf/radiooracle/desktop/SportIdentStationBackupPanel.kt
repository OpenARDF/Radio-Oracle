/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentPortSelector
import org.openardf.radiooracle.desktop.usb.DesktopSportIdentStationBackupService
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupRecord
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupSnapshot

/** Reads and searches the non-destructive punch backup stored by a coupled field station. */
@Composable
internal fun SportIdentStationBackupPanel(
    isReaderConnected: Boolean,
    isStationBusy: Boolean,
    siPortMutex: Mutex
) {
    var snapshot by remember { mutableStateOf<SportIdentStationBackupSnapshot?>(null) }
    var filterText by remember { mutableStateOf("") }
    var statusText by remember {
        mutableStateOf(
            "Couple the field station to the download station. " +
                "Read History wakes it if needed and does not clear or change it."
        )
    }
    var isReading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canRead = !isStationBusy && !isReading && isReaderConnected
    val cardFilter = filterText.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
    val matchingRecords = snapshot?.records.orEmpty().let { records ->
        when {
            filterText.isBlank() -> records
            cardFilter == null -> emptyList()
            else -> records.filter { it.cardNumber == cardFilter }
        }
    }
    val recordLimit = if (filterText.isBlank()) 200 else 1_000
    val visibleRecords = matchingRecords.takeLast(recordLimit).asReversed()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Field Station Punch History",
            color = DesktopPalette.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(statusText, color = DesktopPalette.Disconnected, fontSize = 13.sp)
        TextField(
            value = filterText,
            onValueChange = { value -> filterText = value.filter(Char::isDigit) },
            label = { Text("SI-Card number (optional)") },
            singleLine = true,
            enabled = !isReading,
            modifier = Modifier.widthIn(min = 260.dp, max = 420.dp)
        )
        Button(
            onClick = {
                isReading = true
                snapshot = null
                statusText = "Reading station punch history…"
                scope.launch {
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) {
                            siPortMutex.withLock {
                                desktopSportIdentStationBackupService().readBackup()
                            }
                        }
                    }
                    outcome.onSuccess { result ->
                        snapshot = result
                        statusText = sportIdentBackupSummary(result)
                    }.onFailure { error ->
                        statusText = "Punch-history read failed: ${error.message ?: error::class.simpleName}"
                    }
                    isReading = false
                }
            },
            enabled = canRead
        ) {
            Text(if (isReading) "Reading" else "Read History")
        }
        if (filterText.isNotBlank() && cardFilter == null) {
            Text("Enter a valid numeric SI-Card number.", color = DesktopPalette.Error, fontSize = 13.sp)
        } else if (snapshot != null && matchingRecords.isEmpty()) {
            Text(
                if (filterText.isBlank()) "No punch records were found." else "No records for SI-Card $filterText.",
                color = DesktopPalette.Disconnected,
                fontSize = 13.sp
            )
        } else if (visibleRecords.isNotEmpty()) {
            if (visibleRecords.size < matchingRecords.size) {
                Text(
                    "Showing the newest ${visibleRecords.size} of ${matchingRecords.size} matching records.",
                    color = DesktopPalette.Disconnected,
                    fontSize = 12.sp
                )
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("SI-Card   Date/time or punch error", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    visibleRecords.forEach { record ->
                        Text(record.desktopDisplayText(), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun desktopSportIdentStationBackupService(): DesktopSportIdentStationBackupService =
    DesktopSportIdentStationBackupService(portSelector = DesktopSportIdentPortSelector())

private fun sportIdentBackupSummary(snapshot: SportIdentStationBackupSnapshot): String {
    val stationCode = snapshot.stationInfo.stationCodeNumber?.let { ", SI code $it" }.orEmpty()
    val overflow = if (snapshot.metadata.overflowed) "; ring buffer has wrapped" else ""
    val unreadable = snapshot.unreadableRecordAddresses.takeIf { it.isNotEmpty() }
        ?.let { "; ${it.size} unreadable record(s)" }
        .orEmpty()
    return "Station ${snapshot.stationInfo.serialNumber}$stationCode: " +
        "${snapshot.records.size} punch record(s)$overflow$unreadable."
}

private fun SportIdentStationBackupRecord.desktopDisplayText(): String {
    val dateText = recordedDate?.toString()
        ?: dayOfWeek?.name?.take(3)
        ?: "Unknown date"
    val statusText = errorLabel?.let { label ->
        "$halfDay $label: ${errorDescription ?: "punch failed"}"
    } ?: recordedTime?.toString().orEmpty()
    return "$cardNumber  $dateText $statusText"
}
