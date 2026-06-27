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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.embeddeds.AliasPunch
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.SIRecordType

/** Recycler adapter for displaying readout punches and their split times. */
class PunchRecyclerViewAdapter(
    private var values: List<AliasPunch>,
    private val context: Context,
    private val raceType: RaceType
) :
    RecyclerView.Adapter<PunchRecyclerViewAdapter.PunchViewHolder>() {
    private val dataProcessor = DataProcessor.get()

    /** Creates one punch row view holder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PunchViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_punch, parent, false)

        return PunchViewHolder(adapterLayout)
    }

    /** Returns the number of punches in the readout. */
    override fun getItemCount() = values.size

    /** Binds punch code, type, status, absolute time, and split time. */
    override fun onBindViewHolder(holder: PunchViewHolder, position: Int) {
        val item = values[position]

        holder.punchRealTime.text = item.punch.siTime.getTimeString()
        holder.punchSplit.text = TimeProcessor.durationToFormattedString(
            item.punch.split,
            dataProcessor.useMinuteTimeFormat()
        )

        // Start and finish rows use labels; control rows show SI code, alias, and status.
        when (item.punch.punchType) {
            SIRecordType.START -> {
                holder.punchSiCode.text = context.getText(R.string.punch_type_start)
            }

            SIRecordType.FINISH -> {
                holder.punchSiCode.text = context.getText(R.string.punch_type_finish)
            }

            else -> {
                holder.punchOrder.text = position.toString()
                holder.punchSiCode.text = formatControlCode(item)
                holder.punchStatus.text = when (item.punch.punchStatus) {
                    PunchStatus.VALID -> context.getString(R.string.punch_status_valid)
                    PunchStatus.INVALID -> context.getString(R.string.punch_status_invalid)
                    PunchStatus.DUPLICATE -> context.getString(R.string.punch_status_duplicate)
                    PunchStatus.UNKNOWN -> context.getString(R.string.punch_status_unknown)
                }
            }
        }
    }

    private fun formatControlCode(item: AliasPunch): String {
        if (raceType == RaceType.ORIENTEERING) {
            return if (item.alias != null) {
                "${item.punch.siCode} (${item.alias!!.name})"
            } else {
                item.punch.siCode.toString()
            }
        }

        return if (shouldUseAliases() && item.alias?.name != null) {
            item.alias!!.name
        } else {
            item.punch.siCode.toString()
        }
    }

    private fun shouldUseAliases(): Boolean {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPref.getBoolean(context.getString(R.string.key_results_use_aliases), true)
    }

    /** View holder for one punch row. */
    inner class PunchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var punchOrder: TextView = view.findViewById(R.id.punch_item_order)
        var punchSiCode: TextView = view.findViewById(R.id.punch_item_si_code)
        var punchRealTime: TextView = view.findViewById(R.id.punch_item_real_time)
        var punchSplit: TextView = view.findViewById(R.id.punch_item_split)
        var punchStatus: TextView = view.findViewById(R.id.punch_item_status)
    }
}
