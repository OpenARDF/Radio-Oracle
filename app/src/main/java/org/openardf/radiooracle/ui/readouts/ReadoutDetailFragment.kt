package org.openardf.radiooracle.ui.readouts

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.prints.PrintAttemptResult
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import org.openardf.radiooracle.ui.categories.CategoryEditDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

/** Detail screen for one readout, including competitor data, result status, and punch list. */
class ReadoutDetailFragment : Fragment() {

    private val dataProcessor = DataProcessor.get()
    private val args: ReadoutDetailFragmentArgs by navArgs()
    private val selectedRaceViewModel: SelectedRaceViewModel by activityViewModels()
    private lateinit var resultData: ResultData

    private lateinit var readoutDetailToolbar: Toolbar
    private lateinit var punchRecyclerView: RecyclerView
    private lateinit var competitorNameView: TextView
    private lateinit var siNumberView: TextView
    private lateinit var clubView: TextView
    private lateinit var indexView: TextView
    private lateinit var checkTimeView: TextView
    private lateinit var runTimeView: TextView
    private lateinit var raceStatusView: TextView
    private lateinit var categoryView: TextView
    private lateinit var pointsView: TextView
    private lateinit var placeView: TextView

    /** Inflates the readout detail screen. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_readout_detail, container, false)
    }

    /** Wires toolbar actions, field references, result listeners, and initial content. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resultData = args.resultData

        readoutDetailToolbar = view.findViewById(R.id.readout_detail_toolbar)
        punchRecyclerView = view.findViewById(R.id.readout_detail_punch_recycler_view)
        competitorNameView = view.findViewById(R.id.readout_detail_competitor_name)
        siNumberView = view.findViewById(R.id.readout_detail_si_number)
        clubView = view.findViewById(R.id.readout_detail_club)
        indexView = view.findViewById(R.id.readout_detail_index_callsign)
        checkTimeView = view.findViewById(R.id.readout_detail_check_time)
        runTimeView = view.findViewById(R.id.readout_detail_run_time)
        raceStatusView = view.findViewById(R.id.readout_detail_status)
        categoryView = view.findViewById(R.id.readout_detail_category)
        pointsView = view.findViewById(R.id.readout_detail_points)
        placeView = view.findViewById(R.id.readout_detail_place)

        readoutDetailToolbar.setNavigationIcon(R.drawable.ic_back)
        readoutDetailToolbar.setTitle(R.string.readout_detail_title)
        readoutDetailToolbar.subtitle =
            args.resultData.result.siNumber?.toString()
        readoutDetailToolbar.inflateMenu(R.menu.fragment_menu_readout_detail)

        readoutDetailToolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        setResultListener()
        populateFields()
    }

    /** Populates all visible readout and competitor fields from the current result data. */
    private fun populateFields() {

        if (resultData.competitorCategory?.competitor != null) {
            clubView.text = resultData.competitorCategory!!.competitor.club
            indexView.text = resultData.competitorCategory!!.competitor.index
            competitorNameView.text = resultData.competitorCategory!!.competitor.getFullName()
            pointsView.text = if (resultData.blocksScoreAndRunTimeDisplay()) {
                "-"
            } else {
                resultData.result.points.toString()
            }
        } else {
            competitorNameView.text =
                resultData.result.cardName ?: getString(R.string.readout_unknown_competitor)
            pointsView.text = getText(R.string.unknown)
            clubView.text = getText(R.string.unknown)
            indexView.text = getText(R.string.unknown)
        }
        raceStatusView.text =
            dataProcessor.resultStatusToString(resultData.result.resultStatus)

        if (resultData.competitorCategory?.category != null) {
            categoryView.text = resultData.competitorCategory!!.category!!.name
        } else {
            categoryView.text = getText(R.string.unknown)
        }

        siNumberView.text = if (resultData.result.siNumber != null) {
            resultData.result.siNumber.toString()
        } else {
            "-"
        }
        checkTimeView.text = resultData.result.checkTime?.getTimeString() ?: "-"

        runTimeView.text = if (resultData.blocksScoreAndRunTimeDisplay()) {
            resultData.blockedRunTimeStatusText()
        } else {
            TimeProcessor.durationToFormattedString(
                resultData.result.runTime,
                dataProcessor.useMinuteTimeFormat()
            )
        }

        placeView.text = if (resultData.competitorCategory?.competitor != null &&
            resultData.result.resultStatus == ResultStatus.OK
        ) {
            runBlocking {
                val place = ResultsProcessor.getCompetitorPlace(
                    resultData.competitorCategory!!.competitor.id,
                    resultData.result.raceId,
                    DataProcessor.get()
                )
                "${place.toString()}."
            }
        } else {
            "-"
        }

        val textColor = if (resultData.hasWarning()) {
            ContextCompat.getColor(requireContext(), R.color.red_error)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }
        setDetailTextColor(textColor)

        setMenuActions()
        setRecyclerViewAdapter(resultData.punches)
    }

    private fun ResultData.hasWarning(): Boolean =
        blocksScoreAndRunTimeDisplay() ||
            hasTimingOrPunches() && readoutTiming().issues.isNotEmpty() ||
            punches.any { it.punch.punchStatus == PunchStatus.INVALID }

    private fun ResultData.blocksScoreAndRunTimeDisplay(): Boolean =
        result.resultStatus == ResultStatus.ERROR || hasTimingOrPunches() && readoutTiming().blocksResult

    private fun ResultData.blockedRunTimeStatusText(): String =
        if (hasTimingOrPunches() && readoutTiming().blocksResult) {
            ResultStatus.ERROR.toResultStatusCode()
        } else {
            dataProcessor.resultStatusToShortString(result.resultStatus)
        }

    private fun ResultData.hasTimingOrPunches(): Boolean =
        result.startTime != null ||
            result.finishTime != null ||
            punches.isNotEmpty()

    private fun ResultData.readoutTiming() =
        SportIdentReadoutTiming.calculate(
            startSeconds = result.startTime?.getSeconds(),
            finishSeconds = result.finishTime?.getSeconds(),
            controlSeconds = punches
                .filter { it.punch.punchType == SIRecordType.CONTROL }
                .map { it.punch.siTime.getSeconds() }
        )

    private fun setDetailTextColor(color: Int) {
        competitorNameView.setTextColor(color)
        siNumberView.setTextColor(color)
        clubView.setTextColor(color)
        indexView.setTextColor(color)
        checkTimeView.setTextColor(color)
        runTimeView.setTextColor(color)
        raceStatusView.setTextColor(color)
        categoryView.setTextColor(color)
        pointsView.setTextColor(color)
        placeView.setTextColor(color)
    }

    /** Handles toolbar actions for edit, print, category creation, control assignment, and delete. */
    private fun setMenuActions() {
        readoutDetailToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.readout_detail_menu_edit_readout -> {
                    selectedRaceViewModel.getCurrentRace()?.let { it1 ->
                        findNavController().navigate(
                            ReadoutDetailFragmentDirections.editReadout(
                                false,
                                resultData, -1,
                                it1.id
                            )
                        )
                    }
                    true
                }

                R.id.readout_detail_menu_print_ticket -> {
                    selectedRaceViewModel.getCurrentRace()?.let { race ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val printResult = withContext(Dispatchers.IO) {
                                dataProcessor.printFinishTicket(
                                    resultData,
                                    race
                                )
                            }
                            if (printResult == PrintAttemptResult.NEEDS_SETUP) {
                                findNavController().navigate(R.id.printsFragment)
                            }
                        }
                    }
                    true
                }

                R.id.readout_detail_menu_create_category -> {
                    selectedRaceViewModel.getCurrentRace()?.let { race ->
                        findNavController().navigate(
                            ReadoutDetailFragmentDirections.createCategoryFromReadout(
                                true,
                                -1,
                                null,
                                ControlPointsHelper.getStringFromPunches(
                                    resultData.getPunchList()
                                ), race
                            )
                        )
                    }
                    true
                }

                R.id.readout_detail_menu_assign_controls -> {
                    if (selectedRaceViewModel.getCategories().isNotEmpty()) {
                        findNavController().navigate(
                            ReadoutDetailFragmentDirections.assignControlPoints(
                                ControlPointsHelper.getStringFromPunches(
                                    resultData.getPunchList()
                                )
                            )
                        )
                    } else {
                        Toast.makeText(
                            context,
                            requireContext().getText(R.string.readout_no_category_exists),
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                    true
                }


                R.id.readout_detail_menu_delete_readout -> {
                    confirmReadoutDeletion(resultData)
                    true
                }

                else -> {
                    false
                }
            }
        }
    }

    /** Shows a confirmation dialog before deleting the current readout. */
    private fun confirmReadoutDeletion(resultData: ResultData) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.readout_delete_readout))
        val message =
            getString(
                R.string.readout_delete_readout_confirmation,
                resultData.result.siNumber
            )
        builder.setMessage(message)

        builder.setPositiveButton(R.string.general_ok) { dialog, _ ->
            selectedRaceViewModel.deleteResult(resultData.result.id)
            dialog.dismiss()
            parentFragmentManager.popBackStackImmediate()
        }

        builder.setNegativeButton(R.string.general_cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    /** Refreshes this screen after readout or category edit dialogs report changes. */
    private fun setResultListener() {
        setFragmentResultListener(ReadoutEditDialogFragment.REQUEST_READOUT_MODIFICATION) { _, bundle ->
            val resultId = bundle.getString(
                ReadoutEditDialogFragment.BUNDLE_RESULT_ID
            )
            val newData =
                selectedRaceViewModel.getResultData(UUID.fromString(resultId))
            resultData = newData
            populateFields()

        }

        setFragmentResultListener(CategoryEditDialogFragment.REQUEST_CATEGORY_MODIFICATION) { _, bundle ->
            val categoryId = bundle.getString(CategoryEditDialogFragment.BUNDLE_KEY_CATEGORY_ID)
            if (categoryId != null && resultData.competitorCategory?.competitor != null) {
                val comp = resultData.competitorCategory?.competitor!!
                comp.categoryId = UUID.fromString(categoryId)
                selectedRaceViewModel.createOrUpdateCompetitor(comp)

                val newData =
                    selectedRaceViewModel.getResultData(resultData.result.id)
                resultData = newData
                populateFields()
            }
        }
    }

    /** Displays the readout's ordered punch list. */
    private fun setRecyclerViewAdapter(punches: List<AliasPunch>) {
        val raceType = selectedRaceViewModel.getCurrentRace()?.raceType ?: RaceType.CLASSIC
        punchRecyclerView.adapter = PunchRecyclerViewAdapter(punches, requireContext(), raceType)
    }
}
