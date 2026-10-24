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
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class DesktopNewCompetitionWizardUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun optionalHelpExplainsCompetitionHierarchy() {
        rule.setContent {
            MaterialTheme {
                DesktopNewCompetitionWizardDialog(
                    creationError = null,
                    onClearCreationError = {},
                    onCreate = {},
                    onDismiss = {}
                )
            }
        }

        rule.onNodeWithText("Help").performClick()

        rule.onNodeWithText("How races, series, and courses work").assertIsDisplayed()
        rule.onNodeWithText("A race is one continuous competition activity", substring = true).assertIsDisplayed()
        rule.onNodeWithText("A series is a named collection", substring = true).assertIsDisplayed()
        rule.onNodeWithText("A course is the set of controls", substring = true).assertIsDisplayed()
        rule.onNodeWithText("├── Single race", substring = true).assertIsDisplayed()
    }

    @Test
    fun secondRaceKeepsDateTimeFieldVisible() {
        rule.setContent {
            MaterialTheme {
                DesktopNewCompetitionWizardDialog(
                    creationError = null,
                    onClearCreationError = {},
                    onCreate = {},
                    onDismiss = {}
                )
            }
        }

        rule.onNodeWithTag("wizard-race-name").performTextInput("Classic 80m")
        rule.onNodeWithTag("workspace-scroll").performMouseInput { scroll(10_000f) }
        rule.onNodeWithText("Add Another Race").performClick()

        rule.onNodeWithText("Race 2 of 2").assertIsDisplayed()
        rule.onNodeWithText("Time limit (minutes)").assertIsDisplayed()
        rule.onNodeWithText("Previous Race").assertIsDisplayed()
        rule.onNodeWithTag("wizard-race-date-time").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Add Another Race").assertIsDisplayed()
        rule.onNodeWithText("Finished Adding Races").assertIsDisplayed()
    }
}
