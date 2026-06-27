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
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Upsert
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.embeddeds.CompetitorData
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Room access methods for competitors and competitor/result aggregates. */
@Dao
interface CompetitorDao {
    /** Observes competitor aggregates for a race. */
    @Query("SELECT * FROM competitor WHERE race_id=(:raceId) ")
    @Transaction
    @RewriteQueriesToDropUnusedColumns
    fun getCompetitorDataFlow(raceId: UUID): Flow<List<CompetitorData>>

    /** Returns all competitor entities for a race. */
    @Query("SELECT * FROM competitor WHERE race_id=(:raceId) ")
    suspend fun getCompetitorsByRace(raceId: UUID): List<Competitor>

    /** Returns one competitor by primary key, or null when absent. */
    @Query("SELECT * FROM competitor WHERE id=(:id) LIMIT 1")
    suspend fun getCompetitor(id: UUID): Competitor?

    /** Finds a competitor by SI card number within one race. */
    @Query("SELECT * FROM competitor WHERE si_number=(:siNumber) AND race_id = (:raceId) LIMIT 1")
    suspend fun getCompetitorBySINumber(siNumber: Int, raceId: UUID): Competitor?

    /** Returns the highest start number currently used in a race. */
    @Query("SELECT start_number FROM competitor WHERE race_id=(:raceId) ORDER BY start_number DESC LIMIT 1 ")
    suspend fun getHighestStartNumberByRace(raceId: UUID): Int

    /** Returns competitors assigned to a category. */
    @Query("SELECT * FROM competitor WHERE category_id=(:categoryId)")
    suspend fun getCompetitorsByCategory(categoryId: UUID): List<Competitor>

    /** Returns race competitors that are not assigned to a category. */
    @Query("SELECT * FROM competitor WHERE category_id IS NULL AND race_id=(:raceId)")
    suspend fun getUnmatchedCompetitorsByRace(raceId: UUID): List<Competitor>

    /** Counts competitors using the SI number in a race. */
    @Query("SELECT COUNT(*) FROM competitor WHERE si_number=(:siNumber) AND race_id =(:raceId)  LIMIT 1")
    suspend fun checkIfSINumberExists(siNumber: Int, raceId: UUID): Int

    /** Inserts or updates a competitor. */
    @Upsert
    suspend fun createCompetitor(competitor: Competitor)

    /** Deletes one competitor by primary key. */
    @Query("DELETE FROM competitor WHERE id =(:id)")
    suspend fun deleteCompetitor(id: UUID)

    /** Deletes all competitors assigned to a category. */
    @Query("DELETE FROM competitor WHERE category_id =(:categoryId)")
    suspend fun deleteCompetitorsByCategory(categoryId: UUID)

    /** Clears the category assignment for all competitors assigned to a category. */
    @Query("UPDATE competitor SET category_id = NULL WHERE category_id =(:categoryId)")
    suspend fun clearCompetitorCategory(categoryId: UUID)

    /** Deletes all competitors belonging to a race. */
    @Query("DELETE FROM competitor WHERE race_id =(:raceId)")
    suspend fun deleteAllCompetitorsByRace(raceId: UUID)
}
