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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.time.DurationFormatter

data class EventInForestRow(
    val competitorId: String,
    val competitorName: String,
    val categoryName: String,
    val startTimeText: String,
    val elapsedText: String,
    val limitText: String,
    val overLimit: Boolean
)

data class EventInForestDetails(
    val inForestRows: List<EventInForestRow>,
    val inForestCount: Int,
    val finishedCount: Int,
    val notStartedCount: Int,
    val unscheduledCount: Int
) {
    companion object {
        fun from(raceData: EventRaceData, raceElapsedSeconds: Long): EventInForestDetails {
            val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
            val inForestRows = mutableListOf<EventInForestRow>()
            var finishedCount = 0
            var notStartedCount = 0
            var unscheduledCount = 0

            raceData.competitorData.forEach { competitorData ->
                val competitor = competitorData.competitorCategory.competitor
                val startSeconds = competitor.drawnStartTimeSeconds
                val readoutStatus = competitorData.readoutData?.result?.resultStatus
                if (readoutStatus == ResultStatus.DID_NOT_START) {
                    notStartedCount += 1
                    return@forEach
                }
                if (readoutStatus != null) {
                    finishedCount += 1
                    return@forEach
                }
                if (startSeconds == null) {
                    unscheduledCount += 1
                    return@forEach
                }
                if (startSeconds > raceElapsedSeconds) {
                    notStartedCount += 1
                    return@forEach
                }

                val category = competitorData.competitorCategory.category
                    ?: competitor.categoryId?.let { categoryId ->
                        raceData.categories.firstOrNull { it.category.id == categoryId }?.category
                    }
                val timeLimitSeconds = category?.effectiveTimeLimitSeconds(raceData.race)
                    ?: raceData.race.timeLimitSeconds
                val elapsedSeconds = raceElapsedSeconds - startSeconds
                inForestRows += EventInForestRow(
                    competitorId = competitor.id,
                    competitorName = competitor.nameWithStartNumber(),
                    categoryName = category?.name
                        ?: competitor.categoryId?.let { categoryNamesById[it] }
                        ?: "",
                    startTimeText = DurationFormatter.secondsToFormattedString(startSeconds, useMinutes = true),
                    elapsedText = DurationFormatter.secondsToFormattedString(elapsedSeconds, useMinutes = true),
                    limitText = DurationFormatter.secondsToFormattedString(timeLimitSeconds, useMinutes = true),
                    overLimit = elapsedSeconds > timeLimitSeconds
                )
            }

            return EventInForestDetails(
                inForestRows = inForestRows.sortedWith(
                    compareBy<EventInForestRow> { it.overLimit.not() }
                        .thenBy { it.startTimeText }
                        .thenBy { it.competitorName }
                ),
                inForestCount = inForestRows.size,
                finishedCount = finishedCount,
                notStartedCount = notStartedCount,
                unscheduledCount = unscheduledCount
            )
        }
    }
}
