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

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload

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

    suspend fun desktopUploadForSeries(seriesId: String): DesktopFileTransferUpload =
        dataProcessor.desktopUploadForSeries(seriesId)

    suspend fun exportEventSeriesPackage(uri: Uri, seriesId: String) {
        dataProcessor.exportEventSeriesPackage(uri, seriesId)
    }

    suspend fun removeSeriesGrouping(seriesId: String) {
        dataProcessor.deleteEventSeries(seriesId)
    }
}
