package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesktopCourseBriefReportUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun correctedIofCoursesDisplayDiagramsWithoutApplyingAnotherDesign() {
        val project = correctedIofCourseReportFixture(false)
        rule.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                        CourseReportPanel(project, emptyMap(), emptyMap(), { false })
                    }
                }
            }
        }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("Calculated ideal route").fetchSemanticsNodes().size == 2
        }
        rule.onNodeWithText("Course graphic unavailable.").assertDoesNotExist()
        rule.onAllNodesWithText(" min/km)", substring = true).assertCountEquals(2)
        rule.onNodeWithText("Run Course Analyzer", substring = true).assertDoesNotExist()
        rule.onAllNodesWithText("Calculated ideal route")[0].performScrollTo().assertIsDisplayed()
        val image = org.jetbrains.skia.Image.makeFromBitmap(rule.onRoot().captureToImage().asSkiaBitmap())
        val output = Path.of("build/reports/course-report/corrected-iof-courses.png")
        Files.createDirectories(output.parent)
        Files.write(output, image.encodeToData()!!.bytes)
        rule.onAllNodesWithText("Calculated ideal route")[1].performScrollTo().assertIsDisplayed()
    }

    @Test fun activeReportsAndTheirGraphicsAreReachableAndCsvExportRemainsAvailable() {
        val project = courseReportFixture()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                    DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                        CourseReportPanel(project, emptyMap(), emptyMap(), { false })
                    }
                    }
                }
            }
        }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("course-report-w40").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("M70").assertDoesNotExist()
        rule.onNodeWithText("Export Course Report CSV...").assertExists().performScrollTo().assertIsDisplayed()
        listOf("Horizontal length:", "Total climb:", "Effective length:", "Ideal order:", "Estimated ideal time:").forEach { label ->
            assertEquals(label, 2, rule.onAllNodesWithText(label, substring = true).fetchSemanticsNodes().size)
        }
        rule.onNodeWithText("M21").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText(" min/km)", substring = true).assertCountEquals(2)
        rule.onAllNodesWithText("Ideal order", substring = false)[0].performScrollTo().assertIsDisplayed()
        val scroll = rule.onNodeWithTag("workspace-scroll")
        scroll.performSemanticsAction(SemanticsActions.RequestFocus)
        scroll.performKeyInput { pressKey(Key.MoveEnd) }
        rule.onAllNodesWithText("Ideal order", substring = false)[1].assertIsDisplayed()
        val image = org.jetbrains.skia.Image.makeFromBitmap(rule.onRoot().captureToImage().asSkiaBitmap())
        val output = Path.of("build/reports/course-report/active-courses.png")
        Files.createDirectories(output.parent)
        Files.write(output, image.encodeToData()!!.bytes)
        scroll.performKeyInput { pressKey(Key.MoveHome) }
        rule.onNodeWithText("Export Course Report CSV...").assertIsDisplayed()
    }
}
