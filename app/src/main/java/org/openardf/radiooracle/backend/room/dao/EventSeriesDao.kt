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
