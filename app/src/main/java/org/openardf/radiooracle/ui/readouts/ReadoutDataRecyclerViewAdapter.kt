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
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultData
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming

class ReadoutDataRecyclerViewAdapter(
    private var values: List<ResultData>,
    private val context: Context,
    private val onReadoutClicked: (readoutData: ResultData) -> Unit,
    private val onMoreClicked: (action: Int, position: Int, readoutData: ResultData) -> Unit
) : RecyclerView.Adapter<ReadoutDataRecyclerViewAdapter.ReadoutViewHolder>() {
    val dataProcessor = DataProcessor.get()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReadoutViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_readout, parent, false)

        return ReadoutViewHolder(adapterLayout)
    }

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: ReadoutViewHolder, position: Int) {
        val item = values[position]

        if (item.competitorCategory?.competitor != null) {
            holder.competitorView.text = item.competitorCategory!!.competitor.getFullName().take(30)
        } else {
            holder.competitorView.text = item.result.cardName ?: context.getString(R.string.unknown)
        }

        if (item.competitorCategory?.category != null) {
            holder.categoryView.text = item.competitorCategory!!.category!!.name.take(10)
        } else {
            holder.categoryView.text = context.getString(R.string.no_category)
        }

        holder.siNumberView.text = if (item.result.siNumber != null) {
            item.result.siNumber.toString()
        } else {
            "-"
        }

        holder.clubView.text =
            if (item.competitorCategory?.competitor?.club?.isNotEmpty() == true) {
                item.competitorCategory?.competitor?.club?.take(13)
            } else "-"

        val resultStatusText = dataProcessor.resultStatusToShortString(item.result.resultStatus)
        holder.runTimeView.text = if (item.blocksScoreAndRunTimeDisplay()) {
            item.blockedRunTimeStatusText()
        } else {
            "${
                TimeProcessor.durationToFormattedString(
                    item.result.runTime,
                    dataProcessor.useMinuteTimeFormat()
                )
            } ($resultStatusText)"
        }

        //Set the start + finish + readout time
        holder.startTimeView.text = if (item.result.startTime != null) {
            item.result.startTime!!.getTime().toString()
        } else {
            context.getString(R.string.unknown)
        }

        holder.finishTimeView.text = if (item.result.finishTime != null) {
            item.result.finishTime!!.getTime().toString()
        } else {
            context.getString(R.string.unknown)
        }

        holder.readoutTimeView.text =
            TimeProcessor.formatLocalTime(item.result.readoutTime.toLocalTime())

        //Set readout detail navigation
        holder.itemView.setOnClickListener {
            onReadoutClicked(item)
        }
        holder.itemView.setOnLongClickListener {
            showContextMenu(holder.moreBtn, position, item)
            true
        }

        //Set color based on status
        if (item.result.resultStatus == ResultStatus.ERROR) {
            holder.itemView.setBackgroundResource(R.color.red_result_err)
        } else if (item.result.competitorId == null) {
            holder.itemView.setBackgroundResource(R.color.orange_reading)
        } else if (item.competitorCategory?.competitor?.siRent == true) {
            holder.itemView.setBackgroundResource(R.color.yellow_warning)
        } else {
            holder.itemView.setBackgroundResource(R.color.white)
        }
        val textColor = if (item.hasWarning()) {
            ContextCompat.getColor(context, R.color.red_error)
        } else {
            ContextCompat.getColor(context, R.color.black)
        }
        holder.setTextColor(textColor)

        holder.moreBtn.setOnClickListener {
            showContextMenu(holder.moreBtn, position, item)
        }
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

    private fun showContextMenu(anchor: View, position: Int, item: ResultData) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.context_menu_readout)

        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_edit_readout -> {
                    onMoreClicked(0, position, item)
                    true
                }

                R.id.menu_item_delete_readout -> {
                    onMoreClicked(1, position, item)
                    true
                }

                else -> {
                    false
                }
            }
        }
        popupMenu.show()
    }

    inner class ReadoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var competitorView: TextView = view.findViewById(R.id.readout_item_competitor)
        var siNumberView: TextView = view.findViewById(R.id.readout_item_si_number)
        var clubView: TextView = view.findViewById(R.id.readout_item_club)
        var runTimeView: TextView = view.findViewById(R.id.readout_item_run_time)
        var startTimeView: TextView = view.findViewById(R.id.readout_item_start_time)
        var finishTimeView: TextView = view.findViewById(R.id.readout_item_finish_time)
        var readoutTimeView: TextView = view.findViewById(R.id.readout_item_readout_time)
        var categoryView: TextView = view.findViewById(R.id.readout_item_category)
        var moreBtn: ImageButton = view.findViewById(R.id.readout_item_more_btn)

        fun setTextColor(color: Int) {
            competitorView.setTextColor(color)
            siNumberView.setTextColor(color)
            clubView.setTextColor(color)
            runTimeView.setTextColor(color)
            startTimeView.setTextColor(color)
            finishTimeView.setTextColor(color)
            readoutTimeView.setTextColor(color)
            categoryView.setTextColor(color)
        }
    }
}
