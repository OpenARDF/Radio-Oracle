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

package org.openardf.radiooracle.backend.room.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.openardf.radiooracle.backend.room.dao.AliasDao
import org.openardf.radiooracle.backend.room.dao.CategoryDao
import org.openardf.radiooracle.backend.room.dao.CompetitorDao
import org.openardf.radiooracle.backend.room.dao.ControlPointDao
import org.openardf.radiooracle.backend.room.dao.EventSeriesDao
import org.openardf.radiooracle.backend.room.dao.PunchDao
import org.openardf.radiooracle.backend.room.dao.RaceDao
import org.openardf.radiooracle.backend.room.dao.ResultDao
import org.openardf.radiooracle.backend.room.dao.ResultServiceDao
import org.openardf.radiooracle.backend.room.entity.Alias
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
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
        ResultService::class,
        EventSeries::class,
        EventSeriesMember::class],
    version = 8,
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
    /** DAO for Android-local Event Series membership. */
    abstract fun eventSeriesDao(): EventSeriesDao
}
