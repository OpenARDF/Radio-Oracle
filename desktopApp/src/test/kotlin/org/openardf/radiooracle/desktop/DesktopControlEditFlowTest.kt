package org.openardf.radiooracle.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.CourseControlEditRequest
import org.openardf.radiooracle.shared.event.CourseControlFieldChange
import org.openardf.radiooracle.shared.event.CourseControlLocationSummary
import org.openardf.radiooracle.shared.event.EventControlDetails
import org.openardf.radiooracle.shared.event.EventProjectFactory

class DesktopControlEditFlowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun applyEnablesForAnyDraftAndRejectResetCanRestoreAcceptedValues() {
        val control = EventControlDetails(
            id = "fox-1",
            label = "Fox1",
            siCode = 131,
            siCodeText = "131",
            type = ControlPointType.CONTROL,
            typeLabel = "Fox",
            scored = true,
            publicLabel = "Fox 1",
            notes = ""
        )
        val resetRevision = mutableStateOf(0)
        var applied: CourseControlEditRequest? = null
        rule.setContent {
            MaterialTheme {
                ControlDetailsPanel(
                    controls = listOf(control),
                    categories = emptyList(),
                    raceType = RaceType.CLASSIC,
                    showLocations = true,
                    editResetRevision = resetRevision.value,
                    // A control without accepted coordinates must still allow detail-only edits.
                    locationSummaries = emptyList<CourseControlLocationSummary>(),
                    onApplyControlEdit = { request -> applied = request; "Review prepared" },
                    onAddControl = { _, _, _, _, _, _ -> false },
                    onRemoveControl = {}
                )
            }
        }

        val apply = rule.onNodeWithTag("apply-control-${control.id}")
        apply.assertIsNotEnabled()
        rule.onNodeWithTag("control-public-label-${control.id}")
            .performTextReplacement("Updated Fox")
        rule.waitForIdle()
        rule.onNodeWithTag("apply-control-${control.id}").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertNotNull(applied)
            assertEquals("Updated Fox", applied!!.publicLabel)
            assertNull(applied!!.location)
        }

        // Cancel leaves the local draft intact; Reject increments the reset token used by the row.
        rule.onNodeWithTag("control-public-label-${control.id}").assertTextContains("Updated Fox")
        rule.runOnIdle { resetRevision.value += 1 }
        rule.onNodeWithTag("control-public-label-${control.id}").assertTextContains("Fox 1")
        apply.assertIsNotEnabled()
        rule.onNodeWithText("Review").assertDoesNotExist()
    }

    @Test fun reviewDialogExposesDistinctAcceptRejectAndCancelActions() {
        val project = EventProjectFactory.createEmptyProject("race", "Review fixture", "2026-09-26T09:00")
        val review = DesktopControlEditReview(
            baseProject = project,
            candidateProject = project,
            controlId = "fox-1",
            controlLabel = "Fox 1",
            fieldChanges = listOf(CourseControlFieldChange("Notes", "Blank", "Updated")),
            courseChanges = emptyList()
        )
        var action = ""
        rule.setContent {
            MaterialTheme {
                DesktopControlEditReviewDialog(
                    review = review,
                    onAccept = { action = "accept" },
                    onReject = { action = "reject" },
                    onCancel = { action = "cancel" }
                )
            }
        }

        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertEquals("cancel", action) }
        rule.onNodeWithText("Reject").performClick()
        rule.runOnIdle { assertEquals("reject", action) }
        rule.onNodeWithText("Accept Changes").performClick()
        rule.runOnIdle { assertEquals("accept", action) }
    }
}
