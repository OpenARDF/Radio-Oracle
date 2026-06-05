package org.openardf.radiooracle.shared.event

/** Sort helpers for event-administration category lists. */
object EventCategorySort {
    val byDisplayName: Comparator<EventCategoryData> =
        compareBy<EventCategoryData> { categoryNameKey(it.category.name) }
            .thenBy { it.category.order }

    private fun categoryNameKey(name: String): CategoryNameKey {
        val trimmed = name.trim()
        val prefix = trimmed.takeWhile { it.isLetter() }.uppercase()
        val numberText = trimmed.drop(prefix.length).takeWhile { it.isDigit() }
        val suffix = trimmed.drop(prefix.length + numberText.length).uppercase()
        return CategoryNameKey(
            prefix = prefix,
            number = numberText.toIntOrNull() ?: Int.MAX_VALUE,
            suffix = suffix,
            fallback = trimmed.uppercase()
        )
    }

    private data class CategoryNameKey(
        val prefix: String,
        val number: Int,
        val suffix: String,
        val fallback: String
    ) : Comparable<CategoryNameKey> {
        override fun compareTo(other: CategoryNameKey): Int =
            compareValuesBy(this, other, CategoryNameKey::prefix, CategoryNameKey::number, CategoryNameKey::suffix, CategoryNameKey::fallback)
    }
}
