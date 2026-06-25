package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile

data class DesktopCategoryRemovalResult(
    val projectFile: EventProjectFile,
    val categoryId: String,
    val categoryName: String,
    val deleteCompetitors: Boolean,
    val beforeCategoryIds: List<String>,
    val afterCategoryIds: List<String>,
    val beforeCompetitorIds: List<String>,
    val afterCompetitorIds: List<String>,
    val hadProtectedCourseData: Boolean
) {
    val removedCompetitorCount: Int =
        beforeCompetitorIds.count { it !in afterCompetitorIds.toSet() }
}

object DesktopCategoryActions {
    fun removeCategory(
        projectFile: EventProjectFile,
        categoryIdOrName: String,
        deleteCompetitors: Boolean
    ): DesktopCategoryRemovalResult {
        val matchingCategories = projectFile.raceData.categories.filter { categoryData ->
            categoryData.category.id == categoryIdOrName || categoryData.category.name == categoryIdOrName
        }
        require(matchingCategories.isNotEmpty()) {
            "Category was not found: $categoryIdOrName"
        }
        require(matchingCategories.size == 1) {
            "Category name is not unique: $categoryIdOrName"
        }

        val category = matchingCategories.single().category
        val beforeCategoryIds = projectFile.raceData.categories.map { it.category.id }
        val beforeCompetitorIds = projectFile.raceData.competitorData.map {
            it.competitorCategory.competitor.id
        }
        val updatedProject = EventProjectEditor.removeCategory(
            projectFile = projectFile,
            categoryId = category.id,
            deleteCompetitors = deleteCompetitors
        )
        val afterCategoryIds = updatedProject.raceData.categories.map { it.category.id }
        val afterCompetitorIds = updatedProject.raceData.competitorData.map {
            it.competitorCategory.competitor.id
        }

        return DesktopCategoryRemovalResult(
            projectFile = updatedProject,
            categoryId = category.id,
            categoryName = category.name,
            deleteCompetitors = deleteCompetitors,
            beforeCategoryIds = beforeCategoryIds,
            afterCategoryIds = afterCategoryIds,
            beforeCompetitorIds = beforeCompetitorIds,
            afterCompetitorIds = afterCompetitorIds,
            hadProtectedCourseData = category.encryptedIdealOrder?.isNotBlank() == true ||
                category.encryptedCourseInfo?.isNotBlank() == true
        )
    }
}
