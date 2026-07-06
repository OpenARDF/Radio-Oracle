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

package org.openardf.radiooracle.ui.readouts

import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.logging.DebugLog
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.shared.toSharedReadoutDisplayState
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.backend.wrappers.PunchEditItemWrapper
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import kotlinx.coroutines.runBlocking
import java.text.Collator
import java.time.Duration
import java.util.Locale
import java.util.UUID

class ReadoutEditDialogFragment : DialogFragment() {
    private val args: ReadoutEditDialogFragmentArgs by navArgs()
    private lateinit var selectedRaceViewModel: SelectedRaceViewModel
    private val dataProcessor = DataProcessor.get()

    private lateinit var result: Result
    private var origResult: Result? = null
    private var modified = false    // If the readout was modified or not

    private lateinit var competitors: List<Competitor>
    private lateinit var categories: List<Category>
    private val competitorArr = ArrayList<String>()
    private val categoryArr = ArrayList<String>()
    private var competitor: Competitor? = null
    private var origCategoryId: UUID? = null

    private lateinit var competitorPicker: MaterialAutoCompleteTextView
    private lateinit var competitorPickerLayout: TextInputLayout
    private lateinit var siNumberView: TextView
    private lateinit var categoryPicker: MaterialAutoCompleteTextView
    private lateinit var categoryPickerLayout: TextInputLayout
    private lateinit var raceStatusPicker: MaterialAutoCompleteTextView
    private val statusArr = ArrayList<String>()
    private lateinit var issueExplanationView: TextView
    private lateinit var editSwitch: SwitchMaterial
    private lateinit var punchEditRecyclerView: RecyclerView
    private lateinit var okButton: Button
    private lateinit var cancelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_edit_readout, container, false)
    }

    private fun DialogFragment.setWidthPercent(percentage: Int) {
        val percent = percentage.toFloat() / 100
        val dm = Resources.getSystem().displayMetrics
        val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
        val percentWidth = rect.width() * percent
        dialog?.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun sortCompetitorsLocaleSafe(competitors: List<Competitor>): List<Competitor> {
        val collator = Collator.getInstance(Locale.getDefault())
        return competitors.sortedWith { a, b ->
            collator.compare(a.lastName, b.lastName)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.add_dialog)
        setWidthPercent(98)

        val sl: SelectedRaceViewModel by activityViewModels()
        selectedRaceViewModel = sl
        competitors = sortCompetitorsLocaleSafe(selectedRaceViewModel.getCompetitors())
        categories = selectedRaceViewModel.getCategories()

        competitorPicker = view.findViewById(R.id.readout_dialog_competitor)
        competitorPickerLayout = view.findViewById(R.id.readout_dialog_competitor_layout)
        siNumberView = view.findViewById(R.id.readout_dialog_si_number)
        categoryPicker = view.findViewById(R.id.readout_dialog_category)
        categoryPickerLayout = view.findViewById(R.id.readout_dialog_category_layout)
        raceStatusPicker = view.findViewById(R.id.readout_dialog_status)
        issueExplanationView = view.findViewById(R.id.readout_dialog_issue_explanation)
        editSwitch = view.findViewById(R.id.readout_dialog_edit_switch)
        punchEditRecyclerView = view.findViewById(R.id.readout_dialog_punch_recycler_view)
        okButton = view.findViewById(R.id.readout_dialog_ok)
        cancelButton = view.findViewById(R.id.readout_dialog_cancel)

        populateFields()
        setButtons()
    }

    private fun populateFields() {
        if (args.create) {
            dialog?.setTitle(R.string.readout_create_readout)

            result =
                Result(
                    UUID.randomUUID(),
                    args.raceId,
                    siNumber = null,
                    cardType = 0,
                    checkTime = null,
                    startTime = null,
                    finishTime = null,
                    automaticStatus = true,
                    resultStatus = ResultStatus.NO_RANKING,
                    runTime = Duration.ZERO,
                    modified = true,
                    sent = false
                )

            raceStatusPicker.setText(getString(R.string.general_automatic), false)
            competitorPicker.setText(getString(R.string.readout_unknown_competitor), false)
            editSwitch.visibility = View.GONE
            punchEditRecyclerView.visibility = View.VISIBLE
            modified = true     // Manually created readout is always modified

        } else {
            dialog?.setTitle(R.string.readout_edit_readout)
            result = args.resultData!!.result
            origResult = result

            siNumberView.text = requireContext().getString(
                R.string.readout_si_number,
                result.siNumber ?: "?"
            )

            if (!args.resultData!!.result.automaticStatus) {
                raceStatusPicker.setText(
                    dataProcessor.resultStatusToString(args.resultData!!.result.resultStatus),
                    false
                )
            } else {
                raceStatusPicker.setText(getString(R.string.general_automatic), false)
            }

            if (result.competitorId != null) {
                competitor = selectedRaceViewModel.getCompetitor(result.competitorId!!)
                competitorPicker.setText(competitor?.getNameWithStartNumber())
            } else {
                competitorPicker.setText(getString(R.string.readout_unknown_competitor), false)
            }
        }

        // Category setup
        for (cat in categories) {
            categoryArr.add(cat.name)
        }

        categoryArr.add(
            0,
            getString(R.string.readout_unknown_category)
        )

        val categoryAdapter: ArrayAdapter<String> =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categoryArr
            )

        categoryPicker.setAdapter(categoryAdapter)
        setCategoryPicker()

        // Competitor setup
        for (comp in competitors) {
            competitorArr.add("${comp.getFullName()} (${comp.startNumber})")
        }
        competitorArr.add(
            0,
            getString(R.string.readout_unknown_competitor)
        ) //Add the empty competitor option
        val competitorAdapter: ArrayAdapter<String> =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                competitorArr
            )

        competitorPicker.setAdapter(competitorAdapter)

        // Punches setup
        val punchWrappers = if (args.create) {
            arrayListOf()
        } else {
            // Filter out controls only
            val filtered =
                args.resultData!!.punches.filter { it -> it.punch.punchType == SIRecordType.CONTROL }
                    .sortedBy { it.punch.order }
            PunchEditItemWrapper.getWrappers(ArrayList(filtered))
        }

        punchWrappers.add(
            0,
            PunchEditItemWrapper.getStartOrFinishWrapper(
                true,
                args.resultData?.result,
                args.raceId
            )
        )

        punchWrappers.add(
            PunchEditItemWrapper.getStartOrFinishWrapper(
                false,
                args.resultData?.result,
                args.raceId
            )
        )

        punchEditRecyclerView.adapter =
            PunchEditRecyclerViewAdapter(punchWrappers) { updateIssueExplanation() }

        //Populate the status options
        for (status in ResultStatus.entries) {
            statusArr.add(dataProcessor.resultStatusToString(status))
        }

        statusArr.add(0, getString(R.string.general_automatic))
        val statusAdapter: ArrayAdapter<String> =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                statusArr
            )

        raceStatusPicker.setAdapter(statusAdapter)
        updateIssueExplanation()
    }

    private fun setCategoryPicker() {
        // Preset the category
        if (competitor != null) {
            categoryPickerLayout.isEnabled = true

            origCategoryId = competitor!!.categoryId
            val cat = categories.find { it.id == competitor!!.categoryId }
            if (cat != null) {
                categoryPicker.setText(cat.name, false)
            } else {
                categoryPicker.setText(getString(R.string.readout_unknown_category), false)
            }
        } else {
            categoryPicker.setText("")
            categoryPickerLayout.isEnabled = false
        }
    }

    private fun setButtons() {

        //Competitor picker
        competitorPicker.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                competitorPickerLayout.error = ""
                competitor = getCompetitorFromPicker()
                result.competitorId = competitor?.id

                setCategoryPicker()
            }

        categoryPicker.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                competitor?.categoryId = getCategoryFromPicker()

            }

        raceStatusPicker.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, _, _ ->
                updateIssueExplanation()
            }

        editSwitch.setOnCheckedChangeListener { p0, checked ->
            if (checked) {
                modified = true
                editSwitch.visibility = View.GONE
                punchEditRecyclerView.visibility = View.VISIBLE
            }
        }

        okButton.setOnClickListener {
            if (validateFields()) {

                val punches = PunchEditItemWrapper.getPunches(
                    (punchEditRecyclerView.adapter as PunchEditRecyclerViewAdapter).values
                )

                // Save the competitor if category was changed
                if (competitor?.categoryId != origCategoryId && competitor != null) {
                    runBlocking {
                        selectedRaceViewModel.createOrUpdateCompetitor(competitor!!)
                    }
                }

                // Save punch data
                val selectedStatusText = raceStatusPicker.text.toString()
                val manualStatus = getResultStatusFromPicker()
                DebugLog.info(
                    "Results",
                    "Readout edit submitted result=${result.id} si=${result.siNumber} " +
                        "selectedStatus=\"$selectedStatusText\" manualStatus=${manualStatus?.name ?: "automatic"} " +
                        "modified=$modified"
                )
                runBlocking {
                    selectedRaceViewModel.processManualPunchData(
                        result,
                        punches,
                        manualStatus,
                        modified
                    )
                }

                setFragmentResult(
                    REQUEST_READOUT_MODIFICATION,
                    Bundle().apply {
                        putString(BUNDLE_RESULT_ID, result.id.toString())
                    }
                )
                dialog?.dismiss()
            }
        }

        cancelButton.setOnClickListener {
            dialog?.cancel()
        }
    }

    private fun validateFields(): Boolean {
        var valid = true

        //Check competitor
        if (result.competitorId != null
            && origResult?.competitorId != result.competitorId
            && selectedRaceViewModel.getResultByCompetitor(result.competitorId!!) != null
        ) {
            competitorPickerLayout.error = getString(R.string.readout_competitor_exists)
            valid = false
        }

        //Check punches
        if (!(punchEditRecyclerView.adapter as PunchEditRecyclerViewAdapter).isValid()) {
            valid = false
        }

        return valid
    }

    private fun updateIssueExplanation() {
        val issueExplanation = editableResultData()?.toSharedReadoutDisplayState()?.issueExplanation
        issueExplanationView.text = issueExplanation.orEmpty()
        issueExplanationView.visibility = if (issueExplanation.isNullOrBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun editableResultData(): ResultData? {
        if (args.create) {
            return null
        }
        val adapter = punchEditRecyclerView.adapter as? PunchEditRecyclerViewAdapter ?: return args.resultData
        if (!adapter.isValid()) {
            return null
        }
        val editablePunches = adapter.values.map { it.punch }
        val editableResult = result.copy(
            startTime = editablePunches.firstOrNull { it.punchType == SIRecordType.START }?.siTime?.let(::SITime),
            finishTime = editablePunches.firstOrNull { it.punchType == SIRecordType.FINISH }?.siTime?.let(::SITime),
            resultStatus = previewResultStatus()
        )
        editableResult.place = result.place
        val controlPunches = editablePunches
            .filter { it.punchType == SIRecordType.CONTROL }
            .map { punch -> AliasPunch(punch.copyForPreview(), alias = null) }
        return ResultData(
            result = editableResult,
            punches = controlPunches,
            competitorCategory = args.resultData?.competitorCategory
        )
    }

    private fun previewResultStatus(): ResultStatus {
        val manualStatus = getResultStatusFromPicker()
        if (manualStatus != null) {
            return manualStatus
        }
        return if (result.automaticStatus) {
            ResultStatus.OK
        } else {
            result.resultStatus
        }
    }

    private fun Punch.copyForPreview(): Punch =
        copy(
            siTime = SITime(siTime),
            origSiTime = SITime(origSiTime)
        )

    private fun getCompetitorFromPicker(): Competitor? {
        val compText = competitorPicker.text.toString()
        val compPos = competitorArr.indexOf(compText)
        return if (compPos > 0) {
            competitors[compPos - 1]
        } else null
    }

    private fun getCategoryFromPicker(): UUID? {
        val catText = categoryPicker.text.toString()
        val catPos = categoryArr.indexOf(catText)
        return if (catPos > 0) {
            categories[catPos - 1].id
        } else null
    }

    private fun getResultStatusFromPicker(): ResultStatus? {
        val resultStatusString = raceStatusPicker.text.toString()
        return if (resultStatusString.isNotEmpty()
            && resultStatusString == requireContext().getString(R.string.general_automatic)
        ) {
            null
        } else {
            dataProcessor.resultStatusStringToEnum(resultStatusString)
        }
    }

    companion object {
        const val REQUEST_READOUT_MODIFICATION = "REQUEST_READOUT_MODIFICATION"
        const val BUNDLE_RESULT_ID = "BUNDLE_KEY_READOUT_ID"
    }
}
