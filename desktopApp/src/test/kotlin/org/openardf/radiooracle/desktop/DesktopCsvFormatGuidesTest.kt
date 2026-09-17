package org.openardf.radiooracle.desktop

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventAwardDisplayMode
import org.openardf.radiooracle.shared.files.*

class DesktopCsvFormatGuidesTest {
    @Test fun everyCsvExportGuideMatchesTheHeaderActuallyWritten() {
        val project = DesktopClassicRouteAnalysisTest().fixture()
        val exports = listOf(
            DesktopNavAction.ExportCategoriesCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportCategoriesCsv(p, project) },
            DesktopNavAction.ExportControlsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportControlsCsv(p, project) },
            DesktopNavAction.ExportCompetitorsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportCompetitorsCsv(p, project) },
            DesktopNavAction.ExportStartsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportCompetitorStartsCsv(p, project) },
            DesktopNavAction.ExportStartsByCategoryCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportCompetitorStartsByCategoryCsv(p, project) },
            DesktopNavAction.ExportStartsByMinuteCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportCompetitorStartsByMinuteCsv(p, project) },
            DesktopNavAction.ExportReadoutsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportReadoutsCsv(p, project) },
            DesktopNavAction.ExportResultsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportResultsCsv(p, project) },
            DesktopNavAction.ExportSplitResultsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportSplitResultsCsv(p, project) },
            DesktopNavAction.ExportArdfEventResultsCsv to { p: java.nio.file.Path -> DesktopProjectFiles.exportArdfEventResultsCsv(p, project) }
        )
        val directory = Files.createTempDirectory("csv-guidance-contracts")
        exports.forEach { (action, export) ->
            val path = directory.resolve("$action.csv")
            export(path)
            val guide = DesktopCsvFormatGuides.forAction(action, project)!!
            assertEquals(action.name, guide.headerRow, Files.readString(path).lineSequence().first())
            assertFalse(guide.importable)
        }
        val courseRows = DesktopCourseReportCsv.rows(project)
        val courseGuide = CsvFormatGuides.courseReport(DesktopCourseReportCsv.columns(courseRows.maxOf { it.siControlCodes.size }))
        assertEquals(courseGuide.headerRow, DesktopCourseReportCsv.generate(project).lineSequence().first())
        val robis = directory.resolve("robis.csv")
        DesktopProjectFiles.exportRobisStartListCsv(robis, project)
        val guide = DesktopCsvFormatGuides.forAction(DesktopNavAction.ExportRobisStartListCsv, project)!!
        assertFalse(guide.includesHeader)
        assertEquals(guide.columns.size, CsvCodec.records(Files.readString(robis), ';').first().fields.size)
        assertFalse(Files.readString(robis).startsWith(guide.headerRow))
        val encrypted = directory.resolve("categories-protected.csv")
        DesktopProjectFiles.exportCategoriesCsv(encrypted, project, includeEncryptedIdealOrder = true)
        assertEquals(DesktopCsvFormatGuides.forAction(DesktopNavAction.ExportCategoriesCsv, project, true)!!.headerRow,
            Files.readString(encrypted).lineSequence().first())
    }

    @Test fun mixedFormatGuidesFollowTheirActualMenuActions() {
        for (id in listOf("setup.controls.import", "setup.controls.export", "setup.courses.course-tools.course-analysis")) {
            val state = DesktopNavState(submenuStack = if (id.startsWith("setup.controls")) listOf("setup.courses", id)
                else listOf("setup.courses", "setup.courses.course-tools", id), selectedItemId = id)
            val menuActions = DesktopNavigation.menuItemsForStack(state.workflow, state.submenuStack).mapNotNull { it.action }
            val expected = menuActions.filter { DesktopCsvFormatGuides.supports(it) || DesktopFileFormatGuides.kmlForAction(it) != null }
            val guides = DesktopFileFormatGuides.forNavigation(state, null, false, EventAwardDisplayMode.FIRST_TO_THIRD)
            assertEquals(id, expected.size, guides.size)
            expected.zip(guides).forEach { (action, guide) ->
                when (guide) {
                    is DesktopFileFormatGuide.Csv -> assertEquals(DesktopCsvFormatGuides.forAction(action, null), guide.guide)
                    is DesktopFileFormatGuide.Kml -> assertEquals(DesktopFileFormatGuides.kmlForAction(action), guide.guide)
                }
            }
            assertTrue(id, guides.any { it is DesktopFileFormatGuide.Kml })
        }
    }

    @Test fun allCsvActionsAreCoveredAndOrdinaryDataScreensStayUncluttered() {
        val actions = DesktopNavAction.entries.filter { it.name.endsWith("Csv") }
        actions.forEach { assertNotNull(it.name, DesktopCsvFormatGuides.forAction(it, null)) }
        val category = DesktopNavState(submenuStack = listOf("setup.categories"), selectedItemId = "setup.categories")
        assertTrue(DesktopCsvFormatGuides.forNavigation(category, null, false, EventAwardDisplayMode.FIRST_TO_THIRD).isEmpty())
        val selected = DesktopNavState(submenuStack = listOf("setup.categories"), selectedItemId = "setup.categories.import")
        val item = DesktopNavigation.itemById(selected.workflow, selected.selectedItemId)
        assertNotNull(item)
        assertEquals(listOf(CsvFormatGuides.categories(true)), DesktopCsvFormatGuides.forNavigation(selected, null, false,
            EventAwardDisplayMode.FIRST_TO_THIRD))
    }
}
