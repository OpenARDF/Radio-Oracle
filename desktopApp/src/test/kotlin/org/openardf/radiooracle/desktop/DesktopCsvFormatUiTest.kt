package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
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
import org.openardf.radiooracle.shared.files.*

class DesktopCsvFormatUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun csvMenuActionsShowGuidanceBeforeDispatchingTheExistingFileChooser() {
        val actions = mutableListOf<DesktopNavAction>()
        val project = DesktopClassicRouteAnalysisTest().fixture()
        rule.setContent { MaterialTheme { Surface(Modifier.width(1100.dp).height(850.dp)) {
            DesktopAppShellPreview(projectFile = project, onNavAction = { actions += it })
        } } }
        rule.onNodeWithText("Press any key or click to continue.").performClick()
        rule.onNodeWithText("Categories >").performClick()
        rule.onNodeWithText("Import Categories CSV...").performClick()
        rule.onNodeWithText(CsvFormatGuides.categories(true).headerRow).assertExists()
        assertTrue(actions.isEmpty())
        rule.onNodeWithText("Choose CSV file…").performScrollTo().performClick()
        assertEquals(listOf(DesktopNavAction.ImportCategoriesCsv), actions)
        rule.onNodeWithText("< Back").performClick()
        rule.onNodeWithText("Export Categories CSV...").performClick()
        rule.onNodeWithText(CsvFormatGuides.categories().headerRow).assertExists()
        assertEquals(1, actions.size)
        rule.onNodeWithText("Export CSV…").performScrollTo().performClick()
        assertEquals(listOf(DesktopNavAction.ImportCategoriesCsv, DesktopNavAction.ExportCategoriesCsv), actions)
        screenshot("categories-export-workspace")
    }

    @Test fun allFourCourseImportButtonsDisableForARaceWithReadouts() {
        rule.setContent { MaterialTheme { Surface(Modifier.width(1100.dp).height(850.dp)) {
            DesktopAppShellPreview(DesktopClassicRouteAnalysisTest().fixture()) { error("Blocked imports must not dispatch") }
        } } }
        rule.onNodeWithText("Press any key or click to continue.").performClick()
        rule.onNodeWithText("Courses >").performClick()
        rule.onNodeWithText("Import >").performClick()
        listOf("Import Controls CSV...", "Import KML/KMZ...", "Import GPX...", "Import IOF CourseData XML...")
            .forEach { rule.onNodeWithText(it).assertIsNotEnabled() }
        rule.onNodeWithTag("csv-format-controls-import").assertExists()
        rule.onNodeWithTag("kml-format-controls-import").assertExists()
        screenshot("course-imports-blocked-by-readouts")
    }

    @Test fun courseImportAndExportShowSeparateFormatBoxesInMenuOrder() {
        val actions = mutableListOf<DesktopNavAction>()
        rule.setContent { MaterialTheme { Surface(Modifier.width(1100.dp).height(850.dp)) {
            DesktopAppShellPreview(DesktopClassicRouteAnalysisTest().fixture()) { actions += it }
        } } }
        rule.onNodeWithText("Press any key or click to continue.").performClick()
        rule.onNodeWithText("Courses >").performClick()
        rule.onNodeWithText("Import >").performClick()
        assertTrue(actions.isEmpty())
        assertCsvBeforeKml("import", "controls-import")
        rule.onNodeWithText("KML/KMZ format").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Show format details").performScrollTo().performClick()
        rule.onNodeWithText("Route name and geometry", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Use a regulation category", substring = true).assertExists()
        screenshot("courses-import-kml-details")
        rule.onNodeWithText("< Back").performClick()
        rule.onNodeWithText("Export >").performClick()
        assertCsvBeforeKml("export", "course-export")
        assertTrue(actions.isEmpty())
        rule.onNodeWithText("KML/KMZ format").performScrollTo().assertIsDisplayed()
        screenshot("courses-export-formats")
    }

    @Test fun kmlDetailsRemainReachableInANarrowWindow() {
        rule.setContent { MaterialTheme { Surface(Modifier.width(390.dp).height(600.dp)) {
            DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                DesktopFileFormatPanels(listOf(DesktopFileFormatGuide.Csv(CsvFormatGuides.controls(true)),
                    DesktopFileFormatGuide.Kml(KmlFormatGuides.controlsImport())))
            }
        } } }
        assertCsvBeforeKml("import", "controls-import")
        rule.onNodeWithText("Show format details").performScrollTo().performClick()
        rule.onNodeWithText("Import and exchange", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("kml-details-narrow")
    }

    private fun assertCsvBeforeKml(direction: String, kmlId: String) {
        val csv = rule.onNodeWithTag("csv-format-controls-$direction").fetchSemanticsNode().boundsInRoot
        val kml = rule.onNodeWithTag("kml-format-$kmlId").fetchSemanticsNode().boundsInRoot
        assertTrue("Separate format boxes must follow the menu order", csv.top < kml.top)
    }

    @Test fun importsShowHeaderExampleAndTemplateWithoutStartingAnImport() {
        val guide = CsvFormatGuides.controls(true)
        var saved: CsvFormatGuide? = null
        rule.setContent { MaterialTheme { Surface(Modifier.width(600.dp)) {
            DesktopWorkspaceScroll(Modifier.fillMaxSize()) {
                DesktopCsvFormatPanel(guide) { saved = it; "Saved header-only template" }
            }
        } } }
        rule.onNodeWithText(guide.headerRow).assertExists()
        rule.onNodeWithText(guide.exampleRow).assertExists()
        assertNull(saved)
        rule.onNodeWithText("Save template…").performClick()
        assertEquals(guide, saved)
        rule.onNodeWithText("Saved header-only template").assertExists()
        rule.onNodeWithText("Show field details").performClick()
        rule.onNodeWithText("1. si_code (required)", substring = true).assertExists()
        screenshot("controls-import")
    }

    @Test fun longHeadersFitNarrowWindowsAndStartsExposeAcceptedAlternatives() {
        val guide = CsvFormatGuides.starts(true)
        rule.setContent { MaterialTheme { Surface(Modifier.width(390.dp)) {
            DesktopWorkspaceScroll(Modifier.fillMaxSize()) { DesktopCsvFormatPanel(guide) { null } }
        } } }
        rule.onNodeWithText(guide.headerRow).assertExists()
        rule.onNodeWithText("Show field details").performClick()
        rule.onNodeWithText("Other accepted layouts").assertExists()
        rule.onNodeWithText(CsvFormatGuides.starts().headerRow).assertExists()
        rule.onNodeWithText(CsvFormatGuides.starts().headerRow).performScrollTo().assertIsDisplayed()
        screenshot("starts-alternatives-narrow")
    }

    @Test fun exportsShowTheDynamicHeaderWithoutOfferingAnImportTemplate() {
        val guide = CsvFormatGuides.readouts(3)
        rule.setContent { MaterialTheme { Surface(Modifier.width(600.dp)) { DesktopCsvFormatPanel(guide) } } }
        rule.onNodeWithText(guide.headerRow).assertExists()
        rule.onNodeWithText("Copy header").assertExists()
        rule.onNodeWithText("Save template…").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val output = Path.of("build/reports/csv-guidance/$name.png")
        Files.createDirectories(output.parent)
        val image = org.jetbrains.skia.Image.makeFromBitmap(rule.onRoot().captureToImage().asSkiaBitmap())
        Files.write(output, image.encodeToData()!!.bytes)
    }
}
