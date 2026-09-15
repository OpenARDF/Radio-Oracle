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

class DesktopCoursesPanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun existingUnassignedCourseIsVisibleAndCanBecomeCategoryWithoutReimport() {
        var project by mutableStateOf(DesktopCourseLibraryTest.unassigned())
        val source = project.raceData.courseMappings.single()
        rule.setContent { MaterialTheme { Surface { Column(Modifier.width(850.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
            DesktopCoursesPanel(project, true, { true }, { edit ->
                project = DesktopCourseLibrary.apply(project, edit, null) { "category" }; null
            })
        } } } }
        rule.onNodeWithText("Unassigned courses (1)").assertIsDisplayed()
        rule.onNodeWithText("Shared course").assertIsDisplayed()
        val image = rule.onRoot().captureToImage()
        val file = Path.of("build/reports/courses-list.png")
        Files.createDirectories(file.parent)
        Files.write(file, org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!.bytes)
        rule.onNodeWithTag("assign-course-${source.category.id}").performClick()
        rule.onNodeWithText("New category name (optional)").performTextReplacement("W21")
        rule.onNodeWithText("Assign course").performClick()
        rule.onNodeWithText("Unassigned courses (0)").assertIsDisplayed()
        rule.onNodeWithText("Category courses (1)").assertIsDisplayed()
        rule.runOnIdle { assertEquals("W21", project.raceData.categories.single().category.name) }
    }

    @Test fun deletionRequiresExplicitConfirmationAndCancelKeepsTheCourse() {
        val project = DesktopCourseLibraryTest.unassigned()
        val source = project.raceData.courseMappings.single()
        var edits = 0
        rule.setContent { MaterialTheme { DesktopCoursesPanel(project, true, { true }, { edits++; null }) } }
        rule.onNodeWithTag("delete-course-${source.category.id}").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertEquals(0, edits) }
        rule.onNodeWithTag("delete-course-${source.category.id}").performClick()
        rule.onNodeWithText("Delete course").performClick()
        rule.runOnIdle { assertEquals(1, edits) }
    }
}
