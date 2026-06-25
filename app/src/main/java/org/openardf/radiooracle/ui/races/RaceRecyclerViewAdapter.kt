package org.openardf.radiooracle.ui.races

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.helpers.TimeProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Recycler adapter for the race selection list and each race row's context menu.
 */
class RaceRecyclerViewAdapter(
    private var values: List<Race>, private val onRaceClicked: (raceId: UUID) -> Unit,
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
        holder.title.text = item.name
        holder.date.text =
            item.startDateTime.toLocalDate()
                .toString() + " " + TimeProcessor.hoursMinutesFormatter(item.startDateTime)
        holder.type.text = dataProcessor.raceTypeToString(item.raceType)
        holder.level.text = dataProcessor.raceLevelToString(
            item.raceLevel
        )
        holder.itemView.setOnClickListener {
            onRaceClicked(item.id)
        }
        holder.itemView.setOnLongClickListener {
            showContextMenu(holder.moreBtn, position, item)
            true
        }
        holder.moreBtn.setOnClickListener {
            showContextMenu(holder.moreBtn, position, item)
        }
    }

    private fun showContextMenu(anchor: View, position: Int, item: Race) {
        val popupMenu = PopupMenu(context, anchor)
        popupMenu.inflate(R.menu.context_menu_race)
        if (isSeriesRace(item.id)) {
            popupMenu.menu.findItem(R.id.menu_item_export_race)
                ?.setTitle(R.string.event_series_export)
            popupMenu.menu.findItem(R.id.menu_item_send_race_desktop)
                ?.setTitle(R.string.event_series_send_desktop)
        }

        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_edit_race -> {
                    onMoreClicked(0, position, item)
                    true
                }

                R.id.menu_item_export_race -> {
                    onMoreClicked(1, position, item)
                    true
                }

                R.id.menu_item_send_race_desktop -> {
                    onMoreClicked(2, position, item)
                    true
                }

                R.id.menu_item_delete_race -> {
                    onMoreClicked(3, position, item)
                    true
                }

                else -> {
                    onMoreClicked(4, position, item)
                    true
                }
            }
        }
        popupMenu.show()
    }

    private fun isSeriesRace(raceId: UUID): Boolean = runBlocking {
        dataProcessor.getEventSeriesForRace(raceId) != null
    }

    /** Returns the number of races currently displayed. */
    override fun getItemCount(): Int = values.size

    /** Returns the row's race for swipe and test hooks that operate by adapter position. */
    fun raceAt(position: Int): Race? =
        values.getOrNull(position)

    /** View holder for one race selection row. */
    inner class RaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.race_item_title)
        val date: TextView = view.findViewById(R.id.race_item_date)
        val level: TextView = view.findViewById(R.id.race_item_level)
        val type: TextView = view.findViewById(R.id.race_item_type)
        val moreBtn: ImageButton = view.findViewById(R.id.race_item_more_btn)
    }

}
