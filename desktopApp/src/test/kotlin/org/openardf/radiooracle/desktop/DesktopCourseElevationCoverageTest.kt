package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.course.*
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType.*

class DesktopCourseElevationCoverageTest {
    private val area = ElevationBounds(0.0, 4.0, 0.0, 4.0)
    private fun marker(id: String, type: ProtectedCourseObjectType, lat: Double, lon: Double) =
        ProtectedCourseObjectPoint(id, id, type, lat, lon, elevationMeters = 120.0)

    private fun project(markers: List<ProtectedCourseObjectPoint>, route: List<ProtectedCourseRoutePoint> = emptyList(),
        raceType: RaceType = RaceType.ORIENTEERING): EventProjectFile {
        val base = EventProjectEditor.addCategory(DesktopCourseImportAvailabilityTest.project(), "blue", "Blue")
        val data = base.raceData.categories.single()
        val info = ProtectedCourseInfo(courseObjects = markers, route = route)
        val foxes = markers.filter { it.type == CONTROL }.mapIndexed { i, m ->
            EventControl(m.id, "race", m.label, 31 + i, ControlPointType.CONTROL)
        }
        val course = data.copy(category = data.category.copy(courseInfo = info), publicControlIds = foxes.map { it.id })
        return base.copy(raceData = base.raceData.copy(race = base.raceData.race.copy(raceType = raceType),
            controls = foxes, categories = emptyList(), courseMappings = listOf(course)))
    }

    private fun check(project: EventProjectFile, gapRow: Int = -1, gapCol: Int = -1) =
        eventElevationCoverage(project, emptyMap(), {}) { _, _ ->
            ElevationCoverage(listOf(ElevationCoverageGrid(area, 5, 5, false) { row, col -> row != gapRow || col != gapCol }))
        }

    @Test fun gapOnStoredBendIsAttributedToNamedLegEvenWithCoveredControlsAndStoredElevations() {
        val stops = listOf(marker("Start", START, 0.0, 0.0), marker("Fox 1", CONTROL, 2.0, 0.0),
            marker("Finish", FINISH, 4.0, 0.0))
        val route = listOf(0.0 to 0.0, 1.0 to 4.0, 2.0 to 0.0, 4.0 to 0.0).map { (lat, lon) -> ProtectedCourseRoutePoint(lat, lon, 120.0) }
        val race = project(stops, route)
        val result = check(race, 1, 4)
        assertTrue(result.areaHasGap)
        assertEquals(setOf("Blue · Start → Fox 1"), result.legs)
        assertTrue(result.controls.isEmpty())
        assertFalse(check(race).areaHasGap)
        assertEquals(CourseElevationFinding(areaHasGap = true), check(race, 4, 4))
        assertEquals(setOf("Fox 1 (SI 31)"), check(race, 2, 0).controls)
    }

    @Test fun requiredWaypointAndAlternateFoxOrderAreCheckedSeparately() {
        val stops = listOf(marker("Start", START, 0.0, 0.0), marker("Bridge", WAYPOINT, 0.0, 4.0),
            marker("Fox 1", CONTROL, 4.0, 4.0), marker("Fox 2", CONTROL, 4.0, 0.0),
            marker("Finish", FINISH, 0.0, 4.0))
        val race = project(stops, raceType = RaceType.CLASSIC)
        assertTrue("Blue · Start → Fox 1" in check(race, 0, 2).legs)
        val other = check(race, 2, 0)
        assertTrue(other.legs.isEmpty())
        assertEquals(listOf("Blue · Start → Fox 2"), other.otherLegs.map { it.description })
    }

    @Test fun catalogOnlyCoordinatesAndMissingLocationsAreDistinguished() {
        val base = DesktopCourseImportAvailabilityTest.project()
        val located = EventControl("a", "race", "Alpha", 31, ControlPointType.CONTROL, latitude = 3.0, longitude = 3.0)
        val missing = located.copy(id = "b", label = "Bravo", siCode = 32, latitude = null)
        val race = base.copy(raceData = base.raceData.copy(controls = listOf(located, missing)))
        val result = eventElevationCoverage(race, emptyMap(), {}) { _, _ -> ElevationCoverage(emptyList()) }
        assertEquals(setOf("Alpha (SI 31)"), result.controls)
        assertEquals(setOf("Bravo (SI 32)"), result.unchecked)
    }

    @Test fun sprintCoverageDoesNotInventADirectSlowToFastLegAcrossTheSpectator() {
        val stops = listOf(marker("Start", START, 0.0, 0.0), marker("1", CONTROL, 0.0, 4.0),
            marker("Spectator", SPECTATOR, 0.0, 2.0), marker("1F", CONTROL, 4.0, 0.0),
            marker("Beacon", BEACON, 4.0, 4.0), marker("Finish", FINISH, 4.0, 3.0))
        assertEquals(CourseElevationFinding(areaHasGap = true), check(project(stops, raceType = RaceType.SPRINT), 2, 2))
    }
}
