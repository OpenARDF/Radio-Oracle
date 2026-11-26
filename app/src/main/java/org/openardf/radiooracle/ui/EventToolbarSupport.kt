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

package org.openardf.radiooracle.ui

import android.view.MenuItem
import android.view.Menu
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.openardf.radiooracle.R
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import kotlinx.coroutines.launch

/** Shared Android event toolbar behavior for the main race workflow tabs. */
object EventToolbarSupport {
    fun bind(
        fragment: Fragment,
        toolbar: Toolbar,
        selectedRaceViewModel: SelectedRaceViewModel,
        subtitleForRace: (Race) -> String
    ) {
        toolbar.setBackgroundColor(
            ContextCompat.getColor(fragment.requireContext(), R.color.event_toolbar_background)
        )
        val switch = toolbar.menu.add(Menu.NONE, R.id.series_switch_race, 0, R.string.series_switch_race).apply {
            setIcon(R.drawable.ic_expand)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            isVisible = false
            setOnMenuItemClickListener {
                showSeriesEventDropdown(fragment, toolbar, selectedRaceViewModel)
                true
            }
        }
        applyOnPrimaryContentColor(fragment, toolbar)
        toolbar.setOnClickListener { showSeriesEventDropdown(fragment, toolbar, selectedRaceViewModel) }

        fun refresh() {
            val race = selectedRaceViewModel.getCurrentRace()
            val members = selectableSeriesMembers(selectedRaceViewModel)
            val canSwitch = members.size > 1
            toolbar.title = race?.name
            toolbar.subtitle = race?.let(subtitleForRace)
            toolbar.isClickable = canSwitch
            toolbar.isFocusable = canSwitch
            switch.isVisible = canSwitch
        }
        selectedRaceViewModel.race.observe(fragment.viewLifecycleOwner) { refresh() }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.currentRaceSeries.collect { refresh() }
            }
        }
    }

    /** Applies the app's white-on-primary toolbar content colors to titles and toolbar icons. */
    fun applyOnPrimaryContentColor(fragment: Fragment, toolbar: Toolbar) {
        val onToolbarColor = ContextCompat.getColor(fragment.requireContext(), R.color.white)
        toolbar.setTitleTextColor(onToolbarColor)
        toolbar.setSubtitleTextColor(onToolbarColor)
        toolbar.navigationIcon = toolbar.navigationIcon?.let { icon ->
            tintIcon(icon, onToolbarColor)
        }
        toolbar.overflowIcon = toolbar.overflowIcon?.let { icon ->
            tintIcon(icon, onToolbarColor)
        }
        tintMenuIcons(toolbar.menu, onToolbarColor)
    }

    private fun showSeriesEventDropdown(
        fragment: Fragment,
        toolbar: Toolbar,
        selectedRaceViewModel: SelectedRaceViewModel
    ) {
        val members = selectableSeriesMembers(selectedRaceViewModel)
        if (members.size < 2) return
        val raceId = selectedRaceViewModel.getCurrentRace()?.id ?: return
        val seriesId = selectedRaceViewModel.currentRaceSeries.value?.series?.seriesId
        PopupMenu(fragment.requireContext(), toolbar.findViewById(R.id.series_switch_race) ?: toolbar).apply {
            members.forEachIndexed { index, member ->
                val item = menu.add(1, index, index, member.displayName)
                item.isCheckable = true
                item.isChecked = member.localRaceId == raceId
            }
            menu.setGroupCheckable(1, true, true)
            setOnMenuItemClickListener { item ->
                val member = members[item.itemId]
                // A routed card or a membership edit can change the race underneath an open popup.
                if (selectedRaceViewModel.getCurrentRace()?.id == raceId && member.localRaceId != raceId &&
                    selectedRaceViewModel.currentRaceSeries.value?.series?.seriesId == seriesId &&
                    selectableSeriesMembers(selectedRaceViewModel).any { it.localRaceId == member.localRaceId }) {
                    selectedRaceViewModel.setRace(member.localRaceId)
                }
                true
            }
            show()
        }
    }

    private fun tintMenuIcons(menu: Menu, color: Int) {
        for (index in 0 until menu.size) {
            val item = menu[index]
            item.icon = item.icon?.let { icon ->
                tintIcon(icon, color)
            }
        }
    }

    private fun tintIcon(icon: android.graphics.drawable.Drawable, color: Int) =
        DrawableCompat.wrap(icon.mutate()).apply {
            DrawableCompat.setTint(this, color)
        }

    private fun selectableSeriesMembers(selectedRaceViewModel: SelectedRaceViewModel): List<EventSeriesMember> {
        val currentRaceId = selectedRaceViewModel.getCurrentRace()?.id ?: return emptyList()
        val currentSeries = selectedRaceViewModel.currentRaceSeries.value ?: return emptyList()
        return currentSeries.orderedMembers().takeIf { members -> members.any { it.localRaceId == currentRaceId } }.orEmpty()
    }
}
