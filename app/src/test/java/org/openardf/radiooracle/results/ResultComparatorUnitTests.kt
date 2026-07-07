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

package org.openardf.radiooracle.results

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.openardf.radiooracle.backend.results.ResultsProcessor
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.ReadoutData
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.backend.sportident.SIConstants
import org.junit.Test
import java.time.Duration
import java.util.UUID

class ResultComparatorUnitTests {
    @Test
    fun sortByPlaceDelegatesPlacementWithMissingReadoutsLast() {
        val missing = competitorData("missing", result = null)
        val slower = competitorData("slow", result = result("slow", points = 2, runTime = Duration.ofMinutes(20)))
        val faster = competitorData("fast", result = result("fast", points = 2, runTime = Duration.ofMinutes(10)))

        val sorted = ResultsProcessor.run {
            listOf(missing, slower, faster).sortByPlace()
        }

        assertEquals(listOf("fast", "slow", "missing"), sorted.map { it.competitorCategory.competitor.firstName })
        assertEquals(1, sorted[0].readoutData!!.result.place)
        assertEquals(2, sorted[1].readoutData!!.result.place)
        assertNull(sorted[2].readoutData)
    }

    @Test
    fun resultWrappersSkipCompetitorsWithoutReadouts() {
        val category = Category("M21")
        val missing = competitorData("missing", result = null, category = category)
        val finished = competitorData(
            "finished",
            result = result("finished", points = 2, runTime = Duration.ofMinutes(10)),
            category = category
        )

        val wrappers = ResultsProcessor.run {
            listOf(missing, finished).toResultWrappers()
        }

        assertEquals(1, wrappers.size)
        assertEquals("M21", wrappers.single().category?.name)
        assertEquals(1, wrappers.single().finished)
        assertEquals(
            listOf("finished"),
            wrappers.single().competitorData.map { it.competitorCategory.competitor.firstName }
        )
        assertEquals(0, ResultsProcessor.run { listOf(missing).toResultWrappers() }.size)
    }

    @Test
    fun readoutStatisticsSkipCompetitorsWithoutReadouts() {
        val missing = competitorData("missing", result = null)
        val finished = competitorData(
            "finished",
            result = result("finished", points = 2, runTime = Duration.ofMinutes(10))
        )

        val statistics = ResultsProcessor.run {
            listOf(missing, finished).toReadoutStatistics()
        }

        assertEquals(2, statistics.competitors)
        assertEquals(1, statistics.startedCompetitors)
        assertEquals(1, statistics.finishedCompetitors)
        assertEquals(0, statistics.inLimitCompetitors)

        val deletedReadoutStatistics = ResultsProcessor.run {
            listOf(missing).toReadoutStatistics()
        }

        assertEquals(1, deletedReadoutStatistics.competitors)
        assertEquals(0, deletedReadoutStatistics.startedCompetitors)
        assertEquals(0, deletedReadoutStatistics.finishedCompetitors)
        assertEquals(0, deletedReadoutStatistics.inLimitCompetitors)
    }

    private fun competitorData(name: String, result: Result?, category: Category? = null): CompetitorData {
        val competitor = Competitor(
            id = uuid(name),
            raceId = uuid("race"),
            categoryId = category?.id,
            firstName = name,
            lastName = "Runner",
            club = "",
            index = "",
            isMan = true,
            birthYear = null,
            siNumber = null,
            siRent = false,
            startNumber = 1,
            drawnRelativeStartTime = null
        )
        return CompetitorData(
            competitorCategory = CompetitorCategory(competitor, category = category),
            readoutData = result?.let { ReadoutData(it, emptyList()) }
        )
    }

    private fun result(seed: String, points: Int, runTime: Duration): Result =
        Result(
            id = uuid("result-$seed"),
            raceId = uuid("race"),
            competitorId = uuid(seed),
            siNumber = null,
            cardType = SIConstants.SI_CARD5,
            checkTime = null,
            startTime = null,
            finishTime = null,
            automaticStatus = true,
            resultStatus = ResultStatus.OK,
            points = points,
            runTime = runTime,
            modified = false,
            sent = false
        )

    private fun uuid(seed: String): UUID =
        UUID.nameUUIDFromBytes(seed.toByteArray())
}
