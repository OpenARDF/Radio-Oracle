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

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventResultDetailsTest {
    @Test
    fun buildsDisplayRowsForRankedCompetitorResults() {
        val rows = EventResultDetails.from(
            raceData(
                categories = emptyList(),
                competitorData = listOf(
                    competitorData(
                        id = "competitor",
                        categoryId = null,
                        category = null,
                        firstName = "Alice",
                        lastName = "Runner",
                        resultId = "result",
                        points = 3,
                        place = 1,
                        controlCodes = listOf(31, 32)
                    )
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals("result", rows[0].id)
        assertEquals("Uncategorized", rows[0].categoryName)
        assertEquals("1", rows[0].placeText)
        assertEquals("RUNNER Alice", rows[0].competitorName)
        assertEquals(ResultStatus.OK, rows[0].resultStatus)
        assertEquals(true, rows[0].automaticStatus)
        assertEquals("OK", rows[0].statusLabel)
        assertEquals("3", rows[0].pointsText)
        assertEquals("00:20:00", rows[0].runTimeText)
        assertEquals("31, 32", rows[0].punchCodesText)
        assertEquals(false, rows[0].hasWarning)
    }

    @Test
    fun hidesScoreAndRunTimeForErrorRows() {
        val rows = EventResultDetails.from(
            raceData(
                categories = emptyList(),
                competitorData = listOf(
                    competitorData(
                        id = "competitor",
                        categoryId = null,
                        category = null,
                        firstName = "Alice",
                        lastName = "Runner",
                        resultId = "result",
                        points = 3,
                        place = 0,
                        controlCodes = listOf(31, 32),
                        resultStatus = ResultStatus.OK,
                        startTimeSeconds = 1_000,
                        finishTimeSeconds = 500,
                        runTimeSeconds = -110L * 3_600L - 12L
                    )
                )
            )
        )

        assertEquals("", rows[0].pointsText)
        assertEquals("ERR", rows[0].runTimeText)
        assertEquals(true, rows[0].hasWarning)
    }

    @Test
    fun sortsDisplayRowsByCategoryThenPlace() {
        val firstCategory = category("cat-a", "M21", 1)
        val secondCategory = category("cat-b", "M50", 2)
        val rows = EventResultDetails.from(
            raceData(
                categories = listOf(firstCategory, secondCategory),
                competitorData = listOf(
                    competitorData("second", "cat-b", secondCategory.category, "Bob", "Second", points = 1, place = 0),
                    competitorData("first", "cat-a", firstCategory.category, "Alice", "First", points = 2, place = 0),
                    competitorData("third", "cat-a", firstCategory.category, "Cara", "Third", points = 1, place = 0)
                )
            )
        )

        assertEquals(listOf("M21", "M21", "M50"), rows.map { it.categoryName })
        assertEquals(listOf("1", "2", "1"), rows.map { it.placeText })
        assertEquals(listOf("FIRST Alice", "THIRD Cara", "SECOND Bob"), rows.map { it.competitorName })
    }

    @Test
    fun ignoresStoredOrderAndSortsResultCategoriesWomenFirstByAge() {
        val m21 = category("m21", "M21", 0)
        val w35 = category("w35", "W35", 1)
        val m16 = category("m16", "M16", 2)
        val w16 = category("w16", "W16", 3)
        val rows = EventResultDetails.from(
            raceData(
                categories = listOf(m21, w35, m16, w16),
                competitorData = listOf(
                    competitorData("m21", "m21", m21.category, "A", "M21", points = 1, place = 0),
                    competitorData("w35", "w35", w35.category, "B", "W35", points = 1, place = 0),
                    competitorData("m16", "m16", m16.category, "C", "M16", points = 1, place = 0),
                    competitorData("w16", "w16", w16.category, "D", "W16", points = 1, place = 0)
                )
            )
        )

        assertEquals(listOf("W16", "W35", "M16", "M21"), rows.map { it.categoryName })
    }

    private fun raceData(
        categories: List<EventCategoryData>,
        competitorData: List<EventCompetitorData>
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Result Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = categories,
            aliases = emptyList(),
            competitorData = competitorData,
            unmatchedReadoutData = emptyList()
        )

    private fun category(id: String, name: String, order: Int): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = id,
                raceId = "race",
                name = name,
                isMan = false,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = order,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )

    private fun competitorData(
        id: String,
        categoryId: String?,
        category: EventCategory?,
        firstName: String,
        lastName: String,
        resultId: String = "result-$id",
        points: Int = 1,
        place: Int,
        controlCodes: List<Int> = emptyList(),
        resultStatus: ResultStatus = ResultStatus.OK,
        startTimeSeconds: Long = 600,
        finishTimeSeconds: Long = 1_800,
        runTimeSeconds: Long = 1_200
    ): EventCompetitorData {
        val competitor = EventCompetitor(
            id = id,
            raceId = "race",
            categoryId = categoryId,
            firstName = firstName,
            lastName = lastName,
            club = "",
            index = "",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventCompetitorData(
            competitorCategory = EventCompetitorCategory(competitor, category = category),
            readoutData = EventReadoutData(
                result = EventResult(
                    id = resultId,
                    raceId = "race",
                    competitorId = competitor.id,
                    siNumber = 123456,
                    cardType = 10,
                    checkTimeSeconds = null,
                    startTimeSeconds = startTimeSeconds,
                    finishTimeSeconds = finishTimeSeconds,
                    readoutDateTimeIso = "2026-05-31T11:00",
                    automaticStatus = true,
                    resultStatus = resultStatus,
                    points = points,
                    runTimeSeconds = runTimeSeconds,
                    modified = false,
                    sent = false,
                    place = place
                ),
                punches = controlCodes.mapIndexed { index, siCode ->
                    EventAliasPunch(
                        punch = EventPunch(
                            id = "punch-$id-$index",
                            raceId = "race",
                            resultId = resultId,
                            cardNumber = competitor.siNumber,
                            siCode = siCode,
                            siTimeSeconds = 700L + index,
                            originalSiTimeSeconds = 700L + index,
                            punchType = SIRecordType.CONTROL,
                            order = index,
                            punchStatus = PunchStatus.UNKNOWN,
                            splitSeconds = 0
                        ),
                        alias = null
                    )
                }
            )
        )
    }
}
