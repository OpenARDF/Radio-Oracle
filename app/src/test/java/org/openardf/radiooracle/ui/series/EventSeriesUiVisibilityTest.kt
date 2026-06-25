package org.openardf.radiooracle.ui.series

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSeriesUiVisibilityTest {
    @Test
    fun hidesStoredSeriesActionsWhenNoSeriesAreStored() {
        assertFalse(EventSeriesUiVisibility.showStoredSeriesActions(0))
    }

    @Test
    fun showsStoredSeriesActionsWhenSeriesAreStored() {
        assertTrue(EventSeriesUiVisibility.showStoredSeriesActions(1))
        assertTrue(EventSeriesUiVisibility.showStoredSeriesActions(3))
    }
}
