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
    fun createsNewDuplicateDownloadedSportIdentReadoutAsUnmatchedWithoutSiNumber() {
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
        assertEquals(null, duplicate.result.siNumber)
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
                    raceType = RaceType.CLASSIC,
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
        readoutData: EventReadoutData? = null
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
                    drawnStartTimeSeconds = null
                ),
                category = category
            ),
            readoutData = readoutData
        )

    private fun EventProjectFile.startTimeFor(competitorId: String): Long? =
        raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorId }
            .competitorCategory.competitor.drawnStartTimeSeconds

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
