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

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DesktopTopBarSeriesSelectorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun seriesDestinationAppearsForAOneRaceSeriesAndUsesTheExistingAction() {
        var openedSeries = false
        val currentRace = DesktopEventSeriesEventSummary(
            seriesEventId = "classic-80m",
            displayName = "80m Classic",
            order = 0,
            displayPosition = 1,
            eventFilePath = "80m Classic.json",
            resolvedPath = Path.of("80m Classic.json"),
            exists = true,
            isCurrentEvent = true
        )
        rule.setContent {
            MaterialTheme {
                TopBarSeriesEventSelector(
                    labelText = "Race/Series:",
                    selectionText = "80m Classic",
                    hasSeriesContext = true,
                    isSeriesSelected = false,
                    summaries = listOf(currentRace),
                    onOpenSeries = { openedSeries = true },
                    onOpenEvent = {}
                )
            }
        }

        rule.onNodeWithText("Race/Series:").performClick()
        rule.onNodeWithTag("top-bar-series-option").assertDoesNotExist()

        rule.onNodeWithTag("top-bar-race-series-selection").performClick()
        rule.onNodeWithText("Ready").assertIsDisplayed()
        rule.onNodeWithTag("top-bar-series-option").assertIsDisplayed().performClick()

        assertTrue(openedSeries)
    }

    @Test
    fun selectedSeriesIsShownAsCurrent() {
        rule.setContent {
            MaterialTheme {
                TopBarSeriesEventSelector(
                    labelText = "Race/Series:",
                    selectionText = "80m Classic",
                    hasSeriesContext = true,
                    isSeriesSelected = true,
                    summaries = emptyList(),
                    onOpenSeries = {},
                    onOpenEvent = {}
                )
            }
        }

        rule.onNodeWithTag("top-bar-race-series-selection").performClick()

        rule.onNodeWithText("Current").assertIsDisplayed()
    }
}
