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

import java.util.UUID
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventCategoryCompetitorSync
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.StandardCategoryRules

internal sealed interface DesktopCategoryListEdit {
    data class Add(val name: String) : DesktopCategoryListEdit

    data class Sync(
        val missingCategoryIds: Set<String>,
        val emptyCategoryIds: Set<String>
    ) : DesktopCategoryListEdit
}

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

data class DesktopCategoryListEditResult(
    val projectFile: EventProjectFile,
    val protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val protectedIdealOrderByCategoryId: Map<String, String>,
    val statusText: String
)

internal sealed interface DesktopCategoryListEditAttempt {
    data class RequiresCourseUnlock(
        val requestedName: String,
        val mappingName: String
    ) : DesktopCategoryListEditAttempt

    data class Applied(val result: DesktopCategoryListEditResult) : DesktopCategoryListEditAttempt

    data class Failed(val message: String) : DesktopCategoryListEditAttempt
}

object DesktopCategoryActions {
    internal fun attemptListEdit(
        projectFile: EventProjectFile?,
        edit: DesktopCategoryListEdit,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        protectedIdealOrderByCategoryId: Map<String, String>
    ): DesktopCategoryListEditAttempt {
        if (projectFile == null) return DesktopCategoryListEditAttempt.Failed("Edit failed: No Race File is open.")
        if (edit is DesktopCategoryListEdit.Add) {
            val lockedMappingName = courseMappingNameRequiringUnlock(
                projectFile = projectFile,
                requestedName = edit.name,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId
            )
            if (lockedMappingName != null) {
                return DesktopCategoryListEditAttempt.RequiresCourseUnlock(
                    requestedName = edit.name.trim(),
                    mappingName = lockedMappingName
                )
            }
        }
        return runCatching {
            applyListEdit(
                projectFile = projectFile,
                edit = edit,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                categoryIdFactory = { UUID.randomUUID().toString() },
                controlPointIdFactory = { UUID.randomUUID().toString() }
            )
        }.fold(
            onSuccess = DesktopCategoryListEditAttempt::Applied,
            onFailure = { error ->
                DesktopCategoryListEditAttempt.Failed(
                    "Edit failed: ${error.message ?: error::class.simpleName}"
                )
            }
        )
    }

    internal fun applyListEdit(
        projectFile: EventProjectFile,
        edit: DesktopCategoryListEdit,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        protectedIdealOrderByCategoryId: Map<String, String>,
        categoryIdFactory: () -> String,
        controlPointIdFactory: (Int) -> String
    ): DesktopCategoryListEditResult = when (edit) {
        is DesktopCategoryListEdit.Add -> addCategory(
            projectFile = projectFile,
            categoryId = categoryIdFactory(),
            name = edit.name,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
            controlPointIdFactory = controlPointIdFactory
        )
        is DesktopCategoryListEdit.Sync -> {
            val outcome = EventCategoryCompetitorSync.apply(
                projectFile = projectFile,
                missingCategoryIdsToSync = edit.missingCategoryIds,
                emptyCategoryIdsToRemove = edit.emptyCategoryIds
            )
            DesktopCategoryListEditResult(
                projectFile = outcome.projectFile,
                protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
                protectedIdealOrderByCategoryId = protectedIdealOrderByCategoryId,
                statusText = "Unsaved changes. Category sync added ${outcome.addedCategoryCount}, " +
                    "repaired ${outcome.repairedAssignmentCount} competitor assignments, " +
                    "and removed ${outcome.removedCategoryCount} empty categories."
            )
        }
    }

    fun courseMappingNameRequiringUnlock(
        projectFile: EventProjectFile?,
        requestedName: String,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): String? {
        val mappings = projectFile?.raceData?.courseMappings?.filter { courseMapping ->
            StandardCategoryRules.categoryNamesEquivalent(courseMapping.category.name, requestedName)
        }.orEmpty()
        val mapping = mappings.singleOrNull() ?: return null
        return mapping.category.name.takeIf {
            mapping.category.encryptedCourseInfo?.isNotBlank() == true &&
                protectedCourseInfoByCategoryId[mapping.category.id] == null
        }
    }

    private fun addCategory(
        projectFile: EventProjectFile,
        categoryId: String,
        name: String,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        protectedIdealOrderByCategoryId: Map<String, String>,
        controlPointIdFactory: (Int) -> String
    ): DesktopCategoryListEditResult {
        val outcome = EventProjectEditor.addCategoryActivatingCourseMapping(
            projectFile = projectFile,
            categoryId = categoryId,
            name = name,
            protectedCourseInfoByCategoryId = protectedCourseInfoByCategoryId,
            controlPointIdFactory = controlPointIdFactory
        )
        val mappingId = outcome.activatedCourseMappingId
        val activatedName = outcome.activatedCourseMappingName
        val activatedCategory = outcome.projectFile.raceData.categories
            .first { it.category.id == outcome.categoryId }
            .category
        val updatedCourseInfo = protectedCourseInfoByCategoryId
            .moveKey(mappingId, outcome.categoryId)
            .withValueIfAbsent(outcome.categoryId, activatedCategory.courseInfo)
        val updatedIdealOrder = protectedIdealOrderByCategoryId
            .moveKey(mappingId, outcome.categoryId)
            .withValueIfAbsent(outcome.categoryId, activatedCategory.idealOrder)
        val statusText = if (activatedName == null) {
            "Created category without course data. Add assigned controls or import KML/KMZ or GPX course data before Race Ops."
        } else {
            buildString {
                append("Activated the existing $activatedName course mapping: ")
                append("${activatedCategory.lengthMeters} m length, ${activatedCategory.climbMeters} m climb, ")
                append("and ${outcome.assignedControlCount} assigned control")
                append(if (outcome.assignedControlCount == 1) "." else "s.")
                if (outcome.unavailableControlCount > 0) {
                    append(" ${outcome.unavailableControlCount} stored control")
                    append(if (outcome.unavailableControlCount == 1) " was" else "s were")
                    append(" no longer present in Setup > Controls.")
                }
                append(" Unsaved changes.")
            }
        }
        return DesktopCategoryListEditResult(
            projectFile = outcome.projectFile,
            protectedCourseInfoByCategoryId = updatedCourseInfo,
            protectedIdealOrderByCategoryId = updatedIdealOrder,
            statusText = statusText
        )
    }

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
                category.encryptedCourseInfo?.isNotBlank() == true ||
                category.idealOrder?.isNotBlank() == true ||
                category.courseInfo != null
        )
    }
}

private fun <T> Map<String, T>.moveKey(oldKey: String?, newKey: String): Map<String, T> {
    if (oldKey == null) return this
    val value = this[oldKey]
    val withoutOldKey = this - oldKey
    return if (value == null) withoutOldKey else withoutOldKey + (newKey to value)
}

private fun <T> Map<String, T>.withValueIfAbsent(key: String, value: T?): Map<String, T> =
    if (key in this || value == null) this else this + (key to value)
