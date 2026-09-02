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

package org.openardf.radiooracle.ui.competitors

import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import de.codecrafters.tableview.SortableTableView
import de.codecrafters.tableview.toolkit.SimpleTableHeaderAdapter
import org.openardf.radiooracle.BottomNavDirections
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.databinding.FragmentCompetitorsBinding
import org.openardf.radiooracle.ui.EventToolbarSupport
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.serializableCompat
import org.openardf.radiooracle.ui.races.RaceEditDialogFragment
import kotlinx.coroutines.launch
import java.text.Collator


class CompetitorFragment : Fragment() {

    private var _binding: FragmentCompetitorsBinding? = null

    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private val dataProcessor = DataProcessor.get()
    private lateinit var collator: Collator
    private lateinit var competitorToolbar: Toolbar
    private lateinit var competitorTableView: SortableTableView<CompetitorData>
    private lateinit var competitorDisplayTypePicker: MaterialAutoCompleteTextView
    private lateinit var competitorAddFab: FloatingActionButton
    private var mLastClickTime: Long = 0

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompetitorsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        competitorToolbar = view.findViewById(R.id.competitor_fragment_toolbar)
        competitorAddFab = view.findViewById(R.id.competitor_btn_add)
        competitorTableView = view.findViewById(R.id.competitor_fragment_table_view)
        competitorDisplayTypePicker = view.findViewById(R.id.competitor_fragment_display_type)

        competitorToolbar.inflateMenu(R.menu.fragment_menu_competitor)
        competitorToolbar.setOnMenuItemClickListener {
            return@setOnMenuItemClickListener setFragmentMenuActions(it)
        }

        EventToolbarSupport.bind(this, competitorToolbar, selectedRaceViewModel) { race ->
            dataProcessor.raceTypeToString(race.raceType)
        }

        competitorDisplayTypePicker.setOnItemClickListener { _, _, _, pos ->
            toggleCompetitorDisplay(CompetitorTableDisplayType.getByValue(pos.toInt()))
        }

        competitorAddFab.setOnClickListener {
            //Prevent accidental double click
            if (SystemClock.elapsedRealtime() - mLastClickTime > 1500) {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    findNavController().navigate(
                        CompetitorFragmentDirections.modifyCompetitor(
                            true,
                            null, -1, race
                        )
                    )
                }
            }
            mLastClickTime = SystemClock.elapsedRealtime()
        }
        // Set collator for comparing
        collator = Collator.getInstance(selectedRaceViewModel.getCurrentLocale(requireContext()))

        competitorDisplayTypePicker.setText(getText(R.string.competitor_display_overview), false)
        toggleCompetitorDisplay(CompetitorTableDisplayType.OVERVIEW)
        setBackButton()
        setResultListener()
    }

    private fun setFragmentMenuActions(menuItem: MenuItem): Boolean {

        when (menuItem.itemId) {
            R.id.competitor_menu_import_file -> {
                findNavController().navigate(
                    CompetitorFragmentDirections.importExportDataCompetitors()
                )
                return true
            }

            R.id.competitor_menu_delete_all_competitors -> {
                confirmAllCompetitorsDeletion()
                return true
            }

            R.id.competitor_menu_edit_race -> {
                findNavController().navigate(
                    BottomNavDirections.modifyRaceProperties(
                        RaceEditDialogFragment.RaceEditActions.EDIT,
                        0,
                        selectedRaceViewModel.race.value
                    )
                )
                return true
            }

            R.id.competitor_menu_global_settings -> {
                findNavController().navigate(BottomNavDirections.openSettingsFromRace())
                return true
            }
        }
        return false
    }

    private fun setTableHeaders(displayType: CompetitorTableDisplayType) {

        var headers = IntArray(6)
        when (displayType) {
            CompetitorTableDisplayType.OVERVIEW -> {

                headers =
                    intArrayOf(
                        R.string.competitor_start_number_header,
                        R.string.general_name,
                        R.string.general_club,
                        R.string.general_category,
                        R.string.general_si_number,
                        R.string.general_actions
                    )

                //Set comparators
                competitorTableView.setColumnComparator(0, CompetitorStartNumComparator())
                competitorTableView.setColumnComparator(1, CompetitorNameComparator(collator))
                competitorTableView.setColumnComparator(2, CompetitorClubComparator(collator))
                competitorTableView.setColumnComparator(3, CompetitorCategoryComparator(collator))
                competitorTableView.setColumnComparator(4, CompetitorSINumberComparator())
                competitorTableView.setColumnComparator(5, null)

            }

            CompetitorTableDisplayType.START_LIST -> {
                headers =
                    intArrayOf(
                        R.string.competitor_start_number_header,
                        R.string.general_start_time,
                        R.string.general_name,
                        R.string.general_category,
                        R.string.general_si_number,
                        R.string.general_actions
                    )
                competitorTableView.setColumnComparator(0, CompetitorStartNumComparator())
                competitorTableView.setColumnComparator(1, CompetitorDrawnStartTimeComparator())
                competitorTableView.setColumnComparator(2, CompetitorNameComparator(collator))
                competitorTableView.setColumnComparator(3, CompetitorCategoryComparator(collator))
                competitorTableView.setColumnComparator(4, CompetitorSINumberComparator())
                competitorTableView.setColumnComparator(5, null)
            }

            CompetitorTableDisplayType.FINISH_REACHED -> {
                headers =
                    intArrayOf(
                        R.string.general_name,
                        R.string.general_category,
                        R.string.general_run_time,
                        R.string.general_start_time,
                        R.string.general_finish_time,
                        R.string.general_actions
                    )

                competitorTableView.setColumnComparator(0, CompetitorNameComparator(collator))
                competitorTableView.setColumnComparator(1, CompetitorCategoryComparator(collator))
                competitorTableView.setColumnComparator(2, CompetitorRunTimeComparator())
                competitorTableView.setColumnComparator(3, CompetitorStartTimeComparator())
                competitorTableView.setColumnComparator(4, CompetitorFinishTimeComparator())
                competitorTableView.setColumnComparator(5, null)
            }

            CompetitorTableDisplayType.ON_THE_WAY -> {
                headers =
                    intArrayOf(
                        R.string.general_name,
                        R.string.general_category,
                        R.string.general_start_time,
                        R.string.general_run_time,
                        R.string.competitor_to_limit,
                        R.string.general_actions
                    )
                for (i in 0..5) {
                    competitorTableView.setColumnComparator(i, null)
                }
            }

            CompetitorTableDisplayType.SI_RENT -> {
                headers = intArrayOf(
                    R.string.general_si_number,
                    R.string.general_name,
                    R.string.general_category,
                    R.string.general_start_time,
                    R.string.general_finish_time,
                    R.string.general_actions
                )

                competitorTableView.setColumnComparator(0, CompetitorSINumberComparator())
                competitorTableView.setColumnComparator(1, CompetitorNameComparator(collator))
                competitorTableView.setColumnComparator(2, CompetitorCategoryComparator(collator))
                competitorTableView.setColumnComparator(3, CompetitorStartTimeComparator())
                competitorTableView.setColumnComparator(4, CompetitorFinishTimeComparator())
                competitorTableView.setColumnComparator(5, null)
            }
        }

        val adapter = SimpleTableHeaderAdapter(
            requireContext(),
            *headers
        )
        adapter.setGravity(Gravity.CENTER)
        adapter.setTextSize(14)

        competitorTableView.headerAdapter = adapter

        competitorTableView.setDataRowBackgroundProvider { rowIndex, competitorData ->
            ColorDrawable(
                requireContext().getColor(
                    competitorRowBackgroundColorResource(
                        rowIndex = rowIndex,
                        isRentedSiCard = competitorData.competitorCategory.competitor.siRent
                    )
                )
            )
        }
    }

    /**
     * Filter the data based on required display type
     */
    private fun filterCompetitorData(
        data: List<CompetitorData>,
        displayType: CompetitorTableDisplayType
    ): List<CompetitorData> {
        when (displayType) {
            CompetitorTableDisplayType.OVERVIEW,
            CompetitorTableDisplayType.START_LIST -> return data

            CompetitorTableDisplayType.FINISH_REACHED -> {
                val filtered = data.filter { cd ->
                    cd.readoutData != null
                }
                return filtered.sortedWith(CompetitorFinishTimeComparator())
            }

            CompetitorTableDisplayType.ON_THE_WAY -> {
                val filtered = data.filter { cd ->
                    cd.readoutData == null
                }
                return filtered.sortedWith(CompetitorDrawnStartTimeComparator())
            }

            CompetitorTableDisplayType.SI_RENT -> {
                val filtered = data.filter { cd ->
                    cd.competitorCategory.competitor.siRent
                }
                return filtered.sortedWith(CompetitorSINumberComparator())
            }
        }
    }

    private fun toggleCompetitorDisplay(displayType: CompetitorTableDisplayType) {
        setTableHeaders(displayType)
        setRecyclerAdapter(displayType)
    }

    private fun setRecyclerAdapter(displayType: CompetitorTableDisplayType) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.competitorData.collect { competitorData ->
                    selectedRaceViewModel.getCurrentRace()?.let {
                        competitorTableView.dataAdapter =
                            CompetitorTableViewAdapter(
                                filterCompetitorData(competitorData, displayType),
                                displayType,
                                requireContext(), it
                            ) { action, position, competitor ->
                                tableViewContextMenuActions(
                                    action,
                                    position,
                                    competitor
                                )
                            }
                    }
                }
            }
        }
    }

    private fun tableViewContextMenuActions(
        action: Int,
        position: Int,
        competitorData: CompetitorData
    ) {
        when (action) {
            0 -> {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    findNavController().navigate(
                        CompetitorFragmentDirections.modifyCompetitor(
                            false,
                            competitorData.competitorCategory.competitor,
                            position, race
                        )
                    )
                }
            }

            1 -> confirmCompetitorDeletion(competitorData.competitorCategory.competitor)
        }
    }

    private fun confirmCompetitorDeletion(competitor: Competitor) {
        val builder = AlertDialog.Builder(context)

        // Inflate the custom layout
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_delete_competitor, null)

        // Set dynamic message text
        val messageTextView = dialogView.findViewById<TextView>(R.id.delete_competitor_message)
        messageTextView.text =
            getString(R.string.competitor_delete_confirmation, competitor.getFullName())

        // Reference the checkbox
        val deleteReadoutCheckbox =
            dialogView.findViewById<CheckBox>(R.id.delete_competitor_checkbox)

        builder.setTitle(getString(R.string.competitor_delete))
        builder.setView(dialogView)

        builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
            val deleteReadout = deleteReadoutCheckbox.isChecked
            selectedRaceViewModel.deleteCompetitor(competitor.id, deleteReadout)
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun confirmAllCompetitorsDeletion() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.competitor_delete_all))
        builder.setMessage(R.string.competitor_delete_all_confirmation)

        builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
            selectedRaceViewModel.deleteAllCompetitorsByRace()
            dialog.dismiss()
        }

        builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun setBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(getString(R.string.race_end))
            val message = getString(R.string.race_end_confirmation)
            builder.setMessage(message)

            builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
                selectedRaceViewModel.disableResultService()
                dataProcessor.removeCurrentRace()
                findNavController().navigate(CompetitorFragmentDirections.closeRace())
            }

            builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }
    }

    private fun setResultListener() {
        setFragmentResultListener(CompetitorEditDialogFragment.REQUEST_COMPETITOR_MODIFICATION) { _, bundle ->
            val create = bundle.getBoolean(CompetitorEditDialogFragment.BUNDLE_KEY_CREATE)

            if (!create) {
                competitorTableView.dataAdapter.notifyDataSetChanged()
            }
        }

        //Enable race modification from menu
        setFragmentResultListener(RaceEditDialogFragment.REQUEST_RACE_MODIFICATION) { _, bundle ->
            val race: Race = bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_RACE)!!
            selectedRaceViewModel.updateRace(race)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

internal fun competitorRowBackgroundColorResource(rowIndex: Int, isRentedSiCard: Boolean): Int =
    when {
        isRentedSiCard -> R.color.yellow_warning
        rowIndex % 2 == 0 -> R.color.white
        else -> R.color.light_grey
    }
