package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.openardf.radiooracle.shared.event.*

class DesktopCoursesPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun coverageRefreshesAfterCacheImportControlEditAndRelock() = with(DesktopVenueElevationCacheTest()) {
        withTemporaryUserHome {
            val base = EventProjectEditor.addCategory(DesktopCourseImportAvailabilityTest.project(), "c", "Blue")
            val control = EventControl("f", "race", "Fox 2", 32,
                org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL, latitude = 45.0, longitude = -122.0)
            val category = base.raceData.categories.single()
            val protected = category.copy(category = category.category.copy(encryptedCourseInfo = "protected"))
            var project by mutableStateOf(base.copy(raceData = base.raceData.copy(controls = listOf(control), categories = listOf(protected))))
            var unlocked by mutableStateOf(true)
            var refresh by mutableIntStateOf(0)
            val affected = "Fox 2 (SI 32)"
            rule.setContent { MaterialTheme { DesktopCoursesPanel(project, unlocked, { true }, { null }, elevationRefreshToken = refresh) } }
            fun waitForWarning() = rule.waitUntil(10_000) { rule.onAllNodesWithText(affected).fetchSemanticsNodes().isNotEmpty() }
            waitForWarning()
            val dir = Files.createDirectories(DesktopVenueElevationCache.cacheDirectory())
            writeCache(dir.resolve("venue.roelev.json"), "USGS 3DEP", 10.0, 100.0)
            rule.runOnIdle { refresh++ }
            rule.waitUntil(10_000) { rule.onAllNodesWithText(affected).fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText("Checking local elevation coverage…").fetchSemanticsNodes().isEmpty() }
            rule.runOnIdle { project = project.copy(raceData = project.raceData.copy(controls = listOf(control.copy(latitude = 46.0)))) }
            waitForWarning()
            rule.runOnIdle { unlocked = false }
            rule.onNodeWithText("Unlock course data to check elevation coverage.").assertExists()
            rule.onNodeWithText(affected).assertDoesNotExist()
            rule.runOnIdle { unlocked = true }
            waitForWarning()
        }
    }

    @Test fun elevationNoticeDistinguishesControlsAlternateLegsAndAreaOnlyGaps() {
        var finding by mutableStateOf(CourseElevationFinding(true, setOf("Fox 2 (SI 32)"),
            setOf("Blue · Start → Fox 2"), setOf(PossibleElevationLeg("blue", "Blue · Fox 2 → Fox 4", listOf("Fox 2"), listOf("Fox 4")))))
        var opened = false
        rule.setContent { MaterialTheme { CourseElevationFindingPanel(finding) { opened = true } } }
        rule.onNodeWithText("Fox 2 (SI 32)").assertIsDisplayed()
        rule.onNodeWithText("Blue · Start → Fox 2").assertIsDisplayed()
        rule.onNodeWithText("Other possible legs (1)").performClick()
        rule.onNodeWithText("Blue · Fox 2 → Fox 4").assertIsDisplayed()
        rule.onNodeWithText("Open Elevation Data").performClick()
        rule.runOnIdle {
            assertTrue(opened)
            finding = CourseElevationFinding(areaHasGap = true)
        }
        rule.onNodeWithText("Elevation coverage advisory").assertIsDisplayed()
        rule.onNodeWithText("Other possible legs (1)").assertDoesNotExist()
        rule.runOnIdle { finding = CourseElevationFinding() }
        rule.onNodeWithText("Open Elevation Data").assertDoesNotExist()
    }

    @Test fun categoriesCanAssignTheSameExistingCourseToM70AndW65WithoutAnalyzer() {
        val initial = DesktopCourseLibraryTest.unassigned()
        var base = DesktopCourseLibrary.apply(initial,
            DesktopCourseLibraryEdit.Assign(initial.raceData.courseMappings.single().category.id, emptySet(), "W21"), null) { "w21" }
        base = EventProjectEditor.addCategory(base, "m70", "M70")
        base = EventProjectEditor.addCategory(base, "w65", "W65")
        var project by mutableStateOf(base)
        rule.setContent { MaterialTheme { Surface { Column(Modifier.width(850.dp).padding(16.dp)) {
            DesktopCategoryCourseAssignment(project, true, { true }, { edit ->
                project = DesktopCourseLibrary.apply(project, edit, null); null
            })
        } } } }
        rule.onNodeWithTag("categories-assign-existing-course").performClick()
        rule.onNodeWithTag("existing-course-w21").performClick()
        rule.onNodeWithTag("course-target-m70").performClick()
        rule.onNodeWithTag("course-target-w65").performClick()
        val image = rule.onNodeWithTag("desktop-alert").captureToImage()
        val file = Path.of("build/reports/categories-assign-existing-course.png")
        Files.createDirectories(file.parent)
        Files.write(file, org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.bytes)
        rule.onNodeWithText("Assign course").performClick()
        rule.runOnIdle {
            assertEquals(setOf("W21", "M70", "W65"), project.raceData.categories.map { it.category.name }.toSet())
            assertTrue(project.raceData.categories.all { it.category.courseInfo != null })
            assertEquals(base.raceData.controls, project.raceData.controls)
            assertEquals(base.raceData.categories.first(), project.raceData.categories.first())
        }
    }

    @Test fun existingUnassignedCourseIsVisibleAndCanBecomeCategoryWithoutReimport() {
        var project by mutableStateOf(DesktopCourseLibraryTest.unassigned())
        val source = project.raceData.courseMappings.single()
        rule.setContent { MaterialTheme { Surface { Column(Modifier.width(850.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            DesktopCoursesPanel(project, true, { true }, { edit ->
                project = DesktopCourseLibrary.apply(project, edit, null) { "category" }; null
            })
        } } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Checking local elevation coverage…").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Unassigned courses (1)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Shared course").performScrollTo().assertIsDisplayed()
        val image = rule.onRoot().captureToImage()
        val file = Path.of("build/reports/courses-list.png")
        Files.createDirectories(file.parent)
        Files.write(file, org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.bytes)
        rule.onNodeWithTag("assign-course-${source.category.id}").performScrollTo().performClick()
        rule.onNodeWithText("New category name (optional)").performTextReplacement("W21")
        rule.onNodeWithText("Assign course").performClick()
        rule.onNodeWithText("Unassigned courses (0)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Category courses (1)").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertEquals("W21", project.raceData.categories.single().category.name) }
    }

    @Test fun deletionRequiresExplicitConfirmationAndCancelKeepsTheCourse() {
        val project = DesktopCourseLibraryTest.unassigned()
        val source = project.raceData.courseMappings.single()
        var edits = 0
        // Production hosts the panel in a scrolling workspace, including when the elevation notice occupies several rows.
        rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            DesktopCoursesPanel(project, true, { true }, { edits++; null })
        } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Checking local elevation coverage…").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("delete-course-${source.category.id}").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertEquals(0, edits) }
        rule.onNodeWithTag("delete-course-${source.category.id}").performScrollTo().performClick()
        rule.onNodeWithText("Delete course").performClick()
        rule.runOnIdle { assertEquals(1, edits) }
    }
}
