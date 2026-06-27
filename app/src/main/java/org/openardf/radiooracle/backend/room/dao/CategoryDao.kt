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
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Room access methods for categories and their related course data. */
@Dao
interface CategoryDao {

    /** Observes category aggregates for a race in display order. */
    @Transaction
    @Query("SELECT * FROM category WHERE race_id=(:raceId) ORDER BY `order`")
    fun getCategoryFlowForRace(raceId: UUID): Flow<List<CategoryData>>

    /** Returns category entities for a race in display order. */
    @Query("SELECT * FROM category WHERE race_id=(:raceId) ORDER BY `order`")
    suspend fun getCategoriesForRace(raceId: UUID): List<Category>

    /** Returns one category entity by primary key, or null when absent. */
    @Query("SELECT * FROM category WHERE id=(:id) LIMIT 1")
    suspend fun getCategory(id: UUID): Category?

    /** Returns one category aggregate by primary key, or null when absent. */
    @Transaction
    @Query("SELECT * FROM category WHERE id=(:id) LIMIT 1")
    suspend fun getCategoryData(id: UUID): CategoryData?

    /** Returns all category aggregates for a race. */
    @Transaction
    @Query("SELECT * FROM category WHERE  race_id=(:raceId) ")
    suspend fun getCategoryDataForRace(raceId: UUID): List<CategoryData>

    /** Returns the highest display-order value currently used in a race. */
    @Query("SELECT `order` FROM category WHERE race_id =(:raceId) ORDER BY `order` DESC LIMIT 1")
    suspend fun getHighestCategoryOrder(raceId: UUID): Int

    /** Finds a category by name within one race. */
    @Query("SELECT * FROM category WHERE name=(:name) AND race_id = (:raceId) LIMIT 1")
    suspend fun getCategoryByName(name: String, raceId: UUID): Category?

    /** Inserts a new category or updates the existing row with the same key. */
    @Upsert
    suspend fun createOrUpdateCategory(category: Category)

    /** Deletes one category by primary key. */
    @Query("DELETE FROM category WHERE id=(:id) ")
    suspend fun deleteCategory(id: UUID)
}
