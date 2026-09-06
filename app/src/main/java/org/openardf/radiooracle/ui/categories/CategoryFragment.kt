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

package org.openardf.radiooracle.ui.categories

import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.openardf.radiooracle.BottomNavDirections
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.databinding.FragmentCategoriesBinding
import org.openardf.radiooracle.ui.EventToolbarSupport
import org.openardf.radiooracle.ui.FabListClearance
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.serializableCompat
import org.openardf.radiooracle.ui.races.RaceEditDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private val dataProcessor = DataProcessor.get()

    private lateinit var categoryToolbar: Toolbar
    private lateinit var categoryAddFab: FloatingActionButton
    private lateinit var categoryRecyclerView: RecyclerView

    private var mLastClickTime: Long = 0

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryToolbar = view.findViewById(R.id.category_toolbar)
        categoryAddFab = view.findViewById(R.id.category_btn_add)
        categoryRecyclerView = view.findViewById(R.id.category_recycler_view)
        FabListClearance.bind(view as ViewGroup, categoryRecyclerView, categoryAddFab)

        categoryToolbar.inflateMenu(R.menu.fragment_menu_category)
        categoryToolbar.setOnMenuItemClickListener {
            return@setOnMenuItemClickListener setFragmentMenuActions(it)
        }
        setSeriesMenuTitle()

        categoryAddFab.setOnClickListener {
            //Prevent accidental double click
            if (SystemClock.elapsedRealtime() - mLastClickTime > 1000) {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    findNavController().navigate(
                        CategoryFragmentDirections.modifyCategory(
                            true,
                            -1, null, "",
                            race
                        )
                    )
                }
            }
            mLastClickTime = SystemClock.elapsedRealtime()
        }

        EventToolbarSupport.bind(this, categoryToolbar, selectedRaceViewModel) { race ->
            dataProcessor.raceTypeToString(race.raceType)
        }
        setFragmentListener()
        setRecyclerViewAdapter()
        setBackButton()
    }

    private fun setFragmentMenuActions(menuItem: MenuItem): Boolean {

        when (menuItem.itemId) {
            R.id.category_menu_import_file -> {
                findNavController().navigate(CategoryFragmentDirections.importExportData())
                return true
            }

            R.id.category_menu_manage_aliases -> {
                selectedRaceViewModel.getCurrentRace()
                    ?.let { findNavController().navigate(CategoryFragmentDirections.manageAliases(it.id)) }
                return true
            }

            R.id.category_menu_create_standard_categories -> {
                findNavController().navigate(CategoryFragmentDirections.createStandardCategories())
                return true
            }

            R.id.category_menu_event_series -> {
                showEventSeriesMembershipDialog()
                return true
            }

            R.id.category_menu_edit_race -> {
                findNavController().navigate(
                    BottomNavDirections.modifyRaceProperties(
                        RaceEditDialogFragment.RaceEditActions.EDIT,
                        0,
                        selectedRaceViewModel.race.value
                    )
                )
                return true
            }


            R.id.category_menu_global_settings -> {
                findNavController().navigate(BottomNavDirections.openSettingsFromRace())
                return true
            }
        }
        return false
    }

    private fun setSeriesMenuTitle() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.currentRaceSeries.collect { series ->
                    categoryToolbar.menu.findItem(R.id.category_menu_event_series)
                        ?.setTitle(
                            if (series == null) {
                                R.string.event_series_add_event
                            } else {
                                R.string.event_series_remove_event
                            }
                        )
                }
            }
        }
    }

    private fun showEventSeriesMembershipDialog() {
        val race = selectedRaceViewModel.getCurrentRace() ?: return
        val currentSeries = selectedRaceViewModel.currentRaceSeries.value
        if (currentSeries == null) {
            showAddToSeriesDialog(race)
        } else {
            confirmRemoveFromSeries(race, currentSeries)
        }
    }

    private fun showAddToSeriesDialog(race: Race) {
        val availableSeries = selectedRaceViewModel.eventSeries.value
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.series.name })
        if (availableSeries.isEmpty()) {
            showCreateSeriesDialog(race)
            return
        }

        val itemLabels = listOf(getString(R.string.event_series_create_new)) +
            availableSeries.map { it.series.name }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_add_to_existing_title)
            .setItems(itemLabels.toTypedArray()) { _, which ->
                if (which == 0) {
                    showCreateSeriesDialog(race)
                } else {
                    addCurrentEventToSeries(availableSeries[which - 1])
                }
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun showCreateSeriesDialog(race: Race) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            hint = getString(R.string.event_series_name_hint)
            setSingleLine(true)
            setText(getString(R.string.event_series_default_name, race.name))
            selectAll()
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_create_new_title)
            .setView(input)
            .setPositiveButton(R.string.event_series_create_new, null)
            .setNegativeButton(R.string.general_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val seriesName = input.text.toString().trim()
                if (seriesName.isBlank()) {
                    input.error = getString(R.string.event_series_name_required)
                    SoundProcessor.makeErrorSound(requireContext())
                } else {
                    dialog.dismiss()
                    createSeriesFromCurrentEvent(seriesName)
                }
            }
        }
        dialog.show()
    }

    private fun createSeriesFromCurrentEvent(seriesName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val series = withContext(Dispatchers.IO) {
                    selectedRaceViewModel.createSeriesFromCurrentRace(seriesName)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_create_new_success, series.series.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                showEventSeriesMembershipError(error)
            }
        }
    }

    private fun addCurrentEventToSeries(series: EventSeriesData) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updatedSeries = withContext(Dispatchers.IO) {
                    selectedRaceViewModel.addCurrentRaceToEventSeries(series.series.seriesId)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.event_series_add_to_existing_success, updatedSeries.series.name),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                showEventSeriesMembershipError(error)
            }
        }
    }

    private fun confirmRemoveFromSeries(race: Race, series: EventSeriesData) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_remove_event)
            .setMessage(
                getString(
                    R.string.event_series_remove_event_confirmation,
                    race.name,
                    series.series.name
                )
            )
            .setPositiveButton(R.string.event_series_remove_event) { _, _ ->
                removeCurrentEventFromSeries()
            }
            .setNegativeButton(R.string.general_cancel, null)
            .show()
    }

    private fun removeCurrentEventFromSeries() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    selectedRaceViewModel.removeCurrentRaceFromEventSeries()
                }
                Toast.makeText(
                    requireContext(),
                    R.string.event_series_remove_event_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (error: Exception) {
                showEventSeriesMembershipError(error)
            }
        }
    }

    private fun showEventSeriesMembershipError(error: Exception) {
        SoundProcessor.makeErrorSound(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.event_series_membership_error)
            .setMessage(error.message ?: getString(R.string.event_series_membership_error))
            .setPositiveButton(R.string.general_ok, null)
            .show()
    }


    private fun setFragmentListener() {
        setFragmentResultListener(CategoryEditDialogFragment.REQUEST_CATEGORY_MODIFICATION) { _, bundle ->
            val create = bundle.getBoolean(CategoryEditDialogFragment.BUNDLE_KEY_CREATE)
            val position = bundle.getInt(CategoryEditDialogFragment.BUNDLE_KEY_POSITION)

            if (!create) {
                categoryRecyclerView.adapter?.notifyItemChanged(position)
            }
        }

        //Enable race modification from menu
        setFragmentResultListener(RaceEditDialogFragment.REQUEST_RACE_MODIFICATION) { _, bundle ->
            val race: Race = bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_RACE)!!
            selectedRaceViewModel.updateRace(race)
        }
    }

    private fun setRecyclerViewAdapter() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.categories.collect { categories ->
                    categoryRecyclerView.adapter =
                        CategoryRecyclerViewAdapter(
                            categories, { action, position, categoryData ->
                                recyclerViewContextMenuActions(
                                    action,
                                    position,
                                    categoryData
                                )
                            }, requireContext(),
                            selectedRaceViewModel
                        )
                }
            }
        }
    }

    private fun confirmCategoryDeletion(category: Category) {
        val builder = AlertDialog.Builder(context)

        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_delete_category, null)

        // Set dynamic message text
        val messageTextView = dialogView.findViewById<TextView>(R.id.delete_category_message)
        messageTextView.text = getString(R.string.category_delete_confirmation, category.name)

        val deleteCompetitorsCheckbox =
            dialogView.findViewById<CheckBox>(R.id.delete_category_checkbox)

        builder.setTitle(getString(R.string.category_delete))
        builder.setView(dialogView)

        builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
            selectedRaceViewModel.deleteCategory(
                category.id,
                category.raceId,
                deleteCompetitorsCheckbox.isChecked
            )
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun recyclerViewContextMenuActions(
        action: Int,
        position: Int,
        categoryData: CategoryData
    ) {
        when (action) {
            0 -> {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    findNavController().navigate(
                        CategoryFragmentDirections.modifyCategory(
                            false,
                            position,
                            categoryData.category,
                            categoryData.category.controlPointsString,
                            race
                        )
                    )
                }
            }

            1 -> selectedRaceViewModel.duplicateCategory(categoryData)
            2 -> confirmCategoryDeletion(categoryData.category)
        }
    }

    private fun setBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(getString(R.string.race_end))
            val message = getString(R.string.race_end_confirmation)
            builder.setMessage(message)

            builder.setPositiveButton(R.string.general_ok) { _, _ ->
                selectedRaceViewModel.disableResultService()
                dataProcessor.removeCurrentRace()
                findNavController().navigate(CategoryFragmentDirections.closeRace())
            }

            builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
