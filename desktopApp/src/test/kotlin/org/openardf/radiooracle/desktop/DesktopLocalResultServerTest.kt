package org.openardf.radiooracle.desktop

import org.junit.Assert.assertFalse
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
import java.net.HttpURLConnection
import java.net.URL

class DesktopLocalResultServerTest {
    @Test
    fun rendersResultJsonForOpenProject() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })

        val json = server.resultsJson()

        assertTrue(json.contains(""""project_open":true"""))
        assertTrue(json.contains(""""race_name":"Local \"Race\"""""))
        assertTrue(json.contains(""""category":"M21""""))
        assertTrue(json.contains(""""competitor":"RUNNER Alice""""))
        assertTrue(json.contains(""""status":"OK""""))
    }

    @Test
    fun rendersClosedProjectJson() {
        val server = DesktopLocalResultServer(projectSupplier = { null })

        assertTrue(server.resultsJson().contains(""""project_open":false"""))
    }

    @Test
    fun rendersCategoryJsonForOpenProject() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })

        val json = server.categoriesJson()

        assertTrue(json.contains(""""project_open":true"""))
        assertTrue(json.contains(""""race_name":"Local \"Race\"""""))
        assertTrue(json.contains(""""category_count":1"""))
        assertTrue(json.contains(""""name":"M21""""))
        assertTrue(json.contains(""""competitor_count":1"""))
        assertTrue(json.contains(""""result_count":1"""))
    }

    @Test
    fun categoryJsonOmitsCategoriesWithoutCompetitors() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFileWithEmptyCategory() })

        val json = server.categoriesJson()

        assertTrue(json.contains(""""category_count":1"""))
        assertTrue(json.contains(""""name":"M21""""))
        assertFalse(json.contains(""""name":"W21""""))
    }

    @Test
    fun rendersStartJsonForOpenProject() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })

        val json = server.startsJson()

        assertTrue(json.contains(""""project_open":true"""))
        assertTrue(json.contains(""""race_name":"Local \"Race\"""""))
        assertTrue(json.contains(""""scheduled_count":1"""))
        assertTrue(json.contains(""""unscheduled_count":0"""))
        assertTrue(json.contains(""""competitor":"RUNNER Alice""""))
        assertTrue(json.contains(""""start_sequence":"1""""))
        assertTrue(json.contains(""""start_time":"10:00""""))
        assertTrue(json.contains(""""start_number":"1""""))
    }

    @Test
    fun rendersInForestJsonForOpenProject() {
        val server = DesktopLocalResultServer({ inForestProjectFile() }) { 90 * 60 }

        val json = server.inForestJson()

        assertTrue(json.contains(""""project_open":true"""))
        assertTrue(json.contains(""""race_name":"Local \"Race\"""""))
        assertTrue(json.contains(""""in_forest_count":1"""))
        assertTrue(json.contains(""""competitor":"RUNNER Alice (1)""""))
        assertTrue(json.contains(""""elapsed":"80:00""""))
        assertTrue(json.contains(""""over_limit":true"""))
    }

    @Test
    fun servesResultJsonOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL("${url}results.json").openConnection() as HttpURLConnection
            val json = connection.inputStream.bufferedReader().readText()

            assertTrue(url.startsWith("http://127.0.0.1:"))
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(json.contains(""""result_count":1"""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesAutoRefreshingResultHtmlOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL(url).openConnection() as HttpURLConnection
            val html = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "text/html; charset=utf-8")
            assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"5\">"))
            assertTrue(html.contains("<a href=\"/categories\">Categories</a>"))
            assertTrue(html.contains("<a href=\"/results.json\">results</a>"))
            assertTrue(html.contains("<title>Radio-Oracle Results</title>"))
            assertTrue(html.contains("<tr class=\"category\"><th colspan=\"5\">M21 (1)</th></tr>"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesCategoryJsonOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL("${url}categories.json").openConnection() as HttpURLConnection
            val json = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(json.contains(""""category_count":1"""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesCategoryHtmlOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL("${url}categories").openConnection() as HttpURLConnection
            val html = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "text/html; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"5\">"))
            assertTrue(html.contains("<title>Radio-Oracle Categories</title>"))
            assertTrue(html.contains("<a href=\"/starts\">Starts</a>"))
            assertTrue(html.contains("<a href=\"/categories.json\">categories</a>"))
            assertTrue(html.contains("<td>M21</td>"))
            assertTrue(html.contains("<td>1</td>"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesStartJsonOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL("${url}starts.json").openConnection() as HttpURLConnection
            val json = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "application/json; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(json.contains(""""scheduled_count":1"""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesStartHtmlOnLoopback() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val url = server.start()
            val connection = URL("${url}starts").openConnection() as HttpURLConnection
            val html = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "text/html; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"5\">"))
            assertTrue(html.contains("<title>Radio-Oracle Starts</title>"))
            assertTrue(html.contains("<a href=\"/in-forest\">In Forest</a>"))
            assertTrue(html.contains("<a href=\"/starts.json\">starts</a>"))
            assertTrue(html.contains("<th>Start</th><th>Time</th><th>Competitor</th>"))
            assertTrue(html.contains("<td>RUNNER Alice</td>"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesInForestJsonOnLoopback() {
        val server = DesktopLocalResultServer({ inForestProjectFile() }) { 90 * 60 }
        try {
            val url = server.start()
            val connection = URL("${url}in-forest.json").openConnection() as HttpURLConnection
            val json = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "application/json; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(json.contains(""""in_forest_count":1"""))
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesInForestHtmlOnLoopback() {
        val server = DesktopLocalResultServer({ inForestProjectFile() }) { 90 * 60 }
        try {
            val url = server.start()
            val connection = URL("${url}in-forest").openConnection() as HttpURLConnection
            val html = connection.inputStream.bufferedReader().readText()

            assertTrue(connection.contentType == "text/html; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
            assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"5\">"))
            assertTrue(html.contains("<title>Radio-Oracle In Forest</title>"))
            assertTrue(html.contains("<a href=\"/\">Results</a>"))
            assertTrue(html.contains("<a href=\"/in-forest.json\">in forest</a>"))
            assertTrue(html.contains("<td>RUNNER Alice (1)</td>"))
            assertTrue(html.contains("class=\"over\">Over limit</td>"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsUnknownLocalResultPaths() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val connection = URL("${server.start()}missing").openConnection() as HttpURLConnection

            assertTrue(connection.responseCode == 404)
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsNonGetLocalResultRequests() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val connection = URL("${server.start()}results.json").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"

            assertTrue(connection.responseCode == 405)
            assertTrue(connection.getHeaderField("Allow") == "GET, HEAD")
        } finally {
            server.stop()
        }
    }

    @Test
    fun supportsHeadLocalResultRequests() {
        val server = DesktopLocalResultServer(projectSupplier = { projectFile() })
        try {
            val connection = URL("${server.start()}results.json").openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"

            assertTrue(connection.responseCode == 200)
            assertTrue(connection.contentType == "application/json; charset=utf-8")
            assertTrue(connection.getHeaderField("Cache-Control") == "no-store")
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
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
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
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = 10 * 60
        )

        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
                aliases = emptyList(),
                competitorData = listOf(
                    EventCompetitorData(
                        competitorCategory = EventCompetitorCategory(competitor, category),
                        readoutData = EventReadoutData(result(), emptyList())
                    )
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }

    private fun projectFileWithEmptyCategory(): EventProjectFile {
        val projectFile = projectFile()
        val emptyCategory = projectFile.raceData.categories.single().category.copy(
            id = "empty-category",
            name = "W21",
            isMan = false,
            order = 2
        )
        return projectFile.copy(
            raceData = projectFile.raceData.copy(
                categories = projectFile.raceData.categories + EventCategoryData(
                    category = emptyCategory,
                    controlPoints = emptyList(),
                    competitors = emptyList()
                )
            )
        )
    }

    private fun inForestProjectFile(): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = "Local \"Race\"",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 60 * 60
        )
        val category = EventCategory(
            id = "category",
            raceId = race.id,
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
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
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = 10 * 60
        )

        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = listOf(competitor))),
                aliases = emptyList(),
                competitorData = listOf(
                    EventCompetitorData(
                        competitorCategory = EventCompetitorCategory(competitor, category),
                        readoutData = null
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
