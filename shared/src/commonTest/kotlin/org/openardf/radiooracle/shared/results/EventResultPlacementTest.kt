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

import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventResultPlacementTest {
    @Test
    fun sortsReadoutsBeforeMissingReadoutsAndAssignsPlaces() {
        val missing = competitor("missing", readout = null)
        val slower = competitor("slow", result(points = 2, runTimeSeconds = 200))
        val faster = competitor("fast", result(points = 2, runTimeSeconds = 100))

        val sorted = EventResultPlacement.sortByPlace(listOf(missing, slower, faster))

        assertEquals(listOf("fast", "slow", "missing"), sorted.map { it.competitorCategory.competitor.id })
        assertEquals(1, sorted[0].readoutData!!.result.place)
        assertEquals(2, sorted[1].readoutData!!.result.place)
        assertNull(sorted[2].readoutData)
    }

    @Test
    fun assignsSamePlaceForEqualPointsAndRunTime() {
        val first = competitor("first", result(points = 2, runTimeSeconds = 100))
        val second = competitor("second", result(points = 2, runTimeSeconds = 100))
        val third = competitor("third", result(points = 1, runTimeSeconds = 90))

        val sorted = EventResultPlacement.sortByPlace(listOf(third, second, first))

        assertEquals(listOf(1, 1, 2), sorted.map { it.readoutData!!.result.place })
    }

    @Test
    fun groupsByCategoryBeforeAssigningPlaces() {
        val categoryAFirst = competitor("a1", result(points = 2, runTimeSeconds = 100), categoryId = "a")
        val categoryASecond = competitor("a2", result(points = 1, runTimeSeconds = 100), categoryId = "a")
        val categoryBFirst = competitor("b1", result(points = 1, runTimeSeconds = 100), categoryId = "b")

        val grouped = EventResultPlacement.groupByCategoryAndSortByPlace(
            listOf(categoryASecond, categoryBFirst, categoryAFirst)
        )

        assertEquals(listOf(1, 2), grouped["a"]!!.map { it.readoutData!!.result.place })
        assertEquals(listOf(1), grouped["b"]!!.map { it.readoutData!!.result.place })
    }

    @Test
    fun assignsCategoryPlacesWithoutReorderingCompetitors() {
        val categoryAFirst = competitor("a1", result(points = 2, runTimeSeconds = 100), categoryId = "a")
        val categoryASecond = competitor("a2", result(points = 1, runTimeSeconds = 100), categoryId = "a")
        val categoryBFirst = competitor("b1", result(points = 1, runTimeSeconds = 100), categoryId = "b")

        val placed = EventResultPlacement.assignPlacesByCategory(
            listOf(categoryASecond, categoryBFirst, categoryAFirst)
        )

        assertEquals(listOf("a2", "b1", "a1"), placed.map { it.competitorCategory.competitor.id })
        assertEquals(listOf(2, 1, 1), placed.map { it.readoutData!!.result.place })
    }

    private fun competitor(
        id: String,
        readout: EventResult?,
        categoryId: String? = "category"
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = categoryId,
                    firstName = id,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    startNumber = 1,
                    drawnStartTimeSeconds = null
                ),
                category = categoryId?.let {
                    eventCategory(it)
                }
            ),
            readoutData = readout?.let {
                EventReadoutData(result = it, punches = emptyList())
            }
        )

    private fun result(points: Int, runTimeSeconds: Long): EventResult =
        EventResult(
            id = "result-$points-$runTimeSeconds",
            raceId = "race",
            competitorId = "competitor",
            siNumber = null,
            cardType = 5,
            checkTimeSeconds = null,
            startTimeSeconds = null,
            finishTimeSeconds = null,
            readoutDateTimeIso = "2026-05-30T10:00",
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = points,
            runTimeSeconds = runTimeSeconds,
            modified = false,
            sent = false
        )

    private fun eventCategory(id: String) =
        org.openardf.radiooracle.shared.event.EventCategory(
            id = id,
            raceId = "race",
            name = id,
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
}
