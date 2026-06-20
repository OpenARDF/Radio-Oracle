package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPaletteTest {
    @Test
    fun exposesAndroidNavigationVocabulary() {
        assertEquals(
            listOf(
                "Radio-Oracle",
                "Event File",
                "Races",
                "Categories",
                "Course Order",
                "Competitors",
                "Start List",
                "Event Series",
                "Series Events",
                "Series Start Fairness",
                "Series Competitor Matching",
                "Series Validation",
                "Series Settings",
                "Controls",
                "Course Analyzer",
                "Elevation Data",
                "Import Elevation Data",
                "Import/Export",
                "Import Controls KML/KMZ",
                "Readouts",
                "SI Readout Settings",
                "In Forest",
                "Results",
                "Cloudflare Website",
                "View Public Results",
                "Live Results",
                "Local Web Server",
                "ROBIS",
                "Display Settings",
                "Readiness",
                "Settings"
            ),
            DesktopSection.entries.map { it.label }
        )
    }

    @Test
    fun keepsReaderStatusColorsAlignedWithAndroidResources() {
        assertEquals(0xFF505050L, DesktopPalette.DISCONNECTED_ARGB)
        assertEquals(0xFFFD8204L, DesktopPalette.READING_ARGB)
        assertEquals(0xFF0AE62FL, DesktopPalette.CONNECTED_ARGB)
        assertEquals(0xFFFFFF00L, DesktopPalette.WARNING_ARGB)
        assertEquals(0xFFC62828L, DesktopPalette.ERROR_ARGB)
    }
}
