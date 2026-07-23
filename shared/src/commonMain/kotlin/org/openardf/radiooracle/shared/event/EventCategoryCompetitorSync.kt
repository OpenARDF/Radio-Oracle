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

data class EventCategoryCompetitorSyncMissingCategory(
    val categoryId: String,
    val categoryName: String,
    val competitorCount: Int,
    val existingEquivalentCategoryId: String? = null
)

data class EventCategoryCompetitorSyncEmptyCategory(
    val categoryId: String,
    val categoryName: String,
    val hasCourseData: Boolean
)

data class EventCategoryCompetitorSyncPlan(
    val missingCategories: List<EventCategoryCompetitorSyncMissingCategory>,
    val emptyCategories: List<EventCategoryCompetitorSyncEmptyCategory>
) {
    val hasMismatch: Boolean
        get() = missingCategories.isNotEmpty() || emptyCategories.isNotEmpty()
}

data class EventCategoryCompetitorSyncOutcome(
    val projectFile: EventProjectFile,
    val addedCategoryCount: Int,
    val repairedAssignmentCount: Int,
    val removedCategoryCount: Int
)

object EventCategoryCompetitorSync {
    fun plan(raceData: EventRaceData): EventCategoryCompetitorSyncPlan {
        val categoriesById = raceData.categories.associateBy { it.category.id }
        val referencedCategoriesById = raceData.competitorData
            .mapNotNull { data ->
                val category = data.competitorCategory.category ?: return@mapNotNull null
                category.id to category
            }
            .groupBy({ it.first }, { it.second })
        val missingCategories = referencedCategoriesById
            .filterKeys { categoryId -> categoryId !in categoriesById }
            .mapNotNull { (categoryId, categories) ->
                val category = categories.firstOrNull { it.name.isNotBlank() } ?: return@mapNotNull null
                val equivalentCategoryId = raceData.categories
                    .firstOrNull { categoryData ->
                        StandardCategoryRules.categoryNamesEquivalent(categoryData.category.name, category.name)
                    }
                    ?.category
                    ?.id
                EventCategoryCompetitorSyncMissingCategory(
                    categoryId = categoryId,
                    categoryName = category.name,
                    competitorCount = raceData.competitorData.count { data ->
                        data.effectiveCategoryId() == categoryId
                    },
                    existingEquivalentCategoryId = equivalentCategoryId
                )
            }
            .sortedBy { it.categoryName }
        val categoryIdsReceivingRepairedAssignments = missingCategories
            .mapNotNullTo(mutableSetOf()) { it.existingEquivalentCategoryId }
        val assignedCountsByCategoryId = raceData.competitorData
            .mapNotNull { data -> data.effectiveCategoryId() }
            .groupingBy { it }
            .eachCount()
        val emptyCategories = raceData.categories
            .filter { categoryData ->
                assignedCountsByCategoryId[categoryData.category.id] == null &&
                    categoryData.category.id !in categoryIdsReceivingRepairedAssignments
            }
            .map { categoryData ->
                EventCategoryCompetitorSyncEmptyCategory(
                    categoryId = categoryData.category.id,
                    categoryName = categoryData.category.name,
                    hasCourseData = categoryData.hasCourseData()
                )
            }
            .sortedBy { it.categoryName }
        return EventCategoryCompetitorSyncPlan(
            missingCategories = missingCategories,
            emptyCategories = emptyCategories
        )
    }

    fun apply(
        projectFile: EventProjectFile,
        missingCategoryIdsToSync: Set<String>,
        emptyCategoryIdsToRemove: Set<String>
    ): EventCategoryCompetitorSyncOutcome {
        val syncPlan = plan(projectFile.raceData)
        var updatedProject = projectFile
        var addedCategoryCount = 0
        var repairedAssignmentCount = 0

        syncPlan.missingCategories
            .filter { it.categoryId in missingCategoryIdsToSync }
            .forEach { missing ->
                val targetCategoryId = missing.existingEquivalentCategoryId ?: missing.categoryId.also {
                    updatedProject = EventProjectEditor.addCategory(
                        projectFile = updatedProject,
                        categoryId = missing.categoryId,
                        name = missing.categoryName
                    )
                    addedCategoryCount += 1
                }
                val targetCategory = requireNotNull(
                    updatedProject.raceData.categories
                        .firstOrNull { it.category.id == targetCategoryId }
                        ?.category
                )
                updatedProject = updatedProject.copy(
                    raceData = updatedProject.raceData.copy(
                        competitorData = updatedProject.raceData.competitorData.map { data ->
                            if (data.effectiveCategoryId() == missing.categoryId) {
                                repairedAssignmentCount += 1
                                data.copy(
                                    competitorCategory = data.competitorCategory.copy(
                                        competitor = data.competitorCategory.competitor.copy(
                                            categoryId = targetCategory.id
                                        ),
                                        category = targetCategory
                                    )
                                )
                            } else {
                                data
                            }
                        }
                    )
                )
            }

        var removedCategoryCount = 0
        emptyCategoryIdsToRemove.forEach { categoryId ->
            val isStillEmpty = updatedProject.raceData.competitorData.none { data ->
                data.effectiveCategoryId() == categoryId
            }
            if (isStillEmpty && updatedProject.raceData.categories.any { it.category.id == categoryId }) {
                updatedProject = EventProjectEditor.removeCategory(
                    projectFile = updatedProject,
                    categoryId = categoryId,
                    deleteCompetitors = false
                )
                removedCategoryCount += 1
            }
        }

        val competitorsByCategoryId = updatedProject.raceData.competitorData
            .map { it.competitorCategory.competitor }
            .groupBy { it.categoryId }
        updatedProject = updatedProject.copy(
            raceData = updatedProject.raceData.copy(
                categories = updatedProject.raceData.categories.map { categoryData ->
                    categoryData.copy(
                        competitors = competitorsByCategoryId[categoryData.category.id].orEmpty()
                    )
                }
            )
        )
        return EventCategoryCompetitorSyncOutcome(
            projectFile = updatedProject,
            addedCategoryCount = addedCategoryCount,
            repairedAssignmentCount = repairedAssignmentCount,
            removedCategoryCount = removedCategoryCount
        )
    }

    private fun EventCompetitorData.effectiveCategoryId(): String? =
        competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

    @Suppress("DEPRECATION")
    private fun EventCategoryData.hasCourseData(): Boolean =
        controlPoints.isNotEmpty() ||
            publicControlIds.isNotEmpty() ||
            category.controlPointsString.isNotBlank() ||
            category.lengthMeters != 0 ||
            category.climbMeters != 0 ||
            category.encryptedIdealOrder?.isNotBlank() == true ||
            category.encryptedCourseInfo?.isNotBlank() == true
}
