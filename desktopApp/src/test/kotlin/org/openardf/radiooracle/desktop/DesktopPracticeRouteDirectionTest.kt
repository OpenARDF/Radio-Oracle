package org.openardf.radiooracle.desktop

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import kotlin.math.roundToInt

class DesktopPracticeRouteDirectionTest {
    private val start = CourseGeoPoint(40.0, -75.0, 100.0)
    private val beacon = CourseGeoPoint(40.004, -75.0, 100.0)
    private val fox1 = EventControl("one", "race", "5", 31, ControlPointType.CONTROL)
    private val fox2 = EventControl("two", "race", "3", 32, ControlPointType.CONTROL)
    private val terminal = EventControl("beacon", "race", "Beacon", 99, ControlPointType.BEACON)
    private val selected = listOf(fox2, fox1, terminal)
    private val direction = DesktopPracticeRouteDirection(RaceLevel.PRACTICE, RaceType.CLASSIC, "M21",
        listOf("one", "two", "beacon"))
    private val corner1 = CourseGeoPoint(40.001, -74.99, 100.0)
    private val corner2 = CourseGeoPoint(40.0035, -74.99, 100.0)
    private val flat: (CourseGeoPoint) -> Double? = { 100.0 }

    private fun note(context: DesktopPracticeRouteDirection = direction,
        beaconPoint: CourseGeoPoint? = beacon, controls: List<EventControl> = selected,
        savedLength: Double? = 2000.0, selectedLength: Double? = 1500.0) =
        context.retentionNote(start, beaconPoint, controls) {
            if (it == context.savedControlIds) savedLength else selectedLength
        }

    @Test fun usesFoxIdentitiesAndLeavesBeaconAndSpectatorOutOfReverseComparison() {
        assertNotNull(note()) // Displayed numbers 5 and 3 are deliberately unrelated to IDs.
        val spectator = EventControl("spectator", "race", "Spectator", 98, ControlPointType.SEPARATOR)
        assertNotNull(note(direction.copy(savedControlIds = listOf("one", "spectator", "two", "beacon")),
            controls = listOf(fox2, fox1, spectator, terminal)))
        assertNull(note(controls = listOf(fox1, fox2, terminal)))
        val third = EventControl("three", "race", "4", 33, ControlPointType.CONTROL)
        assertNull(note(direction.copy(savedControlIds = listOf("one", "two", "three", "beacon")),
            controls = listOf(fox2, third, fox1, terminal)))
    }

    @Test fun requiresPracticeAndExistingStartToBeaconViolation() {
        for (level in RaceLevel.entries.filter { it != RaceLevel.PRACTICE }) assertNull(note(direction.copy(raceLevel = level)))
        assertNull(note(beaconPoint = null))
        assertNull(note(beaconPoint = CourseGeoPoint(40.01, -75.0)))
        val sixHundredMeters = CourseGeoPoint(40.0054, -75.0)
        assertNotNull(note(beaconPoint = sixHundredMeters))
        for (youth in listOf("M12", "W14", "M16")) assertNull(note(direction.copy(categoryName = youth), sixHundredMeters))
        assertNotNull(note(direction.copy(categoryName = "W16")))
        assertNotNull(note(direction.copy(raceType = RaceType.SHORT)))
        assertNull(note(direction.copy(raceType = RaceType.SPRINT)))
        assertNull(note(direction.copy(raceType = RaceType.ORIENTEERING)))
        assertNull(note(direction.copy(raceType = RaceType.FOXORING)))
        assertNotNull(note(direction.copy(raceType = RaceType.FOXORING), CourseGeoPoint(40.001, -75.0)))
    }

    @Test fun requiresCompleteUniqueControlMembershipAndAtLeastTwoFoxes() {
        assertNull(note(direction.copy(savedControlIds = emptyList())))
        assertNull(note(direction.copy(savedControlIds = listOf("one", "beacon", "two"))))
        assertNull(note(direction.copy(savedControlIds = listOf("one", "beacon"))))
        assertNull(note(direction.copy(savedControlIds = listOf("one", "one", "beacon"))))
        assertNull(note(direction.copy(savedControlIds = listOf("one", "other", "beacon"))))
        assertNull(note(direction.copy(savedControlIds = listOf("one", "beacon")), controls = listOf(fox1, terminal)))
    }

    @Test fun requiresStrictlyShorterEffectiveLengthAndNeverUsesHorizontalFallback() {
        assertNull(note(selectedLength = 2000.0))
        assertNull(note(selectedLength = 2200.0))
        assertNotNull(note(selectedLength = 1999.99))
        assertNull(note(savedLength = null))
        assertNull(note(selectedLength = null))
        assertNull(note(savedLength = Double.NaN))
        assertNull(note(selectedLength = Double.POSITIVE_INFINITY))
    }

    @Test fun analyzerRetainsMandatoryGeometryInCalculatedMapsExportsAndApplication() {
        val project = fixture()
        val info = info(project)
        val summary = analyze(project)
        assertTrue("saved=${summary.providedIdealOrder}; calculated=${summary.calculatedIdealOrder}; missing=${summary.missingElements}; note=${summary.calculatedRouteSection?.explanation}", summary.idealOrderMatches == true)
        val calculated = requireNotNull(summary.calculatedRouteSection)
        assertFalse(calculated.summaryOnly)
        assertTrue(calculated.explanation.contains("Practice direction exception"))
        assertTrue(summary.courseRecommendation.paragraph.contains("not the shortest route found"))
        assertTrue(summary.summaryExplanation.contains("Practice direction exception"))
        assertTrue(summary.goodnessMetrics.groups.flatMap { it.metrics }
            .filter { it.label.contains("shortest possible route") }.all { it.value.startsWith("No:") })
        assertTrue(summary.metrics.single { it.label == "Applied route is shortest possible route" }.value.startsWith("No:"))
        for (map in summary.routeMaps) assertEquals(2, map.points.count { it.type == DesktopCourseRouteMapPointType.Waypoint })
        val exported = summary.kmlFolders.single { it.routeName == "Calculated route" }
        for (corner in listOf(corner1, corner2)) assertTrue(exported.routePoints.any { it.distanceMetersTo(corner) < 0.01 })
        val application = requireNotNull(summary.calculatedRouteApplication)
        assertEquals(2, application.courseObjects.count { it.type == ProtectedCourseObjectType.WAYPOINT })
        val expectedLength = DesktopCourseRouteMetricsCalculator.metrics(info.route.map {
            CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters)
        }).effectiveLengthMeters!!.roundToInt()
        assertTrue(kotlin.math.abs(expectedLength - calculated.effectiveLengthMeters!!) <= 1)

        val normal = analyze(project.copy(raceData = project.raceData.copy(race = project.raceData.race.copy(raceLevel = RaceLevel.NATIONAL))))
        assertFalse(normal.idealOrderMatches == true)
        assertTrue(normal.calculatedRouteSection!!.effectiveLengthMeters!! < calculated.effectiveLengthMeters!!)
        assertFalse(normal.calculatedRouteSection!!.explanation.contains("Practice direction exception"))
    }

    @Test fun resultsUseSameDirectionAndInvalidateReferencesWhenPolicyInputsChange() = runBlocking {
        val project = fixture()
        val surface = DesktopFrozenElevationSurface(listOf(RouteElevationSource("practice-flat", "Flat", 1.0)), flat)
        val cache = DesktopClassicRouteAnalysis.CalculationCache()
        val stored = DesktopClassicRouteAnalysis.calculate(project, mapOf("category" to info(project)), surface, cacheState = cache)
        val completed = project.copy(desktopRouteAnalysis = stored)
        val length = DesktopClassicRouteAnalysis.projection(completed).getValue("result")
        assertEquals(length.effectiveMeters, length.idealEffectiveMeters)
        assertEquals("1-2", length.idealRoute)
        assertTrue(length.practiceSavedDirection)
        assertTrue(length.text.contains("Practice saved direction"))
        assertTrue(length.categoryHeadingSuffix.contains("Practice reference route"))
        val restored = EventProjectFileJson.decode(EventProjectFileJson.encode(completed))
        assertEquals(length, DesktopClassicRouteAnalysis.projection(restored).getValue("result"))
        val exports = DesktopPublicResultSiteExports.export(
            java.nio.file.Path.of("build/reports/practice-reference-public-site"), restored,
            protectedCourseInfoByCategoryId = mapOf("category" to info(restored)), includeCourseDiagrams = true)
        assertTrue(java.nio.file.Files.readString(exports.printableResultsHtml).contains("Practice reference route"))
        assertTrue(java.nio.file.Files.readString(exports.printableResultsHtml).contains("Practice saved direction"))
        assertTrue(java.nio.file.Files.readString(exports.publicResultsJson).contains("Practice reference route"))
        assertTrue(java.nio.file.Files.readString(exports.splitResultsCsv).contains("Practice saved direction"))
        val national = completed.copy(raceData = completed.raceData.copy(race = completed.raceData.race.copy(raceLevel = RaceLevel.NATIONAL)))
        assertTrue(DesktopClassicRouteAnalysis.projection(national).isEmpty())
        val updated = DesktopClassicRouteAnalysis.calculate(national, mapOf("category" to info(national)), surface, cacheState = cache)
        assertTrue(updated.contexts.getValue(updated.results.getValue("result").contextId).effectiveMeters < length.idealEffectiveMeters)
        val youth = completed.copy(raceData = completed.raceData.copy(categories = completed.raceData.categories.map {
            it.copy(category = it.category.copy(name = "M16"))
        }))
        assertTrue(DesktopClassicRouteAnalysis.projection(youth).isEmpty())
    }

    @Test fun calculatedElevationMarkersFollowMandatoryGeometryForRetainedAndReorderedCourses() {
        for (level in listOf(RaceLevel.PRACTICE, RaceLevel.NATIONAL)) {
            val original = fixture()
            val project = original.copy(raceData = original.raceData.copy(race = original.raceData.race.copy(raceLevel = level)))
            val summary = analyze(project)
            assertEquals(level == RaceLevel.PRACTICE, summary.idealOrderMatches)
            val calculatedProfile = summary.profileComparison.last()
            val exported = summary.kmlFolders.single { it.routeName == "Calculated route" }
            val route = exported.routePoints
            val controls = info(project).controlPoints.filter { it.type == ControlPointType.CONTROL }
            assertEquals(controls.size, calculatedProfile.markers.size)
            for (marker in calculatedProfile.markers) {
                val control = controls.single { it.label == marker.label }
                val point = CourseGeoPoint(control.latitude, control.longitude)
                val index = route.indices.minBy { route[it].distanceMetersTo(point) }
                assertTrue(route[index].distanceMetersTo(point) < 0.01)
                val expectedDistance = route.take(index + 1).zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) }.roundToInt()
                assertEquals("$level control ${marker.label} must include preceding mandatory bends", expectedDistance, marker.distanceMeters)
                assertTrue("Marker must lie on its plotted elevation profile", calculatedProfile.profile.any {
                    it.distanceMeters == marker.distanceMeters && it.elevationMeters == marker.elevationMeters
                })
            }
        }
    }

    private fun analyze(project: EventProjectFile) = DesktopCourseAnalyzer.analyze(project, "category", info(project),
        info(project).idealOrder, elevationLookup = flat, prepareApplication = true,
        controlIdentityMode = DesktopCourseControlIdentityMode.RESULT_CONTROLS)

    private fun info(project: EventProjectFile) = requireNotNull(project.raceData.categories.single().category.courseInfo)

    private fun fixture(): EventProjectFile {
        val project = DesktopClassicRouteAnalysisTest().fixture()
        val original = info(project)
        fun waypoint(label: String, p: CourseGeoPoint) = ProtectedCourseObjectPoint(label, label,
            ProtectedCourseObjectType.WAYPOINT, p.latitude, p.longitude, p.elevationMeters)
        val controls = original.controlPoints.map { ProtectedCourseObjectPoint(it.controlId, it.label,
            if (it.type == ControlPointType.BEACON) ProtectedCourseObjectType.BEACON else ProtectedCourseObjectType.CONTROL,
            it.latitude, it.longitude, it.elevationMeters) }
        val objects = listOf(original.courseObjects.first().copy(elevationMeters = 100.0), waypoint("Corner A", corner1),
            controls[0], controls[1], waypoint("Corner B", corner2), controls[2], original.courseObjects.last().copy(elevationMeters = 100.0))
        val info = original.copy(idealOrder = "1 2 Beacon", courseObjects = objects,
            route = objects.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) })
        return project.copy(raceData = project.raceData.copy(categories = project.raceData.categories.map {
            it.copy(category = it.category.copy(courseInfo = info, idealOrder = info.idealOrder))
        }))
    }
}
