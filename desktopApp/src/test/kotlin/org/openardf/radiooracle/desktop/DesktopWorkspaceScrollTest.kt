package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class DesktopWorkspaceScrollTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shortViewportCanReachLastRowAndReturnUsingKeyboard() {
        rule.setContent {
            MaterialTheme {
                DesktopWorkspaceScroll(Modifier.size(600.dp, 240.dp)) {
                    repeat(30) { Text("Fox ${it + 1}", Modifier.height(56.dp)) }
                }
            }
        }
        rule.onNodeWithText("Fox 30").assertIsNotDisplayed()
        val viewport = rule.onNodeWithTag("workspace-scroll")
        viewport.performSemanticsAction(SemanticsActions.RequestFocus)
        viewport.performKeyInput { pressKey(Key.PageDown) }
        rule.waitForIdle()
        rule.onNodeWithText("Fox 1").assertIsNotDisplayed()
        viewport.performKeyInput { pressKey(Key.MoveEnd) }
        rule.onNodeWithText("Fox 30").assertIsDisplayed()
        viewport.performKeyInput { pressKey(Key.MoveHome) }
        rule.onNodeWithText("Fox 1").assertIsDisplayed()
        viewport.performMouseInput { scroll(30f) }
        rule.waitForIdle()
        rule.onNodeWithText("Fox 1").assertIsNotDisplayed()
        rule.onNodeWithTag("workspace-scrollbar").assertIsDisplayed()
    }

    @Test fun breadcrumbsReturnToAncestorsWithoutReplayingCommandsAndKeepDirtyGuard() {
        val root = DesktopNavState()
        val controls = root.enter(DesktopNavigation.itemById(root.workflow, "setup.controls")!!)
        val elevation = controls.enter(DesktopNavigation.itemById(root.workflow, "setup.controls.elevation-cache")!!)
        val trail = DesktopNavigation.breadcrumbStates(elevation)
        assertEquals(listOf("Setup", "Controls", "Elevation Data"), trail.map { it.first })
        assertEquals(root, trail.first().second)
        assertEquals(controls, trail[1].second)
        assertEquals(elevation, trail.last().second)
        assertTrue(DesktopNavigation.shouldGuardDirtySubmenuExit(elevation, trail[1].second, true))
        assertFalse(DesktopNavigation.shouldGuardDirtySubmenuExit(elevation, trail[1].second, false))
    }

    @Test fun pageKeysScrollWithEditorFocusWithoutChangingTextOrStealingCursorKeys() {
        var text by mutableStateOf("Fox 1")
        rule.setContent { MaterialTheme {
            DesktopWorkspaceScroll(Modifier.size(600.dp, 240.dp)) {
                TextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("editor"))
                repeat(15) { Text("Row $it", Modifier.height(56.dp)) }
            }
        } }
        val editor = rule.onNodeWithTag("editor")
        editor.performClick()
        editor.performKeyInput { pressKey(Key.MoveEnd); pressKey(Key.DirectionLeft) }
        rule.onNodeWithText("Row 0").assertIsDisplayed()
        editor.performKeyInput { pressKey(Key.PageDown) }
        rule.onNodeWithText("Row 0").assertIsNotDisplayed()
        assertEquals("Fox 1", text)
        editor.performKeyInput { pressKey(Key.PageUp) }
        rule.onNodeWithText("Row 0").assertIsDisplayed()
    }
}
