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

package org.openardf.radiooracle.ui.competitors

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import de.codecrafters.tableview.TableDataAdapter
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime


class CompetitorTableViewAdapter(
    private var values: List<CompetitorData>,
    private var display: CompetitorTableDisplayType,
    private val context: Context,
    private val race: Race,
    private val onMoreClicked: (action: Int, position: Int, competitor: CompetitorData) -> Unit,
) : TableDataAdapter<CompetitorData>(context, values) {
    private val dataProcessor = DataProcessor.get()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var updatingJob: kotlinx.coroutines.Job? = null

    init {
        if (display == CompetitorTableDisplayType.ON_THE_WAY) {
            startUpdating()
        }
    }

    private fun startUpdating() {
        updatingJob?.cancel()
        updatingJob = scope.launch {
            while (true) {
                notifyDataSetChanged() // rebind all cells
                delay(1000)
            }
        }
    }

    override fun getCellView(rowIndex: Int, columnIndex: Int, parentView: ViewGroup?): View {
        val item = values[rowIndex]
        if (columnIndex == ACTION_COLUMN_INDEX) {
            return actionCellView(rowIndex, parentView, item)
        }

        val view = layoutInflater.inflate(R.layout.competitor_table_cell, parentView, false)
        val cell: TextView = view.findViewById(R.id.competitor_table_cell_text)

        Log.d("Adapter", "redraw row=$rowIndex col=$columnIndex")

        when (display) {

            CompetitorTableDisplayType.OVERVIEW -> {
                when (columnIndex) {
                    0 -> cell.text =
                        item.competitorCategory.competitor.startNumber.toString()

                    1 -> {
                        cell.text =
                            item.competitorCategory.competitor.getFullName()
                    }

                    2 -> cell.text = item.competitorCategory.competitor.club
                    3 -> cell.text = item.competitorCategory.category?.name
                        ?: context.getString(R.string.no_category)

                    4 -> cell.text =
                        item.competitorCategory.competitor.siNumber?.toString()
                            ?: "-"
                }
            }

            CompetitorTableDisplayType.START_LIST -> {
                when (columnIndex) {
                    0 -> cell.text =
                        item.competitorCategory.competitor.startNumber.toString()

                    1 -> {
                        if (item.competitorCategory.competitor.drawnRelativeStartTime != null) {
                            cell.text =
                                TimeProcessor.durationToFormattedString(
                                    item.competitorCategory.competitor.drawnRelativeStartTime!!,
                                    true
                                )
                        } else {
                            cell.text = "-"
                        }
                    }

                    2 -> cell.text =
                        item.competitorCategory.competitor.getFullName()

                    3 -> cell.text = item.competitorCategory.category?.name
                        ?: context.getString(R.string.no_category)

                    4 -> cell.text =
                        item.competitorCategory.competitor.siNumber?.toString()
                            ?: "-"
                }
            }

            CompetitorTableDisplayType.FINISH_REACHED -> {
                when (columnIndex) {
                    0 -> {
                        cell.text =
                            item.competitorCategory.competitor.getFullName()
                    }

                    1 -> {
                        cell.text = item.competitorCategory.category?.name
                            ?: context.getString(R.string.no_category)
                    }

                    2 -> {
                        cell.text =
                            TimeProcessor.durationToFormattedString(
                                item.readoutData!!.result.runTime,
                                dataProcessor.useMinuteTimeFormat()
                            )
                    }

                    3 -> {
                        cell.text = item.readoutData!!.result.startTime?.localTimeFormatter() ?: ""
                    }

                    4 -> {
                        cell.text = item.readoutData!!.result.finishTime?.localTimeFormatter() ?: ""
                    }
                }
            }

            CompetitorTableDisplayType.ON_THE_WAY -> {
                when (columnIndex) {
                    0 -> cell.text = item.competitorCategory.competitor.getFullName()
                    1 -> cell.text = item.competitorCategory.category?.name
                        ?: context.getString(R.string.no_category)

                    2 -> {
                        val start = item.competitorCategory.competitor.drawnRelativeStartTime
                        cell.text = if (start != null) {
                            TimeProcessor.durationToFormattedString(start, true)
                        } else "-"
                    }

                    3 -> {
                        val start = item.competitorCategory.competitor.drawnRelativeStartTime
                        val runDuration = if (start != null) {
                            TimeProcessor.runDurationFromStart(
                                race.startDateTime, start,
                                LocalDateTime.now()
                            )
                        } else null
                        cell.text = runDuration?.let {
                            TimeProcessor.durationToFormattedString(
                                it,
                                dataProcessor.useMinuteTimeFormat()
                            )
                        } ?: "-"
                    }

                    4 -> {
                        val start = item.competitorCategory.competitor.drawnRelativeStartTime
                        if (start != null && item.readoutData == null) {
                            val limit =
                                item.competitorCategory.category?.timeLimit ?: race.timeLimit
                            val toLimit = TimeProcessor.durationToLimit(
                                race.startDateTime,
                                start,
                                limit,
                                LocalDateTime.now()
                            )
                            cell.text = toLimit?.let {
                                TimeProcessor.durationToFormattedString(it, true)
                            } ?: "-"
                        } else {
                            cell.text = "-"
                        }
                    }
                }
            }

            CompetitorTableDisplayType.SI_RENT -> {
                when (columnIndex) {
                    0 -> cell.text =
                        item.competitorCategory.competitor.siNumber?.toString()
                            ?: "-"

                    1 -> cell.text = item.competitorCategory.competitor.getFullName()
                    2 -> cell.text = item.competitorCategory.category?.name
                        ?: context.getString(R.string.no_category)

                    3 -> {
                        cell.text = item.readoutData?.result?.startTime?.localTimeFormatter() ?: ""
                    }

                    4 -> {
                        cell.text = item.readoutData?.result?.finishTime?.localTimeFormatter() ?: ""
                    }
                }
            }
        }

        view.setOnClickListener {
            onMoreClicked(0, rowIndex, item)
        }

        //Set context menu
        view.setOnLongClickListener { w ->
            showContextMenu(w, rowIndex, item)
            true
        }
        return view
    }

    private fun actionCellView(rowIndex: Int, parentView: ViewGroup?, item: CompetitorData): View {
        val view = layoutInflater.inflate(R.layout.competitor_table_action_cell, parentView, false)
        val moreButton: ImageButton = view.findViewById(R.id.competitor_table_action_more)
        moreButton.setOnClickListener {
            showContextMenu(moreButton, rowIndex, item)
        }
        view.setOnClickListener {
            showContextMenu(moreButton, rowIndex, item)
        }
        return view
    }

    private fun showContextMenu(anchor: View, rowIndex: Int, item: CompetitorData) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.context_menu_competitor)

        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_edit_competitor -> {
                    onMoreClicked(0, rowIndex, item)
                    true
                }

                R.id.menu_item_delete_competitor -> {
                    onMoreClicked(1, rowIndex, item)
                    true
                }

                else -> {
                    false
                }
            }
        }
        popupMenu.show()
    }

    private companion object {
        const val ACTION_COLUMN_INDEX = 5
    }
}
