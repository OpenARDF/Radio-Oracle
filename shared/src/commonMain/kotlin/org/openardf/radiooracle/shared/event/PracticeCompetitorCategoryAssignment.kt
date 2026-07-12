/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 */

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch

/** Shared category-selection rules for competitors discovered during Practice SI-card handling. */
object PracticeCompetitorCategoryAssignment {
    /** Uses the recorded course punches to choose the course that best explains the readout. */
    fun mostLikelyCategory(raceData: EventRaceData, controlPunchCodes: List<Int>): EventCategoryData? {
        if (controlPunchCodes.isEmpty()) {
            return longestCourseCategory(raceData)
        }
        val punches = controlPunchCodes.map { EvaluationPunch(it, SIRecordType.CONTROL) }
        return raceData.categories
            .filter { it.controlPoints.isNotEmpty() }
            .maxWithOrNull(
                compareBy<EventCategoryData> { categoryData ->
                    val evaluation = evaluation(raceData, categoryData, punches)
                    evaluation.resultStatus == ResultStatus.OK
                }.thenBy { categoryData ->
                    evaluation(raceData, categoryData, punches).punchStatuses.count { status ->
                        status == PunchStatus.VALID || status == PunchStatus.DUPLICATE
                    }
                }.thenBy { categoryData ->
                    -evaluation(raceData, categoryData, punches).punchStatuses.count { it == PunchStatus.UNKNOWN }
                }.thenBy { categoryData ->
                    evaluation(raceData, categoryData, punches).points
                }.thenBy { it.category.lengthMeters }
                    .thenBy { -it.category.order }
                    .thenByDescending { it.category.name }
            )
            ?: longestCourseCategory(raceData)
    }

    /** Chooses the longest configured course, with stable category ordering for ties. */
    fun longestCourseCategory(raceData: EventRaceData): EventCategoryData? =
        raceData.categories.maxWithOrNull(
            compareBy<EventCategoryData> { it.category.lengthMeters }
                .thenBy { -it.category.order }
                .thenByDescending { it.category.name }
        )

    /** Resolves the same category assignment in another series race by category name. */
    fun categoryNamedLike(raceData: EventRaceData, categoryName: String): EventCategoryData? =
        raceData.categories.firstOrNull { it.category.name.trim().equals(categoryName.trim(), ignoreCase = true) }

    private fun evaluation(
        raceData: EventRaceData,
        categoryData: EventCategoryData,
        punches: List<EvaluationPunch>
    ) = CourseEvaluator.evaluate(
        raceType = categoryData.category.effectiveRaceType(raceData.race),
        punches = punches,
        controlPoints = raceData.evaluationControlPoints(categoryData)
    )

    private fun EventRaceData.evaluationControlPoints(categoryData: EventCategoryData): List<EvaluationControlPoint> {
        val controlsById = controls.associateBy { it.id }
        return categoryData.controlPoints.map { controlPoint ->
            val control = controlsById[controlPoint.controlId]
            EvaluationControlPoint(
                siCode = control?.siCode ?: controlPoint.siCode,
                type = control?.type ?: controlPoint.type,
                scored = control?.scored ?: controlPoint.type.defaultScored(),
                label = control?.publicLabel ?: control?.label
            )
        }
    }
}
