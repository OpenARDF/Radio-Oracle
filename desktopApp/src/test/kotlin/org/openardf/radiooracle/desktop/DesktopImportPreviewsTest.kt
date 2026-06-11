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
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow

class DesktopImportPreviewsTest {
    @Test
    fun previewsCategoryCsvUpdatesAndPreservedProtectedCourseData() {
        val preview = DesktopImportPreviews.categoryCsvPreview(
            projectFile = projectFile(includeCompetitor = true, encryptedCourseInfo = "encrypted-course"),
            sourceName = "sprint-categories.csv",
            rows = listOf(
                CategoryCsvImportRow(
                    name = "M21",
                    isMan = true,
                    maxAge = 99,
                    lengthMeters = 2_200,
                    climbMeters = 40,
                    followsRacePresets = false,
                    raceType = RaceType.SPRINT,
                    timeLimitMinutes = 90,
                    raceBand = RaceBand.M80,
                    controlPointsText = "31 32 99B"
                ),
                CategoryCsvImportRow(
                    name = "W21",
                    isMan = false,
                    maxAge = 99,
                    lengthMeters = 2_000,
                    climbMeters = 35,
                    followsRacePresets = true,
                    raceType = null,
                    timeLimitMinutes = null,
                    raceBand = null,
                    controlPointsText = "31 32"
                )
            )
        )

        assertEquals(1, preview.addedCount)
        assertEquals(1, preview.updatedCount)
        assertEquals(1, preview.affectedCompetitorCount)
        assertEquals(1, preview.categoriesWithAssignedControlsReplacedCount)
        assertEquals(1, preview.categoriesWithProtectedCoursePreservedCount)
        assertTrue(preview.eventTypeWarnings.any { it.contains("Sprint") && it.contains("Classic") })
    }

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

    private fun projectFile(
        includeCompetitor: Boolean = false,
        encryptedCourseInfo: String? = null
    ): EventProjectFile {
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
        ).copy(
            encryptedCourseInfo = encryptedCourseInfo
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
                competitorData = if (includeCompetitor) {
                    listOf(
                        org.openardf.radiooracle.shared.event.EventCompetitorData(
                            competitorCategory = org.openardf.radiooracle.shared.event.EventCompetitorCategory(
                                competitor = org.openardf.radiooracle.shared.event.EventCompetitor(
                                    id = "competitor",
                                    raceId = race.id,
                                    categoryId = category.id,
                                    firstName = "Alice",
                                    lastName = "Runner",
                                    club = "",
                                    index = "",
                                    isMan = true,
                                    birthYear = null,
                                    siNumber = null,
                                    siRent = false,
                                    startNumber = 1,
                                    drawnStartTimeSeconds = null
                                ),
                                category = category
                            ),
                            readoutData = null
                        )
                    )
                } else {
                    emptyList()
                },
                unmatchedReadoutData = emptyList(),
                controls = listOf(control)
            )
        )
    }
}
