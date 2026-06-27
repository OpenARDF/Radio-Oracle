/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FinalResultJsonExportsTest {
    @Test
    fun exportsAndroidShapedFinalResultDocument() {
        val document = Json.parseToJsonElement(FinalResultJsonExports.results(raceData()))
            .jsonObject
        val category = document["categories"]!!.jsonArray.single().jsonObject
        val alias = document["aliases"]!!.jsonArray.single().jsonObject
        val competitor = document["competitors"]!!.jsonArray.single().jsonObject
        val result = competitor["result"]!!.jsonObject
        val punches = result["punches"]!!.jsonArray

        assertEquals("M21", category["category_name"]!!.jsonPrimitive.content)
        assertEquals(true, category["category_gender"]!!.jsonPrimitive.boolean)
        assertEquals(4500, category["category_length"]!!.jsonPrimitive.int)
        assertEquals(180, category["category_climb"]!!.jsonPrimitive.int)
        assertEquals("CONTROL", category["category_control_points"]!!.jsonArray.single().jsonObject["control_type"]!!.jsonPrimitive.content)
        assertEquals(false, category["category_different_properties"]!!.jsonPrimitive.boolean)
        assertEquals("", category["category_time_limit"]!!.jsonPrimitive.content)

        assertEquals(31, alias["alias_si_code"]!!.jsonPrimitive.int)
        assertEquals("Fox", alias["alias_name"]!!.jsonPrimitive.content)

        assertEquals("Alice", competitor["first_name"]!!.jsonPrimitive.content)
        assertEquals("Runner", competitor["last_name"]!!.jsonPrimitive.content)
        assertEquals("OC", competitor["competitor_club"]!!.jsonPrimitive.content)
        assertEquals("M21", competitor["competitor_category"]!!.jsonPrimitive.content)
        assertEquals("IDX1", competitor["competitor_index"]!!.jsonPrimitive.content)
        assertFalse(competitor["competitor_gender"]!!.jsonPrimitive.boolean)
        assertEquals(123456, competitor["si_number"]!!.jsonPrimitive.int)
        assertFalse(competitor["si_rent"]!!.jsonPrimitive.boolean)
        assertEquals(7, competitor["start_number"]!!.jsonPrimitive.int)
        assertEquals("10:00", competitor["competitor_start_time"]!!.jsonPrimitive.content)

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
        assertEquals("AP", punches[0].jsonObject["punch_status"]!!.jsonPrimitive.content)
        assertEquals("05:00", punches[0].jsonObject["split_time"]!!.jsonPrimitive.content)
        assertEquals("F", punches[1].jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals("FINISH", punches[1].jsonObject["control_type"]!!.jsonPrimitive.content)
    }

    @Test
    fun keepsCompetitorsButOmitsMissingOrErrorResults() {
        val category = category()
        val document = FinalResultJsonExports.resultDocument(
            raceData(
                competitors = listOf(
                    competitorData("missing", category, readout = null),
                    competitorData("error", category, readout = readout("error", resultStatus = ResultStatus.ERROR))
                )
            )
        )

        assertEquals(2, document.competitors.size)
        assertNull(document.competitors[0].result)
        assertNull(document.competitors[1].result)
    }

    @Test
    fun explicitProtectedCourseExportDoesNotFallBackToPublicCategoryLength() {
        val lockedCategory = FinalResultJsonExports.resultDocument(
            raceData(),
            protectedCourseInfoByCategoryId = emptyMap()
        ).categories.single()
        val unlockedCategory = FinalResultJsonExports.resultDocument(
            raceData(),
            protectedCourseInfoByCategoryId = mapOf(
                "category" to ProtectedCourseInfo(lengthMeters = 4_500, climbMeters = 180)
            )
        ).categories.single()

        assertEquals(0, lockedCategory.categoryLength)
        assertEquals(0, lockedCategory.categoryClimb)
        assertEquals(6_300, unlockedCategory.categoryLength)
        assertEquals(180, unlockedCategory.categoryClimb)
    }

    @Test
    fun omitsCategoriesWithoutCompetitorsFromFinalResultCategoryDefinitions() {
        val base = raceData()
        val emptyCategory = category().copy(id = "empty", name = "W21", isMan = false, order = 2)
        val document = FinalResultJsonExports.resultDocument(
            base.copy(
                categories = base.categories + EventCategoryData(
                    category = emptyCategory,
                    controlPoints = emptyList(),
                    competitors = emptyList()
                )
            )
        )

        assertEquals(listOf("M21"), document.categories.map { it.categoryName })
    }

    @Test
    fun exportsGlobalControlsAsAndroidAliasesAndPunchLabels() {
        val document = Json.parseToJsonElement(
            FinalResultJsonExports.results(
                raceData(
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
            )
        ).jsonObject
        val alias = document["aliases"]!!.jsonArray.single().jsonObject
        val punch = document["competitors"]!!
            .jsonArray.single()
            .jsonObject["result"]!!
            .jsonObject["punches"]!!
            .jsonArray.first()
            .jsonObject

        assertEquals(31, alias["alias_si_code"]!!.jsonPrimitive.int)
        assertEquals("F1", alias["alias_name"]!!.jsonPrimitive.content)
        assertEquals("F1", punch["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun resolvesAndroidCategoryControlPointsThroughGlobalControls() {
        val category = category()
        val document = FinalResultJsonExports.resultDocument(
            raceData(
                categoryControlPoint = EventControlPoint(
                    id = "cp-legacy",
                    categoryId = category.id,
                    siCode = 31,
                    type = ControlPointType.CONTROL,
                    order = 0,
                    controlId = "control-35"
                ),
                controls = listOf(
                    EventControl(
                        id = "control-35",
                        raceId = "race",
                        label = "F5",
                        siCode = 35,
                        type = ControlPointType.BEACON
                    )
                )
            )
        )
        val controlPoint = document.categories.single().categoryControlPoints.single()

        assertEquals(35, controlPoint.siCode)
        assertEquals(ControlPointType.BEACON, controlPoint.controlType)
    }

    private fun raceData(
        competitors: List<EventCompetitorData>? = null,
        resultStatus: ResultStatus = ResultStatus.OK,
        punchStatus: PunchStatus = PunchStatus.UNKNOWN,
        categoryControlPoint: EventControlPoint? = null,
        controls: List<EventControl> = emptyList()
    ): EventRaceData {
        val category = category()
        val alias = EventAlias("alias", "race", 31, "Fox")
        val competitor = competitor("competitor", category)
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Final Race",
                apiKey = "",
                startDateTimeIso = "2026-06-01T09:00:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                EventCategoryData(
                    category = category,
                    controlPoints = listOf(
                        categoryControlPoint ?: EventControlPoint("cp-31", category.id, 31, ControlPointType.CONTROL, 0)
                    ),
                    competitors = listOf(competitor)
                )
            ),
            aliases = listOf(alias),
            competitorData = competitors ?: listOf(
                competitorData("competitor", category, readout("result", resultStatus, punchStatus, alias))
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
            lengthMeters = 4_500,
            climbMeters = 180,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "31"
        )

    private fun competitor(id: String, category: EventCategory): EventCompetitor =
        EventCompetitor(
            id = id,
            raceId = "race",
            categoryId = category.id,
            firstName = "Alice",
            lastName = "Runner",
            club = "OC",
            index = "IDX1",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 7,
            drawnStartTimeSeconds = 600
        )

    private fun competitorData(
        id: String,
        category: EventCategory,
        readout: EventReadoutData?
    ): EventCompetitorData {
        val competitor = competitor(id, category)
        return EventCompetitorData(
            competitorCategory = EventCompetitorCategory(competitor, category),
            readoutData = readout
        )
    }

    private fun readout(
        id: String,
        resultStatus: ResultStatus = ResultStatus.OK,
        punchStatus: PunchStatus = PunchStatus.UNKNOWN,
        alias: EventAlias = EventAlias("alias", "race", 31, "Fox")
    ): EventReadoutData =
        EventReadoutData(
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

    private fun punch(
        id: String,
        siCode: Int,
        type: SIRecordType,
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
                punchType = type,
                order = 0,
                punchStatus = status,
                splitSeconds = splitSeconds
            ),
            alias = alias
        )
}
