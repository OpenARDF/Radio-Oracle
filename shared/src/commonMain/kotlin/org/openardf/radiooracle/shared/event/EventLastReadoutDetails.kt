package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ResultStatus

enum class EventLastReadoutSeverity {
    None,
    Normal,
    Warning,
    Error
}

data class EventLastReadoutDetails(
    val hasReadout: Boolean,
    val readoutDateTimeIso: String,
    val siNumberText: String,
    val competitorName: String,
    val statusLabel: String,
    val severity: EventLastReadoutSeverity
) {
    companion object {
        fun from(raceData: EventRaceData): EventLastReadoutDetails {
            val matched = raceData.competitorData.mapNotNull { competitorData ->
                val readoutData = competitorData.readoutData ?: return@mapNotNull null
                LastReadoutCandidate(
                    readoutData = readoutData,
                    competitorName = competitorData.competitorCategory.competitor.fullName()
                )
            }
            val unmatched = raceData.unmatchedReadoutData.map { readoutData ->
                LastReadoutCandidate(readoutData = readoutData, competitorName = "")
            }
            val lastReadout = (matched + unmatched).maxByOrNull { it.readoutData.result.readoutDateTimeIso }
                ?: return empty()
            val result = lastReadout.readoutData.result

            return EventLastReadoutDetails(
                hasReadout = true,
                readoutDateTimeIso = result.readoutDateTimeIso,
                siNumberText = result.siNumber?.toString() ?: "",
                competitorName = lastReadout.competitorName,
                statusLabel = result.resultStatus.toDisplayLabel(),
                severity = result.resultStatus.toLastReadoutSeverity(lastReadout.competitorName.isBlank())
            )
        }

        fun empty(): EventLastReadoutDetails =
            EventLastReadoutDetails(
                hasReadout = false,
                readoutDateTimeIso = "",
                siNumberText = "",
                competitorName = "",
                statusLabel = "",
                severity = EventLastReadoutSeverity.None
            )
    }
}

private data class LastReadoutCandidate(
    val readoutData: EventReadoutData,
    val competitorName: String
)

private fun ResultStatus.toLastReadoutSeverity(unmatched: Boolean): EventLastReadoutSeverity =
    when {
        this == ResultStatus.ERROR -> EventLastReadoutSeverity.Error
        unmatched -> EventLastReadoutSeverity.Error
        this == ResultStatus.OK -> EventLastReadoutSeverity.Normal
        else -> EventLastReadoutSeverity.Warning
    }
