package org.openardf.radiooracle.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.openardf.radiooracle.shared.event.*

@RunWith(Parameterized::class)
class DesktopCourseImportReportDialogTest(private val scale: Float) {
    @get:Rule val rule = createComposeRule()

    @Test fun reportIsReadOnlyUntilApplyAndItsFooterRemainsAccessible() {
        val original = DesktopAuthoritativeCourseImportTest.project()
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val candidate = EventProjectEditor.replaceCategoryAssignedControls(original, "short", listOf("fox1", "fox3")) { "new-$it" }
        var current = original
        val review = DesktopCourseImportReview("courses.xml", transaction, candidate, setOf("short", "full"), null,
            notes = (1..12).map { "Imported course detail $it" })
        show(review, { prepared -> current = transaction.applyTo(current) { prepared } }, {})
        awaitReady()
        assertEquals(original, current)
        val button = rule.onNodeWithTag("apply-course-import")
        button.assertIsDisplayed()
        val bounds = button.fetchSemanticsNode().boundsInRoot
        rule.onAllNodes(hasScrollAction()).onFirst().performMouseInput { moveTo(center); scroll(10_000f) }
        rule.onNodeWithTag("cancel-course-import").assertIsDisplayed()
        assertEquals(bounds, button.fetchSemanticsNode().boundsInRoot)
        button.performClick()
        assertEquals(listOf("fox1", "fox3"), current.raceData.categories.single { it.category.id == "short" }.publicControlIds)
        assertNull(current.raceData.courseDraft)
    }

    @Test fun cancelLeavesAppliedRaceAndPendingDraftUntouched() {
        val original = EventCourseDrafts.edit(DesktopAuthoritativeCourseImportTest.project()) { it.copy(
            raceData = it.raceData.copy(race = it.raceData.race.copy(courseAnalyzerSpeedCompensationFactor = 1.25))) }
        var canceled = false
        var applied = false
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val before = EventProjectFileJson.encode(original)
        show(DesktopCourseImportReview("courses.xml", transaction, transaction.baseProject, setOf("short"), null),
            { applied = true }, { canceled = true })
        rule.onNodeWithTag("cancel-course-import").assertIsDisplayed().performClick()
        assertTrue(canceled)
        assertFalse(applied)
        assertEquals(before, EventProjectFileJson.encode(original))
    }

    @Test fun failedApplyKeepsErrorAndCancelVisible() {
        val original = DesktopAuthoritativeCourseImportTest.project()
        val transaction = DesktopCourseImportTransaction.prepare(original)
        show(DesktopCourseImportReview("courses.xml", transaction, original, setOf("short"), null),
            { error("Course data changed. Cancel and import again.") }, {})
        awaitReady()
        rule.onNodeWithTag("apply-course-import").performClick()
        rule.onAllNodes(hasScrollAction()).onFirst().performMouseInput { moveTo(center); scroll(10_000f) }
        rule.onNodeWithText("Course data changed. Cancel and import again.").assertIsDisplayed()
        rule.onNodeWithTag("cancel-course-import").assertIsDisplayed()
    }

    private fun awaitReady() = rule.waitUntil(15_000) {
        rule.onAllNodesWithTag("apply-course-import").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun show(review: DesktopCourseImportReview, apply: (EventProjectFile) -> Unit, cancel: () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(scale)) {
                MaterialTheme { CourseImportReportDialog(review, apply, cancel) }
            }
        }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "displayScale={0}")
        fun scales() = listOf(arrayOf(1f), arrayOf(1.5f))
    }
}
