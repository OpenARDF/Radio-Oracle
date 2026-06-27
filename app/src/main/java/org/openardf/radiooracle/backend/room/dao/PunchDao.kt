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
import org.openardf.radiooracle.backend.room.entity.Punch
import java.util.UUID

/** Room access methods for SportIdent punches. */
@Dao
interface PunchDao {

    /** Returns punches for one result in recorded order. */
    @Query("SELECT * FROM punch WHERE result_id= (:resultId) ORDER BY `order` ASC")
    suspend fun getPunchesByResult(resultId: UUID): List<Punch>

    /** Returns one punch by primary key. */
    @Query("SELECT * FROM punch WHERE id=(:id)")
    suspend fun getPunch(id: UUID): Punch

    /** Inserts a new punch or updates the existing row with the same key. */
    @Upsert
    fun createOrUpdatePunch(punch: Punch)

    /** Deletes one punch by primary key. */
    @Query("DELETE FROM punch WHERE id =(:id) ")
    suspend fun deletePunch(id: UUID)

    /** Deletes all punches belonging to a race. */
    @Query("DELETE FROM punch WHERE race_id=(:raceId)")
    suspend fun deletePunchesByRace(raceId: UUID)

    /** Deletes all punches belonging to a result. */
    @Query("DELETE FROM punch WHERE result_id=(:resultId)")
    suspend fun deletePunchesByResult(resultId: UUID)

}
