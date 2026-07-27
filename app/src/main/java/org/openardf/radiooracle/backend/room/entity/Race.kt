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
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import org.openardf.radiooracle.backend.room.database.DateTimeTypeConverter
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import java.io.Serializable
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** Room entity for an event/race and its default timing and course settings. */
@Entity(
    tableName = "race",
    indices = [Index("import_source_id")]
)
@TypeConverters(DateTimeTypeConverter::class)
data class Race(
    @PrimaryKey var id: UUID,
    var name: String,
    @ColumnInfo(name = "api_key") var apiKey: String,
    @ColumnInfo(name = "start_date_time") var startDateTime: LocalDateTime,
    @ColumnInfo(name = "race_type") var raceType: RaceType,
    @ColumnInfo(name = "race_level") var raceLevel: RaceLevel,
    @ColumnInfo(name = "race_band") var raceBand: RaceBand,
    @ColumnInfo(name = "time_limit") var timeLimit: Duration,
    @ColumnInfo(name = "import_source_id") var importSourceId: String? = null,
    @ColumnInfo(name = "import_fingerprint") var importFingerprint: String? = null,
    @ColumnInfo(name = "public_results_url") var publicResultsUrl: String? = null,
    @ColumnInfo(name = "public_results_published_at_iso") var publicResultsPublishedAtIso: String? = null
) : Serializable {
    /** Default constructor used by edit screens and persistence tooling. */
    constructor() : this(
        UUID.randomUUID(),
        "", "",
        LocalDateTime.now(),
        RaceType.CLASSIC,
        RaceLevel.PRACTICE,
        RaceBand.M80,
        Duration.ZERO
    )
}
