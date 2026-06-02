package org.openardf.radiooracle.shared.files

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAlias
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Android-shaped final-result JSON export containing categories, aliases, and competitors. */
object FinalResultJsonExports {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun results(raceData: EventRaceData): String =
        json.encodeToString(resultDocument(raceData))

    fun resultDocument(raceData: EventRaceData): FinalResultsJson =
        FinalResultsJson(
            categories = raceData.categories
                .map { it.toFinalCategory() },
            aliases = raceData.aliases
                .map { it.toFinalAlias() },
            competitors = raceData.competitorData
                .map { it.toFinalCompetitor(raceData) }
        )

    private fun EventCategoryData.toFinalCategory(): FinalCategoryJson =
        FinalCategoryJson(
            categoryName = category.name,
            categoryGender = category.isMan,
            categoryMaxAge = category.maxAge,
            categoryLength = category.lengthMeters,
            categoryClimb = category.climbMeters,
            categoryControlPoints = controlPoints
                .sortedBy { it.order }
                .map { it.toFinalControlPoint() },
            categoryDifferentProperties = category.differentProperties,
            categoryRaceType = category.raceType,
            categoryTimeLimit = category.timeLimitSeconds
                ?.let { DurationFormatter.secondsToFormattedString(it, useMinutes = true) }
                ?: "",
            categoryBand = category.raceBand
        )

    private fun EventControlPoint.toFinalControlPoint(): FinalControlPointJson =
        FinalControlPointJson(siCode = siCode, controlType = type)

    private fun EventAlias.toFinalAlias(): FinalAliasJson =
        FinalAliasJson(aliasSiCode = siCode, aliasName = name)

    private fun EventCompetitorData.toFinalCompetitor(raceData: EventRaceData): FinalCompetitorJson {
        val competitor = competitorCategory.competitor
        val result = readoutData?.takeUnless { it.result.resultStatus == ResultStatus.ERROR }
        return FinalCompetitorJson(
            firstName = competitor.firstName,
            lastName = competitor.lastName,
            competitorClub = competitor.club,
            competitorCategory = competitorCategory.category?.name ?: "",
            competitorIndex = competitor.index.takeIf { it.isNotBlank() },
            competitorGender = competitor.isMan,
            birthYear = competitor.birthYear,
            siNumber = competitor.siNumber,
            siRent = competitor.siRent,
            startNumber = competitor.startNumber,
            competitorStartTime = competitor.drawnStartTimeSeconds
                ?.let { DurationFormatter.secondsToFormattedString(it, useMinutes = true) }
                ?: "",
            result = result?.toFinalResult(raceData)
        )
    }

    private fun EventReadoutData.toFinalResult(raceData: EventRaceData): FinalResultJson =
        FinalResultJson(
            checkTime = result.checkTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            startTime = result.startTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            finishTime = result.finishTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            runTime = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = true),
            place = result.place,
            readoutTime = result.readoutDateTimeIso,
            modified = result.modified,
            punchCount = result.points,
            resultStatus = result.resultStatus.toFinalResultStatus(),
            automaticStatus = result.automaticStatus,
            punches = punches
                .filter { it.punch.punchType != SIRecordType.START }
                .map { it.toFinalPunch() }
        )

    private fun EventAliasPunch.toFinalPunch(): FinalPunchJson {
        val rawCode = alias?.name ?: punch.siCode.toString()
        val code = if (punch.punchType == SIRecordType.FINISH && rawCode == "0") "F" else rawCode
        return FinalPunchJson(
            code = code,
            siCode = punch.siCode,
            controlType = punch.punchType.name,
            punchStatus = punch.punchStatus.toFinalPunchStatus(),
            splitTime = DurationFormatter.secondsToFormattedString(punch.splitSeconds, useMinutes = true)
        )
    }

    private fun Long.toRaceDateTime(raceStartIso: String): String {
        val date = raceStartIso.substringBefore('T').ifBlank { raceStartIso.substringBefore(' ') }
        val secondsInDay = 24 * 60 * 60
        val normalized = ((this % secondsInDay) + secondsInDay) % secondsInDay
        val hours = normalized / 3600
        val minutes = (normalized % 3600) / 60
        val seconds = normalized % 60
        return "$date" + "T" + "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun ResultStatus.toFinalResultStatus(): String =
        when (this) {
            ResultStatus.OK -> "OK"
            ResultStatus.MISPUNCHED -> "MP"
            ResultStatus.NO_RANKING -> "NR"
            ResultStatus.DISQUALIFIED -> "DSQ"
            ResultStatus.DID_NOT_START -> "DNS"
            ResultStatus.DID_NOT_FINISH -> "DNF"
            ResultStatus.OVER_TIME_LIMIT -> "OVT"
            ResultStatus.UNOFFICIAL -> "UNF"
            ResultStatus.ERROR -> "ERR"
        }

    private fun PunchStatus.toFinalPunchStatus(): String =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "DP"
            PunchStatus.UNKNOWN -> "AP"
        }

    @Serializable
    data class FinalResultsJson(
        val categories: List<FinalCategoryJson>,
        val aliases: List<FinalAliasJson>,
        val competitors: List<FinalCompetitorJson>
    )

    @Serializable
    data class FinalCategoryJson(
        @SerialName("category_name") val categoryName: String,
        @SerialName("category_gender") val categoryGender: Boolean,
        @SerialName("category_max_age") val categoryMaxAge: Int? = null,
        @SerialName("category_length") val categoryLength: Int,
        @SerialName("category_climb") val categoryClimb: Int,
        @SerialName("category_control_points") val categoryControlPoints: List<FinalControlPointJson>,
        @SerialName("category_different_properties") val categoryDifferentProperties: Boolean,
        @SerialName("category_race_type") val categoryRaceType: RaceType? = null,
        @SerialName("category_time_limit") val categoryTimeLimit: String,
        @SerialName("category_band") val categoryBand: RaceBand? = null
    )

    @Serializable
    data class FinalControlPointJson(
        @SerialName("si_code") val siCode: Int,
        @SerialName("control_type") val controlType: ControlPointType
    )

    @Serializable
    data class FinalAliasJson(
        @SerialName("alias_si_code") val aliasSiCode: Int,
        @SerialName("alias_name") val aliasName: String
    )

    @Serializable
    data class FinalCompetitorJson(
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String,
        @SerialName("competitor_club") val competitorClub: String,
        @SerialName("competitor_category") val competitorCategory: String,
        @SerialName("competitor_index") val competitorIndex: String? = null,
        @SerialName("competitor_gender") val competitorGender: Boolean,
        @SerialName("birth_year") val birthYear: Int? = null,
        @SerialName("si_number") val siNumber: Int? = null,
        @SerialName("si_rent") val siRent: Boolean,
        @SerialName("start_number") val startNumber: Int,
        @SerialName("competitor_start_time") val competitorStartTime: String,
        val result: FinalResultJson? = null
    )

    @Serializable
    data class FinalResultJson(
        @SerialName("check_time") val checkTime: String? = null,
        @SerialName("start_time") val startTime: String? = null,
        @SerialName("finish_time") val finishTime: String? = null,
        @SerialName("run_time") val runTime: String,
        val place: Int,
        val readoutTime: String,
        val modified: Boolean,
        @SerialName("punch_count") val punchCount: Int,
        @SerialName("result_status") val resultStatus: String,
        @SerialName("automatic_status") val automaticStatus: Boolean,
        val punches: List<FinalPunchJson>
    )

    @Serializable
    data class FinalPunchJson(
        val code: String,
        @SerialName("si_code") val siCode: Int,
        @SerialName("control_type") val controlType: String,
        @SerialName("punch_status") val punchStatus: String,
        @SerialName("split_time") val splitTime: String
    )
}
