package org.openardf.radiooracle.desktop

import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import java.net.URL

class DesktopLocalResultServerTest {
    @Test
    fun rendersResultJsonForOpenProject() {
        val server = DesktopLocalResultServer { projectFile() }

        val json = server.resultsJson()

        assertTrue(json.contains(""""project_open":true"""))
        assertTrue(json.contains(""""race_name":"Local \"Race\"""""))
        assertTrue(json.contains(""""competitor":"RUNNER Alice""""))
        assertTrue(json.contains(""""status":"OK""""))
    }

    @Test
    fun rendersClosedProjectJson() {
        val server = DesktopLocalResultServer { null }

        assertTrue(server.resultsJson().contains(""""project_open":false"""))
    }

    @Test
    fun servesResultJsonOnLoopback() {
        val server = DesktopLocalResultServer { projectFile() }
        try {
            val url = server.start()
            val json = URL("${url}results.json").readText()

            assertTrue(url.startsWith("http://127.0.0.1:"))
            assertTrue(json.contains(""""result_count":1"""))
        } finally {
            server.stop()
        }
    }

    private fun projectFile(): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = "Local \"Race\"",
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
            firstName = "Alice",
            lastName = "Runner",
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
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
                        readoutData = EventReadoutData(result(), emptyList())
                    )
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }

    private fun result(): EventResult =
        EventResult(
            id = "result",
            raceId = "race",
            competitorId = "competitor",
            siNumber = 123456,
            cardType = 6,
            checkTimeSeconds = null,
            startTimeSeconds = 600,
            finishTimeSeconds = 1200,
            readoutDateTimeIso = "2026-06-01T10:15",
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = 3,
            runTimeSeconds = 600,
            modified = false,
            sent = false
        )
}
