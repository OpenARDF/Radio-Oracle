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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.CompetitorCsvImportRow
import org.openardf.radiooracle.shared.files.EventCsvImports
import java.nio.file.Files
import java.util.UUID

class DesktopEventRegImportTest {
    @Test
    fun parsesRegistrationTableIntoCompetitionGroups() {
        val registration = DesktopEventRegRegistrationParser.parse(sampleRegistrationHtml())

        assertEquals("Sample Radio Championships", registration.eventName)
        assertEquals(listOf("Sprint", "FoxO", "2m", "SprMod-NC"), registration.competitions.map { it.name })
        assertEquals(
            listOf("Fala", "Kerns"),
            registration.competitions.first { it.name == "Sprint" }.competitors.map { it.lastName }
        )
        assertEquals(
            "SprMod-NC",
            registration.competitions.first { it.name == "SprMod-NC" }.competitors.single().categoryName
        )
    }

    @Test
    fun parsesGoogleSheetRegistrationSummaryWithSiCardsAndBibNumbers() {
        val registration = DesktopEventRegSpreadsheetParser.parseCsv(
            csvText = sampleGoogleSheetCsv(),
            eventName = "Radio-O Champs Reg Summary"
        )

        assertEquals("Radio-O Champs Reg Summary", registration.eventName)
        assertEquals(listOf("Sprint", "FoxO", "SprMod-NC"), registration.competitions.map { it.name })
        assertEquals(
            listOf("Fala", "Kerns"),
            registration.competitions.first { it.name == "Sprint" }.competitors.map { it.lastName }
        )
        assertEquals(
            listOf("Kerns", "Boyd"),
            registration.competitions.first { it.name == "SprMod-NC" }.competitors.map { it.lastName }
        )

        val fala = registration.competitions.first { it.name == "Sprint" }.competitors.first()
        assertEquals(8400555, fala.siNumber)
        assertEquals("101", fala.bibNumber)
        assertEquals("M-21", fala.categoryName)
        assertEquals("M-21", fala.courseName)
        assertEquals("BOK", fala.club)
        assertEquals("K4FAL", fala.callSign)
        assertEquals("fala@example.test", fala.email)
        assertEquals("555-0101", fala.cellPhone)
        assertEquals(true, fala.usaChampEligible)
        assertEquals(false, fala.region2ChampEligible)
        assertEquals(1991, fala.birthYear)
        assertEquals(true, fala.isMan)
        assertEquals("conf-1", fala.personId)
        assertEquals("05:00", fala.startTimeText)
    }

    @Test
    fun googleSpreadsheetDownloadCandidatesPreferWorkbookAndPreserveLinkedGid() {
        val candidates = DesktopEventRegImporter.spreadsheetDownloadCandidateUrls(
            "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit?gid=2089641617#gid=2089641617"
        )

        assertEquals(
            listOf(
                "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/export?format=xlsx",
                "https://drive.google.com/uc?export=download&id=test-spreadsheet-id",
                "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/export?format=csv&gid=2089641617"
            ),
            candidates
        )
    }

    @Test
    fun parsesRichestRegistrationSectionWhenWorkbookExportIncludesSummaryTabs() {
        val registration = DesktopEventRegSpreadsheetParser.parseCsv(
            csvText = raceSummaryGoogleSheetCsv() + "\u000c" + sampleGoogleSheetCsv(),
            eventName = "Radio-O Champs Reg Summary"
        )

        assertEquals(listOf("Sprint", "FoxO", "SprMod-NC"), registration.competitions.map { it.name })
        val fala = registration.competitions.first { it.name == "Sprint" }.competitors.first()
        assertEquals(8400555, fala.siNumber)
        assertEquals("101", fala.bibNumber)
        assertEquals("K4FAL", fala.callSign)
    }

    @Test
    fun parsesChangedRaceSummaryModifierHeaders() {
        val registration = DesktopEventRegSpreadsheetParser.parseCsv(
            csvText = raceSummaryGoogleSheetCsv(),
            eventName = "Radio-O Champs Reg Summary"
        )

        assertEquals(
            listOf("Sprint", "FoxO", "2m", "80m", "SprMod-NC", "FoxMod-NC", "2mMod-NC", "80mMod-NC"),
            registration.competitions.map { it.name }
        )
        assertEquals(
            "SprMod-NC",
            registration.competitions.first { it.name == "SprMod-NC" }.competitors.single().categoryName
        )
        assertEquals(
            "80mMod-NC",
            registration.competitions.first { it.name == "80mMod-NC" }.competitors.single().categoryName
        )
    }

    @Test
    fun generatesOneEventFilePerCompetitionWithCompetitors() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-eventreg-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importFromWebsite(
            url = "https://eventreg.example.test/reglist",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-06-05T09:00",
            fetchHtml = { sampleRegistrationHtml() },
            idFactory = { ids.next() }
        )

        assertEquals(4, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Sample Radio Championships - Sprint.json",
                "Sample Radio Championships - FoxO.json",
                "Sample Radio Championships - 2m.json",
                "Sample Radio Championships - SprMod-NC.json"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )
        result.generatedFiles.forEach { generated ->
            assertTrue(Files.isRegularFile(generated.path))
        }

        val sprintProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "Sprint" }.path
        )
        assertEquals(RaceType.SPRINT, sprintProject.raceData.race.raceType)
        assertEquals(RaceBand.NONE, sprintProject.raceData.race.raceBand)
        assertEquals(2, sprintProject.raceData.competitorData.size)
        assertEquals(
            listOf("M21", "W65"),
            sprintProject.raceData.categories.map { it.category.name }.sorted()
        )
        assertEquals(true, sprintProject.raceData.categories.single { it.category.name == "M21" }.category.isMan)
        assertEquals(false, sprintProject.raceData.categories.single { it.category.name == "W65" }.category.isMan)
        assertEquals(
            "BOK",
            sprintProject.raceData.competitorData
                .first { it.competitorCategory.competitor.lastName == "Fala" }
                .competitorCategory
                .competitor
                .club
        )

        val twoMeterProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "2m" }.path
        )
        assertEquals(RaceBand.M2, twoMeterProject.raceData.race.raceBand)
        assertFalse(twoMeterProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Boyd" })
    }

    @Test
    fun generatesEventFilesFromGoogleSheetWithSiCardsAndBibNumbers() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-google-sheet-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importFromGoogleSheet(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-07-03T09:00",
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = sampleGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Radio-O Champs Reg Summary_02jul26.csv"
                )
            },
            idFactory = { ids.next() }
        )

        assertEquals(3, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Radio-O Champs Reg Summary_02jul26 - Sprint.json",
                "Radio-O Champs Reg Summary_02jul26 - FoxO.json",
                "Radio-O Champs Reg Summary_02jul26 - SprMod-NC.json"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )
        val generatedFileNames = Files.list(outputDirectory).use { paths ->
            paths.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(
            listOf(
                "Radio-O Champs Reg Summary_02jul26 - FoxO.json",
                "Radio-O Champs Reg Summary_02jul26 - SprMod-NC.json",
                "Radio-O Champs Reg Summary_02jul26 - Sprint.json"
            ),
            generatedFileNames
        )

        val sprintProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "Sprint" }.path
        )
        val fala = sprintProject.raceData.competitorData
            .first { it.competitorCategory.competitor.lastName == "Fala" }
            .competitorCategory
            .competitor
        assertEquals(8400555, fala.siNumber)
        assertEquals("101", fala.bibNumber)
        assertEquals("K4FAL", fala.callSign)
        assertEquals("fala@example.test", fala.email)
        assertEquals("555-0101", fala.cellPhone)
        assertEquals(true, fala.usaChampEligible)
        assertEquals(false, fala.region2ChampEligible)
        assertEquals("conf-1", fala.index)
        assertEquals(RaceType.SPRINT, sprintProject.raceData.race.raceType)

        val sprintCategoryRows = sprintProject.categoryImportRows()
        assertEquals(listOf("M21", "W65"), sprintCategoryRows.map { it.name })

        val sprintCompetitorRows = sprintProject.competitorImportRows()
        assertEquals(listOf("Fala", "Kerns"), sprintCompetitorRows.map { it.lastName })
        assertEquals(listOf(8400555, 1800859), sprintCompetitorRows.map { it.siNumber })
        assertEquals(listOf("101", "102"), sprintCompetitorRows.map { it.bibNumber })
        assertEquals(listOf("M21", "W65"), sprintCompetitorRows.map { it.categoryName })

        val foxProject = DesktopProjectFiles.read(
            result.generatedFiles.first { it.competitionName == "FoxO" }.path
        )
        assertEquals(RaceType.FOXORING, foxProject.raceData.race.raceType)
        assertTrue(foxProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Boyd" })
        assertFalse(foxProject.raceData.competitorData.any { it.competitorCategory.competitor.lastName == "Kerns" })
    }

    @Test
    fun competitorSpreadsheetPlanMapsSingleRaceAndRemovesEmptyCategories() {
        val project = eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE)
            .withCompetitorRows(
                listOf(
                    CompetitorCsvImportRow(
                        siNumber = 111,
                        startNumber = null,
                        firstName = "Old",
                        lastName = "Runner",
                        categoryName = "OldCat",
                        isMan = true,
                        birthYear = null,
                        club = "",
                        personId = "old-runner",
                        startTimeText = null,
                        siRent = false
                    )
                )
            )
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-sprint",
            displayName = "Sprint Race",
            path = null,
            projectFile = project
        )

        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            targets = listOf(target),
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = sprintOnlyGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Sprint Registration.csv"
                )
            }
        )

        assertEquals(listOf("Sprint"), plan.selectedMappings.map { it.competitionName })
        val mapping = plan.selectedMappings.single()
        assertEquals("Sprint Race", mapping.target?.displayName)
        assertEquals(listOf("M21"), mapping.preview?.createdCategoryNames)
        assertEquals(listOf("OldCat"), mapping.preview?.removableEmptyCategoryNames)
        assertEquals(1, mapping.preview?.importedCount)
        assertEquals(1, mapping.preview?.deletedCount)

        val applied = DesktopSpreadsheetCompetitorImporter.applyMapping(mapping)

        assertEquals(listOf("M21"), applied.updatedProjectFile.raceData.categories.map { it.category.name })
        assertEquals(listOf("Fala"), applied.updatedProjectFile.raceData.competitorData.map {
            it.competitorCategory.competitor.lastName
        })
        assertEquals(listOf("OldCat"), applied.removedCategoryNames)
    }

    @Test
    fun competitorSpreadsheetApplyCanKeepOrDeleteEmptyCourseCategories() {
        val project = EventProjectEditor.importCategoryRows(
            projectFile = eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE),
            rows = listOf(
                CategoryCsvImportRow(
                    name = "OldCourse",
                    isMan = true,
                    maxAge = 99,
                    lengthMeters = 3_000,
                    climbMeters = 90,
                    followsRacePresets = true,
                    raceType = RaceType.SPRINT,
                    timeLimitMinutes = null,
                    raceBand = RaceBand.NONE,
                    controlPointsText = "31 32"
                )
            ),
            categoryIdFactory = { "cat-old-course" },
            controlPointIdFactory = { categoryId, index -> "$categoryId-control-$index" }
        )
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-sprint",
            displayName = "Sprint Race",
            path = null,
            projectFile = project
        )
        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            targets = listOf(target),
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = sprintOnlyGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Sprint Registration.csv"
                )
            }
        )
        val mapping = plan.selectedMappings.single()

        assertEquals(listOf("OldCourse"), mapping.preview?.protectedEmptyCategoryNames)

        val kept = DesktopSpreadsheetCompetitorImporter.applyMapping(mapping)
        assertEquals(listOf("M21", "OldCourse"), kept.updatedProjectFile.raceData.categories.map { it.category.name }.sorted())
        assertEquals(emptyList<String>(), kept.removedCategoryNames)

        val deleted = DesktopSpreadsheetCompetitorImporter.applyMapping(
            mapping = mapping,
            removeEmptyCourseCategories = true
        )
        assertEquals(listOf("M21"), deleted.updatedProjectFile.raceData.categories.map { it.category.name })
        assertEquals(listOf("OldCourse"), deleted.removedCategoryNames)
    }

    @Test
    fun competitorSpreadsheetApplyHonorsGranularSelections() {
        val project = eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE)
            .withCompetitorRows(
                listOf(
                    CompetitorCsvImportRow(
                        siNumber = 101,
                        startNumber = null,
                        firstName = "Keep",
                        lastName = "Runner",
                        categoryName = "OldCat",
                        isMan = true,
                        birthYear = null,
                        club = "OK",
                        personId = "keep",
                        startTimeText = null,
                        siRent = false
                    ),
                    CompetitorCsvImportRow(
                        siNumber = 102,
                        startNumber = null,
                        firstName = "Update",
                        lastName = "Runner",
                        categoryName = "OldCat",
                        isMan = true,
                        birthYear = null,
                        club = "Old Club",
                        personId = "update",
                        startTimeText = null,
                        siRent = false
                    ),
                    CompetitorCsvImportRow(
                        siNumber = 103,
                        startNumber = null,
                        firstName = "Remove",
                        lastName = "Runner",
                        categoryName = "OldCat",
                        isMan = true,
                        birthYear = null,
                        club = "Gone",
                        personId = "remove",
                        startTimeText = null,
                        siRent = false
                    )
                )
            )
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-sprint",
            displayName = "Sprint Race",
            path = null,
            projectFile = project
        )
        val plan = DesktopSpreadsheetCompetitorImporter.buildRowsPlan(
            sourceUrl = "competitors.csv",
            eventName = "competitors.csv",
            competitionName = "Sprint",
            rows = listOf(
                CompetitorCsvImportRow(
                    siNumber = 102,
                    startNumber = null,
                    firstName = "Update",
                    lastName = "Runner",
                    categoryName = "M-21",
                    isMan = true,
                    birthYear = null,
                    club = "New Club",
                    personId = "update",
                    startTimeText = null,
                    siRent = false
                ),
                CompetitorCsvImportRow(
                    siNumber = 104,
                    startNumber = null,
                    firstName = "Add",
                    lastName = "Runner",
                    categoryName = "W-55",
                    isMan = false,
                    birthYear = null,
                    club = "New",
                    personId = "add",
                    startTimeText = null,
                    siRent = false
                )
            ),
            target = target
        )
        val mapping = plan.selectedMappings.single()
        val preview = mapping.preview!!

        assertEquals(listOf("Add Runner"), preview.addedCompetitors.map { it.name })
        assertEquals(listOf("Update Runner"), preview.updatedCompetitors.map { it.name })
        assertEquals(listOf("Keep Runner", "Remove Runner"), preview.removedCompetitors.map { it.name }.sorted())

        val removeId = preview.removedCompetitors.single { it.name == "Remove Runner" }.competitorId!!
        val applied = DesktopSpreadsheetCompetitorImporter.applyMapping(
            mapping = mapping,
            selectedRowIndexes = preview.addedCompetitors.mapNotNullTo(mutableSetOf()) { it.rowIndex },
            selectedRemovalCompetitorIds = setOf(removeId),
            emptyCategoryNamesToRemove = emptySet(),
            emptyCourseCategoryNamesToRemove = emptySet()
        )
        val competitors = applied.updatedProjectFile.raceData.competitorData
            .map { it.competitorCategory.competitor }

        assertTrue(competitors.any { it.firstName == "Keep" && it.lastName == "Runner" })
        assertTrue(competitors.any { it.firstName == "Add" && it.lastName == "Runner" })
        assertFalse(competitors.any { it.firstName == "Remove" && it.lastName == "Runner" })
        val skippedUpdate = competitors.single { it.firstName == "Update" && it.lastName == "Runner" }
        assertEquals("Old Club", skippedUpdate.club)
        assertEquals("OldCat", applied.updatedProjectFile.raceData.categories.single { it.category.id == skippedUpdate.categoryId }.category.name)
    }

    @Test
    fun competitorSpreadsheetPlanMapsSeriesByRaceFormat() {
        val targets = listOf(
            eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE),
            eventProject("race-fox", "Foxoring Race", RaceType.FOXORING, RaceBand.NONE),
            eventProject("race-2m", "2m Classic Race", RaceType.CLASSIC, RaceBand.M2),
            eventProject("race-80m", "80m Classic Race", RaceType.CLASSIC, RaceBand.M80)
        ).mapIndexed { index, project ->
            DesktopSpreadsheetCompetitorImportTarget(
                targetId = project.raceData.race.id,
                displayName = project.raceData.race.name,
                path = null,
                projectFile = project,
                seriesOrder = index
            )
        }

        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            targets = targets,
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = seriesGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Series Registration.csv"
                )
            }
        )

        assertEquals(
            mapOf(
                "Sprint" to "Sprint Race",
                "FoxO" to "Foxoring Race",
                "2m" to "2m Classic Race",
                "80m" to "80m Classic Race"
            ),
            plan.selectedMappings.associate { it.competitionName to it.target?.displayName }
        )
    }

    @Test
    fun competitorSpreadsheetPlanKeepsLowConfidenceCandidateForUserOverride() {
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-2m",
            displayName = "2m Classic Race",
            path = null,
            projectFile = eventProject("race-2m", "2m Classic Race", RaceType.CLASSIC, RaceBand.M2)
        )

        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            targets = listOf(target),
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = eightyMeterOnlyGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "80m Registration.csv"
                )
            }
        )

        assertEquals(emptyList<DesktopSpreadsheetCompetitorImportMapping>(), plan.selectedMappings)
        val mapping = plan.mappings.single()
        assertEquals("80m", mapping.competitionName)
        assertEquals("2m Classic Race", mapping.target?.displayName)
        assertTrue(mapping.confidence >= DesktopSpreadsheetCompetitorImporter.MinimumOverrideConfidence)
        assertTrue(mapping.confidence < DesktopSpreadsheetCompetitorImporter.MinimumAutoMapConfidence)
        assertTrue(mapping.canOverrideRejection)
        assertEquals(1, mapping.preview?.importedCount)
    }

    @Test
    fun competitorRowsPlanUsesSameReviewShapeForSingleRaceSources() {
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-sprint",
            displayName = "Sprint Race",
            path = null,
            projectFile = eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE)
        )

        val plan = DesktopSpreadsheetCompetitorImporter.buildRowsPlan(
            sourceUrl = "competitors.csv",
            eventName = "competitors.csv",
            competitionName = "competitors",
            rows = listOf(
                CompetitorCsvImportRow(
                    siNumber = 8400555,
                    startNumber = null,
                    firstName = "Gheorghe",
                    lastName = "Fala",
                    categoryName = "M-21",
                    isMan = true,
                    birthYear = 1991,
                    club = "BOK",
                    personId = "conf-1",
                    startTimeText = null,
                    siRent = false
                )
            ),
            target = target,
            warnings = listOf("1 invalid rows skipped.")
        )

        assertEquals(listOf("competitors"), plan.selectedMappings.map { it.competitionName })
        val mapping = plan.selectedMappings.single()
        assertEquals(100, mapping.confidence)
        assertEquals(listOf("1 invalid rows skipped."), mapping.warnings)
        assertEquals(listOf("M21"), mapping.preview?.createdCategoryNames)
    }

    @Test
    fun writesCategoryAndCompetitorDocumentationCsvsForImportPlan() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-competitor-doc-test")
        val target = DesktopSpreadsheetCompetitorImportTarget(
            targetId = "race-sprint",
            displayName = "Sprint Race",
            path = null,
            projectFile = eventProject("race-sprint", "Sprint Race", RaceType.SPRINT, RaceBand.NONE)
        )
        val plan = DesktopSpreadsheetCompetitorImporter.buildPlan(
            url = "https://docs.google.com/spreadsheets/d/test-spreadsheet-id/edit",
            targets = listOf(target),
            fetchSpreadsheet = {
                SpreadsheetDownload(
                    bytes = sprintOnlyGoogleSheetCsv().toByteArray(),
                    contentType = "text/csv",
                    fileName = "Sprint Registration.csv"
                )
            }
        )

        val documentation = DesktopSpreadsheetCompetitorImporter.writeDocumentationCsvs(
            plan = plan,
            outputDirectory = outputDirectory,
            idFactory = {
                UUID.randomUUID().toString()
            }
        )

        assertEquals(outputDirectory, documentation.outputDirectory)
        assertEquals(listOf("categories", "competitors"), documentation.files.map { it.kind })
        documentation.files.forEach { file ->
            assertTrue(Files.isRegularFile(file.path))
        }
        assertTrue(Files.readString(documentation.files.first { it.kind == "categories" }.path).contains("M21"))
        assertTrue(Files.readString(documentation.files.first { it.kind == "competitors" }.path).contains("Fala"))
    }

    @Test
    fun generatesOneCompetitorCsvPerCompetitionWithEventFileStem() {
        val outputDirectory = Files.createTempDirectory("radio-oracle-eventreg-competitors-test")
        val ids = generateSequence(1) { it + 1 }.map { "id-$it" }.iterator()

        val result = DesktopEventRegImporter.importCompetitorCsvsFromWebsite(
            url = "https://eventreg.example.test/reglist",
            outputDirectory = outputDirectory,
            startDateTimeIso = "2026-06-05T09:00",
            fetchHtml = { sampleRegistrationHtml() },
            idFactory = { ids.next() }
        )

        assertEquals(4, result.generatedFiles.size)
        assertEquals(
            listOf(
                "Sample Radio Championships - Sprint competitors.csv",
                "Sample Radio Championships - FoxO competitors.csv",
                "Sample Radio Championships - 2m competitors.csv",
                "Sample Radio Championships - SprMod-NC competitors.csv"
            ),
            result.generatedFiles.map { it.path.fileName.toString() }
        )

        val sprintCsv = Files.readString(result.generatedFiles.first { it.competitionName == "Sprint" }.path)
        val sprintRows = EventCsvImports.parseAndroidCompetitorRows(sprintCsv).rows
        assertEquals(listOf("Fala", "Kerns"), sprintRows.map { it.lastName })
        assertEquals(listOf("M21", "W65"), sprintRows.map { it.categoryName })
        assertEquals(listOf(true, false), sprintRows.map { it.isMan })
    }

    private fun sampleGoogleSheetCsv(): String =
        """
        ,First,Last,ConfNum,Status,Bib#,YearBorn,Sex,Club,email,cellphone,USA--Champ Eligibility,Region2--Champ Eligibility,E-Punch ID,RentPunch,Sprint Class,Sprint Crs,Sprint Start,FoxO Class,FoxO Crs,FoxO Start,SprMod-NC Crs, Call--Call
        0,Gheorghe,Fala,conf-1,Confirmed,101,1991-01-01 00:00:00,M,BOK,fala@example.test,555-0101,Y,N,8400555,N,M-21,M-21,05:00,M-21,M-21,,NC,K4FAL
        1,Kathleen,Kerns,conf-2,Confirmed,102,1959-01-01 00:00:00,F,MTHD,kerns@example.test,555-0102,N,Y,1800859,N,W-65,W-65,,NC,,,Competing,K7KER
        2,Gerald,Boyd,conf-3,Confirmed,103,1957-01-01 00:00:00,M,NMO,boyd@example.test,555-0103,Y,Y,247347,N,NC,,,M-60,M-60,,Competing,WB8WFK
        """.trimIndent()

    private fun raceSummaryGoogleSheetCsv(): String =
        """
        First,Last,Sprint Crs,Sprint Fee,FoxO Crs,FoxO Fee,2m Crs,2m Fee,80m Crs,80m Fee,Sprint Mod,SM Fee,Fox Mod,Fox Fee,2m Mod,2m Fee.1,80m Mod,80m Fee.1
        Scott,Moore,M-60,32,M-60,32,M-60,32,M-60,32,Comp,5,Comp,5,Comp,5,Comp,5
        """.trimIndent()

    private fun sprintOnlyGoogleSheetCsv(): String =
        """
        First,Last,ConfNum,Sex,E-Punch ID,Sprint Class,Sprint Crs
        Gheorghe,Fala,conf-1,M,8400555,M-21,M-21
        """.trimIndent()

    private fun seriesGoogleSheetCsv(): String =
        """
        First,Last,ConfNum,Sex,E-Punch ID,Sprint Class,FoxO Class,2m Class,80m Class
        Gheorghe,Fala,conf-1,M,8400555,M-21,NC,NC,NC
        Gerald,Boyd,conf-2,M,247347,NC,M-60,NC,NC
        Kathleen,Kerns,conf-3,F,1800859,NC,NC,W-65,NC
        Lidia,Stone,conf-4,F,1800860,NC,NC,NC,W-50
        """.trimIndent()

    private fun eightyMeterOnlyGoogleSheetCsv(): String =
        """
        First,Last,ConfNum,Sex,E-Punch ID,80m Class
        Lidia,Stone,conf-4,F,1800860,W-50
        """.trimIndent()

    private fun eventProject(
        raceId: String,
        name: String,
        raceType: RaceType,
        raceBand: RaceBand
    ): EventProjectFile {
        val project = EventProjectFactory.createEmptyProject(
            raceId = raceId,
            raceName = name,
            startDateTimeIso = "2026-07-03T09:00"
        )
        return project.copy(
            raceData = project.raceData.copy(
                race = project.raceData.race.copy(
                    raceType = raceType,
                    raceBand = raceBand
                )
            )
        )
    }

    private fun EventProjectFile.withCompetitorRows(rows: List<CompetitorCsvImportRow>): EventProjectFile {
        var nextId = 0
        val outcome = EventProjectEditor.importCompetitorRowsWithOutcome(
            projectFile = this,
            rows = rows,
            competitorIdFactory = {
                nextId += 1
                "competitor-$nextId"
            },
            categoryIdFactory = {
                nextId += 1
                "category-$nextId"
            }
        )
        return outcome.projectFile
    }

    private fun sampleRegistrationHtml(): String =
        """
        <!DOCTYPE html>
        <html>
          <head>
            <title>EventReg | Sample Radio Championships Registration List</title>
          </head>
          <body>
            <table id="reglistTable">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Conf?</th>
                  <th>Club</th>
                  <th>Sprint Class</th>
                  <th>Sprint Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>FoxO Class</th>
                  <th>FoxO Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>2m Class</th>
                  <th>2m Crs</th>
                  <th>Start</th>
                  <th></th>
                  <th>SprMod-NC Class</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Fala, Gheorghe</td>
                  <td></td>
                  <td>BOK</td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td>05:00</td>
                  <td></td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td></td>
                  <td></td>
                  <td>M-21</td>
                  <td>M-21</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
                <tr>
                  <td>Kerns, Kathleen</td>
                  <td></td>
                  <td>MTHD</td>
                  <td>W-65</td>
                  <td>W-65</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>W-65</td>
                  <td>W-65</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
                <tr>
                  <td>Boyd, Gerald</td>
                  <td></td>
                  <td>NMO</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>M-60</td>
                  <td>M-60</td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>Y</td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </body>
        </html>
        """.trimIndent()
}
