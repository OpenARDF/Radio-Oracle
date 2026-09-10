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

    @Test fun importingMandatoryCornersInvalidatesDisplayedAnalysisAndLateCompletions() {
        val applied = EventCourseDrafts.candidate(draft())
        val oldSummary = analyze(applied, DesktopCourseRouteSource.Applied)
        val path = Files.createTempFile("course-ui-corners-", ".kml")
        Files.writeString(path, courseWorkflowKml().replace(
            "<LineString><coordinates>-75.0,40.0,100 ",
            "<LineString><coordinates>-75.0,40.0,100 -74.999,40.001,100 "
        ))
        val imported = EventCourseDrafts.edit(applied) {
            DesktopCourseKmlImporter.importProtectedCourseInfo(path, it, null, elevationProvider = { 100.0 }).first
        }
        assertEquals(applied.raceData.categories, imported.raceData.categories)
        val candidate = EventCourseDrafts.candidate(imported)
        val refreshed = analyze(candidate)
        assertTrue(refreshed.routeMaps.first().points.any { it.type == DesktopCourseRouteMapPointType.Waypoint })
        assertNotEquals(oldSummary.sourceSnapshotHash, refreshed.sourceSnapshotHash)

        var current by mutableStateOf(applied)
        var completed by mutableStateOf(oldSummary)
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(applied) }
        val ui = DesktopCourseDesignUi()
        rule.setContent { MaterialTheme {
            DesktopCourseDesignHost(current, null, session, ui, onChanged = { value, _ -> current = value }) {
                Text(currentCourseAnalysisResult(current, completed)?.sourceSnapshotHash ?: "Analyze again")
            }
        } }
        rule.onNodeWithText(oldSummary.sourceSnapshotHash!!).assertExists()
        rule.runOnIdle { current = imported }
        rule.onNodeWithText("Analyze again").assertExists()
        rule.waitUntil(20_000) { ui.project == candidate }
        rule.onNodeWithText("Analyze again").assertExists()
        // An old calculation can complete after the imported draft has loaded.
        rule.runOnIdle { completed = oldSummary.copy(analysisPerformedAtText = "Later completion") }
        rule.onNodeWithText("Analyze again").assertExists()
        rule.runOnIdle { completed = refreshed }
        rule.onNodeWithText(refreshed.sourceSnapshotHash!!).assertExists()
        Files.deleteIfExists(path)
    }

    @Test fun refreshedElevationAnalysisMatchesTheUpdatedDraftRatherThanTheStartingSnapshot() {
        val initial = draft()
        var current by mutableStateOf(initial)
        var completed by mutableStateOf(analyze(EventCourseDrafts.candidate(initial)))
        rule.setContent { Text(currentCourseAnalysisResult(current, completed)?.sourceSnapshotHash ?: "Analyze again") }
        val updated = EventCourseDrafts.edit(initial) { candidate ->
            candidate.copy(raceData = candidate.raceData.copy(categories = candidate.raceData.categories.map { category ->
                val info = category.category.courseInfo!!
                category.copy(category = category.category.copy(courseInfo = info.copy(
                    route = info.route.mapIndexed { index, point -> point.copy(elevationMeters = 100.0 + index) }
                )))
            }))
        }
        rule.runOnIdle { current = updated }
        rule.onNodeWithText("Analyze again").assertExists()
        val refreshed = analyze(EventCourseDrafts.candidate(updated))
        rule.runOnIdle { completed = refreshed }
        rule.onNodeWithText(refreshed.sourceSnapshotHash!!).assertExists()
        // Saving the draft does not change its identity or make the refreshed report stale.
        rule.runOnIdle { current = EventProjectFileJson.decode(EventProjectFileJson.encode(updated)) }
        rule.onNodeWithText(refreshed.sourceSnapshotHash!!).assertExists()
    }

    @Test fun identicalGeometryDoesNotKeepAReportLabeledWithThePreviousDraftState() {
        val applied = EventCourseDrafts.candidate(draft())
        val pending = EventCourseDrafts.start(applied)
        val appliedSummary = analyze(applied, DesktopCourseRouteSource.Applied)
        val draftSummary = analyze(EventCourseDrafts.candidate(pending), DesktopCourseRouteSource.Draft)
        assertEquals(appliedSummary.sourceSnapshotHash, draftSummary.sourceSnapshotHash)
        for (summary in listOf(appliedSummary, draftSummary)) {
            assertEquals("Section 1: ${summary.routeSource.routeLabel} analysis", summary.providedRouteSection!!.title)
            val report = DesktopCourseAnalysisExports.reportText(summary)
            assertTrue(report.contains(summary.routeSource.routeLabel))
            assertFalse(report.contains("Saved route"))
            assertTrue(summary.kmlFolders.any { it.routeName == summary.routeSource.routeLabel })
            assertTrue(summary.routeMaps.any { it.title == summary.routeSource.routeLabel })
        }
        var current by mutableStateOf(applied)
        var completed by mutableStateOf(appliedSummary)
        rule.setContent { Text(currentCourseAnalysisResult(current, completed)?.routeSource?.routeLabel ?: "Analyze again") }
        rule.onNodeWithText("Applied route").assertExists()
        rule.runOnIdle { current = pending }
        rule.onNodeWithText("Analyze again").assertExists()
        rule.runOnIdle { completed = draftSummary }
        rule.onNodeWithText("Draft route").assertExists()
        rule.runOnIdle { current = EventCourseDrafts.cancel(pending) }
        rule.onNodeWithText("Analyze again").assertExists()
    }

    private fun analyze(project: EventProjectFile, routeSource: DesktopCourseRouteSource = DesktopCourseRouteSource.Draft): DesktopCourseAnalysisSummary {
        val category = project.raceData.categories.single().category
        val info = category.courseInfo!!
        return DesktopCourseAnalyzer.analyze(project, category.id, info, info.idealOrder, elevationLookup = { 100.0 }, routeSource = routeSource)
    }

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

    @Test fun reviewScrollKeepsTitleAndActionsVisibleAtTheLastStation() {
        val draft = draft()
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(draft) }
        val ui = DesktopCourseDesignUi().apply {
            pendingApplication = DesktopCourseAnalyzer.analyze(candidate, "m21", info, info.idealOrder,
                prepareApplication = true, routeSource = DesktopCourseRouteSource.Draft).calculatedRouteApplication
        }
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(2f, 1.2f)
            ) {
                MaterialTheme { DesktopCourseDesignHost(draft, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } }
            }
        }
        rule.waitUntil(20_000) { ui.project != null }
        rule.onNodeWithTag("course-review-title").assertIsDisplayed()
        rule.onNodeWithTag("course-prepare-all").assertIsDisplayed()
        val initialTitle = rule.onNodeWithTag("course-review-title").fetchSemanticsNode().boundsInRoot
        val initialActions = rule.onNodeWithTag("course-review-actions").fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithTag("course-station-picker").onLast().performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("workspace-scrollbar").assertIsDisplayed()
        assertEquals(initialTitle, rule.onNodeWithTag("course-review-title").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialActions, rule.onNodeWithTag("course-review-actions").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("course-prepare-all").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        rule.runOnIdle { assertNull(ui.pendingApplication); assertEquals(draft, session.currentProject) }
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
