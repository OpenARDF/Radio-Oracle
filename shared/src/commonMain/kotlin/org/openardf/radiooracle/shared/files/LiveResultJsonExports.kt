package org.openardf.radiooracle.shared.files

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.EventAliasPunch
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.time.DurationFormatter

/** Android-shaped live-result JSON payloads shared by desktop preview/export and future sending. */
object LiveResultJsonExports {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        explicitNulls = false
    }

    /** Exports matched, categorized competitor readouts using the Android live-result JSON field names. */
    fun results(raceData: EventRaceData, resultIds: Set<String>? = null): String =
        json.encodeToString(resultRows(raceData, resultIds))

    fun resultRows(raceData: EventRaceData, resultIds: Set<String>? = null): List<LiveResultCompetitorJson> =
        raceData.competitorData.mapNotNull { competitorData ->
            val readoutData = competitorData.readoutData ?: return@mapNotNull null
            if (resultIds != null && readoutData.result.id !in resultIds) {
                return@mapNotNull null
            }
            val competitorCategory = competitorData.competitorCategory
            val category = competitorCategory.category ?: return@mapNotNull null
            val competitor = competitorCategory.competitor

            LiveResultCompetitorJson(
                competitorIndex = competitor.index.takeIf { it.isNotBlank() },
                siNumber = competitor.siNumber ?: readoutData.result.siNumber ?: 0,
                lastName = competitor.lastName,
                firstName = competitor.firstName,
                competitorCategory = category.name,
                result = readoutData.toLiveResultJson(raceData)
            )
        }

    private fun EventReadoutData.toLiveResultJson(raceData: EventRaceData): LiveResultJson =
        LiveResultJson(
            checkTime = result.checkTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            startTime = result.startTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            finishTime = result.finishTimeSeconds?.toRaceDateTime(raceData.race.startDateTimeIso),
            runTime = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = true),
            place = result.place,
            readoutTime = result.readoutDateTimeIso,
            modified = result.modified,
            punchCount = result.points,
            resultStatus = result.resultStatus.toLiveResultStatus(),
            automaticStatus = result.automaticStatus,
            punches = punches
                .filter { it.punch.punchType != SIRecordType.START }
                .map { it.toLivePunchJson() }
        )

    private fun EventAliasPunch.toLivePunchJson(): LiveResultPunchJson {
        val rawCode = alias?.name ?: punch.siCode.toString()
        val code = if (punch.punchType == SIRecordType.FINISH && rawCode == "0") "F" else rawCode
        return LiveResultPunchJson(
            code = code,
            siCode = punch.siCode,
            controlType = punch.punchType.name,
            punchStatus = punch.punchStatus.toLivePunchStatus(),
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

    private fun ResultStatus.toLiveResultStatus(): String =
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

    private fun PunchStatus.toLivePunchStatus(): String =
        when (this) {
            PunchStatus.VALID -> "OK"
            PunchStatus.INVALID -> "MP"
            PunchStatus.DUPLICATE -> "DP"
            PunchStatus.UNKNOWN -> "AP"
        }

    @Serializable
    data class LiveResultCompetitorJson(
        @SerialName("competitor_index") val competitorIndex: String? = null,
        @SerialName("si_number") val siNumber: Int,
        @SerialName("last_name") val lastName: String,
        @SerialName("first_name") val firstName: String,
        @SerialName("competitor_category") val competitorCategory: String,
        val result: LiveResultJson
    )

    @Serializable
    data class LiveResultJson(
        @SerialName("check_time") val checkTime: String? = null,
        @SerialName("start_time") val startTime: String? = null,
        @SerialName("finish_time") val finishTime: String? = null,
        @SerialName("run_time") val runTime: String,
        val place: Int = 0,
        val readoutTime: String,
        val modified: Boolean,
        @SerialName("punch_count") val punchCount: Int,
        @SerialName("result_status") val resultStatus: String,
        @SerialName("automatic_status") val automaticStatus: Boolean,
        val punches: List<LiveResultPunchJson>
    )

    @Serializable
    data class LiveResultPunchJson(
        val code: String,
        @SerialName("si_code") val siCode: Int,
        @SerialName("control_type") val controlType: String,
        @SerialName("punch_status") val punchStatus: String,
        @SerialName("split_time") val splitTime: String
    )
}
