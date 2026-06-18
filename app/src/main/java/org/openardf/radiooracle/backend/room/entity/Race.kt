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
    @ColumnInfo(name = "import_fingerprint") var importFingerprint: String? = null
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
