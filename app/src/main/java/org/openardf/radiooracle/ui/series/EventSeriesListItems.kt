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
