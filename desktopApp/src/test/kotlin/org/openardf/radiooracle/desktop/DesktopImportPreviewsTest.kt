package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
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
import org.openardf.radiooracle.shared.files.ControlCsvImportRow

class DesktopImportPreviewsTest {
    @Test
    fun previewsControlsCsvChangesAndAffectedCourses() {
        val preview = DesktopImportPreviews.controlsCsvPreview(
            projectFile = projectFile(),
            sourceName = "sprint-controls.csv",
            rows = listOf(
                ControlCsvImportRow(31, ControlPointType.CONTROL, scored = true, publicLabel = "1F", notes = "updated"),
                ControlCsvImportRow(32, ControlPointType.CONTROL, scored = true, publicLabel = "2F", notes = "")
            ),
            protectedCourseInfoByCategoryId = mapOf(
                "cat" to ProtectedCourseInfo(
                    controlPoints = listOf(
                        ProtectedCourseControlPoint(
                            controlId = "control-31",
                            label = "F1",
                            latitude = 45.0,
                            longitude = -122.0
                        )
                    )
                )
            )
        )

        assertEquals(1, preview.addedCount)
        assertEquals(1, preview.changedCount)
        assertEquals(0, preview.unchangedCount)
        assertEquals(1, preview.affectedAssignedCategoryCount)
        assertEquals(1, preview.affectedProtectedCourseCount)
        assertTrue(preview.eventTypeWarnings.any { it.contains("Sprint") && it.contains("Classic") })
    }

    private fun projectFile(): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = "Preview Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
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
            controlPointsString = "31"
        )
        val control = EventControl(
            id = "control-31",
            raceId = race.id,
            label = "F1",
            siCode = 31,
            type = ControlPointType.CONTROL,
            publicLabel = "F1",
            notes = "old"
        )
        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = listOf(
                            EventControlPoint(
                                id = "point-31",
                                categoryId = category.id,
                                siCode = 31,
                                type = ControlPointType.CONTROL,
                                order = 0,
                                controlId = control.id
                            )
                        ),
                        competitors = emptyList(),
                        publicControlIds = listOf(control.id)
                    )
                ),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = listOf(control)
            )
        )
    }
}
