package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData

class DesktopProjectDiagnosticsTest {
    @Test
    fun reportsClosedProjectState() {
        val diagnostics = DesktopProjectDiagnostics.from(null)

        assertEquals("No project open", diagnostics.projectState)
        assertEquals("", diagnostics.schemaText)
        assertEquals(0, diagnostics.categoryCount)
        assertEquals("No project open", diagnostics.validationState)
        assertTrue(diagnostics.validationIssues.isEmpty())
        assertTrue(diagnostics.betaLimitations.any { it.contains("SPORTident") })
    }

    @Test
    fun reportsOpenProjectSummary() {
        val diagnostics = DesktopProjectDiagnostics.from(projectFile())

        assertEquals("Project open", diagnostics.projectState)
        assertEquals("Radio-Oracle schema 1", diagnostics.schemaText)
        assertEquals("race", diagnostics.raceId)
        assertEquals("Diagnostics Race", diagnostics.raceName)
        assertEquals("2026-06-01T10:00", diagnostics.startDateTimeIso)
        assertEquals(1, diagnostics.competitorCount)
        assertEquals("No validation issues", diagnostics.validationState)
        assertTrue(diagnostics.validationIssues.isEmpty())
    }

    @Test
    fun reportsProjectValidationIssues() {
        val diagnostics = DesktopProjectDiagnostics.from(projectFile(raceName = ""))

        assertEquals("1 validation issue", diagnostics.validationState)
        assertTrue(diagnostics.validationIssues.any { it.contains("Race name is blank") })
    }

    private fun projectFile(raceName: String = "Diagnostics Race"): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = raceName,
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = null,
            firstName = "Test",
            lastName = "Runner",
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = null,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )

        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = listOf(
                    EventCompetitorData(
                        competitorCategory = EventCompetitorCategory(competitor, null),
                        readoutData = null
                    )
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }
}
