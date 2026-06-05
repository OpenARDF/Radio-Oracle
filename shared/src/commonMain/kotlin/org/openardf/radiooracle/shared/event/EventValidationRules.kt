package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.course.ControlPointValidationError
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.importing.ImportValidationRules
import org.openardf.radiooracle.shared.importing.ReadoutPunchValidationError
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Shared validation service for complete event aggregates. */
object EventValidationRules {
    /** Returns all currently supported validation issues without localizing messages. */
    fun validateRaceData(raceData: EventRaceData): List<EventValidationIssue> {
        val issues = mutableListOf<EventValidationIssue>()

        if (raceData.race.name.isEmpty()) {
            issues.add(EventValidationIssue.BlankRaceName)
        }

        ImportValidationRules.duplicateCategoryNames(
            raceData.categories.map { it.category.name }
        ).takeIf { it.isNotEmpty() }?.let { duplicateNames ->
            issues.add(EventValidationIssue.DuplicateCategoryNames(duplicateNames))
        }
        validateCategories(raceData).takeIf { it.isNotEmpty() }?.let(issues::addAll)
        validateControls(raceData).takeIf { it.isNotEmpty() }?.let(issues::addAll)

        ImportValidationRules.duplicateAliasNames(
            raceData.aliases.map { it.name }
        ).takeIf { it.isNotEmpty() }?.let { duplicateNames ->
            issues.add(EventValidationIssue.DuplicateAliasNames(duplicateNames))
        }

        ImportValidationRules.duplicateAliasCodes(
            raceData.aliases.map { it.siCode }
        ).takeIf { it.isNotEmpty() }?.let { duplicateCodes ->
            issues.add(EventValidationIssue.DuplicateAliasCodes(duplicateCodes))
        }
        validateAliases(raceData.aliases).takeIf { it.isNotEmpty() }?.let(issues::addAll)

        validateCompetitors(raceData.competitorData).takeIf { it.isNotEmpty() }?.let(issues::addAll)
        validateReadouts(raceData.competitorData.mapNotNull { it.readoutData }, issues)
        validateReadouts(raceData.unmatchedReadoutData, issues)

        return issues
    }

    private fun validateCategories(raceData: EventRaceData): List<EventValidationIssue> =
        buildList {
            val controlsById = raceData.controls.associateBy { it.id }
            raceData.categories.forEach { data ->
                try {
                    val definitions = if (data.controlPoints.isEmpty() && data.category.controlPointsString.isNotBlank()) {
                        ControlPointRules.parseControlPoints(
                            data.category.controlPointsString.trim(),
                            data.category.effectiveRaceType(raceData.race)
                        )
                    } else {
                        val controlPoints = data.controlPoints
                        val missingControlIds = controlPoints
                        .map { it.controlId }
                        .filter { it.isBlank() || controlsById[it] == null }
                        .toSet()
                        if (missingControlIds.isNotEmpty()) {
                            add(EventValidationIssue.MissingCategoryControlReferences(data.category.name, missingControlIds))
                        }
                        controlPoints.map {
                            org.openardf.radiooracle.shared.course.ControlPointDefinition(
                                siCode = controlsById[it.controlId]?.siCode ?: it.siCode,
                                type = controlsById[it.controlId]?.type ?: it.type,
                                order = it.order
                            )
                        }
                    }
                    ControlPointRules.parseControlPoints(
                        ControlPointRules.formatControlPoints(definitions),
                        data.category.effectiveRaceType(raceData.race)
                    )
                    definitions
                        .map { it.siCode }
                        .filterNot(SportIdentCodes::isLegacyCompatibleSICode)
                        .toSet()
                        .takeIf { it.isNotEmpty() }
                        ?.let { codes ->
                            add(EventValidationIssue.LegacyIncompatibleCategoryControlCodes(data.category.name, codes))
                        }
                } catch (exception: ControlPointValidationException) {
                    add(
                        EventValidationIssue.InvalidCategoryControlPoints(
                            categoryName = data.category.name,
                            error = exception.error,
                            token = exception.token,
                            siCode = exception.siCode
                        )
                    )
                }
            }
        }

    private fun validateControls(raceData: EventRaceData): List<EventValidationIssue> =
        buildList {
            val duplicateIds = raceData.controls.groupBy { it.id }.filterValues { it.size > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                add(EventValidationIssue.DuplicateControlIds(duplicateIds))
            }
            val duplicateLabels = raceData.controls.groupBy { it.label }.filterValues { it.size > 1 }.keys
            if (duplicateLabels.isNotEmpty()) {
                add(EventValidationIssue.DuplicateControlLabels(duplicateLabels))
            }
            raceData.controls
                .map { it.siCode }
                .filterNot(SportIdentCodes::isLegacyCompatibleSICode)
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?.let { codes -> add(EventValidationIssue.LegacyIncompatibleControlCodes(codes)) }
        }

    private fun validateAliases(aliases: List<EventAlias>): List<EventValidationIssue> =
        aliases
            .map { it.siCode }
            .filterNot(SportIdentCodes::isLegacyCompatibleSICode)
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(EventValidationIssue.LegacyIncompatibleAliasCodes(it)) }
            ?: emptyList()

    private fun validateCompetitors(competitors: List<EventCompetitorData>): List<EventValidationIssue> {
        val eventCompetitors = competitors.map { it.competitorCategory.competitor }
        return buildList {
            ImportValidationRules.duplicateStartNumbers(
                eventCompetitors.map { it.startNumber }
            ).takeIf { it.isNotEmpty() }?.let { add(EventValidationIssue.DuplicateStartNumbers(it)) }

            ImportValidationRules.duplicateSINumbers(
                eventCompetitors.map { it.siNumber }
            ).takeIf { it.isNotEmpty() }?.let { add(EventValidationIssue.DuplicateSINumbers(it)) }
        }
    }

    private fun validateReadouts(
        readouts: List<EventReadoutData>,
        issues: MutableList<EventValidationIssue>
    ) {
        readouts.forEach { readout ->
            val punchErrors = ImportValidationRules.validateReadoutPunchTypes(
                readout.punches.map { it.punch.punchType }
            )
            if (punchErrors.contains(ReadoutPunchValidationError.MULTIPLE_START)) {
                issues.add(EventValidationIssue.MultipleStartPunches(readout.result.siNumber))
            }
            if (punchErrors.contains(ReadoutPunchValidationError.MULTIPLE_FINISH)) {
                issues.add(EventValidationIssue.MultipleFinishPunches(readout.result.siNumber))
            }
        }
    }
}

/** Machine-readable event validation issue used by Android and future desktop UI layers. */
sealed interface EventValidationIssue {
    data object BlankRaceName : EventValidationIssue
    data class DuplicateCategoryNames(val names: Set<String>) : EventValidationIssue
    data class DuplicateAliasNames(val names: Set<String>) : EventValidationIssue
    data class DuplicateAliasCodes(val codes: Set<Int>) : EventValidationIssue
    data class DuplicateControlIds(val ids: Set<String>) : EventValidationIssue
    data class DuplicateControlLabels(val labels: Set<String>) : EventValidationIssue
    data class DuplicateStartNumbers(val startNumbers: Set<Int>) : EventValidationIssue
    data class DuplicateSINumbers(val siNumbers: Set<Int>) : EventValidationIssue
    data class MultipleStartPunches(val siNumber: Int?) : EventValidationIssue
    data class MultipleFinishPunches(val siNumber: Int?) : EventValidationIssue
    data class LegacyIncompatibleCategoryControlCodes(val categoryName: String, val codes: Set<Int>) : EventValidationIssue
    data class LegacyIncompatibleAliasCodes(val codes: Set<Int>) : EventValidationIssue
    data class LegacyIncompatibleControlCodes(val codes: Set<Int>) : EventValidationIssue
    data class MissingCategoryControlReferences(val categoryName: String, val controlIds: Set<String>) : EventValidationIssue
    data class InvalidCategoryControlPoints(
        val categoryName: String,
        val error: ControlPointValidationError,
        val token: String?,
        val siCode: Int?
    ) : EventValidationIssue
}
