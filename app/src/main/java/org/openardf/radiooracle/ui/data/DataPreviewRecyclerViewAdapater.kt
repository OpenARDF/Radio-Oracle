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

package org.openardf.radiooracle.ui.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.constants.DataType
import org.openardf.radiooracle.backend.files.wrappers.DataImportWrapper
import org.openardf.radiooracle.backend.helpers.ControlPointsHelper
import org.openardf.radiooracle.backend.helpers.TimeProcessor

/** Recycler adapter for the small preview table shown before importing data. */
class DataPreviewRecyclerViewAdapater(
    var value: DataImportWrapper,
    var dataType: DataType
) :
    RecyclerView.Adapter<DataPreviewRecyclerViewAdapater.DataPreviewViewHolder>() {
    private val dataProcessor = DataProcessor.get()

    /** Creates one four-column preview row. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataPreviewViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_data, parent, false)

        return DataPreviewViewHolder(adapterLayout)
    }

    /** Returns the number of preview rows, limiting competitor previews to five rows. */
    override fun getItemCount(): Int {
        return when (dataType) {
            DataType.CATEGORIES -> value.categories.size
            DataType.COMPETITORS, DataType.COMPETITOR_STARTS ->
                if (value.competitorCategories.size < 5) {
                    value.competitorCategories.size
                } else {
                    5
                }

            DataType.RESULTS_LIVE ->
                if (value.readoutData.size < 5) {
                    value.readoutData.size
                } else {
                    5
                }

            else -> 0
        }
    }

    /** Binds one category, competitor, or start-list preview row. */
    override fun onBindViewHolder(holder: DataPreviewViewHolder, position: Int) {
        when (dataType) {
            DataType.CATEGORIES -> {
                val item = value.categories[position]
                holder.columnOne.text = item.category.name
                holder.columnTwo.text = dataProcessor.genderToString(item.category.isMan)
                holder.columnThree.text = item.category.maxAge.toString()
                holder.columnFour.text =
                    ControlPointsHelper.getStringFromControlPoints(item.controlPoints)
            }

            DataType.COMPETITORS -> {
                val item = value.competitorCategories[position]
                holder.columnOne.text = item.competitor.siNumber?.toString() ?: "-"
                holder.columnTwo.text = item.competitor.getFullName()
                holder.columnThree.text = item.competitor.birthYear.toString()
                holder.columnFour.text = item.category?.name ?: "-"
            }

            DataType.COMPETITOR_STARTS -> {
                val item = value.competitorCategories[position]
                holder.columnOne.text = if (item.competitor.drawnRelativeStartTime != null) {
                    TimeProcessor.durationToFormattedString(
                        item.competitor.drawnRelativeStartTime!!, true
                    )
                } else {
                    "-"
                }
                holder.columnTwo.text = item.competitor.getFullName()
                holder.columnThree.text = item.competitor.siNumber?.toString() ?: "-"
                holder.columnFour.text = item.category?.name ?: "-"
            }

            DataType.RESULTS_LIVE -> {
                val item = value.readoutData[position].result
                holder.columnOne.text = item.siNumber?.toString() ?: "-"
                holder.columnTwo.text = item.competitorId?.toString() ?: "-"
                holder.columnThree.text = TimeProcessor.durationToFormattedString(item.runTime, true)
                holder.columnFour.text = item.resultStatus.toString()
            }

            else -> {}
        }
    }

    /** View holder for one import preview row. */
    inner class DataPreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var columnOne: TextView = view.findViewById(R.id.data_import_item_column_1)
        var columnTwo: TextView = view.findViewById(R.id.data_import_item_column_2)
        var columnThree: TextView = view.findViewById(R.id.data_import_item_column_3)
        var columnFour: TextView = view.findViewById(R.id.data_import_item_column_4)
    }

}
