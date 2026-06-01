package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventCategoryDetails
import org.openardf.radiooracle.shared.event.EventCompetitorDetails
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectSummary
import org.openardf.radiooracle.shared.event.EventReadoutDetails
import org.openardf.radiooracle.shared.event.EventResultDetails
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopSmokeSampleTest {
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
        val aliasId = original.raceData.aliases.first().id

        val edited = EventProjectEditor.assignCompetitorCategory(
            EventProjectEditor.removeCategory(
                EventProjectEditor.updateAlias(
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
                    aliasId,
                    "40",
                    "F4"
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
        assertEquals("F4", readBack.raceData.aliases.first { it.id == aliasId }.name)
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
        assertEquals(ResultStatus.NO_RANKING, readout.result.resultStatus)
        assertEquals(listOf(0, 41, 42, 0), readout.punches.map { it.punch.siCode })
        assertEquals(
            "41 42",
            EventReadoutDetails.from(readBack.raceData)
                .first { it.id == "manual-result" }
                .punchCodesText
        )
    }
}
