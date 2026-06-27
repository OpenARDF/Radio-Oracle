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

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.backend.wrappers.PunchEditItemWrapper
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

class PunchEditRecyclerViewAdapter(
    var values: ArrayList<PunchEditItemWrapper>
) :
    RecyclerView.Adapter<PunchEditRecyclerViewAdapter.PunchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PunchViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_punch_edit, parent, false)

        return PunchViewHolder(adapterLayout)
    }

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: PunchViewHolder, position: Int) {
        val item = values[position]

        holder.time.setText(item.punch.siTime.getTimeString())
        holder.weekday.setText(item.punch.siTime.getDayOfWeek().toString())
        holder.week.setText(item.punch.siTime.getWeek().toString())
        holder.installKeyboardDoneHandlers()

        holder.addBtn.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::addPunch)
        }

        holder.deleteBtn.setOnClickListener {
            holder.code.clearFocus()
            holder.time.clearFocus()
            holder.week.clearFocus()
            holder.weekday.clearFocus()
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::deletePunch)
        }

        //Set the start punch
        when (item.punch.punchType) {
            SIRecordType.CHECK -> {}

            SIRecordType.START -> {
                holder.code.setText("S")
                holder.code.isEnabled = false
                holder.deleteBtn.visibility = View.GONE
            }

            SIRecordType.FINISH -> {
                holder.code.setText("F")
                holder.code.isEnabled = false
                holder.addBtn.visibility = View.GONE
                holder.deleteBtn.visibility = View.GONE
            }

            SIRecordType.CONTROL -> {
                holder.code.setText(item.displayCodeText())
                holder.code.isEnabled = true
                holder.addBtn.visibility = View.VISIBLE
                holder.deleteBtn.visibility = View.VISIBLE
            }
        }

        //Set watchers
        holder.code.doOnTextChanged { cs: CharSequence?, i: Int, i1: Int, i2: Int ->
            // Omit the check for start and finish
            if (item.punch.punchType != SIRecordType.START && item.punch.punchType != SIRecordType.FINISH) {
                if (!codeWatcher(holder.bindingAdapterPosition, cs.toString())) {
                    holder.code.error = holder.code.context.getString(R.string.general_invalid)
                }
            }
        }

        holder.time.doOnTextChanged { cs: CharSequence?, i: Int, i1: Int, i2: Int ->
            if (!timeWatcher(holder.bindingAdapterPosition, cs.toString())) {
                holder.time.error = holder.code.context.getString(R.string.general_invalid)
            }
        }

        holder.weekday.doOnTextChanged { cs: CharSequence?, i: Int, i1: Int, i2: Int ->
            if (!dayWatcher(holder.bindingAdapterPosition, cs.toString())) {
                holder.weekday.error = holder.code.context.getString(R.string.general_invalid)
            }
        }

        holder.week.doOnTextChanged { cs: CharSequence?, i: Int, i1: Int, i2: Int ->
            if (!weekWatcher(holder.bindingAdapterPosition, cs.toString())) {
                holder.week.error = holder.code.context.getString(R.string.general_invalid)
            }
        }
    }

    private fun addPunch(position: Int) {
        values.add(
            position + 1, PunchEditItemWrapper(
                Punch(
                    UUID.randomUUID(),
                    values[0].punch.raceId,
                    null,
                    null,
                    0,
                    SITime(values[position].punch.siTime),
                    SITime(values[position].punch.siTime),
                    SIRecordType.CONTROL,
                    values[position].punch.order++,
                    PunchStatus.UNKNOWN, Duration.ZERO,
                ), false, true, true, true
            )
        )
        notifyItemInserted(position + 1)
    }

    private fun deletePunch(position: Int) {
        values.removeAt(position)
        notifyItemRemoved(position)
    }

    //Text watchers
    private fun codeWatcher(position: Int, text: String): Boolean {
        if (position == RecyclerView.NO_POSITION) return true
        if (values[position].matchesDisplayCodeText(text)) {
            values[position].isCodeValid = true
            return true
        }
        try {
            val code = text.toInt()
            if (SIConstants.isSICodeValid(code)) {
                values[position].punch.siCode = code
                values[position].aliasName = null
                values[position].isCodeValid = true
            } else {
                values[position].isCodeValid = false
                return false
            }
        } catch (e: Exception) {
            values[position].isCodeValid = false
            return false
        }
        return true
    }


    private fun timeWatcher(position: Int, text: String): Boolean {
        if (position == RecyclerView.NO_POSITION) return true
        //Try parsing the time into SI time
        try {
            val time = LocalTime.parse(text)
            values[position].punch.siTime.setTime(time)
            values[position].isTimeValid = true
        } catch (e: Exception) {
            values[position].isTimeValid = false
            return false
        }
        return true
    }

    private fun dayWatcher(position: Int, text: String): Boolean {
        if (position == RecyclerView.NO_POSITION) return true
        try {
            val day = text.toInt()
            if (day in 0..7) {
                values[position].punch.siTime.setDayOfWeek(day)
                values[position].isDayValid = true
            }
        } catch (e: Exception) {
            values[position].isDayValid = false
            return false
        }
        return true
    }

    private fun weekWatcher(position: Int, text: String): Boolean {
        if (position == RecyclerView.NO_POSITION) return true
        try {
            val week = text.toInt()
            if (week in 0..3) {
                values[position].punch.siTime.setWeek(week)
                values[position].isWeekValid = true
            }
        } catch (e: Exception) {
            values[position].isWeekValid = false
            return false
        }
        return true
    }

    fun isValid(): Boolean {
        for (item in values) {
            if (!item.isCodeValid || !item.isTimeValid || !item.isDayValid || !item.isWeekValid) {
                return false
            }
        }
        return true
    }

    inner class PunchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var code: EditText = view.findViewById(R.id.punch_edit_item_si_code)
        var time: EditText = view.findViewById(R.id.punch_edit_item_time)
        var weekday: EditText = view.findViewById(R.id.punch_edit_item_weekday)
        var week: EditText = view.findViewById(R.id.punch_edit_item_week)
        var addBtn: ImageButton = view.findViewById(R.id.punch_edit_item_add_btn)
        var deleteBtn: ImageButton = view.findViewById(R.id.punch_edit_item_delete_btn)

        fun installKeyboardDoneHandlers() {
            listOf(code, time, weekday, week).forEach { editor ->
                editor.setOnEditorActionListener { view, actionId, event ->
                    val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
                    val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
                    if (isDoneAction || isEnter) {
                        view.clearFocus()
                        val inputMethodManager =
                            view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

}
