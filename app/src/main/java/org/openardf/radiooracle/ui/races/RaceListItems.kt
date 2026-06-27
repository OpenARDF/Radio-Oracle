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

import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import java.time.LocalDateTime
import java.util.UUID

data class RaceListItem(
    val race: Race,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val showTopSeparator: Boolean = true
) {
    val isSeriesMember: Boolean
        get() = seriesId != null
}

object RaceListItems {
    fun build(
        races: List<Race>,
        eventSeries: List<EventSeriesData>
    ): List<RaceListItem> {
        val racesById = races.associateBy { it.id }
        val groupedRaceIds = mutableSetOf<UUID>()
        val groups = mutableListOf<RaceListGroup>()

        eventSeries.forEach { seriesData ->
            val memberRaces = seriesData.orderedMembers()
                .mapNotNull { member ->
                    racesById[member.localRaceId]?.let { race -> member to race }
                }
            if (memberRaces.isEmpty()) {
                return@forEach
            }

            memberRaces.forEach { (member, _) -> groupedRaceIds += member.localRaceId }
            groups += RaceListGroup(
                sortDateTime = memberRaces.minOf { (_, race) -> race.startDateTime },
                sortName = seriesData.series.name,
                items = memberRaces.mapIndexed { index, (_, race) ->
                    RaceListItem(
                        race = race,
                        seriesId = seriesData.series.seriesId,
                        seriesName = seriesData.series.name,
                        showTopSeparator = index == 0
                    )
                }
            )
        }

        races
            .filterNot { race -> race.id in groupedRaceIds }
            .forEach { race ->
                groups += RaceListGroup(
                    sortDateTime = race.startDateTime,
                    sortName = race.name,
                    items = listOf(RaceListItem(race = race))
                )
            }

        val displayOrder = compareBy<RaceListGroup> { it.sortDateTime }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.sortName }
        return groups
            .sortedWith(displayOrder)
            .flatMap { group -> group.items }
    }

    private data class RaceListGroup(
        val sortDateTime: LocalDateTime,
        val sortName: String,
        val items: List<RaceListItem>
    )
}
