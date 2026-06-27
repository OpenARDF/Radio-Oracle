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

package org.openardf.radiooracle.ui.races

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class RaceListItemsTest {
    @Test
    fun groupsSeriesMembersTogetherWithInternalSeparatorsHidden() {
        val standAlone = race("11111111-1111-1111-1111-111111111111", "Solo", 10)
        val dayOne = race("22222222-2222-2222-2222-222222222222", "Series Day 1", 9)
        val dayTwo = race("33333333-3333-3333-3333-333333333333", "Series Day 2", 11)
        val series = series(
            name = "Summer Series",
            member(dayTwo, order = 1),
            member(dayOne, order = 0)
        )

        val items = RaceListItems.build(
            races = listOf(standAlone, dayTwo, dayOne),
            eventSeries = listOf(series)
        )

        assertEquals(listOf("Series Day 1", "Series Day 2", "Solo"), items.map { it.race.name })
        assertEquals(listOf("Summer Series", "Summer Series", null), items.map { it.seriesName })
        assertTrue(items[0].showTopSeparator)
        assertFalse(items[1].showTopSeparator)
        assertTrue(items[2].showTopSeparator)
    }

    private fun race(id: String, name: String, hour: Int): Race =
        Race(
            id = UUID.fromString(id),
            name = name,
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 6, 26, hour, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )

    private fun series(name: String, vararg members: EventSeriesMember): EventSeriesData =
        EventSeriesData(
            series = EventSeries(seriesId = "series-1", name = name),
            members = members.toList()
        )

    private fun member(race: Race, order: Int): EventSeriesMember =
        EventSeriesMember(
            seriesId = "series-1",
            seriesEventId = "event-$order",
            localRaceId = race.id,
            eventFilePath = "$order.rom.json",
            eventOrder = order,
            displayName = race.name,
            startDateTimeIso = race.startDateTime.toString(),
            formatLabel = "Classic"
        )
}
