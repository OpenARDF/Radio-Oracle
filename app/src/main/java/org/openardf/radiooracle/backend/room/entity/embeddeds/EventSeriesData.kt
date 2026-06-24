package org.openardf.radiooracle.backend.room.entity.embeddeds

import androidx.room.Embedded
import androidx.room.Relation
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember

/** Event Series row with its ordered Android-local member mappings. */
data class EventSeriesData(
    @Embedded
    val series: EventSeries,
    @Relation(
        parentColumn = "series_id",
        entityColumn = "series_id"
    )
    val members: List<EventSeriesMember>
) {
    fun orderedMembers(): List<EventSeriesMember> =
        members.sortedWith(compareBy({ it.eventOrder }, { it.displayName }, { it.seriesEventId }))
}
