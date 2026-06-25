package org.openardf.radiooracle.ui.series

object EventSeriesUiVisibility {
    fun showStoredSeriesActions(seriesCount: Int): Boolean =
        seriesCount > 0
}
