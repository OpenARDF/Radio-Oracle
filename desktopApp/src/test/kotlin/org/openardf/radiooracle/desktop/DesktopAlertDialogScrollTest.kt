package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DesktopAlertDialogScrollTest(private val fontScale: Float, private val height: Int) {
    @get:Rule val rule = createComposeRule()

    @Test fun growingMessagesRemainReachableAndActionsStayPinned() {
        var expanded by mutableStateOf(false)
        var canceled = 0
        var accepted = 0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    DesktopAlertDialog(
                        onDismissRequest = { canceled++ }, title = { Text("Review import") },
                        modifier = Modifier.width(480.dp).heightIn(max = height.dp),
                        text = { Column {
                            Text("Import summary")
                            if (expanded) repeat(40) { Text("Warning $it: Check the selected course and controls.") }
                            Text("Last review option")
                        } },
                        confirmButton = { Button(onClick = { accepted++ }) { Text("Accept") } },
                        dismissButton = { Button(onClick = { canceled++ }) { Text("Cancel") } }
                    )
                }
            }
        }
        rule.onNodeWithText("Last review option").assertIsDisplayed()
        val smallHeight = rule.onNodeWithTag("desktop-alert").fetchSemanticsNode().boundsInRoot.height
        rule.runOnIdle { expanded = true }
        rule.onNodeWithText("Last review option").assertIsNotDisplayed()
        val before = rule.onNodeWithText("Accept").fetchSemanticsNode().boundsInRoot
        val viewport = rule.onNodeWithTag("workspace-scroll")
        viewport.performMouseInput { moveTo(center); scroll(10_000f) }
        rule.waitForIdle()
        rule.onNodeWithText("Last review option").assertIsDisplayed()
        rule.onNodeWithTag("workspace-scrollbar").assertIsDisplayed()
        assertEquals(before, rule.onNodeWithText("Accept").fetchSemanticsNode().boundsInRoot)
        viewport.performSemanticsAction(SemanticsActions.RequestFocus)
        viewport.performKeyInput { pressKey(Key.MoveHome) }
        rule.onNodeWithText("Import summary").assertIsDisplayed()
        viewport.performKeyInput { pressKey(Key.MoveEnd) }
        rule.onNodeWithText("Last review option").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(1, canceled)
        assertEquals(0, accepted)
        if (height == 520) assertTrue("Short alerts must wrap their content", smallHeight < height)
    }

    @Test fun pageNavigationFromAnEditorPreservesItsTextAndReachesTheLastField() {
        var value by mutableStateOf("Station 71")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    DesktopAlertDialog(
                        onDismissRequest = {}, title = { Text("Edit controls") },
                        modifier = Modifier.width(480.dp).heightIn(max = height.dp),
                        text = { Column {
                            TextField(value, { value = it }, singleLine = true, modifier = Modifier.testTag("field"))
                            repeat(25) { Text("Course control ${it + 1}", Modifier.height(48.dp)) }
                            Text("Last field")
                        } },
                        confirmButton = { Button(onClick = {}) { Text("Save changes") } },
                        dismissButton = { Button(onClick = {}) { Text("Cancel") } }
                    )
                }
            }
        }
        val field = rule.onNodeWithTag("field")
        field.performClick()
        field.performKeyInput { pressKey(Key.MoveEnd); pressKey(Key.DirectionLeft) }
        field.assertIsDisplayed()
        repeat(12) { field.performKeyInput { pressKey(Key.PageDown) } }
        rule.onNodeWithText("Last field").assertIsDisplayed()
        rule.onNodeWithText("Save changes").assertIsDisplayed()
        assertEquals("Station 71", value)
        repeat(12) { field.performKeyInput { pressKey(Key.PageUp) } }
        field.assertIsDisplayed()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "fontScale={0}, height={1}")
        fun viewports() = listOf(1f, 1.5f, 2f).flatMap { scale -> listOf(320, 520).map { arrayOf<Any>(scale, it) } }
    }
}
