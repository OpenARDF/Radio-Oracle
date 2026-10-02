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

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
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
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncInspection
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentTimeSyncResult
import org.openardf.radiooracle.backend.sportident.SIReaderService

/** Station inspection, time synchronization, diagnostics, and sleep controls. */
class SportIdentStationFragment :
    SportIdentToolFragment(R.layout.fragment_sportident_station) {
    private lateinit var computerTimeView: TextView
    private lateinit var stationStatusView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var inspectButton: Button
    private lateinit var syncButton: Button
    private lateinit var sleepButton: Button
    private lateinit var sleepAfterCheckBox: MaterialCheckBox

    private var operationJob: Job? = null
    private var clockJob: Job? = null
    private var lastInspection: AndroidSportIdentTimeSyncInspection? = null
    private var statusNormalColor: Int = 0
    private var statusErrorColor: Int = 0

    override val navigationBlocked: Boolean
        get() = operationJob?.isActive == true

    override fun onToolViewCreated(view: View, savedInstanceState: Bundle?) {
        configureToolbar(
            view.findViewById<Toolbar>(R.id.sportident_station_toolbar),
            R.string.sportident_station_tools_title
        )
        computerTimeView = view.findViewById(R.id.sportident_time_sync_computer_time)
        stationStatusView = view.findViewById(R.id.sportident_time_sync_station_status)
        progressView = view.findViewById(R.id.sportident_time_sync_progress)
        inspectButton = view.findViewById(R.id.sportident_time_sync_inspect)
        syncButton = view.findViewById(R.id.sportident_time_sync_run)
        sleepButton = view.findViewById(R.id.sportident_station_sleep)
        sleepAfterCheckBox = view.findViewById(R.id.sportident_time_sync_sleep_after)
        statusNormalColor = stationStatusView.currentTextColor
        statusErrorColor = MaterialColors.getColor(
            stationStatusView,
            android.R.attr.colorError
        )

        inspectButton.setOnClickListener { inspectStation() }
        syncButton.setOnClickListener { confirmTimeSync() }
        sleepButton.setOnClickListener { confirmStationSleep() }
    }

    override fun onStart() {
        super.onStart()
        startComputerClock()
    }

    override fun onStop() {
        clockJob?.cancel()
        clockJob = null
        super.onStop()
    }

    override fun onSportIdentBinderChanged(binder: SIReaderService.LocalBinder?) {
        if (binder == null) {
            lastInspection = null
            showStatus(R.string.sportident_time_sync_disconnected, isError = true)
            updateButtons(isBusy = operationJob?.isActive == true)
        } else if (operationJob?.isActive != true) {
            updateButtons(isBusy = false)
            inspectStation()
        }
    }

    private fun startComputerClock() {
        clockJob?.cancel()
        clockJob = viewLifecycleOwner.lifecycleScope.launch {
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
        val activeBinder = sportIdentBinder ?: run {
            showStatus(R.string.sportident_time_sync_disconnected, isError = true)
            return
        }
        operationJob?.cancel()
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            lastInspection = null
            showStatus(R.string.sportident_time_sync_inspecting)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) { activeBinder.inspectTimeSyncStation() }
            }.onSuccess { inspection ->
                lastInspection = inspection
                showStatus(inspection.summaryText())
            }.onFailure { error ->
                showStatus(
                    getString(
                        R.string.sportident_time_sync_inspection_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            operationJob = null
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
        val activeBinder = sportIdentBinder ?: return
        val inspectedStationSerialNumber = lastInspection?.stationInfo?.serialNumber ?: return
        operationJob?.cancel()
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            var inspectAgain = false
            lastInspection = null
            showStatus(R.string.sportident_time_sync_syncing)
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
                showStatus(result.successText())
                inspectAgain = !sleepAfterCheckBox.isChecked
            }.onFailure { error ->
                showStatus(
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

    private fun confirmStationSleep() {
        val serialNumber = lastInspection?.stationInfo?.serialNumber ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sportident_station_sleep_confirm_title)
            .setMessage(
                getString(
                    R.string.sportident_station_sleep_confirm_message,
                    serialNumber
                )
            )
            .setNegativeButton(R.string.general_cancel, null)
            .setPositiveButton(R.string.sportident_station_sleep) { _, _ ->
                runStationSleep(serialNumber)
            }
            .show()
    }

    private fun runStationSleep(expectedStationSerialNumber: Int) {
        val activeBinder = sportIdentBinder ?: return
        operationJob?.cancel()
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    activeBinder.sleepStation(
                        writeEnabled = true,
                        expectedStationSerialNumber = expectedStationSerialNumber
                    )
                }
            }.onSuccess { result ->
                showStatus(result.message, isError = !result.confirmed)
                if (result.confirmed) lastInspection = null
            }.onFailure { error ->
                showStatus(
                    getString(
                        R.string.sportident_station_sleep_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            operationJob = null
            updateButtons(isBusy = false)
        }
    }

    private fun updateButtons(isBusy: Boolean) {
        progressView.visibility = if (isBusy) View.VISIBLE else View.GONE
        inspectButton.isEnabled = sportIdentBinder != null && !isBusy
        syncButton.isEnabled = sportIdentBinder != null && lastInspection != null && !isBusy
        sleepButton.isEnabled = sportIdentBinder != null && lastInspection != null && !isBusy
        sleepAfterCheckBox.isEnabled = !isBusy
    }

    private fun showStatus(@StringRes stringResource: Int, isError: Boolean = false) {
        showStatus(getString(stringResource), isError)
    }

    private fun showStatus(text: CharSequence, isError: Boolean = false) {
        stationStatusView.setTextColor(if (isError) statusErrorColor else statusNormalColor)
        stationStatusView.text = text
        if (isError) SoundProcessor.makeErrorSound(requireContext())
    }

    private fun AndroidSportIdentTimeSyncInspection.summaryText(): String {
        val stationCode = stationInfo.stationCodeNumber
            ?.let { getString(R.string.sportident_time_sync_station_code, it) }
            .orEmpty()
        val summary = getString(
            R.string.sportident_time_sync_station_summary,
            stationInfo.serialNumber,
            stationCode,
            stationTime.format(DISPLAY_TIME_FORMAT),
            formatDelta(stationMinusComputerMillis)
        )
        val details = listOfNotNull(
            stationInfo.modelName?.let { "model $it" },
            stationInfo.firmwareVersion?.let { "firmware $it" },
            stationInfo.batteryVoltage?.let {
                String.format(Locale.US, "battery %.2f V", it)
            },
            stationInfo.memorySizeKb?.let { "memory $it KB" },
            stationInfo.activeTimeMinutes?.let { "active $it min" },
            stationInfo.protocolByte?.let {
                "protocol 0x${it.toString(16).uppercase(Locale.US).padStart(2, '0')}"
            },
            stationInfo.stationModeLabel?.let { "mode $it" },
            if (stationInfo.extendedMode) "extended mode" else "legacy mode"
        ).joinToString(", ")
        return "$summary\n${getString(R.string.sportident_station_diagnostics, details)}"
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
        if (deltaMillis == null) {
            return getString(R.string.sportident_time_sync_readback_unavailable)
        }
        if (deltaMillis == 0L) return getString(R.string.sportident_time_sync_aligned)
        val duration = if (abs(deltaMillis) < 1_000L) {
            "${abs(deltaMillis)} ms"
        } else {
            String.format(Locale.getDefault(), "%.3f s", abs(deltaMillis) / 1_000.0)
        }
        return getString(
            if (deltaMillis > 0) {
                R.string.sportident_time_sync_ahead
            } else {
                R.string.sportident_time_sync_behind
            },
            duration
        )
    }

    private companion object {
        val DISPLAY_TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
