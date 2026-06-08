package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.ResultStatus
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
        assertEquals(1112, result.projectFile.readoutFor("comp-2")?.result?.siNumber)
        assertNull(result.projectFile.readoutFor("comp-no-card"))
        assertEquals(
            listOf(31, 32, 90),
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

    @Test
    fun insertsVariedDownloadedReadoutsForResultsTesting() {
        val result = DesktopTestSportIdentDownloads.insert(
            projectFile = sampleProject(competitorCount = 12),
            maxDownloads = 12,
            readoutDateTimeIso = "2026-06-05T12:00:00"
        )
        val readouts = result.projectFile.raceData.competitorData.mapNotNull { it.readoutData }
        val statuses = readouts.map { it.result.resultStatus }.toSet()

        assertEquals(12, result.insertedCount)
        assertEquals(true, statuses.contains(ResultStatus.OK))
        assertEquals(true, statuses.contains(ResultStatus.DID_NOT_FINISH))
        assertEquals(true, statuses.contains(ResultStatus.ERROR))
        assertEquals(true, readouts.any { it.result.points == 2 })
        assertEquals(true, readouts.any { it.result.points in 1..1 })
        assertEquals(true, readouts.count { it.result.finishTimeSeconds != null } >= 10)
        assertEquals(true, readouts.count { readout ->
            readout.punches.any { it.punch.siCode == 90 && it.punch.punchType == SIRecordType.CONTROL }
        } >= 10)
    }

    private fun sampleProject(competitorCount: Int = 2) =
        (1..competitorCount).fold(
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
                    scored = false,
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
                    scored = false,
                    publicLabel = "2",
                    notes = ""
                )
            }
            .let {
                EventProjectEditor.addControl(
                    projectFile = it,
                    controlId = "beacon-90",
                    label = "Beacon",
                    siCode = "90",
                    type = ControlPointType.BEACON,
                    scored = false,
                    publicLabel = "B",
                    notes = ""
                )
            }
            .let {
                EventProjectEditor.updateCategoryControlPoints(
                    projectFile = it,
                    categoryId = "cat",
                    controlPointsText = "F1 F2 Beacon"
                ) { index -> "cat-control-$index" }
            }
        ) { project, index ->
            EventProjectEditor.addCompetitor(
                project,
                "comp-$index",
                "Runner",
                "$index",
                index.toString(),
                (1110 + index).toString()
            ).let { EventProjectEditor.assignCompetitorCategory(it, "comp-$index", "cat") }
        }.let { project ->
            EventProjectEditor.addCompetitor(project, "comp-no-card", "No", "Card", (competitorCount + 1).toString(), "")
                .let { EventProjectEditor.assignCompetitorCategory(it, "comp-no-card", "cat") }
        }

    private fun org.openardf.radiooracle.shared.event.EventProjectFile.readoutFor(competitorId: String) =
        raceData.competitorData
            .first { it.competitorCategory.competitor.id == competitorId }
            .readoutData
}
