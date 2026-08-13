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

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.results.CourseEvaluator
import org.openardf.radiooracle.shared.results.EvaluationControlPoint
import org.openardf.radiooracle.shared.results.EvaluationPunch

/** Comparable evidence that one configured course explains an SI-card readout. */
data class EventReadoutCourseMatchQuality(
    val matchedPunchCount: Int,
    val unknownPunchCount: Int,
    val completedCourse: Boolean,
    val validPunchCount: Int,
    val points: Int,
    val unusedControlCodeCount: Int
) : Comparable<EventReadoutCourseMatchQuality> {
    override fun compareTo(other: EventReadoutCourseMatchQuality): Int =
        compareValuesBy(
            this,
            other,
            EventReadoutCourseMatchQuality::matchedPunchCount,
            { -it.unknownPunchCount },
            EventReadoutCourseMatchQuality::completedCourse,
            EventReadoutCourseMatchQuality::validPunchCount,
            EventReadoutCourseMatchQuality::points,
            { -it.unusedControlCodeCount }
        )
}

/** The configured course that best explains an SI-card readout in one race. */
data class EventReadoutCourseMatch(
    val category: EventCategoryData,
    val quality: EventReadoutCourseMatchQuality,
    val usesRegisteredCompetitorCategory: Boolean
)

/** Shared Android/desktop course matching for routing series SI-card readouts. */
object EventReadoutCourseMatcher {
    fun bestMatch(
        raceData: EventRaceData,
        siNumber: Int,
        controlPunchCodes: List<Int>
    ): EventReadoutCourseMatch? {
        if (controlPunchCodes.isEmpty()) {
            return null
        }
        val registeredCategoryIds = raceData.competitorData
            .asSequence()
            .map { it.competitorCategory.competitor }
            .filter { it.siNumber == siNumber }
            .mapNotNull { it.categoryId }
            .toSet()
        val registeredCategories = raceData.categories.filter { it.category.id in registeredCategoryIds }
        val candidateCategories = registeredCategories.ifEmpty { raceData.categories }
        val punches = controlPunchCodes.map { EvaluationPunch(it, SIRecordType.CONTROL) }

        return candidateCategories
            .mapNotNull { categoryData ->
                matchForCategory(
                    raceData = raceData,
                    categoryData = categoryData,
                    punches = punches,
                    punchCodes = controlPunchCodes,
                    usesRegisteredCompetitorCategory = categoryData.category.id in registeredCategoryIds
                )
            }
            .maxWithOrNull(
                compareBy<EventReadoutCourseMatch> { it.quality }
                    .thenBy { it.category.category.lengthMeters }
                    .thenBy { -it.category.category.order }
                    .thenByDescending { it.category.category.name }
            )
    }

    private fun matchForCategory(
        raceData: EventRaceData,
        categoryData: EventCategoryData,
        punches: List<EvaluationPunch>,
        punchCodes: List<Int>,
        usesRegisteredCompetitorCategory: Boolean
    ): EventReadoutCourseMatch? {
        val controlPoints = raceData.evaluationControlPoints(categoryData)
        if (controlPoints.isEmpty()) {
            return null
        }
        val evaluation = CourseEvaluator.evaluate(
            raceType = categoryData.category.effectiveRaceType(raceData.race),
            punches = punches,
            controlPoints = controlPoints
        )
        val controlCodes = controlPoints.mapTo(mutableSetOf()) { it.siCode }
        val punchedCodeSet = punchCodes.toSet()
        return EventReadoutCourseMatch(
            category = categoryData,
            quality = EventReadoutCourseMatchQuality(
                matchedPunchCount = punchCodes.count { it in controlCodes },
                unknownPunchCount = punchCodes.count { it !in controlCodes },
                completedCourse = evaluation.resultStatus == ResultStatus.OK,
                validPunchCount = evaluation.punchStatuses.count {
                    it == PunchStatus.VALID || it == PunchStatus.DUPLICATE
                },
                points = evaluation.points,
                unusedControlCodeCount = controlCodes.count { it !in punchedCodeSet }
            ),
            usesRegisteredCompetitorCategory = usesRegisteredCompetitorCategory
        )
    }

    private fun EventRaceData.evaluationControlPoints(
        categoryData: EventCategoryData
    ): List<EvaluationControlPoint> {
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
