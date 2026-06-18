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
        results.sortedWith { left, right -> compareNullableCategories(left.category, right.category) }

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
