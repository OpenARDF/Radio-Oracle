package org.openardf.radiooracle.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
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

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun applicationButtonsExplainTheirActionsAndDisableTheNumberingOnlyNoOp() {
        val original = analyze(EventCourseDrafts.candidate(draft()), DesktopCourseRouteSource.Applied)
        assertNotNull(original.calculatedRouteApplication)
        val primary = CourseAnalysisApplyAction.CalculatedRoute
        val without = CourseAnalysisApplyAction.CalculatedWithoutRenumbering
        var summary by mutableStateOf(original.copy(calculatedGeometryMatchesSource = false))
        var primaryClicks = 0
        var withoutClicks = 0
        rule.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column {
                CourseAnalysisNavigationActions(summary, false, { primaryClicks++ }, { withoutClicks++ })
            }
        } }
        rule.onNodeWithText(primary.label).assertIsEnabled().performClick()
        rule.onNodeWithText(without.label).assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, primaryClicks); assertEquals(1, withoutClicks) }
        for (action in listOf(primary, without)) {
            rule.onNodeWithText(action.label).performMouseInput { enter(center) }
            rule.mainClock.advanceTimeBy(1000)
            rule.onNodeWithText(action.tooltip).assertExists()
            rule.onNodeWithText(action.label).performMouseInput { exit() }
        }
        rule.runOnIdle { summary = summary.copy(calculatedGeometryMatchesSource = true) }
        rule.onNodeWithText(primary.label).assertIsEnabled()
        rule.onNodeWithText(without.label).assertIsNotEnabled()
        rule.onNodeWithText(without.label).performMouseInput { enter(center) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText(without.disabledReason(summary)!!).assertExists()
        rule.onNodeWithText("Save Draft Numbering").assertDoesNotExist()
    }

    @Test fun analyzerDefaultsToAppliedCourseEvenWithPendingDraft() {
        val applied = DesktopAuthoritativeCourseImportTest.project()
        val pending = EventCourseDrafts.edit(applied) { candidate ->
            EventProjectEditor.replaceCategoryAssignedControls(candidate, "short", listOf("fox1", "fox3")) { "draft-$it" }
        }
        val ui = DesktopCourseDesignUi()
        assertEquals(applied, ui.analysisProject(pending))
        assertEquals(DesktopCourseRouteSource.Applied, ui.routeSource(pending))
        ui.analysisSource = DesktopCourseRouteSource.Draft
        assertEquals(listOf("fox1", "fox3"), ui.analysisProject(pending).raceData.categories.single { it.category.id == "short" }.publicControlIds)
        // Applying/canceling a draft must not leave the next analysis pointed at a nonexistent draft.
        assertEquals(applied, ui.analysisProject(applied))
        assertEquals(DesktopCourseRouteSource.Applied, ui.routeSource(applied))
    }

    @Test fun appliedAnalysisRemainsAvailableWhenAnUnrelatedDraftExists() {
        val applied = EventCourseDrafts.candidate(draft())
        val pending = EventCourseDrafts.edit(applied) { it.copy(raceData = it.raceData.copy(
            race = it.raceData.race.copy(courseAnalyzerSpeedCompensationFactor = 1.25))) }
        val summary = analyze(applied, DesktopCourseRouteSource.Applied)
        var source by mutableStateOf(DesktopCourseRouteSource.Applied)
        rule.setContent { Text(currentCourseAnalysisResult(pending, summary, source)?.routeSource?.label ?: "Analyze again") }
        rule.onNodeWithText("Applied").assertExists()
        rule.runOnIdle { source = DesktopCourseRouteSource.Draft }
        rule.onNodeWithText("Analyze again").assertExists()
    }

    @Test fun appliedSpeedChangeInvalidatesReportAndReanalyzesWithAnUnrelatedStaleDraft() {
        val applied = EventCourseDrafts.candidate(draft())
        val pending = EventCourseDrafts.edit(applied) {
            EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(it, 1.25)
        }
        val initial = EventProjectEditor.updateCourseAnalyzerSpeedCompensationFactor(pending, 0.6)
        val ui = DesktopCourseDesignUi()
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(initial) }
        var current by mutableStateOf(initial)
        val oldSummary = analyze(ui.analysisProject(initial), DesktopCourseRouteSource.Applied)
        var completed by mutableStateOf(oldSummary)
        rule.setContent {
            Text(currentCourseAnalysisResult(current, completed, ui.routeSource(current))?.sourceSnapshotHash ?: "Analyze again")
        }
        rule.onNodeWithText(oldSummary.sourceSnapshotHash!!).assertExists()
        rule.runOnIdle { current = ui.updateSpeedFactor(session, 0.7) }
        rule.onNodeWithText("Analyze again").assertExists()
        val refreshed = analyze(ui.analysisProject(current), DesktopCourseRouteSource.Applied)
        assertNotEquals(oldSummary.sourceSnapshotHash, refreshed.sourceSnapshotHash)
        assertEquals(initial.raceData.courseDraft, current.raceData.courseDraft)
        rule.runOnIdle { completed = refreshed }
        rule.onNodeWithText(refreshed.sourceSnapshotHash!!).assertExists()
        // A late result using the previous speed cannot replace the current report.
        rule.runOnIdle { completed = oldSummary.copy(analysisPerformedAtText = "Late result") }
        rule.onNodeWithText("Analyze again").assertExists()
    }

    @Test fun importingMandatoryCornersInvalidatesDisplayedAnalysisAndLateCompletions() {
        val applied = EventCourseDrafts.candidate(draft())
        val oldSummary = analyze(applied, DesktopCourseRouteSource.Applied)
        val path = Files.createTempFile("course-ui-corners-", ".kml")
        Files.writeString(path, courseWorkflowKml().replace(
            "<LineString><coordinates>-75.0,40.0,100 ",
            "<LineString><coordinates>-75.0,40.0,100 -74.998,40.001,100 "
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
        Files.writeString(kml, courseWorkflowKml().replace("<LineString><coordinates>-75.0,40.0,100 ",
            "<LineString><coordinates>-75.0,40.0,100 -74.999,40.001,100 "))
        val empty = EventProjectFactory.createEmptyProject("race", "UI fixture", "2026-09-06T09:00")
        val source = EventProjectEditor.addCategory(empty.copy(raceData = empty.raceData.copy(controls = EventControlCatalog.classicPreset("race"))), "m21", "M21")
        return EventCourseDrafts.edit(source) { DesktopCourseKmlImporter.importProtectedCourseInfo(kml, it, null, elevationProvider = { 100.0 }).first }
    }

    @Test fun oneApplyActionCommitsAndPersistsWithoutASecondConfirmation() {
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
        rule.waitUntil(30_000) { session.currentProject?.raceData?.courseDraft == null }
        rule.onNodeWithTag("course-apply-flow").assertDoesNotExist()
        val applied = session.currentProject!!
        assertEquals("passed", CourseWorkflowAudit.audit(applied.raceData).status)
        assertNotNull(applied.raceData.categories.single().category.courseInfo!!.appliedBindings)
        val path = Files.createTempDirectory("course-ui-save-").resolve("race.json")
        session.saveAs(path)
        assertEquals(applied.raceData, DesktopProjectFiles.read(path).raceData)
    }

    @Test fun reviewScrollKeepsTitleAndActionsVisibleAtTheLastStation() {
        val draft = unresolvedDraft()
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
        rule.onNodeWithTag("course-apply-all").assertIsDisplayed()
        val initialTitle = rule.onNodeWithTag("course-review-title").fetchSemanticsNode().boundsInRoot
        val initialActions = rule.onNodeWithTag("course-review-actions").fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithTag("course-station-picker").onLast().performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("workspace-scrollbar").assertIsDisplayed()
        assertEquals(initialTitle, rule.onNodeWithTag("course-review-title").fetchSemanticsNode().boundsInRoot)
        assertEquals(initialActions, rule.onNodeWithTag("course-review-actions").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithTag("course-apply-all").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        rule.runOnIdle { assertNull(ui.pendingApplication); assertEquals(draft, session.currentProject) }
    }

    @Test fun cancelingTheActualApplyDialogPreservesTheDraftAndAppliedRace() {
        val draft = unresolvedDraft()
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        val session = DesktopProjectSession(DesktopProjectFiles)
        session.newProject(draft)
        val ui = DesktopCourseDesignUi().apply {
            pendingApplication = DesktopCourseAnalyzer.analyze(candidate, "m21", info, info.idealOrder, prepareApplication = true).calculatedRouteApplication
        }
        rule.setContent { MaterialTheme { DesktopCourseDesignHost(draft, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } } }
        rule.waitUntil(20_000) { ui.project != null }
        rule.onNodeWithTag("course-apply-flow").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertNull(ui.pendingApplication); assertEquals(draft, session.currentProject) }
    }
    private fun unresolvedDraft(): EventProjectFile = EventCourseDrafts.edit(draft()) { candidate ->
        val info = candidate.raceData.categories.single().category.courseInfo!!
        candidate.withStoredCourseInfo("m21", info.copy(
            controlPoints = info.controlPoints.map { it.copy(controlId = "placement-${it.controlId}") },
            courseObjects = info.courseObjects.map { if (it.type.controlRole() != null) it.copy(id = "placement-${it.id}") else it }
        ), null)
    }

    @Test fun onlyMissingStationsAreShownAndTheDiagramAndKmlShareLocations() {
        val original = draft()
        val draft = EventCourseDrafts.edit(original) { candidate ->
            val info = candidate.raceData.categories.single().category.courseInfo!!
            val pointId = info.controlPoints.first().controlId
            candidate.withStoredCourseInfo("m21", info.copy(
                controlPoints = info.controlPoints.map { if (it.controlId == pointId) it.copy(controlId = "unknown-location") else it },
                courseObjects = info.courseObjects.map { if (it.id == pointId) it.copy(id = "unknown-location") else it }
            ), null)
        }
        val candidate = EventCourseDrafts.candidate(draft)
        val info = candidate.raceData.categories.single().category.courseInfo!!
        val app = analyze(candidate).calculatedRouteApplication!!
        val choices = courseStationChoices(candidate, mapOf("m21" to info))
        val allBindings = choices.associate { it.placementId to (it.controlId ?: EventCourseDrafts.candidate(original).raceData.categories.single().category.courseInfo!!.controlPoints.first().controlId) }
        DesktopCourseAnalysisApplier.prepareAll(draft, DesktopCourseRouteSelection(info, app, allBindings), mapOf("m21" to allBindings), null)
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(draft) }
        val ui = DesktopCourseDesignUi().apply { pendingApplication = app }
        rule.setContent { MaterialTheme { DesktopCourseDesignHost(draft, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } } }
        rule.waitUntil(20_000) { ui.project != null }
        rule.onAllNodesWithTag("course-station-picker").assertCountEquals(1)
        rule.onNodeWithText("Export locations to KML…").assertExists()
        val screenshot = org.jetbrains.skia.Image.makeFromBitmap(rule.onNodeWithTag("course-apply-flow").captureToImage().asSkiaBitmap())
        val screenshotPath = java.nio.file.Path.of("build/reports/course-station-resolution.png")
        Files.createDirectories(screenshotPath.parent)
        Files.write(screenshotPath, screenshot.encodeToData()!!.bytes)
        rule.onNodeWithTag("course-apply-all").assertIsNotEnabled()
        assertEquals(draft, session.currentProject)
        val folder = courseStationPreviewFolders(candidate, mapOf("m21" to info), choices.associate { it.key to it.controlId.orEmpty() }, app).single()
        val map = courseStationPreviewMap(folder)
        assertEquals(folder.courseObjects.filterNot { it.type == DesktopCourseKmlExportPointType.WAYPOINT }.map { it.label }, map.points.map { it.label })
        val path = Files.createTempFile("station-preview-", ".kml")
        DesktopCourseAnalysisExports.exportKmlFolders(path, listOf(folder))
        val exported = DesktopCourseFileReader.read(path)
        assertEquals(folder.courseObjects.map { it.label to it.point }, exported.controls.map { it.name to it.point })
        assertEquals(folder.routePoints.size, exported.routes.single().points.size)
        folder.routePoints.zip(exported.routes.single().points).forEach { (expected, actual) ->
            assertEquals(expected.latitude, actual.latitude, 0.00000001)
            assertEquals(expected.longitude, actual.longitude, 0.00000001)
        }
        val station = candidate.raceData.controls.single { it.id == allBindings.getValue("unknown-location") }
        rule.onNodeWithText("Choose SI station").performScrollTo().performClick()
        rule.onNodeWithText("${station.publicLabel ?: station.label} — SI ${station.siCode}").performClick()
        rule.onNodeWithTag("course-apply-all").assertIsEnabled().performClick()
        rule.waitUntil(30_000) { ui.pendingApplication == null }
        assertNull(session.currentProject!!.raceData.courseDraft)
        assertEquals("passed", CourseWorkflowAudit.audit(session.currentProject!!.raceData).status)
    }

    @Test fun staleAnalysisStopsAutomaticApplicationAndKeepsTheDraft() {
        val original = draft()
        val app = analyze(EventCourseDrafts.candidate(original)).calculatedRouteApplication!!
        val newer = EventCourseDrafts.edit(original) { EventProjectEditor.updateCategoryPhysicalStats(it, "m21", "999", "99") }
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(newer) }
        val ui = DesktopCourseDesignUi().apply { pendingApplication = app }
        rule.setContent { MaterialTheme { DesktopCourseDesignHost(newer, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } } }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Course changes could not be applied").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Course data changed after this calculation. Analyze the current draft before applying it.").assertExists()
        assertEquals(newer, session.currentProject)
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertNull(ui.pendingApplication) }
    }

    @Test fun recordedReadoutsExplainTheBlockWithoutRequestingStationReview() {
        val pending = draft()
        val readout = EventReadoutData(EventResult("readout", "race", null, 123456, 0, null, 0, 1200,
            "2026-09-10T10:00", true, org.openardf.radiooracle.shared.domain.ResultStatus.OK, 1, 1200, false, false), emptyList())
        val recorded = pending.copy(raceData = pending.raceData.copy(unmatchedReadoutData = listOf(readout)))
        val session = DesktopProjectSession(DesktopProjectFiles).apply { newProject(recorded) }
        val ui = DesktopCourseDesignUi().apply { pendingApplication = analyze(EventCourseDrafts.candidate(recorded)).calculatedRouteApplication }
        rule.setContent { MaterialTheme { DesktopCourseDesignHost(recorded, null, session, ui, onChanged = { _, _ -> }) { Text("Fixture") } } }
        rule.waitUntil(20_000) { ui.project != null }
        rule.onNodeWithText("Course changes blocked by readouts").assertIsDisplayed()
        rule.onNodeWithText("Create revised race copy…").assertExists()
        rule.onAllNodesWithTag("course-station-picker").assertCountEquals(0)
        rule.onNodeWithTag("course-apply-all").assertDoesNotExist()
        assertEquals(recorded, session.currentProject)
    }

}
