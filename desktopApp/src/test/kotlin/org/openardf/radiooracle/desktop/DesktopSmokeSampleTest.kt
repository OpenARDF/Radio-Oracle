package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventCategoryDetails
import org.openardf.radiooracle.shared.event.EventCompetitorDetails
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventReadoutDetails
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.ControlPointType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopSmokeSampleTest {
    @Test
    fun repositoryUsaDensitySampleIncludesUsaRuleCategories() {
        val projectFile = DesktopProjectFiles.read(Path.of("..", "samples", "usa-radio-orienteering-density.rom.json"))
        val raceData = projectFile.raceData
        val categoryDetails = EventCategoryDetails.from(raceData)

        assertTrue(projectFile.isSupportedSchema())
        assertEquals("USA Radio Orienteering Category Density", EventProjectSummary.from(projectFile).raceName)
        assertEquals(
            listOf(
                "W12", "W14", "W16", "W19", "W21", "W35", "W45", "W55", "W65", "W75",
                "M12", "M14", "M16", "M19", "M21", "M40", "M50", "M60", "M70", "M80"
            ),
            categoryDetails.map { it.name }
        )
        assertEquals(12, EventCompetitorDetails.from(raceData).size)
        assertEquals(12, EventReadoutDetails.from(raceData).size)
        assertEquals(11, EventResultDetails.from(raceData).size)
        assertTrue(raceData.categories.all { category ->
            category.category.controlPointsString.isBlank()
        })
        assertTrue(raceData.categories.all { category ->
            category.controlPoints.all { it.controlId.isNotBlank() }
        })
        assertTrue(raceData.categories.all { category ->
            category.publicControlIds.containsAll(category.controlPoints.map { it.controlId })
        })
        assertTrue(raceData.categories.all { category ->
            category.controlPoints.last().siCode == 90 &&
                    category.controlPoints.last().type == ControlPointType.BEACON
        })
        assertTrue(raceData.aliases.isEmpty())
        assertTrue(raceData.controls.isNotEmpty())
    }

    @Test
    fun repositorySmokeSampleExercisesImplementedDesktopSections() {
        val projectFile = DesktopProjectFiles.read(Path.of("..", "samples", "desktop-smoke.rom.json"))
        val raceData = projectFile.raceData

        assertTrue(projectFile.isSupportedSchema())
        assertEquals("Desktop Smoke Race", EventProjectSummary.from(projectFile).raceName)
        assertEquals(2, EventCategoryDetails.from(raceData).size)
        assertEquals(2, EventCompetitorDetails.from(raceData).size)
        assertEquals(2, EventReadoutDetails.from(raceData).size)
        assertEquals(1, EventResultDetails.from(raceData).size)
    }

    @Test
    fun repositorySmokeSampleCanBeEditedSavedAndReadBack() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-edit-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val categoryId = original.raceData.categories.first().category.id
        val removedCategoryId = original.raceData.categories.last().category.id
        val competitorId = original.raceData.competitorData.first().competitorCategory.competitor.id
        val controlId = original.raceData.controls.first().id

        val edited = EventProjectEditor.assignCompetitorCategory(
            EventProjectEditor.removeCategory(
                EventProjectEditor.updateControl(
                    EventProjectEditor.updateCompetitorNumbers(
                        EventProjectEditor.renameCompetitor(
                            EventProjectEditor.updateCategoryControlPoints(
                                EventProjectEditor.renameCategory(
                                    EventProjectEditor.renameRace(original, "Edited Smoke Race"),
                                    categoryId,
                                    "M21E"
                                ),
                                categoryId,
                                "31 32 36B"
                            ) { index -> "edited-control-$index" },
                            competitorId,
                            "Edited",
                            "Runner"
                        ),
                        competitorId,
                        "501",
                        "7654321"
                    ),
                    controlId,
                    "F1",
                    "31",
                    ControlPointType.CONTROL,
                    false,
                    "",
                    "Edited control"
                ),
                removedCategoryId,
                deleteCompetitors = false
            ),
            competitorId,
            null
        )
        val editedWithRaceSettings = EventProjectEditor.updateRaceSettings(
            edited,
            RaceType.FOXORING,
            RaceLevel.REGIONAL,
            RaceBand.COMBINED,
            "90"
        )

        DesktopProjectFiles.write(target, editedWithRaceSettings)
        val readBack = DesktopProjectFiles.read(target)

        assertEquals("Edited Smoke Race", readBack.raceData.race.name)
        assertEquals(RaceType.FOXORING, readBack.raceData.race.raceType)
        assertEquals(RaceLevel.REGIONAL, readBack.raceData.race.raceLevel)
        assertEquals(RaceBand.COMBINED, readBack.raceData.race.raceBand)
        assertEquals(5_400, readBack.raceData.race.timeLimitSeconds)
        assertEquals(listOf(categoryId), readBack.raceData.categories.map { it.category.id })
        assertEquals("M21E", readBack.raceData.categories.first { it.category.id == categoryId }.category.name)
        assertEquals("31 32 36B", readBack.raceData.categories.first().category.controlPointsString)
        assertEquals(
            listOf("edited-control-0", "edited-control-1", "edited-control-2"),
            readBack.raceData.categories.first().controlPoints.map { it.id }
        )
        assertEquals(listOf(31, 32, 36), readBack.raceData.categories.first().controlPoints.map { it.siCode })
        assertEquals(
            null,
            readBack.raceData.competitorData
                .first { it.competitorCategory.competitor.id != competitorId }
                .competitorCategory.competitor.categoryId
        )
        assertEquals(
            "Edited",
            readBack.raceData.competitorData
                .first { it.competitorCategory.competitor.id == competitorId }
                .competitorCategory.competitor.firstName
        )
        assertEquals(
            null,
            readBack.raceData.competitorData
                .first { it.competitorCategory.competitor.id == competitorId }
                .competitorCategory.competitor.categoryId
        )
        assertEquals(501, readBack.raceData.competitorData.first().competitorCategory.competitor.startNumber)
        assertEquals(7_654_321, readBack.raceData.competitorData.first().competitorCategory.competitor.siNumber)
        assertEquals("F1", readBack.raceData.controls.first { it.id == controlId }.label)
        assertEquals("Edited control", readBack.raceData.controls.first { it.id == controlId }.notes)
    }

    @Test
    fun projectEditorCanAddUpdateAndRemoveGlobalControls() {
        val original = EventProjectFactory.createEmptyProject(
            raceId = "control-editor-race",
            raceName = "Control Editor Race",
            startDateTimeIso = "2026-06-05T09:00"
        )

        val added = EventProjectEditor.addControl(
            original,
            controlId = "custom-control",
            label = "F6",
            siCode = "46",
            type = ControlPointType.CONTROL,
            mandatory = true,
            publicLabel = "6",
            notes = "Fast course control"
        )
        val updated = EventProjectEditor.updateControl(
            added,
            controlId = "custom-control",
            label = "F6A",
            siCode = "47",
            type = ControlPointType.SEPARATOR,
            mandatory = false,
            publicLabel = "",
            notes = "Spectator pass"
        )
        val control = updated.raceData.controls.first { it.id == "custom-control" }

        assertEquals("F6A", control.label)
        assertEquals(47, control.siCode)
        assertEquals(ControlPointType.SEPARATOR, control.type)
        assertEquals(false, control.mandatory)
        assertEquals(null, control.publicLabel)
        assertEquals("Spectator pass", control.notes)

        val removed = EventProjectEditor.removeControl(updated, "custom-control")
        assertTrue(removed.raceData.controls.none { it.id == "custom-control" })
    }

    @Test
    fun projectEditorRejectsRemovingCategoryControl() {
        val projectFile = DesktopProjectFiles.read(Path.of("..", "samples", "desktop-smoke.rom.json"))
        val usedControlId = projectFile.raceData.categories.first().controlPoints.first().controlId

        assertThrows(IllegalArgumentException::class.java) {
            EventProjectEditor.removeControl(projectFile, usedControlId)
        }
    }

    @Test
    fun repositorySmokeSampleCompletesDesktopSessionSaveReopenAndExportCopy() {
        val directory = Files.createTempDirectory("rom-desktop-session-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val workingCopy = directory.resolve("working.rom.json")
        val exportCopy = directory.resolve("exported.rom.json")
        Files.copy(source, workingCopy, StandardCopyOption.REPLACE_EXISTING)
        val session = DesktopProjectSession(DesktopProjectFiles)

        session.open(workingCopy)
        val edited = session.updateCurrentProject { currentProject ->
            EventProjectEditor.renameRace(currentProject, "Session Smoke Race")
        }
        session.save()
        session.closeProject()

        val reopened = session.open(workingCopy)
        session.exportCopy(exportCopy)
        val exported = DesktopProjectFiles.read(exportCopy)

        assertEquals("Session Smoke Race", edited.raceData.race.name)
        assertEquals("Session Smoke Race", reopened.raceData.race.name)
        assertEquals(reopened, exported)
        assertEquals(workingCopy, session.currentPath)
        assertEquals(false, session.hasUnsavedChanges)
    }

    @Test
    fun repositorySmokeSampleExportsDesktopCsvFiles() {
        val directory = Files.createTempDirectory("rom-desktop-csv-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val projectFile = DesktopProjectFiles.read(source)
        val categories = directory.resolve("categories.csv")
        val competitors = directory.resolve("competitors.csv")
        val controls = directory.resolve("controls.csv")
        val starts = directory.resolve("starts.csv")
        val startsByCategory = directory.resolve("starts-by-category.csv")
        val startsByMinute = directory.resolve("starts-by-minute.csv")
        val readouts = directory.resolve("readouts.csv")
        val results = directory.resolve("results.csv")

        DesktopProjectFiles.exportCategoriesCsv(categories, projectFile)
        DesktopProjectFiles.exportCompetitorsCsv(competitors, projectFile)
        DesktopProjectFiles.exportControlsCsv(controls, projectFile)
        DesktopProjectFiles.exportCompetitorStartsCsv(starts, projectFile)
        DesktopProjectFiles.exportCompetitorStartsByCategoryCsv(startsByCategory, projectFile)
        DesktopProjectFiles.exportCompetitorStartsByMinuteCsv(startsByMinute, projectFile)
        DesktopProjectFiles.exportReadoutsCsv(readouts, projectFile)
        DesktopProjectFiles.exportResultsCsv(results, projectFile)

        assertEquals(2, Files.readAllLines(categories).size)
        assertEquals(3, Files.readAllLines(competitors).size)
        assertTrue(Files.readString(controls).startsWith("si_code;role;mandatory;public_label;notes"))
        assertEquals(2, Files.readAllLines(starts).size)
        assertEquals(2, Files.readAllLines(startsByCategory).size)
        assertEquals(2, Files.readAllLines(startsByMinute).size)
        assertEquals(2, Files.readAllLines(readouts).size)
        assertEquals(1, Files.readAllLines(results).size)
        assertTrue(Files.readString(competitors).contains("123456;101;Alice;Runner;M21"))
        assertTrue(Files.readString(starts).contains("101;Runner;Alice;M21;;10:00"))
        assertTrue(Files.readString(startsByCategory).contains("101;Runner;Alice;M21;;10:00"))
        assertTrue(Files.readString(startsByMinute).contains("101;Runner;Alice;M21;;10:00"))
        assertTrue(Files.readString(readouts).contains("123456;;00:10:00;00:30:00;0"))
        assertTrue(Files.readString(results).contains("RUNNER Alice;OK;3;00:20:00"))
    }

    @Test
    fun repositorySmokeSampleCanDrawStartList() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val projectFile = DesktopProjectFiles.read(source)

        val drawn = EventProjectEditor.drawStartList(projectFile, "02:00")
        val startTimes = drawn.raceData.competitorData
            .map { it.competitorCategory.competitor.drawnStartTimeSeconds }

        assertEquals(listOf(0L, 120L), startTimes)
    }

    @Test
    fun repositorySmokeSampleExportsArdfJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-ardf-json-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke.ardf.json")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportArdfJson(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("\"format_version\": 1"))
        assertTrue(exported.contains("\"race_type\": \"CLASSIC\""))
        assertTrue(exported.contains("\"category_length\": 5.0"))
        assertTrue(exported.contains("\"unmatched_results\""))
    }

    @Test
    fun repositorySmokeSampleExportsAndroidRaceBackupJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-android-race-backup-json-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke.ardfjs")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportAndroidRaceBackupJson(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("\"race_name\": \"Desktop Smoke Race\""))
        assertTrue(exported.contains("\"race_time_limit\": \"120\""))
        assertTrue(exported.contains("\"unmatched_results\""))
        assertTrue(exported.contains("\"si_number\": 654321"))
    }

    @Test
    fun repositorySmokeSampleImportsAndroidRaceBackupJsonFileAsUnsavedProject() {
        val directory = Files.createTempDirectory("rom-desktop-import-android-race-backup-json-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val backup = directory.resolve("desktop-smoke.ardfjs")
        val projectFile = DesktopProjectFiles.read(source)
        val session = DesktopProjectSession(DesktopProjectFiles)
        var nextId = 0

        DesktopProjectFiles.exportAndroidRaceBackupJson(backup, projectFile)
        val imported = DesktopProjectFiles.importAndroidRaceBackupJson(backup) {
            "smoke-import-${nextId++}"
        }
        session.newProject(imported)

        assertEquals("Desktop Smoke Race", imported.raceData.race.name)
        assertEquals(2, imported.raceData.categories.size)
        assertEquals(2, imported.raceData.competitorData.size)
        assertEquals(1, imported.raceData.unmatchedReadoutData.size)
        assertEquals(null, session.currentPath)
        assertEquals(true, session.hasUnsavedChanges)
    }

    @Test
    fun repositorySmokeSampleExportsFinalResultsJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-final-results-json-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke.final-results.json")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportFinalResultsJson(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("\"categories\""))
        assertTrue(exported.contains("\"aliases\""))
        assertTrue(exported.contains("\"competitors\""))
        assertTrue(exported.contains("\"result_status\""))
    }

    @Test
    fun repositorySmokeSampleExportsIofStartListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-start-list-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke.iof.xml")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportIofStartListXml(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("<StartList"))
        assertTrue(exported.contains("<ClassStart>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<StartTime>2026-05-31T10:10:00</StartTime>"))
    }

    @Test
    fun repositorySmokeSampleExportsIofResultListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-result-list-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke.iof.xml")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportIofResultListXml(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("<ResultList"))
        assertTrue(exported.contains("<ClassResult>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<Status>OK</Status>"))
    }

    @Test
    fun repositorySmokeSampleExportsResultsHtmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-html-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke-results.html")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportResultsHtml(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("<!doctype html>"))
        assertTrue(exported.contains("<h1>Desktop Smoke Race</h1>"))
        assertTrue(exported.contains("<td>RUNNER Alice</td>"))
        assertTrue(exported.contains("<td>00:20:00</td>"))
    }

    @Test
    fun repositorySmokeSampleExportsResultsTextFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-text-smoke")
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = directory.resolve("desktop-smoke-results.txt")
        val projectFile = DesktopProjectFiles.read(source)

        DesktopProjectFiles.exportResultsText(target, projectFile)
        val exported = Files.readString(target)

        assertTrue(exported.contains("Race: Desktop Smoke Race"))
        assertTrue(exported.contains("Category M21"))
        assertTrue(exported.contains("1.\tRUNNER Alice"))
        assertTrue(exported.contains("00:20:00"))
    }

    @Test
    fun repositorySmokeSampleReadoutsCanBeDeletedAndSaved() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-readout-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val matchedReadoutId = original.raceData.competitorData.first { it.readoutData != null }.readoutData!!.result.id
        val unmatchedReadoutId = original.raceData.unmatchedReadoutData.single().result.id

        val withoutMatched = EventProjectEditor.removeReadout(original, matchedReadoutId)
        val withoutBoth = EventProjectEditor.removeReadout(withoutMatched, unmatchedReadoutId)

        DesktopProjectFiles.write(target, withoutBoth)
        val readBack = DesktopProjectFiles.read(target)

        assertEquals(0, EventReadoutDetails.from(readBack.raceData).size)
        assertEquals(null, readBack.raceData.competitorData.first().readoutData)
        assertTrue(readBack.raceData.unmatchedReadoutData.isEmpty())
    }

    @Test
    fun repositorySmokeSampleUnmatchedReadoutCanBeAssignedAndSaved() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-assign-readout-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val unmatchedReadoutId = original.raceData.unmatchedReadoutData.single().result.id
        val competitorWithoutReadout = original.raceData.competitorData.first { it.readoutData == null }
            .competitorCategory.competitor
        val edited = EventProjectEditor.assignUnmatchedReadout(
            original,
            unmatchedReadoutId,
            competitorWithoutReadout.id
        )

        DesktopProjectFiles.write(target, edited)
        val readBack = DesktopProjectFiles.read(target)
        val assigned = readBack.raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorWithoutReadout.id }
            .readoutData!!

        assertTrue(readBack.raceData.unmatchedReadoutData.isEmpty())
        assertEquals(unmatchedReadoutId, assigned.result.id)
        assertEquals(competitorWithoutReadout.id, assigned.result.competitorId)
        assertEquals(true, assigned.result.modified)
        assertEquals(false, assigned.result.sent)
    }

    @Test
    fun repositorySmokeSampleReadoutStatusCanBeEditedAndSaved() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-status-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val matchedReadoutId = original.raceData.competitorData.first { it.readoutData != null }.readoutData!!.result.id
        val edited = EventProjectEditor.updateReadoutManualStatus(
            original,
            matchedReadoutId,
            ResultStatus.DISQUALIFIED
        )

        DesktopProjectFiles.write(target, edited)
        val readBack = DesktopProjectFiles.read(target)
        val result = readBack.raceData.competitorData.first { it.readoutData != null }.readoutData!!.result

        assertEquals(ResultStatus.DISQUALIFIED, result.resultStatus)
        assertEquals(false, result.automaticStatus)
        assertEquals(true, result.modified)
        assertEquals(false, result.sent)
    }

    @Test
    fun repositorySmokeSampleCompetitorCanBeMarkedDnsAndSaved() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-dns-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val competitorWithoutReadout = original.raceData.competitorData.first { it.readoutData == null }
            .competitorCategory.competitor
        val edited = EventProjectEditor.markCompetitorDidNotStart(
            projectFile = original,
            competitorId = competitorWithoutReadout.id,
            resultId = "dns-result",
            readoutDateTimeIso = "2026-05-31T13:00"
        )

        DesktopProjectFiles.write(target, edited)
        val readBack = DesktopProjectFiles.read(target)
        val readout = readBack.raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorWithoutReadout.id }
            .readoutData!!

        assertEquals(ResultStatus.DID_NOT_START, readout.result.resultStatus)
        assertEquals(competitorWithoutReadout.id, readout.result.competitorId)
        assertEquals(false, readout.result.automaticStatus)
        assertEquals(true, readout.result.modified)
        assertEquals(false, readout.result.sent)
        assertEquals(0, readout.punches.size)
    }

    @Test
    fun repositorySmokeSampleManualReadoutCanBeEnteredAndSaved() {
        val source = Path.of("..", "samples", "desktop-smoke.rom.json")
        val target = Files.createTempDirectory("rom-desktop-manual-readout-smoke").resolve("edited.rom.json")

        val original = DesktopProjectFiles.read(source)
        val competitorWithoutReadout = original.raceData.competitorData.first { it.readoutData == null }
            .competitorCategory.competitor
        val edited = EventProjectEditor.addManualReadout(
            projectFile = original,
            resultId = "manual-result",
            competitorId = competitorWithoutReadout.id,
            siNumber = "222222",
            startSeconds = "660",
            finishSeconds = "1660",
            controlCodes = "41 42",
            resultStatus = ResultStatus.OK,
            readoutDateTimeIso = "2026-05-31T12:30"
        ) { index, type ->
            "manual-punch-$index-${type.name}"
        }

        DesktopProjectFiles.write(target, edited)
        val readBack = DesktopProjectFiles.read(target)
        val readout = readBack.raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorWithoutReadout.id }
            .readoutData!!

        assertEquals("manual-result", readout.result.id)
        assertEquals(competitorWithoutReadout.id, readout.result.competitorId)
        assertEquals(222222, readout.result.siNumber)
        assertEquals(1_000, readout.result.runTimeSeconds)
        assertEquals(ResultStatus.OK, readout.result.resultStatus)
        assertEquals(listOf(0, 41, 42, 0), readout.punches.map { it.punch.siCode })
        assertEquals(
            "41 42",
            EventReadoutDetails.from(readBack.raceData)
                .first { it.id == "manual-result" }
                .punchCodesText
        )
    }
}
