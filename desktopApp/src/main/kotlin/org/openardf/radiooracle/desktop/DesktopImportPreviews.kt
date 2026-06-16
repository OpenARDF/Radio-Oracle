package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.files.CategoryCsvImportRow
import org.openardf.radiooracle.shared.files.ControlCsvImportRow
import java.util.Locale

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
        val inferredTypes = inferredRaceTypes(sourceName, clues, controlCount, controlTypes)
            .filterNot { it == eventRaceType }
        if (inferredTypes.isEmpty()) {
            return emptyList()
        }
        val eventTypeText = eventRaceType.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }
        return inferredTypes.distinct().map { inferredType ->
            val importTypeText = inferredType.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }
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

    private fun inferredRaceTypes(
        sourceName: String,
        clues: List<String>,
        controlCount: Int?,
        controlTypes: List<ControlPointType>
    ): List<RaceType> {
        val sourceNameText = sourceName.lowercase()
        val haystack = (listOf(sourceName) + clues)
            .joinToString(" ")
            .lowercase()
        val sourceNameSuggestsFoxoring = sourceNameText.containsFoxoringToken()
        val foxCount = controlTypes.count { it == ControlPointType.CONTROL }
        val hasSpectator = controlTypes.any { it == ControlPointType.SEPARATOR }
        val exceedsSprintFoxLimit = foxCount > 10
        val hasSprintControlShape = controlCount != null &&
            controlCount > 6 &&
            foxCount in 1..10 &&
            hasSpectator
        return buildList {
            if (
                !sourceNameSuggestsFoxoring &&
                !exceedsSprintFoxLimit &&
                (haystack.contains("sprint") || hasSprintControlShape)
            ) {
                add(RaceType.SPRINT)
            }
            if (haystack.containsFoxoringToken()) {
                add(RaceType.FOXORING)
            }
            if (haystack.contains("classic")) {
                add(RaceType.CLASSIC)
            }
            if (haystack.contains("orienteering")) {
                add(RaceType.ORIENTEERING)
            }
        }
    }

    private fun String.containsFoxoringToken(): Boolean =
        contains("foxoring") ||
            contains("fox-o") ||
            contains("fox o") ||
            Regex("""\bfoxo\b""").containsMatchIn(this)
}
