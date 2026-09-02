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

package org.openardf.radiooracle.ui.sportident

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncInspection
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncResult
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.device.SIReaderStatus
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupRecord
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupSnapshot

/** Android entry point for read-only station inspection and confirmed SPORTident time sync. */
class SportIdentTimeSyncDialogFragment : DialogFragment() {
    private lateinit var computerTimeView: TextView
    private lateinit var stationStatusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var inspectButton: Button
    private lateinit var syncButton: Button
    private lateinit var sleepAfterCheckBox: MaterialCheckBox
    private lateinit var backupFilterView: EditText
    private lateinit var backupReadButton: Button
    private lateinit var backupShowButton: Button
    private lateinit var backupSummaryView: TextView
    private lateinit var backupRecordsView: TextView

    private var binder: SIReaderService.LocalBinder? = null
    private var isBound = false
    private var operationJob: Job? = null
    private var clockJob: Job? = null
    private var lastInspection: AndroidSportIdentTimeSyncInspection? = null
    private var backupSnapshot: SportIdentStationBackupSnapshot? = null
    private var stationStatusNormalColor: Int = 0
    private var backupStatusNormalColor: Int = 0
    private var statusErrorColor: Int = 0
    private val dataProcessor by lazy { DataProcessor.get() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? SIReaderService.LocalBinder
            updateButtons(isBusy = false)
            if (binder != null) inspectStation()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            lastInspection = null
            showStationStatus(R.string.sportident_time_sync_disconnected, isError = true)
            updateButtons(isBusy = false)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_sportident_time_sync, null)
        computerTimeView = view.findViewById(R.id.sportident_time_sync_computer_time)
        stationStatusView = view.findViewById(R.id.sportident_time_sync_station_status)
        progressView = view.findViewById(R.id.sportident_time_sync_progress)
        inspectButton = view.findViewById(R.id.sportident_time_sync_inspect)
        syncButton = view.findViewById(R.id.sportident_time_sync_run)
        sleepAfterCheckBox = view.findViewById(R.id.sportident_time_sync_sleep_after)
        backupFilterView = view.findViewById(R.id.sportident_backup_filter)
        backupReadButton = view.findViewById(R.id.sportident_backup_read)
        backupShowButton = view.findViewById(R.id.sportident_backup_show)
        backupSummaryView = view.findViewById(R.id.sportident_backup_summary)
        backupRecordsView = view.findViewById(R.id.sportident_backup_records)
        stationStatusNormalColor = stationStatusView.currentTextColor
        backupStatusNormalColor = backupSummaryView.currentTextColor
        statusErrorColor = MaterialColors.getColor(
            stationStatusView,
            android.R.attr.colorError
        )

        inspectButton.setOnClickListener { inspectStation() }
        syncButton.setOnClickListener { confirmTimeSync() }
        backupReadButton.setOnClickListener { readStationBackup() }
        backupShowButton.setOnClickListener { showBackupResults() }
        dataProcessor.currentState.observe(this) { state ->
            if (state.siReaderState.status == SIReaderStatus.DISCONNECTED) {
                lastInspection = null
                showStationStatus(R.string.sportident_time_sync_disconnected, isError = true)
                updateButtons(isBusy = operationJob?.isActive == true)
            } else if (binder == null && !isBound && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                bindReaderService()
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.sportident_tools_title)
            .setView(view)
            .setPositiveButton(R.string.general_close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.let { alertDialog ->
            alertDialog.findViewById<TextView>(resources.getIdentifier("alertTitle", "id", "android"))
                ?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        startComputerClock()
        bindReaderService()
    }

    override fun onStop() {
        clockJob?.cancel()
        clockJob = null
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
        binder = null
        super.onStop()
    }

    private fun bindReaderService() {
        showStationStatus(R.string.sportident_time_sync_connecting)
        val state = dataProcessor.currentState.value?.siReaderState
        if (state == null || state.status == SIReaderStatus.DISCONNECTED) {
            showStationStatus(R.string.sportident_time_sync_disconnected, isError = true)
            updateButtons(isBusy = false)
            return
        }
        isBound = requireContext().bindService(
            Intent(requireContext(), SIReaderService::class.java),
            serviceConnection,
            0
        )
        if (!isBound) {
            showStationStatus(R.string.sportident_time_sync_disconnected, isError = true)
            updateButtons(isBusy = false)
        }
    }

    private fun startComputerClock() {
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            while (isActive) {
                computerTimeView.text = getString(
                    R.string.sportident_time_sync_computer_time,
                    LocalDateTime.now().withNano(0).format(DISPLAY_TIME_FORMAT)
                )
                delay(1_000L)
            }
        }
    }

    private fun inspectStation() {
        val activeBinder = binder ?: run {
            showStationStatus(R.string.sportident_time_sync_disconnected, isError = true)
            return
        }
        operationJob?.cancel()
        operationJob = lifecycleScope.launch {
            lastInspection = null
            showStationStatus(R.string.sportident_time_sync_inspecting)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) { activeBinder.inspectTimeSyncStation() }
            }.onSuccess { inspection ->
                lastInspection = inspection
                showStationStatus(inspection.summaryText())
            }.onFailure { error ->
                showStationStatus(
                    getString(
                        R.string.sportident_time_sync_inspection_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            updateButtons(isBusy = false)
        }
    }

    private fun confirmTimeSync() {
        val inspection = lastInspection ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sportident_time_sync_confirm_title)
            .setMessage(
                getString(
                    R.string.sportident_time_sync_confirm_message,
                    inspection.stationInfo.serialNumber
                )
            )
            .setNegativeButton(R.string.general_cancel, null)
            .setPositiveButton(R.string.sportident_time_sync_sync) { _, _ -> runTimeSync() }
            .show()
    }

    private fun runTimeSync() {
        val activeBinder = binder ?: return
        val inspectedStationSerialNumber = lastInspection?.stationInfo?.serialNumber ?: return
        operationJob?.cancel()
        operationJob = lifecycleScope.launch {
            var inspectAgain = false
            lastInspection = null
            showStationStatus(R.string.sportident_time_sync_syncing)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    activeBinder.syncTime(
                        writeEnabled = true,
                        putStationToSleepAfterSync = sleepAfterCheckBox.isChecked,
                        expectedStationSerialNumber = inspectedStationSerialNumber
                    )
                }
            }.onSuccess { result ->
                showStationStatus(result.successText())
                inspectAgain = !sleepAfterCheckBox.isChecked
            }.onFailure { error ->
                showStationStatus(
                    getString(
                        R.string.sportident_time_sync_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            operationJob = null
            updateButtons(isBusy = false)
            if (inspectAgain) inspectStation()
        }
    }

    private fun updateButtons(isBusy: Boolean) {
        progressView.visibility = if (isBusy) View.VISIBLE else View.GONE
        inspectButton.isEnabled = binder != null && !isBusy
        syncButton.isEnabled = binder != null && lastInspection != null && !isBusy
        sleepAfterCheckBox.isEnabled = !isBusy
        backupReadButton.isEnabled = binder != null && !isBusy
        backupReadButton.setText(
            if (isBusy) R.string.sportident_backup_reading_button else R.string.sportident_backup_read
        )
        backupShowButton.isEnabled = backupSnapshot != null && !isBusy
        backupFilterView.isEnabled = !isBusy
    }

    private fun readStationBackup() {
        val activeBinder = binder ?: run {
            showBackupStatus(R.string.sportident_time_sync_disconnected, isError = true)
            Toast.makeText(requireContext(), R.string.sportident_time_sync_disconnected, Toast.LENGTH_LONG).show()
            return
        }
        operationJob?.cancel()
        operationJob = lifecycleScope.launch {
            backupSnapshot = null
            backupRecordsView.text = ""
            showBackupStatus(R.string.sportident_backup_reading)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    activeBinder.readStationBackup { completed, total ->
                        if (total > 0 && (completed == total || completed % PROGRESS_UPDATE_INTERVAL == 0)) {
                            backupSummaryView.post {
                                showBackupStatus("Reading station punch history: $completed / $total records…")
                            }
                        }
                    }
                }
            }.onSuccess { snapshot ->
                backupSnapshot = snapshot
                showBackupResults()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.sportident_backup_complete, snapshot.records.size),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                val message = getString(
                    R.string.sportident_backup_failed,
                    error.message ?: error::class.simpleName
                )
                showBackupStatus(message, isError = true)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            operationJob = null
            updateButtons(isBusy = false)
        }
    }

    private fun showBackupResults() {
        val snapshot = backupSnapshot ?: return
        val filterText = backupFilterView.text?.toString()?.trim().orEmpty()
        val cardNumberFilter = filterText.toIntOrNull()
        val matchingRecords = if (filterText.isEmpty()) {
            snapshot.records
        } else if (cardNumberFilter == null) {
            emptyList()
        } else {
            snapshot.records.filter { it.cardNumber == cardNumberFilter }
        }
        val visibleRecords = if (filterText.isEmpty()) {
            matchingRecords.takeLast(MAX_UNFILTERED_RECORDS)
        } else {
            matchingRecords.takeLast(MAX_FILTERED_RECORDS)
        }.asReversed()
        val stationCode = snapshot.stationInfo.stationCodeNumber?.let { ", SI code $it" }.orEmpty()
        val overflowText = if (snapshot.metadata.overflowed) "; ring buffer has wrapped" else ""
        val unreadableText = snapshot.unreadableRecordAddresses.takeIf { it.isNotEmpty() }
            ?.let { "; ${it.size} unreadable record(s)" }
            .orEmpty()
        val displayText = when {
            filterText.isNotEmpty() && cardNumberFilter == null -> "Enter a valid numeric SI-Card number."
            matchingRecords.isEmpty() && filterText.isNotEmpty() -> "No records for SI-Card $filterText."
            matchingRecords.isEmpty() -> "No punch records were found."
            else -> visibleRecords.joinToString("\n") { it.displayText() }
        }
        val shownText = if (visibleRecords.size < matchingRecords.size) {
            "; showing newest ${visibleRecords.size}"
        } else {
            ""
        }
        showBackupStatus(
            "Station ${snapshot.stationInfo.serialNumber}$stationCode: ${snapshot.records.size} record(s)" +
                overflowText + unreadableText + shownText + "."
        )
        backupRecordsView.text = displayText
    }

    private fun showStationStatus(@StringRes stringResource: Int, isError: Boolean = false) {
        showStationStatus(getString(stringResource), isError)
    }

    private fun showStationStatus(text: CharSequence, isError: Boolean = false) {
        stationStatusView.setTextColor(if (isError) statusErrorColor else stationStatusNormalColor)
        stationStatusView.text = text
        if (isError) SoundProcessor.makeErrorSound(requireContext())
    }

    private fun showBackupStatus(@StringRes stringResource: Int, isError: Boolean = false) {
        showBackupStatus(getString(stringResource), isError)
    }

    private fun showBackupStatus(text: CharSequence, isError: Boolean = false) {
        backupSummaryView.setTextColor(if (isError) statusErrorColor else backupStatusNormalColor)
        backupSummaryView.text = text
        if (isError) SoundProcessor.makeErrorSound(requireContext())
    }

    private fun SportIdentStationBackupRecord.displayText(): String {
        val dateText = recordedDate?.format(BACKUP_DATE_FORMAT)
            ?: dayOfWeek?.name?.take(3)
            ?: "Unknown date"
        val statusText = errorLabel?.let { label ->
            "$halfDay $label: ${errorDescription ?: "punch failed"}"
        } ?: recordedTime?.format(BACKUP_TIME_FORMAT).orEmpty()
        return "$cardNumber  $dateText $statusText"
    }

    private fun AndroidSportIdentTimeSyncInspection.summaryText(): String {
        val stationCode = stationInfo.stationCodeNumber
            ?.let { getString(R.string.sportident_time_sync_station_code, it) }
            .orEmpty()
        return getString(
            R.string.sportident_time_sync_station_summary,
            stationInfo.serialNumber,
            stationCode,
            stationTime.format(DISPLAY_TIME_FORMAT),
            formatDelta(stationMinusComputerMillis)
        )
    }

    private fun AndroidSportIdentTimeSyncResult.successText(): String {
        val attemptsText = if (attempts > 1) {
            getString(R.string.sportident_time_sync_attempts, attempts)
        } else {
            ""
        }
        val powerText = stationPowerStateWrite?.let { " ${it.message}" }.orEmpty()
        return getString(
            R.string.sportident_time_sync_success,
            stationInfo.serialNumber,
            sourceTime.format(DISPLAY_TIME_FORMAT),
            formatDelta(confirmedStationMinusComputerMillis),
            attemptsText + powerText
        )
    }

    private fun formatDelta(deltaMillis: Long?): String {
        if (deltaMillis == null) return getString(R.string.sportident_time_sync_readback_unavailable)
        if (deltaMillis == 0L) return "aligned with the Android device"
        val duration = if (abs(deltaMillis) < 1_000L) {
            "${abs(deltaMillis)} ms"
        } else {
            String.format(Locale.getDefault(), "%.3f s", abs(deltaMillis) / 1_000.0)
        }
        val direction = if (deltaMillis > 0) "ahead of" else "behind"
        return "$duration $direction the Android device"
    }

    companion object {
        const val TAG = "SportIdentTimeSyncDialog"
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        private const val MAX_UNFILTERED_RECORDS = 200
        private const val MAX_FILTERED_RECORDS = 1_000
        private const val PROGRESS_UPDATE_INTERVAL = 25
    }
}
