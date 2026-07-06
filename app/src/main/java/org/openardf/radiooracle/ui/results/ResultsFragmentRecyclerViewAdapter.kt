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

package org.openardf.radiooracle.ui.results

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.shared.toSharedReadoutDisplayState
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.ui.SelectedRaceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class ResultsFragmentRecyclerViewAdapter(
    var values: ArrayList<ResultWrapper>,
    var context: Context,
    var selectedRaceViewModel: SelectedRaceViewModel,
    private val openDetail: (competitorData: CompetitorData) -> Unit
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>(
    ) {
    val dataProcessor = DataProcessor.get()

    override fun onCreateViewHolder(parent: ViewGroup, child: Int): RecyclerView.ViewHolder {

        return if (child == 0) {
            val rowView: View =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recycler_item_result_category, parent, false)
            CategoryViewHolder(rowView)
        } else {
            val rowView: View =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.recycler_item_result_competitor, parent, false)
            CompetitorViewHolder(rowView)
        }
    }

    private fun toggleArrow(expandButton: ImageButton, isExpanded: Boolean) {
        if (isExpanded) {
            expandButton.setImageResource(R.drawable.ic_collapse)
        } else {
            expandButton.setImageResource(R.drawable.ic_expand)
        }
    }

    override fun getItemCount(): Int = values.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val dataList = values[position]

        if (dataList.isChild == 0) {
            holder as CategoryViewHolder
            holder.apply {
                if (dataList.displayLabel != null) {
                    categoryName.text =
                        "${dataList.displayLabel} (${dataList.finished}/${dataList.competitorData.size})"
                } else if (dataList.category != null) {
                    categoryName.text =
                        "${dataList.category.name} (${dataList.finished}/${
                            dataList.competitorData.size
                        })"
                } else {
                    categoryName.text =
                        "${context.getText(R.string.no_category)} (${dataList.finished}/${dataList.competitorData.size})"
                }
                if (dataList.competitorData.isNotEmpty()) {
                    expandButton.visibility = View.VISIBLE

                    //Set on click expansion + icon
                    holder.itemView.setOnClickListener {
                        expandOrCollapseParentItem(dataList, position)
                        toggleArrow(holder.expandButton, dataList.isExpanded)
                    }

                    holder.expandButton.setOnClickListener {
                        expandOrCollapseParentItem(dataList, position)
                        toggleArrow(holder.expandButton, dataList.isExpanded)
                    }

                } else {
                    expandButton.visibility = View.GONE
                }
                toggleArrow(holder.expandButton, dataList.isExpanded)
            }

        } else {
            holder as CompetitorViewHolder

            holder.apply {
                val singleResult = dataList.competitorData.first()

                //Set the competitor place
                if (singleResult.readoutData != null) {
                    val res = singleResult.readoutData!!.result
                    competitorPlace.text =
                        if (res.resultStatus == ResultStatus.OK) {
                            "${res.place}. ${res.resultStatus.toResultStatusCode()}"
                        } else {
                            dataProcessor.resultStatusToShortString(res.resultStatus)
                        }
                } else {
                    competitorPlace.text = "-"
                }

                var compName = singleResult.competitorCategory.competitor.getFullName().take(40)

                // Inform that the readout was modified
                if (singleResult.readoutData?.result?.modified == true) {
                    compName += " *"
                }
                competitorName.text = compName

                // Cancel previous timer job if exists
                holder.timerJob?.cancel()

                // Set the competitor time
                val competitor = singleResult.competitorCategory.competitor
                val drawnStartTime = competitor.drawnRelativeStartTime

                val readoutData = singleResult.readoutData
                val displayState = singleResult.toSharedReadoutDisplayState()
                if (readoutData != null) {
                    holder.competitorTime.text = if (displayState?.blocksScoreAndRunTime == true) {
                        displayState.blockedRunTimeStatusCode
                    } else {
                        TimeProcessor.durationToFormattedString(
                            readoutData.result.runTime,
                            dataProcessor.useMinuteTimeFormat()
                        )
                    }
                } else if (drawnStartTime != null) {
                    holder.timerJob = CoroutineScope(Dispatchers.Main).launch {
                        while (true) {
                            selectedRaceViewModel.getCurrentRace()?.let {
                                holder.competitorTime.text =
                                    TimeProcessor.runDurationFromStartString(
                                        it.startDateTime,
                                        drawnStartTime,
                                        dataProcessor, LocalDateTime.now()
                                    )
                            }
                            delay(1000)
                        }
                    }
                } else {
                    holder.competitorTime.text = "-"
                }

                //Set points
                competitorPoints.text = if (displayState?.blocksScoreAndRunTime == true) {
                    "-"
                } else if (singleResult.readoutData?.result?.points != null) {
                    singleResult.readoutData?.result?.points.toString()
                } else {
                    "-"
                }
                holder.itemView.setOnClickListener {
                    if (singleResult.readoutData != null) {
                        openDetail(singleResult)
                    }
                }


                if (dataList.childPosition % 2 == 1)
                    holder.itemView.setBackgroundResource(R.color.light_grey)
                else {
                    holder.itemView.setBackgroundResource(R.color.white)
                }

                val textColor = if (displayState?.hasWarning == true) {
                    ContextCompat.getColor(context, R.color.red_error)
                } else {
                    ContextCompat.getColor(context, R.color.black)
                }
                setCompetitorTextColor(holder, textColor)
            }
        }
    }

    private fun setCompetitorTextColor(holder: CompetitorViewHolder, color: Int) {
        holder.competitorPlace.setTextColor(color)
        holder.competitorName.setTextColor(color)
        holder.competitorTime.setTextColor(color)
        holder.competitorPoints.setTextColor(color)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is CompetitorViewHolder) {
            holder.timerJob?.cancel()
            holder.timerJob = null
        }
    }

    private fun expandOrCollapseParentItem(singleBoarding: ResultWrapper, position: Int) {
        if (singleBoarding.isExpanded) {
            collapseParentRow(position)
        } else {
            expandParentRow(position)
        }
    }

    private fun expandParentRow(position: Int) {
        val currentBoardingRow = values[position]
        val competitors = currentBoardingRow.competitorData
        currentBoardingRow.isExpanded = true
        var nextPosition = position
        if (currentBoardingRow.isChild == 0) {

            competitors.forEachIndexed { index, service ->
                val parentModel = ResultWrapper(isChild = 1, childPosition = index, finished = 0)
                parentModel.competitorData.add(service)
                values.add(++nextPosition, parentModel)
            }
            notifyDataSetChanged()
        }
    }

    private fun collapseParentRow(position: Int) {
        val currentBoardingRow = values[position]
        val services = currentBoardingRow.competitorData
        values[position].isExpanded = false
        if (values[position].isChild == 0) {
            services.forEach { _ ->
                values.removeAt(position + 1)
            }
            notifyDataSetChanged()
        }
    }

    fun expandAllItems() {
        values.expandAllResultParentRows()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = values[position].isChild

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class CategoryViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        val categoryName: TextView = row.findViewById(R.id.result_category_name)
        val expandButton: ImageButton = row.findViewById(R.id.down_iv)
    }

    class CompetitorViewHolder(row: View) : RecyclerView.ViewHolder(row) {
        val competitorPlace: TextView = row.findViewById(R.id.result_competitor_place)
        val competitorName: TextView = row.findViewById(R.id.result_competitor_name)
        val competitorTime: TextView = row.findViewById(R.id.result_competitor_time)
        val competitorPoints: TextView = row.findViewById(R.id.result_competitor_points)

        var timerJob: Job? = null
    }
}

internal fun MutableList<ResultWrapper>.expandAllResultParentRows() {
    var index = 0
    while (index < size) {
        val row = this[index]
        if (row.isChild != 0) {
            index++
            continue
        }

        val existingChildRows = followingChildRowCount(index)
        if (row.competitorData.isNotEmpty() && existingChildRows < row.competitorData.size) {
            row.isExpanded = true
            row.competitorData
                .drop(existingChildRows)
                .forEachIndexed { childOffset, competitorData ->
                    val childRow = ResultWrapper(
                        isChild = 1,
                        childPosition = existingChildRows + childOffset,
                        finished = 0
                    )
                    childRow.competitorData.add(competitorData)
                    add(index + existingChildRows + childOffset + 1, childRow)
                }
            index += row.competitorData.size + 1
        } else {
            row.isExpanded = row.competitorData.isNotEmpty() || row.isExpanded
            index += existingChildRows + 1
        }
    }
}

private fun List<ResultWrapper>.followingChildRowCount(parentIndex: Int): Int {
    var count = 0
    var index = parentIndex + 1
    while (index < size && this[index].isChild != 0) {
        count++
        index++
    }
    return count
}
