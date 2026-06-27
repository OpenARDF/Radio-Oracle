package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.course.ControlPointValidationError
import org.openardf.radiooracle.shared.course.ControlPointValidationException
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
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

        validateCompetitors(raceData).takeIf { it.isNotEmpty() }?.let(issues::addAll)
        validateReadouts(raceData.competitorData.mapNotNull { it.readoutData }, issues)
        validateReadouts(raceData.unmatchedReadoutData, issues)

        return issues
    }

    /** Classifies validation issues so UI layers can distinguish blocking errors from review warnings. */
    fun severity(issue: EventValidationIssue): EventValidationIssueSeverity =
        when (issue) {
            is EventValidationIssue.LegacyIncompatibleAliasCodes,
            is EventValidationIssue.LegacyIncompatibleCategoryControlCodes,
            is EventValidationIssue.LegacyIncompatibleControlCodes,
            is EventValidationIssue.MissingCompetitorSiNumbers,
            is EventValidationIssue.MissingPublicLabels,
            is EventValidationIssue.UnusedControls -> EventValidationIssueSeverity.WARNING
            else -> EventValidationIssueSeverity.ERROR
        }

    @Suppress("DEPRECATION")
    private fun validateCategories(raceData: EventRaceData): List<EventValidationIssue> =
        buildList {
            if (raceData.categories.isEmpty()) {
                add(EventValidationIssue.NoCategories)
                return@buildList
            }
            val controlsById = raceData.controls.associateBy { it.id }
            raceData.categories.forEach { data ->
                try {
                    val definitions = if (data.controlPoints.isEmpty() &&
                        data.publicControlIds.isEmpty() &&
                        data.category.controlPointsString.isNotBlank()
                    ) {
                        ControlPointRules.parseAssignedControlPoints(
                            data.category.controlPointsString.trim(),
                            data.category.effectiveRaceType(raceData.race)
                        )
                    } else {
                        val controlPoints = when {
                            data.controlPoints.isNotEmpty() -> data.controlPoints
                            data.publicControlIds.isNotEmpty() -> data.publicControlIds.mapIndexed { index, controlId ->
                                val control = controlsById[controlId]
                                EventControlPoint(
                                    id = "public-$controlId",
                                    categoryId = data.category.id,
                                    siCode = control?.siCode ?: 0,
                                    type = control?.type ?: ControlPointType.CONTROL,
                                    order = index + 1,
                                    controlId = controlId
                                )
                            }
                            else -> emptyList()
                        }
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
                    ControlPointRules.parseAssignedControlPoints(
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
                    validateCategoryCourseRequirements(
                        categoryName = data.category.name,
                        raceType = data.category.effectiveRaceType(raceData.race),
                        definitions = definitions,
                        issues = this
                    )
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
            validateControlInventory(raceData, this)
            validatePublicLabels(raceData, this)
            validateUnusedControls(raceData, this)
        }

    private fun validateControlInventory(
        raceData: EventRaceData,
        issues: MutableList<EventValidationIssue>
    ) {
        val counts = controlRoleCounts(raceData.controls.map { ControlPointDefinition(it.siCode, it.type, 0) })
        val message = when (raceData.race.raceType) {
            RaceType.CLASSIC,
            RaceType.SHORT -> when {
                counts.foxes != 5 -> "Classic events should define exactly 5 fox controls; found ${counts.foxes}."
                counts.beacons != 1 -> "Classic events should define exactly 1 finish beacon; found ${counts.beacons}."
                counts.spectators != 0 -> "Classic events should not define spectator controls; found ${counts.spectators}."
                else -> null
            }
            RaceType.SPRINT -> when {
                counts.foxes != 10 -> "Sprint events should define exactly 10 fox controls; found ${counts.foxes}."
                counts.beacons != 1 -> "Sprint events should define exactly 1 finish beacon; found ${counts.beacons}."
                counts.spectators > 1 -> "Sprint events should define at most 1 spectator control; found ${counts.spectators}."
                else -> null
            }
            RaceType.FOXORING -> when {
                counts.foxes !in 4..12 -> "Foxoring events should define 4 to 12 fox controls; found ${counts.foxes}."
                counts.beacons != 1 -> "Foxoring events should define exactly 1 finish beacon; found ${counts.beacons}."
                counts.spectators != 0 -> "Foxoring events should not define spectator controls; found ${counts.spectators}."
                else -> null
            }
            RaceType.ORIENTEERING -> when {
                counts.foxes <= 0 -> "Orienteering events should define at least 1 control; found ${counts.foxes}."
                else -> null
            }
        }
        message?.let { issues.add(EventValidationIssue.ControlInventoryIssue(it)) }
    }

    private fun validateCategoryCourseRequirements(
        categoryName: String,
        raceType: RaceType,
        definitions: List<ControlPointDefinition>,
        issues: MutableList<EventValidationIssue>
    ) {
        if (definitions.isEmpty()) {
            issues.add(EventValidationIssue.MissingCategoryAssignedControls(categoryName))
            return
        }
        val counts = controlRoleCounts(definitions)
        val message = when (raceType) {
            RaceType.CLASSIC,
            RaceType.SHORT -> when {
                counts.foxes <= 0 -> "Classic category must assign at least one fox."
                counts.beacons != 1 -> "Classic category must assign exactly one finish beacon; found ${counts.beacons}."
                counts.spectators != 0 -> "Classic category must not assign spectator controls; found ${counts.spectators}."
                else -> null
            }
            RaceType.SPRINT -> when {
                counts.foxes != 10 -> "Sprint category must assign exactly 10 foxes; found ${counts.foxes}."
                counts.beacons != 1 -> "Sprint category must assign exactly one finish beacon; found ${counts.beacons}."
                counts.spectators > 1 -> "Sprint category must assign at most one spectator; found ${counts.spectators}."
                else -> null
            }
            RaceType.FOXORING -> when {
                counts.foxes <= 0 -> "Foxoring category must assign at least one fox."
                counts.beacons != 1 -> "Foxoring category must assign exactly one finish beacon; found ${counts.beacons}."
                counts.spectators != 0 -> "Foxoring category must not assign spectator controls; found ${counts.spectators}."
                else -> null
            }
            RaceType.ORIENTEERING -> when {
                counts.foxes <= 0 -> "Orienteering category must assign at least one control."
                else -> null
            }
        }
        message?.let { issues.add(EventValidationIssue.CategoryCourseRequirementIssue(categoryName, it)) }
    }

    private fun validatePublicLabels(
        raceData: EventRaceData,
        issues: MutableList<EventValidationIssue>
    ) {
        raceData.controls
            .filter { it.publicLabel.isNullOrBlank() }
            .map { controlDisplayName(it) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { issues.add(EventValidationIssue.MissingPublicLabels(it)) }

        raceData.controls
            .mapNotNull { it.publicLabel?.trim()?.takeIf(String::isNotEmpty) }
            .groupBy { it.lowercase() }
            .filterValues { it.size > 1 }
            .values
            .map { labels -> labels.first() }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { issues.add(EventValidationIssue.DuplicatePublicLabels(it)) }
    }

    private fun validateUnusedControls(
        raceData: EventRaceData,
        issues: MutableList<EventValidationIssue>
    ) {
        if (raceData.categories.isEmpty()) {
            return
        }
        val assignedIds = assignedControlIds(raceData)
        raceData.controls
            .filter { it.id !in assignedIds }
            .map { controlDisplayName(it) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { issues.add(EventValidationIssue.UnusedControls(it)) }
    }

    private fun validateAliases(aliases: List<EventAlias>): List<EventValidationIssue> =
        aliases
            .map { it.siCode }
            .filterNot(SportIdentCodes::isLegacyCompatibleSICode)
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(EventValidationIssue.LegacyIncompatibleAliasCodes(it)) }
            ?: emptyList()

    private fun validateCompetitors(raceData: EventRaceData): List<EventValidationIssue> {
        val competitors = raceData.competitorData
        val eventCompetitors = competitors.map { it.competitorCategory.competitor }
        return buildList {
            duplicateSiNumbersForValidation(raceData).takeIf { it.isNotEmpty() }?.let {
                add(EventValidationIssue.DuplicateSINumbers(it))
            }

            ImportValidationRules.duplicateBibNumbers(
                eventCompetitors.map { it.bibNumber }
            ).takeIf { it.isNotEmpty() }?.let { add(EventValidationIssue.DuplicateBibNumbers(it)) }

            ImportValidationRules.duplicateCallSigns(
                eventCompetitors.map { it.callSign }
            ).takeIf { it.isNotEmpty() }?.let { add(EventValidationIssue.DuplicateCallSigns(it)) }

            val expectedByStartTime = competitors
                .mapNotNull { it.competitorCategory.competitor.drawnStartTimeSeconds }
                .distinct()
                .sorted()
                .withIndex()
                .associate { (index, startSeconds) -> startSeconds to index + 1 }
            eventCompetitors
                .filter { competitor ->
                    val expected = competitor.drawnStartTimeSeconds?.let(expectedByStartTime::get)
                    competitor.startNumber != expected
                }
                .takeIf { it.isNotEmpty() }
                ?.let { mismatched -> add(EventValidationIssue.InvalidStartNumberAssignments(mismatched.map { it.id }.toSet())) }

            eventCompetitors
                .filter { it.siNumber == null || it.siNumber <= 0 }
                .map { it.fullName().ifBlank { it.id } }
                .toSet()
                .takeIf { it.isNotEmpty() }
                ?.let { add(EventValidationIssue.MissingCompetitorSiNumbers(it)) }
        }
    }

    private fun duplicateSiNumbersForValidation(raceData: EventRaceData): Set<Int> {
        val competitorsBySiNumber = raceData.competitorData
            .mapNotNull { data ->
                data.competitorCategory.competitor.siNumber?.let { siNumber -> siNumber to data }
            }
            .groupBy({ it.first }, { it.second })
        if (raceData.race.raceLevel != RaceLevel.PRACTICE) {
            return competitorsBySiNumber
                .filterValues { it.size > 1 }
                .keys
        }
        return competitorsBySiNumber
            .filterValues { competitors ->
                competitors.size > 1 &&
                    !competitors.all { data ->
                        data.readoutData?.result?.siNumber == data.competitorCategory.competitor.siNumber
                    }
            }
            .keys
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

    @Suppress("DEPRECATION")
    private fun assignedControlIds(raceData: EventRaceData): Set<String> {
        val idsByLegacyDefinition = raceData.controls
            .groupBy { it.siCode to it.type }
            .mapValues { (_, controls) -> controls.map { it.id } }
        return raceData.categories
            .flatMap { categoryData ->
                val legacyControlIds = if (
                    categoryData.controlPoints.isEmpty() &&
                    categoryData.publicControlIds.isEmpty() &&
                    categoryData.category.controlPointsString.isNotBlank()
                ) {
                    runCatching {
                        ControlPointRules.parseAssignedControlPoints(
                            categoryData.category.controlPointsString.trim(),
                            categoryData.category.effectiveRaceType(raceData.race)
                        ).flatMap { definition ->
                            idsByLegacyDefinition[definition.siCode to definition.type].orEmpty()
                        }
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                categoryData.publicControlIds +
                    categoryData.controlPoints.flatMap { controlPoint ->
                        if (controlPoint.controlId.isNotBlank()) {
                            listOf(controlPoint.controlId)
                        } else {
                            idsByLegacyDefinition[controlPoint.siCode to controlPoint.type].orEmpty()
                        }
                    } + legacyControlIds
            }
            .toSet()
    }

    private fun controlRoleCounts(definitions: List<ControlPointDefinition>): ControlRoleCounts {
        val grouped = definitions.groupingBy { it.type }.eachCount()
        return ControlRoleCounts(
            foxes = grouped[ControlPointType.CONTROL] ?: 0,
            beacons = grouped[ControlPointType.BEACON] ?: 0,
            spectators = grouped[ControlPointType.SEPARATOR] ?: 0
        )
    }

    private fun controlDisplayName(control: EventControl): String =
        control.publicLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: control.label.takeIf { it.isNotBlank() }
            ?: control.siCode.toString()
}

/** Machine-readable event validation issue used by Android and future desktop UI layers. */
sealed interface EventValidationIssue {
    data object BlankRaceName : EventValidationIssue
    data object NoCategories : EventValidationIssue
    data class DuplicateCategoryNames(val names: Set<String>) : EventValidationIssue
    data class DuplicateAliasNames(val names: Set<String>) : EventValidationIssue
    data class DuplicateAliasCodes(val codes: Set<Int>) : EventValidationIssue
    data class DuplicateControlIds(val ids: Set<String>) : EventValidationIssue
    data class DuplicateControlLabels(val labels: Set<String>) : EventValidationIssue
    data class ControlInventoryIssue(val message: String) : EventValidationIssue
    data class MissingPublicLabels(val controls: Set<String>) : EventValidationIssue
    data class DuplicatePublicLabels(val labels: Set<String>) : EventValidationIssue
    data class UnusedControls(val controls: Set<String>) : EventValidationIssue
    data class InvalidStartNumberAssignments(val competitorIds: Set<String>) : EventValidationIssue
    data class DuplicateSINumbers(val siNumbers: Set<Int>) : EventValidationIssue
    data class DuplicateBibNumbers(val bibNumbers: Set<String>) : EventValidationIssue
    data class DuplicateCallSigns(val callSigns: Set<String>) : EventValidationIssue
    data class MissingCompetitorSiNumbers(val competitorNames: Set<String>) : EventValidationIssue
    data class MultipleStartPunches(val siNumber: Int?) : EventValidationIssue
    data class MultipleFinishPunches(val siNumber: Int?) : EventValidationIssue
    data class LegacyIncompatibleCategoryControlCodes(val categoryName: String, val codes: Set<Int>) : EventValidationIssue
    data class LegacyIncompatibleAliasCodes(val codes: Set<Int>) : EventValidationIssue
    data class LegacyIncompatibleControlCodes(val codes: Set<Int>) : EventValidationIssue
    data class MissingCategoryControlReferences(val categoryName: String, val controlIds: Set<String>) : EventValidationIssue
    data class MissingCategoryAssignedControls(val categoryName: String) : EventValidationIssue
    data class CategoryCourseRequirementIssue(val categoryName: String, val message: String) : EventValidationIssue
    data class InvalidCategoryControlPoints(
        val categoryName: String,
        val error: ControlPointValidationError,
        val token: String?,
        val siCode: Int?
    ) : EventValidationIssue
}

enum class EventValidationIssueSeverity {
    ERROR,
    WARNING
}

private data class ControlRoleCounts(
    val foxes: Int,
    val beacons: Int,
    val spectators: Int
)
