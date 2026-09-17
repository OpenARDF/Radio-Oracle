package org.openardf.radiooracle.desktop

import java.nio.file.Path
import java.nio.file.Files
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asSkiaBitmap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

class DesktopIofControlMappingReviewTest {
    @get:Rule val rule = createComposeRule()

    private fun review(id: String = "B"): PendingIofControlMappingReview {
        val original = DesktopCourseImportAvailabilityTest.project()
        val xml = DesktopIofCourseAnalysisTest().xml().replace("31", id)
        val sources = IofXmlImports.validatedCourseControlSources(xml, IofXmlSchemaResource.loadBundledSchema())
        return PendingIofControlMappingReview(Path.of("condes.xml"), DesktopCourseImportTransaction.prepare(original),
            xml, sources, IofCourseControlMappings.prefill(sources, original))
    }

    @Test fun missingCodeBlocksNextAndEditedNameAndRoleReachEveryCourseWithoutApplying() {
        val review = review()
        val original = review.transaction.baseProject
        val before = EventProjectFileJson.encode(original)
        var candidate: EventProjectFile? = null
        rule.setContent { MaterialTheme {
            IofControlMappingReviewDialog(review, onReview = { rows, bends ->
                candidate = EventProjectEditor.importIofCourseData(original,
                    IofXmlImports.courseDataWithControlMappings(review.xml, original.raceData.race, rows, bends).parsedData).projectFile
                null
            }, onCancel = {})
        } }
        rule.onNodeWithText("Review Courses").assertIsNotEnabled()
        rule.onNodeWithTag("xml-name-B").performScrollTo().performTextReplacement("Finish Beacon")
        rule.onNodeWithTag("xml-si-B").performScrollTo().performTextInput("79")
        rule.onNodeWithText("Review Courses").assertIsEnabled().performClick()
        rule.runOnIdle {
            val beacon = candidate!!.raceData.controls.single { it.siCode == 79 }
            assertEquals("Finish Beacon", EventControlCatalog.displayLabel(beacon))
            assertTrue(candidate!!.raceData.categories.all { it.controlPoints.any { it.controlId == beacon.id } })
            assertEquals(before, EventProjectFileJson.encode(original))
        }
        rule.onNodeWithTag("xml-role-B").performScrollTo().performClick()
        rule.onNodeWithText("Spectator").performClick()
        rule.onNodeWithText("Review Courses").performClick()
        rule.runOnIdle { assertEquals(ProtectedCourseObjectType.SPECTATOR,
            candidate!!.raceData.categories.first().category.courseInfo!!.courseObjects[1].type) }
    }

    @Test fun duplicateCodeAndPreparationFailureStayInReviewAndCancellationDoesNotMutate() {
        val review = review()
        val original = review.transaction.baseProject
        var canceled = false
        rule.setContent { MaterialTheme {
            IofControlMappingReviewDialog(review, onReview = { _, _ -> "A reviewed control conflicts with the existing race." },
                onCancel = { canceled = true })
        } }
        rule.onNodeWithTag("xml-si-B").performScrollTo().performTextReplacement("32")
        rule.onNodeWithText("Review Courses").assertIsNotEnabled()
        rule.onNodeWithTag("xml-si-B").performTextReplacement("79")
        rule.onNodeWithText("Review Courses").assertIsEnabled().performClick()
        rule.onNodeWithText("A reviewed control conflicts with the existing race.").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertTrue(canceled); assertTrue(original.raceData.controls.isEmpty()) }
    }

    @Test fun routePointsRequireAnExplicitOptionAndNeverBecomeStations() {
        val review = review("900")
        var candidate: IofCourseDataPreview? = null
        rule.setContent { MaterialTheme {
            IofControlMappingReviewDialog(review, onReview = { rows, bends ->
                candidate = IofXmlImports.courseDataWithControlMappings(review.xml, review.transaction.baseProject.raceData.race, rows, bends).parsedData
                null
            }, onCancel = {})
        } }
        rule.onNodeWithText("Review Courses").assertIsNotEnabled()
        rule.onNodeWithTag("xml-mapping-route-bends").performScrollTo().performClick().assertIsOn()
        rule.onNodeWithText("Review Courses").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertTrue(candidate!!.categories.all { data -> data.controlPoints.none { it.siCode == 900 } })
            assertTrue(candidate!!.categories.all { data -> data.category.courseInfo!!.courseObjects.any { it.type == ProtectedCourseObjectType.WAYPOINT } })
        }
    }

    @Test fun lateReadoutBlocksNextAndKeepsCancelAvailable() {
        val review = review("31")
        var current by mutableStateOf(review.transaction.baseProject)
        var proceeded = false
        var canceled = false
        rule.setContent { MaterialTheme {
            IofControlMappingReviewDialog(review, review.transaction.disabledReason(current),
                onReview = { _, _ -> proceeded = true; null }, onCancel = { canceled = true })
        } }
        rule.onNodeWithText("Review Courses").assertIsEnabled()
        rule.runOnIdle { current = DesktopCourseImportAvailabilityTest.withReadout(current) }
        rule.onNodeWithText("Review Courses").assertIsNotEnabled()
        rule.onNodeWithText(DesktopCourseImportAvailability.ReadoutRestriction).assertExists()
        rule.onNodeWithText("Cancel").assertIsEnabled().performClick()
        rule.runOnIdle { assertTrue(canceled); assertFalse(proceeded) }
    }

    @Test fun mappingReviewRendersWithActionsVisibleAndScrollableRows() {
        rule.setContent { MaterialTheme { IofControlMappingReviewDialog(review(), onReview = { _, _ -> null }, onCancel = {}) } }
        rule.onNodeWithTag("xml-name-B").performScrollTo()
        rule.onNodeWithText("Review Courses").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        val image = rule.onNodeWithTag("desktop-alert").captureToImage()
        val output = Path.of("build/reports/xml-mapping-review.png")
        Files.createDirectories(output.parent)
        Files.write(output, org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.bytes)
    }
}
