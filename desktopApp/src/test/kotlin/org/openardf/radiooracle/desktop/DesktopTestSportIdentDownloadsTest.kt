package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFactory
import org.openardf.radiooracle.shared.event.EventResultDetails

class DesktopTestSportIdentDownloadsTest {
    @Test
    fun insertsDownloadedReadoutsForEligibleCompetitors() {
        val project = sampleProject()

        val result = DesktopTestSportIdentDownloads.insert(
            projectFile = project,
            maxDownloads = 2,
            readoutDateTimeIso = "2026-06-05T12:00:00"
        )

        assertEquals(2, result.insertedCount)
        assertEquals(1111, result.projectFile.readoutFor("comp-1")?.result?.siNumber)
        assertEquals(2222, result.projectFile.readoutFor("comp-2")?.result?.siNumber)
        assertNull(result.projectFile.readoutFor("comp-3"))
        assertEquals(
            listOf(31, 32),
            result.projectFile.readoutFor("comp-1")?.punches
                ?.filter { it.punch.punchType == SIRecordType.CONTROL }
                ?.map { it.punch.siCode }
        )
        assertEquals(2, EventResultDetails.from(result.projectFile.raceData).size)
    }

    @Test
    fun skipsCompetitorsThatAlreadyHaveReadouts() {
        val firstInsert = DesktopTestSportIdentDownloads.insert(
            projectFile = sampleProject(),
            maxDownloads = 1,
            readoutDateTimeIso = "2026-06-05T12:00:00"
        )

        val secondInsert = DesktopTestSportIdentDownloads.insert(
            projectFile = firstInsert.projectFile,
            maxDownloads = 10,
            readoutDateTimeIso = "2026-06-05T12:05:00"
        )

        assertEquals(1, firstInsert.insertedCount)
        assertEquals(1, secondInsert.insertedCount)
        assertNotNull(secondInsert.projectFile.readoutFor("comp-1"))
        assertNotNull(secondInsert.projectFile.readoutFor("comp-2"))
    }

    private fun sampleProject() =
        EventProjectFactory.createEmptyProject(
            raceId = "race",
            raceName = "Test Downloads",
            startDateTimeIso = "2026-06-05T09:00"
        )
            .let { EventProjectEditor.addCategory(it, "cat", "M21") }
            .let {
                EventProjectEditor.addControl(
                    projectFile = it,
                    controlId = "control-31",
                    label = "F1",
                    siCode = "31",
                    type = ControlPointType.CONTROL,
                    scored = true,
                    publicLabel = "1",
                    notes = ""
                )
            }
            .let {
                EventProjectEditor.addControl(
                    projectFile = it,
                    controlId = "control-32",
                    label = "F2",
                    siCode = "32",
                    type = ControlPointType.CONTROL,
                    scored = true,
                    publicLabel = "2",
                    notes = ""
                )
            }
            .let {
                EventProjectEditor.updateCategoryControlPoints(
                    projectFile = it,
                    categoryId = "cat",
                    controlPointsText = "F1 F2"
                ) { index -> "cat-control-$index" }
            }
            .let { EventProjectEditor.addCompetitor(it, "comp-1", "Alice", "Runner", "1", "1111") }
            .let { EventProjectEditor.assignCompetitorCategory(it, "comp-1", "cat") }
            .let { EventProjectEditor.addCompetitor(it, "comp-2", "Bob", "Racer", "2", "2222") }
            .let { EventProjectEditor.assignCompetitorCategory(it, "comp-2", "cat") }
            .let { EventProjectEditor.addCompetitor(it, "comp-3", "No", "Card", "3", "") }
            .let { EventProjectEditor.assignCompetitorCategory(it, "comp-3", "cat") }

    private fun org.openardf.radiooracle.shared.event.EventProjectFile.readoutFor(competitorId: String) =
        raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorId }
            .readoutData
}
