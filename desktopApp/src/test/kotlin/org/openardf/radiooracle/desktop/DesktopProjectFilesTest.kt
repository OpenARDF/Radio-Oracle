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
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import java.nio.file.Files

class DesktopProjectFilesTest {
    @Test
    fun writesAndReadsSharedProjectFiles() {
        val directory = Files.createTempDirectory("rom-desktop-project")
        val path = directory.resolve("sample.rom.json")
        val projectFile = EventProjectFile(raceData = raceData())

        DesktopProjectFiles.write(path, projectFile)

        assertEquals(projectFile, DesktopProjectFiles.read(path))
    }

    @Test
    fun exportsResultsCsvFile() {
        val directory = Files.createTempDirectory("rom-desktop-results")
        val path = directory.resolve("results.csv")

        DesktopProjectFiles.exportResultsCsv(path, EventProjectFile(raceData = raceData()))

        assertEquals("", Files.readString(path))
    }

    @Test
    fun exportsArdfJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-ardf-json")
        val path = directory.resolve("event.ardf.json")

        DesktopProjectFiles.exportArdfJson(path, EventProjectFile(raceData = raceData()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"format_version\": 1"))
        assertTrue(exported.contains("\"event_name\": \"Desktop File Race\""))
        assertTrue(exported.contains("\"race_name\": \"Desktop File Race\""))
    }

    @Test
    fun exportsLiveResultsJsonFile() {
        val directory = Files.createTempDirectory("rom-desktop-live-results-json")
        val path = directory.resolve("event.live-results.json")

        DesktopProjectFiles.exportLiveResultsJson(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("\"competitor_category\": \"M21\""))
        assertTrue(exported.contains("\"result_status\": \"OK\""))
    }

    @Test
    fun exportsIofStartListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-start-list")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofStartListXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<StartList"))
        assertTrue(exported.contains("<ClassStart>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<StartTime>2026-05-31T10:00:00</StartTime>"))
        assertTrue(exported.contains("<ControlCard>123456</ControlCard>"))
    }

    @Test
    fun exportsIofResultListXmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-iof-result-list")
        val path = directory.resolve("event.iof.xml")

        DesktopProjectFiles.exportIofResultListXml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<ResultList"))
        assertTrue(exported.contains("<ClassResult>"))
        assertTrue(exported.contains("<Family>Runner</Family>"))
        assertTrue(exported.contains("<StartTime>2026-05-31T10:00:00</StartTime>"))
        assertTrue(exported.contains("<FinishTime>2026-05-31T10:20:00</FinishTime>"))
        assertTrue(exported.contains("<Status>OK</Status>"))
    }

    @Test
    fun exportsResultsHtmlFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-html")
        val path = directory.resolve("results.html")

        DesktopProjectFiles.exportResultsHtml(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("<!doctype html>"))
        assertTrue(exported.contains("<h1>Desktop File Race</h1>"))
        assertTrue(exported.contains("<h2>M21</h2>"))
        assertTrue(exported.contains("<td>RUNNER Alice</td>"))
        assertTrue(exported.contains("<td>00:20:00</td>"))
    }

    @Test
    fun exportsResultsTextFile() {
        val directory = Files.createTempDirectory("rom-desktop-results-text")
        val path = directory.resolve("results.txt")

        DesktopProjectFiles.exportResultsText(path, EventProjectFile(raceData = raceDataWithReadout()))
        val exported = Files.readString(path)

        assertTrue(exported.contains("Results"))
        assertTrue(exported.contains("Race: Desktop File Race"))
        assertTrue(exported.contains("Category M21"))
        assertTrue(exported.contains("1.\tRUNNER Alice"))
        assertTrue(exported.contains("00:20:00"))
    }



    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Desktop File Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun raceDataWithReadout(): EventRaceData {
        val race = raceData().race
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Alice",
            lastName = "Runner",
            club = "",
            index = "IDX",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return raceData().copy(
            categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = EventReadoutData(
                        result = EventResult(
                            id = "result",
                            raceId = race.id,
                            competitorId = competitor.id,
                            siNumber = 123456,
                            cardType = 5,
                            checkTimeSeconds = null,
                            startTimeSeconds = 36_000,
                            finishTimeSeconds = 37_200,
                            readoutDateTimeIso = "2026-05-31T10:21:00",
                            automaticStatus = true,
                            resultStatus = ResultStatus.OK,
                            points = 0,
                            runTimeSeconds = 1_200,
                            modified = false,
                            sent = false
                        ),
                        punches = emptyList()
                    )
                )
            )
        )
    }
}
