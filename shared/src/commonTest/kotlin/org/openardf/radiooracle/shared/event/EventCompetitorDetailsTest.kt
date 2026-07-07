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
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCompetitorDetailsTest {
    @Test
    fun buildsDisplayRowsWithCategoryLookup() {
        val rows = EventCompetitorDetails.from(raceData())

        assertEquals(2, rows.size)
        assertEquals("Alice", rows[0].id)
        assertEquals("Alice", rows[0].firstName)
        assertEquals("Runner", rows[0].lastName)
        assertEquals("RUNNER Alice", rows[0].fullName)
        assertEquals("OK Test", rows[0].club)
        assertEquals("A101", rows[0].index)
        assertEquals("1985", rows[0].birthYearText)
        assertEquals("category", rows[0].categoryId)
        assertEquals("W21", rows[0].categoryName)
        assertEquals("101", rows[0].startNumberText)
        assertEquals("10:15", rows[0].startTimeText)
        assertEquals("123456", rows[0].siNumberText)
        assertEquals(true, rows[0].usaChampEligible)
        assertEquals(false, rows[0].region2ChampEligible)
        assertEquals(emptyList(), rows[0].warningReasons)

        assertEquals("Bob", rows[1].id)
        assertEquals("RUNNER Bob", rows[1].fullName)
        assertEquals(null, rows[1].categoryId)
        assertEquals("", rows[1].categoryName)
        assertEquals("102", rows[1].startNumberText)
        assertEquals("", rows[1].startTimeText)
        assertEquals("", rows[1].siNumberText)
        assertEquals(false, rows[1].usaChampEligible)
        assertEquals(true, rows[1].region2ChampEligible)
        assertEquals(listOf("No SI number is assigned.", "No category is assigned."), rows[1].warningReasons)
    }

    @Test
    fun warnsWhenCompetitorAppearsTooYoungForAdultCategory() {
        val rows = EventCompetitorDetails.from(
            raceData(
                categoryName = "M50",
                competitors = listOf(
                    competitorData(
                        firstName = "Junior",
                        categoryId = "category",
                        siNumber = 222222,
                        startNumber = 1,
                        birthYear = 2010
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "Apparent birth year/category discrepancy: competitor appears to be 16 on the race date, too young for M50."
            ),
            rows.single().warningReasons
        )
    }

    private fun raceData(
        categoryName: String = "W21",
        competitors: List<EventCompetitorData> = listOf(
            competitorData(
                "Alice",
                categoryId = "category",
                siNumber = 123456,
                startNumber = 101,
                usaChampEligible = true
            ),
            competitorData(
                "Bob",
                categoryId = null,
                siNumber = null,
                startNumber = 102,
                region2ChampEligible = true
            )
        )
    ): EventRaceData {
        val category = EventCategory(
            id = "category",
            raceId = "race",
            name = categoryName,
            isMan = false,
            maxAge = null,
            lengthMeters = 5_000,
            climbMeters = 100,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        return EventRaceData(
            race = EventRace(
                id = "race",
                name = "Competitor Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(EventCategoryData(category, emptyList(), emptyList())),
            aliases = emptyList(),
            competitorData = competitors,
            unmatchedReadoutData = emptyList()
        )
    }

    private fun competitorData(
        firstName: String,
        categoryId: String?,
        siNumber: Int?,
        startNumber: Int,
        birthYear: Int? = if (firstName == "Alice") 1985 else null,
        usaChampEligible: Boolean? = null,
        region2ChampEligible: Boolean? = null
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = firstName,
                    raceId = "race",
                    categoryId = categoryId,
                    firstName = firstName,
                    lastName = "Runner",
                    club = if (firstName == "Alice") "OK Test" else "",
                    index = if (firstName == "Alice") "A101" else "",
                    isMan = true,
                    birthYear = birthYear,
                    siNumber = siNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = if (firstName == "Alice") 10 * 60L + 15 else null,
                    usaChampEligible = usaChampEligible,
                    region2ChampEligible = region2ChampEligible
                ),
                category = null
            ),
            readoutData = null
        )
}
