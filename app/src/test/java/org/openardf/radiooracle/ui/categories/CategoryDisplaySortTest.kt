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

package org.openardf.radiooracle.ui.categories

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper

class CategoryDisplaySortTest {
    @Test
    fun sortsCategoryDataForDisplayWithoutChangingStoredOrder() {
        val categories = listOf(
            categoryData("M19", order = 5),
            categoryData("W65", order = 3),
            categoryData("M21", order = 0),
            categoryData("W55", order = 4),
            categoryData("M50", order = 2),
            categoryData("M60", order = 1)
        )

        val sorted = CategoryDisplaySort.categoryData(categories)

        assertEquals(listOf("W55", "W65", "M19", "M21", "M50", "M60"), sorted.map { it.category.name })
        assertEquals(listOf(4, 3, 5, 0, 2, 1), sorted.map { it.category.order })
    }

    @Test
    fun preservesSeriesResultWrapperOrderWhenDisplayLabelsArePresent() {
        val results = listOf(
            resultWrapper("Day 2 - M21", "M21"),
            resultWrapper("Day 1 - W21", "W21"),
            resultWrapper("Day 1 - M21", "M21")
        )

        val sorted = CategoryDisplaySort.resultWrappers(results)

        assertEquals(
            listOf("Day 2 - M21", "Day 1 - W21", "Day 1 - M21"),
            sorted.map { it.displayLabel }
        )
    }

    private fun categoryData(name: String, order: Int): CategoryData {
        val category = Category(name)
        category.order = order
        return CategoryData(category, emptyList(), emptyList())
    }

    private fun resultWrapper(displayLabel: String, categoryName: String): ResultWrapper =
        ResultWrapper(
            category = Category(categoryName),
            finished = 0,
            displayLabel = displayLabel
        )
}
