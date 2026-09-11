package org.openardf.radiooracle.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.openardf.radiooracle.shared.event.EventProjectFactory
import java.nio.file.Path
import java.nio.file.Files

@RunWith(Parameterized::class)
class DesktopCourseImportReviewScrollTest(private val scale: Float) {
    @get:Rule val rule = createComposeRule()

    @Test fun pageKeysReachHiddenOptionsWhileImportActionsStayVisible() {
        showReview()
        val file = rule.onNodeWithText("File: course.gpx")
        file.assertIsDisplayed()
        // A user can focus an option, then page through the rest of this modal.
        val option = rule.onAllNodes(isToggleable()).onFirst()
        option.performClick()
        option.performKeyInput { pressKey(Key.PageDown) }
        file.assertIsNotDisplayed()
        assertActionsVisible()
        option.performKeyInput { pressKey(Key.MoveEnd) }
        finalNotice().assertIsDisplayed()
        assertActionsVisible()
        option.performKeyInput { pressKey(Key.MoveHome) }
        file.assertIsDisplayed()
    }

    @Test fun mouseWheelAndScrollbarReachTheEndWithoutMovingTheButtons() {
        showReview()
        val accept = rule.onNodeWithText("Accept Import")
        val before = accept.fetchSemanticsNode().boundsInRoot
        finalNotice().assertIsNotDisplayed()
        capture("top")
        rule.onAllNodes(hasScrollAction()).onFirst().performMouseInput {
            moveTo(center)
            scroll(10_000f)
        }
        rule.waitForIdle()
        finalNotice().assertIsDisplayed()
        assertActionsVisible()
        assertEquals(before, accept.fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("workspace-scrollbar").assertIsDisplayed()
        capture("bottom")
        rule.onNodeWithTag("workspace-scrollbar").performMouseInput {
            moveTo(Offset(center.x, height - 4f))
            press()
            moveTo(Offset(center.x, 4f), delayMillis = 500)
            release()
        }
        rule.onNodeWithText("File: course.gpx").assertIsDisplayed()
        assertActionsVisible()
    }

    private fun finalNotice() = rule.onNodeWithText("Accept Import will", substring = true)

    private fun assertActionsVisible() {
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Accept Import").assertIsDisplayed()
    }

    private fun capture(position: String) {
        val image = org.jetbrains.skia.Image.makeFromBitmap(
            rule.onNodeWithText("Review controls/route import").onParent().captureToImage().asSkiaBitmap()
        )
        val path = Path.of("build/reports/import-review/scale-$scale-$position.png")
        Files.createDirectories(path.parent)
        Files.write(path, image.encodeToData()!!.bytes)
    }

    private fun showReview() {
        val path = Path.of(requireNotNull(javaClass.getResource("/condes/course.gpx")).toURI())
        val base = EventProjectFactory.createEmptyProject("race", "Import review", "2026-09-11T09:00")
        val (updated, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path, base, null, elevationProvider = { 100.0 }, createMissingControls = true,
            createMissingCategories = true
        )
        val review = PendingCourseKmlKmzImportReview(
            "course.gpx", path, base, updated, summary, updated, summary,
            null, null, null, null, null, null, false
        )
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                MaterialTheme {
                    CourseKmlKmzImportReviewDialog(review, { _, _, _, _, _, _, _ -> }, {})
                }
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "displayScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.5f))
    }
}
