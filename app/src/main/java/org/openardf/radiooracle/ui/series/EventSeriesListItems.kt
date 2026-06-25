package org.openardf.radiooracle.ui.series

import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import java.time.LocalDateTime

data class EventSeriesListItem(
    val seriesId: String,
    val name: String,
    val memberCount: Int,
    val memberLines: List<String>
)

object EventSeriesListItems {
    fun from(seriesData: EventSeriesData): EventSeriesListItem =
        EventSeriesListItem(
            seriesId = seriesData.series.seriesId,
            name = seriesData.series.name,
            memberCount = seriesData.members.size,
            memberLines = seriesData.orderedMembers().mapIndexed { index, member ->
                val datePrefix = member.startDateTimeIso
                    .takeIf { it.isNotBlank() }
                    ?.let(::dateLabel)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { "$it - " }
                    .orEmpty()
                "${index + 1}. $datePrefix${member.displayName}"
            }
        )

    fun sort(series: List<EventSeriesData>): List<EventSeriesListItem> =
        series
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.series.name })
            .map(::from)

    private fun dateLabel(value: String): String =
        runCatching { LocalDateTime.parse(value).toLocalDate().toString() }
            .getOrDefault(value.substringBefore('T').trim())
}
