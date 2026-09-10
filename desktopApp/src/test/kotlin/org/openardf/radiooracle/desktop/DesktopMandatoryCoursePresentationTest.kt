package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.files.*
import org.openardf.radiooracle.shared.publicresults.*
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

class DesktopMandatoryCoursePresentationTest {
    private val flat = DesktopFrozenElevationSurface(listOf(RouteElevationSource("mandatory-flat", "Flat", 1.0))) { 100.0 }
    private val firstCorner = CourseGeoPoint(40.0005, -74.9998, 100.0)
    private val middleCorner = CourseGeoPoint(40.002, -74.9997, 100.0)
    private val finalCorner = CourseGeoPoint(40.0045, -74.9998, 100.0)

    @Test fun resultsUseMandatoryLegsForIdealAndRecordedOrdersIncludingReverse() = runBlocking {
        for (codes in listOf(listOf(31, 32, 99), listOf(32, 31, 99), listOf(31, 32, 31, 99))) {
            val project = fixture(codes)
            val info = info(project)
            val completed = project.copy(desktopRouteAnalysis = DesktopClassicRouteAnalysis.calculate(project, mapOf("category" to info), flat))
            val length = DesktopClassicRouteAnalysis.projection(completed).getValue("result")
            val points = info.controlPoints.associate { it.controlId to CourseGeoPoint(it.latitude, it.longitude, 100.0) }
            val ids = codes.map { code -> project.raceData.controls.single { it.siCode == code }.id }
            val start = CourseGeoPoint(40.0, -75.0, 100.0)
            val finish = CourseGeoPoint(40.005, -75.0, 100.0)
            val expected = buildList {
                add(start)
                if (codes.first() == 31) add(firstCorner)
                ids.forEachIndexed { index, id ->
                    add(points.getValue(id))
                    if (index < ids.lastIndex && setOf(id, ids[index + 1]) == setOf("one", "two")) add(middleCorner)
                }
                add(finalCorner)
                add(finish)
            }
            val meters = DesktopCourseRouteMetricsCalculator.metrics(expected).horizontalLengthMeters.roundToInt()
            assertEquals(meters, length.effectiveMeters)
            assertEquals("1-2", length.idealRoute)
            assertEquals(0, length.climbMeters)
            assertEquals(codes.size, completed.raceData.competitorData.single().readoutData!!.punches.size)
        }
    }

    @Test fun resultsClimbIncludesMandatoryCornersAndOldMethodIsStale() = runBlocking {
        val project = fixture()
        val hill = DesktopFrozenElevationSurface(listOf(RouteElevationSource("mandatory-hill", "Hill", 1.0))) {
            100.0 + (it.longitude + 75.0).coerceAtLeast(0.0) * 100_000
        }
        val completed = project.copy(desktopRouteAnalysis = DesktopClassicRouteAnalysis.calculate(project, mapOf("category" to info(project)), hill))
        val length = DesktopClassicRouteAnalysis.projection(completed).getValue("result")
        val expected = DesktopCourseRouteMetricsCalculator.metrics(DesktopCourseRouteSampler.sampledStraightRoutePoints(
            info(project).route.map { CourseGeoPoint(it.latitude, it.longitude) }, hill.elevation
        ))
        assertTrue(length.climbMeters > 0)
        assertEquals(expected.climbMeters!!.roundToInt(), length.climbMeters)
        assertEquals(length.horizontalMeters + 10 * length.climbMeters, length.effectiveMeters)
        val old = completed.copy(desktopRouteAnalysis = completed.desktopRouteAnalysis!!.let { saved ->
            saved.copy(contexts = saved.contexts.mapValues { (_, value) -> value.copy(method = "classic-straight-25m-median50-prominence2-rounded-components-applied-bindings-v3") })
        })
        assertTrue(DesktopClassicRouteAnalysis.needsMethodRefresh(old))
        assertTrue(DesktopClassicRouteAnalysis.projection(old).isEmpty())
    }

    @Test fun cloudflareSiteAndResultExportsCarryGeometryAndCorrectedLengths() = runBlocking {
        val input = fixture()
        val info = info(input)
        val project = input.copy(desktopRouteAnalysis = DesktopClassicRouteAnalysis.calculate(input, mapOf("category" to info), flat))
        val lengths = DesktopClassicRouteAnalysis.projection(project)
        val length = lengths.getValue("result")
        val folder = Path.of("build/reports/mandatory-course-presentation")
        Files.createDirectories(folder)
        val paths = DesktopPublicResultSiteExports.export(folder.resolve("public-site"), project,
            protectedCourseInfoByCategoryId = mapOf("category" to info), includeCourseDiagrams = true)
        assertTrue(Files.readString(paths.publicResultsJson).contains("\"estimatedEffectiveRouteLengthMeters\": ${length.effectiveMeters}"))
        assertTrue(Files.readString(paths.printableResultsHtml).contains(length.text))
        assertTrue(Files.readString(paths.splitResultsCsv).contains(length.effectiveMeters.toString()))
        assertTrue(Files.readString(paths.printableResultsHtml).contains("Other legs may require unmodeled detours"))
        val png = paths.eventDirectory.resolve("course-graphics/course-category.png")
        assertTrue(ImageIO.read(png.toFile()).width > 0)
        val summary = DesktopCourseAnalyzer.analyze(project, "category", info, info.idealOrder,
            magneticDeclinationProvider = DesktopMagneticDeclination::result,
            controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS)
        DesktopCourseAnalysisExports.exportPdfAndKml(folder.resolve("course-analysis.pdf"), summary)
        val map = summary.routeMaps.first()
        assertEquals(3, map.points.count { it.type == DesktopCourseRouteMapPointType.Waypoint })
        // The Foxoring web path must preserve the full ordered polyline too.
        assertEquals(DesktopCourseGraphic.webRouteMap(map), DesktopCourseGraphic.webRouteMap(map, simplifyRouteToStops = true))
        val expectedPng = folder.resolve("expected-course.png")
        DesktopCourseGraphic.writeWebPng(expectedPng, map.copy(title = "M21 course"), showWaypointMarkers = false)
        assertArrayEquals(Files.readAllBytes(expectedPng), Files.readAllBytes(png))
        val race = project.raceData
        for (text in listOf(EventCsvExports.results(race, routeLengths = lengths),
            TextResultExports.results(race, routeLengths = lengths), ResultReportExports.xml(race, routeLengths = lengths))) {
            assertTrue(text.contains(length.effectiveMeters.toString()) || text.contains(length.text))
        }
        val iof = IofXmlExports.courseData(race, protectedCourseInfoByCategoryId = mapOf("category" to info))
        assertTrue(iof.contains("<Length>${info.lengthMeters}</Length>"))
        assertFalse(iof.contains("Mandatory point")) // Route bends are not scored controls.
        DesktopProjectFiles.exportSplitResultsPdf(folder.resolve("split-results.pdf"), project)
        DesktopProjectFiles.exportResultReportPdf(folder.resolve("results-report.pdf"), project)
    }

    @Test fun spatialExportsAndNativeSaveKeepMandatoryPoints() {
        val project = fixture()
        val expected = info(project).courseObjects.filter { it.type == ProtectedCourseObjectType.WAYPOINT }
        val reopened = EventProjectFileJson.decode(EventProjectFileJson.encode(project))
        assertEquals(info(project), info(reopened))
        for (format in DesktopControlsRouteKmlKmzExportFormat.entries) {
            val path = Files.createTempFile("mandatory-spatial-", format.plainFileSuffix)
            try {
                DesktopControlsRouteKmlKmzExporter.exportPlainFile(DesktopControlsRouteKmlKmzExportTarget(path, format), reopened)
                val (imported, report) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, project, password = null, elevationProvider = { 100.0 })
                assertEquals(1, report.importedCategoryCount)
                assertEquals(expected.map { it.latitude to it.longitude }, info(imported).courseObjects
                    .filter { it.type == ProtectedCourseObjectType.WAYPOINT }.map { it.latitude to it.longitude })
            } finally { Files.deleteIfExists(path) }
        }
    }

    @Test fun analysisPdfDrawsStoredPolylineInsteadOfReconstructingMarkerOrder() {
        val project = fixture()
        val summary = DesktopCourseAnalyzer.analyze(project, "category", info(project), "1 2 B")
        val map = DesktopCourseRouteMap("Repeated mandatory corner", points = listOf(
            DesktopCourseRouteMapPoint("S", 0.1, 0.9, DesktopCourseRouteMapPointType.Start),
            DesktopCourseRouteMapPoint("Corner", 0.5, 0.2, DesktopCourseRouteMapPointType.Waypoint),
            DesktopCourseRouteMapPoint("F", 0.9, 0.1, DesktopCourseRouteMapPointType.Finish)
        ), routeLabels = listOf("S", "F"), routePointIndexes = listOf(0, 1, 2), lineStrings = listOf(DesktopCourseRouteMapLine("", listOf(
            DesktopCourseRouteMapLinePoint(0.1, 0.9), DesktopCourseRouteMapLinePoint(0.5, 0.2),
            DesktopCourseRouteMapLinePoint(0.2, 0.4), DesktopCourseRouteMapLinePoint(0.5, 0.2),
            DesktopCourseRouteMapLinePoint(0.9, 0.1)
        ), dashed = false)))
        assertEquals(5, map.routeLinesForDrawing().single().points.size)
        assertEquals(3, map.copy(lineStrings = emptyList()).routeLinesForDrawing().single().points.size)
        val path = Files.createTempFile("mandatory-pdf-geometry", ".pdf")
        try {
            DesktopCourseAnalysisExports.exportPdf(path, summary.copy(routeMaps = listOf(map)))
            val pdf = Files.readAllBytes(path).toString(Charsets.ISO_8859_1)
            assertTrue(pdf.contains("166.50 553.00 m 99.00 516.00 l S"))
            assertTrue(pdf.contains("99.00 516.00 m 166.50 553.00 l S"))
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun sharedPublicDiagramKeepsCornersButHidesWaypointMarkersAndLabels() {
        val project = fixture()
        val info = info(project)
        val rendered = PublicResultsSiteRenderer.renderRace(PublicResultsRaceRenderRequest(project,
            protectedCourseInfoByCategoryId = mapOf("category" to info)), "2026-09-10T12:00:00Z", "test")
        val svg = rendered.files.getValue(rendered.courseGraphics.single()).decodeToString()
        val polyline = Regex("<polyline points=\"([^\"]+)\"").find(svg)!!.groupValues[1].split(" ")
        assertEquals(info.route.size, polyline.size)
        // Results hide waypoint symbols and labels, while retaining exactly the same route bends.
        val reviewSvg = CourseDiagramSvg.render("Review", info)
        val reviewPolyline = Regex("<polyline points=\"([^\"]+)\"").find(reviewSvg)!!.groupValues[1]
        assertEquals(reviewPolyline, polyline.joinToString(" "))
        assertTrue(reviewSvg.contains("Mandatory point A"))
        assertFalse(svg.contains("Mandatory point"))
        assertFalse(svg.contains("width=\"28\""))
        val directory = Path.of("build/reports/mandatory-course-presentation")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("shared-public-course.svg"), svg)
    }

    private fun info(project: EventProjectFile) = project.raceData.categories.single().category.courseInfo!!

    private fun fixture(codes: List<Int> = listOf(31, 32, 99)): EventProjectFile {
        val project = DesktopClassicRouteAnalysisTest().fixture(codes)
        val original = info(project)
        fun waypoint(label: String, point: CourseGeoPoint) = ProtectedCourseObjectPoint(label, label,
            ProtectedCourseObjectType.WAYPOINT, point.latitude, point.longitude, point.elevationMeters)
        val controls = original.controlPoints.map { ProtectedCourseObjectPoint(it.controlId, it.label,
            if (it.controlId == "beacon") ProtectedCourseObjectType.BEACON else ProtectedCourseObjectType.CONTROL,
            it.latitude, it.longitude, it.elevationMeters) }
        val objects = listOf(original.courseObjects.first(), waypoint("Mandatory point A", firstCorner), controls[0],
            waypoint("Mandatory point B", middleCorner), controls[1], controls[2], waypoint("Mandatory point C", finalCorner), original.courseObjects.last())
        val route = objects.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, 100.0) }
        val length = DesktopCourseRouteMetricsCalculator.metrics(route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) })
        val info = original.copy(idealOrder = "1 2 B", courseObjects = objects, route = route,
            lengthMeters = length.horizontalLengthMeters.roundToInt(), climbMeters = 0)
        return project.copy(raceData = project.raceData.copy(categories = project.raceData.categories.map {
            it.copy(category = it.category.copy(courseInfo = info, lengthMeters = requireNotNull(info.lengthMeters)))
        }))
    }
}
