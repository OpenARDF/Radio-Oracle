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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.competitionCategories
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Standards-facing ARDF JSON export helpers. */
object ArdfJsonExports {
    private const val FORMAT_VERSION = 1
    private const val METERS_PER_KILOMETER = 1000.0

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Exports a full ARDF JSON event document containing the current Radio-Oracle race. */
    fun event(
        projectName: String,
        raceData: EventRaceData,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): String =
        json.encodeToString(eventDocument(projectName, raceData, protectedCourseInfoByCategoryId))

    fun eventDocument(
        projectName: String,
        raceData: EventRaceData,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>? = null
    ): ArdfEventDocument =
        ArdfEventDocument(
            formatVersion = FORMAT_VERSION,
            eventName = projectName,
            races = listOf(raceData.toArdfRace(protectedCourseInfoByCategoryId))
        )

    private fun EventRaceData.toArdfRace(
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ): ArdfRace {
        val controlsById = controls.associateBy { it.id }
        return ArdfRace(
            raceName = race.name,
            raceStart = race.startDateTimeIso,
            raceType = race.raceType.toArdfRaceType(),
            raceBand = race.raceBand.name,
            raceLevel = race.raceLevel.name,
            raceTimeLimit = race.timeLimitSeconds.toMinutes(),
            raceApiKey = race.apiKey.takeIf { it.isNotBlank() },
            categories = competitionCategories()
                .sortedWith(compareBy({ it.category.order }, { it.category.name }))
                .map { it.toArdfCategory(race, controlsById, protectedCourseInfoByCategoryId) },
            aliases = FinalResultJsonExports.androidAliases(this)
                .map { it.toArdfAlias() },
            competitors = competitorData
                .sortedWith(compareBy({ it.competitorCategory.competitor.startNumber }, { it.competitorCategory.competitor.fullName() }))
                .map { it.toArdfCompetitor(this) },
            unmatchedResults = unmatchedReadoutData
                .map { it.toArdfUnmatchedResult(this) }
        )
    }

    private fun EventCategoryData.toArdfCategory(
        race: org.openardf.radiooracle.shared.event.EventRace,
        controlsById: Map<String, EventControl>,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>?
    ): ArdfCategory {
        val protectedCourseInfo = protectedCourseInfoByCategoryId?.get(category.id)
        return ArdfCategory(
            categoryName = category.name,
            categoryGender = category.isMan,
            categoryMaxAge = category.maxAge ?: EventCsvFormat.Category.OPEN_MAX_AGE,
            categoryLength = if (protectedCourseInfoByCategoryId == null) {
                category.lengthMeters / METERS_PER_KILOMETER
            } else {
                (protectedCourseInfo?.effectiveLengthMeters() ?: 0) / METERS_PER_KILOMETER
            },
            categoryClimb = if (protectedCourseInfoByCategoryId == null) {
                category.climbMeters
            } else {
                protectedCourseInfo?.climbMeters ?: 0
            },
            categoryControlPoints = controlPoints
                .sortedBy { it.order }
                .map { it.toArdfControlPoint(controlsById) },
            categoryDifferentProperties = false,
            categoryRaceType = null,
            categoryTimeLimit = null,
            categoryBand = null
        )
    }

    private fun EventControlPoint.toArdfControlPoint(controlsById: Map<String, EventControl>): ArdfControlPoint {
        val control = controlsById[controlId]
        return ArdfControlPoint(
            siCode = control?.siCode ?: siCode,
            controlType = (control?.type ?: type).toArdfControlType()
        )
    }

    private fun FinalResultJsonExports.FinalAliasJson.toArdfAlias(): ArdfAlias =
        ArdfAlias(aliasSiCode = aliasSiCode, aliasName = aliasName)

    private fun EventCompetitorData.toArdfCompetitor(raceData: EventRaceData): ArdfCompetitor {
        val competitor = competitorCategory.competitor
        val resultCategoryId = readoutData?.result?.categoryId ?: competitor.categoryId
        return ArdfCompetitor(
            firstName = competitor.firstName,
            lastName = competitor.lastName,
            competitorClub = competitor.club.takeIf { it.isNotBlank() },
            competitorCategory = raceData.categoryNameFor(resultCategoryId),
            competitorIndex = competitor.index.takeIf { it.isNotBlank() },
            competitorGender = competitor.isMan,
            birthYear = competitor.birthYear,
            siNumber = competitor.siNumber,
            siRent = competitor.siRent,
            startNumber = competitor.startNumber,
            competitorStartTime = competitor.drawnStartTimeSeconds?.let { DurationFormatter.secondsToFormattedString(it, useMinutes = true) },
            result = readoutData?.toArdfResult(raceData, resultCategoryId)
        )
    }

    private fun EventReadoutData.toArdfResult(raceData: EventRaceData, categoryId: String?): ArdfResult {
        val punchLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        return ArdfResult(
            runTime = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = true),
            resultStatus = result.resultStatus.toArdfResultStatus(),
            place = result.place,
            modified = result.modified,
            punchCount = result.points,
            punches = punches
                .map { aliasPunch ->
                    val punch = aliasPunch.punch
                    ArdfPunch(
                        code = punchLabelsByCode[punch.siCode] ?: aliasPunch.alias?.name ?: punch.siCode.toString(),
                        controlType = punch.toArdfControlType(raceData, categoryId),
                        splitTime = punch.splitSeconds.takeIf { it > 0 }?.let {
                            DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                        },
                        punchStatus = punch.punchStatus.toArdfPunchStatus()
                    )
                }
        )
    }

    private fun EventReadoutData.toArdfUnmatchedResult(raceData: EventRaceData): ArdfUnmatchedResult {
        val punchLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        return ArdfUnmatchedResult(
            siNumber = result.siNumber,
            runTime = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = true),
            punches = punches.map { aliasPunch ->
                val punch = aliasPunch.punch
                ArdfPunch(
                    code = punchLabelsByCode[punch.siCode] ?: aliasPunch.alias?.name ?: punch.siCode.toString(),
                    siCode = punch.siCode,
                    controlType = punch.toArdfControlType(raceData, categoryId = null),
                    splitTime = punch.splitSeconds.takeIf { it > 0 }?.let {
                        DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                    }
                )
            }
        )
    }

    private fun org.openardf.radiooracle.shared.event.EventPunch.toArdfControlType(
        raceData: EventRaceData,
        categoryId: String?
    ): String =
        when (punchType) {
            SIRecordType.FINISH -> "FINISH"
            SIRecordType.CONTROL -> raceData.controlPointTypeFor(categoryId, siCode)?.toArdfControlType() ?: "CONTROL"
            SIRecordType.CHECK, SIRecordType.START -> "CONTROL"
        }

    private fun EventRaceData.controlPointTypeFor(categoryId: String?, siCode: Int): ControlPointType? =
        categoryId?.let { id ->
            val controlsById = controls.associateBy { it.id }
            categories
                .firstOrNull { it.category.id == id }
                ?.controlPoints
                ?.firstOrNull { controlPoint -> (controlsById[controlPoint.controlId]?.siCode ?: controlPoint.siCode) == siCode }
                ?.let { controlPoint -> controlsById[controlPoint.controlId]?.type ?: controlPoint.type }
        }

    private fun EventRaceData.categoryNameFor(categoryId: String?): String =
        categoryId?.let { id -> categories.firstOrNull { it.category.id == id }?.category?.name } ?: ""

    private fun RaceType.toArdfRaceType(): String = name

    private fun ControlPointType.toArdfControlType(): String =
        when (this) {
            ControlPointType.CONTROL -> "CONTROL"
            ControlPointType.BEACON -> "BEACON"
            ControlPointType.SEPARATOR -> "SEPARATOR"
        }

    private fun ResultStatus.toArdfResultStatus(): String? =
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

    private fun PunchStatus.toArdfPunchStatus(): String? =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "DP"
            PunchStatus.UNKNOWN -> "AP"
        }

    private fun Long.toMinutes(): Int =
        (this / 60).toInt()

    @Serializable
    data class ArdfEventDocument(
        @SerialName("format_version") val formatVersion: Int,
        @SerialName("event_name") val eventName: String,
        val races: List<ArdfRace>
    )

    @Serializable
    data class ArdfRace(
        @SerialName("race_name") val raceName: String,
        @SerialName("race_start") val raceStart: String,
        @SerialName("race_type") val raceType: String,
        @SerialName("race_band") val raceBand: String,
        @SerialName("race_level") val raceLevel: String,
        @SerialName("race_time_limit") val raceTimeLimit: Int,
        @SerialName("race_api_key") val raceApiKey: String? = null,
        val categories: List<ArdfCategory> = emptyList(),
        val aliases: List<ArdfAlias> = emptyList(),
        val competitors: List<ArdfCompetitor> = emptyList(),
        @SerialName("unmatched_results") val unmatchedResults: List<ArdfUnmatchedResult> = emptyList()
    )

    @Serializable
    data class ArdfCategory(
        @SerialName("category_name") val categoryName: String,
        @SerialName("category_gender") val categoryGender: Boolean,
        @SerialName("category_max_age") val categoryMaxAge: Int,
        @SerialName("category_length") val categoryLength: Double,
        @SerialName("category_climb") val categoryClimb: Int,
        @SerialName("category_control_points") val categoryControlPoints: List<ArdfControlPoint>,
        @SerialName("category_different_properties") val categoryDifferentProperties: Boolean = false,
        @SerialName("category_race_type") val categoryRaceType: String? = null,
        @SerialName("category_time_limit") val categoryTimeLimit: Int? = null,
        @SerialName("category_band") val categoryBand: String? = null
    )

    @Serializable
    data class ArdfControlPoint(
        @SerialName("si_code") val siCode: Int,
        @SerialName("control_type") val controlType: String
    )

    @Serializable
    data class ArdfAlias(
        @SerialName("alias_si_code") val aliasSiCode: Int,
        @SerialName("alias_name") val aliasName: String
    )

    @Serializable
    data class ArdfCompetitor(
        @SerialName("first_name") val firstName: String,
        @SerialName("last_name") val lastName: String,
        @SerialName("competitor_club") val competitorClub: String? = null,
        @SerialName("competitor_category") val competitorCategory: String,
        @SerialName("competitor_index") val competitorIndex: String? = null,
        @SerialName("competitor_gender") val competitorGender: Boolean,
        @SerialName("birth_year") val birthYear: Int? = null,
        @SerialName("si_number") val siNumber: Int? = null,
        @SerialName("si_rent") val siRent: Boolean = false,
        @SerialName("start_number") val startNumber: Int? = null,
        @SerialName("competitor_start_time") val competitorStartTime: String? = null,
        val result: ArdfResult? = null
    )

    @Serializable
    data class ArdfResult(
        @SerialName("run_time") val runTime: String,
        @SerialName("result_status") val resultStatus: String? = null,
        val place: Int,
        val modified: Boolean,
        @SerialName("punch_count") val punchCount: Int,
        val punches: List<ArdfPunch>
    )

    @Serializable
    data class ArdfUnmatchedResult(
        @SerialName("si_number") val siNumber: Int? = null,
        @SerialName("run_time") val runTime: String,
        val punches: List<ArdfPunch>
    )

    @Serializable
    data class ArdfPunch(
        val code: String,
        @SerialName("si_code") val siCode: Int? = null,
        @SerialName("control_type") val controlType: String,
        @SerialName("split_time") val splitTime: String? = null,
        @SerialName("punch_status") val punchStatus: String? = null
    )
}
