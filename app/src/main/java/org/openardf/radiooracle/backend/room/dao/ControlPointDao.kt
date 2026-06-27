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
import androidx.room.Transaction
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.embeddeds.ControlPointAlias
import java.util.UUID

/** Room access methods for category control points and alias joins. */
@Dao
interface ControlPointDao {
    /** Returns ordered control points for a category. */
    @Query("SELECT * FROM control_point WHERE category_id=(:categoryId) ORDER BY `order`ASC")
    suspend fun getControlPointsByCategory(categoryId: UUID): List<ControlPoint>

    /** Returns ordered control points joined with any matching aliases. */
    @Transaction
    @Query("SELECT * FROM control_point WHERE category_id=(:categoryId) ORDER BY `order`ASC")
    suspend fun getControlPointAliasesByCategory(categoryId: UUID): List<ControlPointAlias>

    /** Returns one control point by primary key. */
    @Query("SELECT * FROM control_point WHERE id=(:id) LIMIT 1")
    suspend fun getControlPoint(id: UUID): ControlPoint

    /** Inserts one control point. */
    @Insert
    suspend fun createControlPoint(controlPoint: ControlPoint)

    /** Deletes one control point by primary key. */
    @Query("DELETE FROM control_point WHERE id =(:id) ")
    suspend fun deleteControlPoint(id: UUID)

    /** Deletes all control points belonging to a category. */
    @Query("DELETE FROM control_point WHERE category_id=(:categoryId)")
    suspend fun deleteControlPointsByCategory(categoryId: UUID)

}
