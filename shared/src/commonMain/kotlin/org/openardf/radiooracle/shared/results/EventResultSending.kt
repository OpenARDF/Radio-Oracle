package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData

data class EventResultSendCandidate(
    val competitorId: String,
    val resultId: String,
    val siNumber: Int?
)

data class EventResultSendPlan(
    val candidates: List<EventResultSendCandidate>,
    val alreadySentCount: Int,
    val missingReadoutCount: Int,
    val unmatchedReadoutCount: Int
) {
    val candidateCount: Int
        get() = candidates.size

    val hasCandidates: Boolean
        get() = candidates.isNotEmpty()
}

/** Shared result-service policy helpers that do not perform network or persistence work. */
object EventResultSending {
    /** Returns competitor ids for readouts that exist and have not yet been marked sent. */
    fun unsentCompetitorIds(results: List<EventCompetitorData>): Set<String> {
        return results
            .filter { competitorData ->
                competitorData.readoutData?.result?.sent == false
            }
            .map { competitorData ->
                competitorData.competitorCategory.competitor.id
            }
            .toSet()
    }

    /** Builds a platform-neutral live-result send plan without performing network work. */
    fun plan(raceData: EventRaceData): EventResultSendPlan {
        val candidates = mutableListOf<EventResultSendCandidate>()
        var alreadySentCount = 0
        var missingReadoutCount = 0

        raceData.competitorData.forEach { competitorData ->
            val readoutData = competitorData.readoutData
            if (readoutData == null) {
                missingReadoutCount += 1
                return@forEach
            }

            val result = readoutData.result
            if (result.sent) {
                alreadySentCount += 1
                return@forEach
            }

            candidates += EventResultSendCandidate(
                competitorId = competitorData.competitorCategory.competitor.id,
                resultId = result.id,
                siNumber = result.siNumber
            )
        }

        return EventResultSendPlan(
            candidates = candidates,
            alreadySentCount = alreadySentCount,
            missingReadoutCount = missingReadoutCount,
            unmatchedReadoutCount = raceData.unmatchedReadoutData.size
        )
    }
}
