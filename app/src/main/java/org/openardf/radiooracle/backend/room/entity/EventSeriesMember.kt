package org.openardf.radiooracle.backend.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

/** Android-local mapping from a series manifest event to the imported Room race. */
@Entity(
    tableName = "event_series_member",
    primaryKeys = ["series_id", "series_event_id"],
    foreignKeys = [
        ForeignKey(
            entity = EventSeries::class,
            parentColumns = ["series_id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Race::class,
            parentColumns = ["id"],
            childColumns = ["local_race_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("series_id"),
        Index(value = ["local_race_id"], unique = true)
    ]
)
data class EventSeriesMember(
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "series_event_id")
    val seriesEventId: String,
    @ColumnInfo(name = "local_race_id")
    val localRaceId: UUID,
    @ColumnInfo(name = "event_file_path")
    val eventFilePath: String,
    @ColumnInfo(name = "event_order")
    val eventOrder: Int,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "start_date_time_iso")
    val startDateTimeIso: String,
    @ColumnInfo(name = "format_label")
    val formatLabel: String
)
