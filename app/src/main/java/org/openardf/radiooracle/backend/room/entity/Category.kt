package org.openardf.radiooracle.backend.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import org.openardf.radiooracle.backend.room.database.DateTimeTypeConverter
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.openardf.radiooracle.backend.shared.toEventCategory
import org.openardf.radiooracle.shared.files.EventCsvRows
import java.io.Serializable
import java.time.Duration
import java.util.UUID

/**
 * Room entity for a competition category and its assigned controls.
 *
 * The race-setting columns are legacy storage retained for database compatibility. Current
 * behavior uses the owning Race for event type, band, and time limit, and category saves clear
 * these fields instead of treating them as overrides.
 */
@Entity(
    tableName = "category", indices = [
        Index(
            value = ["name", "race_id"],
            unique = true
        ),
        Index("race_id")
    ],
    foreignKeys = [ForeignKey(
        entity = Race::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("race_id"),
        onDelete = ForeignKey.CASCADE
    )]
)
@TypeConverters(DateTimeTypeConverter::class)
data class Category(
    @PrimaryKey var id: UUID,
    @ColumnInfo(name = "race_id") var raceId: UUID,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "is_man") var isMan: Boolean,
    @ColumnInfo(name = "max_age") var maxAge: Int?,
    @ColumnInfo(name = "length") var length: Int,   // Course length in meters.
    @ColumnInfo(name = "climb") var climb: Int,     // Course climb in meters.
    @ColumnInfo(name = "order") var order: Int,
    @ColumnInfo(name = "different_properties") var differentProperties: Boolean = false,
    @ColumnInfo(name = "race_type") var raceType: RaceType? = null,
    @ColumnInfo(name = "category_band") var categoryBand: RaceBand? = null,
    @ColumnInfo(name = "limit") var timeLimit: Duration? = null,
    @ColumnInfo(name = "control_points_string") var controlPointsString: String
) : Serializable {

    /** Formats this category in the legacy CSV export row shape. */
    fun toCSVString(): String {
        return EventCsvRows.categoryRow(toEventCategory())
    }

    /** Convenience constructor for tests and lightweight UI placeholders. */
    constructor(name: String) : this(
        UUID.randomUUID(),
        UUID.randomUUID(),
        name,
        true,
        null,
        0,
        0,
        0,
        false,
        null,
        null,
        null,
        ""
    )
}
