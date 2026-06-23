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
        assertEquals(0, summary.controlCatalogCount)
        assertEquals(6, summary.courseControlPointCount)

        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.kml", header.fileName)
        val kml = zip.getInputStream(header).use { it.readBytes().decodeToString() }
        assertTrue(kml.contains("<name>Course Test controls and routes</name>"))
        assertFalse(kml.contains("<name>Control catalog</name>"))
        assertFalse(kml.contains("<name>Category courses</name>"))
        assertTrue(kml.contains("<name>Courses</name>"))
        assertTrue(kml.contains("<name>M21 route</name>"))
        assertTrue(kml.contains("<Style id=\"courseControlDoughnutStyle\">"))
        assertTrue(kml.contains("<Style id=\"courseStartStyle\">"))
        assertTrue(kml.contains("<Style id=\"courseFinishStyle\">"))
        assertTrue(kml.contains("<scale>1.2</scale><color>ffef72ed</color><colorMode>normal</colorMode>"))
        assertTrue(kml.contains("<LabelStyle><color>ffef72ed</color><colorMode>normal</colorMode></LabelStyle>"))
        assertTrue(kml.contains("<href>http://maps.google.com/mapfiles/kml/shapes/donut.png</href>"))
        assertTrue(kml.contains("<href>http://maps.google.com/mapfiles/kml/shapes/triangle.png</href>"))
        assertTrue(kml.contains("<href>http://maps.google.com/mapfiles/kml/shapes/target.png</href>"))
        assertTrue(kml.contains("<Style id=\"courseRoute-cat-m21\">"))
        assertTrue(kml.contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kml.contains("<styleUrl>#courseStartStyle</styleUrl>"))
        assertTrue(kml.contains("<styleUrl>#courseFinishStyle</styleUrl>"))
        assertTrue(kml.contains("<name>Start</name>"))
        assertTrue(kml.contains("<name>Finish</name>"))
        assertTrue(kml.contains("<name>Spectator</name>"))
        assertTrue(kml.placemarkNamed("Start").contains("<styleUrl>#courseStartStyle</styleUrl>"))
        assertTrue(kml.placemarkNamed("Finish").contains("<styleUrl>#courseFinishStyle</styleUrl>"))
        assertTrue(kml.placemarkNamed("Spectator").contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kml.placemarkNamed("1").contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertTrue(kml.placemarkNamed("B").contains("<styleUrl>#courseControlDoughnutStyle</styleUrl>"))
        assertEquals(
            listOf("M21 route", "Start", "Spectator", "B", "Finish", "1", "2"),
            kml.folderPlacemarkNames("Courses")
        )
        val routeColor = extractRouteColorByCategoryId(kml, "cat-m21")
        assertNotEquals("ffffffff", routeColor)
        assertNotEquals("ff00ffff", routeColor)
        assertTrue(kml.contains("-122.0001,45.0001,90"))
        assertTrue(kml.contains("<Data name=\"controlId\"><value>control-31</value></Data>"))
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
    fun exportsSharedStartsAndFinishesOnceAcrossCategoryEndpointVariants() {
        val project = sampleProjectWithEndpointVariants("course-key")
        val kmlOutput = Files.createTempFile("radio-oracle-controls-routes-endpoints", ".kml.zip")

        val kmlSummary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(kmlOutput, DesktopControlsRouteKmlKmzExportFormat.Kml),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(3, kmlSummary.routeCount)
        assertEquals(6, kmlSummary.courseControlPointCount)
        val kmlNames = exportedKmlText(kmlOutput, "course-key").folderPlacemarkNames("Courses")
        assertEquals(1, kmlNames.count { it == "Start" })
        assertEquals(1, kmlNames.count { it == "Finish" })

        val gpxOutput = Files.createTempFile("radio-oracle-controls-routes-endpoints", ".gpx.zip")
        val gpxSummary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(gpxOutput, DesktopControlsRouteKmlKmzExportFormat.Gpx),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(3, gpxSummary.routeCount)
        assertEquals(6, gpxSummary.courseControlPointCount)
        val gpx = exportedGpxText(gpxOutput, "course-key")
        assertEquals(1, gpx.gpxWaypointNames().count { it == "Start" })
        assertEquals(1, gpx.gpxWaypointNames().count { it == "Finish" })
        assertEquals(listOf("M21", "W65", "M40"), gpx.gpxRouteNames())
    }

    @Test
    fun exportsColocatedMandatoryWaypointsOnceAcrossCategoryCoordinateNoise() {
        val project = sampleProjectWithColocatedWaypointVariants("course-key")
        val kmlOutput = Files.createTempFile("radio-oracle-controls-routes-waypoints", ".kml.zip")

        val kmlSummary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(kmlOutput, DesktopControlsRouteKmlKmzExportFormat.Kml),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(2, kmlSummary.routeCount)
        assertEquals(7, kmlSummary.courseControlPointCount)
        val kmlNames = exportedKmlText(kmlOutput, "course-key").folderPlacemarkNames("Courses")
        assertEquals(1, kmlNames.count { it == "Gate A" })

        val gpxOutput = Files.createTempFile("radio-oracle-controls-routes-waypoints", ".gpx.zip")
        val gpxSummary = DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(gpxOutput, DesktopControlsRouteKmlKmzExportFormat.Gpx),
            projectFile = project,
            password = "course-key"
        )

        assertEquals(2, gpxSummary.routeCount)
        assertEquals(7, gpxSummary.courseControlPointCount)
        assertEquals(1, exportedGpxText(gpxOutput, "course-key").gpxWaypointNames().count { it == "Gate A" })
    }

    @Test
    fun exportedCourseKmlImportsBackWithoutDuplicateCourseObjects() {
        val output = Files.createTempFile("radio-oracle-controls-routes-round-trip", ".kml.zip")
        val sourceProject = sampleProjectWithTwoCategories("course-key")
        DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Kml),
            projectFile = sourceProject,
            password = "course-key"
        )
        val kmlPath = Files.createTempFile("radio-oracle-controls-routes-round-trip", ".kml")
        Files.writeString(kmlPath, exportedKmlText(output, "course-key"))
        val targetProject = sampleProjectWithoutProtectedCourses(sourceProject)

        val (importedProject, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = kmlPath,
            projectFile = targetProject,
            password = "course-key",
            elevationProvider = { null }
        )

        assertEquals(2, summary.importedCategoryCount)
        assertEquals(emptyList<String>(), summary.missingControlNames)
        importedProject.raceData.categories.forEach { categoryData ->
            val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
                requireNotNull(categoryData.category.encryptedCourseInfo),
                "course-key"
            )
            val courseObjectLabels = protectedCourseInfo.courseObjects.map { it.label }
            assertEquals(listOf("Start", "1", "Spectator", "B", "2", "Finish"), courseObjectLabels)
            assertEquals(courseObjectLabels.distinct(), courseObjectLabels)
            assertEquals("1 Spectator B 2", protectedCourseInfo.idealOrder)
        }
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
        assertEquals(0, summary.controlCatalogCount)
        assertEquals(6, summary.courseControlPointCount)
        val zip = ZipFile(output.toFile(), "course-key".toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.gpx", header.fileName)
        val gpx = zip.getInputStream(header).use { it.readBytes().decodeToString() }
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("<name>Course Test controls and routes</name>"))
        assertTrue(gpx.indexOf("<metadata>") < gpx.indexOf("<wpt "))
        assertTrue(gpx.indexOf("<wpt ") < gpx.indexOf("<rte>"))
        assertEquals(listOf("Start", "Spectator", "B", "Finish", "1", "2"), gpx.gpxWaypointNames())
        assertTrue(gpx.contains("<rte>"))
        assertTrue(gpx.contains("<name>M21</name>"))
        assertEquals(listOf("M21"), gpx.gpxRouteNames())
        assertEquals(6, Regex("<rtept\\b").findAll(gpx).count())
        assertTrue(gpx.contains("<wpt lat=\"45\" lon=\"-122\">"))
        assertTrue(gpx.contains("<wpt lat=\"45.0001\" lon=\"-122.0001\">"))
        assertTrue(gpx.contains("<type>Radio-Oracle category route</type>"))
        assertFalse(gpx.contains("SI=31"))
    }

    @Test
    fun exportedCourseGpxImportsBackWithoutDuplicateCourseObjects() {
        val output = Files.createTempFile("radio-oracle-controls-routes-round-trip", ".gpx.zip")
        val sourceProject = sampleProjectWithTwoCategories("course-key")
        DesktopControlsRouteKmlKmzExporter.exportEncryptedZip(
            target = DesktopControlsRouteKmlKmzExportTarget(output, DesktopControlsRouteKmlKmzExportFormat.Gpx),
            projectFile = sourceProject,
            password = "course-key"
        )
        val gpxPath = Files.createTempFile("radio-oracle-controls-routes-round-trip", ".gpx")
        Files.writeString(gpxPath, exportedGpxText(output, "course-key"))
        val targetProject = sampleProjectWithoutProtectedCourses(sourceProject)

        val (importedProject, summary) = DesktopCourseKmlImporter.importProtectedCourseInfo(
            path = gpxPath,
            projectFile = targetProject,
            password = "course-key",
            elevationProvider = { null }
        )

        assertEquals(2, summary.importedCategoryCount)
        assertEquals(emptyList<String>(), summary.missingControlNames)
        importedProject.raceData.categories.forEach { categoryData ->
            val protectedCourseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(
                requireNotNull(categoryData.category.encryptedCourseInfo),
                "course-key"
            )
            val courseObjectLabels = protectedCourseInfo.courseObjects.map { it.label }
            assertEquals(listOf("Start", "1", "Spectator", "B", "2", "Finish"), courseObjectLabels)
            assertEquals(courseObjectLabels.distinct(), courseObjectLabels)
            assertEquals("1 Spectator B 2", protectedCourseInfo.idealOrder)
        }
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
                        ),
                        EventControl(
                            id = "control-32",
                            raceId = "race",
                            label = "Fox 2",
                            siCode = 32,
                            type = ControlPointType.CONTROL,
                            publicLabel = "2",
                            latitude = 45.0008,
                            longitude = -122.0008
                        ),
                        EventControl(
                            id = "control-spectator",
                            raceId = "race",
                            label = "Spectator",
                            siCode = 46,
                            type = ControlPointType.SEPARATOR,
                            publicLabel = "Spectator",
                            latitude = 45.0004,
                            longitude = -122.0004
                        ),
                        EventControl(
                            id = "control-beacon",
                            raceId = "race",
                            label = "Beacon",
                            siCode = 99,
                            type = ControlPointType.BEACON,
                            publicLabel = "B",
                            latitude = 45.0006,
                            longitude = -122.0006
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

    private fun sampleProjectWithEndpointVariants(password: String): EventProjectFile {
        val withSecondCategory = EventProjectEditor.addCategory(sampleProject(password), categoryId = "cat-w65", name = "W65")
        val withThirdCategory = EventProjectEditor.addCategory(withSecondCategory, categoryId = "cat-m40", name = "M40")
        return EventProjectEditor.updateCategoryEncryptedCourseInfo(
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                withThirdCategory,
                "cat-w65",
                DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(endpointOffset = 0.00002), password)
            ),
            "cat-m40",
            DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(endpointOffset = -0.00002), password)
        )
    }

    private fun sampleProjectWithColocatedWaypointVariants(password: String): EventProjectFile {
        val withSecondCategory = EventProjectEditor.addCategory(sampleProject(password), categoryId = "cat-w65", name = "W65")
        return EventProjectEditor.updateCategoryEncryptedCourseInfo(
            EventProjectEditor.updateCategoryEncryptedCourseInfo(
                withSecondCategory,
                "cat-m21",
                DesktopProtectedCourseOrder.encryptCourseInfo(sampleCourseInfo(includeWaypoint = true), password)
            ),
            "cat-w65",
            DesktopProtectedCourseOrder.encryptCourseInfo(
                sampleCourseInfo(includeWaypoint = true, waypointOffset = 0.00003),
                password
            )
        )
    }

    private fun sampleProjectWithoutProtectedCourses(project: EventProjectFile): EventProjectFile =
        project.copy(
            raceData = project.raceData.copy(
                categories = project.raceData.categories.map { categoryData ->
                    categoryData.copy(
                        category = categoryData.category.copy(
                            encryptedIdealOrder = null,
                            encryptedCourseInfo = null
                        )
                    )
                }
            )
        )

    private fun exportedKmlText(output: java.nio.file.Path, password: String): String {
        val zip = ZipFile(output.toFile(), password.toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.kml", header.fileName)
        return zip.getInputStream(header).use { it.readBytes().decodeToString() }
    }

    private fun exportedGpxText(output: java.nio.file.Path, password: String): String {
        val zip = ZipFile(output.toFile(), password.toCharArray())
        val header = zip.fileHeaders.single()
        assertEquals("controls-routes.gpx", header.fileName)
        return zip.getInputStream(header).use { it.readBytes().decodeToString() }
    }

    private fun extractRouteColorByCategoryId(kml: String, categoryId: String): String {
        val routeStyleId = "courseRoute-$categoryId"
        val colorRegex = Regex("<Style id=\"$routeStyleId\">[\\s\\S]*?<color>([0-9a-fA-F]{8})</color>")
        val match = colorRegex.find(kml) ?: error("Missing route style for category $categoryId")
        return match.groupValues[1].lowercase()
    }

    private fun String.placemarkNamed(name: String): String {
        val escapedName = Regex.escape(name)
        return Regex("<Placemark>[\\s\\S]*?<name>$escapedName</name>[\\s\\S]*?</Placemark>")
            .find(this)
            ?.value
            ?: error("Missing Placemark named $name")
    }

    private fun String.folderPlacemarkNames(folderName: String): List<String> {
        val escapedName = Regex.escape(folderName)
        val folder = Regex("<Folder>\\s*<name>$escapedName</name>[\\s\\S]*?</Folder>")
            .find(this)
            ?.value
            ?: error("Missing folder $folderName")
        return Regex("<Placemark>[\\s\\S]*?<name>([\\s\\S]*?)</name>[\\s\\S]*?</Placemark>")
            .findAll(folder)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun String.gpxWaypointNames(): List<String> =
        Regex("<wpt\\b[\\s\\S]*?</wpt>")
            .findAll(this)
            .mapNotNull { waypoint ->
                Regex("<name>([\\s\\S]*?)</name>").find(waypoint.value)?.groupValues?.get(1)
            }
            .toList()

    private fun String.gpxRouteNames(): List<String> =
        Regex("<rte>[\\s\\S]*?</rte>")
            .findAll(this)
            .mapNotNull { route ->
                Regex("<name>([\\s\\S]*?)</name>").find(route.value)?.groupValues?.get(1)
            }
            .toList()

    private fun sampleCourseInfo(
        endpointOffset: Double = 0.0,
        includeWaypoint: Boolean = false,
        waypointOffset: Double = 0.0
    ): ProtectedCourseInfo {
        val startLatitude = 45.0 + endpointOffset
        val startLongitude = -122.0 + endpointOffset
        val finishLatitude = 45.0009 + endpointOffset
        val finishLongitude = -122.0009 + endpointOffset
        val waypointLatitude = 45.0002 + waypointOffset
        val waypointLongitude = -122.0002 + waypointOffset
        return ProtectedCourseInfo(
        idealOrder = "1 2",
        lengthMeters = 1200,
        climbMeters = 25,
        sourceName = "sample.kml",
        sourceSha256 = "abc123",
        sampledPointCount = 6,
        route = listOf(
            ProtectedCourseRoutePoint(latitude = startLatitude, longitude = startLongitude, elevationMeters = 88.0),
            ProtectedCourseRoutePoint(latitude = 45.0001, longitude = -122.0001, elevationMeters = 90.0),
            if (includeWaypoint) {
                ProtectedCourseRoutePoint(latitude = waypointLatitude, longitude = waypointLongitude, elevationMeters = 95.0)
            } else {
                null
            },
            ProtectedCourseRoutePoint(latitude = 45.0004, longitude = -122.0004, elevationMeters = 100.0),
            ProtectedCourseRoutePoint(latitude = 45.0006, longitude = -122.0006, elevationMeters = 105.0),
            ProtectedCourseRoutePoint(latitude = 45.0008, longitude = -122.0008, elevationMeters = 110.0),
            ProtectedCourseRoutePoint(latitude = finishLatitude, longitude = finishLongitude, elevationMeters = 112.0)
        ).filterNotNull(),
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
            ),
            ProtectedCourseControlPoint(
                controlId = "control-beacon",
                label = "Beacon",
                latitude = 45.0006,
                longitude = -122.0006,
                type = ControlPointType.BEACON,
                elevationMeters = 105.0
            )
        ),
        courseObjects = listOf(
            ProtectedCourseObjectPoint(
                id = "start",
                label = "Start",
                type = ProtectedCourseObjectType.START,
                latitude = startLatitude,
                longitude = startLongitude,
                elevationMeters = 88.0
            ),
            if (includeWaypoint) {
                ProtectedCourseObjectPoint(
                    id = "waypoint-1-${waypointLatitude}-${waypointLongitude}",
                    label = "Gate A",
                    type = ProtectedCourseObjectType.WAYPOINT,
                    latitude = waypointLatitude,
                    longitude = waypointLongitude,
                    elevationMeters = 95.0
                )
            } else {
                null
            },
            ProtectedCourseObjectPoint(
                id = "spectator",
                label = "Spectator",
                type = ProtectedCourseObjectType.SPECTATOR,
                latitude = 45.0004,
                longitude = -122.0004,
                elevationMeters = 100.0
            ),
            ProtectedCourseObjectPoint(
                id = "control-beacon",
                label = "Beacon",
                type = ProtectedCourseObjectType.BEACON,
                latitude = 45.0006,
                longitude = -122.0006,
                elevationMeters = 105.0
            ),
            ProtectedCourseObjectPoint(
                id = "finish",
                label = "Finish",
                type = ProtectedCourseObjectType.FINISH,
                latitude = finishLatitude,
                longitude = finishLongitude,
                elevationMeters = 112.0
            )
        ).filterNotNull()
    )
    }
}
