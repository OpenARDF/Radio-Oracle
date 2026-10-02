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

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentCardInspection
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentCardPresenceState
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentOwnerRecoveryState
import org.openardf.radiooracle.backend.sportident.AndroidSportIdentOwnerWriteInstruction
import org.openardf.radiooracle.backend.sportident.SIReaderService
import org.openardf.radiooracle.shared.sportident.SportIdentCardFamily
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerDataStatus
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameWriteRequest

/** Read-only owner inspection plus the guarded SI-Card8 owner-name transaction. */
class SportIdentCardFragment : SportIdentToolFragment(R.layout.fragment_sportident_card) {
    private lateinit var statusView: TextView
    private lateinit var presenceDotView: View
    private lateinit var presenceStatusView: TextView
    private lateinit var actionView: TextView
    private lateinit var progressView: ProgressBar
    private lateinit var writeButton: Button
    private lateinit var acceptRecoveryButton: Button
    private lateinit var firstNameView: EditText
    private lateinit var lastNameView: EditText

    private var operationJob: Job? = null
    private var monitorJob: Job? = null
    private var cardInspection: AndroidSportIdentCardInspection? = null
    private var cardPresenceState = AndroidSportIdentCardPresenceState.NOT_PRESENT
    private var ownerRecoveryState: AndroidSportIdentOwnerRecoveryState =
        AndroidSportIdentOwnerRecoveryState.Empty
    private var statusNormalColor: Int = 0
    private var statusErrorColor: Int = 0
    private var actionNormalColor: Int = 0

    override val navigationBlocked: Boolean
        get() = operationJob?.isActive == true

    override fun onToolViewCreated(view: View, savedInstanceState: Bundle?) {
        configureToolbar(
            view.findViewById<Toolbar>(R.id.sportident_card_toolbar),
            R.string.sportident_card_tools_title
        )
        statusView = view.findViewById(R.id.sportident_card_status)
        presenceDotView = view.findViewById(R.id.sportident_card_presence_dot)
        presenceStatusView = view.findViewById(R.id.sportident_card_presence_status)
        actionView = view.findViewById(R.id.sportident_card_action)
        progressView = view.findViewById(R.id.sportident_card_progress)
        writeButton = view.findViewById(R.id.sportident_card_write)
        acceptRecoveryButton = view.findViewById(R.id.sportident_card_accept_recovery)
        firstNameView = view.findViewById(R.id.sportident_card_first_name)
        lastNameView = view.findViewById(R.id.sportident_card_last_name)
        statusNormalColor = statusView.currentTextColor
        statusErrorColor = ContextCompat.getColor(requireContext(), R.color.red_error)
        actionNormalColor = actionView.currentTextColor

        writeButton.setOnClickListener { confirmOwnerNameWrite() }
        acceptRecoveryButton.setOnClickListener { acceptOwnerWriteRecovery() }
        showDisconnectedPresence()
        updateControls(isBusy = false)
    }

    override fun onSportIdentBinderChanged(binder: SIReaderService.LocalBinder?) {
        if (binder == null) {
            monitorJob?.cancel()
            monitorJob = null
            cardInspection = null
            showDisconnectedPresence()
            showStatus(R.string.sportident_time_sync_disconnected, isError = true)
        } else {
            refreshOwnerRecoveryState()
            showPreparingPresence()
            startCardMonitor()
        }
        updateControls(isBusy = operationJob?.isActive == true)
    }

    override fun onStop() {
        monitorJob?.cancel()
        monitorJob = null
        // A retained inspection is editable only during one continuously owned tool session.
        cardInspection = null
        super.onStop()
    }

    private fun startCardMonitor(initiallySeatedCardNumber: Int? = null) {
        val activeBinder = sportIdentBinder ?: return
        if (monitorJob?.isActive == true || operationJob?.isActive == true) return
        monitorJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeBinder.monitorCardInspections(
                        initiallySeatedCardNumber = initiallySeatedCardNumber,
                        onPresenceChanged = { state ->
                            postToCardView { handleCardPresence(state) }
                        },
                        onInspection = { inspection ->
                            postToCardView { showInspection(inspection) }
                        },
                        onReadError = { error ->
                            postToCardView {
                                cardInspection = null
                                showStatus(
                                    getString(
                                        R.string.sportident_card_read_failed,
                                        error.message ?: error::class.simpleName
                                    ),
                                    isError = true
                                )
                                updateControls(isBusy = false)
                            }
                        }
                    )
                }
            } catch (_: CancellationException) {
                // Leaving Card Tools or starting a guarded operation releases the station promptly.
            } catch (error: Throwable) {
                showStatus(
                    getString(
                        R.string.sportident_card_read_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            monitorJob = null
        }
    }

    private suspend fun stopCardMonitor() {
        monitorJob?.cancelAndJoin()
        monitorJob = null
    }

    private fun postToCardView(action: () -> Unit) {
        view?.post {
            if (view != null && isAdded) action()
        }
    }

    private fun showInspection(inspection: AndroidSportIdentCardInspection) {
        cardInspection = inspection
        firstNameView.setText(inspection.owner.holder?.firstName.orEmpty())
        lastNameView.setText(inspection.owner.holder?.lastName.orEmpty())
        showStatus(inspection.displayText())
        refreshOwnerRecoveryState()
        updateControls(isBusy = false)
    }

    private fun handleCardPresence(state: AndroidSportIdentCardPresenceState) {
        cardPresenceState = state
        if (state == AndroidSportIdentCardPresenceState.READING) {
            // A completed snapshot remains editable after removal, but a new
            // insertion invalidates it until that card has been read fully.
            cardInspection = null
        }
        showCardPresence(state)
        updateControls(isBusy = operationJob?.isActive == true)
    }

    private fun confirmOwnerNameWrite() {
        val inspection = cardInspection ?: return
        val request = runCatching {
            SportIdentOwnerNameProgramming.prepare(
                inspection.owner,
                inspection.stationInfo.serialNumber,
                firstNameView.text?.toString().orEmpty(),
                lastNameView.text?.toString().orEmpty(),
                // Complete before/after comparison supersedes the retained legacy consent field.
                acceptPossiblePunchLoss = true
            )
        }.getOrElse { error ->
            showStatus(
                error.message ?: getString(R.string.sportident_card_names_invalid),
                isError = true
            )
            return
        }
        val stored = listOf(request.expectedFirstName, request.expectedLastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifEmpty { getString(R.string.sportident_card_name_not_stored) }
        val replacement = listOf(request.firstName, request.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifEmpty { getString(R.string.sportident_card_name_not_stored) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sportident_card_write_confirm_title, request.cardNumber))
            .setMessage(
                getString(
                    R.string.sportident_card_write_confirm_message,
                    request.stationNumber,
                    request.cardNumber,
                    stored,
                    replacement
                )
            )
            .setNegativeButton(R.string.general_cancel, null)
            .setPositiveButton(R.string.sportident_card_write) { _, _ -> runOwnerNameWrite(request) }
            .show()
    }

    private fun runOwnerNameWrite(request: SportIdentOwnerNameWriteRequest) {
        val activeBinder = sportIdentBinder ?: return
        val baseline = cardInspection?.si8RawRead ?: return
        val cardWasPresent = cardPresenceState == AndroidSportIdentCardPresenceState.PRESENT
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            updateControls(isBusy = true)
            stopCardMonitor()
            var lastInstruction: AndroidSportIdentOwnerWriteInstruction? = null
            var monitorSeatedCard: Int? = request.cardNumber.takeIf { cardWasPresent }
            try {
                withContext(Dispatchers.IO) {
                    activeBinder.writeOwnerNames(request, baseline) { instruction ->
                        lastInstruction = instruction
                        postToCardView {
                            showWriteInstruction(instruction, request.cardNumber)
                        }
                    }
                }.also { result ->
                    monitorSeatedCard = result.cardNumber
                    cardInspection = null
                    showCardPresence(AndroidSportIdentCardPresenceState.PRESENT)
                    showStatus(
                        resources.getQuantityString(
                            R.plurals.sportident_card_write_verified,
                            result.controlPunchCount,
                            result.cardNumber,
                            result.controlPunchCount
                        )
                    )
                    refreshOwnerRecoveryState()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                cardInspection = null
                refreshOwnerRecoveryState()
                monitorSeatedCard = when (lastInstruction) {
                    AndroidSportIdentOwnerWriteInstruction.INSERT_FOR_WRITE,
                    AndroidSportIdentOwnerWriteInstruction.INSERT_FOR_READ_BACK -> null
                    else -> request.cardNumber
                }
                showStatus(
                    getString(
                        R.string.sportident_card_write_failed,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            operationJob = null
            updateControls(isBusy = false)
            startCardMonitor(monitorSeatedCard)
        }
    }

    private fun acceptOwnerWriteRecovery() {
        val activeBinder = sportIdentBinder ?: return
        val inspection = cardInspection ?: return
        val raw = inspection.si8RawRead ?: return
        operationJob = viewLifecycleOwner.lifecycleScope.launch {
            updateControls(isBusy = true)
            stopCardMonitor()
            try {
                withContext(Dispatchers.IO) {
                    activeBinder.acknowledgeOwnerWriteRecovery(
                        raw,
                        inspection.owner.holder?.firstName.orEmpty(),
                        inspection.owner.holder?.lastName.orEmpty()
                    )
                }
                refreshOwnerRecoveryState()
                showStatus(R.string.sportident_card_recovery_accepted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showStatus(
                    getString(
                        R.string.sportident_card_recovery_blocked,
                        error.message ?: error::class.simpleName
                    ),
                    isError = true
                )
            }
            operationJob = null
            updateControls(isBusy = false)
            startCardMonitor(inspection.owner.siNumber)
        }
    }

    private fun refreshOwnerRecoveryState() {
        ownerRecoveryState = runCatching {
            sportIdentBinder?.ownerWriteRecoveryState()
                ?: AndroidSportIdentOwnerRecoveryState.Empty
        }.getOrElse { AndroidSportIdentOwnerRecoveryState.Unavailable }
        when (val recovery = ownerRecoveryState) {
            AndroidSportIdentOwnerRecoveryState.Empty -> Unit
            is AndroidSportIdentOwnerRecoveryState.Pending -> showStatus(
                getString(
                    R.string.sportident_card_recovery_pending,
                    recovery.attempt.request.cardNumber
                ),
                isError = true
            )
            AndroidSportIdentOwnerRecoveryState.Unavailable -> showStatus(
                R.string.sportident_card_recovery_unavailable,
                isError = true
            )
        }
    }

    private fun updateControls(isBusy: Boolean) {
        progressView.visibility =
            if (isBusy || cardPresenceState == AndroidSportIdentCardPresenceState.READING) {
                View.VISIBLE
            } else {
                View.GONE
            }
        val writableInspection = cardInspection?.takeIf {
            it.owner.family == SportIdentCardFamily.SI8 &&
                it.owner.status == SportIdentOwnerDataStatus.READ &&
                it.si8RawRead != null
        }
        writeButton.isEnabled = sportIdentBinder != null && !isBusy &&
            writableInspection != null &&
            ownerRecoveryState == AndroidSportIdentOwnerRecoveryState.Empty
        firstNameView.isEnabled = writableInspection != null && !isBusy
        lastNameView.isEnabled = writableInspection != null && !isBusy
        val recoveryCanBeAccepted =
            ownerRecoveryState is AndroidSportIdentOwnerRecoveryState.Pending &&
                writableInspection != null
        acceptRecoveryButton.visibility =
            if (ownerRecoveryState == AndroidSportIdentOwnerRecoveryState.Empty) {
                View.GONE
            } else {
                View.VISIBLE
            }
        acceptRecoveryButton.isEnabled =
            sportIdentBinder != null && !isBusy && recoveryCanBeAccepted
    }

    private fun showStatus(@StringRes stringResource: Int, isError: Boolean = false) {
        showStatus(getString(stringResource), isError)
    }

    private fun showStatus(text: CharSequence, isError: Boolean = false) {
        statusView.setTextColor(if (isError) statusErrorColor else statusNormalColor)
        statusView.text = text
        if (isError) SoundProcessor.makeErrorSound(requireContext())
    }

    private fun showDisconnectedPresence() {
        cardPresenceState = AndroidSportIdentCardPresenceState.NOT_PRESENT
        setPresenceDot(R.color.grey)
        presenceStatusView.setText(R.string.sportident_card_station_disconnected)
        actionView.setText(R.string.sportident_card_connect_station)
        actionView.setTextColor(statusErrorColor)
    }

    private fun showPreparingPresence() {
        cardPresenceState = AndroidSportIdentCardPresenceState.NOT_PRESENT
        setPresenceDot(R.color.grey)
        presenceStatusView.setText(R.string.sportident_card_reader_preparing)
        actionView.setText(R.string.sportident_card_please_wait)
        actionView.setTextColor(actionNormalColor)
    }

    private fun showCardPresence(state: AndroidSportIdentCardPresenceState) {
        cardPresenceState = state
        when (state) {
            AndroidSportIdentCardPresenceState.NOT_PRESENT -> {
                setPresenceDot(R.color.grey)
                if (cardInspection == null) {
                    presenceStatusView.setText(R.string.sportident_card_not_detected)
                    actionView.setText(R.string.sportident_card_insert_now)
                    actionView.setTextColor(statusErrorColor)
                } else {
                    presenceStatusView.setText(R.string.sportident_card_removed)
                    actionView.setText(R.string.sportident_card_edit_then_write)
                    actionView.setTextColor(actionNormalColor)
                }
            }
            AndroidSportIdentCardPresenceState.READING -> {
                setPresenceDot(R.color.orange_reading)
                presenceStatusView.setText(R.string.sportident_card_reading_status)
                actionView.setText(R.string.sportident_card_keep_inserted)
                actionView.setTextColor(actionNormalColor)
            }
            AndroidSportIdentCardPresenceState.PRESENT -> {
                setPresenceDot(R.color.green_card_present)
                presenceStatusView.setText(R.string.sportident_card_detected)
                if (cardInspection == null) {
                    actionView.setText(R.string.sportident_card_remove_now)
                    actionView.setTextColor(statusErrorColor)
                } else {
                    actionView.setText(R.string.sportident_card_ready_for_name)
                    actionView.setTextColor(actionNormalColor)
                }
            }
        }
    }

    private fun showWriteInstruction(
        instruction: AndroidSportIdentOwnerWriteInstruction,
        cardNumber: Int
    ) {
        when (instruction) {
            AndroidSportIdentOwnerWriteInstruction.REMOVE_FOR_WRITE,
            AndroidSportIdentOwnerWriteInstruction.REMOVE_FOR_READ_BACK -> {
                setPresenceDot(R.color.green_card_present)
                presenceStatusView.setText(R.string.sportident_card_detected)
                actionView.setText(R.string.sportident_card_remove_now)
                actionView.setTextColor(statusErrorColor)
            }
            AndroidSportIdentOwnerWriteInstruction.INSERT_FOR_WRITE,
            AndroidSportIdentOwnerWriteInstruction.INSERT_FOR_READ_BACK -> {
                setPresenceDot(R.color.grey)
                presenceStatusView.setText(R.string.sportident_card_not_detected)
                actionView.setText(R.string.sportident_card_insert_now)
                actionView.setTextColor(statusErrorColor)
            }
            AndroidSportIdentOwnerWriteInstruction.KEEP_INSERTED_FOR_WRITE,
            AndroidSportIdentOwnerWriteInstruction.KEEP_INSERTED_FOR_READ_BACK -> {
                setPresenceDot(R.color.orange_reading)
                presenceStatusView.setText(R.string.sportident_card_reading_status)
                actionView.setText(R.string.sportident_card_keep_inserted)
                actionView.setTextColor(actionNormalColor)
            }
        }
        showStatus(
            getString(
                if (instruction == AndroidSportIdentOwnerWriteInstruction.REMOVE_FOR_READ_BACK ||
                    instruction == AndroidSportIdentOwnerWriteInstruction.INSERT_FOR_READ_BACK ||
                    instruction == AndroidSportIdentOwnerWriteInstruction.KEEP_INSERTED_FOR_READ_BACK
                ) {
                    R.string.sportident_card_verifying
                } else {
                    R.string.sportident_card_write_in_progress
                },
                cardNumber
            )
        )
    }

    private fun setPresenceDot(colorResource: Int) {
        presenceDotView.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorResource)
        )
    }

    private fun AndroidSportIdentCardInspection.displayText(): String {
        val holder = owner.holder
        val names = listOfNotNull(holder?.firstName, holder?.lastName)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .ifEmpty { getString(R.string.sportident_card_name_not_stored) }
        val ownerText = when (owner.status) {
            SportIdentOwnerDataStatus.READ -> buildString {
                append(getString(R.string.sportident_card_names_display, names))
                if (owner.family.supportsClub) {
                    append(
                        getString(
                            R.string.sportident_card_club_display,
                            holder?.club ?: getString(R.string.sportident_card_name_not_stored)
                        )
                    )
                }
            }
            SportIdentOwnerDataStatus.NOT_SUPPORTED ->
                getString(R.string.sportident_card_owner_not_supported)
            SportIdentOwnerDataStatus.INCOMPLETE ->
                getString(R.string.sportident_card_owner_incomplete)
            SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING -> getString(
                R.string.sportident_card_owner_unsupported_encoding,
                owner.characterSet
            )
        }
        return resources.getQuantityString(
            R.plurals.sportident_card_read_summary,
            controlPunchCount,
            owner.family.label,
            owner.siNumber,
            controlPunchCount,
            ownerText
        )
    }

}
