package org.openardf.radiooracle.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asSkiaBitmap
import java.nio.file.Files
import java.nio.file.Path
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.IofXmlImports

class DesktopIofCourseImportUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun condesRoutePointOptionRequiresAnExplicitSelectionBeforeReportPreparation() {
        val original = DesktopCourseImportAvailabilityTest.project()
        val parsed = IofXmlImports.courseData(DesktopIofCourseAnalysisTest().xml().replace("31", "900"), original.raceData.race).parsedData
        val review = PendingIofCourseDataImportReview(Path.of("route-points.xml"), DesktopCourseImportTransaction.prepare(original), parsed,
            parsed.categories.map { it.category.name }, DesktopImportPreviews.categoryDataPreview(original, "route-points.xml", parsed.categories),
            emptyList(), emptyList())
        val choices = mutableListOf<Boolean>()
        rule.setContent { MaterialTheme {
            IofCourseDataImportReviewDialog(review, { _, useBends -> choices += useBends }, {})
        } }
        rule.onNodeWithTag("iof-route-bends").assertIsOff()
        rule.onNodeWithText("Review Course Report").performClick()
        rule.onNodeWithTag("iof-route-bends").performClick().assertIsOn()
        rule.onNodeWithText("Review Course Report").performClick()
        rule.runOnIdle { assertEquals(listOf(false, true), choices); assertTrue(original.raceData.categories.isEmpty()) }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun disabledImportButtonStillShowsTheReadoutTooltip() {
        var clicks = 0
        rule.setContent { MaterialTheme {
            ControlsRouteKmlImportPanel(onSelectFile = { clicks++ },
                disabledReason = DesktopCourseImportAvailability.ReadoutRestriction)
        } }
        val button = rule.onNodeWithText("Import Controls KML/KMZ...")
        button.assertIsNotEnabled()
        button.performMouseInput { enter(center) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(DesktopCourseImportAvailability.ReadoutRestriction).assertExists()
        rule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun lateReadoutDisablesAcceptanceAndExplainsHowToProceed() {
        val original = DesktopCourseImportAvailabilityTest.project()
        val imported = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseData(DesktopIofCourseAnalysisTest().xml(), original.raceData.race).parsedData).projectFile
        val review = DesktopCourseImportReview("courses.xml", DesktopCourseImportTransaction.prepare(original), imported,
            imported.raceData.categories.map { it.category.id }.toSet(), null, analyzeIofCourses = true)
        var current by mutableStateOf(original)
        var accepted = false
        var rejected = false
        rule.setContent { MaterialTheme {
            CourseImportReportDialog(review, onApply = { accepted = true }, onCancel = { rejected = true }, currentProject = current)
        } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("W21, M21").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Accept Import").assertIsEnabled()
        rule.runOnIdle { current = DesktopCourseImportAvailabilityTest.withReadout(original) }
        rule.onNodeWithText("Accept Import").assertIsNotEnabled()
        rule.onNodeWithText(DesktopCourseImportAvailability.ReadoutRestriction).assertExists()
        rule.onNodeWithText("Reject Import").assertIsEnabled().performClick()
        rule.runOnIdle { assertTrue(rejected); assertFalse(accepted) }
    }

    @Test fun reportsPrecedeAcceptanceAndRejectDoesNotApply() {
        val original = EventProjectFactory.createEmptyProject("race", "Review", "2026-09-14T09:00")
        val imported = EventProjectEditor.importIofCourseData(original,
            IofXmlImports.courseData(DesktopIofCourseAnalysisTest().xml(), original.raceData.race).parsedData).projectFile
        val review = DesktopCourseImportReview("courses.xml", DesktopCourseImportTransaction.prepare(original), imported,
            imported.raceData.categories.map { it.category.id }.toSet(), null, analyzeIofCourses = true)
        var accepted: EventProjectFile? = null
        var rejected = false
        rule.setContent { MaterialTheme {
            CourseImportReportDialog(review, onApply = { accepted = it }, onCancel = { rejected = true })
        } }
        rule.waitUntil(30_000) { rule.onAllNodesWithText("W21, M21").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("W21, M21").assertExists()
        rule.onNodeWithText("Accept Import").assertIsEnabled()
        val image = rule.onNodeWithTag("desktop-alert").captureToImage()
        val output = Path.of("build/reports/iof-import-review.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.bytes)
        rule.onNodeWithText("Reject Import").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(rejected); assertNull(accepted) }
        rule.onNodeWithText("Accept Import").performClick()
        rule.runOnIdle {
            assertNotNull(accepted)
            assertTrue(original.raceData.categories.isEmpty())
            assertEquals(2, accepted!!.raceData.categories.size)
        }
    }
}
