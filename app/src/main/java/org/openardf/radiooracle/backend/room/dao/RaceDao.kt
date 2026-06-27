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
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.openardf.radiooracle.backend.room.entity.Race
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Room access methods for race/event records. */
@Dao
interface RaceDao {
    /** Observes all races available in the local database. */
    @Query("SELECT * FROM race")
    fun getRaces(): Flow<List<Race>>

    /** Returns one race by primary key, or null when absent. */
    @Query("SELECT * FROM race WHERE id=(:id)")
    suspend fun getRace(id: UUID): Race?

    /** Inserts a new race. */
    @Insert
    suspend fun createRace(race: Race)

    /** Updates an existing race. */
    @Update
    suspend fun updateRace(race: Race)

    /** Deletes one race by primary key. */
    @Query("DELETE FROM race WHERE id =(:id)")
    suspend fun deleteRace(id: UUID)

    /** Deletes prior imported copies for the same source event, excluding the new clone. */
    @Query("DELETE FROM race WHERE import_source_id = (:importSourceId) AND id != (:raceId)")
    suspend fun deletePriorImportedCopies(importSourceId: String, raceId: UUID): Int
}
