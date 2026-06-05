package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
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
                competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")),
                competitorData("comp-2", "Bob", "Racer", category = category("cat-2", "W21"))
            )
        )

        val updated = EventProjectEditor.removeCategory(original, "cat-1", deleteCompetitors = false)

        assertEquals(listOf("cat-2", "cat-3"), updated.raceData.categories.map { it.category.id })
        assertEquals(listOf(0, 1), updated.raceData.categories.map { it.category.order })
        assertEquals(listOf(31), updated.raceData.categories.first().controlPoints.map { it.siCode })
        assertEquals(null, updated.raceData.competitorData[0].competitorCategory.competitor.categoryId)
        assertEquals(null, updated.raceData.competitorData[0].competitorCategory.category)
        assertEquals("cat-2", updated.raceData.competitorData[1].competitorCategory.competitor.categoryId)
    }

    @Test
    fun removesCategoryAndAssignedCompetitorsWhenRequested() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"), categoryData("cat-2", "W21")),
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", category = category("cat-1", "M21")),
                competitorData("comp-2", "Bob", "Racer", category = category("cat-2", "W21"))
            )
        )

        val updated = EventProjectEditor.removeCategory(original, "cat-1", deleteCompetitors = true)

        assertEquals(listOf("cat-2"), updated.raceData.categories.map { it.category.id })
        assertEquals(listOf("comp-2"), updated.raceData.competitorData.map { it.competitorCategory.competitor.id })
    }

    @Test
    fun rejectsUnknownCategoryRemove() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.removeCategory(projectFile(), "missing", deleteCompetitors = false)
        }
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
            mandatory = true,
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
    fun updatesCompetitorClubAndIndex() {
        val original = projectFile(
            competitors = listOf(competitorData("comp-1", "Alice", "Runner"))
        )

        val updated = EventProjectEditor.updateCompetitorClubIndex(original, "comp-1", " OK Test ", " A101 ")

        val competitor = updated.raceData.competitorData.single().competitorCategory.competitor
        assertEquals("OK Test", competitor.club)
        assertEquals("A101", competitor.index)
    }

    @Test
    fun rejectsUnknownCompetitorClubAndIndexUpdate() {
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorClubIndex(projectFile(), "missing", "OK Test", "A101")
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
        assertEquals(3, updated.raceData.competitorData[1].competitorCategory.competitor.startNumber)
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
            EventProjectEditor.updateCompetitorNumbers(original, "comp-2", "", "3333")
        }
        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.updateCompetitorNumbers(original, "comp-2", "1", "3333")
        }
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
        assertEquals(2, competitor.startNumber)
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
            EventProjectEditor.addCompetitor(original, "comp-2", "Bob", "Racer", "1", "")
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
    fun rejectsDuplicateCategoryImports() {
        val original = projectFile(
            categories = listOf(categoryData("cat-1", "M21"))
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCategoryRows(
                projectFile = original,
                rows = listOf(categoryImportRow(name = "M21")),
                categoryIdFactory = { "cat-2" },
                controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
            )
        }
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
        assertEquals(listOf(1, 2, 3), updated.raceData.competitorData.map { it.competitorCategory.competitor.startNumber })

        val imported = updated.raceData.competitorData[1].competitorCategory
        assertEquals("comp-2", imported.competitor.id)
        assertEquals("cat-1", imported.competitor.categoryId)
        assertEquals("Pavel", imported.competitor.firstName)
        assertEquals("Kolsky", imported.competitor.lastName)

        val newCategoryCompetitor = updated.raceData.competitorData[2].competitorCategory
        assertEquals("cat-2", newCategoryCompetitor.competitor.categoryId)
        assertEquals("W21", newCategoryCompetitor.category?.name)
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
    fun updatesCompetitorRowsByIndexWhenPolicyAllows() {
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
            duplicatePolicy = CompetitorCsvImportDuplicatePolicy.UPDATE_EXISTING_BY_INDEX
        )

        val updatedCompetitor = outcome.projectFile.raceData.competitorData.single().competitorCategory.competitor
        assertEquals(0, outcome.importedCount)
        assertEquals(1, outcome.updatedCount)
        assertEquals("comp-1", updatedCompetitor.id)
        assertEquals("New", updatedCompetitor.firstName)
        assertEquals("Runner", updatedCompetitor.lastName)
        assertEquals(7, updatedCompetitor.startNumber)
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
        assertEquals(1, updatedCompetitor.startNumber)
        assertEquals("cat-2", updatedCompetitor.categoryId)
        assertEquals(listOf("M21", "W21"), outcome.projectFile.raceData.categories.map { it.category.name })
        assertEquals("result-1", outcome.projectFile.raceData.unmatchedReadoutData.single().result.id)
        assertEquals(null, outcome.projectFile.raceData.unmatchedReadoutData.single().result.competitorId)
    }

    @Test
    fun rejectsDuplicateCompetitorImportNumbers() {
        val original = projectFile(
            competitors = listOf(
                competitorData("comp-1", "Alice", "Runner", startNumber = 1, siNumber = 1111).let { data ->
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(
                            competitor = data.competitorCategory.competitor.copy(index = "OK001")
                        )
                    )
                }
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventProjectEditor.importCompetitorRows(
                projectFile = original,
                rows = listOf(competitorImportRow(startNumber = 1, siNumber = 2222)),
                competitorIdFactory = { "comp-2" },
                categoryIdFactory = { "cat-1" }
            )
        }
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
    }

    @Test
    fun importsCompetitorStartRowsByStartNumber() {
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
        val changed = updated.raceData.competitorData[1].competitorCategory.competitor
        assertEquals(null, kept.drawnStartTimeSeconds)
        assertEquals(2222, changed.siNumber)
        assertEquals(10 * 60L + 15, changed.drawnStartTimeSeconds)
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
        assertEquals(0, readout.result.runTimeSeconds)
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
    fun addsDownloadedSportIdentReadoutAsUnmatchedWhenNoCompetitorMatches() {
        val original = projectFile()

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
        categories: List<EventCategoryData> = emptyList(),
        competitors: List<EventCompetitorData> = emptyList(),
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
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = categories,
                aliases = aliases,
                competitorData = competitors,
                unmatchedReadoutData = unmatchedReadouts
            )
        )

    private fun categoryData(
        id: String,
        name: String,
        order: Int = 0,
        controlSiCodes: List<Int> = emptyList(),
        competitors: List<EventCompetitor> = emptyList()
    ): EventCategoryData =
        EventCategoryData(
            category = category(id, name, order),
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
        preferredStartGroup: Int? = null
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
                    preferredStartGroup = preferredStartGroup
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
        siRent: Boolean = false
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
            index = index,
            startTimeText = startTimeText,
            siRent = siRent
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

    private fun sportIdentReadout(
        siNumber: Int = 123456,
        checkSeconds: Long? = null,
        startSeconds: Long? = 600,
        finishSeconds: Long? = 1_800,
        controlCodes: List<Int> = listOf(31, 32),
        firstControlSeconds: Long = 900
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
            }
        )

    private fun alias(id: String, siCode: Int, name: String): EventAlias =
        EventAlias(
            id = id,
            raceId = "race",
            siCode = siCode,
            name = name
        )
}
