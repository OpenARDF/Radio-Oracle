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

/**
 * Derives event start numbers from assigned start times.
 *
 * In Radio-Oracle, a start number is the departure-slot sequence within a
 * specific Race File. Competitors starting together share the same start
 * number; competitors without a drawn start time have no start number yet.
 */
object EventStartNumbers {
    fun numberByStartTime(raceData: EventRaceData): Map<Long, Int> =
        raceData.competitorData
            .mapNotNull { it.competitorCategory.competitor.drawnStartTimeSeconds }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, startSeconds) -> startSeconds to index + 1 }

    fun assignFromDrawnStartTimes(projectFile: EventProjectFile): EventProjectFile =
        projectFile.copy(raceData = assignFromDrawnStartTimes(projectFile.raceData))

    fun assignFromDrawnStartTimes(raceData: EventRaceData): EventRaceData {
        val numberByStartTime = numberByStartTime(raceData)
        val competitorsById = mutableMapOf<String, EventCompetitor>()
        fun EventCompetitor.withDerivedStartNumber(): EventCompetitor =
            copy(startNumber = drawnStartTimeSeconds?.let(numberByStartTime::get))

        val competitorData = raceData.competitorData.map { data ->
            val competitor = data.competitorCategory.competitor.withDerivedStartNumber()
            competitorsById[competitor.id] = competitor
            data.copy(
                competitorCategory = data.competitorCategory.copy(
                    competitor = competitor
                )
            )
        }

        return raceData.copy(
            categories = raceData.categories.map { categoryData ->
                categoryData.copy(
                    competitors = categoryData.competitors.map { competitor ->
                        competitorsById[competitor.id] ?: competitor.withDerivedStartNumber()
                    }
                )
            },
            competitorData = competitorData
        )
    }
}
