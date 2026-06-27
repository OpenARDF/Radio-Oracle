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

import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.wrappers.ResultWrapper
import org.openardf.radiooracle.shared.event.EventCategorySort

/** Android presentation sorting for category-bearing UI lists; stored category order is left unchanged. */
object CategoryDisplaySort {
    fun categoryData(categories: List<CategoryData>): List<CategoryData> =
        categories.sortedWith { left, right -> compareCategories(left.category, right.category) }

    fun categories(categories: List<Category>): List<Category> =
        categories.sortedWith(::compareCategories)

    fun resultWrappers(results: List<ResultWrapper>): List<ResultWrapper> =
        if (results.any { it.displayLabel != null }) {
            results
        } else {
            results.sortedWith { left, right -> compareNullableCategories(left.category, right.category) }
        }

    private fun compareNullableCategories(left: Category?, right: Category?): Int =
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> compareCategories(left, right)
        }

    private fun compareCategories(left: Category, right: Category): Int {
        val nameCompare = EventCategorySort.compareNames(left.name, right.name)
        return if (nameCompare != 0) {
            nameCompare
        } else {
            left.order.compareTo(right.order)
        }
    }
}
