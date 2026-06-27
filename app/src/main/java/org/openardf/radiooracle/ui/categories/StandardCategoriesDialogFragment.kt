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

import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.enums.StandardCategoryType
import org.openardf.radiooracle.ui.SelectedRaceViewModel

/** Dialog for adding one of the built-in standard category sets to the current race. */
class StandardCategoriesDialogFragment : DialogFragment() {
    private lateinit var selectedRaceViewModel: SelectedRaceViewModel
    private lateinit var presetGroup: RadioGroup
    private lateinit var okButton: Button
    private lateinit var cancelButton: Button

    /** Inflates the standard-category selection dialog layout. */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_standard_categories, container, false)
    }

    /** Sizes the dialog relative to the display width for compact phone and tablet layouts. */
    private fun DialogFragment.setWidthPercent(percentage: Int) {
        val percent = percentage.toFloat() / 100
        val dm = Resources.getSystem().displayMetrics
        val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
        val percentWidth = rect.width() * percent
        dialog?.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Initializes controls and the shared race view model. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setWidthPercent(95)
        setStyle(STYLE_NORMAL, R.style.add_dialog)
        dialog?.setTitle(R.string.category_create_standard_categories)

        val sl: SelectedRaceViewModel by activityViewModels()
        selectedRaceViewModel = sl

        okButton = view.findViewById(R.id.standard_cat_dialog_ok)
        cancelButton = view.findViewById(R.id.standard_cat_dialog_cancel)
        presetGroup = view.findViewById(R.id.standard_cat_dialog_radio_group)
        setButtons()
    }

    /** Applies the selected standard category set or cancels the dialog. */
    private fun setButtons() {
        presetGroup.check(R.id.standard_cat_dialog_btn_international)
        okButton.setOnClickListener {

            val currentCheck = when (presetGroup.checkedRadioButtonId) {
                R.id.standard_cat_dialog_btn_czech -> StandardCategoryType.CZECH
                else -> StandardCategoryType.INTERNATIONAL
            }

            selectedRaceViewModel.getCurrentRace()?.let { it1 ->
                selectedRaceViewModel.createStandardCategories(
                    currentCheck,
                    it1.id
                )
                dialog?.dismiss()
            }
        }
        cancelButton.setOnClickListener {
            dialog?.cancel()
        }
    }
}
