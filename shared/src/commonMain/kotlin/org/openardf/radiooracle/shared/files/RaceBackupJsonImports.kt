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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.domain.resultStatusFromCode
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventPunch
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.event.EventStartNumbers
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Imports Android `.ardfjs` full race backup JSON into the shared desktop project model. */
object RaceBackupJsonImports {
    private val json = Json { ignoreUnknownKeys = true }

    fun projectFile(text: String, idFactory: () -> String): EventProjectFile {
        val root = json.parseToJsonElement(text).jsonObject
        val raceId = idFactory()
        val raceStart = root.string("race_start") ?: "1970-01-01T00:00:00"
        val race = EventRace(
            id = raceId,
            name = root.requiredString("race_name"),
            apiKey = root.string("race_api_key") ?: "",
            startDateTimeIso = raceStart,
            raceType = root.enumValue("race_type", RaceType.CLASSIC),
            raceLevel = root.enumValue("race_level", RaceLevel.PRACTICE),
            raceBand = root.enumValue("race_band", RaceBand.M80),
            timeLimitSeconds = (root.longValue("race_time_limit") ?: 120L) * 60L,
            combinedNationalRegionalAwards = root.boolean("combined_national_regional_awards") ?: false
        )

        val categoryData = root.array("categories").mapIndexed { index, element ->
            parseCategory(element.jsonObject, raceId, index, idFactory)
        }
        val categoriesByName = categoryData.associateBy { it.category.name }
        val categoryCompetitors = categoryData.associate { it.category.id to mutableListOf<EventCompetitor>() }
        val aliases = root.array("aliases").map { element ->
            val alias = element.jsonObject
            EventAlias(
                id = idFactory(),
                raceId = raceId,
                siCode = alias.requiredInt("alias_si_code"),
                name = alias.requiredString("alias_name")
            )
        }

        val competitorData = root.array("competitors").map { element ->
            val competitorJson = element.jsonObject
            val category = categoriesByName[competitorJson.string("competitor_category") ?: ""]

            val competitor = EventCompetitor(
                id = idFactory(),
                raceId = raceId,
                categoryId = category?.category?.id,
                firstName = competitorJson.string("first_name") ?: "",
                lastName = competitorJson.string("last_name") ?: "",
                club = competitorJson.string("competitor_club") ?: "",
                index = competitorJson.string("competitor_index") ?: "",
                isMan = competitorJson.boolean("competitor_gender") ?: true,
                birthYear = competitorJson.int("birth_year"),
                siNumber = competitorJson.int("si_number"),
                siRent = competitorJson.boolean("si_rent") ?: false,
                startNumber = competitorJson.int("start_number"),
                drawnStartTimeSeconds = competitorJson.durationSeconds("competitor_start_time"),
                bibNumber = competitorJson.string("bib_number") ?: "",
                callSign = competitorJson.string("call_sign")?.ifBlank { "SWL" } ?: "SWL",
                email = competitorJson.string("email") ?: "",
                cellPhone = competitorJson.string("cell_phone") ?: "",
                usaChampEligible = competitorJson.boolean("usa_champ_eligible"),
                region2ChampEligible = competitorJson.boolean("region2_champ_eligible")
            )
            category?.let { categoryCompetitors[it.category.id]?.add(competitor) }

            EventCompetitorData(
                competitorCategory = EventCompetitorCategory(competitor, category?.category),
                readoutData = competitorJson["result"]?.jsonObjectOrNull()
                    ?.let { parseMatchedReadout(it, raceId, competitor.id, competitor.siNumber, raceStart, idFactory) }
            )
        }

        val categoriesWithCompetitors = categoryData.map { data ->
            data.copy(competitors = categoryCompetitors[data.category.id]?.toList() ?: emptyList())
        }
        val unmatchedReadouts = root.array("unmatched_results").map { element ->
            parseUnmatchedReadout(element.jsonObject, raceId, raceStart, idFactory)
        }

        return EventStartNumbers.assignFromDrawnStartTimes(EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = categoriesWithCompetitors,
                aliases = aliases,
                competitorData = competitorData,
                unmatchedReadoutData = unmatchedReadouts,
                controls = org.openardf.radiooracle.shared.event.EventControlCatalog.deriveFromRaceData(
                    EventRaceData(
                        race = race,
                        categories = categoriesWithCompetitors,
                        aliases = aliases,
                        competitorData = competitorData,
                        unmatchedReadoutData = unmatchedReadouts
                    )
                )
            )
        ))
    }

    private fun parseCategory(
        categoryJson: JsonObject,
        raceId: String,
        order: Int,
        idFactory: () -> String
    ): EventCategoryData {
        val categoryId = idFactory()
        val controlPoints = categoryJson.array("category_control_points").mapIndexed { index, element ->
            val controlPointJson = element.jsonObject
            EventControlPoint(
                id = idFactory(),
                categoryId = categoryId,
                controlId = org.openardf.radiooracle.shared.event.EventControlCatalog.stableId(
                    label = controlPointJson.requiredInt("si_code").toString(),
                    siCode = controlPointJson.requiredInt("si_code"),
                    type = controlPointJson.enumValue("control_type", ControlPointType.CONTROL)
                ),
                siCode = controlPointJson.requiredInt("si_code"),
                type = controlPointJson.enumValue("control_type", ControlPointType.CONTROL),
                order = index
            )
        }
        val categoryName = categoryJson.requiredString("category_name")
        val category = EventCategory(
            id = categoryId,
            raceId = raceId,
            name = categoryName,
            isMan = StandardCategoryRules.inferIsManFromName(categoryName)
                ?: (categoryJson.boolean("category_gender") ?: true),
            maxAge = categoryJson.int("category_max_age"),
            lengthMeters = categoryJson.int("category_length") ?: 0,
            climbMeters = categoryJson.int("category_climb") ?: 0,
            order = order,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = controlPoints.joinToString(" ") { it.controlPointToken() }
        )
        return EventCategoryData(
            category = category,
            controlPoints = controlPoints,
            competitors = emptyList(),
            publicControlIds = controlPoints.sortedBy { it.siCode }.map { it.controlId }
        )
    }

    private fun parseMatchedReadout(
        resultJson: JsonObject,
        raceId: String,
        competitorId: String,
        siNumber: Int?,
        raceStart: String,
        idFactory: () -> String
    ): EventReadoutData {
        val resultId = idFactory()
        val startTimeSeconds = resultJson.dateTimeSeconds("start_time")
        val punches = parsePunches(resultJson, raceId, resultId, siNumber, startTimeSeconds ?: 0L, idFactory)
        return EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = raceId,
                competitorId = competitorId,
                siNumber = siNumber,
                cardType = 0,
                checkTimeSeconds = resultJson.dateTimeSeconds("check_time"),
                startTimeSeconds = startTimeSeconds,
                finishTimeSeconds = resultJson.dateTimeSeconds("finish_time"),
                readoutDateTimeIso = resultJson.string("readoutTime") ?: raceStart,
                automaticStatus = resultJson.boolean("automatic_status") ?: true,
                resultStatus = resultJson.resultStatus("result_status"),
                points = resultJson.int("punch_count") ?: punches.size,
                runTimeSeconds = resultJson.durationSeconds("run_time") ?: 0,
                modified = resultJson.boolean("modified") ?: false,
                sent = false,
                place = resultJson.int("place") ?: 0
            ),
            punches = punches
        )
    }

    private fun parseUnmatchedReadout(
        resultJson: JsonObject,
        raceId: String,
        raceStart: String,
        idFactory: () -> String
    ): EventReadoutData {
        val resultId = idFactory()
        val siNumber = resultJson.int("si_number")
        val startTimeSeconds = resultJson.dateTimeSeconds("start_time") ?: 0L
        val punches = parsePunches(resultJson, raceId, resultId, siNumber, startTimeSeconds, idFactory)
        return EventReadoutData(
            result = EventResult(
                id = resultId,
                raceId = raceId,
                competitorId = null,
                siNumber = siNumber,
                cardType = 0,
                checkTimeSeconds = resultJson.dateTimeSeconds("check_time"),
                startTimeSeconds = startTimeSeconds,
                finishTimeSeconds = resultJson.dateTimeSeconds("finish_time"),
                readoutDateTimeIso = raceStart,
                automaticStatus = false,
                resultStatus = ResultStatus.NO_RANKING,
                points = punches.size,
                runTimeSeconds = resultJson.durationSeconds("run_time") ?: 0,
                modified = false,
                sent = false
            ),
            punches = punches
        )
    }

    private fun parsePunches(
        resultJson: JsonObject,
        raceId: String,
        resultId: String,
        siNumber: Int?,
        startTimeSeconds: Long,
        idFactory: () -> String
    ): List<EventAliasPunch> {
        var elapsedSeconds = 0L
        return resultJson.array("punches").mapIndexed { index, element ->
            val punchJson = element.jsonObject
            val punchType = punchJson.enumValue("control_type", SIRecordType.CONTROL)
            val splitSeconds = punchJson.durationSeconds("split_time") ?: 0
            elapsedSeconds += splitSeconds
            val siCode = if (punchType == SIRecordType.CONTROL) {
                punchJson.int("si_code") ?: punchJson.string("code")?.toIntOrNull() ?: 0
            } else {
                0
            }
            EventAliasPunch(
                punch = EventPunch(
                    id = idFactory(),
                    raceId = raceId,
                    resultId = resultId,
                    cardNumber = siNumber,
                    siCode = siCode,
                    siTimeSeconds = startTimeSeconds + elapsedSeconds,
                    originalSiTimeSeconds = startTimeSeconds + elapsedSeconds,
                    punchType = punchType,
                    order = index,
                    punchStatus = punchJson.punchStatus("punch_status"),
                    splitSeconds = splitSeconds
                ),
                alias = null
            )
        }
    }

    private fun EventControlPoint.controlPointToken(): String =
        when (type) {
            ControlPointType.CONTROL -> siCode.toString()
            ControlPointType.BEACON -> "${siCode}B"
            ControlPointType.SEPARATOR -> "${siCode}S"
        }

    private fun JsonObject.requiredString(name: String): String =
        requireNotNull(string(name)) { "Missing required field: $name" }

    private fun JsonObject.requiredInt(name: String): Int =
        requireNotNull(int(name)) { "Missing required field: $name" }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitiveOrNull()?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitiveOrNull()?.intOrNull

    private fun JsonObject.longValue(name: String): Long? {
        val primitive = this[name]?.jsonPrimitiveOrNull() ?: return null
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitiveOrNull()?.booleanOrNull

    private fun JsonObject.array(name: String): List<JsonElement> =
        this[name]?.jsonArrayOrNull()?.toList() ?: emptyList()

    private fun JsonObject.durationSeconds(name: String): Long? {
        val primitive = this[name]?.jsonPrimitiveOrNull() ?: return null
        val value = primitive.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return if (value.contains(":")) {
            DurationFormatter.minuteStringToSeconds(value)
        } else {
            value.toLongOrNull()?.times(60)
        }
    }

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String, default: T): T =
        enumValueOrNull<T>(name) ?: default

    private inline fun <reified T : Enum<T>> JsonObject.enumValueOrNull(name: String): T? =
        string(name)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

    private fun JsonObject.resultStatus(name: String): ResultStatus =
        resultStatusFromCode(string(name), blankAsOk = true)

    private fun JsonObject.punchStatus(name: String): PunchStatus =
        when (string(name)) {
            "OK" -> PunchStatus.VALID
            "MP" -> PunchStatus.INVALID
            "DP" -> PunchStatus.DUPLICATE
            "AP" -> PunchStatus.UNKNOWN
            else -> PunchStatus.VALID
        }

    private fun JsonObject.dateTimeSeconds(name: String): Long? =
        string(name)?.substringAfter('T', "")?.ifBlank { string(name)?.substringAfter(' ', "") }
            ?.takeIf { it.isNotBlank() }
            ?.split(":")
            ?.takeIf { it.size >= 2 }
            ?.let { parts ->
                val hours = parts[0].toLongOrNull() ?: return@let null
                val minutes = parts[1].toLongOrNull() ?: return@let null
                val seconds = parts.getOrNull(2)?.toLongOrNull() ?: 0
                hours * 3600 + minutes * 60 + seconds
            }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun JsonElement.jsonArrayOrNull() =
        runCatching { jsonArray }.getOrNull()

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? =
        this as? JsonPrimitive
}
