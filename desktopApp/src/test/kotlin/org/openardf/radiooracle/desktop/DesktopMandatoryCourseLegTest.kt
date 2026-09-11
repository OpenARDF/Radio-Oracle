package org.openardf.radiooracle.desktop

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import kotlin.math.roundToInt

class DesktopMandatoryCourseLegTest {
    private val start = CourseGeoPoint(35.0, -78.0)
    private val fox1 = CourseGeoPoint(35.0, -77.99)
    private val fox2 = CourseGeoPoint(35.0, -77.98)
    private val beacon = CourseGeoPoint(35.0, -77.97)
    private val finish = CourseGeoPoint(35.0, -77.96)
    private val objects = linkedMapOf("Start" to start, "1" to fox1, "2" to fox2, "B" to beacon, "Finish" to finish)

    @Test fun legRowsCombineMandatoryBendsWithoutDroppingTheirDistanceOrTime() {
        val firstCorners = listOf(CourseGeoPoint(35.001, -77.998), CourseGeoPoint(35.001, -77.993))
        val middleCorner = CourseGeoPoint(35.001, -77.985)
        val finalCorner = CourseGeoPoint(35.002, -77.965)
        val vertices = listOf(start) + firstCorners + listOf(fox1, middleCorner, fox2, beacon, finalCorner, finish)
        val (project, info) = importRoute(vertices)
        val summary = analyze(project, info)
        val expectedPairs = listOf("S" to "1", "1" to "2", "2" to "B", "B" to "F")
        val expectedLegVertices = listOf(listOf(start) + firstCorners + fox1,
            listOf(fox1, middleCorner, fox2), listOf(fox2, beacon), listOf(beacon, finalCorner, finish))
        val report = DesktopCourseAnalysisExports.reportText(summary)
        for (section in listOfNotNull(summary.providedRouteSection, summary.calculatedRouteSection)) {
            assertEquals(expectedPairs, section.legRows.map { it.fromLabel to it.toLabel })
            var previousDeparture = 0
            section.legRows.zip(expectedLegVertices).forEach { (leg, points) ->
                val expectedLength = points.zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) }.roundToInt()
                assertTrue("${leg.fromLabel} -> ${leg.toLabel} must include every bend",
                    kotlin.math.abs(expectedLength - leg.lengthMeters!!) <= 1)
                assertTrue(leg.arrivalSeconds!! > previousDeparture)
                assertEquals(leg.arrivalSeconds + leg.waitSeconds!! + leg.findPunchSeconds!!, leg.departureSeconds)
                assertTrue(kotlin.math.abs(leg.splitSeconds!! - (leg.departureSeconds!! - previousDeparture)) <= 1)
                previousDeparture = leg.departureSeconds
                assertTrue(report.contains(leg.analysisText()))
            }
            assertEquals(section.estimatedIdealSeconds, section.legRows.last().departureSeconds)
        }
        // Waypoints still belong to the actual geometry and spatial exports.
        assertEquals(4, summary.kmlFolders.single { it.routeName == "Calculated route" }.courseObjects.count {
            it.type == DesktopCourseKmlExportPointType.WAYPOINT
        })
    }

    @Test
    fun originalVerticesSurviveAnalysisMetricsAndKmlRoundTrip() {
        val corners = listOf(CourseGeoPoint(35.001, -77.998), CourseGeoPoint(35.001, -77.993))
        val finishCorner = CourseGeoPoint(35.002, -77.965)
        val vertices = listOf(start) + corners + listOf(fox1, fox2, beacon, finishCorner, finish)
        val (project, info) = importRoute(vertices)
        val waypoints = info.courseObjects.filter { it.type == ProtectedCourseObjectType.WAYPOINT }
        assertEquals(3, waypoints.size)
        assertEquals(listOf("1", "2", "B"), info.controlPoints.map { it.label })
        assertEquals(listOf("B", waypoints.last().label, "Finish"), info.courseObjects.takeLast(3).map { it.label })
        assertTrue("Elevation samples must not become mandatory vertices", info.route.size > vertices.size)

        val summary = analyze(project, info)
        assertTrue(summary.calculatedRouteSection!!.explanation.contains("Other legs may need the same detour"))
        assertTrue(summary.courseRecommendation.paragraph.contains("conditional on the known leg constraints"))
        val application = requireNotNull(summary.calculatedRouteApplication)
        assertEquals("1 2 B", application.idealOrderText)
        val folder = summary.kmlFolders.single { it.title == "Calculated foxes and route" }
        assertEquals(vertices.map { it.latitude to it.longitude }, folder.routeStops.map { it.point.latitude to it.point.longitude })
        val metrics = DesktopCourseRouteMetricsCalculator.metrics(folder.routePoints)
        assertEquals(metrics.horizontalLengthMeters.roundToInt(), application.routeLengthMeters)
        assertEquals(metrics.effectiveLengthMeters!!.roundToInt(), summary.calculatedRouteSection?.effectiveLengthMeters)
        assertEquals(waypoints.map { it.id }, application.orderedPlacementIds.filter { id -> waypoints.any { it.id == id } })

        val path = Files.createTempFile("mandatory-leg-round-trip", ".kml")
        try {
            DesktopCourseAnalysisExports.exportKml(path, summary.copy(kmlFolders = listOf(folder)))
            val (reimported, report) = DesktopCourseKmlImporter.importProtectedCourseInfo(
                path, project, password = null, elevationProvider = { 100.0 }
            )
            assertEquals(1, report.importedCategoryCount)
            val restored = requireNotNull(reimported.raceData.categories.single().category.courseInfo)
            assertEquals(restored.courseObjects.toString(), 3, restored.courseObjects.count { it.type == ProtectedCourseObjectType.WAYPOINT })
            assertEquals(vertices.map { it.latitude to it.longitude }, restored.courseObjects.map { it.latitude to it.longitude })
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun repeatedBeaconRetainsTerminalCornersWithoutAddingAnExtraVisit() {
        val corner = CourseGeoPoint(35.002, -77.965)
        val (_, info) = importRoute(listOf(start, fox1, beacon, fox2, beacon, corner, finish))
        assertEquals(2, info.route.count { it.latitude == beacon.latitude && it.longitude == beacon.longitude })
        assertEquals(1, info.courseObjects.count { it.type == ProtectedCourseObjectType.WAYPOINT })
        assertEquals(listOf("B", "Mandatory point F", "Finish"), info.courseObjects.takeLast(3).map { it.label })
    }

    @Test
    fun reversedLegReversesItsCornersAndUnrelatedLegsDoNotInheritThem() {
        val first = CourseGeoPoint(35.001, -77.983)
        val second = CourseGeoPoint(35.001, -77.987)
        val unrelated = CourseGeoPoint(35.004, -77.999)
        val (project, info) = importRoute(listOf(start, unrelated, fox2, first, second, fox1, beacon, finish))
        val summary = analyze(project, info)
        assertEquals("1 2 B", summary.calculatedRouteApplication?.idealOrderText)
        val stops = summary.kmlFolders.single { it.title == "Calculated foxes and route" }.routeStops
        assertEquals(listOf(start, fox1, second, first, fox2, beacon, finish).map { it.latitude to it.longitude },
            stops.map { it.point.latitude to it.point.longitude })
        val unusedId = info.courseObjects.single { it.latitude == unrelated.latitude && it.longitude == unrelated.longitude }.id
        assertFalse(requireNotNull(summary.calculatedRouteApplication).orderedPlacementIds.contains(unusedId))
        assertFalse(requireNotNull(summary.calculatedRouteSection?.routeMap).points.any { it.label == info.courseObjects.single { it.id == unusedId }.label })

        val prepared = DesktopCourseAnalysisApplier.prepare(project, listOf(DesktopCourseRouteSelection(
            info, requireNotNull(summary.calculatedRouteApplication), info.controlPoints.associate { it.controlId to it.controlId }
        )), password = null)
        val saved = DesktopCourseAnalysisApplier.commit(project, prepared)
        val savedInfo = requireNotNull(saved.raceData.categories.single().category.courseInfo)
        val recalculated = analyze(saved, savedInfo)
        assertEquals(stops.map { it.point.latitude to it.point.longitude },
            recalculated.kmlFolders.single { it.title == "Calculated foxes and route" }.routeStops.map { it.point.latitude to it.point.longitude })
    }

    @Test
    fun routeSelectionIncludesMandatoryDetoursWithoutElevation() {
        val longDetour = CourseGeoPoint(35.06, -77.995)
        val (project, info) = importRoute(listOf(start, longDetour, fox1, fox2, beacon, finish), elevation = { null })
        val summary = analyze(project, info, elevation = { null })
        assertEquals("2 1 B", summary.calculatedRouteApplication?.idealOrderText)
        assertFalse(summary.kmlFolders.single { it.title == "Calculated foxes and route" }.routeStops.any {
            it.point.latitude == longDetour.latitude && it.point.longitude == longDetour.longitude
        })
    }

    @Test
    fun routeSelectionIncludesClimbAtMandatoryCorners() {
        val hill = CourseGeoPoint(35.001, -77.995)
        val vertices = listOf(start, hill, fox1, fox2, beacon, finish)
        val (flatProject, flatInfo) = importRoute(vertices)
        assertEquals("1 2 B", analyze(flatProject, flatInfo).calculatedRouteApplication?.idealOrderText)
        val hillElevation: (CourseGeoPoint) -> Double? = { point ->
            if (point.latitude > 35.0002) 100.0 + (point.latitude - 35.0002) * 1_000_000.0 else 100.0
        }
        val (project, info) = importRoute(vertices, hillElevation)
        val summary = analyze(project, info, hillElevation)
        assertEquals("2 1 B", summary.calculatedRouteApplication?.idealOrderText)
        assertTrue(requireNotNull(summary.providedRouteSection?.effectiveLengthMeters) > requireNotNull(summary.calculatedRouteSection?.effectiveLengthMeters))
    }

    private fun analyze(project: EventProjectFile, info: ProtectedCourseInfo, elevation: (CourseGeoPoint) -> Double? = { 100.0 }) =
        DesktopCourseAnalyzer.analyze(project, "short", info, info.idealOrder,
            elevationLookup = elevation, allowFoxRenumbering = false, prepareApplication = true)

    private fun importRoute(vertices: List<CourseGeoPoint>, elevation: (CourseGeoPoint) -> Double? = { 100.0 }): Pair<EventProjectFile, ProtectedCourseInfo> {
        val base = EventProjectFactory.createEmptyProject("race", "Mandatory route test", "2026-09-13T09:00")
        val controls = listOf("1", "2", "B").mapIndexed { index, label ->
            EventControl(id = "control-$label", raceId = "race", label = label, siCode = 131 + index,
                type = if (label == "B") ControlPointType.BEACON else ControlPointType.CONTROL)
        }
        val project = EventProjectEditor.addCategory(base.copy(raceData = base.raceData.copy(controls = controls)), "short", "Short")
        val path = Files.createTempFile("mandatory-corners", ".kml")
        try {
            fun coordinate(point: CourseGeoPoint) = "${point.longitude},${point.latitude},0"
            val placemarks = objects.entries.joinToString("\n") { (name, point) ->
                "<Placemark><name>$name</name><Point><coordinates>${coordinate(point)}</coordinates></Point></Placemark>"
            }
            Files.writeString(path, """<kml xmlns="http://www.opengis.net/kml/2.2"><Document>
                $placemarks
                <Placemark><name>Classic - 2 Foxes - Short</name><description>Categories: Short</description>
                <LineString><coordinates>${vertices.joinToString(" ", transform = ::coordinate)}</coordinates></LineString></Placemark>
                </Document></kml>""")
            val (updated, report) = DesktopCourseKmlImporter.importProtectedCourseInfo(path, project, password = null, elevationProvider = elevation)
            assertEquals(1, report.importedCategoryCount)
            val assigned = DesktopCourseKmlImporter.applyCategoryAssignmentUpdates(updated, report.categoryAssignmentUpdates)
            return assigned to requireNotNull(assigned.raceData.categories.single().category.courseInfo)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
