package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopCondesCourseImportTest {
    private val codes = listOf(71, 73, 75, 74, 79)

    @Test fun importsCondesGpxControlsAndCourse() = verifyImport(fixture("gpx"))

    @Test fun importsCondesKmzControlsAndCourse() {
        val kmz = Files.createTempFile("condes-course-", ".kmz")
        try {
            ZipOutputStream(Files.newOutputStream(kmz)).use { zip ->
                zip.putNextEntry(ZipEntry("files/map.txt"))
                zip.write("Ignored map asset".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("doc.kml"))
                zip.write(Files.readAllBytes(fixture("kml")))
                zip.closeEntry()
            }
            verifyImport(kmz)
        } finally {
            Files.deleteIfExists(kmz)
        }
    }

    @Test fun importsOriginalReportAttachmentsWhenProvided() {
        val directory = System.getenv("RADIO_ORACLE_CONDES_REPORT_DIRECTORY")
        assumeTrue("Optional private report attachments", directory != null)
        verifyImport(Path.of(directory!!, "Umstead South 10 4 26 10 4 2026.gpx"))
        verifyImport(Path.of(directory, "Umstead South 10 4 26 2m4k.kmz"))
    }

    @Test fun doesNotInferStationCodesFromUntypedWaypointNames() {
        withModifiedFixture("gpx", { it.replace("<type>Control</type>", "<type>Waypoint</type>") }) { parsed ->
            assertTrue(parsed.controls.all { it.siCodeHint == null })
        }
    }

    @Test fun rejectsAmbiguousCourseOrderButRetainsExplicitControlCodes() {
        for (replacement in listOf("1 (73)", "3 (73)", "Control 73")) {
            withModifiedFixture("kml", { it.replace("2 (73)", replacement) }) { parsed ->
                assertTrue(parsed.routes.isEmpty())
                assertEquals(codes, parsed.controls.mapNotNull { it.siCodeHint })
            }
        }
    }

    @Test fun recognizesCourseSettingNamespaceRegardlessOfPrefix() {
        withModifiedFixture("kml", { it.replace("xmlns:cs=", "xmlns:course=").replace("cs:", "course:") }) { parsed ->
            assertEquals(codes, parsed.controls.mapNotNull { it.siCodeHint })
            assertEquals(7, parsed.routes.single().points.size)
        }
    }

    @Test fun ignoresUnrelatedExtendedDataNamespaces() {
        withModifiedFixture("kml", { it.replace("http://www.orienteering.org/schemas/course-setting", "urn:unrelated") }) { parsed ->
            assertTrue(parsed.controls.all { it.siCodeHint == null })
            assertTrue(parsed.routes.isEmpty())
        }
    }

    private fun withModifiedFixture(extension: String, modify: (String) -> String, check: (DesktopCourseKmlData) -> Unit) {
        val path = Files.createTempFile("condes-variant-", ".$extension")
        try {
            Files.writeString(path, modify(Files.readString(fixture(extension))))
            check(DesktopCourseFileReader.read(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun verifyImport(path: Path) {
        val parsed = DesktopCourseFileReader.read(path)
        assertEquals(codes, parsed.controls.mapNotNull { it.siCodeHint })
        assertEquals("Start", parsed.controls.first().name)
        assertEquals("Finish", parsed.controls.last().name)
        assertEquals(7, parsed.routes.single().points.size)
        val empty = EventProjectFactory.createEmptyProject("race", "Condes test", "2026-09-10T09:00")
        for (requireRoutes in listOf(false, true)) {
            val (created, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
                path, empty, null, elevationProvider = { 100.0 }, createMissingControls = true,
                createMissingCategories = true, requireRoutes = requireRoutes
            )
            assertEquals(5, summary.matchedFoxCount)
            assertEquals(codes.toSet(), created.raceData.controls.map { it.siCode }.toSet())
            assertEquals(1, summary.importedCategoryCount)
            assertEquals(5, created.raceData.courseMappings.single().category.courseInfo!!.controlPoints.size)
            // Course order is not fox identity: Fox 5 is first on this course.
            val configured = empty.copy(raceData = empty.raceData.copy(
                categories = listOf(created.raceData.courseMappings.single().copy(
                    category = created.raceData.courseMappings.single().category.copy(courseInfo = null, idealOrder = null)
                )),
                controls = codes.mapIndexed { i, code ->
                EventControl("fox-$code", "race", "Fox ${5 - i}", code, ControlPointType.CONTROL,
                    publicLabel = "Fox ${5 - i}")
            }))
            val (matched, matchedSummary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
                path, configured, null, elevationProvider = { 100.0 }, createMissingControls = true,
                createMissingCategories = true, requireRoutes = requireRoutes
            )
            assertEquals(5, matchedSummary.matchedFoxCount)
            assertEquals(5, matchedSummary.assignedCategoryControlCount)
            assertTrue(matchedSummary.createdControlNames.isEmpty())
            assertEquals(configured.raceData.controls.map { Triple(it.id, it.label, it.siCode) },
                matched.raceData.controls.map { Triple(it.id, it.label, it.siCode) })
            val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(matched))
            val course = reopened.raceData.categories.single().category.courseInfo!!
            assertEquals(codes.map { "fox-$it" }, course.controlPoints.map { it.controlId })
            codes.forEachIndexed { i, code ->
                val control = course.controlPoints.single { it.controlId == "fox-$code" }
                assertEquals(parsed.routes.single().points[i + 1].latitude, control.latitude, 0.000000001)
                assertEquals(parsed.routes.single().points[i + 1].longitude, control.longitude, 0.000000001)
            }
        }
    }

    private fun fixture(extension: String): Path =
        Path.of(requireNotNull(javaClass.getResource("/condes/course.$extension")).toURI())
}
