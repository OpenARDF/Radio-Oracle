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

package org.openardf.radiooracle.ui.races

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.sounds.SoundProcessor
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.ui.pickers.DatePickerFragment
import org.openardf.radiooracle.ui.pickers.TimePickerFragment
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class RaceEditDialogFragment : DialogFragment() {
    private val args: RaceEditDialogFragmentArgs by navArgs()
    private val dataProcessor = DataProcessor.get()

    private lateinit var nameEditText: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKey: TextInputEditText
    private lateinit var dateView: TextInputEditText
    private lateinit var startTimeView: TextInputEditText
    private lateinit var limitEditText: TextInputEditText
    private lateinit var raceTypePicker: MaterialAutoCompleteTextView
    private lateinit var raceLevelPicker: MaterialAutoCompleteTextView
    private lateinit var raceBandPicker: MaterialAutoCompleteTextView

    private lateinit var okButton: Button
    private lateinit var cancelButton: Button

    private lateinit var race: Race

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_edit_race, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.add_dialog)

        nameEditText = view.findViewById(R.id.race_dialog_name)
        apiKeyLayout = view.findViewById(R.id.race_dialog_external_id_layout)
        apiKey = view.findViewById(R.id.race_dialog_external_id)
        dateView = view.findViewById(R.id.race_dialog_date)
        startTimeView = view.findViewById(R.id.race_dialog_start_time)
        limitEditText = view.findViewById(R.id.race_dialog_limit)
        raceTypePicker = view.findViewById(R.id.category_dialog_type)
        raceLevelPicker = view.findViewById(R.id.race_dialog_level)
        raceBandPicker = view.findViewById(R.id.race_dialog_band)
        cancelButton = view.findViewById(R.id.race_dialog_cancel)
        okButton = view.findViewById(R.id.race_dialog_ok)


        //TODO: Process the saving - this is just to disable the filtering after screen rotation
        raceTypePicker.isSaveEnabled = false
        raceLevelPicker.isSaveEnabled = false
        raceBandPicker.isSaveEnabled = false

        populateFields()
        setButtons()
        setPickers()
    }

    /**
     * Set the date and time picker in an external dialog
     */
    private fun setPickers() {
        dateView.setOnClickListener {
            findNavController().navigate(RaceEditDialogFragmentDirections.selectDate(race.startDateTime.toLocalDate()))
        }
        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->

            race.startDateTime =
                LocalDateTime.of(
                    LocalDate.parse(bundle.getString(DatePickerFragment.BUNDLE_KEY_DATE)),
                    race.startDateTime.toLocalTime()
                )

            dateView.setText(race.startDateTime.toLocalDate().toString())
        }

        startTimeView.setOnClickListener {
            findNavController().navigate(RaceEditDialogFragmentDirections.selectTime(race.startDateTime.toLocalTime()))
        }
        setFragmentResultListener(TimePickerFragment.REQUEST_KEY_TIME) { _, bundle ->
            race.startDateTime = race.startDateTime.with(
                LocalTime.parse(bundle.getString(TimePickerFragment.BUNDLE_KEY_TIME))
            )
            startTimeView.setText(race.startDateTime.toLocalTime().toString())
        }
    }

    private fun populateFields() {

        //Create new race
        when (args.action) {
            RaceEditActions.CREATE -> {
                dialog?.setTitle(R.string.race_create)
                race = Race(
                    UUID.randomUUID(),
                    "", "",
                    LocalDateTime.now(),
                    RaceType.CLASSIC,
                    RaceLevel.PRACTICE,
                    RaceBand.M80,
                    Duration.ofMinutes(120)
                )
            }
            RaceEditActions.EDIT -> {
                race = args.race!!.copy()
                dialog?.setTitle(R.string.race_edit)
                nameEditText.setText(race.name)
            }
            else -> {
                race = args.race!!.copy()
                dialog?.setTitle(R.string.race_import)
                nameEditText.setText(race.name)

            }
        }

        dateView.setText(race.startDateTime.toLocalDate().toString())
        apiKey.setText(race.apiKey)
        apiKeyLayout.visibility = if (args.action == RaceEditActions.IMPORT && race.apiKey.isBlank()) {
            View.GONE
        } else {
            View.VISIBLE
        }
        startTimeView.setText(TimeProcessor.hoursMinutesFormatter(race.startDateTime))
        limitEditText.setText(race.timeLimit.toMinutes().toString())

        raceTypePicker.setText(dataProcessor.raceTypeToString(race.raceType), false)
        raceLevelPicker.setText(dataProcessor.raceLevelToString(race.raceLevel), false)
        raceBandPicker.setText(dataProcessor.raceBandToString(race.raceBand), false)

    }

    private fun setButtons() {
        okButton.setOnClickListener {

            //Send the arguments to create a new race
            if (validateFields()) {

                race.name = nameEditText.text.toString().trim()
                if (apiKey.text.toString().trim().isNotBlank()) {
                    race.apiKey = apiKey.text.toString().trim()
                } else {
                    race.apiKey = ""
                }
                race.raceType =
                    dataProcessor.raceTypeStringToEnum(raceTypePicker.text.toString())
                race.startDateTime = race.startDateTime.with(
                    LocalTime.parse(startTimeView.text.toString().trim())
                )
                race.raceLevel =
                    dataProcessor.raceLevelStringToEnum(raceLevelPicker.text.toString())
                race.raceBand =
                    dataProcessor.raceBandStringToEnum(raceBandPicker.text.toString())
                race.timeLimit = Duration.ofMinutes(limitEditText.text.toString().trim().toLong())

                setFragmentResult(
                    REQUEST_RACE_MODIFICATION,
                    Bundle().apply {
                        putSerializable(BUNDLE_KEY_ACTIONS, args.action)
                        putSerializable(BUNDLE_KEY_RACE, race)
                    }
                )
                //End the dialog
                dialog?.dismiss()
            }
        }
        cancelButton.setOnClickListener {
            dialog?.cancel()
        }
    }

    /**
     * Check if all the provided fields are valid
     */
    private fun validateFields(): Boolean {
        var valid = true

        if (nameEditText.text?.isBlank() == true) {
            nameEditText.error = getString(R.string.general_required)
            valid = false
        }

        //Validate start time
        if (startTimeView.text?.isBlank() == true) {
            startTimeView.error = getString(R.string.general_required)
            valid = false
        } else {
            try {
                LocalTime.parse(startTimeView.text.toString().trim())
            } catch (e: Exception) {
                startTimeView.error = getString(R.string.general_invalid)
                valid = false
            }
        }

        if (limitEditText.text?.isBlank() == false) {
            try {
                Duration.ofMinutes(limitEditText.text.toString().trim().toLong())
            } catch (e: Exception) {
                limitEditText.error = getString(R.string.general_invalid)
                valid = false
            }
        } else {
            limitEditText.error = getString(R.string.general_required)
            valid = false
        }

        if (!valid) SoundProcessor.makeErrorSound(requireContext())
        return valid
    }

    companion object {
        const val REQUEST_RACE_MODIFICATION = "REQUEST_RACE_MODIFICATION"
        const val BUNDLE_KEY_ACTIONS = "BUNDLE_KEY_ACTIONS"
        const val BUNDLE_KEY_RACE = "BUNDLE_KEY_RACE"
        const val BUNDLE_KEY_POSITION = "BUNDLE_KEY_POSITION"
    }

    enum class RaceEditActions {
        CREATE,
        EDIT,
        IMPORT
    }
}
