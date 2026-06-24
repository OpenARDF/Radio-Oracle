package org.openardf.radiooracle.backend.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Android-local record for one imported Event Series. */
@Entity(tableName = "event_series")
data class EventSeries(
    @PrimaryKey
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    val name: String
)
