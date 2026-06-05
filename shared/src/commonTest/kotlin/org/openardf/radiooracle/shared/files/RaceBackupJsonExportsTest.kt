package org.openardf.radiooracle.shared.files

import kotlinx.serialization.json.Json
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
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RaceBackupJsonExportsTest {
    @Test
    fun exportsAndroidShapedRaceBackupDocument() {
        val document = Json.parseToJsonElement(RaceBackupJsonExports.race(raceData()))
            .jsonObject
        val unmatched = document["unmatched_results"]!!.jsonArray.single().jsonObject
        val punch = unmatched["punches"]!!.jsonArray.single().jsonObject

        assertEquals("Backup Race", document["race_name"]!!.jsonPrimitive.content)
        assertEquals("2026-06-01T09:00:00", document["race_start"]!!.jsonPrimitive.content)
        assertEquals("CLASSIC", document["race_type"]!!.jsonPrimitive.content)
        assertEquals("M80", document["race_band"]!!.jsonPrimitive.content)
        assertEquals("PRACTICE", document["race_level"]!!.jsonPrimitive.content)
        assertEquals("120", document["race_time_limit"]!!.jsonPrimitive.content)
        assertEquals("api-key", document["race_api_key"]!!.jsonPrimitive.content)
        assertEquals(0, document["categories"]!!.jsonArray.size)
        assertEquals(0, document["competitors"]!!.jsonArray.size)

        assertEquals(654321, unmatched["si_number"]!!.jsonPrimitive.int)
        assertEquals("2026-06-01T10:00:00", unmatched["start_time"]!!.jsonPrimitive.content)
        assertEquals("2026-06-01T10:25:00", unmatched["finish_time"]!!.jsonPrimitive.content)
        assertEquals("25:00", unmatched["run_time"]!!.jsonPrimitive.content)
        assertEquals("0", punch["code"]!!.jsonPrimitive.content)
        assertEquals("FINISH", punch["control_type"]!!.jsonPrimitive.content)
        assertEquals("OK", punch["punch_status"]!!.jsonPrimitive.content)
    }

    @Test
    fun doesNotExportDesktopProtectedIdealOrderToAndroidBackup() {
        val encryptedIdealOrder = "radio-oracle-protected-order"
        val exported = RaceBackupJsonExports.race(
            raceData().copy(
                categories = listOf(
                    EventCategoryData(
                        category = EventCategory(
                            id = "category",
                            raceId = "race",
                            name = "M21",
                            isMan = true,
                            maxAge = 99,
                            lengthMeters = 6_000,
                            climbMeters = 200,
                            order = 0,
                            differentProperties = false,
                            raceType = null,
                            raceBand = null,
                            timeLimitSeconds = null,
                            controlPointsString = "31 32 33",
                            encryptedIdealOrder = encryptedIdealOrder
                        ),
                        controlPoints = listOf(
                            EventControlPoint(
                                id = "control-31",
                                categoryId = "category",
                                siCode = 31,
                                type = ControlPointType.CONTROL,
                                order = 0
                            )
                        ),
                        competitors = emptyList()
                    )
                )
            )
        )

        assertFalse(exported.contains("encryptedIdealOrder"))
        assertFalse(exported.contains(encryptedIdealOrder))
    }

    private fun raceData(): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Backup Race",
                apiKey = "api-key",
                startDateTimeIso = "2026-06-01T09:00:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = listOf(
                EventReadoutData(
                    result = EventResult(
                        id = "result",
                        raceId = "race",
                        competitorId = null,
                        siNumber = 654321,
                        cardType = 10,
                        checkTimeSeconds = null,
                        startTimeSeconds = 36_000,
                        finishTimeSeconds = 37_500,
                        readoutDateTimeIso = "2026-06-01T10:26:00",
                        automaticStatus = true,
                        resultStatus = ResultStatus.NO_RANKING,
                        points = 0,
                        runTimeSeconds = 1_500,
                        modified = false,
                        sent = false
                    ),
                    punches = listOf(
                        EventAliasPunch(
                            punch = EventPunch(
                                id = "finish",
                                raceId = "race",
                                resultId = "result",
                                cardNumber = 654321,
                                siCode = 0,
                                siTimeSeconds = 37_500,
                                originalSiTimeSeconds = 37_500,
                                punchType = SIRecordType.FINISH,
                                order = 0,
                                punchStatus = PunchStatus.VALID,
                                splitSeconds = 1_500
                            ),
                            alias = null
                        )
                    )
                )
            )
        )
}
