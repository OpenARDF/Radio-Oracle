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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.openardf.radiooracle.backend.room.entity.ResultService
import org.openardf.radiooracle.backend.room.entity.embeddeds.ResultServiceData
import java.util.UUID

/** Room access methods for configured live-result services. */
@Dao
interface ResultServiceDao {
    /** Returns one result-service configuration by primary key. */
    @Query("SELECT * FROM result_service WHERE id=(:id)")
    suspend fun getResultService(id: UUID): ResultService

    /** Observes one race's result service plus the number of result rows available to send. */
    @Query(
        """
    SELECT *,
    (SELECT COUNT(*) FROM result WHERE result.race_id = :raceId) AS resultCount
    FROM result_service
    WHERE race_id = :raceId
    LIMIT 1"""
    )
    fun getResultServiceLiveDataWithCountByRaceId(raceId: UUID): LiveData<ResultServiceData>

    /** Returns the result-service configuration for a race, or null when absent. */
    @Query("SELECT * FROM result_service WHERE race_id = (:raceId) LIMIT 1")
    fun getResultServiceByRaceId(raceId: UUID): ResultService?

    /** Inserts a new result-service configuration or updates the existing row. */
    @Upsert
    suspend fun createOrUpdateResultService(resultService: ResultService)

    /** Deletes one result-service configuration by primary key. */
    @Query("DELETE FROM result_service WHERE id =(:id)")
    suspend fun deleteResultService(id: UUID)
}
