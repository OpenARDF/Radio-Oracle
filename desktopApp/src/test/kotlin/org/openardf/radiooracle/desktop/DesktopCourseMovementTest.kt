package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.*

class DesktopCourseMovementTest {
    private val controls = listOf(EventControl("fox1", "race", "Fox1", 131, ControlPointType.CONTROL),
        EventControl("fox2", "race", "Fox2", 132, ControlPointType.CONTROL))

    private fun course(control: EventControl) = ProtectedCourseInfo(idealOrder = control.label,
        lengthMeters = 900, climbMeters = 80, sourceName = "Course Analyzer calculated route",
        route = listOf(ProtectedCourseRoutePoint(40.0, -75.0), ProtectedCourseRoutePoint(41.0, -75.0)),
        controlPoints = listOf(ProtectedCourseControlPoint(control.id, control.label, 40.0, -75.0)))

    private fun project(password: String?): EventProjectFile {
        var p = EventProjectFactory.createEmptyProject("race", "Movement fixture", "2026-09-06T09:00")
        p = p.copy(raceData = p.raceData.copy(controls = controls))
        listOf("m21" to controls[0], "w55" to controls[0], "m60" to controls[1]).forEach { (id, control) ->
            p = EventProjectEditor.addCategory(p, id, id.uppercase())
            p = p.withStoredCourseInfo(id, course(control), password).withStoredIdealOrder(id, control.label, password)
            p = EventProjectEditor.updateCategoryPhysicalStats(p, id, "900", "80")
        }
        val inactive = p.raceData.categories.single { it.category.id == "w55" }
        return p.copy(raceData = p.raceData.copy(categories = p.raceData.categories - inactive, courseMappings = listOf(inactive)))
    }

    @Test fun movementInvalidatesAllDependenciesIncludingInactiveMappingsAndPreservesUnrelatedCourse() {
        for (password in listOf(null, "fixture-password")) {
            val original = project(password)
            val result = DesktopProtectedControlLocationUpdater.applyControlLocations(original, emptyMap(),
                listOf(DesktopProtectedControlLocationUpdate("fox1", 40.1, -75.0)), password)
            assertEquals(setOf("m21", "w55"), result.affectedCategoryIds.toSet())
            assertEquals(2, result.affectedCategoryCount)
            val categories = result.projectFile.raceData.categories + result.projectFile.raceData.courseMappings
            categories.filter { it.category.id in result.affectedCategoryIds }.forEach {
                val info = requireNotNull(it.category.storedCourseInfo(password))
                assertTrue(info.route.isEmpty())
                assertEquals("", info.idealOrder)
                assertEquals("", it.category.storedIdealOrder(password))
                assertNull(info.lengthMeters)
                assertNull(info.climbMeters)
                assertEquals(0, it.category.lengthMeters)
                assertEquals(0, it.category.climbMeters)
                assertTrue(info.sourceName.startsWith("Course Analyzer"))
            }
            assertEquals(original.raceData.categories.last(), result.projectFile.raceData.categories.last())
            val repeated = DesktopProtectedControlLocationUpdater.applyControlLocations(result.projectFile, emptyMap(),
                listOf(DesktopProtectedControlLocationUpdate("fox1", 40.1, -75.0)), password)
            assertEquals(result.projectFile, repeated.projectFile)
            assertTrue(repeated.affectedCategoryIds.isEmpty())
        }
    }

    @Test fun staleLoadedDataAndConflictingUpdatesDoNotApply() {
        val original = project(null)
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedControlLocationUpdater.applyControlLocations(original,
                mapOf("m21" to course(controls[0]).copy(lengthMeters = 123)),
                listOf(DesktopProtectedControlLocationUpdate("fox1", 40.1, -75.0)), null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedControlLocationUpdater.applyControlLocations(original, emptyMap(), listOf(
                DesktopProtectedControlLocationUpdate("fox1", 40.1, -75.0),
                DesktopProtectedControlLocationUpdate("fox1", 40.2, -75.0)), null)
        }
        assertEquals(900, original.raceData.categories.first().category.courseInfo?.lengthMeters)
    }
}
