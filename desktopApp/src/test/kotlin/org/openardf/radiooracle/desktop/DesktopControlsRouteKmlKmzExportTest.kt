package org.openardf.radiooracle.desktop

import net.lingala.zip4j.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
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
        assertTrue(kml.contains("-122.0001,45.0001,90"))
        assertTrue(kml.contains("<Data name=\"siCode\"><value>31</value></Data>"))
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
