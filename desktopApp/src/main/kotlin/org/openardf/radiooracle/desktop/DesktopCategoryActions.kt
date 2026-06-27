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
