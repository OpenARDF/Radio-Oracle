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
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
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
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncInspection
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncResult
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.device.SIReaderStatus

/** Android entry point for read-only station inspection and confirmed SPORTident time sync. */
class SportIdentTimeSyncDialogFragment : DialogFragment() {
    private lateinit var computerTimeView: TextView
    private lateinit var stationStatusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var inspectButton: Button
    private lateinit var syncButton: Button
    private lateinit var sleepAfterCheckBox: MaterialCheckBox

    private var binder: SIReaderService.LocalBinder? = null
    private var isBound = false
    private var operationJob: Job? = null
    private var clockJob: Job? = null
    private var lastInspection: AndroidSportIdentTimeSyncInspection? = null
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
            stationStatusView.setText(R.string.sportident_time_sync_disconnected)
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

        inspectButton.setOnClickListener { inspectStation() }
        syncButton.setOnClickListener { confirmTimeSync() }
        dataProcessor.currentState.observe(this) { state ->
            if (state.siReaderState.status == SIReaderStatus.DISCONNECTED) {
                lastInspection = null
                stationStatusView.setText(R.string.sportident_time_sync_disconnected)
                updateButtons(isBusy = operationJob?.isActive == true)
            } else if (binder == null && !isBound && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                bindReaderService()
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.sportident_time_sync_title)
            .setView(view)
            .setPositiveButton(R.string.general_close, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
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
        stationStatusView.setText(R.string.sportident_time_sync_connecting)
        val state = dataProcessor.currentState.value?.siReaderState
        if (state == null || state.status == SIReaderStatus.DISCONNECTED) {
            stationStatusView.setText(R.string.sportident_time_sync_disconnected)
            updateButtons(isBusy = false)
            return
        }
        isBound = requireContext().bindService(
            Intent(requireContext(), SIReaderService::class.java),
            serviceConnection,
            0
        )
        if (!isBound) {
            stationStatusView.setText(R.string.sportident_time_sync_disconnected)
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
            stationStatusView.setText(R.string.sportident_time_sync_disconnected)
            return
        }
        operationJob?.cancel()
        operationJob = lifecycleScope.launch {
            lastInspection = null
            stationStatusView.setText(R.string.sportident_time_sync_inspecting)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) { activeBinder.inspectTimeSyncStation() }
            }.onSuccess { inspection ->
                lastInspection = inspection
                stationStatusView.text = inspection.summaryText()
            }.onFailure { error ->
                stationStatusView.text = getString(
                    R.string.sportident_time_sync_inspection_failed,
                    error.message ?: error::class.simpleName
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
            stationStatusView.setText(R.string.sportident_time_sync_syncing)
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
                stationStatusView.text = result.successText()
                inspectAgain = !sleepAfterCheckBox.isChecked
            }.onFailure { error ->
                stationStatusView.text = getString(
                    R.string.sportident_time_sync_failed,
                    error.message ?: error::class.simpleName
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
    }
}
