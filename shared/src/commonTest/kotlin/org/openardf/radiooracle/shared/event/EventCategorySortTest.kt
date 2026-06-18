package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals

class EventCategorySortTest {
    @Test
    fun comparesCategoryNamesUsingDesktopDisplayOrder() {
        val names = listOf("M19", "W65", "M21", "W55", "M50", "M60")

        val sorted = names.sortedWith(EventCategorySort::compareNames)

        assertEquals(listOf("W55", "W65", "M19", "M21", "M50", "M60"), sorted)
    }
}
