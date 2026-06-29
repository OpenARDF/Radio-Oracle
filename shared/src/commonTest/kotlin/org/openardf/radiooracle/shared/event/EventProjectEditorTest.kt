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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow
import org.openardf.radiooracle.shared.files.IofXmlImports
import org.openardf.radiooracle.shared.sportident.SportIdentCardHolder
import org.openardf.radiooracle.shared.sportident.SportIdentCardPunch
import org.openardf.radiooracle.shared.sportident.SportIdentCardReadout
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EventProjectEditorTest {
    @Test
    fun renamesRaceWithoutChangingOtherProjectData() {
        val original = projectFile("Original Race")

        val renamed = EventProjectEditor.renameRace(original, " Updated Race ")

        assertEquals("Updated Race", renamed.raceData.race.name)
        assertEquals(original.raceData.race.id, renamed.raceData.race.id)
        assertEquals(original.raceData.categories, renamed.raceData.categories)
        assertEquals(original.schemaVersion, renamed.schemaVersion)
    }

    @Test
    fun rejectsBlankRaceName() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameRace(projectFile("Original Race"), "   ")
        }
    }

    @Test
    fun updatesRaceSettingsUsingAndroidMinuteLimitConvention() {
        val original = projectFile("Original Race")

        val updated = EventProjectEditor.updateRaceSettings(
            original,
            raceType = RaceType.FOXORING,
            raceLevel = RaceLevel.REGIONAL,
            raceBand = RaceBand.COMBINED,
            timeLimitMinutes = " 90 "
        )

        val race = updated.raceData.race
        assertEquals(RaceType.FOXORING, race.raceType)
        assertEquals(RaceLevel.REGIONAL, race.raceLevel)
        assertEquals(RaceBand.COMBINED, race.raceBand)
        assertEquals(5_400, race.timeLimitSeconds)
    }

    @Test
    fun rejectsInvalidRaceSettings() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateRaceSettings(projectFile(), RaceType.CLASSIC, RaceLevel.PRACTICE, RaceBand.M80, "")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateRaceSettings(projectFile(), RaceType.CLASSIC, RaceLevel.PRACTICE, RaceBand.M80, "abc")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateRaceSettings(projectFile(), RaceType.CLASSIC, RaceLevel.PRACTICE, RaceBand.M80, "-1")
        }
    }

    @Test
    fun updatesRaceStartDateTime() {
        val updated = EventProjectEditor.updateRaceStartDateTime(projectFile(), " 2026-06-01T09:30 ")

        assertEquals("2026-06-01T09:30", updated.raceData.race.startDateTimeIso)
    }

    @Test
    fun rejectsBlankRaceStartDateTime() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateRaceStartDateTime(projectFile(), " ")
        }
    }

    @Test
    fun renamesCategoryWithoutChangingOtherCategories() {
        val original = projectFile(
            name = "Original Race",
            categories = listOf(categoryData("cat-1", "M21"), categoryData("cat-2", "W21"))
        )

        val updated = EventProjectEditor.renameCategory(original, "cat-2", " W35 ")

        assertEquals(listOf("M21", "W35"), updated.raceData.categories.map { it.category.name })
        assertEquals(original.raceData.categories[0], updated.raceData.categories[0])
    }

    @Test
    fun rejectsBlankCategoryName() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21")))

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCategory(original, "cat-1", " ")
        }
    }

    @Test
    fun rejectsDuplicateCategoryName() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"), categoryData("cat-2", "W21"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCategory(original, "cat-2", "M21")
        }
    }

    @Test
    fun rejectsUnknownCategoryId() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCategory(projectFile(), "missing", "W21")
        }
    }

    @Test
    fun addsCategoryUsingConservativeDefaults() {
        val existingCategory = categoryData("cat-1", "M21")
        val original = projectFile(
            categories = listOf(existingCategory.copy(category = existingCategory.category.copy(order = 4)))
        )

        val updated = EventProjectEditor.addCategory(original, "cat-2", " W21 ")

        val category = updated.raceData.categories.last().category
        assertEquals("cat-2", category.id)
        assertEquals("race", category.raceId)
        assertEquals("W21", category.name)
        assertEquals(5, category.order)
        assertEquals(false, category.differentProperties)
        assertEquals("", category.controlPointsString)
    }

    @Test
    fun rejectsInvalidCategoryAdds() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCategory(original, "", "W21")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCategory(original, "cat-2", " ")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCategory(original, "cat-1", "W21")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCategory(original, "cat-2", "M21")
        }
    }

    @Test
    fun removesCategoryAndUnassignsKeptCompetitors() {
        val original = projectFile(
            categories = listOf(
                categoryData("cat-1", "M21", order = 0, controlSiCodes = listOf(31, 32)),
                categoryData("cat-2", "W21", order = 1, controlSiCodes = listOf(31)),
                categoryData("cat-3", "M40", order = 2)
            ),
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    category = category("cat-1", "M21"),
                    readoutData = readout("result-1", "comp-1", 12345).withResultCategory("cat-1")
                ),
                competitorData("comp-2", "Bob", "Racer", category = category("cat-2", "W21"))
            ),
            unmatchedReadouts = listOf(
                readout("unmatched-result-1", null, 54321).withResultCategory("cat-1")
            )
        )

        val updated = EventProjectEditor.removeCategory(original, "cat-1", deleteCompetitors = false)

        assertEquals(listOf("cat-2", "cat-3"), updated.raceData.categories.map { it.category.id })
        assertEquals(listOf(0, 1), updated.raceData.categories.map { it.category.order })
        assertEquals(listOf(31), updated.raceData.categories.first().controlPoints.map { it.siCode })
        assertEquals(null, updated.raceData.competitorData[0].competitorCategory.competitor.categoryId)
        assertEquals(null, updated.raceData.competitorData[0].competitorCategory.category)
        assertEquals(null, updated.raceData.competitorData[0].readoutData?.result?.categoryId)
        assertEquals("cat-2", updated.raceData.competitorData[1].competitorCategory.competitor.categoryId)
        assertEquals(null, updated.raceData.unmatchedReadoutData.single().result.categoryId)
    }

    @Test
    fun removesCategoryAndAssignedCompetitorsWhenRequested() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"), categoryData("cat-2", "W21")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")),
                competitorData(
                    "comp-2",
                    "Bob",
                    "Racer",
                    category = category("cat-2", "W21"),
                    readoutData = readout("result-2", "comp-2", 23456).withResultCategory("cat-1")
                )
            )
        )

        val updated = EventProjectEditor.removeCategory(original, "cat-1", deleteCompetitors = true)

        assertEquals(listOf("cat-2"), updated.raceData.categories.map { it.category.id })
        assertEquals(listOf("comp-2"), updated.raceData.competitorData.map { it.competitorCategory.competitor.id })
        assertEquals(null, updated.raceData.competitorData.single().readoutData?.result?.categoryId)
    }

    @Test
    fun rejectsUnknownCategoryRemove() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.removeCategory(projectFile(), "missing", deleteCompetitors = false)
        }
    }

    @Test
    fun removesAllAssignedCategoryControlsButKeepsCategoryNamesAndCompetitors() {
        val category1 = categoryData(
            "cat-1",
            "M21",
            controlSiCodes = listOf(31, 32),
            encryptedIdealOrder = "encrypted-order",
            encryptedCourseInfo = "encrypted-course"
        ).let { data ->
            data.copy(category = data.category.copy(lengthMeters = 4500, climbMeters = 120))
        }
        val category2 = categoryData("cat-2", "W21", controlSiCodes = listOf(31)).let { data ->
            data.copy(category = data.category.copy(lengthMeters = 3900, climbMeters = 80))
        }
        val original = projectFile(
            categories = listOf(category1, category2),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")))
        )

        val updated = EventProjectEditor.removeAllAssignedCategoryControls(original)

        assertEquals(listOf("M21", "W21"), updated.raceData.categories.map { it.category.name })
        assertTrue(updated.raceData.categories.all { it.controlPoints.isEmpty() })
        assertTrue(updated.raceData.categories.all { it.publicControlIds.isEmpty() })
        assertTrue(updated.raceData.categories.all { it.category.controlPointsString.isBlank() })
        assertTrue(updated.raceData.categories.all { it.category.lengthMeters == 0 })
        assertTrue(updated.raceData.categories.all { it.category.climbMeters == 0 })
        assertTrue(updated.raceData.categories.all { it.category.encryptedIdealOrder == null })
        assertTrue(updated.raceData.categories.all { it.category.encryptedCourseInfo == null })
        assertEquals("cat-1", updated.raceData.competitorData.single().competitorCategory.competitor.categoryId)
    }

    @Test
    fun removesAllControlsAndClearsDependentCategoryCourseData() {
        val original = projectFile(
            controls = listOf(
                EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "F1"),
                EventControl("control-32", "race", "F2", 32, ControlPointType.CONTROL, publicLabel = "F2")
            ),
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    controlSiCodes = listOf(31, 32),
                    encryptedIdealOrder = "encrypted-order",
                    encryptedCourseInfo = "encrypted-course"
                ).let { data ->
                    data.copy(
                        category = data.category.copy(lengthMeters = 4500, climbMeters = 120),
                        publicControlIds = listOf("control-31")
                    )
                }
            ),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")))
        )

        val updated = EventProjectEditor.removeAllControls(original)

        assertTrue(updated.raceData.controls.isEmpty())
        assertEquals(listOf("M21"), updated.raceData.categories.map { it.category.name })
        assertTrue(updated.raceData.categories.single().controlPoints.isEmpty())
        assertTrue(updated.raceData.categories.single().publicControlIds.isEmpty())
        assertEquals(0, updated.raceData.categories.single().category.lengthMeters)
        assertEquals(0, updated.raceData.categories.single().category.climbMeters)
        assertEquals("", updated.raceData.categories.single().category.controlPointsString)
        assertEquals(null, updated.raceData.categories.single().category.encryptedIdealOrder)
        assertEquals(null, updated.raceData.categories.single().category.encryptedCourseInfo)
        assertEquals("cat-1", updated.raceData.competitorData.single().competitorCategory.competitor.categoryId)
    }

    @Test
    fun removesAllCategoriesAndClearsCompetitorCategoryAssignments() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"), categoryData("cat-2", "W21")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")),
                competitorData("comp-2", "Bob", "Racer", category = category("cat-2", "W21"))
            )
        )

        val updated = EventProjectEditor.removeAllCategories(original)

        assertTrue(updated.raceData.categories.isEmpty())
        assertEquals(listOf("comp-1", "comp-2"), updated.raceData.competitorData.map { it.competitorCategory.competitor.id })
        assertTrue(updated.raceData.competitorData.all { it.competitorCategory.competitor.categoryId == null })
        assertTrue(updated.raceData.competitorData.all { it.competitorCategory.category == null })
    }

    @Test
    fun removesAllCompetitorsAndKeepsMatchedReadoutsAsUnmatched() {
        val category = category("cat-1", "M21")
        val competitor1 = competitorData(
            "comp-1",
            "Alice",
            "Runner",
            category = category,
            readoutData = readout("result-1", "comp-1", siNumber = 1111)
        )
        val competitor2 = competitorData("comp-2", "Bob", "Racer", category = category)
        val original = projectFile(
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    competitors = listOf(competitor1.competitorCategory.competitor, competitor2.competitorCategory.competitor)
                )
            ),
            competitors = listOf(competitor1, competitor2),
            unmatchedReadouts = listOf(readout("result-2", null, siNumber = 2222))
        )

        val updated = EventProjectEditor.removeAllCompetitors(original)

        assertTrue(updated.raceData.competitorData.isEmpty())
        assertTrue(updated.raceData.categories.single().competitors.isEmpty())
        assertEquals(listOf("result-2", "result-1"), updated.raceData.unmatchedReadoutData.map { it.result.id })
        assertTrue(updated.raceData.unmatchedReadoutData.all { it.result.competitorId == null })
    }

    @Test
    fun updatesCategoryControlPointsUsingSharedValidationRules() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21")))

        val updated = EventProjectEditor.updateCategoryControlPoints(original, "cat-1", " 31 32 36B ") { index ->
            "control-$index"
        }

        val categoryData = updated.raceData.categories.single()
        assertEquals("31 32 36B", categoryData.category.controlPointsString)
        assertEquals(listOf("control-0", "control-1", "control-2"), categoryData.controlPoints.map { it.id })
        assertEquals(listOf(31, 32, 36), categoryData.controlPoints.map { it.siCode })
        assertEquals(listOf(1, 2, 3), categoryData.controlPoints.map { it.order })
        assertEquals(listOf(ControlPointType.CONTROL, ControlPointType.CONTROL, ControlPointType.BEACON), categoryData.controlPoints.map { it.type })
    }

    @Test
    fun updatesCategoryControlPointsUsingDefinedControlLabels() {
        val fox = EventControl(
            id = "control-f1",
            raceId = "race",
            label = "F1",
            siCode = 41,
            type = ControlPointType.CONTROL,
            publicLabel = "Fox 1"
        )
        val beacon = EventControl(
            id = "control-m",
            raceId = "race",
            label = "M",
            siCode = 99,
            type = ControlPointType.BEACON,
            publicLabel = "Beacon"
        )
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            controls = listOf(fox, beacon)
        )

        val updated = EventProjectEditor.updateCategoryControlPoints(original, "cat-1", "Fox 1, Beacon") { index ->
            "control-$index"
        }

        val categoryData = updated.raceData.categories.single()
        assertEquals(listOf("control-f1", "control-m"), categoryData.controlPoints.map { it.controlId })
        assertEquals(listOf(41, 99), categoryData.controlPoints.map { it.siCode })
        assertEquals(listOf("control-f1", "control-m"), categoryData.publicControlIds)
    }

    @Test
    fun updatesCategoryControlPointsUsingDefinedNumericControlRoles() {
        val beacon = EventControl(
            id = "control-m",
            raceId = "race",
            label = "M",
            siCode = 99,
            type = ControlPointType.BEACON,
            publicLabel = "Beacon"
        )
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            controls = listOf(beacon)
        )

        val updated = EventProjectEditor.updateCategoryControlPoints(original, "cat-1", "99") { index ->
            "control-$index"
        }

        val categoryData = updated.raceData.categories.single()
        assertEquals(listOf("control-m"), categoryData.controlPoints.map { it.controlId })
        assertEquals(listOf(ControlPointType.BEACON), categoryData.controlPoints.map { it.type })
    }

    @Test
    fun updatesSprintAssignedControlsInNeutralDisplayOrder() {
        val controls = listOf(
            EventControl("control-slow-1", "race", "1", 31, ControlPointType.CONTROL, publicLabel = "Slow 1"),
            EventControl("control-slow-2", "race", "2", 32, ControlPointType.CONTROL, publicLabel = "Slow 2"),
            EventControl("control-fast-1", "race", "F1", 41, ControlPointType.CONTROL, publicLabel = "Fast 1"),
            EventControl("control-fast-2", "race", "F2", 42, ControlPointType.CONTROL, publicLabel = "Fast 2"),
            EventControl("control-s", "race", "S", 46, ControlPointType.SEPARATOR, publicLabel = "Spectator"),
            EventControl("control-m", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
        )
        val original = projectFile(
            raceType = RaceType.SPRINT,
            categories = listOf(categoryData("cat-1", "M21")),
            controls = controls
        )

        val updated = EventProjectEditor.updateCategoryControlPoints(
            original,
            "cat-1",
            "Beacon, Fast 2, Slow 1, Spectator, Fast 1, Slow 2"
        ) { index -> "control-$index" }

        val categoryData = updated.raceData.categories.single()
        assertEquals(
            listOf("control-slow-1", "control-slow-2", "control-s", "control-fast-1", "control-fast-2", "control-m"),
            categoryData.controlPoints.map { it.controlId }
        )
        assertEquals("31 32 46! 41 42 99B", categoryData.category.controlPointsString)
    }

    @Test
    fun clearsCategoryControlPoints() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32))))

        val updated = EventProjectEditor.updateCategoryControlPoints(original, "cat-1", " ") { index ->
            "control-$index"
        }

        val categoryData = updated.raceData.categories.single()
        assertEquals("", categoryData.category.controlPointsString)
        assertEquals(emptyList(), categoryData.controlPoints)
    }

    @Test
    fun rejectsInvalidCategoryControlPointUpdates() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21")))

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryControlPoints(original, "missing", "31") { index -> "control-$index" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryControlPoints(original, "cat-1", "31 31") { index -> "control-$index" }
        }
    }

    @Test
    fun replacesCategoryAssignedControlsByStoredControlIds() {
        val control31 = EventControl("control-31", "race", "31", 31, ControlPointType.CONTROL, publicLabel = "Fox 1")
        val control32 = EventControl("control-32", "race", "32", 32, ControlPointType.CONTROL, publicLabel = "Fox 2")
        val beacon = EventControl("control-99", "race", "99B", 99, ControlPointType.BEACON, publicLabel = "B")
        val original = projectFile(
            controls = listOf(control31, control32, beacon),
            categories = listOf(
                categoryData("cat-1", "M21", controlSiCodes = listOf(31)).copy(
                    publicControlIds = listOf(control31.id)
                )
            )
        )

        val updated = EventProjectEditor.replaceCategoryAssignedControls(
            original,
            categoryId = "cat-1",
            controlIds = listOf(control32.id, beacon.id)
        ) { index ->
            "replacement-$index"
        }

        val categoryData = updated.raceData.categories.single()
        assertEquals(listOf(control32.id, beacon.id), categoryData.controlPoints.map { it.controlId })
        assertEquals(listOf(32, 99), categoryData.controlPoints.map { it.siCode })
        assertEquals(listOf(ControlPointType.CONTROL, ControlPointType.BEACON), categoryData.controlPoints.map { it.type })
        assertEquals("32 99B", categoryData.category.controlPointsString)
        assertEquals(listOf(control32.id, beacon.id), categoryData.publicControlIds)
    }

    @Test
    fun addsControlWithGeneratedLabelWhenLabelIsBlank() {
        val updated = EventProjectEditor.addControl(
            projectFile(),
            controlId = "control-31",
            label = "",
            siCode = "31",
            type = ControlPointType.CONTROL,
            publicLabel = "Fox 1"
        )

        val control = updated.raceData.controls.single()
        assertEquals("31", control.label)
        assertEquals("Fox 1", control.publicLabel)
    }

    @Test
    fun importsControlRowsByAddingOrMergingControls() {
        val original = projectFile(
            controls = listOf(
                EventControl(
                    id = "existing-31",
                    raceId = "race",
                    label = "31",
                    siCode = 31,
                    type = ControlPointType.CONTROL
                )
            )
        )

        val updated = EventProjectEditor.importControlRows(
            original,
            rows = listOf(
                ControlCsvImportRow(31, ControlPointType.CONTROL, scored = true, publicLabel = "F1", notes = "first"),
                ControlCsvImportRow(99, ControlPointType.BEACON, scored = false, publicLabel = "M", notes = "beacon")
            ),
            controlIdFactory = { "new-control" }
        )

        assertEquals(2, updated.raceData.controls.size)
        assertEquals("existing-31", updated.raceData.controls.first { it.siCode == 31 }.id)
        assertEquals("F1", updated.raceData.controls.first { it.siCode == 31 }.publicLabel)
        assertEquals("new-control", updated.raceData.controls.first { it.siCode == 99 }.id)
    }

    @Test
    fun updatesControlWithGeneratedLabelWhenLabelIsBlank() {
        val original = EventProjectEditor.addControl(
            projectFile(),
            controlId = "control-31",
            label = "Original",
            siCode = "31",
            type = ControlPointType.CONTROL
        )

        val updated = EventProjectEditor.updateControl(
            original,
            controlId = "control-31",
            label = "",
            siCode = "99",
            type = ControlPointType.BEACON,
            scored = false,
            publicLabel = "Finish beacon",
            notes = "Updated from UI"
        )

        val control = updated.raceData.controls.single()
        assertEquals("99B", control.label)
        assertEquals(99, control.siCode)
        assertEquals(ControlPointType.BEACON, control.type)
        assertEquals("Finish beacon", control.publicLabel)
    }

    @Test
    fun updatingControlCascadesDerivedCategoryControlFields() {
        val control = EventControl(
            id = "control-31",
            raceId = "race",
            label = "31",
            siCode = 31,
            type = ControlPointType.CONTROL,
            publicLabel = "Fox 1"
        )
        val category = categoryData("cat-1", "M21").copy(
            category = category("cat-1", "M21").copy(controlPointsString = "31"),
            controlPoints = listOf(
                EventControlPoint(
                    id = "cat-1-control-1",
                    categoryId = "cat-1",
                    controlId = control.id,
                    siCode = control.siCode,
                    type = control.type,
                    order = 1
                )
            ),
            publicControlIds = listOf(control.id)
        )
        val original = projectFile(
            controls = listOf(control),
            categories = listOf(category)
        )

        val updated = EventProjectEditor.updateControl(
            original,
            controlId = control.id,
            label = "",
            siCode = "99",
            type = ControlPointType.BEACON,
            scored = false,
            publicLabel = "Beacon",
            notes = ""
        )

        val updatedControlPoint = updated.raceData.categories.single().controlPoints.single()
        assertEquals(control.id, updatedControlPoint.controlId)
        assertEquals(99, updatedControlPoint.siCode)
        assertEquals(ControlPointType.BEACON, updatedControlPoint.type)
        assertEquals("99B", updated.raceData.categories.single().category.controlPointsString)
        assertEquals(listOf(control.id), updated.raceData.categories.single().publicControlIds)
    }

    @Test
    fun clearsPublicControlCoordinatesWhenUpdatingVisibleFields() {
        val original = projectFile(
            controls = listOf(
                EventControl(
                    id = "control-31",
                    raceId = "race",
                    label = "31",
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    latitude = 39.123456,
                    longitude = -95.654321
                )
            )
        )

        val updated = EventProjectEditor.updateControl(
            original,
            controlId = "control-31",
            label = "Fox 1",
            siCode = "31",
            type = ControlPointType.CONTROL,
            scored = true,
            publicLabel = "31",
            notes = "moved"
        )

        val control = updated.raceData.controls.single()
        assertEquals(null, control.latitude)
        assertEquals(null, control.longitude)
    }

    @Test
    fun updatesCategoryPhysicalStats() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21")))

        val updated = EventProjectEditor.updateCategoryPhysicalStats(original, "cat-1", " 6500 ", " 220 ")

        val category = updated.raceData.categories.single().category
        assertEquals(6_500, category.lengthMeters)
        assertEquals(220, category.climbMeters)
    }

    @Test
    fun rejectsInvalidCategoryPhysicalStats() {
        val original = projectFile(categories = listOf(categoryData("cat-1", "M21")))

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryPhysicalStats(original, "missing", "6500", "220")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryPhysicalStats(original, "cat-1", "", "220")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryPhysicalStats(original, "cat-1", "6500", "abc")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryPhysicalStats(original, "cat-1", "-1", "220")
        }
    }

    @Test
    fun renamesCompetitorWithoutChangingOtherCompetitors() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"), competitorData("comp-2", "Bob", "Racer"))
        )

        val updated = EventProjectEditor.renameCompetitor(original, "comp-2", " Robert ", " Runner ")

        assertEquals("Alice", updated.raceData.competitorData[0].competitorCategory.competitor.firstName)
        assertEquals("Robert", updated.raceData.competitorData[1].competitorCategory.competitor.firstName)
        assertEquals("Runner", updated.raceData.competitorData[1].competitorCategory.competitor.lastName)
    }

    @Test
    fun rejectsBlankCompetitorFirstName() {
        val original = projectFile(competitors = listOf(competitorData("comp-1", "Alice", "Runner")))

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCompetitor(original, "comp-1", " ", "Runner")
        }
    }

    @Test
    fun rejectsBlankCompetitorLastName() {
        val original = projectFile(competitors = listOf(competitorData("comp-1", "Alice", "Runner")))

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCompetitor(original, "comp-1", "Alice", " ")
        }
    }

    @Test
    fun rejectsUnknownCompetitorId() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.renameCompetitor(projectFile(), "missing", "Alice", "Runner")
        }
    }

    @Test
    fun assignsCompetitorToCategory() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.assignCompetitorCategory(original, "comp-1", " cat-1 ")

        val competitorCategory = updated.raceData.competitorData.single().competitorCategory
        assertEquals("cat-1", competitorCategory.competitor.categoryId)
        assertEquals("M21", competitorCategory.category?.name)
    }

    @Test
    fun unassignsCompetitorFromCategory() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")))
        )

        val updated = EventProjectEditor.assignCompetitorCategory(original, "comp-1", null)

        val competitorCategory = updated.raceData.competitorData.single().competitorCategory
        assertEquals(null, competitorCategory.competitor.categoryId)
        assertEquals(null, competitorCategory.category)
    }

    @Test
    fun rejectsInvalidCompetitorCategoryAssignment() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.assignCompetitorCategory(original, "missing", "cat-1")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.assignCompetitorCategory(original, "comp-1", "missing")
        }
    }

    @Test
    fun updatesCompetitorClubAndPersonId() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.updateCompetitorClubPersonId(original, "comp-1", " OK Test ", " A101 ")

        val competitor = updated.raceData.competitorData.single().competitorCategory.competitor
        assertEquals("OK Test", competitor.club)
        assertEquals("A101", competitor.index)
        assertEquals("", competitor.bibNumber)
    }

    @Test
    fun updatesCompetitorBibNumberAndCallSign() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.updateCompetitorClubBibCallSign(
            projectFile = original,
            competitorId = "comp-1",
            club = " OK Test ",
            bibNumber = " B101 ",
            callSign = " K0ARDF "
        )

        val competitor = updated.raceData.competitorData.single().competitorCategory.competitor
        assertEquals("OK Test", competitor.club)
        assertEquals("B101", competitor.bibNumber)
        assertEquals("K0ARDF", competitor.callSign)
        assertEquals("", competitor.index)
    }

    @Test
    fun rejectsDuplicateCompetitorBibNumber() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner"),
                competitorData("comp-2", "Bob", "Runner", startNumber = 2)
            )
        )
        val withFirstBib = EventProjectEditor.updateCompetitorClubBibCallSign(
            projectFile = original,
            competitorId = "comp-1",
            club = "",
            bibNumber = "B101",
            callSign = ""
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorClubBibCallSign(
                projectFile = withFirstBib,
                competitorId = "comp-2",
                club = "",
                bibNumber = "B101",
                callSign = ""
            )
        }
    }

    @Test
    fun rejectsDuplicateCompetitorCallSign() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner"),
                competitorData("comp-2", "Bob", "Runner", startNumber = 2)
            )
        )
        val withFirstCallSign = EventProjectEditor.updateCompetitorClubBibCallSign(
            projectFile = original,
            competitorId = "comp-1",
            club = "",
            bibNumber = "",
            callSign = "K0ARDF"
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorClubBibCallSign(
                projectFile = withFirstCallSign,
                competitorId = "comp-2",
                club = "",
                bibNumber = "",
                callSign = "k0ardf"
            )
        }
    }

    @Test
    fun rejectsUnknownCompetitorClubAndPersonIdUpdate() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorClubPersonId(projectFile(), "missing", "OK Test", "A101")
        }
    }

    @Test
    fun updatesCompetitorBirthYear() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.updateCompetitorBirthYear(original, "comp-1", " 1985 ")
        val cleared = EventProjectEditor.updateCompetitorBirthYear(updated, "comp-1", " ")

        assertEquals(1985, updated.raceData.competitorData.single().competitorCategory.competitor.birthYear)
        assertEquals(null, cleared.raceData.competitorData.single().competitorCategory.competitor.birthYear)
    }

    @Test
    fun rejectsInvalidCompetitorBirthYear() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorBirthYear(original, "missing", "1985")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorBirthYear(original, "comp-1", "abc")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorBirthYear(original, "comp-1", "0")
        }
    }

    @Test
    fun updatesCompetitorStartTime() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.updateCompetitorStartTime(original, "comp-1", " 10:15 ")
        val cleared = EventProjectEditor.updateCompetitorStartTime(updated, "comp-1", " ")

        assertEquals(10 * 60L + 15, updated.raceData.competitorData.single().competitorCategory.competitor.drawnStartTimeSeconds)
        assertEquals(null, cleared.raceData.competitorData.single().competitorCategory.competitor.drawnStartTimeSeconds)
    }

    @Test
    fun rejectsInvalidCompetitorStartTime() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorStartTime(original, "missing", "10:15")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorStartTime(original, "comp-1", "10")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorStartTime(original, "comp-1", "10:60")
        }
    }

    @Test
    fun updatesCompetitorNumbersUsingSharedValidationRules() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = 2222)
            )
        )

        val updated = EventProjectEditor.updateCompetitorNumbers(original, "comp-2", " 3 ", " ")

        assertEquals(1, updated.raceData.competitorData[0].competitorCategory.competitor.startNumber)
        assertEquals(2, updated.raceData.competitorData[1].competitorCategory.competitor.startNumber)
        assertEquals(null, updated.raceData.competitorData[1].competitorCategory.competitor.siNumber)
    }

    @Test
    fun rejectsInvalidCompetitorNumberUpdates() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = 2222)
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorNumbers(original, "comp-2", "3", "999")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorNumbers(original, "comp-2", "3", "1111")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorNumbers(original, "missing", "3", "3333")
        }
    }

    @Test
    fun addsCompetitorUsingConservativeDefaults() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111))
        )

        val updated = EventProjectEditor.addCompetitor(original, "comp-2", " Bob ", " Racer ", " 2 ", " ")

        val competitor = updated.raceData.competitorData.last().competitorCategory.competitor
        assertEquals("comp-2", competitor.id)
        assertEquals("race", competitor.raceId)
        assertEquals(null, competitor.categoryId)
        assertEquals("Bob", competitor.firstName)
        assertEquals("Racer", competitor.lastName)
        assertEquals(null, competitor.startNumber)
        assertEquals(null, competitor.siNumber)
    }

    @Test
    fun rejectsInvalidCompetitorAdds() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCompetitor(original, "", "Bob", "Racer", "2", "")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCompetitor(original, "comp-1", "Bob", "Racer", "2", "")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCompetitor(original, "comp-2", "", "Racer", "2", "")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addCompetitor(original, "comp-2", "Bob", "Racer", "2", "1111")
        }
    }

    @Test
    fun importsCategoryRowsWithControlPoints() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", order = 3))
        )

        val updated = EventProjectEditor.importCategoryRows(
            projectFile = original,
            rows = listOf(categoryImportRow(name = "W21", controlPointsText = "31 32 90B")),
            categoryIdFactory = { "cat-2" },
            controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
        )

        val imported = updated.raceData.categories.last()
        assertEquals("cat-2", imported.category.id)
        assertEquals("W21", imported.category.name)
        assertEquals(4, imported.category.order)
        assertEquals("31 32 90B", imported.category.controlPointsString)
        assertEquals(listOf(31, 32, 90), imported.controlPoints.map { it.siCode })
        assertEquals(ControlPointType.BEACON, imported.controlPoints.last().type)
    }

    @Test
    fun addCategoryInfersStandardCategoryGenderFromName() {
        val original = projectFile(categories = emptyList())

        val withWomen = EventProjectEditor.addCategory(original, "cat-w21", "W21")
        val withMen = EventProjectEditor.addCategory(withWomen, "cat-m50", "M50")

        assertEquals(false, withMen.raceData.categories.single { it.category.name == "W21" }.category.isMan)
        assertEquals(true, withMen.raceData.categories.single { it.category.name == "M50" }.category.isMan)
    }

    @Test
    fun updatesCategoryGender() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"))
        )

        val updated = EventProjectEditor.updateCategoryGender(original, "cat-1", false)

        assertEquals(false, updated.raceData.categories.single().category.isMan)
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCategoryGender(original, "missing", false)
        }
    }

    @Test
    fun importCategoryRowsInfersStandardCategoryGenderFromName() {
        val original = projectFile(categories = emptyList())

        val updated = EventProjectEditor.importCategoryRows(
            projectFile = original,
            rows = listOf(
                categoryImportRow(name = "M50", isMan = false),
                categoryImportRow(name = "W21", isMan = true)
            ),
            categoryIdFactory = generateSequence(1) { it + 1 }.map { "cat-$it" }.iterator()::next,
            controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
        )

        assertEquals(true, updated.raceData.categories.single { it.category.name == "M50" }.category.isMan)
        assertEquals(false, updated.raceData.categories.single { it.category.name == "W21" }.category.isMan)
    }

    @Test
    fun updatesExistingCategoryImportsByName() {
        val original = projectFile(
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    order = 7,
                    controlSiCodes = listOf(31),
                    encryptedIdealOrder = "encrypted-order",
                    encryptedCourseInfo = "encrypted-course"
                )
            )
        )

        val outcome = EventProjectEditor.importCategoryRowsWithOutcome(
            projectFile = original,
            rows = listOf(categoryImportRow(name = "M21", controlPointsText = "32 90B")),
            categoryIdFactory = { "cat-2" },
            controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
        )

        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals(1, outcome.projectFile.raceData.categories.size)
        val updated = outcome.projectFile.raceData.categories.single()
        assertEquals("cat-1", updated.category.id)
        assertEquals(7, updated.category.order)
        assertEquals("32 90B", updated.category.controlPointsString)
        assertEquals(listOf(32, 90), updated.controlPoints.map { it.siCode })
        assertEquals("encrypted-order", updated.category.encryptedIdealOrder)
        assertEquals("encrypted-course", updated.category.encryptedCourseInfo)
    }

    @Test
    fun rejectsDuplicateCategoryNamesInSameImport() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCategoryRows(
                projectFile = original,
                rows = listOf(categoryImportRow(name = "W21"), categoryImportRow(name = "W21")),
                categoryIdFactory = { "cat-2" },
                controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
            )
        }
    }

    @Test
    fun importsCompetitorRowsAndCreatesMissingCategories() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111))
        )
        var nextCompetitorId = 2
        var nextCategoryId = 2

        val updated = EventProjectEditor.importCompetitorRows(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Pavel",
                    lastName = "Kolsky",
                    categoryName = "M21",
                    startNumber = 2,
                    index = "T002"
                ),
                competitorImportRow(
                    siNumber = 3333,
                    firstName = "Anna",
                    lastName = "Berg",
                    categoryName = "W21",
                    startNumber = null,
                    index = "T003"
                )
            ),
            competitorIdFactory = { "comp-${nextCompetitorId++}" },
            categoryIdFactory = { "cat-${nextCategoryId++}" }
        )

        assertEquals(listOf("M21", "W21"), updated.raceData.categories.map { it.category.name })
        assertEquals(listOf(null, null, null), updated.raceData.competitorData.map { it.competitorCategory.competitor.startNumber })

        val imported = updated.raceData.competitorData[1].competitorCategory
        assertEquals("comp-2", imported.competitor.id)
        assertEquals("cat-1", imported.competitor.categoryId)
        assertEquals("Pavel", imported.competitor.firstName)
        assertEquals("Kolsky", imported.competitor.lastName)

        val newCategoryCompetitor = updated.raceData.competitorData[2].competitorCategory
        assertEquals("cat-2", newCategoryCompetitor.competitor.categoryId)
        assertEquals("W21", newCategoryCompetitor.category?.name)
        assertEquals(false, newCategoryCompetitor.category?.isMan)
    }

    @Test
    fun importsCompetitorRowsInferMissingCategoryGenderFromName() {
        val original = projectFile()

        val updated = EventProjectEditor.importCompetitorRows(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Pavel",
                    lastName = "Kolsky",
                    categoryName = "M50",
                    isMan = false,
                    startNumber = 1,
                    index = "T001"
                )
            ),
            competitorIdFactory = { "comp-1" },
            categoryIdFactory = { "cat-m50" }
        )

        val category = updated.raceData.categories.single().category
        assertEquals("M50", category.name)
        assertEquals(true, category.isMan)
    }

    @Test
    fun importsCompetitorRowsWithoutCreatingMissingCategoriesWhenDisabled() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"))
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Anna",
                    lastName = "Berg",
                    categoryName = "W21",
                    startNumber = 1,
                    index = "T003"
                )
            ),
            competitorIdFactory = { "comp-1" },
            categoryIdFactory = { "cat-2" },
            createMissingCategories = false
        )

        assertEquals(listOf("M21"), outcome.projectFile.raceData.categories.map { it.category.name })
        val imported = outcome.projectFile.raceData.competitorData.single().competitorCategory
        assertEquals(null, imported.competitor.categoryId)
        assertEquals(null, imported.category)
        assertEquals(
            listOf("Line 1: category 'W21' does not exist; competitor Berg Anna was imported without a category."),
            outcome.warnings
        )
    }

    @Test
    fun importsCompetitorRowsWithoutCategory() {
        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = projectFile(categories = listOf(categoryData("cat-1", "M21"))),
            rows = listOf(competitorImportRow(firstName = "Practice", lastName = "Attendee", categoryName = "")),
            competitorIdFactory = { "comp-1" },
            categoryIdFactory = { "cat-2" }
        )
        val updated = outcome.projectFile

        val competitorCategory = updated.raceData.competitorData.single().competitorCategory
        assertEquals(null, competitorCategory.competitor.categoryId)
        assertEquals(null, competitorCategory.category)
        assertEquals(listOf("M21"), updated.raceData.categories.map { it.category.name })
        assertEquals(listOf("Line 1: competitor Attendee Practice has no category."), outcome.warnings)
    }

    @Test
    fun updatesCompetitorRowsByPersonIdWhenPolicyAllows() {
        val category = category("cat-1", "M21")
        val existing = competitorData(
            "comp-1",
            "Old",
            "Name",
            startNumber = 7,
            siNumber = 1111,
            category = category
        ).let { data ->
            data.copy(
                competitorCategory = data.competitorCategory.copy(
                    competitor = data.competitorCategory.competitor.copy(index = "OK001")
                )
            )
        }
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", competitors = listOf(existing.competitorCategory.competitor))),
            competitors = listOf(existing)
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "New",
                    lastName = "Runner",
                    categoryName = "W21",
                    startNumber = null,
                    siNumber = 2222,
                    index = "OK001"
                )
            ),
            competitorIdFactory = { "comp-new" },
            categoryIdFactory = { "cat-2" },
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_PERSON_ID
        )

        val updatedCompetitor = outcome.projectFile.raceData.competitorData.single().competitorCategory.competitor
        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals("comp-1", updatedCompetitor.id)
        assertEquals("New", updatedCompetitor.firstName)
        assertEquals("Runner", updatedCompetitor.lastName)
        assertEquals(null, updatedCompetitor.startNumber)
        assertEquals(2222, updatedCompetitor.siNumber)
        assertEquals("cat-2", updatedCompetitor.categoryId)
        assertEquals(listOf("M21", "W21"), outcome.projectFile.raceData.categories.map { it.category.name })
    }

    @Test
    fun skipsExistingCompetitorRowsByImportKeyWhenPolicyAllows() {
        val category = category("cat-1", "M21")
        val existing = competitorData(
            "comp-1",
            "Alice",
            "Runner",
            startNumber = 1,
            siNumber = 1111,
            category = category,
            club = "BOK"
        )
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", competitors = listOf(existing.competitorCategory.competitor))),
            competitors = listOf(existing)
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Alice",
                    lastName = "Runner",
                    club = "BOK",
                    startNumber = 99,
                    siNumber = 9999,
                    categoryName = "W21",
                    index = ""
                ),
                competitorImportRow(
                    firstName = "New",
                    lastName = "Starter",
                    club = "MTHD",
                    startNumber = 2,
                    siNumber = 2222,
                    categoryName = "M21",
                    index = ""
                )
            ),
            competitorIdFactory = { "comp-2" },
            categoryIdFactory = { "cat-2" },
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.SKIP_EXISTING_BY_IMPORT_KEY
        )

        val competitors = outcome.projectFile.raceData.competitorData.map { it.competitorCategory.competitor }
        assertEquals(1, outcome.importedCount)
        assertEquals(0, outcome.updatedCount)
        assertEquals(1, outcome.skippedCount)
        assertEquals(0, outcome.deletedCount)
        assertEquals(listOf("comp-1", "comp-2"), competitors.map { it.id })
        assertEquals(1111, competitors.first { it.id == "comp-1" }.siNumber)
        assertEquals("M21", outcome.projectFile.raceData.categories.single().category.name)
    }

    @Test
    fun synchronizesCompetitorRowsByImportKeyAndMovesRemovedReadoutsToUnmatched() {
        val category = category("cat-1", "M21")
        val kept = competitorData(
            "comp-1",
            "Alice",
            "Runner",
            startNumber = 1,
            siNumber = 1111,
            category = category,
            club = "BOK"
        )
        val removed = competitorData(
            "comp-2",
            "Bob",
            "Missing",
            startNumber = 2,
            siNumber = 2222,
            category = category,
            club = "NMO",
            readoutData = readout("result-1", "comp-2", 2222)
        )
        val original = projectFile(
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    competitors = listOf(kept.competitorCategory.competitor, removed.competitorCategory.competitor)
                )
            ),
            competitors = listOf(kept, removed)
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Alice",
                    lastName = "Runner",
                    club = "BOK",
                    startNumber = null,
                    siNumber = 3333,
                    categoryName = "W21",
                    index = ""
                )
            ),
            competitorIdFactory = { "comp-new" },
            categoryIdFactory = { "cat-2" },
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY,
            deleteMissingByImportKey = true
        )

        val updatedCompetitor = outcome.projectFile.raceData.competitorData.single().competitorCategory.competitor
        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals(0, outcome.skippedCount)
        assertEquals(1, outcome.deletedCount)
        assertEquals("comp-1", updatedCompetitor.id)
        assertEquals("Alice", updatedCompetitor.firstName)
        assertEquals(3333, updatedCompetitor.siNumber)
        assertEquals(null, updatedCompetitor.startNumber)
        assertEquals("cat-2", updatedCompetitor.categoryId)
        assertEquals(listOf("M21", "W21"), outcome.projectFile.raceData.categories.map { it.category.name })
        assertEquals("result-1", outcome.projectFile.raceData.unmatchedReadoutData.single().result.id)
        assertEquals(null, outcome.projectFile.raceData.unmatchedReadoutData.single().result.competitorId)
    }

    @Test
    fun synchronizingExistingCompetitorKeepsInternalStartNumberWhenCsvStartNumberCollides() {
        val category = category("cat-1", "M21")
        val alice = competitorData(
            "comp-1",
            "Alice",
            "Runner",
            startNumber = 1,
            siNumber = null,
            category = category,
            club = "BOK"
        )
        val bob = competitorData(
            "comp-2",
            "Bob",
            "Runner",
            startNumber = 2,
            siNumber = 2222,
            category = category,
            club = "BOK"
        )
        val original = projectFile(
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    competitors = listOf(alice.competitorCategory.competitor, bob.competitorCategory.competitor)
                )
            ),
            competitors = listOf(alice, bob)
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Alice",
                    lastName = "Runner",
                    club = "BOK",
                    startNumber = 2,
                    siNumber = 3333,
                    categoryName = "M21",
                    index = ""
                )
            ),
            competitorIdFactory = { "comp-new" },
            categoryIdFactory = { "cat-2" },
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY
        )

        val competitors = outcome.projectFile.raceData.competitorData.map { it.competitorCategory.competitor }
        val updatedAlice = competitors.single { it.id == "comp-1" }
        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals(null, updatedAlice.startNumber)
        assertEquals(3333, updatedAlice.siNumber)
        assertEquals(listOf(null, null), competitors.map { it.startNumber })
    }

    @Test
    fun synchronizingExistingCompetitorCanMatchNameClubWhenCsvAddsRegistrationIndex() {
        val category = category("cat-1", "M21")
        val existing = competitorData(
            "comp-1",
            "Gheorghe",
            "Fala",
            startNumber = 1,
            siNumber = null,
            category = category,
            club = "BOK"
        )
        val original = projectFile(
            categories = listOf(
                categoryData("cat-1", "M21", competitors = listOf(existing.competitorCategory.competitor))
            ),
            competitors = listOf(existing)
        )

        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = original,
            rows = listOf(
                competitorImportRow(
                    firstName = "Gheorghe",
                    lastName = "Fala",
                    club = "BOK",
                    startNumber = 1,
                    siNumber = 2450670,
                    categoryName = "M21",
                    index = "75",
                    bibNumber = "75"
                )
            ),
            competitorIdFactory = { "comp-new" },
            categoryIdFactory = { "cat-2" },
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_IMPORT_KEY
        )

        val updated = outcome.projectFile.raceData.competitorData.single().competitorCategory.competitor
        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals("comp-1", updated.id)
        assertEquals(null, updated.startNumber)
        assertEquals(2450670, updated.siNumber)
        assertEquals("75", updated.index)
        assertEquals("75", updated.bibNumber)
    }

    @Test
    fun rejectsDuplicateCompetitorImportNumbers() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111).let { data ->
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = data.competitorCategory.competitor.copy(
                                index = "OK001",
                                bibNumber = "B101",
                                callSign = "K0ARDF"
                            )
                        )
                    )
                }
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCompetitorRows(
                projectFile = original,
                rows = listOf(competitorImportRow(startNumber = 2, siNumber = 1111)),
                competitorIdFactory = { "comp-2" },
                categoryIdFactory = { "cat-1" }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCompetitorRows(
                projectFile = original,
                rows = listOf(competitorImportRow(startNumber = 2, siNumber = 2222, index = "OK001")),
                competitorIdFactory = { "comp-2" },
                categoryIdFactory = { "cat-1" }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCompetitorRows(
                projectFile = original,
                rows = listOf(competitorImportRow(startNumber = 2, siNumber = 2222, index = "OK002", bibNumber = "B101")),
                competitorIdFactory = { "comp-2" },
                categoryIdFactory = { "cat-1" }
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCompetitorRows(
                projectFile = original,
                rows = listOf(
                    competitorImportRow(
                        startNumber = 2,
                        siNumber = 2222,
                        index = "OK002",
                        bibNumber = "B102",
                        callSign = "k0ardf"
                    )
                ),
                competitorIdFactory = { "comp-2" },
                categoryIdFactory = { "cat-1" }
            )
        }
    }

    @Test
    fun doesNotImportCompetitorStartRowsByStartNumberAlone() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = null)
            )
        )

        val updated = EventProjectEditor.importCompetitorStartRows(
            original,
            listOf(
                CompetitorStartCsvImportRow(startNumber = 2, startTimeText = "10:15", siNumber = 2222),
                CompetitorStartCsvImportRow(startNumber = 99, startTimeText = "10:30", siNumber = 3333)
            )
        )

        val kept = updated.raceData.competitorData[0].competitorCategory.competitor
        val alsoKept = updated.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(null, alsoKept.siNumber)
        assertEquals(null, alsoKept.drawnStartTimeSeconds)
    }

    @Test
    fun importsExportedCompetitorStartRowsBySiNumberWhenStartNumberChanged() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = 2222)
            )
        )

        val updated = EventProjectEditor.importCompetitorStartRows(
            original,
            listOf(
                CompetitorStartCsvImportRow(
                    startNumber = 99,
                    startTimeText = "10:15",
                    siNumber = 2222,
                    bibNumber = "REG002"
                )
            )
        )

        val kept = updated.raceData.competitorData[0].competitorCategory.competitor
        val changed = updated.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(10 * 60L + 15, changed.drawnStartTimeSeconds)
    }

    @Test
    fun importsCompetitorStartRowsByUniqueBibNumberWhenStartNumberChanged() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = null, bibNumber = "1001"),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = null, bibNumber = "1002")
            )
        )

        val updated = EventProjectEditor.importCompetitorStartRows(
            original,
            listOf(
                CompetitorStartCsvImportRow(
                    startNumber = 99,
                    startTimeText = "10:15",
                    siNumber = null,
                    bibNumber = "1002"
                )
            )
        )

        val kept = updated.raceData.competitorData[0].competitorCategory.competitor
        val changed = updated.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(10 * 60L + 15, changed.drawnStartTimeSeconds)
    }

    @Test
    fun importsCompetitorStartRowsByUniqueCallSignWhenStartNumberChanged() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = null, callSign = "K0AAA"),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = null, callSign = "K0BBB")
            )
        )

        val updated = EventProjectEditor.importCompetitorStartRows(
            original,
            listOf(
                CompetitorStartCsvImportRow(
                    startNumber = 99,
                    startTimeText = "10:15",
                    siNumber = null,
                    callSign = "k0bbb"
                )
            )
        )

        val kept = updated.raceData.competitorData[0].competitorCategory.competitor
        val changed = updated.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(10 * 60L + 15, changed.drawnStartTimeSeconds)
    }

    @Test
    fun importsIofStartListUsingSharedMatcher() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111, category = category),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = null, category = category)
            )
        )
        val preview = IofXmlImports.startList(
            """
            <StartList iofVersion="3.0">
              <Event>
                <Name>Original Race</Name>
                <StartTime><Date>2026-05-31</Date><Time>10:00:00</Time></StartTime>
              </Event>
              <ClassStart>
                <Class><Name>M21</Name></Class>
                <PersonStart>
                  <Person><Id>comp-2</Id><Name><Family>Racer</Family><Given>Bob</Given></Name></Person>
                  <Start><BibNumber>1002</BibNumber><StartTime>2026-05-31T10:07:00</StartTime><ControlCard>2222</ControlCard></Start>
                </PersonStart>
              </ClassStart>
            </StartList>
            """.trimIndent()
        ).parsedData

        val outcome = EventProjectEditor.importIofStartList(original, preview)

        assertEquals(1, outcome.updatedCount)
        assertEquals(0, outcome.skippedCount)
        assertEquals(emptyList(), outcome.warnings)
        val kept = outcome.projectFile.raceData.competitorData[0].competitorCategory.competitor
        val changed = outcome.projectFile.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(2222, changed.siNumber)
        assertEquals("1002", changed.bibNumber)
        assertEquals(7 * 60L, changed.drawnStartTimeSeconds)
        assertEquals(1, changed.startNumber)
    }

    @Test
    fun rejectsIofStartListWithDuplicateBibNumber() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111, bibNumber = "1002", category = category),
                competitorData("comp-2", "Bob", "Racer", startNumber = 2, siNumber = null, category = category)
            )
        )
        val preview = IofXmlImports.startList(
            """
            <StartList iofVersion="3.0">
              <Event>
                <Name>Original Race</Name>
                <StartTime><Date>2026-05-31</Date><Time>10:00:00</Time></StartTime>
              </Event>
              <ClassStart>
                <Class><Name>M21</Name></Class>
                <PersonStart>
                  <Person><Id>comp-2</Id><Name><Family>Racer</Family><Given>Bob</Given></Name></Person>
                  <Start><BibNumber>1002</BibNumber><StartTime>2026-05-31T10:07:00</StartTime><ControlCard>2222</ControlCard></Start>
                </PersonStart>
              </ClassStart>
            </StartList>
            """.trimIndent()
        ).parsedData

        val error = assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importIofStartList(original, preview)
        }

        assertEquals("StartList row 1: Bib number must be unique.", error.message)
    }

    @Test
    fun rejectsIofStartListWhenNameFallbackIsAmbiguous() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21")),
            competitors = listOf(
                competitorData("comp-1", "Alex", "Runner", startNumber = 1, category = category),
                competitorData("comp-2", "Alex", "Runner", startNumber = 2, category = category)
            )
        )
        val preview = IofXmlImports.startList(
            """
            <StartList iofVersion="3.0">
              <Event>
                <StartTime><Date>2026-05-31</Date><Time>10:00:00</Time></StartTime>
              </Event>
              <ClassStart>
                <Class><Name>M21</Name></Class>
                <PersonStart>
                  <Person><Name><Family>Runner</Family><Given>Alex</Given></Name></Person>
                  <Start><StartTime>2026-05-31T10:03:00</StartTime></Start>
                </PersonStart>
              </ClassStart>
            </StartList>
            """.trimIndent()
        ).parsedData

        val error = assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importIofStartList(original, preview)
        }

        assertTrue(error.message?.contains("Competitor match is not unique.") == true)
    }

    @Test
    fun importsIofResultListAsMatchedReadoutWithSplitTimes() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32))),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 123456, category = category)
            )
        )
        val preview = IofXmlImports.resultList(
            """
            <ResultList iofVersion="3.0">
              <Event>
                <Name>Original Race</Name>
                <StartTime><Date>2026-05-31</Date><Time>10:00:00</Time></StartTime>
              </Event>
              <ClassResult>
                <Class><Name>M21</Name></Class>
                <PersonResult>
                  <Person><Id>comp-1</Id><Name><Family>Runner</Family><Given>Alice</Given></Name></Person>
                  <Result>
                    <ControlCard>123456</ControlCard>
                    <StartTime>2026-05-31T10:02:00</StartTime>
                    <FinishTime>2026-05-31T10:12:00</FinishTime>
                    <Time>600</Time>
                    <Position>1</Position>
                    <Status>OK</Status>
                    <SplitTime><ControlCode>31</ControlCode><Time>120</Time></SplitTime>
                    <SplitTime><ControlCode>32</ControlCode><Time>420</Time></SplitTime>
                  </Result>
                </PersonResult>
              </ClassResult>
            </ResultList>
            """.trimIndent()
        ).parsedData

        val outcome = EventProjectEditor.importIofResultList(
            projectFile = original,
            preview = preview,
            resultIdFactory = { "iof-result-$it" },
            punchIdFactory = { resultId, index, type -> "$resultId-punch-$index-${type.name}" }
        )

        assertEquals(1, outcome.importedCount)
        assertEquals(0, outcome.skippedCount)
        assertEquals(emptyList(), outcome.warnings)
        val readout = outcome.projectFile.raceData.competitorData.single().readoutData!!
        assertEquals("iof-result-0", readout.result.id)
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(123456, readout.result.siNumber)
        assertEquals(36_120, readout.result.startTimeSeconds)
        assertEquals(36_720, readout.result.finishTimeSeconds)
        assertEquals(600, readout.result.runTimeSeconds)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(false, readout.result.automaticStatus)
        assertEquals(true, readout.result.modified)
        assertEquals(1, readout.result.place)
        assertEquals(
            listOf(SIRecordType.START, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.FINISH),
            readout.punches.map { it.punch.punchType }
        )
        assertEquals(listOf(0, 31, 32, 0), readout.punches.map { it.punch.siCode })
        assertEquals(listOf(36_120L, 36_240L, 36_540L, 36_720L), readout.punches.map { it.punch.siTimeSeconds })
        assertEquals(listOf(0L, 120L, 300L, 180L), readout.punches.map { it.punch.splitSeconds })
    }

    @Test
    fun skipsIofResultListWhenCompetitorAlreadyHasReadout() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31))),
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 123456,
                    category = category,
                    readoutData = readout("existing", "comp-1", 123456)
                )
            )
        )
        val preview = IofXmlImports.resultList(
            """
            <ResultList iofVersion="3.0">
              <ClassResult>
                <Class><Name>M21</Name></Class>
                <PersonResult>
                  <Person><Id>comp-1</Id><Name><Family>Runner</Family><Given>Alice</Given></Name></Person>
                  <Result>
                    <ControlCard>123456</ControlCard>
                    <StartTime>2026-05-31T10:02:00</StartTime>
                    <FinishTime>2026-05-31T10:12:00</FinishTime>
                    <Time>600</Time>
                    <Status>OK</Status>
                  </Result>
                </PersonResult>
              </ClassResult>
            </ResultList>
            """.trimIndent()
        ).parsedData

        val outcome = EventProjectEditor.importIofResultList(
            projectFile = original,
            preview = preview,
            resultIdFactory = { "iof-result-$it" },
            punchIdFactory = { resultId, index, type -> "$resultId-punch-$index-${type.name}" }
        )

        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.skippedCount)
        assertTrue(outcome.warnings.single().contains("already has a readout"))
        assertEquals("existing", outcome.projectFile.raceData.competitorData.single().readoutData?.result?.id)
    }

    @Test
    fun removesCompetitorAndKeepsReadoutAsUnmatched() {
        val readoutData = readout("result-1", "comp-1", siNumber = 1111)
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readoutData)
            )
        )

        val updated = EventProjectEditor.removeCompetitor(original, "comp-1", deleteReadout = false)

        assertEquals(emptyList(), updated.raceData.competitorData)
        assertEquals(1, updated.raceData.unmatchedReadoutData.size)
        assertEquals("result-1", updated.raceData.unmatchedReadoutData.single().result.id)
        assertEquals(null, updated.raceData.unmatchedReadoutData.single().result.competitorId)
        assertEquals(1111, updated.raceData.unmatchedReadoutData.single().result.siNumber)
    }

    @Test
    fun removesCompetitorAndReadoutWhenRequested() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111))
            )
        )

        val updated = EventProjectEditor.removeCompetitor(original, "comp-1", deleteReadout = true)

        assertEquals(emptyList(), updated.raceData.competitorData)
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun removesCompetitorFromCategoryAggregates() {
        val category = category("cat-1", "M21")
        val competitor = competitorData("comp-1", "Alice", "Runner", category = category)
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", competitors = listOf(competitor.competitorCategory.competitor))),
            competitors = listOf(competitor)
        )

        val updated = EventProjectEditor.removeCompetitor(original, "comp-1", deleteReadout = false)

        assertEquals(emptyList(), updated.raceData.categories.single().competitors)
    }

    @Test
    fun rejectsUnknownCompetitorRemove() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.removeCompetitor(projectFile(), "missing", deleteReadout = false)
        }
    }

    @Test
    fun removesMatchedReadoutFromCompetitor() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111))
            )
        )

        val updated = EventProjectEditor.removeReadout(original, "result-1")

        assertEquals(null, updated.raceData.competitorData.single().readoutData)
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun removesUnmatchedReadout() {
        val original = projectFile(
            unmatchedReadouts = listOf(
                readout("result-1", null, 1111),
                readout("result-2", null, 2222)
            )
        )

        val updated = EventProjectEditor.removeReadout(original, "result-1")

        assertEquals(listOf("result-2"), updated.raceData.unmatchedReadoutData.map { it.result.id })
    }

    @Test
    fun rejectsUnknownReadoutRemove() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.removeReadout(projectFile(), "missing")
        }
    }

    @Test
    fun assignsUnmatchedReadoutToCompetitor() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner")
            ),
            unmatchedReadouts = listOf(readout("result-1", null, 1111, sent = true))
        )

        val updated = EventProjectEditor.assignUnmatchedReadout(original, "result-1", "comp-1")
        val readout = updated.raceData.competitorData.single().readoutData!!

        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
        assertEquals("result-1", readout.result.id)
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(true, readout.result.modified)
        assertEquals(false, readout.result.sent)
    }

    @Test
    fun rejectsUnmatchedReadoutAssignmentToCompetitorWithReadout() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("existing", "comp-1", 1111))
            ),
            unmatchedReadouts = listOf(readout("result-1", null, 2222))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.assignUnmatchedReadout(original, "result-1", "comp-1")
        }
    }

    @Test
    fun updatesMatchedReadoutManualStatus() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111))
            )
        )

        val updated = EventProjectEditor.updateReadoutManualStatus(
            original,
            "result-1",
            ResultStatus.DISQUALIFIED
        )

        val result = updated.raceData.competitorData.single().readoutData!!.result
        assertEquals(ResultStatus.DISQUALIFIED, result.resultStatus)
        assertEquals(false, result.automaticStatus)
        assertEquals(true, result.modified)
        assertEquals(false, result.sent)
    }

    @Test
    fun updatesUnmatchedReadoutManualStatus() {
        val original = projectFile(
            unmatchedReadouts = listOf(readout("result-1", null, 1111))
        )

        val updated = EventProjectEditor.updateReadoutManualStatus(
            original,
            "result-1",
            ResultStatus.DID_NOT_FINISH
        )

        val result = updated.raceData.unmatchedReadoutData.single().result
        assertEquals(ResultStatus.DID_NOT_FINISH, result.resultStatus)
        assertEquals(false, result.automaticStatus)
        assertEquals(true, result.modified)
        assertEquals(false, result.sent)
    }

    @Test
    fun rejectsUnknownReadoutStatusUpdate() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateReadoutManualStatus(projectFile(), "missing", ResultStatus.DISQUALIFIED)
        }
    }

    @Test
    fun editsMatchedReadoutAndCanUpdateCompetitorCategory() {
        val shortCategory = category("cat-short", "M21")
        val longCategory = category("cat-long", "M40")
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            categories = listOf(
                categoryData("cat-short", "M21", controlSiCodes = listOf(31)),
                categoryData("cat-long", "M40", controlSiCodes = listOf(31, 33))
            ),
            controls = listOf(
                EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"),
                EventControl("control-33", "race", "F3", 33, ControlPointType.CONTROL, publicLabel = "Fox 3")
            ),
            aliases = listOf(alias("alias-31", 31, "F1"), alias("alias-33", 33, "F3")),
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 1111,
                    category = shortCategory,
                    readoutData = readout("result-1", "comp-1", 1111, sent = true)
                )
            )
        )

        val updated = EventProjectEditor.updateReadoutEdit(
            projectFile = original,
            resultId = "result-1",
            startSeconds = "10:00",
            finishSeconds = "30:00",
            controlPunchesText = "Fox 1 @ 15:00\nFox 3 @ 20:00",
            resultStatus = ResultStatus.OK,
            categoryId = longCategory.id,
            updateCompetitorCategory = true,
            punchIdFactory = { index, type -> "edit-$index-${type.name}" }
        )

        val competitorData = updated.raceData.competitorData.single()
        val readout = competitorData.readoutData!!
        assertEquals(longCategory.id, competitorData.competitorCategory.competitor.categoryId)
        assertEquals(longCategory, competitorData.competitorCategory.category)
        assertEquals(longCategory.id, readout.result.categoryId)
        assertEquals(36_600, readout.result.startTimeSeconds)
        assertEquals(37_800, readout.result.finishTimeSeconds)
        assertEquals(1200, readout.result.runTimeSeconds)
        assertEquals(2, readout.result.points)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(false, readout.result.automaticStatus)
        assertEquals(true, readout.result.modified)
        assertEquals(false, readout.result.sent)
        assertEquals(1, readout.result.place)
        assertEquals(
            listOf(SIRecordType.START, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.FINISH),
            readout.punches.map { it.punch.punchType }
        )
        assertEquals(listOf(0, 31, 33, 0), readout.punches.map { it.punch.siCode })
        assertEquals(listOf(36_600L, 36_900L, 37_200L, 37_800L), readout.punches.map { it.punch.siTimeSeconds })
        assertEquals(listOf(0L, 300L, 300L, 600L), readout.punches.map { it.punch.splitSeconds })
        assertEquals(listOf(null, "F1", "F3", null), readout.punches.map { it.alias?.name })
    }

    @Test
    fun editsMatchedReadoutCategoryWithoutChangingCompetitorCategory() {
        val shortCategory = category("cat-short", "M21")
        val longCategory = category("cat-long", "M40")
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            categories = listOf(
                categoryData("cat-short", "M21", controlSiCodes = listOf(31)),
                categoryData("cat-long", "M40", controlSiCodes = listOf(31, 33))
            ),
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 1111,
                    category = shortCategory,
                    readoutData = readout("result-1", "comp-1", 1111)
                )
            )
        )

        val updated = EventProjectEditor.updateReadoutEdit(
            projectFile = original,
            resultId = "result-1",
            startSeconds = "10:00",
            finishSeconds = "30:00",
            controlPunchesText = "31\n33",
            resultStatus = ResultStatus.OK,
            categoryId = longCategory.id,
            updateCompetitorCategory = false,
            punchIdFactory = { index, type -> "edit-$index-${type.name}" }
        )

        val competitorData = updated.raceData.competitorData.single()
        val readout = competitorData.readoutData!!
        assertEquals(shortCategory.id, competitorData.competitorCategory.competitor.categoryId)
        assertEquals(shortCategory, competitorData.competitorCategory.category)
        assertEquals(longCategory.id, readout.result.categoryId)
        assertEquals(2, readout.result.points)
    }

    @Test
    fun editsPracticeReadoutTimesRelativeToCardStartPunch() {
        val category = category("cat-1", "M21")
        val baseReadout = readout("result-1", "comp-1", 1111).let { readoutData ->
            readoutData.copy(
                result = readoutData.result.copy(
                    startTimeSeconds = 40_000,
                    finishTimeSeconds = 41_000,
                    runTimeSeconds = 1_000
                )
            )
        }
        val original = projectFile(
            raceLevel = RaceLevel.PRACTICE,
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31))),
            controls = listOf(
                EventControl("control-31", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1")
            ),
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 1111,
                    category = category,
                    readoutData = baseReadout
                )
            )
        )

        val updated = EventProjectEditor.updateReadoutEdit(
            projectFile = original,
            resultId = "result-1",
            startSeconds = "00:00",
            finishSeconds = "12:00",
            controlPunchesText = "Fox 1 @ 04:00",
            resultStatus = ResultStatus.OK,
            categoryId = category.id,
            updateCompetitorCategory = false,
            punchIdFactory = { index, type -> "practice-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals(40_000, readout.result.startTimeSeconds)
        assertEquals(40_720, readout.result.finishTimeSeconds)
        assertEquals(720, readout.result.runTimeSeconds)
        assertEquals(listOf(40_000L, 40_240L, 40_720L), readout.punches.map { it.punch.siTimeSeconds })
        assertEquals(listOf(0L, 240L, 480L), readout.punches.map { it.punch.splitSeconds })
    }

    @Test
    fun editingTimingAndStatusClearsReadoutDisplayError() {
        val invalidReadout = readout("result-1", "comp-1", 1111).let { readoutData ->
            readoutData.copy(
                result = readoutData.result.copy(
                    startTimeSeconds = 43_200,
                    finishTimeSeconds = 37_800,
                    runTimeSeconds = 0,
                    resultStatus = ResultStatus.ERROR
                )
            )
        }
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 1111,
                    readoutData = invalidReadout
                )
            )
        )

        val updated = EventProjectEditor.updateReadoutEdit(
            projectFile = original,
            resultId = "result-1",
            startSeconds = "00:00",
            finishSeconds = "30:00",
            controlPunchesText = "",
            resultStatus = ResultStatus.OK,
            categoryId = null,
            updateCompetitorCategory = false,
            punchIdFactory = { index, type -> "edit-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        val row = EventReadoutDetails.from(updated.raceData).single()
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(1_800, readout.result.runTimeSeconds)
        assertEquals("00:30:00", row.runTimeText)
        assertEquals("0", row.pointsText)
        assertEquals(false, row.hasWarning)
    }

    @Test
    fun editsReadoutTimesThatCrossMidnight() {
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 1111,
                    readoutData = readout("result-1", "comp-1", 1111)
                )
            )
        ).let { file ->
            file.copy(
                raceData = file.raceData.copy(
                    race = file.raceData.race.copy(startDateTimeIso = "2026-05-31T23:30")
                )
            )
        }

        val updated = EventProjectEditor.updateReadoutEdit(
            projectFile = original,
            resultId = "result-1",
            startSeconds = "45:00",
            finishSeconds = "1:00:00",
            controlPunchesText = "",
            resultStatus = ResultStatus.OK,
            categoryId = null,
            updateCompetitorCategory = false,
            punchIdFactory = { index, type -> "midnight-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        val row = EventReadoutDetails.from(updated.raceData).single()
        assertEquals(87_300, readout.result.startTimeSeconds)
        assertEquals(88_200, readout.result.finishTimeSeconds)
        assertEquals(900, readout.result.runTimeSeconds)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals("00:15:00", row.runTimeText)
        assertEquals(false, row.hasWarning)
    }

    @Test
    fun marksMatchedReadoutsSent() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111)),
                competitorData("comp-2", "Bob", "Runner", readoutData = readout("result-2", "comp-2", 2222))
            )
        )

        val updated = EventProjectEditor.markReadoutsSent(original, setOf("result-1"))

        assertEquals(true, updated.raceData.competitorData[0].readoutData!!.result.sent)
        assertEquals(false, updated.raceData.competitorData[1].readoutData!!.result.sent)
    }

    @Test
    fun ignoresEmptyMarkReadoutsSentRequest() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111))
            )
        )

        assertEquals(original, EventProjectEditor.markReadoutsSent(original, emptySet()))
    }

    @Test
    fun rejectsUnmatchedReadoutMarkSent() {
        val original = projectFile(
            unmatchedReadouts = listOf(readout("result-1", null, 1111))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.markReadoutsSent(original, setOf("result-1"))
        }
    }

    @Test
    fun rejectsUnknownReadoutMarkSent() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.markReadoutsSent(projectFile(), setOf("missing"))
        }
    }

    @Test
    fun marksCompetitorDidNotStartWithStatusOnlyReadout() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 123456)
            )
        )

        val updated = EventProjectEditor.markCompetitorDidNotStart(
            projectFile = original,
            competitorId = "comp-1",
            resultId = "dns-result",
            readoutDateTimeIso = "2026-05-31T12:00"
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals("dns-result", readout.result.id)
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(123456, readout.result.siNumber)
        assertEquals(ResultStatus.DID_NOT_START, readout.result.resultStatus)
        assertEquals(false, readout.result.automaticStatus)
        assertEquals(true, readout.result.modified)
        assertEquals(false, readout.result.sent)
        assertEquals(null, readout.result.startTimeSeconds)
        assertEquals(null, readout.result.finishTimeSeconds)
        assertEquals(0L, readout.result.runTimeSeconds)
        assertEquals(emptyList(), readout.punches)
    }

    @Test
    fun rejectsCompetitorDidNotStartWhenCompetitorAlreadyHasReadout() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("result-1", "comp-1", 1111))
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.markCompetitorDidNotStart(original, "comp-1", "dns-result", "2026-05-31T12:00")
        }
    }

    @Test
    fun rejectsUnknownCompetitorDidNotStart() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.markCompetitorDidNotStart(projectFile(), "missing", "dns-result", "2026-05-31T12:00")
        }
    }

    @Test
    fun addsManualReadoutForMatchedCompetitorAndEvaluatesCourse() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32, 33))),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 123456, category = category)
            )
        )

        val updated = EventProjectEditor.addManualReadout(
            projectFile = original,
            resultId = "result-1",
            competitorId = "comp-1",
            siNumber = "",
            startSeconds = "600",
            finishSeconds = "1800",
            controlCodes = "31 32 33",
            resultStatus = ResultStatus.OK,
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(123456, readout.result.siNumber)
        assertEquals(600, readout.result.startTimeSeconds)
        assertEquals(1800, readout.result.finishTimeSeconds)
        assertEquals(1200, readout.result.runTimeSeconds)
        assertEquals(3, readout.result.points)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(false, readout.result.automaticStatus)
        assertEquals(true, readout.result.modified)
        assertEquals(false, readout.result.sent)
        assertEquals(listOf(SIRecordType.START, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.FINISH), readout.punches.map { it.punch.punchType })
        assertEquals(listOf(0, 31, 32, 33, 0), readout.punches.map { it.punch.siCode })
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun addsManualReadoutAsUnmatchedWhenNoCompetitorIsSelected() {
        val original = projectFile()

        val updated = EventProjectEditor.addManualReadout(
            projectFile = original,
            resultId = "result-1",
            competitorId = null,
            siNumber = "123456",
            startSeconds = "",
            finishSeconds = "",
            controlCodes = "31, 32",
            resultStatus = ResultStatus.NO_RANKING,
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.unmatchedReadoutData.single()
        assertEquals(null, readout.result.competitorId)
        assertEquals(123456, readout.result.siNumber)
        assertEquals(null, readout.result.startTimeSeconds)
        assertEquals(null, readout.result.finishTimeSeconds)
        assertEquals(ResultStatus.NO_RANKING, readout.result.resultStatus)
        assertEquals(2, readout.punches.size)
    }

    @Test
    fun addsAndUndoesManualDidNotStartReadoutThroughReadoutWorkflow() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 123456)
            )
        )

        val withDns = EventProjectEditor.addManualReadout(
            projectFile = original,
            resultId = "dns-result",
            competitorId = "comp-1",
            siNumber = "",
            startSeconds = "",
            finishSeconds = "",
            controlCodes = "",
            resultStatus = ResultStatus.DID_NOT_START,
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )
        val dnsReadout = withDns.raceData.competitorData.single().readoutData!!
        assertEquals("dns-result", dnsReadout.result.id)
        assertEquals("comp-1", dnsReadout.result.competitorId)
        assertEquals(123456, dnsReadout.result.siNumber)
        assertEquals(ResultStatus.DID_NOT_START, dnsReadout.result.resultStatus)
        assertEquals(false, dnsReadout.result.automaticStatus)
        assertEquals(emptyList(), dnsReadout.punches)

        val statusChanged = EventProjectEditor.updateReadoutManualStatus(withDns, "dns-result", ResultStatus.NO_RANKING)
        assertEquals(
            ResultStatus.NO_RANKING,
            statusChanged.raceData.competitorData.single().readoutData!!.result.resultStatus
        )

        val removed = EventProjectEditor.removeReadout(statusChanged, "dns-result")
        assertEquals(null, removed.raceData.competitorData.single().readoutData)
    }

    @Test
    fun addsDownloadedSportIdentReadoutForMatchedCompetitorAndEvaluatesCourse() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32, 33))),
            aliases = listOf(alias("alias-31", 31, "F1")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 2005010, category = category)
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(
                siNumber = 2005010,
                checkSeconds = 36_800,
                startSeconds = 36_900,
                finishSeconds = 38_100,
                controlCodes = listOf(31, 32, 33),
                firstControlSeconds = 37_100
            ),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(2005010, readout.result.siNumber)
        assertEquals(SportIdentProtocol.SI_CARD8_9_SIAC, readout.result.cardType)
        assertEquals(36_800, readout.result.checkTimeSeconds)
        assertEquals(36_900, readout.result.startTimeSeconds)
        assertEquals(38_100, readout.result.finishTimeSeconds)
        assertEquals(1_200, readout.result.runTimeSeconds)
        assertEquals(3, readout.result.points)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(true, readout.result.automaticStatus)
        assertEquals(false, readout.result.modified)
        assertEquals(listOf(SIRecordType.START, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.CONTROL, SIRecordType.FINISH), readout.punches.map { it.punch.punchType })
        assertEquals(listOf(0, 31, 32, 33, 0), readout.punches.map { it.punch.siCode })
        assertEquals(listOf(0L, 200L, 60L, 60L, 880L), readout.punches.map { it.punch.splitSeconds })
        assertEquals("F1", readout.punches[1].alias?.name)
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun downloadedSportIdentReadoutWithFinishBeforeStartIsStoredAsTimingError() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            raceType = RaceType.SPRINT,
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    controlSiCodes = listOf(161, 162, 163, 164, 165, 137, 171, 172, 173, 174, 175, 136)
                )
            ),
            competitors = listOf(
                competitorData("comp-1", "Charles", "Scharlau", siNumber = 2005010, category = category)
            )
        )
        val motoReadout = SportIdentCardReadout(
            siNumber = 2005010,
            series = 2,
            checkTime = SportIdentTime(14, 2, 18, 4, 0),
            startTime = SportIdentTime(14, 2, 23, 4, 0),
            finishTime = SportIdentTime(0, 2, 11, 0, 0),
            punches = listOf(
                SportIdentCardPunch(171, SportIdentTime(14, 4, 50, 4, 0)),
                SportIdentCardPunch(162, SportIdentTime(14, 4, 51, 4, 0)),
                SportIdentCardPunch(165, SportIdentTime(14, 5, 6, 4, 0)),
                SportIdentCardPunch(161, SportIdentTime(14, 5, 5, 4, 0)),
                SportIdentCardPunch(137, SportIdentTime(14, 7, 17, 4, 0)),
                SportIdentCardPunch(172, SportIdentTime(14, 7, 36, 4, 0)),
                SportIdentCardPunch(173, SportIdentTime(14, 7, 37, 4, 0)),
                SportIdentCardPunch(172, SportIdentTime(14, 7, 54, 4, 0)),
                SportIdentCardPunch(136, SportIdentTime(14, 7, 55, 4, 0))
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = motoReadout,
            readoutDateTimeIso = "2026-06-25T16:52:43",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals(2005010, readout.result.siNumber)
        assertEquals(SportIdentTime(14, 2, 23, 4, 0).getSeconds(), readout.result.startTimeSeconds)
        assertEquals(SportIdentTime(0, 2, 11, 0, 0).getSeconds(), readout.result.finishTimeSeconds)
        assertEquals(0L, readout.result.runTimeSeconds)
        assertEquals(ResultStatus.ERROR, readout.result.resultStatus)
    }

    @Test
    fun recalculatingDownloadedReadoutWithFinishBeforeStartKeepsTimingError() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            raceType = RaceType.SPRINT,
            categories = listOf(
                categoryData(
                    "cat-1",
                    "M21",
                    controlSiCodes = listOf(161, 162, 163, 164, 165, 137, 171, 172, 173, 174, 175, 136)
                )
            ),
            competitors = listOf(
                competitorData("comp-1", "Charles", "Scharlau", siNumber = 2005010, category = category)
            )
        )
        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = SportIdentCardReadout(
                siNumber = 2005010,
                series = 2,
                checkTime = SportIdentTime(14, 2, 18, 4, 0),
                startTime = SportIdentTime(14, 2, 23, 4, 0),
                finishTime = SportIdentTime(0, 2, 11, 0, 0),
                punches = listOf(
                    SportIdentCardPunch(171, SportIdentTime(14, 4, 50, 4, 0)),
                    SportIdentCardPunch(162, SportIdentTime(14, 4, 51, 4, 0)),
                    SportIdentCardPunch(165, SportIdentTime(14, 5, 6, 4, 0)),
                    SportIdentCardPunch(161, SportIdentTime(14, 5, 5, 4, 0)),
                    SportIdentCardPunch(137, SportIdentTime(14, 7, 17, 4, 0)),
                    SportIdentCardPunch(172, SportIdentTime(14, 7, 36, 4, 0)),
                    SportIdentCardPunch(173, SportIdentTime(14, 7, 37, 4, 0)),
                    SportIdentCardPunch(172, SportIdentTime(14, 7, 54, 4, 0)),
                    SportIdentCardPunch(136, SportIdentTime(14, 7, 55, 4, 0))
                )
            ),
            readoutDateTimeIso = "2026-06-25T16:52:43",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val recalculated = EventProjectEditor.recalculateResults(updated)
        val readout = recalculated.projectFile.raceData.competitorData.single().readoutData!!

        assertEquals(1, recalculated.recalculatedCount)
        assertEquals(0L, readout.result.runTimeSeconds)
        assertEquals(ResultStatus.ERROR, readout.result.resultStatus)
    }

    @Test
    fun downloadedSportIdentReadoutWithNonSequentialControlTimeKeepsResultButFlagsPunch() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32))),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 2005010, category = category)
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = SportIdentCardReadout(
                siNumber = 2005010,
                series = 2,
                checkTime = null,
                startTime = SportIdentTime(10, 0, 0, 4, 0),
                finishTime = SportIdentTime(10, 30, 0, 4, 0),
                punches = listOf(
                    SportIdentCardPunch(31, SportIdentTime(10, 12, 0, 4, 0)),
                    SportIdentCardPunch(32, SportIdentTime(10, 11, 59, 4, 0))
                )
            ),
            readoutDateTimeIso = "2026-06-25T16:52:43",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(2, readout.result.points)
        assertEquals(30L * 60L, readout.result.runTimeSeconds)
        assertEquals(
            listOf(PunchStatus.VALID, PunchStatus.INVALID),
            readout.punches
                .filter { it.punch.punchType == SIRecordType.CONTROL }
                .map { it.punch.punchStatus }
        )
    }

    @Test
    fun downloadedReadoutsAssignPlacesWithinEachCategory() {
        val categoryAData = categoryData("cat-a", "M21", order = 1, controlSiCodes = listOf(31, 32))
        val categoryBData = categoryData("cat-b", "M50", order = 2, controlSiCodes = listOf(31))
        val original = projectFile(
            categories = listOf(categoryAData, categoryBData),
            competitors = listOf(
                competitorData("comp-a2", "Ann", "Second", siNumber = 2005002, category = categoryAData.category),
                competitorData("comp-b1", "Ben", "Winner", siNumber = 2005003, category = categoryBData.category),
                competitorData("comp-a1", "Al", "Winner", siNumber = 2005001, category = categoryAData.category)
            )
        )

        val withASecond = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-a2",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005002, controlCodes = listOf(31)),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "a2-punch-$index-${type.name}" }
        )
        val withBFirst = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = withASecond,
            resultId = "result-b1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005003, controlCodes = listOf(31)),
            readoutDateTimeIso = "2026-05-31T12:01",
            punchIdFactory = { index, type -> "b1-punch-$index-${type.name}" }
        )
        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = withBFirst,
            resultId = "result-a1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005001, controlCodes = listOf(31, 32)),
            readoutDateTimeIso = "2026-05-31T12:02",
            punchIdFactory = { index, type -> "a1-punch-$index-${type.name}" }
        )

        assertEquals(
            listOf("comp-a2", "comp-b1", "comp-a1"),
            updated.raceData.competitorData.map { it.competitorCategory.competitor.id }
        )
        assertEquals(
            listOf(2, 1, 1),
            updated.raceData.competitorData.map { it.readoutData!!.result.place }
        )
    }

    @Test
    fun recalculatesDownloadedResultsAfterCourseChangesAndMarksChangedResultsUnsent() {
        val originalCategory = categoryData("cat-1", "M21", controlSiCodes = listOf(31, 32))
        val original = projectFile(
            raceType = RaceType.ORIENTEERING,
            categories = listOf(originalCategory),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 2005010, category = originalCategory.category)
            )
        )
        val withReadout = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010, controlCodes = listOf(31, 32)),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )
        val sent = EventProjectEditor.markReadoutsSent(withReadout, setOf("result-1"))
        val changedCourse = sent.copy(
            raceData = sent.raceData.copy(
                categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31, 33)))
            )
        )

        val outcome = EventProjectEditor.recalculateResults(changedCourse)
        val recalculated = outcome.projectFile.raceData.competitorData.single().readoutData!!

        assertEquals(1, outcome.recalculatedCount)
        assertEquals(1, outcome.changedCount)
        assertEquals(0, outcome.skippedStatusOnlyCount)
        assertEquals(ResultStatus.MISPUNCHED, recalculated.result.resultStatus)
        assertEquals(1_200, recalculated.result.runTimeSeconds)
        assertEquals(false, recalculated.result.sent)
        assertEquals(1, recalculated.result.place)
        assertEquals(PunchStatus.VALID, recalculated.punches.first().punch.punchStatus)
        assertEquals(PunchStatus.VALID, recalculated.punches.last().punch.punchStatus)
    }

    @Test
    fun recalculationPreservesStatusOnlyResults() {
        val category = category("cat-1", "M21")
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21", controlSiCodes = listOf(31))),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", siNumber = 2005010, category = category)
            )
        )
        val withDns = EventProjectEditor.markCompetitorDidNotStart(
            projectFile = original,
            competitorId = "comp-1",
            resultId = "dns-result",
            readoutDateTimeIso = "2026-05-31T12:00"
        )

        val outcome = EventProjectEditor.recalculateResults(withDns)
        val readout = outcome.projectFile.raceData.competitorData.single().readoutData!!

        assertEquals(0, outcome.recalculatedCount)
        assertEquals(0, outcome.changedCount)
        assertEquals(1, outcome.skippedStatusOnlyCount)
        assertEquals(ResultStatus.DID_NOT_START, readout.result.resultStatus)
        assertEquals(true, readout.result.modified)
    }

    @Test
    fun addsDownloadedSportIdentReadoutAsUnmatchedWhenNoCompetitorMatches() {
        val original = projectFile(raceLevel = RaceLevel.REGIONAL)

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val readout = updated.raceData.unmatchedReadoutData.single()
        assertEquals(null, readout.result.competitorId)
        assertEquals(2005010, readout.result.siNumber)
        assertEquals(ResultStatus.NO_RANKING, readout.result.resultStatus)
        assertEquals(emptyList(), updated.raceData.competitorData.mapNotNull { it.readoutData })
    }

    @Test
    fun addsDownloadedPracticeReadoutByCreatingCompetitorWhenNoCompetitorMatches() {
        val original = projectFile(raceLevel = RaceLevel.PRACTICE)

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(
                siNumber = 2005010,
                controlCodes = emptyList(),
                cardHolder = SportIdentCardHolder(firstName = "Alice", lastName = "Runner", club = "OK Test")
            ),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val competitorData = updated.raceData.competitorData.single()
        val competitor = competitorData.competitorCategory.competitor
        val readout = competitorData.readoutData!!
        assertEquals("practice-competitor-result-1", competitor.id)
        assertEquals("Alice", competitor.firstName)
        assertEquals("Runner", competitor.lastName)
        assertEquals("OK Test", competitor.club)
        assertEquals(2005010, competitor.siNumber)
        assertEquals(null, competitor.categoryId)
        assertEquals("practice-competitor-result-1", readout.result.competitorId)
        assertEquals("Runner Alice", readout.result.cardName)
        assertEquals(2005010, readout.result.siNumber)
        assertEquals(ResultStatus.NO_RANKING, readout.result.resultStatus)
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun addsDownloadedPracticeReadoutWithSiPlaceholderWhenCardHasNoName() {
        val original = projectFile(raceLevel = RaceLevel.PRACTICE)

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "result-1",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010, cardHolder = null),
            readoutDateTimeIso = "2026-05-31T12:00",
            punchIdFactory = { index, type -> "punch-$index-${type.name}" }
        )

        val competitor = updated.raceData.competitorData.single().competitorCategory.competitor
        assertEquals("SI 2005010", competitor.firstName)
        assertEquals("Practice", competitor.lastName)
        assertEquals(null, updated.raceData.competitorData.single().readoutData!!.result.cardName)
    }

    @Test
    fun rejectsDuplicateDownloadedSportIdentReadout() {
        val original = projectFile(
            unmatchedReadouts = listOf(readout("existing", null, 2005010))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addDownloadedSportIdentReadout(
                projectFile = original,
                resultId = "result-1",
                cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
                readout = sportIdentReadout(siNumber = 2005010),
                readoutDateTimeIso = "2026-05-31T12:00",
                punchIdFactory = { index, type -> "punch-$index-${type.name}" }
            )
        }
    }

    @Test
    fun replacesDuplicateDownloadedSportIdentReadout() {
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            unmatchedReadouts = listOf(readout("existing", null, 2005010))
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "replacement",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010),
            readoutDateTimeIso = "2026-05-31T12:00",
            duplicatePolicy = EventReadoutDuplicatePolicy.Replace,
            punchIdFactory = { index, type -> "replacement-punch-$index-${type.name}" }
        )

        val readout = updated.raceData.unmatchedReadoutData.single()
        assertEquals("replacement", readout.result.id)
        assertEquals(2005010, readout.result.siNumber)
    }

    @Test
    fun replacesDuplicateMatchedDownloadedSportIdentReadout() {
        val original = projectFile(
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 2005010,
                    readoutData = readout("existing", "comp-1", 2005010)
                )
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "replacement",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010),
            readoutDateTimeIso = "2026-05-31T12:00",
            duplicatePolicy = EventReadoutDuplicatePolicy.Replace,
            punchIdFactory = { index, type -> "replacement-punch-$index-${type.name}" }
        )

        val readout = updated.raceData.competitorData.single().readoutData!!
        assertEquals("replacement", readout.result.id)
        assertEquals("comp-1", readout.result.competitorId)
        assertEquals(2005010, readout.result.siNumber)
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun createsNewDuplicateDownloadedSportIdentReadoutAsUnmatchedWithSiNumber() {
        val original = projectFile(
            raceLevel = RaceLevel.REGIONAL,
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 2005010,
                    readoutData = readout("existing", "comp-1", 2005010)
                )
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "new-duplicate",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010),
            readoutDateTimeIso = "2026-05-31T12:00",
            duplicatePolicy = EventReadoutDuplicatePolicy.CreateNew,
            punchIdFactory = { index, type -> "new-punch-$index-${type.name}" }
        )

        assertEquals("existing", updated.raceData.competitorData.single().readoutData!!.result.id)
        val duplicate = updated.raceData.unmatchedReadoutData.single()
        assertEquals("new-duplicate", duplicate.result.id)
        assertEquals(null, duplicate.result.competitorId)
        assertEquals(2005010, duplicate.result.siNumber)
    }

    @Test
    fun createsNewDuplicatePracticeReadoutAsNumberedCompetitorResult() {
        val original = projectFile(
            raceLevel = RaceLevel.PRACTICE,
            competitors = listOf(
                competitorData(
                    "comp-1",
                    "Alice",
                    "Runner",
                    siNumber = 2005010,
                    readoutData = readout("existing", "comp-1", 2005010)
                )
            )
        )

        val updated = EventProjectEditor.addDownloadedSportIdentReadout(
            projectFile = original,
            resultId = "new-duplicate",
            cardType = SportIdentProtocol.SI_CARD8_9_SIAC,
            readout = sportIdentReadout(siNumber = 2005010),
            readoutDateTimeIso = "2026-05-31T12:00",
            duplicatePolicy = EventReadoutDuplicatePolicy.CreateNew,
            punchIdFactory = { index, type -> "new-punch-$index-${type.name}" }
        )

        val competitors = updated.raceData.competitorData.map { it.competitorCategory.competitor }
        assertEquals(listOf("comp-1", "practice-competitor-new-duplicate"), competitors.map { it.id })
        assertEquals(listOf("Runner", "Runner #2"), competitors.map { it.lastName })
        assertEquals(listOf(2005010, 2005010), competitors.map { it.siNumber })
        assertEquals(listOf("existing", "new-duplicate"), updated.raceData.competitorData.map { it.readoutData!!.result.id })
        assertEquals(
            listOf("comp-1", "practice-competitor-new-duplicate"),
            updated.raceData.competitorData.map { it.readoutData!!.result.competitorId }
        )
        assertEquals(emptyList(), updated.raceData.unmatchedReadoutData)
    }

    @Test
    fun rejectsInvalidManualReadoutInputs() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", readoutData = readout("existing", "comp-1", 123456))
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(original, "", null, "123456", "", "", "", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(original, "new", "missing", "123456", "", "", "", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(original, "new", "comp-1", "123456", "", "", "", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(projectFile(), "new", null, "999", "", "", "", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(projectFile(), "new", null, "123456", "900", "600", "", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addManualReadout(projectFile(), "new", null, "123456", "", "", "999", ResultStatus.OK, "now") { index, type -> "$index-$type" }
        }
    }

    @Test
    fun updatesAliasUsingSharedValidationRules() {
        val original = projectFile(
            aliases = listOf(alias("alias-1", 31, "F1"), alias("alias-2", 32, "F2"))
        )

        val updated = EventProjectEditor.updateAlias(original, "alias-2", " 33 ", " F3 ")

        assertEquals(31, updated.raceData.aliases[0].siCode)
        assertEquals("F1", updated.raceData.aliases[0].name)
        assertEquals(33, updated.raceData.aliases[1].siCode)
        assertEquals("F3", updated.raceData.aliases[1].name)
    }

    @Test
    fun rejectsInvalidAliasUpdates() {
        val original = projectFile(
            aliases = listOf(alias("alias-1", 31, "F1"), alias("alias-2", 32, "F2"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateAlias(original, "alias-2", "31", "F3")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateAlias(original, "alias-2", "33", "TOOLONG")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateAlias(original, "missing", "33", "F3")
        }
    }

    @Test
    fun addsAliasUsingSharedValidationRules() {
        val original = projectFile(
            aliases = listOf(alias("alias-1", 31, "F1"))
        )

        val updated = EventProjectEditor.addAlias(original, "alias-2", " 32 ", " F2 ")

        assertEquals(2, updated.raceData.aliases.size)
        assertEquals("race", updated.raceData.aliases[1].raceId)
        assertEquals(32, updated.raceData.aliases[1].siCode)
        assertEquals("F2", updated.raceData.aliases[1].name)
    }

    @Test
    fun rejectsInvalidAliasAdds() {
        val original = projectFile(
            aliases = listOf(alias("alias-1", 31, "F1"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addAlias(original, "", "32", "F2")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addAlias(original, "alias-1", "32", "F2")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addAlias(original, "alias-2", "31", "F2")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.addAlias(original, "alias-2", "32", "F1")
        }
    }

    @Test
    fun removesAlias() {
        val original = projectFile(
            aliases = listOf(alias("alias-1", 31, "F1"), alias("alias-2", 32, "F2"))
        )

        val updated = EventProjectEditor.removeAlias(original, "alias-1")

        assertEquals(listOf("alias-2"), updated.raceData.aliases.map { it.id })
    }

    @Test
    fun rejectsUnknownAliasRemove() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.removeAlias(projectFile(), "missing")
        }
    }

    @Test
    fun drawsStartListByCategoryOrderAndRotatesClubs() {
        val m21 = category("cat-m21", "M21", order = 1)
        val w21 = category("cat-w21", "W21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order), categoryData(w21.id, w21.name, order = w21.order)),
            competitors = listOf(
                competitorData("m-a1", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m-a2", "Aaron", "Alpha", startNumber = 2, category = m21, club = "A"),
                competitorData("m-b1", "Bob", "Bravo", startNumber = 3, category = m21, club = "B"),
                competitorData("w-c1", "Cara", "Charlie", startNumber = 4, category = w21, club = "C")
            )
        )

        val drawn = EventProjectEditor.drawStartList(original, "02:00")

        assertEquals(0L, drawn.startTimeFor("w-c1"))
        assertEquals(120L, drawn.startTimeFor("m-a1"))
        assertEquals(240L, drawn.startTimeFor("m-b1"))
        assertEquals(360L, drawn.startTimeFor("m-a2"))
    }

    @Test
    fun drawStartListAvoidsAvoidableSameClubAdjacencyInUnevenCategory() {
        val m21 = category("cat-m21", "M21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("a1", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("a2", "Aaron", "Alpha", startNumber = 2, category = m21, club = "A"),
                competitorData("a3", "Ava", "Alpha", startNumber = 3, category = m21, club = "A"),
                competitorData("b1", "Bob", "Bravo", startNumber = 4, category = m21, club = "B"),
                competitorData("c1", "Cara", "Charlie", startNumber = 5, category = m21, club = "C")
            )
        )

        val drawn = EventProjectEditor.drawStartList(original, "01:00")

        assertEquals(0L, drawn.startTimeFor("a1"))
        assertEquals(60L, drawn.startTimeFor("b1"))
        assertEquals(120L, drawn.startTimeFor("a2"))
        assertEquals(180L, drawn.startTimeFor("c1"))
        assertEquals(240L, drawn.startTimeFor("a3"))
    }

    @Test
    fun drawStartListAvoidsSameClubAcrossCategoryBoundaryWhenPossible() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order)
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m40-a", "Aaron", "Alpha", startNumber = 2, category = m40, club = "A"),
                competitorData("m40-b", "Bob", "Bravo", startNumber = 3, category = m40, club = "B")
            )
        )

        val drawn = EventProjectEditor.drawStartList(original, "01:00")

        assertEquals(0L, drawn.startTimeFor("m21-a"))
        assertEquals(60L, drawn.startTimeFor("m40-b"))
        assertEquals(120L, drawn.startTimeFor("m40-a"))
    }

    @Test
    fun drawStartListCanIgnoreClubs() {
        val m21 = category("cat-m21", "M21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("a1", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("a2", "Aaron", "Alpha", startNumber = 2, category = m21, club = "A"),
                competitorData("b1", "Bob", "Bravo", startNumber = 3, category = m21, club = "B")
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "01:00",
            StartDrawOptions(clubHandling = StartDrawClubHandling.IGNORE)
        )

        assertEquals(0L, drawn.startTimeFor("a1"))
        assertEquals(60L, drawn.startTimeFor("a2"))
        assertEquals(120L, drawn.startTimeFor("b1"))
    }

    @Test
    fun drawStartListSupportsMultipleStartersPerStartTimeWithoutSameCategoryWhenPossible() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val m60 = category("cat-m60", "M60", order = 2)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order),
                categoryData(m60.id, m60.name, order = m60.order)
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m21-b", "Aaron", "Alpha", startNumber = 2, category = m21, club = "B"),
                competitorData("m40-a", "Bob", "Bravo", startNumber = 3, category = m40, club = "C"),
                competitorData("m60-a", "Cara", "Charlie", startNumber = 4, category = m60, club = "D")
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "01:00",
            StartDrawOptions(startersPerStartTime = 2)
        )

        assertEquals(0L, drawn.startTimeFor("m21-a"))
        assertEquals(0L, drawn.startTimeFor("m40-a"))
        assertEquals(60L, drawn.startTimeFor("m21-b"))
        assertEquals(60L, drawn.startTimeFor("m60-a"))
    }

    @Test
    fun drawStartListAvoidsSameFirstFoxForSimilarSpeedCategoriesWhenPossible() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val m60 = category("cat-m60", "M60", order = 2)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order, controlSiCodes = listOf(31, 32)),
                categoryData(m40.id, m40.name, order = m40.order, controlSiCodes = listOf(31, 33)),
                categoryData(m60.id, m60.name, order = m60.order, controlSiCodes = listOf(31, 34))
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m40-a", "Bob", "Bravo", startNumber = 2, category = m40, club = "B"),
                competitorData("m60-a", "Cara", "Charlie", startNumber = 3, category = m60, club = "C")
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "01:00",
            StartDrawOptions(
                startersPerStartTime = 2,
                idealFirstFoxByCategoryId = mapOf(
                    m21.id to 31,
                    m40.id to 31
                )
            )
        )

        assertEquals(0L, drawn.startTimeFor("m21-a"))
        assertEquals(0L, drawn.startTimeFor("m60-a"))
        assertEquals(60L, drawn.startTimeFor("m40-a"))
    }

    @Test
    fun drawStartListAllowsPartiallyFilledStartTimesToAvoidFirstFoxSpeedConflict() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order, controlSiCodes = listOf(31, 32)),
                categoryData(m40.id, m40.name, order = m40.order, controlSiCodes = listOf(31, 33))
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m40-a", "Bob", "Bravo", startNumber = 2, category = m40, club = "B")
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "01:00",
            StartDrawOptions(
                startersPerStartTime = 2,
                idealFirstFoxByCategoryId = mapOf(
                    m21.id to 31,
                    m40.id to 31
                )
            )
        )

        assertEquals(0L, drawn.startTimeFor("m21-a"))
        assertEquals(60L, drawn.startTimeFor("m40-a"))
    }

    @Test
    fun drawStartListPersistsGeneratorSettings() {
        val m21 = category("cat-m21", "M21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("a1", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("a2", "Aaron", "Alpha", startNumber = 2, category = m21, club = "A")
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "05:00",
            StartDrawOptions(
                clubHandling = StartDrawClubHandling.IGNORE,
                startersPerStartTime = 2,
                seed = "test-seed",
                startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS,
                idealFirstFoxByCategoryId = mapOf(m21.id to 31)
            )
        )

        val settings = drawn.raceData.startDrawSettings!!
        assertEquals(300L, settings.intervalSeconds)
        assertEquals(StartDrawClubHandling.IGNORE, settings.options.clubHandling)
        assertEquals(2, settings.options.startersPerStartTime)
        assertEquals("test-seed", settings.options.seed)
        assertEquals(StartDrawStartGroupMode.PREFERRED_THIRDS, settings.options.startGroupMode)
        assertEquals(emptyMap(), settings.options.idealFirstFoxByCategoryId)
    }

    @Test
    fun updateStartDrawSettingsPersistsWithoutDrawingStarts() {
        val updated = EventProjectEditor.updateStartDrawSettings(
            projectFile(),
            "05:00",
            StartDrawOptions(
                clubHandling = StartDrawClubHandling.IGNORE,
                startersPerStartTime = 3,
                seed = "settings-only",
                startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS
            )
        )

        val details = EventStartListDetails.from(updated.raceData)

        assertEquals(300L, details.settings.intervalSeconds)
        assertEquals(StartDrawClubHandling.IGNORE, details.settings.options.clubHandling)
        assertEquals(3, details.settings.options.startersPerStartTime)
        assertEquals("settings-only", details.settings.options.seed)
        assertEquals(StartDrawStartGroupMode.PREFERRED_THIRDS, details.settings.options.startGroupMode)
    }

    @Test
    fun updateStartDrawSeriesOptimizationLockPersistsAcrossSettingsChanges() {
        val locked = EventProjectEditor.updateStartDrawSeriesOptimizationLock(projectFile(), true)
        val updatedSettings = EventProjectEditor.updateStartDrawSettings(
            locked,
            "03:00",
            StartDrawOptions(clubHandling = StartDrawClubHandling.IGNORE)
        )

        assertEquals(true, locked.raceData.effectiveStartDrawSettings().lockedForSeriesOptimization)
        assertEquals(true, updatedSettings.raceData.effectiveStartDrawSettings().lockedForSeriesOptimization)
        assertEquals(
            false,
            EventProjectEditor.updateStartDrawSeriesOptimizationLock(updatedSettings, false)
                .raceData
                .effectiveStartDrawSettings()
                .lockedForSeriesOptimization
        )
    }

    @Test
    fun nationalStartListDefaultsOnlyChangeNationalProfileFields() {
        val original = StartDrawOptions(
            clubHandling = StartDrawClubHandling.AVOID_BACK_TO_BACK,
            startersPerStartTime = 1,
            seed = "review-seed",
            startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS,
            idealFirstFoxByCategoryId = mapOf("cat-m21" to 31)
        )

        val nationalDefaults = original.withNationalEventDefaults()

        assertFalse(original.hasNationalEventDefaults())
        assertTrue(nationalDefaults.hasNationalEventDefaults())
        assertEquals(StartDrawClubHandling.IGNORE, nationalDefaults.clubHandling)
        assertEquals(2, nationalDefaults.startersPerStartTime)
        assertEquals(StartDrawStartGroupMode.DISABLED, nationalDefaults.startGroupMode)
        assertEquals("review-seed", nationalDefaults.seed)
        assertEquals(mapOf("cat-m21" to 31), nationalDefaults.idealFirstFoxByCategoryId)
    }

    @Test
    fun eventStartListGenerationDoesNotUseSeriesBalancedThirdsAsEventRule() {
        val seriesOptions = StartDrawOptions(
            clubHandling = StartDrawClubHandling.IGNORE,
            startersPerStartTime = 2,
            seed = "series-seed",
            startGroupMode = StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS
        )
        val preferredOptions = seriesOptions.copy(startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS)

        val eventOptions = seriesOptions.forEventStartListGeneration()

        assertEquals(StartDrawStartGroupMode.DISABLED, eventOptions.startGroupMode)
        assertEquals(StartDrawClubHandling.IGNORE, eventOptions.clubHandling)
        assertEquals(2, eventOptions.startersPerStartTime)
        assertEquals("series-seed", eventOptions.seed)
        assertEquals(StartDrawStartGroupMode.PREFERRED_THIRDS, preferredOptions.forEventStartListGeneration().startGroupMode)
    }

    @Test
    fun drawStartListHonorsPreferredStartThirdsBeforeSpacingBestPractices() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order)
            ),
            competitors = listOf(
                competitorData("m21-g1", "Alice", "Alpha", startNumber = 1, category = m21, club = "A", preferredStartGroup = 1),
                competitorData("m21-g2", "Aaron", "Alpha", startNumber = 2, category = m21, club = "B", preferredStartGroup = 2),
                competitorData("m21-g3", "Ava", "Alpha", startNumber = 3, category = m21, club = "C", preferredStartGroup = 3),
                competitorData("m40-g1", "Bob", "Bravo", startNumber = 4, category = m40, club = "D", preferredStartGroup = 1),
                competitorData("m40-g2", "Bill", "Bravo", startNumber = 5, category = m40, club = "E", preferredStartGroup = 2),
                competitorData("m40-g3", "Bea", "Bravo", startNumber = 6, category = m40, club = "F", preferredStartGroup = 3)
            )
        )

        val drawn = EventProjectEditor.drawStartList(
            original,
            "01:00",
            StartDrawOptions(startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS)
        )

        assertEquals(1, drawn.startGroupFor("m21-g1"))
        assertEquals(1, drawn.startGroupFor("m40-g1"))
        assertEquals(2, drawn.startGroupFor("m21-g2"))
        assertEquals(2, drawn.startGroupFor("m40-g2"))
        assertEquals(3, drawn.startGroupFor("m21-g3"))
        assertEquals(3, drawn.startGroupFor("m40-g3"))
        assertEquals(listOf(m21.id, m40.id, m21.id, m40.id, m21.id, m40.id), drawn.startOrderCategories())
    }

    @Test
    fun drawStartListWithBalancedStartGroupsAvoidsThirdUsedTwiceInPriorStarts() {
        val m21 = category("cat-m21", "M21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("avoid-middle", "Alice", "Alpha", startNumber = 1, siNumber = 101, category = m21),
                competitorData("avoid-late", "Bob", "Bravo", startNumber = 2, siNumber = 102, category = m21),
                competitorData("avoid-early", "Cara", "Charlie", startNumber = 3, siNumber = 103, category = m21),
                competitorData("new-one", "Drew", "Delta", startNumber = 4, siNumber = 104, category = m21),
                competitorData("new-two", "Evan", "Echo", startNumber = 5, siNumber = 105, category = m21),
                competitorData("new-three", "Fran", "Foxtrot", startNumber = 6, siNumber = 106, category = m21)
            )
        )
        val priorDayOne = listOf(
            startRow(1, "00:00", 103),
            startRow(2, "01:00", 101),
            startRow(3, "02:00", 102)
        )
        val priorDayTwo = listOf(
            startRow(1, "00:00", 103),
            startRow(2, "01:00", 101),
            startRow(3, "02:00", 102)
        )

        val drawn = EventProjectEditor.drawStartListWithBalancedStartGroups(
            original,
            "01:00",
            StartDrawOptions(seed = "balanced"),
            listOf(priorDayOne, priorDayTwo)
        )

        assertNotEquals(2, drawn.startGroupFor("avoid-middle"))
        assertNotEquals(3, drawn.startGroupFor("avoid-late"))
        assertNotEquals(1, drawn.startGroupFor("avoid-early"))
        assertEquals(StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS, drawn.raceData.effectiveStartDrawSettings().options.startGroupMode)
    }

    @Test
    fun drawStartListWithBalancedStartGroupsPrefersEarlyAfterThreeDesirableStarts() {
        val m21 = category("cat-m21", "M21", order = 0)
        val original = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("needs-early", "Alice", "Alpha", startNumber = 1, siNumber = 101, category = m21),
                competitorData("other-one", "Bob", "Bravo", startNumber = 2, siNumber = 102, category = m21),
                competitorData("other-two", "Cara", "Charlie", startNumber = 3, siNumber = 103, category = m21),
                competitorData("new-one", "Drew", "Delta", startNumber = 4, siNumber = 104, category = m21),
                competitorData("new-two", "Evan", "Echo", startNumber = 5, siNumber = 105, category = m21),
                competitorData("new-three", "Fran", "Foxtrot", startNumber = 6, siNumber = 106, category = m21)
            )
        )

        val drawn = EventProjectEditor.drawStartListWithBalancedStartGroups(
            original,
            "01:00",
            StartDrawOptions(seed = "balanced"),
            listOf(
                listOf(startRow(1, "00:00", 102), startRow(2, "01:00", 101), startRow(3, "02:00", 103)),
                listOf(startRow(1, "00:00", 102), startRow(2, "01:00", 103), startRow(3, "02:00", 101)),
                listOf(startRow(1, "00:00", 102), startRow(2, "01:00", 101), startRow(3, "02:00", 103))
            )
        )

        assertEquals(1, drawn.startGroupFor("needs-early"))
    }

    @Test
    fun startListQualityFlagsPreferredStartThirdViolations() {
        val m21 = category("cat-m21", "M21", order = 0)
        val raceData = projectFile(
            categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
            competitors = listOf(
                competitorData("early", "Alice", "Alpha", startNumber = 1, category = m21, preferredStartGroup = 3),
                competitorData("middle", "Bob", "Bravo", startNumber = 2, category = m21, preferredStartGroup = 2),
                competitorData("late", "Cara", "Charlie", startNumber = 3, category = m21, preferredStartGroup = 1)
            )
        ).raceData.copy(
            competitorData = projectFile(
                categories = listOf(categoryData(m21.id, m21.name, order = m21.order)),
                competitors = listOf(
                    competitorData("early", "Alice", "Alpha", startNumber = 1, category = m21, preferredStartGroup = 3)
                        .withStartTime(0),
                    competitorData("middle", "Bob", "Bravo", startNumber = 2, category = m21, preferredStartGroup = 2)
                        .withStartTime(60),
                    competitorData("late", "Cara", "Charlie", startNumber = 3, category = m21, preferredStartGroup = 1)
                        .withStartTime(120)
                )
            ).raceData.competitorData,
            startDrawSettings = StartDrawSettings(
                intervalSeconds = 60,
                options = StartDrawOptions(startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS)
            )
        )

        val quality = EventStartListDetails.from(raceData).quality

        assertEquals(EventStartListRuleSeverity.RED, quality.severity)
        assertEquals(true, quality.rowFindings.any { it.competitorId == "early" && it.text == "Outside preferred start third" })
        assertEquals(true, quality.rowFindings.any { it.competitorId == "late" && it.text == "Outside preferred start third" })
    }

    @Test
    fun startListQualityTreatsSeriesBalancedThirdsAsSeriesOverlay() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val w21 = category("cat-w21", "W21", order = 2)
        val raceData = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order),
                categoryData(w21.id, w21.name, order = w21.order)
            ),
            competitors = listOf(
                competitorData("early", "Alice", "Alpha", startNumber = 1, category = m21, preferredStartGroup = 3)
                    .withStartTime(0),
                competitorData("middle", "Bob", "Bravo", startNumber = 2, category = m40, preferredStartGroup = 2)
                    .withStartTime(60),
                competitorData("late", "Cara", "Charlie", startNumber = 3, category = w21, preferredStartGroup = 1)
                    .withStartTime(120)
            )
        ).raceData.copy(
            startDrawSettings = StartDrawSettings(
                intervalSeconds = 60,
                options = StartDrawOptions(startGroupMode = StartDrawStartGroupMode.BALANCED_MULTI_DAY_THIRDS)
            )
        )

        val quality = EventStartListDetails.from(raceData).quality

        assertEquals(EventStartListRuleSeverity.GREEN, quality.severity)
        assertEquals(100, quality.score)
        assertEquals(false, quality.rowFindings.any { it.text == "Outside preferred start third" })
    }

    @Test
    fun updateStartDrawSettingsUsesDefaultSeedWhenSeedIsBlank() {
        val updated = EventProjectEditor.updateStartDrawSettings(
            projectFile(),
            "02:00",
            StartDrawOptions(seed = "")
        )

        assertEquals(StartDrawOptions.DEFAULT_SEED, updated.raceData.effectiveStartDrawSettings().options.seed)
    }

    @Test
    fun startListDefaultsUseEventTypeIntervals() {
        val classicSettings = projectFile(raceType = RaceType.CLASSIC).raceData.effectiveStartDrawSettings()
        assertEquals(300L, classicSettings.intervalSeconds)
        assertEquals(StartDrawOptions.DEFAULT_SEED, classicSettings.options.seed)
        assertEquals(120L, projectFile(raceType = RaceType.SPRINT).raceData.effectiveStartDrawSettings().intervalSeconds)
        assertEquals(120L, projectFile(raceType = RaceType.FOXORING).raceData.effectiveStartDrawSettings().intervalSeconds)
    }

    @Test
    fun drawStartListUsesRepeatableSeededRandomization() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val m60 = category("cat-m60", "M60", order = 2)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order),
                categoryData(m60.id, m60.name, order = m60.order)
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m21-b", "Aaron", "Alpha", startNumber = 2, category = m21, club = "B"),
                competitorData("m40-a", "Bob", "Bravo", startNumber = 3, category = m40, club = "C"),
                competitorData("m40-b", "Bill", "Bravo", startNumber = 4, category = m40, club = "D"),
                competitorData("m60-a", "Cara", "Charlie", startNumber = 5, category = m60, club = "E"),
                competitorData("m60-b", "Cory", "Charlie", startNumber = 6, category = m60, club = "F")
            )
        )

        val first = EventProjectEditor.drawStartList(original, "02:00", StartDrawOptions(seed = "repeatable")).startOrder()
        val second = EventProjectEditor.drawStartList(original, "02:00", StartDrawOptions(seed = "repeatable")).startOrder()
        val different = EventProjectEditor.drawStartList(original, "02:00", StartDrawOptions(seed = "different")).startOrder()

        assertEquals(first, second)
        assertNotEquals(first, different)
    }

    @Test
    fun seededStartThirdDrawExploresEqualPressureCategoriesBeforeTotalCategorySize() {
        val m60 = category("cat-m60", "M60", order = 0)
        val m50 = category("cat-m50", "M50", order = 1)
        val w65 = category("cat-w65", "W65", order = 2)
        val m21 = category("cat-m21", "M21", order = 3)
        val w21 = category("cat-w21", "W21", order = 4)
        val original = projectFile(
            categories = listOf(
                categoryData(m60.id, m60.name, order = m60.order),
                categoryData(m50.id, m50.name, order = m50.order),
                categoryData(w65.id, w65.name, order = w65.order),
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(w21.id, w21.name, order = w21.order)
            ),
            competitors = listOf(
                competitorData("m60-g1", "Alice", "Alpha", startNumber = 1, category = m60, preferredStartGroup = 1),
                competitorData("m60-g2", "Aaron", "Alpha", startNumber = 2, category = m60, preferredStartGroup = 2),
                competitorData("m60-g3", "Ava", "Alpha", startNumber = 3, category = m60, preferredStartGroup = 3),
                competitorData("m50-g1", "Bob", "Bravo", startNumber = 4, category = m50, preferredStartGroup = 1),
                competitorData("w65-g1", "Cara", "Charlie", startNumber = 5, category = w65, preferredStartGroup = 1),
                competitorData("m21-g2", "Drew", "Delta", startNumber = 6, category = m21, preferredStartGroup = 2),
                competitorData("m21-g3", "Dana", "Delta", startNumber = 7, category = m21, preferredStartGroup = 3),
                competitorData("w21-g2", "Evan", "Echo", startNumber = 8, category = w21, preferredStartGroup = 2),
                competitorData("w21-g3", "Fran", "Foxtrot", startNumber = 9, category = w21, preferredStartGroup = 3)
            )
        )

        val firstStarterCategories = (1..300)
            .map { seedIndex ->
                EventProjectEditor.drawStartList(
                    original,
                    "01:00",
                    StartDrawOptions(
                        clubHandling = StartDrawClubHandling.IGNORE,
                        seed = "category-pressure-$seedIndex",
                        startGroupMode = StartDrawStartGroupMode.PREFERRED_THIRDS
                    )
                ).startOrderCategories().first()
            }
            .toSet()

        assertEquals(setOf(m60.id, m50.id, w65.id), firstStarterCategories)
    }

    @Test
    fun drawStartListAvoidsConsecutiveSameCategoryWhenPossible() {
        val m21 = category("cat-m21", "M21", order = 0)
        val m40 = category("cat-m40", "M40", order = 1)
        val original = projectFile(
            categories = listOf(
                categoryData(m21.id, m21.name, order = m21.order),
                categoryData(m40.id, m40.name, order = m40.order)
            ),
            competitors = listOf(
                competitorData("m21-a", "Alice", "Alpha", startNumber = 1, category = m21, club = "A"),
                competitorData("m21-b", "Aaron", "Alpha", startNumber = 2, category = m21, club = "B"),
                competitorData("m40-a", "Bob", "Bravo", startNumber = 3, category = m40, club = "C"),
                competitorData("m40-b", "Bill", "Bravo", startNumber = 4, category = m40, club = "D")
            )
        )

        val drawn = EventProjectEditor.drawStartList(original, "02:00")
        val categoryOrder = drawn.startOrderCategories()

        assertEquals(listOf("cat-m21", "cat-m40", "cat-m21", "cat-m40"), categoryOrder)
        assertEquals(EventStartListRuleSeverity.GREEN, EventStartListDetails.from(drawn.raceData).quality.severity)
    }

    @Test
    fun rejectsInvalidStartListStartersPerTime() {
        assertFailsWith<IllegalArgumentException> {
            StartDrawOptions(startersPerStartTime = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            StartDrawOptions(startersPerStartTime = 7)
        }
    }

    @Test
    fun rejectsInvalidStartListDrawIntervals() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.drawStartList(projectFile(), "2")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.drawStartList(projectFile(), "00:00")
        }
    }

    private fun projectFile(
        name: String = "Original Race",
        raceType: RaceType = RaceType.CLASSIC,
        raceLevel: RaceLevel = RaceLevel.PRACTICE,
        categories: List<EventCategoryData> = emptyList(),
        competitors: List<EventCompetitorData> = emptyList(),
        controls: List<EventControl> = emptyList(),
        aliases: List<EventAlias> = emptyList(),
        unmatchedReadouts: List<EventReadoutData> = emptyList()
    ): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = "race",
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-05-31T10:00",
                    raceType = raceType,
                    raceLevel = raceLevel,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = categories,
                aliases = aliases,
                controls = controls,
                competitorData = competitors,
                unmatchedReadoutData = unmatchedReadouts
            )
        )

    private fun categoryData(
        id: String,
        name: String,
        order: Int = 0,
        controlSiCodes: List<Int> = emptyList(),
        competitors: List<EventCompetitor> = emptyList(),
        encryptedIdealOrder: String? = null,
        encryptedCourseInfo: String? = null
    ): EventCategoryData =
        EventCategoryData(
            category = category(id, name, order).copy(
                encryptedIdealOrder = encryptedIdealOrder,
                encryptedCourseInfo = encryptedCourseInfo
            ),
            controlPoints = controlSiCodes.mapIndexed { index, siCode ->
                EventControlPoint(
                    id = "$id-control-$index",
                    categoryId = id,
                    siCode = siCode,
                    type = ControlPointType.CONTROL,
                    order = index
                )
            },
            competitors = competitors
        )

    private fun category(id: String, name: String, order: Int = 0): EventCategory =
        EventCategory(
            id = id,
            raceId = "race",
            name = name,
            isMan = name.startsWith("M"),
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = order,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun competitorData(
        id: String,
        firstName: String,
        lastName: String,
        startNumber: Int = 1,
        siNumber: Int? = null,
        category: EventCategory? = null,
        club: String = "",
        readoutData: EventReadoutData? = null,
        preferredStartGroup: Int? = null,
        bibNumber: String = "",
        callSign: String = ""
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category?.id,
                    firstName = firstName,
                    lastName = lastName,
                    club = club,
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = siNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = null,
                    preferredStartGroup = preferredStartGroup,
                    bibNumber = bibNumber,
                    callSign = callSign
                ),
                category = category
            ),
            readoutData = readoutData
        )

    private fun startRow(startNumber: Int, startTimeText: String, siNumber: Int? = null): CompetitorStartCsvImportRow =
        CompetitorStartCsvImportRow(
            startNumber = startNumber,
            startTimeText = startTimeText,
            siNumber = siNumber
        )

    private fun EventCompetitorData.withStartTime(startTimeSeconds: Long): EventCompetitorData =
        copy(
            competitorCategory = competitorCategory.copy(
                competitor = competitorCategory.competitor.copy(drawnStartTimeSeconds = startTimeSeconds)
            )
        )

    private fun EventProjectFile.startTimeFor(competitorId: String): Long? =
        raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorId }
            .competitorCategory.competitor.drawnStartTimeSeconds

    private fun EventProjectFile.startGroupFor(competitorId: String): Int {
        val startTimes = raceData.competitorData
            .mapNotNull { it.competitorCategory.competitor.drawnStartTimeSeconds }
            .distinct()
            .sorted()
        val startTime = startTimeFor(competitorId)!!
        val slotIndex = startTimes.indexOf(startTime)
        return ((slotIndex * 3) / startTimes.size + 1).coerceIn(1, 3)
    }

    private fun EventProjectFile.startOrder(): List<String> =
        raceData.competitorData
            .sortedWith(compareBy({ it.competitorCategory.competitor.drawnStartTimeSeconds }, { it.competitorCategory.competitor.startNumber }))
            .map { it.competitorCategory.competitor.id }

    private fun EventProjectFile.startOrderCategories(): List<String?> =
        raceData.competitorData
            .sortedWith(compareBy({ it.competitorCategory.competitor.drawnStartTimeSeconds }, { it.competitorCategory.competitor.startNumber }))
            .map { it.competitorCategory.competitor.categoryId }

    private fun categoryImportRow(
        name: String = "W21",
        isMan: Boolean = false,
        maxAge: Int = 99,
        lengthMeters: Int = 5_000,
        climbMeters: Int = 100,
        followsRacePresets: Boolean = true,
        raceType: RaceType? = null,
        timeLimitMinutes: Long? = null,
        raceBand: RaceBand? = null,
        controlPointsText: String = "31 32"
    ): CategoryCsvImportRow =
        CategoryCsvImportRow(
            name = name,
            isMan = isMan,
            maxAge = maxAge,
            lengthMeters = lengthMeters,
            climbMeters = climbMeters,
            followsRacePresets = followsRacePresets,
            raceType = raceType,
            timeLimitMinutes = timeLimitMinutes,
            raceBand = raceBand,
            controlPointsText = controlPointsText
        )

    private fun competitorImportRow(
        siNumber: Int? = 2222,
        startNumber: Int? = 2,
        firstName: String = "Bob",
        lastName: String = "Racer",
        categoryName: String = "",
        isMan: Boolean = true,
        birthYear: Int? = 1980,
        club: String = "OK Test",
        index: String = "T001",
        startTimeText: String? = null,
        siRent: Boolean = false,
        bibNumber: String = "",
        callSign: String = ""
    ): CompetitorCsvImportRow =
        CompetitorCsvImportRow(
            siNumber = siNumber,
            startNumber = startNumber,
            firstName = firstName,
            lastName = lastName,
            categoryName = categoryName,
            isMan = isMan,
            birthYear = birthYear,
            club = club,
            personId = index,
            startTimeText = startTimeText,
            siRent = siRent,
            bibNumber = bibNumber,
            callSign = callSign
        )

    private fun readout(
        id: String,
        competitorId: String?,
        siNumber: Int,
        sent: Boolean = false
    ): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = competitorId,
                siNumber = siNumber,
                cardType = 0,
                checkTimeSeconds = null,
                startTimeSeconds = null,
                finishTimeSeconds = null,
                readoutDateTimeIso = "2026-05-31T11:00",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 0,
                runTimeSeconds = 0,
                modified = false,
                sent = sent
            ),
            punches = emptyList()
        )

    private fun EventReadoutData.withResultCategory(categoryId: String): EventReadoutData =
        copy(result = result.copy(categoryId = categoryId))

    private fun sportIdentReadout(
        siNumber: Int = 123456,
        checkSeconds: Long? = null,
        startSeconds: Long? = 600,
        finishSeconds: Long? = 1_800,
        controlCodes: List<Int> = listOf(31, 32),
        firstControlSeconds: Long = 900,
        cardHolder: SportIdentCardHolder? = null
    ): SportIdentCardReadout =
        SportIdentCardReadout(
            siNumber = siNumber,
            series = 2,
            checkTime = checkSeconds?.let(::SportIdentTime),
            startTime = startSeconds?.let(::SportIdentTime),
            finishTime = finishSeconds?.let(::SportIdentTime),
            punches = controlCodes.mapIndexed { index, code ->
                SportIdentCardPunch(
                    siCode = code,
                    siTime = SportIdentTime(firstControlSeconds + index * 60)
                )
            },
            cardHolder = cardHolder
        )

    private fun alias(id: String, siCode: Int, name: String): EventAlias =
        EventAlias(
            id = id,
            raceId = "race",
            siCode = siCode,
            name = name
        )
}
