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

import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import java.time.LocalDateTime
import java.util.UUID

data class EventSeriesMemberListItem(
    val localRaceId: UUID,
    val displayLine: String
)

data class EventSeriesListItem(
    val seriesId: String,
    val name: String,
    val memberCount: Int,
    val members: List<EventSeriesMemberListItem>
) {
    val memberLines: List<String> get() = members.map { it.displayLine }
}

private fun EventSeriesData.memberItems(): List<EventSeriesMemberListItem> =
    orderedMembers().mapIndexed { index, member ->
        val datePrefix = member.startDateTimeIso
            .takeIf { it.isNotBlank() }
            ?.let(EventSeriesListItems::dateLabel)
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it - " }
            .orEmpty()
        EventSeriesMemberListItem(
            localRaceId = member.localRaceId,
            displayLine = "${index + 1}. $datePrefix${member.displayName}"
        )
    }

object EventSeriesListItems {
    fun from(seriesData: EventSeriesData): EventSeriesListItem =
        EventSeriesListItem(
            seriesId = seriesData.series.seriesId,
            name = seriesData.series.name,
            memberCount = seriesData.members.size,
            members = seriesData.memberItems()
        )

    fun sort(series: List<EventSeriesData>): List<EventSeriesListItem> =
        series
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.series.name })
            .map(::from)

    fun dateLabel(value: String): String =
        runCatching { LocalDateTime.parse(value).toLocalDate().toString() }
            .getOrDefault(value.substringBefore('T').trim())
}
