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
