package org.openardf.radiooracle.ui.pickers

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import java.time.LocalTime

/** DialogFragment wrapper that returns a selected time through the Fragment Result API. */
class TimePickerFragment : DialogFragment() {
    private val args: TimePickerFragmentArgs by navArgs()

    /** Builds a 24-hour time picker initialized from navigation arguments. */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val timeListener =
            TimePickerDialog.OnTimeSetListener { _: TimePicker, hour: Int, minute: Int ->
                val resTime = LocalTime.of(hour, minute).toString()
                setFragmentResult(
                    REQUEST_KEY_TIME,
                    Bundle().apply {
                        putString(BUNDLE_KEY_TIME, resTime)
                    }
                )
            }
        val localTime = args.curTime
        val hour: Int = localTime.hour
        val minute: Int = localTime.minute

        return TimePickerDialog(
            requireContext(),
            timeListener,
            hour,
            minute,
            true
        )
    }

    /** Fragment Result keys used to return the selected time. */
    companion object {
        const val REQUEST_KEY_TIME = "REQUEST_KEY_TIME"
        const val BUNDLE_KEY_TIME = "BUNDLE_KEY_TIME"
    }
}
