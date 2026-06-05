package org.openardf.radiooracle.shared.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LiveResultJsonExportsTest {
    @Test
    fun exportsAndroidShapedLiveResultRows() {
        val rows = Json.parseToJsonElement(LiveResultJsonExports.results(raceData()))
            .jsonArray
        val competitor = rows.single().jsonObject
        val result = competitor["result"]!!.jsonObject
        val punches = result["punches"]!!.jsonArray

        assertEquals("IDX1", competitor["competitor_index"]!!.jsonPrimitive.content)
        assertEquals(123456, competitor["si_number"]!!.jsonPrimitive.int)
        assertEquals("Runner", competitor["last_name"]!!.jsonPrimitive.content)
        assertEquals("Alice", competitor["first_name"]!!.jsonPrimitive.content)
        assertEquals("M21", competitor["competitor_category"]!!.jsonPrimitive.content)

        assertEquals("2026-06-01T09:55:00", result["check_time"]!!.jsonPrimitive.content)
        assertEquals("2026-06-01T10:00:00", result["start_time"]!!.jsonPrimitive.content)
        assertEquals("2026-06-01T10:20:00", result["finish_time"]!!.jsonPrimitive.content)
        assertEquals("20:00", result["run_time"]!!.jsonPrimitive.content)
        assertEquals(2, result["place"]!!.jsonPrimitive.int)
        assertEquals("2026-06-01T10:21:00", result["readoutTime"]!!.jsonPrimitive.content)
        assertFalse(result["modified"]!!.jsonPrimitive.boolean)
        assertEquals(2, result["punch_count"]!!.jsonPrimitive.int)
        assertEquals("OK", result["result_status"]!!.jsonPrimitive.content)
        assertEquals(true, result["automatic_status"]!!.jsonPrimitive.boolean)

        assertEquals(2, punches.size)
        assertEquals("Fox", punches[0].jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(31, punches[0].jsonObject["si_code"]!!.jsonPrimitive.int)
        assertEquals("CONTROL", punches[0].jsonObject["control_type"]!!.jsonPrimitive.content)
        assertEquals("AP", punches[0].jsonObject["punch_status"]!!.jsonPrimitive.content)
        assertEquals("05:00", punches[0].jsonObject["split_time"]!!.jsonPrimitive.content)
        assertEquals("F", punches[1].jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("FINISH", punches[1].jsonObject["control_type"]!!.jsonPrimitive.content)
    }

    @Test
    fun skipsCompetitorsWithoutReadoutsOrCategories() {
        val category = category()
        val rows = LiveResultJsonExports.resultRows(
            raceData(
                competitors = listOf(
                    competitorData("with-readout", category = category, readout = readout("with-readout")),
                    competitorData("missing-readout", category = category, readout = null),
                    competitorData("missing-category", category = null, readout = readout("missing-category"))
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals("M21", rows.single().competitorCategory)
        assertEquals(123456, rows.single().siNumber)
    }

    @Test
    fun canLimitExportToSpecificResultIds() {
        val category = category()
        val rows = LiveResultJsonExports.resultRows(
            raceData(
                competitors = listOf(
                    competitorData("one", category = category, readout = readout("result-one")),
                    competitorData("two", category = category, readout = readout("result-two"))
                )
            ),
            resultIds = setOf("result-two")
        )

        assertEquals(1, rows.size)
        assertEquals("2026-06-01T10:21:00", rows.single().result.readoutTime)
    }

    @Test
    fun exportsResultAndPunchStatusShortCodes() {
        val result = LiveResultJsonExports.resultRows(
            raceData(
                resultStatus = ResultStatus.DISQUALIFIED,
                punchStatus = PunchStatus.DUPLICATE
            )
        ).single().result

        assertEquals("DSQ", result.resultStatus)
        assertEquals("DP", result.punches.first().punchStatus)
    }

    @Test
    fun exportsGlobalControlLabelsInPunchCodes() {
        val punch = Json.parseToJsonElement(
            LiveResultJsonExports.results(
                raceData(
                    controls = listOf(
                        EventControl("control-31", "race", "F1", 31, org.openardf.radiooracle.shared.domain.ControlPointType.CONTROL)
                    )
                )
            )
        ).jsonArray.single().jsonObject["result"]!!.jsonObject["punches"]!!.jsonArray.first().jsonObject

        assertEquals("F1", punch["code"]!!.jsonPrimitive.content)
    }

    private fun raceData(
        competitors: List<EventCompetitorData>? = null,
        resultStatus: ResultStatus = ResultStatus.OK,
        punchStatus: PunchStatus = PunchStatus.UNKNOWN,
        controls: List<EventControl> = emptyList()
    ): EventRaceData {
        val category = category()
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Live Race",
                apiKey = "",
                startDateTimeIso = "2026-06-01T09:00:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = competitors ?: listOf(
                competitorData(
                    id = "competitor",
                    category = category,
                    readout = readout("result", resultStatus, punchStatus)
                )
            ),
            unmatchedReadoutData = emptyList(),
            controls = controls
        )
    }

    private fun category(): EventCategory =
        EventCategory(
            id = "category",
            raceId = "race",
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
            controlPointsString = "31"
        )

    private fun competitorData(
        id: String,
        category: EventCategory?,
        readout: EventReadoutData?
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category?.id,
                    firstName = "Alice",
                    lastName = "Runner",
                    club = "",
                    index = "IDX1",
                    isMan = false,
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

    private fun readout(
        id: String,
        resultStatus: ResultStatus = ResultStatus.OK,
        punchStatus: PunchStatus = PunchStatus.UNKNOWN
    ): EventReadoutData {
        val alias = EventAlias("alias", "race", 31, "Fox")
        return EventReadoutData(
            result = EventResult(
                id = id,
                raceId = "race",
                competitorId = "competitor",
                siNumber = 123456,
                cardType = 5,
                checkTimeSeconds = 35_700,
                startTimeSeconds = 36_000,
                finishTimeSeconds = 37_200,
                readoutDateTimeIso = "2026-06-01T10:21:00",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = 2,
                runTimeSeconds = 1_200,
                modified = false,
                sent = false,
                place = 2
            ),
            punches = listOf(
                punch("start", 0, SIRecordType.START, PunchStatus.VALID, 36_000, 0, null),
                punch("control", 31, SIRecordType.CONTROL, punchStatus, 36_300, 300, alias),
                punch("finish", 0, SIRecordType.FINISH, PunchStatus.VALID, 37_200, 1_200, null)
            )
        )
    }

    private fun punch(
        id: String,
        siCode: Int,
        punchType: SIRecordType,
        status: PunchStatus,
        timeSeconds: Long,
        splitSeconds: Long,
        alias: EventAlias?
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = "race",
                resultId = "result",
                cardNumber = 123456,
                siCode = siCode,
                siTimeSeconds = timeSeconds,
                originalSiTimeSeconds = timeSeconds,
                punchType = punchType,
                order = 1,
                punchStatus = status,
                splitSeconds = splitSeconds
            ),
            alias = alias
        )
}
