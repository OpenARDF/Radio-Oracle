package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DesktopCourseBriefReportUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun routeMapShowsControlsButHidesMandatoryAndPhantomLabels() {
        val points = listOf(
            DesktopCourseRouteMapPoint("Start", 0.1, 0.9, DesktopCourseRouteMapPointType.Start),
            DesktopCourseRouteMapPoint("Mandatory bend", 0.4, 0.1, DesktopCourseRouteMapPointType.Waypoint),
            DesktopCourseRouteMapPoint("900", 0.7, 0.2, DesktopCourseRouteMapPointType.Waypoint),
            DesktopCourseRouteMapPoint("Fox 1", 0.8, 0.8, DesktopCourseRouteMapPointType.Control),
            DesktopCourseRouteMapPoint("Finish", 0.9, 0.9, DesktopCourseRouteMapPointType.Finish)
        )
        val map = DesktopCourseRouteMap("Course", points, routeLabels = points.map { it.label }, routePointIndexes = points.indices.toList())
        rule.setContent { MaterialTheme { Surface { CourseAnalysisRouteMap(map) } } }
        listOf("Start", "Fox 1", "Finish").forEach { rule.onNodeWithText(it).assertExists() }
        listOf("Mandatory bend", "900").forEach { rule.onNodeWithText(it).assertDoesNotExist() }
        assertEquals(points.map { DesktopCourseRouteMapLinePoint(it.xFraction, it.yFraction) },
            map.routeLinesForDrawing().single().points)
        saveScreenshot("hidden-mandatory-and-phantom-points")
    }

    @Test fun coursesScreenShowsReportsWithPairedDiagramsAndControlMarkers() {
        val project = courseReportFixture()
        val before = org.openardf.radiooracle.shared.event.EventProjectFileJson.encode(project)
        rule.setContent {
            MaterialTheme { Surface(Modifier.width(820.dp)) {
                DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                    DesktopCoursesPanel(project, true, { true }, { error("Reports must not edit the race") })
                }
            } }
        }
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("course-report-profile-inactive").fetchSemanticsNodes().isNotEmpty() }
        listOf("inactive", "m21", "w40").forEach { id ->
            rule.onNodeWithTag("course-report-$id").assertExists()
            rule.onNodeWithTag("course-report-graphics-row-$id").assertExists()
            val map = rule.onNodeWithTag("course-report-map-$id").fetchSemanticsNode().boundsInRoot
            val profile = rule.onNodeWithTag("course-report-profile-$id").fetchSemanticsNode().boundsInRoot
            assertEquals(map.top, profile.top, 1f)
            assertTrue(profile.left >= map.right)
            assertTrue(profile.width <= 360f)
        }
        rule.onAllNodesWithText("Fox1 0.", substring = true).assertCountEquals(3)
        rule.onNodeWithTag("assign-course-inactive").assertExists()
        rule.onNodeWithTag("delete-course-inactive").assertExists()
        rule.onNodeWithTag("course-report-profile-inactive").performScrollTo().assertIsDisplayed()
        saveScreenshot("paired-diagrams")
        assertEquals(before, org.openardf.radiooracle.shared.event.EventProjectFileJson.encode(project))
    }

    @Test fun narrowReportsStackDiagramsAndKeepControlMarkersVisible() {
        val report = DesktopCourseBriefReports.build(courseReportFixture(), emptyMap()).first()
        rule.setContent { MaterialTheme { Surface(Modifier.width(390.dp)) {
            DesktopWorkspaceScroll(Modifier.fillMaxSize()) { CourseBriefReportSection(report) }
        } } }
        rule.onNodeWithTag("course-report-graphics-column-m21").assertExists()
        val map = rule.onNodeWithTag("course-report-map-m21").fetchSemanticsNode().boundsInRoot
        val profile = rule.onNodeWithTag("course-report-profile-m21").fetchSemanticsNode().boundsInRoot
        assertTrue(profile.top >= map.bottom)
        rule.onNodeWithText("Fox1 0.", substring = true).performScrollTo().assertIsDisplayed()
        saveScreenshot("stacked-diagrams")
    }

    @Test fun lockingClearsBothGraphicsAndMissingElevationKeepsTheMap() {
        val source = DesktopCourseBriefReports.build(courseReportFixture(), emptyMap()).first()
        val report = mutableStateOf(source)
        rule.setContent { MaterialTheme { Surface(Modifier.width(820.dp)) {
            DesktopWorkspaceScroll(Modifier.fillMaxSize()) { CourseBriefReportSection(report.value) }
        } } }
        rule.onNodeWithTag("course-report-profile-m21").assertExists()
        rule.runOnIdle { report.value = source.copy(elevationProfile = null) }
        rule.onNodeWithTag("course-report-map-m21").assertExists()
        rule.onNodeWithTag("course-report-profile-m21").assertDoesNotExist()
        rule.onNodeWithText("Elevation profile unavailable:", substring = true).assertExists()
        rule.runOnIdle { report.value = source.copy(isLocked = true, routeMap = null, elevationProfile = null) }
        rule.onNodeWithTag("course-report-map-m21").assertDoesNotExist()
        rule.onNodeWithTag("course-report-profile-m21").assertDoesNotExist()
        rule.onNodeWithText("Course graphic unavailable.").assertExists()
    }

    private fun saveScreenshot(name: String) {
        val image = org.jetbrains.skia.Image.makeFromBitmap(rule.onRoot().captureToImage().asSkiaBitmap())
        val output = Path.of("build/reports/course-report/$name.png")
        Files.createDirectories(output.parent)
        Files.write(output, image.encodeToData()!!.bytes)
    }

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

    @Test fun coursesPdfExportIsEnabledAboveTheFirstCalculatedReportAndLegTableIsVisible() {
        val project = courseReportFixtureWithMandatoryBend()
        rule.setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                    DesktopCoursesPanel(project, true, { true }, { null })
                }
            } }
        }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("course-report-leg-table-m21").fetchSemanticsNodes().isNotEmpty()
        }
        val export = rule.onNodeWithTag("export-course-report-pdf").assertIsEnabled()
        val firstReport = rule.onNodeWithTag("course-report-m21")
        assertTrue(export.fetchSemanticsNode().boundsInRoot.bottom <= firstReport.fetchSemanticsNode().boundsInRoot.top)
        listOf("Ideal route legs", "Ideal-order leg", "S → Fox1").forEach { text ->
            rule.onAllNodesWithText(text, substring = true).onFirst().assertExists()
        }
        rule.onNodeWithText("Mandatory bend", substring = true).assertDoesNotExist()
    }

    @Test fun coursesPdfExportIsDisabledWhenNoReportsAreAvailable() {
        val project = org.openardf.radiooracle.shared.event.EventProjectFactory.createEmptyProject(
            "empty-race", "Empty race", "2026-09-26T09:00"
        )
        rule.setContent {
            MaterialTheme { Surface {
                DesktopCoursesPanel(project, true, { true }, { null })
            } }
        }
        rule.onNodeWithTag("export-course-report-pdf").assertIsNotEnabled()
    }
}
