package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun clearsProtectedCourseDataWhenAssignedCategoryLosesLastControl() {
        val projectFile = projectFile(withAssignedControl = true)

        val result = DesktopProtectedCourseCleanup.removeStaleControlReferencesForDeletedControl(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = mapOf("cat" to protectedCourseInfo()),
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
    fun prunesDeletedAssignedControlFromProtectedCourseDataWhenCourseKeepsControls() {
        val projectFile = projectFileWithTwoAssignedControls()
        val courseInfo = protectedCourseInfoWithTwoControls()

        val result = DesktopProtectedCourseCleanup.removeStaleControlReferencesForDeletedControl(
            projectFile = projectFile,
            protectedCourseInfoByCategoryId = mapOf("cat" to courseInfo),
            controlId = "control-31",
            password = PASSWORD
        )

        val category = result.projectFile.raceData.categories.single().category
        assertNull(category.encryptedIdealOrder)
        assertNotNull(category.encryptedCourseInfo)
        val prunedCourseInfo = result.protectedCourseInfoByCategoryId.getValue("cat")
        assertEquals(listOf("control-32"), prunedCourseInfo.controlPoints.map { it.controlId })
        assertEquals(listOf("control-32"), prunedCourseInfo.courseObjects.map { it.id })
        assertEquals(0, result.clearedCourseCount)
        assertEquals(1, result.prunedCourseCount)
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

    private fun projectFileWithTwoAssignedControls(): EventProjectFile {
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
        val controls = listOf(
            EventControl(
                id = "control-31",
                raceId = race.id,
                label = "F1",
                siCode = 31,
                type = ControlPointType.CONTROL,
                publicLabel = "F1",
                notes = ""
            ),
            EventControl(
                id = "control-32",
                raceId = race.id,
                label = "F2",
                siCode = 32,
                type = ControlPointType.CONTROL,
                publicLabel = "F2",
                notes = ""
            )
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
            controlPointsString = "31,32",
            encryptedIdealOrder = DesktopProtectedCourseOrder.encrypt("31,32", PASSWORD),
            encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedCourseInfoWithTwoControls(), PASSWORD)
        )
        val categoryControlPoints = controls.mapIndexed { index, control ->
            EventControlPoint(
                id = "point-${control.siCode}",
                categoryId = category.id,
                siCode = control.siCode,
                type = ControlPointType.CONTROL,
                order = index + 1,
                controlId = control.id
            )
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
                controls = controls
            )
        )
    }

    private fun protectedCourseInfoWithTwoControls(): ProtectedCourseInfo =
        ProtectedCourseInfo(
            idealOrder = "31,32",
            controlPoints = listOf(
                ProtectedCourseControlPoint(
                    controlId = "control-31",
                    label = "F1",
                    latitude = 45.0,
                    longitude = -122.0
                ),
                ProtectedCourseControlPoint(
                    controlId = "control-32",
                    label = "F2",
                    latitude = 45.1,
                    longitude = -122.1
                )
            ),
            courseObjects = listOf(
                ProtectedCourseObjectPoint(
                    id = "control-31",
                    label = "F1",
                    type = ProtectedCourseObjectType.CONTROL,
                    latitude = 45.0,
                    longitude = -122.0
                ),
                ProtectedCourseObjectPoint(
                    id = "control-32",
                    label = "F2",
                    type = ProtectedCourseObjectType.CONTROL,
                    latitude = 45.1,
                    longitude = -122.1
                )
            )
        )

    private companion object {
        const val PASSWORD = "secret"
    }
}
