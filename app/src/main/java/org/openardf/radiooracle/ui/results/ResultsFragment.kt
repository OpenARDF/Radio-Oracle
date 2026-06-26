package org.openardf.radiooracle.ui.results

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.BottomNavDirections
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.prints.PrintAttemptResult
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.databinding.FragmentResultsBinding
import org.openardf.radiooracle.ui.EventToolbarSupport
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.serializableCompat
import org.openardf.radiooracle.ui.races.RaceEditDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private val dataProcessor = DataProcessor.get()

    private lateinit var resultsToolbar: Toolbar
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var resultsServiceMenuItem: MenuItem

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        resultsRecyclerView.adapter = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resultsToolbar = view.findViewById(R.id.results_toolbar)
        resultsRecyclerView = view.findViewById(R.id.results_recycler_view)
        resultsToolbar.inflateMenu(R.menu.fragment_menu_result)
        resultsServiceMenuItem = resultsToolbar.menu.findItem(R.id.result_menu_results_service)
        resultsToolbar.setOnMenuItemClickListener {
            return@setOnMenuItemClickListener setFragmentMenuActions(it)
        }

        EventToolbarSupport.bind(this, resultsToolbar, selectedRaceViewModel) { race ->
            dataProcessor.raceTypeToString(race.raceType)
        }

        // Set results service icon
        selectedRaceViewModel.resultService.observe(viewLifecycleOwner) { data ->
            if (data != null && data.resultService?.enabled == true) {
                resultsServiceMenuItem.icon =
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_result_service_running,
                        null
                    )
            } else {
                resultsServiceMenuItem.icon =
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_result_service_stopped,
                        null
                    )
            }
        }

        setResultListener()
        setBackButton()
        setRecyclerViewAdapter()
    }

    private fun setFragmentMenuActions(menuItem: MenuItem): Boolean {

        when (menuItem.itemId) {
            R.id.result_menu_share_results -> {
                findNavController().navigate(ResultsFragmentDirections.exportResults())
            }

            R.id.result_menu_results_service -> {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    findNavController().navigate(ResultsFragmentDirections.openResultService(race))
                }
            }

            R.id.result_menu_recalculate_results -> {
                selectedRaceViewModel.getCurrentRace()
                    ?.let { race -> selectedRaceViewModel.updateResultsByRace(race.id) }
                return true
            }

            R.id.result_menu_print_results -> {
                selectedRaceViewModel.getCurrentRace()?.let { race ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val printResult = withContext(Dispatchers.IO) {
                            dataProcessor.printResults(
                                selectedRaceViewModel.resultWrappers.value,
                                race
                            )
                        }
                        if (printResult == PrintAttemptResult.NEEDS_SETUP) {
                            findNavController().navigate(R.id.printsFragment)
                        }
                    }
                }
                return true
            }

            R.id.result_menu_edit_race -> {
                findNavController().navigate(
                    BottomNavDirections.modifyRaceProperties(
                        RaceEditDialogFragment.RaceEditActions.EDIT,
                        0,
                        selectedRaceViewModel.race.value
                    )
                )
                return true
            }

            R.id.result_menu_global_settings -> {
                findNavController().navigate(BottomNavDirections.openSettingsFromRace())
                return true
            }

        }
        return false
    }

    private fun setResultListener() {
        //Enable event modification from menu
        setFragmentResultListener(RaceEditDialogFragment.REQUEST_RACE_MODIFICATION) { _, bundle ->
            val race: Race = bundle.serializableCompat(RaceEditDialogFragment.BUNDLE_KEY_RACE)!!
            selectedRaceViewModel.updateRace(race)
        }
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
                findNavController().navigate(ResultsFragmentDirections.closeRace())
            }

            builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }

    }

    private fun openReadoutDetail(competitorData: CompetitorData) {
        val resultData = ResultData(
            competitorData.readoutData!!.result,
            competitorData.readoutData!!.punches,
            competitorData.competitorCategory
        )
        findNavController().navigate(ResultsFragmentDirections.openReadoutDetail(resultData))
    }

    private fun setRecyclerViewAdapter() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.resultWrappers.collect { results ->
                    resultsRecyclerView.adapter =
                        ResultsFragmentRecyclerViewAdapter(
                            ArrayList(results),
                            requireContext(),
                            selectedRaceViewModel
                        ) { cd -> openReadoutDetail(cd) }

                    (resultsRecyclerView.adapter as ResultsFragmentRecyclerViewAdapter).expandAllItems()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
