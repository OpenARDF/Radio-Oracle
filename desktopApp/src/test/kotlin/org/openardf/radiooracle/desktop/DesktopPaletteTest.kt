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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPaletteTest {
    @Test
    fun exposesAndroidNavigationVocabulary() {
        assertEquals(
            listOf(
                "Radio-Oracle",
                "Race File",
                "Races",
                "Categories",
                "Course Order",
                "Competitors",
                "Competitor Files",
                "Start List",
                "Race Series",
                "Series Races",
                "Series Start Fairness",
                "Series Competitor Matching",
                "Series Validation",
                "Series Settings",
                "Controls",
                "Course Analyzer",
                "Elevation Data",
                "Import Elevation Data",
                "More...",
                "Race Validator",
                "Course Tools",
                "SPORTident",
                "Time Sync",
                "Move Course",
                "Create Course",
                "Route Generator",
                "Control Files",
                "Import Controls KML/KMZ",
                "Readouts",
                "SI Readout Settings",
                "In Forest",
                "Results",
                "Awards Results",
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
        assertEquals(0xFFE86F00L, DesktopPalette.WARNING_ARGB)
        assertEquals(0xFFC62828L, DesktopPalette.ERROR_ARGB)
    }

    @Test
    fun keepsSeriesNavigationVisuallyDistinctFromEventWorkflows() {
        assertEquals(0xFFFFD59EL, DesktopPalette.SERIES_NAVIGATION_ARGB)
    }

    @Test
    fun keepsToolsNavigationVisuallyDistinctFromWorkflowMenus() {
        assertEquals(0xFFFFF176L, DesktopPalette.TOOLS_NAVIGATION_ARGB)
    }
}
