package org.openardf.radiooracle.shared.files

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArdfJsonExportsTest {
    @Test
    fun exportsFullEventDocumentUsingStandardsNames() {
        val encoded = ArdfJsonExports.event("Test Event", raceData())
        val root = Json.parseToJsonElement(encoded).jsonObject
        val race = root["races"]!!.jsonArray.single().jsonObject
        val category = race["categories"]!!.jsonArray.single().jsonObject
        val competitor = race["competitors"]!!.jsonArray.single().jsonObject
        val result = competitor["result"]!!.jsonObject
        val punches = result["punches"]!!.jsonArray

        assertEquals(1, root["format_version"]!!.jsonPrimitive.int)
        assertEquals("Test Event", root["event_name"]!!.jsonPrimitive.content)
        assertEquals("Classic Race", race["race_name"]!!.jsonPrimitive.content)
        assertEquals("CLASSIC", race["race_type"]!!.jsonPrimitive.content)
        assertEquals("M80", race["race_band"]!!.jsonPrimitive.content)
        assertEquals(120, race["race_time_limit"]!!.jsonPrimitive.int)

        assertEquals("M21", category["category_name"]!!.jsonPrimitive.content)
        assertEquals(5.0, category["category_length"]!!.jsonPrimitive.content.toDouble())
        assertEquals(100, category["category_climb"]!!.jsonPrimitive.int)
        assertEquals(99, category["category_max_age"]!!.jsonPrimitive.int)
        assertEquals("CONTROL", category["category_control_points"]!!.jsonArray[0].jsonObject["control_type"]!!.jsonPrimitive.content)
        assertEquals("BEACON", category["category_control_points"]!!.jsonArray[1].jsonObject["control_type"]!!.jsonPrimitive.content)

        assertEquals("Runner", competitor["last_name"]!!.jsonPrimitive.content)
        assertEquals("M21", competitor["competitor_category"]!!.jsonPrimitive.content)
        assertEquals("10:00", competitor["competitor_start_time"]!!.jsonPrimitive.content)
        assertFalse(encoded.contains(": null"))

        assertEquals("OK", result["result_status"]!!.jsonPrimitive.content)
        assertEquals("10:00", result["run_time"]!!.jsonPrimitive.content)
        assertEquals(2, result["punch_count"]!!.jsonPrimitive.int)
        assertEquals("31", punches[0].jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("CONTROL", punches[0].jsonObject["control_type"]!!.jsonPrimitive.content)
        assertEquals("AP", punches[0].jsonObject["punch_status"]!!.jsonPrimitive.content)
        assertEquals("M", punches[1].jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("BEACON", punches[1].jsonObject["control_type"]!!.jsonPrimitive.content)
        assertEquals("OK", punches[1].jsonObject["punch_status"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportsPunchStatusShortCodesUsingAndroidAndStandardValues() {
        val raceData = raceData(
            punchStatuses = listOf(PunchStatus.INVALID, PunchStatus.DUPLICATE)
        )

        val punches = Json.parseToJsonElement(ArdfJsonExports.event("Test Event", raceData))
            .jsonObject["races"]!!.jsonArray.single().jsonObject["competitors"]!!
            .jsonArray.single().jsonObject["result"]!!.jsonObject["punches"]!!.jsonArray

        assertEquals("MP", punches[0].jsonObject["punch_status"]!!.jsonPrimitive.content)
        assertEquals("DP", punches[1].jsonObject["punch_status"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportsCategoryOverridesWhenPresent() {
        val raceData = raceData(
            category = category().copy(
                differentProperties = true,
                raceType = RaceType.SPRINT,
                raceBand = RaceBand.M2,
                timeLimitSeconds = 2_700
            )
        )

        val category = Json.parseToJsonElement(ArdfJsonExports.event("Test Event", raceData))
            .jsonObject["races"]!!.jsonArray.single().jsonObject["categories"]!!
            .jsonArray.single().jsonObject

        assertTrue(category["category_different_properties"]!!.jsonPrimitive.boolean)
        assertEquals("SPRINT", category["category_race_type"]!!.jsonPrimitive.content)
        assertEquals("M2", category["category_band"]!!.jsonPrimitive.content)
        assertEquals(45, category["category_time_limit"]!!.jsonPrimitive.int)
    }

    @Test
    fun exportsDisqualifiedResultStatus() {
        val raceData = raceData(resultStatus = ResultStatus.DISQUALIFIED)

        val result = Json.parseToJsonElement(ArdfJsonExports.event("Test Event", raceData))
            .jsonObject["races"]!!.jsonArray.single().jsonObject["competitors"]!!
            .jsonArray.single().jsonObject["result"]!!.jsonObject

        assertEquals("DSQ", result["result_status"]!!.jsonPrimitive.content)
    }

    @Test
    fun exportsGlobalControlsAsArdfAliasesAndPunchLabels() {
        val raceData = raceData(
            controls = listOf(
                EventControl(
                    id = "control-31",
                    raceId = "race",
                    label = "F1",
                    siCode = 31,
                    type = ControlPointType.CONTROL
                )
            )
        )

        val race = Json.parseToJsonElement(ArdfJsonExports.event("Test Event", raceData))
            .jsonObject["races"]!!.jsonArray.single().jsonObject
        val alias = race["aliases"]!!.jsonArray.first().jsonObject
        val punch = race["competitors"]!!.jsonArray.single().jsonObject["result"]!!
            .jsonObject["punches"]!!.jsonArray.first().jsonObject

        assertEquals(31, alias["alias_si_code"]!!.jsonPrimitive.int)
        assertEquals("F1", alias["alias_name"]!!.jsonPrimitive.content)
        assertEquals("F1", punch["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun resolvesArdfCategoryControlPointsThroughGlobalControls() {
        val raceData = raceData(
            categoryControlPoints = listOf(
                EventControlPoint(
                    id = "cp-legacy",
                    categoryId = "category",
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    order = 1,
                    controlId = "control-35"
                )
            ),
            controls = listOf(
                EventControl(
                    id = "control-35",
                    raceId = "race",
                    label = "M",
                    siCode = 35,
                    type = ControlPointType.BEACON
                )
            )
        )

        val controlPoint = ArdfJsonExports.eventDocument("Test Event", raceData)
            .races.single()
            .categories.single()
            .categoryControlPoints.single()

        assertEquals(35, controlPoint.siCode)
        assertEquals("BEACON", controlPoint.controlType)
    }

    private fun raceData(
        category: EventCategory = category(),
        resultStatus: ResultStatus = ResultStatus.OK,
        punchStatuses: List<PunchStatus> = listOf(PunchStatus.UNKNOWN, PunchStatus.VALID),
        categoryControlPoints: List<EventControlPoint>? = null,
        controls: List<EventControl> = emptyList()
    ): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Classic Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val competitor = EventCompetitor(
            id = "competitor",
            raceId = race.id,
            categoryId = category.id,
            firstName = "Test",
            lastName = "Runner",
            club = "OK Test",
            index = "OK001",
            isMan = true,
            birthYear = 1985,
            siNumber = 123456,
            siRent = false,
            startNumber = 7,
            drawnStartTimeSeconds = 600
        )
        val categoryData = EventCategoryData(
            category = category,
            controlPoints = categoryControlPoints ?: listOf(
                EventControlPoint("control-1", category.id, 31, ControlPointType.CONTROL, 1),
                EventControlPoint("control-2", category.id, 90, ControlPointType.BEACON, 2)
            ),
            competitors = listOf(competitor)
        )
        val readout = EventReadoutData(
            result = EventResult(
                id = "result",
                raceId = race.id,
                competitorId = competitor.id,
                siNumber = 123456,
                cardType = 0,
                checkTimeSeconds = null,
                startTimeSeconds = null,
                finishTimeSeconds = null,
                readoutDateTimeIso = "2026-06-01T10:20",
                automaticStatus = true,
                resultStatus = resultStatus,
                points = 2,
                runTimeSeconds = 600,
                modified = false,
                sent = false,
                place = 1
            ),
            punches = listOf(
                punch("punch-1", race.id, "result", 31, 120, 1, punchStatuses[0]),
                punch("punch-2", race.id, "result", 90, 480, 2, punchStatuses[1], EventAlias("alias-90", race.id, 90, "M"))
            )
        )

        return EventRaceData(
            race = race,
            categories = listOf(categoryData),
            aliases = listOf(EventAlias("alias-90", race.id, 90, "M")),
            competitorData = listOf(
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(competitor, category),
                    readoutData = readout
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
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "31 90B"
        )

    private fun punch(
        id: String,
        raceId: String,
        resultId: String,
        siCode: Int,
        splitSeconds: Long,
        order: Int,
        punchStatus: PunchStatus,
        alias: EventAlias? = null
    ): EventAliasPunch =
        EventAliasPunch(
            punch = EventPunch(
                id = id,
                raceId = raceId,
                resultId = resultId,
                cardNumber = 123456,
                siCode = siCode,
                siTimeSeconds = 600 + splitSeconds,
                originalSiTimeSeconds = 600 + splitSeconds,
                punchType = SIRecordType.CONTROL,
                order = order,
                punchStatus = punchStatus,
                splitSeconds = splitSeconds
            ),
            alias = alias
        )
}
