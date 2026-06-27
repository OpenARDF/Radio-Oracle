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

package org.openardf.radiooracle.ui.series

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.openardf.radiooracle.R

class EventSeriesRecyclerViewAdapter(
    private val values: List<EventSeriesListItem>,
    private val context: Context,
    private val onSendToDesktop: (EventSeriesListItem) -> Unit,
    private val onExport: (EventSeriesListItem) -> Unit,
    private val onRemoveGrouping: (EventSeriesListItem) -> Unit,
    private val onOpenMember: (EventSeriesMemberListItem) -> Unit
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
        holder.members.removeAllViews()
        item.members.forEach { member ->
            holder.members.addView(memberView(member))
        }
        holder.sendButton.setOnClickListener {
            onSendToDesktop(item)
        }
        holder.exportButton.setOnClickListener {
            onExport(item)
        }
        holder.removeGroupingButton.setOnClickListener {
            onRemoveGrouping(item)
        }
    }

    override fun getItemCount(): Int = values.size

    private fun memberView(member: EventSeriesMemberListItem): TextView =
        TextView(context).apply {
            text = member.displayLine
            contentDescription = context.getString(R.string.event_series_open_member, member.displayLine)
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableItemBackgroundResource())
            val verticalPadding = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(0, verticalPadding, 0, verticalPadding)
            setOnClickListener { onOpenMember(member) }
        }

    private fun selectableItemBackgroundResource(): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    inner class EventSeriesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.event_series_item_title)
        val count: TextView = view.findViewById(R.id.event_series_item_count)
        val members: LinearLayout = view.findViewById(R.id.event_series_item_members)
        val sendButton: ImageButton = view.findViewById(R.id.event_series_item_send_desktop)
        val exportButton: ImageButton = view.findViewById(R.id.event_series_item_export)
        val removeGroupingButton: ImageButton = view.findViewById(R.id.event_series_item_remove_grouping)
    }
}
