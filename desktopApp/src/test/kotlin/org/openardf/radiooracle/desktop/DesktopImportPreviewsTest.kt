/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
        assertEquals(0, preview.missingExistingCount)
        assertEquals(0, preview.removableMissingCount)
        assertEquals(0, preview.usedMissingCount)
        assertEquals(1, preview.affectedAssignedCategoryCount)
        assertEquals(1, preview.affectedProtectedCourseCount)
        assertTrue(preview.eventTypeWarnings.any { it.contains("Sprint") && it.contains("Classic") })
    }

    @Test
    fun previewsControlsCsvMissingExistingControlsForOptionalSync() {
        val preview = DesktopImportPreviews.controlsCsvPreview(
            projectFile = projectFile(extraUnusedControl = true),
            sourceName = "classic-controls.csv",
            rows = listOf(
                ControlCsvImportRow(31, ControlPointType.CONTROL, scored = true, publicLabel = "F1", notes = "old")
            ),
            protectedCourseInfoByCategoryId = emptyMap()
        )

        assertEquals(1, preview.unchangedCount)
        assertEquals(1, preview.missingExistingCount)
        assertEquals(1, preview.removableMissingCount)
        assertEquals(0, preview.usedMissingCount)
    }

    @Test
    fun doesNotInferSprintFromFoxoringFastLabelControlNames() {
        val warnings = DesktopImportPreviews.eventTypeWarnings(
            eventRaceType = RaceType.FOXORING,
            sourceName = "Foxoring M21.kml",
            clues = listOf("1", "1F", "2", "2F", "3", "3F", "4", "4F", "5", "5F", "B"),
            controlCount = 11,
            controlTypes = List(10) { ControlPointType.CONTROL } + ControlPointType.BEACON
        )

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun blocksSprintInferenceWhenFileNameSaysFoxO() {
        val warnings = DesktopImportPreviews.eventTypeWarnings(
            eventRaceType = RaceType.CLASSIC,
            sourceName = "M21 Fox-O controls.kml",
            clues = listOf("1F", "2F", "S", "B"),
            controlCount = 8,
            controlTypes = List(6) { ControlPointType.CONTROL } +
                listOf(ControlPointType.SEPARATOR, ControlPointType.BEACON)
        )

        assertTrue(warnings.any { it.contains("Foxoring") && it.contains("Classic") })
        assertTrue(warnings.none { it.contains("Sprint") })
    }

    @Test
    fun infersSprintFromSpectatorWhenFoxCountIsInSprintRange() {
        val warnings = DesktopImportPreviews.eventTypeWarnings(
            eventRaceType = RaceType.CLASSIC,
            sourceName = "M21 controls.kml",
            clues = listOf("1", "1F", "2", "2F", "S", "B"),
            controlCount = 8,
            controlTypes = List(6) { ControlPointType.CONTROL } +
                listOf(ControlPointType.SEPARATOR, ControlPointType.BEACON)
        )

        assertTrue(warnings.any { it.contains("Sprint") && it.contains("Classic") })
    }

    @Test
    fun doesNotInferSprintWhenThereAreMoreThanTenFoxes() {
        val warnings = DesktopImportPreviews.eventTypeWarnings(
            eventRaceType = RaceType.CLASSIC,
            sourceName = "Sprint M21 controls.kml",
            clues = listOf("1", "1F", "2", "2F", "S", "B"),
            controlCount = 13,
            controlTypes = List(11) { ControlPointType.CONTROL } +
                listOf(ControlPointType.SEPARATOR, ControlPointType.BEACON)
        )

        assertEquals(emptyList<String>(), warnings)
    }

    private fun projectFile(
        includeCompetitor: Boolean = false,
        encryptedCourseInfo: String? = null,
        extraUnusedControl: Boolean = false
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
                controls = listOf(control) + if (extraUnusedControl) {
                    listOf(
                        EventControl(
                            id = "control-99",
                            raceId = race.id,
                            label = "99",
                            siCode = 99,
                            type = ControlPointType.CONTROL
                        )
                    )
                } else {
                    emptyList()
                }
            )
        )
    }
}
