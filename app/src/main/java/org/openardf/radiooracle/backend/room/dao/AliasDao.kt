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
import androidx.room.Upsert
import org.openardf.radiooracle.backend.room.entity.Alias
import java.util.UUID

/** Room access methods for control-point aliases scoped to a race. */
@Dao
interface AliasDao {
    /** Returns all aliases configured for a race. */
    @Query("SELECT * FROM alias WHERE race_id= (:raceId)")
    suspend fun getAliasesByRace(raceId: UUID): List<Alias>

    /** Returns one alias by primary key. */
    @Query("SELECT * FROM alias WHERE id=(:id)")
    suspend fun getAlias(id: UUID): Alias

    /** Inserts a new alias or updates the existing row with the same key. */
    @Upsert
    fun createOrUpdateAlias(alias: Alias)

    /** Deletes one alias by primary key. */
    @Query("DELETE FROM alias WHERE id =(:id) ")
    suspend fun deleteAlias(id: UUID)

    /** Deletes all aliases belonging to a race. */
    @Query("DELETE FROM alias WHERE race_id=(:raceId)")
    suspend fun deleteAliasesByRace(raceId: UUID)
}
