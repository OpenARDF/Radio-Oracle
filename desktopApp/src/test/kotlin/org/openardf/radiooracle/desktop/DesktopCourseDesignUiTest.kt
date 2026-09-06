package org.openardf.radiooracle.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import java.nio.file.Files

class DesktopCourseDesignUiTest {
    @get:Rule val rule = createComposeRule()

    private fun draft(): EventProjectFile {
        val folder = Files.createTempDirectory("course-ui-")
        DesktopDebugLog.initialize(folder.resolve("logs"))
        val kml = folder.resolve("course.kml")
        Files.writeString(kml, courseWorkflowKml())
        val empty = EventProjectFactory.createEmptyProject("race", "UI fixture", "2026-09-06T09:00")
        val source = EventProjectEditor.addCategory(empty.copy(raceData = empty.raceData.copy(controls = EventControlCatalog.classicPreset("race"))), "m21", "M21")
        return EventCourseDrafts.edit(source) { DesktopCourseKmlImporter.importProtectedCourseInfo(kml, it, null, elevationProvider = { 100.0 }).first }
    }

    @Test fun reviewedUiApplyCommitsAndPersistsTheWholeDesign() {
        val draft = draft()
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        val app = DesktopCourseAnalyzer.analyze(candidate, "m21", info, info.idealOrder, prepareApplication = true).calculatedRouteApplication!!
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.newProject(draft)
        val ui = DesktopCourseDesignUi().apply { pendingApplication = app }
        var current by mutableStateOf(draft)
        rule.setContent { MaterialTheme {
            DesktopCourseDesignHost(current, null, session, ui, onChanged = { value, _ -> current = value }) { Text("Fixture") }
        } }
        rule.waitUntil(20_000) { ui.project != null }
        rule.onNodeWithTag("course-prepare-all").assertIsEnabled().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("course-apply-all").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(draft, session.currentProject)
        rule.onNodeWithTag("course-apply-all").performClick()
        rule.waitUntil(10_000) { session.currentProject?.raceData?.courseDraft == null }
        val applied = session.currentProject!!
        assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
        assertNotNull(applied.raceData.categories.single().category.courseInfo!!.appliedBindings)
        val path = Files.createTempDirectory("course-ui-save-").resolve("race.json")
        session.saveAs(path)
        assertEquals(applied.raceData, DesktopProjectFiles.read(path).raceData)
    }

    @Test fun cancelingTheActualApplyDialogPreservesTheDraftAndAppliedRace() {
        val draft = draft()
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.newProject(draft)
        val ui = DesktopCourseDesignUi().apply {
            pendingApplication = DesktopCourseAnalyzer.analyze(candidate, "m21", info, info.idealOrder, prepareApplication = true).calculatedRouteApplication
        }
        rule.setContent { MaterialTheme { DesktopCourseDesignHost(draft, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } } }
        rule.onNodeWithTag("course-apply-review").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertNull(ui.pendingApplication); assertEquals(draft, session.currentProject) }
    }
}
