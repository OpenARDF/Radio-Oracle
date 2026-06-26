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

    private fun selectableSeriesMembers(selectedRaceViewModel: SelectedRaceViewModel): List<EventSeriesMember> {
        val currentRaceId = selectedRaceViewModel.getCurrentRace()?.id ?: return emptyList()
        val currentSeries = selectedRaceViewModel.currentRaceSeries.value ?: return emptyList()
        return currentSeries.orderedMembers()
            .filter { member -> member.localRaceId != currentRaceId }
    }
}
