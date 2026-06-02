package org.openardf.radiooracle.backend.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.openardf.radiooracle.backend.room.dao.AliasDao
import org.openardf.radiooracle.backend.room.dao.CategoryDao
import org.openardf.radiooracle.backend.room.dao.CompetitorDao
import org.openardf.radiooracle.backend.room.dao.ControlPointDao
import org.openardf.radiooracle.backend.room.dao.PunchDao
import org.openardf.radiooracle.backend.room.dao.RaceDao
import org.openardf.radiooracle.backend.room.dao.ResultDao
import org.openardf.radiooracle.backend.room.dao.ResultServiceDao
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.Punch
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.Result
import org.openardf.radiooracle.backend.room.entity.ResultService

/** Room database that stores all Android event administration data. */
@Database(
    entities = [Race::class,
        Category::class,
        Alias::class,
        Competitor::class,
        ControlPoint::class,
        Punch::class,
        Result::class,
        ResultService::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(DateTimeTypeConverter::class)
abstract class EventDatabase : RoomDatabase() {
    /** DAO for race records. */
    abstract fun raceDao(): RaceDao
    /** DAO for control-point aliases. */
    abstract fun aliasDao(): AliasDao
    /** DAO for category and course records. */
    abstract fun categoryDao(): CategoryDao
    /** DAO for competitor records. */
    abstract fun competitorDao(): CompetitorDao
    /** DAO for category control points. */
    abstract fun controlPointDao(): ControlPointDao
    /** DAO for SportIdent punches. */
    abstract fun punchDao(): PunchDao
    /** DAO for SI readout results. */
    abstract fun resultDao(): ResultDao
    /** DAO for live-result service settings. */
    abstract fun resultServiceDao(): ResultServiceDao
}
