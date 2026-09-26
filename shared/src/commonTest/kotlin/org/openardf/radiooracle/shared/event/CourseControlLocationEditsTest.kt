package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseControlLocationEditsTest {
    private val control = EventControl(
        id = "fox-1",
        raceId = "race",
        label = "Fox1",
        siCode = 131,
        type = ControlPointType.CONTROL,
        latitude = 99.0,
        longitude = 99.0
    )

    private fun appliedInfo(): ProtectedCourseInfo {
        val source = ProtectedCourseInfo(
            sourceName = "Course Analyzer applied design",
            idealOrder = "Fox1",
            lengthMeters = 900,
            climbMeters = 80,
            route = listOf(
                ProtectedCourseRoutePoint(40.0, -75.0),
                ProtectedCourseRoutePoint(41.0, -75.0)
            ),
            sampledPointCount = 2,
            controlPoints = listOf(
                ProtectedCourseControlPoint("placement-1", "Fox1", 40.0, -75.0)
            )
        )
        return CourseDesignBindings.prepare(
            info = source,
            controls = listOf(control.copy(latitude = null, longitude = null)),
            controlIdsByPlacementId = mapOf("placement-1" to control.id),
            orderedPlacementIds = listOf("placement-1"),
            revision = "fixture"
        )
    }

    @Test
    fun mutationUpdatesEveryCourseAndRefreshesBindingsWithoutChangingInput() {
        val original = appliedInfo()
        val infos = mapOf("m21" to original, "w55" to original)

        val result = CourseControlLocationEdits.apply(
            controls = listOf(control),
            courseInfoByCategoryId = infos,
            updates = listOf(CourseControlLocationUpdate(control.id, 40.25, -75.5)),
            elevationLookup = { 123.0 }
        )

        assertEquals(setOf("m21", "w55"), result.affectedCategoryIds.toSet())
        assertEquals(40.0, original.controlPoints.single().latitude)
        assertNull(result.controls.single().latitude)
        assertNull(result.controls.single().longitude)
        result.courseInfoByCategoryId.values.forEach { updated ->
            assertTrue(updated.route.isEmpty())
            assertTrue(updated.idealOrder.isEmpty())
            assertNull(updated.lengthMeters)
            assertNull(updated.climbMeters)
            assertEquals(0, updated.sampledPointCount)
            assertEquals(40.25, updated.controlPoints.single().latitude)
            assertEquals(-75.5, updated.controlPoints.single().longitude)
            assertEquals(123.0, updated.controlPoints.single().elevationMeters)
            assertNull(CourseDesignBindings.validationError(updated))
            assertEquals(updated.appliedBindings!!.inputFingerprint, updated.appliedBindings.revision)
            assertNotEquals(original.appliedBindings!!.inputFingerprint, updated.appliedBindings.inputFingerprint)
        }
        val resolved = CourseControlResolver.resolve(
            result.controls.single(),
            result.courseInfoByCategoryId.values.toList()
        )
        assertEquals(CourseResolutionStatus.RESOLVED, resolved.status)
        assertEquals(40.25, resolved.location?.latitude)
    }

    @Test
    fun validationAndReviewProjectionsAreShared() {
        assertEquals(90.0, CourseCoordinateRules.latitudeOrNull(" 90 "))
        assertNull(CourseCoordinateRules.latitudeOrNull("90.1"))
        assertEquals(-180.0, CourseCoordinateRules.longitudeOrNull("-180"))
        assertNull(CourseCoordinateRules.longitudeOrNull("NaN"))
        assertFailsWith<IllegalArgumentException> {
            CourseControlLocationEdits.apply(
                controls = listOf(control),
                courseInfoByCategoryId = mapOf("m21" to appliedInfo()),
                updates = listOf(
                    CourseControlLocationUpdate(control.id, 40.1, -75.0),
                    CourseControlLocationUpdate(control.id, 40.2, -75.0)
                )
            )
        }

        var project = EventProjectFactory.createEmptyProject("race", "Location fixture", "2026-09-26T09:00")
        project = project.copy(raceData = project.raceData.copy(controls = listOf(control.copy(latitude = null, longitude = null))))
        project = EventProjectEditor.addCategory(project, "m21", "M21")
        val before = mapOf("m21" to appliedInfo())
        val mutation = CourseControlLocationEdits.apply(
            controls = project.raceData.controls,
            courseInfoByCategoryId = before,
            updates = listOf(CourseControlLocationUpdate(control.id, 40.25, -75.5))
        )
        val summaries = CourseControlLocationEdits.summaries(project.raceData, mutation.courseInfoByCategoryId)
        assertEquals(40.25, summaries.single().latitude)
        assertEquals(1, summaries.single().affectedCategoryCount)
        val changes = CourseControlLocationEdits.changes(
            race = project.raceData,
            beforeByCategoryId = before,
            afterByCategoryId = mutation.courseInfoByCategoryId,
            directlyAffectedCategoryIds = mutation.affectedCategoryIds.toSet()
        )
        assertEquals("M21", changes.single().categoryName)
        assertTrue(changes.single().usesMovedControl)
        assertEquals(900, changes.single().previousHorizontalLengthMeters)
        assertNull(changes.single().updatedHorizontalLengthMeters)
    }

    @Test
    fun controlFieldReviewAndMutationUseOneSharedRequest() {
        val emptyProject = EventProjectFactory.createEmptyProject(
            "race",
            "Control edit fixture",
            "2026-09-26T09:00"
        )
        val project = emptyProject.copy(raceData = emptyProject.raceData.copy(controls = listOf(control)))
        val edit = CourseControlEditRequest(
            controlId = control.id,
            label = "",
            siCode = 141,
            type = ControlPointType.BEACON,
            scored = false,
            publicLabel = "Beacon",
            notes = "Reviewed",
            location = CourseControlLocation(40.25, -75.5)
        )

        val changes = CourseControlEdits.changes(
            control = control,
            currentLocation = CourseControlLocation(40.0, -75.0),
            edit = edit
        )
        assertEquals(
            setOf("SI code", "Role", "Public label", "Notes", "Latitude", "Longitude"),
            changes.map { it.field }.toSet()
        )

        val updated = CourseControlEdits.applyDetails(project, edit)
        assertEquals(131, project.raceData.controls.single().siCode)
        with(updated.raceData.controls.single()) {
            assertEquals(141, siCode)
            assertEquals(ControlPointType.BEACON, type)
            assertEquals("Beacon", publicLabel)
            assertEquals("Reviewed", notes)
        }
    }
}
