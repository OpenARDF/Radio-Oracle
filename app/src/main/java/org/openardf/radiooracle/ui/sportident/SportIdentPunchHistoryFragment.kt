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
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupRecord
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupSnapshot

/** Read-only station-backup download, search, and presentation. */
class SportIdentPunchHistoryFragment :
    SportIdentToolFragment(R.layout.fragment_sportident_punch_history) {
    private lateinit var filterView: EditText
    private lateinit var readButton: Button
    private lateinit var showButton: Button
    private lateinit var summaryView: TextView
    private lateinit var recordsView: TextView
    private lateinit var progressView: ProgressBar

    private var operationJob: Job? = null
    private var backupSnapshot: SportIdentStationBackupSnapshot? = null
    private var statusNormalColor: Int = 0
    private var statusErrorColor: Int = 0

    override val navigationBlocked: Boolean
        get() = operationJob?.isActive == true

    override fun onToolViewCreated(view: View, savedInstanceState: Bundle?) {
        configureToolbar(
            view.findViewById<Toolbar>(R.id.sportident_punch_history_toolbar),
            R.string.sportident_punch_history_tools_title
        )
        filterView = view.findViewById(R.id.sportident_backup_filter)
        readButton = view.findViewById(R.id.sportident_backup_read)
        showButton = view.findViewById(R.id.sportident_backup_show)
        summaryView = view.findViewById(R.id.sportident_backup_summary)
        recordsView = view.findViewById(R.id.sportident_backup_records)
        progressView = view.findViewById(R.id.sportident_backup_progress)
        statusNormalColor = summaryView.currentTextColor
        statusErrorColor = MaterialColors.getColor(summaryView, android.R.attr.colorError)

        readButton.setOnClickListener { readStationBackup() }
        showButton.setOnClickListener { showBackupResults() }
        updateButtons(isBusy = false)
    }

    override fun onSportIdentBinderChanged(binder: SIReaderService.LocalBinder?) {
        if (binder == null) {
            showStatus(R.string.sportident_time_sync_disconnected, isError = true)
        }
        updateButtons(isBusy = operationJob?.isActive == true)
    }

    private fun readStationBackup() {
        val activeBinder = sportIdentBinder ?: run {
            showStatus(R.string.sportident_time_sync_disconnected, isError = true)
            return
        }
        operationJob?.cancel()
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            backupSnapshot = null
            recordsView.text = ""
            showStatus(R.string.sportident_backup_reading)
            updateButtons(isBusy = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    activeBinder.readStationBackup { completed, total ->
                        if (
                            total > 0 &&
                            (completed == total || completed % PROGRESS_UPDATE_INTERVAL == 0)
                        ) {
                            summaryView.post {
                                showStatus(
                                    getString(
                                        R.string.sportident_backup_progress,
                                        completed,
                                        total
                                    )
                                )
                            }
                        }
                    }
                }
            }.onSuccess { snapshot ->
                backupSnapshot = snapshot
                showBackupResults()
                Toast.makeText(
                    requireContext(),
                    resources.getQuantityString(
                        R.plurals.sportident_backup_complete,
                        snapshot.records.size,
                        snapshot.records.size
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                val message = getString(
                    R.string.sportident_backup_failed,
                    error.message ?: error::class.simpleName
                )
                showStatus(message, isError = true)
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
            operationJob = null
            updateButtons(isBusy = false)
        }
    }

    private fun showBackupResults() {
        val snapshot = backupSnapshot ?: return
        val filterText = filterView.text?.toString()?.trim().orEmpty()
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
        val stationCode = snapshot.stationInfo.stationCodeNumber
            ?.let { getString(R.string.sportident_backup_station_code, it) }
            .orEmpty()
        val overflowText = if (snapshot.metadata.overflowed) {
            getString(R.string.sportident_backup_overflowed)
        } else {
            ""
        }
        val unreadableText = snapshot.unreadableRecordAddresses.takeIf { it.isNotEmpty() }
            ?.let {
                resources.getQuantityString(
                    R.plurals.sportident_backup_unreadable,
                    it.size,
                    it.size
                )
            }
            .orEmpty()
        val displayText = when {
            filterText.isNotEmpty() && cardNumberFilter == null ->
                getString(R.string.sportident_backup_invalid_card)
            matchingRecords.isEmpty() && filterText.isNotEmpty() ->
                getString(R.string.sportident_backup_no_card_records, filterText)
            matchingRecords.isEmpty() -> getString(R.string.sportident_backup_no_records)
            else -> visibleRecords.joinToString("\n") { it.displayText() }
        }
        val shownText = if (visibleRecords.size < matchingRecords.size) {
            getString(R.string.sportident_backup_showing_newest, visibleRecords.size)
        } else {
            ""
        }
        showStatus(
            resources.getQuantityString(
                R.plurals.sportident_backup_summary,
                snapshot.records.size,
                snapshot.stationInfo.serialNumber,
                stationCode,
                snapshot.records.size,
                overflowText,
                unreadableText,
                shownText
            )
        )
        recordsView.text = displayText
    }

    private fun updateButtons(isBusy: Boolean) {
        progressView.visibility = if (isBusy) View.VISIBLE else View.GONE
        readButton.isEnabled = sportIdentBinder != null && !isBusy
        readButton.setText(
            if (isBusy) {
                R.string.sportident_backup_reading_button
            } else {
                R.string.sportident_backup_read
            }
        )
        showButton.isEnabled = backupSnapshot != null && !isBusy
        filterView.isEnabled = !isBusy
    }

    private fun showStatus(@StringRes stringResource: Int, isError: Boolean = false) {
        showStatus(getString(stringResource), isError)
    }

    private fun showStatus(text: CharSequence, isError: Boolean = false) {
        summaryView.setTextColor(if (isError) statusErrorColor else statusNormalColor)
        summaryView.text = text
        if (isError) SoundProcessor.makeErrorSound(requireContext())
    }

    private fun SportIdentStationBackupRecord.displayText(): String {
        val dateText = recordedDate?.format(BACKUP_DATE_FORMAT)
            ?: dayOfWeek?.name?.take(3)
            ?: getString(R.string.sportident_backup_unknown_date)
        val statusText = errorLabel?.let { label ->
            getString(
                R.string.sportident_backup_error_record,
                halfDay,
                label,
                errorDescription ?: getString(R.string.sportident_backup_punch_failed)
            )
        } ?: recordedTime?.format(BACKUP_TIME_FORMAT).orEmpty()
        return "$cardNumber  $dateText $statusText"
    }

    private companion object {
        val BACKUP_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val BACKUP_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
        const val MAX_UNFILTERED_RECORDS = 200
        const val MAX_FILTERED_RECORDS = 1_000
        const val PROGRESS_UPDATE_INTERVAL = 25
    }
}
