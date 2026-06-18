package org.openardf.radiooracle.ui.categories

import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData

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

    private fun categoryData(name: String, order: Int): CategoryData {
        val category = Category(name)
        category.order = order
        return CategoryData(category, emptyList(), emptyList())
    }
}
