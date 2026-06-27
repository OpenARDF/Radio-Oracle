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

import android.view.Menu
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
        val onToolbarColor = ContextCompat.getColor(fragment.requireContext(), R.color.white)
        toolbar.setTitleTextColor(onToolbarColor)
        toolbar.setSubtitleTextColor(onToolbarColor)
        toolbar.overflowIcon = toolbar.overflowIcon?.let { icon ->
            DrawableCompat.wrap(icon.mutate()).apply {
                DrawableCompat.setTint(this, onToolbarColor)
            }
        }
        tintMenuIcons(toolbar.menu, onToolbarColor)
        toolbar.setOnClickListener {
            showSeriesEventDropdown(fragment, toolbar, selectedRaceViewModel)
        }

        selectedRaceViewModel.race.observe(fragment.viewLifecycleOwner) { race ->
            toolbar.title = race?.name
            toolbar.subtitle = race?.let(subtitleForRace)
            updateDropdownEnabledState(toolbar, selectedRaceViewModel)
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            fragment.viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedRaceViewModel.currentRaceSeries.collect {
                    updateDropdownEnabledState(toolbar, selectedRaceViewModel)
                }
            }
        }
    }

    private fun updateDropdownEnabledState(
        toolbar: Toolbar,
        selectedRaceViewModel: SelectedRaceViewModel
    ) {
        val enabled = selectableSeriesMembers(selectedRaceViewModel).isNotEmpty()
        toolbar.isClickable = enabled
        toolbar.isFocusable = enabled
    }

    private fun showSeriesEventDropdown(
        fragment: Fragment,
        toolbar: Toolbar,
        selectedRaceViewModel: SelectedRaceViewModel
    ) {
        val members = selectableSeriesMembers(selectedRaceViewModel)
        if (members.isEmpty()) {
            return
        }
        PopupMenu(fragment.requireContext(), toolbar).apply {
            members.forEachIndexed { index, member ->
                menu.add(Menu.NONE, index, index, member.displayName)
            }
            setOnMenuItemClickListener { item ->
                members.getOrNull(item.itemId)?.let { member ->
                    selectedRaceViewModel.setRace(member.localRaceId)
                    true
                } ?: false
            }
            show()
        }
    }

    private fun tintMenuIcons(menu: Menu, color: Int) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            item.icon = item.icon?.let { icon ->
                DrawableCompat.wrap(icon.mutate()).apply {
                    DrawableCompat.setTint(this, color)
                }
            }
        }
    }

    private fun selectableSeriesMembers(selectedRaceViewModel: SelectedRaceViewModel): List<EventSeriesMember> {
        val currentRaceId = selectedRaceViewModel.getCurrentRace()?.id ?: return emptyList()
        val currentSeries = selectedRaceViewModel.currentRaceSeries.value ?: return emptyList()
        return currentSeries.orderedMembers()
            .filter { member -> member.localRaceId != currentRaceId }
    }
}
