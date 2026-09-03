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
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Android-shaped full race backup JSON export, compatible with Android's race export action. */
object RaceBackupJsonExports {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun race(raceData: EventRaceData): String =
        json.encodeToString(raceDocument(raceData))

    fun raceDocument(raceData: EventRaceData): RaceBackupJson {
        val finalResults = FinalResultJsonExports.resultDocument(
            raceData,
            includeCategoriesWithoutResults = true
        )
        return RaceBackupJson(
            raceName = raceData.race.name,
            raceStart = raceData.race.startDateTimeIso,
            raceType = raceData.race.raceType,
            raceBand = raceData.race.raceBand,
            raceLevel = raceData.race.raceLevel,
            raceTimeLimit = (raceData.race.timeLimitSeconds / 60).toString(),
            raceApiKey = raceData.race.apiKey,
            combinedNationalRegionalAwards = raceData.race.combinedNationalRegionalAwards,
            categories = finalResults.categories,
            aliases = finalResults.aliases,
            competitors = finalResults.competitors,
            unmatchedResults = raceData.unmatchedReadoutData.mapNotNull { it.toRaceBackupUnmatched(raceData) }
        )
    }

    private fun EventReadoutData.toRaceBackupUnmatched(raceData: EventRaceData): RaceBackupUnmatchedResultJson? {
        val startTime = result.startTimeSeconds ?: return null
        val finishTime = result.finishTimeSeconds ?: return null
        val punchLabelsByCode = FinalResultJsonExports.controlLabelsByCode(raceData)
        return RaceBackupUnmatchedResultJson(
            siNumber = result.siNumber,
            checkTime = result.checkTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            startTime = startTime.toRaceDateTime(raceData.race.startDateTimeIso),
            finishTime = finishTime.toRaceDateTime(raceData.race.startDateTimeIso),
            runTime = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = true),
            punches = punches.map { it.toRaceBackupPunch(punchLabelsByCode) }
        )
    }

    private fun EventAliasPunch.toRaceBackupPunch(punchLabelsByCode: Map<Int, String>): FinalResultJsonExports.FinalPunchJson =
        FinalResultJsonExports.FinalPunchJson(
            code = punchLabelsByCode[punch.siCode] ?: alias?.name ?: punch.siCode.toString(),
            siCode = punch.siCode,
            controlType = punch.punchType.name,
            punchStatus = punch.punchStatus.toRaceBackupPunchStatus(),
            splitTime = DurationFormatter.secondsToFormattedString(punch.splitSeconds, useMinutes = true)
        )

    private fun Long.toRaceDateTime(raceStartIso: String): String {
        val date = raceStartIso.substringBefore('T').ifBlank { raceStartIso.substringBefore(' ') }
        val secondsInDay = 24 * 60 * 60
        val normalized = ((this % secondsInDay) + secondsInDay) % secondsInDay
        val hours = normalized / 3600
        val minutes = (normalized % 3600) / 60
        val seconds = normalized % 60
        return "$date" + "T" + "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun PunchStatus.toRaceBackupPunchStatus(): String =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "DP"
            PunchStatus.UNKNOWN -> "AP"
        }

    @Serializable
    data class RaceBackupJson(
        @SerialName("race_name") val raceName: String,
        @SerialName("race_start") val raceStart: String,
        @SerialName("race_type") val raceType: RaceType,
        @SerialName("race_band") val raceBand: RaceBand,
        @SerialName("race_level") val raceLevel: RaceLevel,
        @SerialName("race_time_limit") val raceTimeLimit: String,
        @SerialName("race_api_key") val raceApiKey: String,
        @SerialName("combined_national_regional_awards") val combinedNationalRegionalAwards: Boolean = false,
        val categories: List<FinalResultJsonExports.FinalCategoryJson>,
        val aliases: List<FinalResultJsonExports.FinalAliasJson>,
        val competitors: List<FinalResultJsonExports.FinalCompetitorJson>,
        @SerialName("unmatched_results") val unmatchedResults: List<RaceBackupUnmatchedResultJson>
    )

    @Serializable
    data class RaceBackupUnmatchedResultJson(
        @SerialName("si_number") val siNumber: Int? = null,
        @SerialName("check_time") val checkTime: String? = null,
        @SerialName("start_time") val startTime: String,
        @SerialName("finish_time") val finishTime: String,
        @SerialName("run_time") val runTime: String,
        val punches: List<FinalResultJsonExports.FinalPunchJson>
    )
}
