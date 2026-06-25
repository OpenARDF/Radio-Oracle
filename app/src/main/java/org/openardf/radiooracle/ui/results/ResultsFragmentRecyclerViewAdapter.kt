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
import org.openardf.radiooracle.backend.room.enums.PunchStatus
import org.openardf.radiooracle.backend.room.enums.ResultStatus
import org.openardf.radiooracle.backend.room.enums.SIRecordType
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.shared.domain.toResultStatusCode
import org.openardf.radiooracle.shared.sportident.SportIdentReadoutTiming
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
                if (readoutData != null) {
                    holder.competitorTime.text = if (singleResult.blocksScoreAndRunTimeDisplay()) {
                        singleResult.blockedRunTimeStatusText()
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
                competitorPoints.text = if (singleResult.readoutData?.result?.points != null) {
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

                val textColor = if (singleResult.hasWarning()) {
                    ContextCompat.getColor(context, R.color.red_error)
                } else {
                    ContextCompat.getColor(context, R.color.black)
                }
                setCompetitorTextColor(holder, textColor)
            }
        }
    }

    private fun CompetitorData.hasWarning(): Boolean =
        blocksScoreAndRunTimeDisplay() ||
            hasTimingOrPunches() && readoutTiming()?.issues?.isNotEmpty() == true ||
            readoutData?.punches?.any { it.punch.punchStatus == PunchStatus.INVALID } == true

    private fun CompetitorData.blocksScoreAndRunTimeDisplay(): Boolean =
        readoutData?.result?.resultStatus == ResultStatus.ERROR ||
            hasTimingOrPunches() && readoutTiming()?.blocksResult == true

    private fun CompetitorData.blockedRunTimeStatusText(): String =
        if (hasTimingOrPunches() && readoutTiming()?.blocksResult == true) {
            ResultStatus.ERROR.toResultStatusCode()
        } else {
            readoutData?.result?.resultStatus?.let(dataProcessor::resultStatusToShortString).orEmpty()
        }

    private fun CompetitorData.hasTimingOrPunches(): Boolean =
        readoutData?.let { readout ->
            readout.result.startTime != null ||
                readout.result.finishTime != null ||
                readout.punches.isNotEmpty()
        } == true

    private fun CompetitorData.readoutTiming() =
        readoutData?.let { readout ->
            SportIdentReadoutTiming.calculate(
                startSeconds = readout.result.startTime?.getSeconds(),
                finishSeconds = readout.result.finishTime?.getSeconds(),
                controlSeconds = readout.punches
                    .filter { it.punch.punchType == SIRecordType.CONTROL }
                    .map { it.punch.siTime.getSeconds() }
            )
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
        var index = 0
        while (index < values.size) {
            if (values[index].isExpanded) {
                expandParentRow(index)
            }
            index++
        }
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
