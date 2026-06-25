package org.openardf.radiooracle.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openardf.radiooracle.backend.DataProcessor

class EventSeriesViewModel : ViewModel() {
    private val dataProcessor = DataProcessor.get()
    private val _series: MutableStateFlow<List<EventSeriesListItem>> = MutableStateFlow(emptyList())
    val series: StateFlow<List<EventSeriesListItem>> get() = _series.asStateFlow()

    init {
        viewModelScope.launch {
            dataProcessor.getEventSeries().collect { eventSeries ->
                _series.value = EventSeriesListItems.sort(eventSeries)
            }
        }
    }
}
