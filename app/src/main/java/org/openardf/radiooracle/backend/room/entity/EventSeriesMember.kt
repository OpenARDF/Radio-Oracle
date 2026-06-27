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
