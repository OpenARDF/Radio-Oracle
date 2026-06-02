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
            timeLimitSeconds = (root.longValue("race_time_limit") ?: 120L) * 60L
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

        var highestStartNumber = root.array("competitors")
            .maxOfOrNull { it.jsonObject.int("start_number") ?: 0 } ?: 0
        val competitorData = root.array("competitors").map { element ->
            val competitorJson = element.jsonObject
            val category = categoriesByName[competitorJson.string("competitor_category") ?: ""]
            var startNumber = competitorJson.int("start_number") ?: 0
            if (startNumber == 0) {
                highestStartNumber += 1
                startNumber = highestStartNumber
            }

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
                startNumber = startNumber,
                drawnStartTimeSeconds = competitorJson.durationSeconds("competitor_start_time")
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

        return EventProjectFile(
            raceData = EventRaceData(
                race = race,
                categories = categoriesWithCompetitors,
                aliases = aliases,
                competitorData = competitorData,
                unmatchedReadoutData = unmatchedReadouts
            )
        )
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
                siCode = controlPointJson.requiredInt("si_code"),
                type = controlPointJson.enumValue("control_type", ControlPointType.CONTROL),
                order = index
            )
        }
        val category = EventCategory(
            id = categoryId,
            raceId = raceId,
            name = categoryJson.requiredString("category_name"),
            isMan = categoryJson.boolean("category_gender") ?: true,
            maxAge = categoryJson.int("category_max_age"),
            lengthMeters = categoryJson.int("category_length") ?: 0,
            climbMeters = categoryJson.int("category_climb") ?: 0,
            order = order,
            differentProperties = categoryJson.boolean("category_different_properties") ?: false,
            raceType = categoryJson.enumValueOrNull<RaceType>("category_race_type"),
            raceBand = categoryJson.enumValueOrNull<RaceBand>("category_band")
                ?: categoryJson.enumValueOrNull<RaceBand>("category_race_band"),
            timeLimitSeconds = categoryJson.durationSeconds("category_time_limit"),
            controlPointsString = controlPoints.joinToString(" ") { it.controlPointToken() }
        )
        return EventCategoryData(category, controlPoints, emptyList())
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
        when (string(name)) {
            "OK" -> ResultStatus.OK
            "MP" -> ResultStatus.MISPUNCHED
            "NR" -> ResultStatus.NO_RANKING
            "DSQ" -> ResultStatus.DISQUALIFIED
            "DNS" -> ResultStatus.DID_NOT_START
            "DNF" -> ResultStatus.DID_NOT_FINISH
            "OVT" -> ResultStatus.OVER_TIME_LIMIT
            "UNF" -> ResultStatus.UNOFFICIAL
            "ERR" -> ResultStatus.ERROR
            else -> ResultStatus.NO_RANKING
        }

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
