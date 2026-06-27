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

package org.openardf.radiooracle.ui.series

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import java.util.UUID

class EventSeriesListItemsTest {
    @Test
    fun formatsOrderedMemberLinesAndSortsSeriesByName() {
        val dayOneRaceId = UUID.randomUUID()
        val dayTwoRaceId = UUID.randomUUID()
        val soloRaceId = UUID.randomUUID()
        val beta = seriesData(
            seriesId = "beta",
            name = "Beta Series",
            members = listOf(
                member("day-2", 1, "Day 2", localRaceId = dayTwoRaceId),
                member("day-1", 0, "Day 1", localRaceId = dayOneRaceId)
            )
        )
        val alpha = seriesData(
            seriesId = "alpha",
            name = "Alpha Series",
            members = listOf(member("solo", 0, "Solo", startDateTimeIso = "", localRaceId = soloRaceId))
        )

        val items = EventSeriesListItems.sort(listOf(beta, alpha))

        assertEquals(listOf("Alpha Series", "Beta Series"), items.map { it.name })
        assertEquals(1, items[0].memberCount)
        assertEquals(listOf("1. Solo"), items[0].memberLines)
        assertEquals(listOf(soloRaceId), items[0].members.map { it.localRaceId })
        assertEquals(
            listOf("1. 2026-06-20 - Day 1", "2. 2026-06-21 - Day 2"),
            items[1].memberLines
        )
        assertEquals(listOf(dayOneRaceId, dayTwoRaceId), items[1].members.map { it.localRaceId })
    }

    private fun seriesData(
        seriesId: String,
        name: String,
        members: List<EventSeriesMember>
    ): EventSeriesData =
        EventSeriesData(
            series = EventSeries(seriesId = seriesId, name = name),
            members = members
        )

    private fun member(
        seriesEventId: String,
        order: Int,
        displayName: String,
        startDateTimeIso: String = "2026-06-${20 + order}T09:00",
        localRaceId: UUID = UUID.randomUUID()
    ): EventSeriesMember =
        EventSeriesMember(
            seriesId = "series",
            seriesEventId = seriesEventId,
            localRaceId = localRaceId,
            eventFilePath = "$seriesEventId.json",
            eventOrder = order,
            displayName = displayName,
            startDateTimeIso = startDateTimeIso,
            formatLabel = "Classic"
        )
}
