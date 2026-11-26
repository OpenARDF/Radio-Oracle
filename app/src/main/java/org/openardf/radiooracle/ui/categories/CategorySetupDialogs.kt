package org.openardf.radiooracle.ui.categories

import android.app.AlertDialog
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.shared.event.EventCategoryCompetitorSyncPlan
import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.hasEncryptedCourseData
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.races.racePasswordInput
import java.util.UUID

/** Native lists and dialogs are scoped to the race that opened them, including automatic SI-card switches. */
internal class CategorySetupDialogs(private val fragment: Fragment, private val selection: SelectedRaceViewModel) {
    private val setup = ARDFRepository.get().raceSetup
    private val context get() = fragment.requireContext()
    private var dialog: AlertDialog? = null
    private var action: Job? = null
    private var plan: EventCategoryCompetitorSyncPlan? = null
    private var boundRaceId: UUID? = null

    fun bind(toolbar: Toolbar, review: Button) {
        val station = toolbar.menu.findItem(R.id.category_menu_station)
        review.isEnabled = false
        station.isEnabled = false
        review.setOnClickListener {
            val race = selection.getCurrentRace()
            if (race != null && race.id == boundRaceId) plan?.let { reviewCategories(race.id, it) }
        }
        station.setOnMenuItemClickListener {
            selection.getCurrentRace()?.let { showControls(it.id) }
            true
        }
        fun refreshStationAction() {
            val race = selection.getCurrentRace()
            val locked = race != null && selection.readoutData.value.any { it.result.raceId == race.id }
            station.isEnabled = race != null && !locked
            station.setTitle(if (locked) R.string.station_locked_after_readout else R.string.edit_station_code)
        }
        val owner = fragment.viewLifecycleOwner
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    selection.race.asFlow().collectLatest { race ->
                        if (boundRaceId != race?.id) close()
                        boundRaceId = race?.id
                        plan = null
                        review.isEnabled = false
                        review.isVisible = true
                        refreshStationAction()
                        if (race != null) setup.categoryReviews(race.id).collect { current ->
                            plan = current
                            review.isEnabled = current.hasMismatch
                            review.isVisible = !current.hasRegisteredCompetitors || current.hasMismatch
                        }
                    }
                }
                launch {
                    selection.readoutData.collect { refreshStationAction() }
                }
            }
        }
    }

    fun close() {
        action?.cancel()
        dialog?.dismiss()
        dialog = null
    }

    private fun present(builder: AlertDialog.Builder): AlertDialog {
        dialog?.dismiss()
        return builder.show().also { dialog = it }
    }

    private fun runAction(raceId: UUID, block: suspend () -> Unit) {
        if (selection.getCurrentRace()?.id != raceId) return
        action?.cancel()
        action = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                present(AlertDialog.Builder(context).setMessage(error.localizedMessage ?: context.getString(R.string.general_unknown_error))
                    .setPositiveButton(R.string.general_ok, null))
            }
        }
    }

    private fun showControls(raceId: UUID) = runAction(raceId) {
        val project = withContext(Dispatchers.IO) { setup.stationCatalog(raceId) }
        val controls = project.raceData.controls
        val picker = AlertDialog.Builder(context).setTitle(R.string.edit_station_code)
            .setNegativeButton(R.string.general_cancel, null)
        if (controls.isEmpty()) picker.setMessage(R.string.station_no_controls)
        else picker.setItems(controls.map { "${it.publicLabel ?: it.label}  (SI ${it.siCode})" }.toTypedArray()) { _, index ->
            editStation(raceId, project, controls[index])
        }
        present(picker)
    }

    private fun editStation(raceId: UUID, project: EventProjectFile, control: EventControl) {
        val code = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(context.getString(R.string.station_number, control.siCode))
            selectAll()
            hint = context.getString(R.string.edit_station_code)
        }
        val password = if (project.hasEncryptedCourseData()) racePasswordInput(context).apply {
            hint = context.getString(R.string.race_current_password)
        } else null
        val fields = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(code)
            password?.let { addView(it) }
        }
        val edit = present(AlertDialog.Builder(context).setTitle(control.publicLabel ?: control.label)
            .setView(ScrollView(context).apply { addView(fields) })
            .setNegativeButton(R.string.general_cancel, null).setPositiveButton(R.string.general_preview, null))
        edit.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = code.text.toString().toIntOrNull()
            if (number == null) {
                code.error = context.getString(R.string.station_enter_code)
                return@setOnClickListener
            }
            val secret = password?.text?.toString()
            edit.dismiss()
            previewStation(raceId, project, control, number, secret)
        }
    }

    private fun previewStation(raceId: UUID, project: EventProjectFile, control: EventControl, code: Int, password: String?) =
        runAction(raceId) {
            val change = withContext(Dispatchers.Default) { setup.reviewStationCode(project, control.id, code, password) }
            val summary = context.getString(R.string.station_review_change, project.raceData.race.name,
                control.publicLabel ?: control.label, change.oldCode, change.newCode)
            val message = listOfNotNull(summary, ControlRoleLabelRules.mismatchWarning(control.publicLabel, control.type))
                .joinToString("\n\n")
            present(AlertDialog.Builder(context).setTitle(R.string.edit_station_code).setMessage(message)
                .setNegativeButton(R.string.general_cancel, null).setPositiveButton(R.string.general_save) { _, _ ->
                    runAction(raceId) {
                        setup.applyStationCode(change)
                        Toast.makeText(context, R.string.station_change_saved, Toast.LENGTH_SHORT).show()
                    }
                })
        }

    private fun reviewCategories(raceId: UUID, review: EventCategoryCompetitorSyncPlan) {
        val missing = review.missingCategories
        val empty = review.emptyCategories
        val labels = missing.map {
            context.getString(if (it.existingEquivalentCategoryId == null) R.string.category_sync_add else R.string.category_sync_repair,
                it.categoryName, it.competitorCount)
        } + empty.map {
            context.getString(if (it.hasCourseData) R.string.category_sync_remove_course else R.string.category_sync_remove, it.categoryName)
        }
        // Only missing assignments are checked initially. Removing an unused course is always explicit.
        val checked = BooleanArray(labels.size) { it < missing.size }
        val picker = present(AlertDialog.Builder(context).setTitle(R.string.category_review_mismatches)
            .setMultiChoiceItems(labels.toTypedArray(), checked) { source, index, selected ->
                checked[index] = selected
                (source as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
            }.setNegativeButton(R.string.general_cancel, null).setPositiveButton(R.string.general_save) { _, _ ->
                runAction(raceId) {
                    val outcome = setup.applyCategoryReview(raceId, review,
                        missing.filterIndexed { i, _ -> checked[i] }.mapTo(mutableSetOf()) { it.categoryId },
                        empty.filterIndexed { i, _ -> checked[missing.size + i] }.mapTo(mutableSetOf()) { it.categoryId })
                    Toast.makeText(context, context.getString(R.string.category_sync_saved, outcome.addedCategoryCount,
                        outcome.repairedAssignmentCount, outcome.removedCategoryCount), Toast.LENGTH_LONG).show()
                }
            })
        picker.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = checked.any { it }
    }
}
