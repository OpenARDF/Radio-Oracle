package org.openardf.radiooracle.desktop

import net.lingala.zip4j.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import java.nio.file.Files
import java.util.zip.ZipInputStream

class DesktopControlsRouteKmlKmzExportTest {
    @Test
    fun exportsKmlInsideEncryptedZip() {
        val output = Files.createTempFile("radio-oracle-controls-routes", ".kml.zip")
        val project = sampleProject("course-key")

        val summary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Kml),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(1, summary.categoryCount)
        assertEquals(1, summary.routeCount)
        assertEquals(1, summary.controlCatalogCount)
        assertEquals(2, summary.courseControlPointCount)

        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.kml", header.fileName)
        val kml = zip.getInputStream(header).use { it.readBytes().decodeToString() }
        assertTrue(kml.contains("<name>Course Test controls and routes</name>"))
        assertTrue(kml.contains("<name>Control catalog</name>"))
        assertTrue(kml.contains("<name>M21 route</name>"))
        assertTrue(kml.contains("<Style id=\"courseControlDoughnutStyle\">"))
        assertTrue(kml.contains("<Style id=\"courseStartStyle\">"))
        assertTrue(kml.contains("<Style id=\"courseFinishStyle\">"))
        assertTrue(kml.contains("<Style id=\"courseRoute-cat-m21\">"))
        assertTrue(kml.contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kml.contains("<styleUrl>#courseStartStyle</styleUrl>"))
        val routeColor = extractRouteColorByCategoryId(kml, "cat-m21")
        assertNotEquals("ffffffff", routeColor)
        assertNotEquals("ff00ffff", routeColor)
        assertTrue(kml.contains("-122.0001,45.0001,90"))
        assertTrue(kml.contains("<Data name=\"siCode\"><value>31</value></Data>"))
    }

    @Test
    fun exportsDifferentCategoryColorsForAgeGenderRoutes() {
        val output = Files.createTempFile("radio-oracle-controls-routes", ".kml.zip")
        val project = sampleProjectWithTwoCategories("course-key")

        val summary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Kml),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(2, summary.categoryCount)
        assertEquals(2, summary.routeCount)

        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        val kml = zip.getInputStream(header).use { it.readBytes().decodeToString() }
        val m21Color = extractRouteColorByCategoryId(kml, "cat-m21")
        val w65Color = extractRouteColorByCategoryId(kml, "cat-w65")
        assertNotEquals(m21Color, w65Color)
        assertNotEquals("ffffffff", m21Color)
        assertNotEquals("ff00ffff", m21Color)
        assertNotEquals("ffffffff", w65Color)
        assertNotEquals("ff00ffff", w65Color)
    }

    @Test
    fun exportsKmzInsideEncryptedZip() {
        val output = Files.createTempFile("radio-oracle-controls-routes", ".kmz.zip")
        val project = sampleProject("course-key")

        DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Kmz),
            projectFile = project,
            password = "course-key"
        )

        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.kmz", header.fileName)
        val innerKmzBytes = zip.getInputStream(header).use { it.readBytes() }
        val innerEntries = ZipInputStream(innerKmzBytes.inputStream()).use { innerZip ->
            generateSequence { innerZip.nextEntry }.map { it.name }.toList()
        }
        assertEquals(listOf("doc.kml"), innerEntries)
    }

    @Test
    fun exportsGpxInsideEncryptedZip() {
        val output = Files.createTempFile("radio-oracle-controls-routes", ".gpx.zip")
        val project = sampleProject("course-key")

        val summary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Gpx),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(DesktopControlsRouteKmlKmzExportFormat.Gpx, summary.outputFormat)
        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.gpx", header.fileName)
        val gpx = zip.getInputStream(header).use { it.readBytes().decodeToString() }
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("<name>Course Test controls and routes</name>"))
        assertTrue(gpx.contains("<wpt lat=\"45.0001\" lon=\"-122.0001\">"))
        assertTrue(gpx.contains("<rte>"))
        assertTrue(gpx.contains("<name>M21</name>"))
        assertTrue(gpx.contains("<rtept lat=\"45.0001\" lon=\"-122.0001\">"))
        assertTrue(gpx.contains("<type>Radio-Oracle category route</type>"))
    }

    @Test
    fun rejectsWrongEventPasswordBeforeWritingZip() {
        val output = Files.createTempFile("radio-oracle-controls-routes", ".kml.zip")
        Files.delete(output)
        val project = sampleProject("course-key")

        assertThrows(IllegalArgumentException::class.java) {
            DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
                target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Kml),
                projectFile = project,
                password = "wrong-key"
            )
        }
        assertFalse(Files.exists(output))
    }

    private fun sampleProject(password: String) =
        EventProjectEditor.addCategory(
            EventProjectFactory.createEmptyProject("race", "Course Test", "2026-06-05T09:00").copy(
                raceData = EventProjectFactory.createEmptyProject(
                    "race",
                    "Course Test",
                    "2026-06-05T09:00"
                ).raceData.copy(
                    controls = listOf(
                        EventControl(
                            id = "control-31",
                            raceId = "race",
                            label = "Fox 1",
                            siCode = 31,
                            type = ControlPointType.CONTROL,
                            publicLabel = "1",
                            latitude = 45.0001,
                            longitude = -122.0001
                        )
                    )
                )
            ),
            categoryId = "cat-m21",
            name = "M21"
        ).let { projectFile ->
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                projectFile,
                "cat-m21",
                DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(), password)
            )
        }

    private fun sampleProjectWithTwoCategories(password: String): EventProjectFile {
        val encryptedCourse = DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(), password)
        val withSecondCategory = EventProjectEditor.addCategory(sampleProject(password), categoryId = "cat-w65", name = "W65")
        return EventProjectEditor.updateCategoryEncryptedCourseInfo(
            EventProjectEditor.updateCategoryEncryptedCourseInfo(withSecondCategory, "cat-m21", encryptedCourse),
            "cat-w65",
            encryptedCourse
        )
    }

    private fun extractRouteColorByCategoryId(kml: String, categoryId: String): String {
        val routeStyleId = "courseRoute-$categoryId"
        val colorRegex = Regex("<Style id=\"$routeStyleId\">[\\s\\S]*?<color>([0-9a-fA-F]{8})</color>")
        val match = colorRegex.find(kml) ?: error("Missing route style for category $categoryId")
        return match.groupValues[1].lowercase()
    }

    private fun sampleCourseInfo() = ProtectedCourseInfo(
        idealOrder = "1 2",
        lengthMeters = 1200,
        climbMeters = 25,
        sourceName = "sample.kml",
        sourceSha256 = "abc123",
        sampledPointCount = 2,
        route = listOf(
            ProtectedCourseRoutePoint(latitude = 45.0001, longitude = -122.0001, elevationMeters = 90.0),
            ProtectedCourseRoutePoint(latitude = 45.0008, longitude = -122.0008, elevationMeters = 110.0)
        ),
        controlPoints = listOf(
            ProtectedCourseControlPoint(
                controlId = "control-31",
                label = "1",
                latitude = 45.0001,
                longitude = -122.0001,
                type = ControlPointType.CONTROL,
                elevationMeters = 90.0
            ),
            ProtectedCourseControlPoint(
                controlId = "control-32",
                label = "2",
                latitude = 45.0008,
                longitude = -122.0008,
                type = ControlPointType.CONTROL,
                elevationMeters = 110.0
            )
        ),
        courseObjects = listOf(
            ProtectedCourseObjectPoint(
                id = "start",
                label = "Start",
                type = ProtectedCourseObjectType.START,
                latitude = 45.0,
                longitude = -122.0,
                elevationMeters = 88.0
            )
        )
    )
}
