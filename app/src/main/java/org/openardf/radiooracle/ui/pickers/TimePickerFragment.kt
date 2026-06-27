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
