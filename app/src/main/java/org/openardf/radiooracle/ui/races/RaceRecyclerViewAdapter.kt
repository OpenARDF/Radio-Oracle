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

package org.openardf.radiooracle.ui.races

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import java.util.UUID

/**
 * Recycler adapter for the race selection list and each race row's context menu.
 */
class RaceRecyclerViewAdapter(
    private var values: List<RaceListItem>, private val onRaceClicked: (raceId: UUID) -> Unit,
    private val onMoreClicked: (action: Int, position: Int, race: Race) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<RaceRecyclerViewAdapter.RaceViewHolder>() {

    private val dataProcessor = DataProcessor.get()

    /** Creates one race-list row view holder. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_race, parent, false)

        return RaceViewHolder(adapterLayout)
    }

    /** Binds race summary text and click/context-menu callbacks. */
    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        val item = values[position]
        val race = item.race
        holder.separator.visibility = if (item.showTopSeparator) View.VISIBLE else View.GONE
        holder.content.setBackgroundColor(
            if (item.isSeriesMember) {
                ContextCompat.getColor(context, R.color.series_navigation_background)
            } else {
                Color.TRANSPARENT
            }
        )
        holder.series.text = item.seriesName?.let { seriesName ->
            context.getString(R.string.race_series_label, seriesName)
        }
        holder.series.visibility = if (item.seriesName == null) View.GONE else View.VISIBLE
        holder.title.text = race.name
        holder.date.text =
            race.startDateTime.toLocalDate()
                .toString() + " " + TimeProcessor.hoursMinutesFormatter(race.startDateTime)
        holder.type.text = dataProcessor.raceTypeToString(race.raceType)
        holder.level.text = dataProcessor.raceLevelToString(
            race.raceLevel
        )
        holder.itemView.setOnClickListener {
            onRaceClicked(race.id)
        }
        holder.itemView.setOnLongClickListener {
            showContextMenu(holder.moreBtn, position, item)
            true
        }
        holder.moreBtn.setOnClickListener {
            showContextMenu(holder.moreBtn, position, item)
        }
    }

    private fun showContextMenu(anchor: View, position: Int, item: RaceListItem) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.context_menu_race)
        if (item.isSeriesMember) {
            popupMenu.menu.findItem(R.id.menu_item_export_race)
                ?.setTitle(R.string.event_series_export)
            popupMenu.menu.findItem(R.id.menu_item_send_race_desktop)
                ?.setTitle(R.string.event_series_send_desktop)
        }

        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_edit_race -> {
                    onMoreClicked(0, position, item.race)
                    true
                }

                R.id.menu_item_export_race -> {
                    onMoreClicked(1, position, item.race)
                    true
                }

                R.id.menu_item_send_race_desktop -> {
                    onMoreClicked(2, position, item.race)
                    true
                }

                R.id.menu_item_delete_race -> {
                    onMoreClicked(3, position, item.race)
                    true
                }

                else -> {
                    onMoreClicked(4, position, item.race)
                    true
                }
            }
        }
        popupMenu.show()
    }

    /** Returns the number of races currently displayed. */
    override fun getItemCount(): Int = values.size

    /** Returns the row's race for swipe and test hooks that operate by adapter position. */
    fun raceAt(position: Int): Race? =
        values.getOrNull(position)?.race

    /** View holder for one race selection row. */
    inner class RaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val separator: View = view.findViewById(R.id.race_item_separator)
        val content: LinearLayout = view.findViewById(R.id.race_item_content)
        val title: TextView = view.findViewById(R.id.race_item_title)
        val date: TextView = view.findViewById(R.id.race_item_date)
        val level: TextView = view.findViewById(R.id.race_item_level)
        val type: TextView = view.findViewById(R.id.race_item_type)
        val series: TextView = view.findViewById(R.id.race_item_series)
        val moreBtn: ImageButton = view.findViewById(R.id.race_item_more_btn)
    }

}
