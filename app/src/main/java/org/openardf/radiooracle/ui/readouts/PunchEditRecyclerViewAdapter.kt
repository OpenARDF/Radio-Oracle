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
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.sportident.SITime
import org.openardf.radiooracle.backend.wrappers.PunchEditItemWrapper
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming
import org.openardf.radiooracle.shared.sportident.SportIdentRunTimingStatus
import java.time.Duration
import java.util.UUID

class PunchEditRecyclerViewAdapter(
    var values: ArrayList<PunchEditItemWrapper>,
    private val aliases: List<Alias> = emptyList(),
    private val onPunchesChanged: (() -> Unit)? = null
) :
    RecyclerView.Adapter<PunchEditRecyclerViewAdapter.PunchViewHolder>() {
    private var semanticTimeErrorPositions: Set<Int> = emptySet()
    private val boundHolders = mutableSetOf<PunchViewHolder>()

    override fun onViewRecycled(holder: PunchViewHolder) {
        holder.clearTextWatchers()
        holder.boundItem = null
        boundHolders.remove(holder)
        super.onViewRecycled(holder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PunchViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_punch_edit, parent, false)

        return PunchViewHolder(adapterLayout)
    }

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: PunchViewHolder, position: Int) {
        val item = values[position]

        holder.clearTextWatchers()
        holder.boundItem = item
        boundHolders += holder
        holder.number.text = punchNumberLabel(position)
        holder.time.setText(item.timeDraft)
        holder.weekday.setText(item.dayDraft)
        holder.week.setText(item.weekDraft)
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
                holder.addBtn.visibility = View.VISIBLE
                holder.deleteBtn.visibility = View.INVISIBLE
            }

            SIRecordType.FINISH -> {
                holder.code.setText("F")
                holder.code.isEnabled = false
                holder.addBtn.visibility = View.INVISIBLE
                holder.deleteBtn.visibility = View.INVISIBLE
            }

            SIRecordType.CONTROL -> {
                holder.code.setText(item.codeDraft)
                holder.code.isEnabled = true
                holder.addBtn.visibility = View.VISIBLE
                holder.deleteBtn.visibility = View.VISIBLE
            }
        }

        holder.installTextWatchers(item)
        holder.applyValidationErrors(item, position)
    }

    fun refreshSemanticTimeErrors() {
        val newSemanticTimeErrorPositions = semanticTimeErrorPositions()
        if (semanticTimeErrorPositions != newSemanticTimeErrorPositions) {
            semanticTimeErrorPositions = newSemanticTimeErrorPositions
            // Validation must not rebind text or move the cursor while the user is typing.
            boundHolders.forEach { it.refreshValidationErrors() }
        }
    }

    private fun semanticTimeErrorPositions(): Set<Int> {
        val startPosition = values.indexOfFirst { it.punch.punchType == SIRecordType.START }
        val finishPosition = values.indexOfFirst { it.punch.punchType == SIRecordType.FINISH }
        val controls = values.withIndex().filter {
            it.value.punch.punchType == SIRecordType.CONTROL && it.value.isTimeValid
        }

        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = values.getOrNull(startPosition)?.takeIf { it.isTimeValid }?.punch?.siTime?.getSeconds(),
            finishSeconds = values.getOrNull(finishPosition)?.takeIf { it.isTimeValid }?.punch?.siTime?.getSeconds(),
            controlSeconds = controls.map { it.value.punch.siTime.getSeconds() }
        )

        val positions = mutableSetOf<Int>()
        timing.issues.forEach { issue ->
            when (issue.status) {
                SportIdentRunTimingStatus.FINISH_BEFORE_START -> {
                    positions += finishPosition
                }

                SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL -> {
                    positions += finishPosition
                }

                SportIdentRunTimingStatus.CONTROL_NOT_AFTER_START -> {
                    issue.controlIndex?.let { controls.getOrNull(it)?.index }?.let(positions::add)
                }

                SportIdentRunTimingStatus.CONTROL_NOT_AFTER_PREVIOUS_CONTROL -> {
                    issue.controlIndex?.let { controls.getOrNull(it)?.index }?.let(positions::add)
                }

                SportIdentRunTimingStatus.MISSING_START_OR_FINISH,
                SportIdentRunTimingStatus.VALID -> Unit
            }
        }
        return positions.filter { it >= 0 }.toSet()
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
                    position + 1,
                    PunchStatus.UNKNOWN, Duration.ZERO,
                ), false, false, true, true
            ).apply {
                timeDraft = ""
            }
        )
        values.forEachIndexed { index, row -> row.punch.order = index }
        notifyItemInserted(position + 1)
        notifyItemRangeChanged(position + 1, values.size - position - 1)
        onPunchesChanged?.invoke()
    }

    private fun deletePunch(position: Int) {
        values.removeAt(position)
        values.forEachIndexed { index, row -> row.punch.order = index }
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, values.size - position)
        onPunchesChanged?.invoke()
    }

    private fun punchNumberLabel(position: Int): String {
        return when (values[position].punch.punchType) {
            SIRecordType.START -> "S"
            SIRecordType.FINISH -> "F"
            SIRecordType.CONTROL -> values
                .take(position + 1)
                .count { it.punch.punchType == SIRecordType.CONTROL }
                .toString()
            SIRecordType.CHECK -> ""
        }
    }

    // Update draft text even when it cannot yet be parsed. Callbacks target the bound row,
    // never a captured adapter position that may have changed after insertion/deletion.
    private fun codeWatcher(item: PunchEditItemWrapper, text: String): Boolean {
        item.codeDraft = text
        val code = ControlPointsHelper.resolvePunchCode(text, aliases)
        item.isCodeValid = code != null
        if (code != null) {
            item.punch.siCode = code
            item.aliasName = aliases.firstOrNull { it.siCode == code }?.name
        }
        onPunchesChanged?.invoke()
        return item.isCodeValid
    }

    private fun timeWatcher(item: PunchEditItemWrapper, text: String): Boolean {
        item.timeDraft = text
        val time = runCatching { TimeProcessor.parseClockInput(text) }.getOrNull()
        item.isTimeValid = time != null
        time?.let(item.punch.siTime::setTime)
        onPunchesChanged?.invoke()
        return item.isTimeValid
    }

    private fun dayWatcher(item: PunchEditItemWrapper, text: String): Boolean {
        item.dayDraft = text
        val day = text.toIntOrNull()?.takeIf { it in 0..7 }
        item.isDayValid = day != null
        day?.let(item.punch.siTime::setDayOfWeek)
        onPunchesChanged?.invoke()
        return item.isDayValid
    }

    private fun weekWatcher(item: PunchEditItemWrapper, text: String): Boolean {
        item.weekDraft = text
        val week = text.toIntOrNull()?.takeIf { it in 0..3 }
        item.isWeekValid = week != null
        week?.let(item.punch.siTime::setWeek)
        onPunchesChanged?.invoke()
        return item.isWeekValid
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
        var boundItem: PunchEditItemWrapper? = null
        var number: TextView = view.findViewById(R.id.punch_edit_item_number)
        var code: EditText = view.findViewById(R.id.punch_edit_item_si_code)
        var time: EditText = view.findViewById(R.id.punch_edit_item_time)
        var weekday: EditText = view.findViewById(R.id.punch_edit_item_weekday)
        var week: EditText = view.findViewById(R.id.punch_edit_item_week)
        var addBtn: ImageButton = view.findViewById(R.id.punch_edit_item_add_btn)
        var deleteBtn: ImageButton = view.findViewById(R.id.punch_edit_item_delete_btn)
        private var codeTextWatcher: TextWatcher? = null
        private var timeTextWatcher: TextWatcher? = null
        private var weekdayTextWatcher: TextWatcher? = null
        private var weekTextWatcher: TextWatcher? = null

        fun clearTextWatchers() {
            codeTextWatcher?.let(code::removeTextChangedListener)
            timeTextWatcher?.let(time::removeTextChangedListener)
            weekdayTextWatcher?.let(weekday::removeTextChangedListener)
            weekTextWatcher?.let(week::removeTextChangedListener)
            codeTextWatcher = null
            timeTextWatcher = null
            weekdayTextWatcher = null
            weekTextWatcher = null
        }

        fun installTextWatchers(item: PunchEditItemWrapper) {
            // RecyclerView rebinds rows while scrolling; watchers must be replaced so rebinding text
            // does not mutate stale rows or hide the current explanation.
            codeTextWatcher = code.doOnTextChanged { cs: CharSequence?, _, _, _ ->
                if (item.punch.punchType != SIRecordType.START && item.punch.punchType != SIRecordType.FINISH) {
                    if (!codeWatcher(item, cs.toString())) {
                        code.error = code.context.getString(R.string.general_invalid)
                    } else {
                        refreshValidationErrors()
                    }
                }
            }

            timeTextWatcher = time.doAfterTextChanged { editable ->
                val text = editable.toString()
                // Wait for all six digits so four-digit hour/minute input can still be extended.
                // Leave partial and invalid drafts untouched so they remain easy to correct.
                if (text.length == 6 && text.all { it in '0'..'9' }) {
                    val clock = runCatching { TimeProcessor.parseClockInput(text) }.getOrNull()
                    if (clock != null) {
                        val selectionStart = time.selectionStart
                        val selectionEnd = time.selectionEnd
                        editable?.replace(0, editable.length, TimeProcessor.formatLocalTime(clock))
                        fun formattedPosition(position: Int): Int =
                            (position + (if (position > 2) 1 else 0) + (if (position > 4) 1 else 0))
                                .coerceIn(0, time.length())
                        time.setSelection(formattedPosition(selectionStart), formattedPosition(selectionEnd))
                        return@doAfterTextChanged
                    }
                }
                if (!timeWatcher(item, text)) {
                    time.error = code.context.getString(R.string.general_invalid)
                } else {
                    refreshValidationErrors()
                }
            }

            weekdayTextWatcher = weekday.doOnTextChanged { cs: CharSequence?, _, _, _ ->
                if (!dayWatcher(item, cs.toString())) {
                    weekday.error = code.context.getString(R.string.general_invalid)
                } else {
                    refreshValidationErrors()
                }
            }

            weekTextWatcher = week.doOnTextChanged { cs: CharSequence?, _, _, _ ->
                if (!weekWatcher(item, cs.toString())) {
                    week.error = code.context.getString(R.string.general_invalid)
                } else {
                    refreshValidationErrors()
                }
            }
        }

        fun installKeyboardDoneHandlers() {
            listOf(code, time, weekday, week).forEach { editor ->
                editor.setOnEditorActionListener { view, actionId, event ->
                    val isAction = actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_NEXT
                    val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                    if (!isAction && !isEnter) return@setOnEditorActionListener false
                    // Consume both key events but advance only once.
                    if (isEnter && event?.action == KeyEvent.ACTION_UP) return@setOnEditorActionListener true
                    val item = boundItem ?: return@setOnEditorActionListener true
                    val next = when {
                        view == code -> if (item.isCodeValid) time else code
                        !item.isCodeValid && code.isEnabled -> code
                        !item.isTimeValid -> time
                        !item.isDayValid -> weekday
                        !item.isWeekValid -> week
                        else -> null
                    }
                    if (next != null) {
                        next.requestFocus()
                    } else {
                        view.clearFocus()
                        val manager = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        manager?.hideSoftInputFromWindow(view.windowToken, 0)
                    }
                    true
                }
            }
        }

        fun applyValidationErrors(item: PunchEditItemWrapper, position: Int) {
            val invalid = code.context.getString(R.string.general_invalid)
            code.error = if (item.isCodeValid) null else invalid
            time.error = if (item.isTimeValid && position !in semanticTimeErrorPositions) null else invalid
            weekday.error = if (item.isDayValid) null else invalid
            week.error = if (item.isWeekValid) null else invalid
        }

        fun refreshValidationErrors() {
            val item = boundItem ?: return
            val position = values.indexOf(item)
            if (position >= 0) applyValidationErrors(item, position)
        }
    }

}
