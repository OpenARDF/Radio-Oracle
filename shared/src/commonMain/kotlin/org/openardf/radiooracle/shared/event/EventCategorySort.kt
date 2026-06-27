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

package org.openardf.radiooracle.shared.event

/** Sort helpers for event-administration category lists. */
object EventCategorySort {
    val byDisplayName: Comparator<EventCategoryData> =
        compareBy<EventCategoryData> { categoryNameKey(it.category.name) }
            .thenBy { it.category.order }

    /** Compares category names with the same natural ARDF ordering used by category administration. */
    fun compareNames(left: String, right: String): Int =
        categoryNameKey(left).compareTo(categoryNameKey(right))

    private fun categoryNameKey(name: String): CategoryNameKey {
        val trimmed = name.trim()
        val prefix = trimmed.takeWhile { it.isLetter() }.uppercase()
        val numberText = trimmed.drop(prefix.length).takeWhile { it.isDigit() }
        val suffix = trimmed.drop(prefix.length + numberText.length).uppercase()
        return CategoryNameKey(
            presetGroup = presetGroup(prefix),
            prefix = prefix,
            number = numberText.toIntOrNull() ?: Int.MAX_VALUE,
            suffix = suffix,
            fallback = trimmed.uppercase()
        )
    }

    private fun presetGroup(prefix: String): Int =
        when (prefix) {
            "W", "D" -> 0
            "M" -> 1
            else -> 2
        }

    private data class CategoryNameKey(
        val presetGroup: Int,
        val prefix: String,
        val number: Int,
        val suffix: String,
        val fallback: String
    ) : Comparable<CategoryNameKey> {
        override fun compareTo(other: CategoryNameKey): Int =
            compareValuesBy(
                this,
                other,
                CategoryNameKey::presetGroup,
                CategoryNameKey::prefix,
                CategoryNameKey::number,
                CategoryNameKey::suffix,
                CategoryNameKey::fallback
            )
    }
}
