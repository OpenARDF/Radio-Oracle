package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.network.LiveResultRequestSpec
import org.openardf.radiooracle.shared.network.NetworkEndpoints
import org.openardf.radiooracle.shared.network.NetworkHeaders

class DesktopRobisLiveResultSenderTest {
    @Test
    fun sendsOnlyUnsentMatchedResultsAndMarksThemSent() {
        var capturedRequest: LiveResultRequestSpec? = null
        var capturedPayload = ""
        val sender = DesktopRobisLiveResultSender { request, payload ->
            capturedRequest = request
            capturedPayload = payload
            DesktopLiveResultSendResponse(statusCode = 200, body = """{"ok":true}""")
        }

        val result = sender.sendUnsent(projectFile(), apiKey = "secret")

        assertEquals(1, result.sentCount)
        assertEquals(200, result.statusCode)
        assertEquals(NetworkEndpoints.ROBIS_RESULTS_API_URL, capturedRequest!!.url)
        assertEquals("secret", capturedRequest!!.headers[NetworkHeaders.ROBIS_API_HEADER])
        assertTrue(capturedPayload.contains("\"competitor_index\": \"alpha\""))
        assertFalse(capturedPayload.contains("\"competitor_index\": \"beta\""))
        assertTrue(result.projectFile.raceData.competitorData[0].readoutData!!.result.sent)
        assertTrue(result.projectFile.raceData.competitorData[1].readoutData!!.result.sent)
    }

    @Test
    fun rejectsWhenThereAreNoCandidates() {
        val sender = DesktopRobisLiveResultSender { _, _ ->
            error("transport should not be called")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            sender.sendUnsent(projectFile(unsent = false), apiKey = "secret")
        }

        assertEquals("There are no unsent matched results to send.", error.message)
    }

    @Test
    fun rejectsFailedHttpResponseWithoutReturningUpdatedProject() {
        val sender = DesktopRobisLiveResultSender { _, _ ->
            DesktopLiveResultSendResponse(statusCode = 401, body = "unauthorized")
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            sender.sendUnsent(projectFile(), apiKey = "secret")
        }

        assertEquals("ROBIS send failed with HTTP 401: unauthorized", error.message)
    }

    private fun projectFile(unsent: Boolean = true): EventProjectFile {
        val race = EventRace(
            id = "race",
            name = "ROBIS Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T09:00:00",
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
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = listOf(
                    competitorData("alpha", category, readout("result-unsent", sent = !unsent)),
                    competitorData("beta", category, readout("result-sent", sent = true))
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }

    private fun competitorData(
        id: String,
        category: EventCategory,
        readout: EventReadoutData
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category.id,
                    firstName = id,
                    lastName = "Runner",
                    club = "",
                    index = id,
                    isMan = true,
                    birthYear = null,
                    siNumber = 123456,
                    siRent = false,
                    startNumber = 1,
                    drawnStartTimeSeconds = null
                ),
                category = category
            ),
            readoutData = readout
        )

    private fun readout(id: String, sent: Boolean): EventReadoutData =
        EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = id,
                siNumber = 123456,
                cardType = 5,
                checkTimeSeconds = null,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 37_200,
                readoutDateTimeIso = "2026-06-01T10:21:00",
                automaticStatus = true,
                resultStatus = ResultStatus.OK,
                points = 0,
                runTimeSeconds = 1_200,
                modified = false,
                sent = sent
            ),
            punches = emptyList()
        )
}
