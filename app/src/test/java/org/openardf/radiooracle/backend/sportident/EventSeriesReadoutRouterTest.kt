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

package org.openardf.radiooracle.backend.sportident

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorCategory
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRoute.Ambiguous
import org.openardf.radiooracle.backend.sportident.EventSeriesReadoutRoute.Matched
import org.openardf.radiooracle.backend.sportident.SIPort.CardData
import org.openardf.radiooracle.backend.sportident.SIPort.PunchData
import org.openardf.radiooracle.backend.sportident.SIConstants.SI_CARD6
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class EventSeriesReadoutRouterTest {
    @Test
    fun routesByUniqueControlPunches() {
        val day1 = memberData("day-1", "Day 1", controls = listOf(31, 32), siNumbers = listOf(1001))
        val day2 = memberData("day-2", "Day 2", controls = listOf(41, 42), siNumbers = listOf(1001))

        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 1001, punches = listOf(41, 42)),
            members = listOf(day1, day2)
        )

        assertSame(day2, (route as Matched).memberData)
        assertEquals(EventSeriesReadoutRouteReason.CONTROL_PUNCHES_AND_SI_NUMBER, route.reason)
    }

    @Test
    fun routesSameRegisteredCardToClosestMatchingCourse() {
        val shortCourse = memberData(
            "short",
            "Short course",
            controls = listOf(31, 32),
            siNumbers = listOf(1001)
        )
        val longerCourse = memberData(
            "longer",
            "Longer course",
            controls = listOf(31, 32, 33),
            siNumbers = listOf(1001)
        )

        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 1001, punches = listOf(31, 32)),
            members = listOf(shortCourse, longerCourse)
        )

        assertSame(shortCourse, (route as Matched).memberData)
        assertEquals(EventSeriesReadoutRouteReason.CONTROL_PUNCHES_AND_SI_NUMBER, route.reason)
    }

    @Test
    fun narrowsAmbiguousControlPunchesBySiNumber() {
        val day1 = memberData("day-1", "Day 1", controls = listOf(31, 32), siNumbers = listOf(1001))
        val day2 = memberData("day-2", "Day 2", controls = listOf(31, 32), siNumbers = listOf(2002))

        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 2002, punches = listOf(31, 32)),
            members = listOf(day1, day2)
        )

        assertSame(day2, (route as Matched).memberData)
        assertEquals(EventSeriesReadoutRouteReason.CONTROL_PUNCHES_AND_SI_NUMBER, route.reason)
    }

    @Test
    fun reportsAmbiguousWhenOnlySiNumberMatchesMultipleEvents() {
        val day1 = memberData("day-1", "Day 1", controls = listOf(31, 32), siNumbers = listOf(1001))
        val day2 = memberData("day-2", "Day 2", controls = listOf(41, 42), siNumbers = listOf(1001))

        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 1001, punches = emptyList()),
            members = listOf(day1, day2)
        )

        assertEquals(listOf(day1, day2), (route as Ambiguous).candidates)
        assertEquals(EventSeriesReadoutRouteReason.SI_NUMBER, route.reason)
    }

    @Test
    fun doesNotRouteBySiNumberWhenControlPunchesMatchNoEvent() {
        val day1 = memberData("day-1", "Day 1", controls = listOf(31, 32), siNumbers = listOf(1001))
        val day2 = memberData("day-2", "Day 2", controls = listOf(41, 42), siNumbers = listOf(1001))

        val route = EventSeriesReadoutRouter.route(
            cardData = card(siNumber = 1001, punches = listOf(99)),
            members = listOf(day1, day2)
        )

        assertSame(EventSeriesReadoutRoute.NoMatch, route)
    }

    private fun card(siNumber: Int, punches: List<Int>): CardData =
        CardData(
            cardType = SI_CARD6,
            siNumber = siNumber,
            punchData = ArrayList(punches.map { PunchData(it, SITime(10 * 60)) })
        )

    private fun memberData(
        seriesEventId: String,
        displayName: String,
        controls: List<Int>,
        siNumbers: List<Int>
    ): EventSeriesReadoutMemberData {
        val raceId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        val category = Category(
            id = categoryId,
            raceId = raceId,
            name = "M21",
            isMan = true,
            maxAge = null,
            length = 0,
            climb = 0,
            order = 0,
            controlPointsString = controls.joinToString(",")
        )
        val raceData = RaceData(
            race = Race(
                id = raceId,
                name = displayName,
                apiKey = "",
                startDateTime = LocalDateTime.of(2026, 6, 20, 9, 0),
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimit = Duration.ofHours(2)
            ),
            categories = listOf(
                CategoryData(
                    category = category,
                    controlPoints = controls.mapIndexed { index, siCode ->
                        ControlPoint(
                            id = UUID.randomUUID(),
                            categoryId = categoryId,
                            siCode = siCode,
                            type = ControlPointType.CONTROL,
                            order = index + 1
                        )
                    },
                    competitors = emptyList()
                )
            ),
            aliases = emptyList(),
            competitorData = siNumbers.map { siNumber ->
                CompetitorData(
                    competitorCategory = CompetitorCategory(
                        competitor = Competitor(
                            id = UUID.randomUUID(),
                            raceId = raceId,
                            categoryId = categoryId,
                            firstName = "SI",
                            lastName = siNumber.toString(),
                            club = "",
                            index = "",
                            isMan = true,
                            birthYear = null,
                            siNumber = siNumber,
                            siRent = false,
                            startNumber = 0
                        ),
                        category = category
                    ),
                    readoutData = null
                )
            },
            unmatchedReadoutData = emptyList()
        )
        return EventSeriesReadoutMemberData(
            member = EventSeriesMember(
                seriesId = "series-2026",
                seriesEventId = seriesEventId,
                localRaceId = raceId,
                eventFilePath = "$seriesEventId.rom.json",
                eventOrder = 0,
                displayName = displayName,
                startDateTimeIso = "2026-06-20T09:00",
                formatLabel = "Classic"
            ),
            raceData = raceData
        )
    }
}
