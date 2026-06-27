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

package org.openardf.radiooracle.backend.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Room access methods for Android-local Event Series membership. */
@Dao
interface EventSeriesDao {
    /** Observes all locally imported series. */
    @Transaction
    @Query("SELECT * FROM event_series ORDER BY name")
    fun getSeries(): Flow<List<EventSeriesData>>

    /** Returns one local series by manifest id. */
    @Transaction
    @Query("SELECT * FROM event_series WHERE series_id = (:seriesId)")
    suspend fun getSeries(seriesId: String): EventSeriesData?

    /** Returns the series containing a local race, or null when the race is not a member. */
    @Transaction
    @Query(
        """
        SELECT event_series.* FROM event_series
        INNER JOIN event_series_member
            ON event_series.series_id = event_series_member.series_id
        WHERE event_series_member.local_race_id = (:raceId)
        LIMIT 1
        """
    )
    suspend fun getSeriesForRace(raceId: UUID): EventSeriesData?

    /** Inserts or updates a series row. */
    @Upsert
    suspend fun upsertSeries(series: EventSeries)

    /** Inserts or updates one manifest member to local race mapping. */
    @Upsert
    suspend fun upsertMember(member: EventSeriesMember)

    /** Removes all member mappings for a series before replacing them. */
    @Query("DELETE FROM event_series_member WHERE series_id = (:seriesId)")
    suspend fun deleteMembersForSeries(seriesId: String)

    /** Deletes a local series and cascades its member mappings. */
    @Query("DELETE FROM event_series WHERE series_id = (:seriesId)")
    suspend fun deleteSeries(seriesId: String)
}
