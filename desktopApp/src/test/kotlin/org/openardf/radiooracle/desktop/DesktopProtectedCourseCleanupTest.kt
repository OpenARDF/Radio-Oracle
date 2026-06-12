package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType

class DesktopProtectedCourseCleanupTest {

    @Test
    fun clearsProtectedCourseDataForUnassignedCategoryReferencingDeletedControl() {
        val projectFile = projectFile(withAssignedControl = false)
        val courseInfo = protectedCourseInfo()

        val result = DesktopProtectedCourseCleanup.removeStaleControlReferencesForDeletedControl(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = mapOf("cat" to courseInfo),
            controlId = "control-31",
            password = PASSWORD
        )

        val category = result.projectFile.raceData.categories.single().category
        assertNull(category.encryptedIdealOrder)
        assertNull(category.encryptedCourseInfo)
        assertTrue("cat" !in result.protectedCourseInfoByCategoryId)
        assertEquals(1, result.clearedCourseCount)
        assertEquals(0, result.prunedCourseCount)
    }

    @Test
    fun rejectsCleanupWhenControlIsStillAssignedToCategory() {
        val projectFile = projectFile(withAssignedControl = true)

        assertThrows(IllegalArgumentException::class.java) {
            DesktopProtectedCourseCleanup.removeStaleControlReferencesForDeletedControl(
                projectFile = projectFile,
                protectedCourseInfoByCategoryId = mapOf("cat" to protectedCourseInfo()),
                controlId = "control-31",
                password = PASSWORD
            )
        }
    }

    private fun projectFile(withAssignedControl: Boolean): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = "Cleanup Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val control = EventControl(
            id = "control-31",
            raceId = race.id,
            label = "F1",
            siCode = 31,
            type = ControlPointType.CONTROL,
            publicLabel = "F1",
            notes = ""
        )
        val category = EventCategory(
            id = "cat",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = if (withAssignedControl) "31" else "",
            encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt("31", PASSWORD),
            encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedCourseInfo(), PASSWORD)
        )
        val categoryControlPoints = if (withAssignedControl) {
            listOf(
                EventControlPoint(
                    id = "point-31",
                    categoryId = category.id,
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    order = 0,
                    controlId = control.id
                )
            )
        } else {
            emptyList()
        }
        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = categoryControlPoints,
                        competitors = emptyList(),
                        publicControlIds = categoryControlPoints.map { it.controlId }
                    )
                ),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = listOf(control)
            )
        )
    }

    private fun protectedCourseInfo(): ProtectedCourseInfo =
        ProtectedCourseInfo(
            idealOrder = "31",
            controlPoints = listOf(
                ProtectedCourseControlPoint(
                    controlId = "control-31",
                    label = "F1",
                    latitude = 45.0,
                    longitude = -122.0
                )
            ),
            courseObjects = listOf(
                ProtectedCourseObjectPoint(
                    id = "control-31",
                    label = "F1",
                    type = ProtectedCourseObjectType.CONTROL,
                    latitude = 45.0,
                    longitude = -122.0
                )
            )
        )

    private companion object {
        const val PASSWORD = "secret"
    }
}
