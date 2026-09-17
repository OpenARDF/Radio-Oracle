package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*

/** Opt-in reproduction against locally supplied support files; private fixtures never enter the repository. */
class DesktopIofReimportAcceptanceTest {
    @Test fun suppliedRaceAndXmlImportAnalyzeAndExportWithoutChangingSourceFiles() {
        val racePath = System.getProperty("radiooracle.iofRegressionRace").orEmpty()
        val xmlPath = System.getProperty("radiooracle.iofRegressionXml").orEmpty()
        assumeTrue("Supply -PiofRegressionRace and -PiofRegressionXml to run the private-file reproduction.", racePath.isNotBlank() && xmlPath.isNotBlank())
        val raceBytes = Files.readAllBytes(Path.of(racePath))
        val xmlBytes = Files.readAllBytes(Path.of(xmlPath))
        val original = EventProjectFileJson.decode(String(raceBytes, Charsets.UTF_8))
        val snapshot = EventProjectFileJson.encode(original)
        val preview = IofXmlImports.validatedCourseData(String(xmlBytes, Charsets.UTF_8), IofXmlSchemaResource.loadBundledSchema(), original.raceData.race).parsedData
        val transaction = DesktopCourseImportTransaction.prepare(original)
        val imported = EventProjectEditor.importIofCourseData(transaction.baseProject, preview).projectFile
        val names = preview.categories.map { it.category.name }.toSet()
        val ids = (imported.raceData.categories + imported.raceData.courseMappings).filter { it.category.name in names }.map { it.category.id }.toSet()
        val candidate = DesktopIofCourseAnalysis.prepare(imported, ids, null, elevationLookup = { 100.0 })
        assertEquals(snapshot, EventProjectFileJson.encode(original)) // Reject retains the original byte-for-byte model.
        val accepted = EventProjectFileJson.decode(EventProjectFileJson.encode(transaction.applyTo(original) { candidate.project }))
        EventControlCatalog.requireCanonical(accepted)
        val acceptedSnapshot = EventProjectFileJson.encode(accepted)
        val output = Path.of("build/reports/iof-reimport-acceptance")
        Files.createDirectories(output)
        (accepted.raceData.categories + accepted.raceData.courseMappings).filter { it.category.id in ids }.forEachIndexed { index, data ->
            val expected = preview.categories.single { it.category.name == data.category.name }.category.courseInfo!!
            val info = data.category.courseInfo!!
            val importedData = preview.categories.single { it.category.name == data.category.name }
            val expectedByStation = importedData.controlPoints.associate { cp ->
                val point = expected.courseObjects.single { it.id == cp.controlId }
                cp.siCode to (point.latitude to point.longitude)
            }
            val actualByStation = info.controlPoints.associate { point ->
                accepted.raceData.controls.single { it.id == point.controlId }.siCode to (point.latitude to point.longitude)
            }
            assertEquals(expectedByStation, actualByStation)
            assertEquals(expected.courseObjects.filter { it.type.controlRole() == null }.associate { it.id to (it.latitude to it.longitude) },
                info.courseObjects.filter { it.type.controlRole() == null }.associate { it.id to (it.latitude to it.longitude) })
            val summary = DesktopCourseAnalyzer.analyze(accepted, data.category.id, info, data.category.idealOrder,
                elevationLookup = { 100.0 }, allowFoxRenumbering = false, prepareApplication = true, magneticDeclinationProvider = { null })
            assertTrue(summary.calculatedRouteApplication!!.foxAssignments.all { it.originalLabel == it.calculatedLabel })
            DesktopCourseAnalysisExports.exportPdfAndKml(output.resolve("course-${index + 1}.pdf"), summary)
        }
        assertEquals(acceptedSnapshot, EventProjectFileJson.encode(accepted))
        assertArrayEquals(raceBytes, Files.readAllBytes(Path.of(racePath)))
        assertArrayEquals(xmlBytes, Files.readAllBytes(Path.of(xmlPath)))
        Files.writeString(output.resolve("verification.txt"), "Imported ${ids.size} categories/courses in ${candidate.reports.size} unique reports. Locations preserved; Analyze and PDF/KML export left the race and supplied files unchanged.\n")
    }
}
