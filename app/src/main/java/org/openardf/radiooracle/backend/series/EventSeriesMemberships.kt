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

package org.openardf.radiooracle.backend.series

import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import org.openardf.radiooracle.shared.event.toDisplayLabel
import java.util.UUID

object EventSeriesMemberships {
    fun memberForRace(seriesId: String, race: Race, eventOrder: Int): EventSeriesMember =
        EventSeriesMember(
            seriesId = seriesId,
            seriesEventId = seriesEventId(race.id),
            localRaceId = race.id,
            eventFilePath = eventFilePath(race.id),
            eventOrder = eventOrder,
            displayName = race.name,
            startDateTimeIso = race.startDateTime.toString(),
            formatLabel = race.raceType.toDisplayLabel()
        )

    fun appendRace(seriesData: EventSeriesData, race: Race): List<EventSeriesMember> {
        val nextOrder = (seriesData.members.maxOfOrNull { it.eventOrder } ?: -1) + 1
        return seriesData.members + memberForRace(seriesData.series.seriesId, race, nextOrder)
    }

    fun removeRace(seriesData: EventSeriesData, raceId: UUID): List<EventSeriesMember> =
        seriesData.orderedMembers()
            .filterNot { it.localRaceId == raceId }
            .mapIndexed { index, member -> member.copy(eventOrder = index) }

    private fun seriesEventId(raceId: UUID): String =
        "event-$raceId"

    private fun eventFilePath(raceId: UUID): String =
        "events/$raceId.rom.json"
}
