package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData

class DesktopProjectDiagnosticsTest {
    @Test
    fun reportsClosedProjectState() {
        val diagnostics = DesktopProjectDiagnostics.from(null)

        assertEquals("No Event File open", diagnostics.projectState)
        assertEquals("", diagnostics.schemaText)
        assertEquals(0, diagnostics.categoryCount)
        assertEquals("No Event File open", diagnostics.validationState)
        assertEquals("No Event File open", diagnostics.liveResultPlanText)
        assertTrue(diagnostics.validationIssues.isEmpty())
        assertTrue(diagnostics.betaLimitations.any { it.contains("SPORTident") })
        assertTrue(diagnostics.betaLimitations.any { it.contains("Android result-service configuration") })
        assertTrue(diagnostics.betaLimitations.none { it == "Live result network sending remains post-beta." })
    }

    @Test
    fun reportsOpenProjectSummary() {
        val diagnostics = DesktopProjectDiagnostics.from(projectFile())

        assertEquals("Event File open", diagnostics.projectState)
        assertEquals("Radio-Oracle schema 3", diagnostics.schemaText)
        assertEquals("race", diagnostics.raceId)
        assertEquals("Diagnostics Race", diagnostics.raceName)
        assertEquals("2026-06-01T10:00", diagnostics.startDateTimeIso)
        assertEquals(1, diagnostics.competitorCount)
        assertEquals("No validation issues", diagnostics.validationState)
        assertEquals(
            "0 unsent matched results; 0 already sent; 1 competitor without readouts; 0 unmatched readouts.",
            diagnostics.liveResultPlanText
        )
        assertTrue(diagnostics.validationIssues.isEmpty())
        assertTrue(diagnostics.readinessIssues.isEmpty())
    }

    @Test
    fun reportsLiveResultSendPlan() {
        val diagnostics = DesktopProjectDiagnostics.from(projectFile(readout = result(sent = false)))

        assertEquals(
            "1 unsent matched result; 0 already sent; 0 competitors without readouts; 1 unmatched readout.",
            diagnostics.liveResultPlanText
        )
    }

    @Test
    fun reportsProjectValidationIssues() {
        val diagnostics = DesktopProjectDiagnostics.from(projectFile(raceName = ""))

        assertEquals("1 validation issue", diagnostics.validationState)
        assertTrue(diagnostics.validationIssues.any { it.contains("Event name is blank") })
    }

    @Test
    fun reportsInvalidCategoryControlPoints() {
        val diagnostics = DesktopProjectDiagnostics.from(
            projectFile(
                categories = listOf(categoryData(name = "M21", controlPointsString = "31 32 31"))
            )
        )

        assertEquals("1 validation issue; 1 readiness issue", diagnostics.validationState)
        assertTrue(
            diagnostics.validationIssues.any {
                it.contains("Invalid control points for M21") &&
                        it.contains("Assigned Controls cannot include the same control more than once")
            }
        )
    }

    @Test
    fun reportsCategoryWithCompetitorsButNoCourseData() {
        val category = categoryData(name = "M21", controlPointsString = "")
        val diagnostics = DesktopProjectDiagnostics.from(
            projectFile(categories = listOf(category), competitorCategory = category.category)
        )

        assertTrue(diagnostics.validationState.contains("1 readiness issue"))
        assertTrue(diagnostics.readinessIssues.any { it.contains("M21") && it.contains("competitor") && it.contains("no course data") })
    }

    @Test
    fun reportsCategoryWithCourseDataButNoCompetitors() {
        val diagnostics = DesktopProjectDiagnostics.from(
            projectFile(categories = listOf(categoryData(name = "W21", controlPointsString = "31 32")))
        )

        assertTrue(diagnostics.validationState.contains("1 readiness issue"))
        assertTrue(diagnostics.readinessIssues.any { it.contains("W21") && it.contains("course data but no competitors") })
    }

    private fun projectFile(
        raceName: String = "Diagnostics Race",
        categories: List<EventCategoryData> = emptyList(),
        readout: EventResult? = null,
        competitorCategory: EventCategory? = null
    ): EventProjectFile {
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
            categoryId = competitorCategory?.id,
            firstName = "Test",
            lastName = "Runner",
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = null,
            siRent = false,
            startNumber = null,
            drawnStartTimeSeconds = null
        )

        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = categories,
                aliases = emptyList(),
                competitorData = listOf(
                    EventCompetitorData(
                        competitorCategory = EventCompetitorCategory(competitor, competitorCategory),
                        readoutData = readout?.let { EventReadoutData(it, emptyList()) }
                    )
                ),
                unmatchedReadoutData = if (readout == null) {
                    emptyList()
                } else {
                    listOf(EventReadoutData(result(id = "unmatched", sent = false), emptyList()))
                }
            )
        )
    }

    private fun result(id: String = "result", sent: Boolean): EventResult =
        EventResult(
            id = id,
            raceId = "race",
            competitorId = "competitor",
            siNumber = 123456,
            cardType = 5,
            checkTimeSeconds = null,
            startTimeSeconds = null,
            finishTimeSeconds = null,
            readoutDateTimeIso = "2026-06-01T11:00",
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 1,
            runTimeSeconds = 600,
            modified = false,
            sent = sent
        )

    private fun categoryData(name: String, controlPointsString: String): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = name,
                raceId = "race",
                name = name,
                isMan = true,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = 0,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = controlPointsString
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )
}
