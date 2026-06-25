package org.openardf.radiooracle.ui.series

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R

class EventSeriesRecyclerViewAdapter(
    private val values: List<EventSeriesListItem>,
    private val context: Context,
    private val onSendToDesktop: (EventSeriesListItem) -> Unit,
    private val onExport: (EventSeriesListItem) -> Unit
) : RecyclerView.Adapter<EventSeriesRecyclerViewAdapter.EventSeriesViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventSeriesViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_item_event_series, parent, false)
        return EventSeriesViewHolder(adapterLayout)
    }

    override fun onBindViewHolder(holder: EventSeriesViewHolder, position: Int) {
        val item = values[position]
        holder.title.text = item.name
        holder.count.text = context.resources.getQuantityString(
            R.plurals.event_series_member_count,
            item.memberCount,
            item.memberCount
        )
        holder.members.text = item.memberLines.joinToString("\n")
        holder.sendButton.setOnClickListener {
            onSendToDesktop(item)
        }
        holder.exportButton.setOnClickListener {
            onExport(item)
        }
    }

    override fun getItemCount(): Int = values.size

    inner class EventSeriesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.event_series_item_title)
        val count: TextView = view.findViewById(R.id.event_series_item_count)
        val members: TextView = view.findViewById(R.id.event_series_item_members)
        val sendButton: ImageButton = view.findViewById(R.id.event_series_item_send_desktop)
        val exportButton: ImageButton = view.findViewById(R.id.event_series_item_export)
    }
}
