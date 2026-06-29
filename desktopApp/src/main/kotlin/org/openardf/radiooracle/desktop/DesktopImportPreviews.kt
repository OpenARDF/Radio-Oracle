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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow

data class DesktopCategoryCsvImportPreview(
    val addedCount: Int,
    val updatedCount: Int,
    val affectedCompetitorCount: Int,
    val categoriesWithProtectedCoursePreservedCount: Int,
    val categoriesWithAssignedControlsReplacedCount: Int,
    val eventTypeWarnings: List<String>
)

data class DesktopControlsCsvImportPreview(
    val addedCount: Int,
    val changedCount: Int,
    val unchangedCount: Int,
    val missingExistingCount: Int,
    val removableMissingCount: Int,
    val usedMissingCount: Int,
    val affectedAssignedCategoryCount: Int,
    val affectedProtectedCourseCount: Int,
    val eventTypeWarnings: List<String>
)

object DesktopImportPreviews {
    fun categoryCsvPreview(
        projectFile: EventProjectFile,
        sourceName: String,
        rows: List<CategoryCsvImportRow>
    ): DesktopCategoryCsvImportPreview {
        val existingByName = projectFile.raceData.categories.associateBy { it.category.name }
        val competitorCountsByCategoryId = projectFile.raceData.competitorData
            .mapNotNull { data -> data.competitorCategory.competitor.categoryId ?: data.competitorCategory.category?.id }
            .groupingBy { it }
            .eachCount()
        var addedCount = 0
        var updatedCount = 0
        var affectedCompetitorCount = 0
        var categoriesWithProtectedCoursePreservedCount = 0
        var categoriesWithAssignedControlsReplacedCount = 0

        rows.forEach { row ->
            val existing = existingByName[row.name]
            if (existing == null) {
                addedCount++
            } else {
                updatedCount++
                affectedCompetitorCount += competitorCountsByCategoryId[existing.category.id] ?: 0
                if (existing.category.encryptedCourseInfo?.isNotBlank() == true ||
                    existing.category.encryptedIdealOrder?.isNotBlank() == true
                ) {
                    categoriesWithProtectedCoursePreservedCount++
                }
                if (existing.controlPoints.isNotEmpty() || existing.category.controlPointsString.isNotBlank()) {
                    categoriesWithAssignedControlsReplacedCount++
                }
            }
        }

        return DesktopCategoryCsvImportPreview(
            addedCount = addedCount,
            updatedCount = updatedCount,
            affectedCompetitorCount = affectedCompetitorCount,
            categoriesWithProtectedCoursePreservedCount = categoriesWithProtectedCoursePreservedCount,
            categoriesWithAssignedControlsReplacedCount = categoriesWithAssignedControlsReplacedCount,
            eventTypeWarnings = eventTypeWarnings(
                eventRaceType = projectFile.raceData.race.raceType,
                sourceName = sourceName,
                clues = rows.flatMap { row ->
                    listOfNotNull(row.name, row.raceType?.name, row.raceBand?.name, row.controlPointsText)
                },
                controlCount = null,
                controlTypes = emptyList()
            )
        )
    }

    fun categoryDataPreview(
        projectFile: EventProjectFile,
        sourceName: String,
        categories: List<EventCategoryData>
    ): DesktopCategoryCsvImportPreview {
        val existingByName = projectFile.raceData.categories.associateBy { it.category.name }
        val competitorCountsByCategoryId = projectFile.raceData.competitorData
            .mapNotNull { data -> data.competitorCategory.competitor.categoryId ?: data.competitorCategory.category?.id }
            .groupingBy { it }
            .eachCount()
        var addedCount = 0
        var updatedCount = 0
        var affectedCompetitorCount = 0
        var categoriesWithProtectedCoursePreservedCount = 0
        var categoriesWithAssignedControlsReplacedCount = 0

        categories.forEach { imported ->
            val existing = existingByName[imported.category.name]
            if (existing == null) {
                addedCount++
            } else {
                updatedCount++
                affectedCompetitorCount += competitorCountsByCategoryId[existing.category.id] ?: 0
                if (existing.category.encryptedCourseInfo?.isNotBlank() == true ||
                    existing.category.encryptedIdealOrder?.isNotBlank() == true
                ) {
                    categoriesWithProtectedCoursePreservedCount++
                }
                if (existing.controlPoints.isNotEmpty() || existing.category.controlPointsString.isNotBlank()) {
                    categoriesWithAssignedControlsReplacedCount++
                }
            }
        }

        return DesktopCategoryCsvImportPreview(
            addedCount = addedCount,
            updatedCount = updatedCount,
            affectedCompetitorCount = affectedCompetitorCount,
            categoriesWithProtectedCoursePreservedCount = categoriesWithProtectedCoursePreservedCount,
            categoriesWithAssignedControlsReplacedCount = categoriesWithAssignedControlsReplacedCount,
            eventTypeWarnings = eventTypeWarnings(
                eventRaceType = projectFile.raceData.race.raceType,
                sourceName = sourceName,
                clues = categories.flatMap { categoryData ->
                    listOf(categoryData.category.name) +
                        categoryData.controlPoints.map { point -> "${point.siCode}:${point.type.name}" }
                },
                controlCount = null,
                controlTypes = emptyList()
            )
        )
    }

    fun controlsCsvPreview(
        projectFile: EventProjectFile,
        sourceName: String,
        rows: List<ControlCsvImportRow>,
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): DesktopControlsCsvImportPreview {
        val existingByIdentity = projectFile.raceData.controls.associateBy { it.siCode to it.type }
        val changedControlIds = mutableSetOf<String>()
        var addedCount = 0
        var changedCount = 0
        var unchangedCount = 0

        rows.forEach { row ->
            val existing = existingByIdentity[row.siCode to row.type]
            when {
                existing == null -> addedCount++
                existing.scored != row.scored ||
                    existing.publicLabel.orEmpty() != row.publicLabel ||
                    existing.notes.orEmpty() != row.notes -> {
                    changedCount++
                    changedControlIds += existing.id
                }
                else -> unchangedCount++
            }
        }
        val importedIdentities = rows.mapTo(mutableSetOf()) { it.siCode to it.type }
        val missingExistingControls = projectFile.raceData.controls.filterNot { it.siCode to it.type in importedIdentities }
        val missingExistingIds = missingExistingControls.mapTo(mutableSetOf()) { it.id }
        val usedMissingIds = missingExistingIds.filterTo(mutableSetOf()) { controlId ->
            assignedCategoryUseCount(projectFile, setOf(controlId)) > 0 ||
                protectedCourseUseCount(protectedCourseInfoByCategoryId, setOf(controlId)) > 0
        }

        return DesktopControlsCsvImportPreview(
            addedCount = addedCount,
            changedCount = changedCount,
            unchangedCount = unchangedCount,
            missingExistingCount = missingExistingControls.size,
            removableMissingCount = missingExistingIds.size - usedMissingIds.size,
            usedMissingCount = usedMissingIds.size,
            affectedAssignedCategoryCount = assignedCategoryUseCount(projectFile, changedControlIds),
            affectedProtectedCourseCount = protectedCourseUseCount(protectedCourseInfoByCategoryId, changedControlIds),
            eventTypeWarnings = eventTypeWarnings(
                eventRaceType = projectFile.raceData.race.raceType,
                sourceName = sourceName,
                clues = rows.flatMap { listOf(it.publicLabel, it.notes, it.type.name) },
                controlCount = rows.size,
                controlTypes = rows.map { it.type }
            )
        )
    }

    fun eventTypeWarnings(
        eventRaceType: RaceType,
        sourceName: String,
        clues: List<String>,
        controlCount: Int? = null,
        controlTypes: List<ControlPointType> = emptyList()
    ): List<String> {
        val inferredTypes = DesktopCourseFormatDetector.inferredRaceTypes(sourceName, clues, controlCount, controlTypes)
            .filterNot { it == eventRaceType }
        if (inferredTypes.isEmpty()) {
            return emptyList()
        }
        val eventTypeText = DesktopCourseFormatDetector.run { eventRaceType.displayName() }
        return inferredTypes.distinct().map { inferredType ->
            val importTypeText = DesktopCourseFormatDetector.run { inferredType.displayName() }
            "Import data suggests $importTypeText, but the Event File is $eventTypeText."
        }
    }

    fun assignedCategoryUseCount(projectFile: EventProjectFile, controlIds: Set<String>): Int {
        if (controlIds.isEmpty()) return 0
        return projectFile.raceData.categories.count { categoryData ->
            categoryData.controlPoints.any { it.controlId in controlIds } ||
                categoryData.publicControlIds.any { it in controlIds }
        }
    }

    fun protectedCourseUseCount(
        protectedCourseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        controlIds: Set<String>
    ): Int {
        if (controlIds.isEmpty()) return 0
        return protectedCourseInfoByCategoryId.values.count { courseInfo ->
            courseInfo.controlPoints.any { it.controlId in controlIds } ||
                courseInfo.courseObjects.any { it.id in controlIds }
        }
    }

}
