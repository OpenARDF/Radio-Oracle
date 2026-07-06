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

package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType

/** Control definition reduced to the fields needed by the course evaluator. */
data class EvaluationControlPoint(
    val siCode: Int,
    val type: ControlPointType,
    /**
     * True for an elective radio-o control worth one point when punched.
     *
     * False for a required zero-point radio-o control, such as a Beacon or
     * Spectator control. This flag is intentionally ignored for orienteering,
     * where every course control is required and scoring follows ordered-course
     * completion rules. Radio-o CONTROL role entries are treated as scored
     * foxes even when imported legacy data incorrectly stores scored=false.
     */
    val scored: Boolean = type == ControlPointType.CONTROL,
    /** Public or display label used to distinguish sprint S, B, and fast-loop controls. */
    val label: String? = null
)

/** Punch definition reduced to the fields needed by the course evaluator. */
data class EvaluationPunch(
    val siCode: Int,
    val type: SIRecordType
)

/** Course evaluation output: point total, final result status, and per-punch statuses. */
data class CourseEvaluation(
    val points: Int,
    val resultStatus: ResultStatus,
    val punchStatuses: List<PunchStatus>
)

/** Shared evaluator for ARDF/orienteering course completion rules. */
object CourseEvaluator {
    /** Evaluates punches against a course for the selected race type. */
    fun evaluate(
        raceType: RaceType,
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>
    ): CourseEvaluation {
        return when (raceType) {
            RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> evaluateClassics(punches, controlPoints)
            RaceType.SPRINT -> evaluateSprint(punches, controlPoints)
            RaceType.ORIENTEERING -> evaluateOrienteering(punches, controlPoints)
        }
    }

    private fun evaluateClassics(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>
    ): CourseEvaluation {
        val loop = evaluateLoop(punches, controlPoints)
        return CourseEvaluation(loop.points, statusForRadioO(controlPoints, loop.missingRequiredControl), loop.statuses)
    }

    private fun evaluateSprint(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>
    ): CourseEvaluation {
        val statuses = MutableList(punches.size) { PunchStatus.UNKNOWN }
        if (controlPoints.isEmpty()) {
            return CourseEvaluation(0, ResultStatus.NO_RANKING, statuses)
        }

        val course = SprintCourse.from(controlPoints)
        val transitionIndex = course.transitionControl?.let { transition ->
            punches.indexOfFirst { it.type == SIRecordType.CONTROL && it.siCode == transition.siCode }
        } ?: -1
        val hasTransition = transitionIndex >= 0
        val finalBeaconIndex = if (hasTransition && course.finishBeacon != null) {
            punches.indexOfLast { punch ->
                punch.type == SIRecordType.CONTROL && punch.siCode == course.finishBeacon.siCode
            }.takeIf { it > transitionIndex && it == punches.lastIndex }
        } else {
            null
        }

        val slowLoop = evaluateLoopWithControls(
            punches,
            statuses,
            punchRange = if (hasTransition) 0..<transitionIndex else punches.indices,
            loopControls = course.slowControls
        )
        if (hasTransition) {
            statuses[transitionIndex] = PunchStatus.VALID
        }

        val fastLoop = if (hasTransition) {
            evaluateLoopWithControls(
                punches,
                statuses,
                punchRange = closedRangeOrEmpty(transitionIndex + 1, (finalBeaconIndex ?: punches.size) - 1),
                loopControls = course.fastControls
            )
        } else {
            LoopEvaluation(0, statuses, missingRequiredControl = true)
        }
        if (finalBeaconIndex != null) {
            statuses[finalBeaconIndex] = PunchStatus.VALID
        }

        val missingRequiredControl =
            course.transitionControl == null ||
                !hasTransition ||
                course.slowControls.isEmpty() ||
                course.fastControls.isEmpty() ||
                slowLoop.points == 0 ||
                fastLoop.points == 0

        return CourseEvaluation(
            points = slowLoop.points + fastLoop.points,
            resultStatus = statusForRadioO(controlPoints, missingRequiredControl),
            punchStatuses = statuses
        )
    }

    private fun closedRangeOrEmpty(start: Int, endInclusive: Int): IntRange =
        if (start <= endInclusive) start..endInclusive else IntRange.EMPTY

    private fun evaluateOrienteering(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>
    ): CourseEvaluation {
        val statuses = MutableList(punches.size) { PunchStatus.UNKNOWN }
        var controlPointIndex = 0
        var points = 0

        for ((punchIndex, punch) in punches.withIndex()) {
            if (controlPointIndex >= controlPoints.size) {
                break
            }

            if (punch.siCode == controlPoints[controlPointIndex].siCode) {
                controlPointIndex++
                points++
                statuses[punchIndex] = PunchStatus.VALID
            } else {
                statuses[punchIndex] = PunchStatus.INVALID
            }
        }

        return CourseEvaluation(
            points = points,
            resultStatus = if (points == controlPoints.size) ResultStatus.OK else ResultStatus.MISPUNCHED,
            punchStatuses = statuses
        )
    }

    private fun evaluateLoop(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>
    ): LoopEvaluation {
        val statuses = MutableList(punches.size) { PunchStatus.UNKNOWN }
        return evaluateLoopInto(punches, controlPoints, statuses, punches.indices, controlPoints.indices)
    }

    private fun evaluateLoopInto(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>,
        statuses: MutableList<PunchStatus>,
        punchRange: IntRange,
        controlRange: IntRange
    ): LoopEvaluation {
        val loopControls = controlRange.map { controlPoints[it] }
        return evaluateLoopWithControls(punches, statuses, punchRange, loopControls)
    }

    private fun evaluateLoopWithControls(
        punches: List<EvaluationPunch>,
        statuses: MutableList<PunchStatus>,
        punchRange: IntRange,
        loopControls: List<EvaluationControlPoint>
    ): LoopEvaluation {
        val controlsByCode = loopControls.groupBy { it.siCode }
        val takenScored = mutableSetOf<Int>()
        val fulfilledRequired = mutableSetOf<Int>()
        var points = 0
        /*
         * Radio-o beacons keep their role-specific "last punch in the loop"
         * constraint whether or not an organizer manually marks one as scored.
         * Normally Beacon is not scored, but retaining the role rule makes a
         * mistimed beacon visibly invalid instead of silently accepting it.
         */
        val beacon = if (loopControls.isNotEmpty() && loopControls.last().type == ControlPointType.BEACON) {
            loopControls.last().siCode
        } else {
            -1
        }

        for (punchIndex in punchRange) {
            val punch = punches[punchIndex]
            val matchingControl = controlsByCode[punch.siCode]?.firstOrNull()
            if (punch.type == SIRecordType.CONTROL && matchingControl != null) {
                if (punch.siCode == beacon) {
                    if (punchIndex == punchRange.last) {
                        statuses[punchIndex] = PunchStatus.VALID
                        if (matchingControl.scored && takenScored.add(punch.siCode)) {
                            points++
                        }
                        if (!matchingControl.scored) {
                            fulfilledRequired.add(punch.siCode)
                        }
                    } else {
                        statuses[punchIndex] = PunchStatus.INVALID
                    }
                } else if (matchingControl.effectiveScored) {
                    if (takenScored.add(punch.siCode)) {
                        statuses[punchIndex] = PunchStatus.VALID
                        points++
                    } else {
                        statuses[punchIndex] = PunchStatus.DUPLICATE
                    }
                } else {
                    if (fulfilledRequired.add(punch.siCode)) {
                        statuses[punchIndex] = PunchStatus.VALID
                    } else {
                        statuses[punchIndex] = PunchStatus.DUPLICATE
                    }
                }
            } else {
                statuses[punchIndex] = PunchStatus.UNKNOWN
            }
        }
        return LoopEvaluation(
            points = points,
            statuses = statuses,
            missingRequiredControl = loopControls.any { !it.effectiveScored && !fulfilledRequired.contains(it.siCode) }
        )
    }

    private fun statusForRadioO(
        controlPoints: List<EvaluationControlPoint>,
        missingRequiredControl: Boolean
    ): ResultStatus =
        when {
            controlPoints.isEmpty() -> ResultStatus.NO_RANKING
            missingRequiredControl -> ResultStatus.DID_NOT_FINISH
            else -> ResultStatus.OK
        }

    private data class LoopEvaluation(
        val points: Int,
        val statuses: List<PunchStatus>,
        val missingRequiredControl: Boolean
    )

    private data class SprintCourse(
        val slowControls: List<EvaluationControlPoint>,
        val fastControls: List<EvaluationControlPoint>,
        val transitionControl: EvaluationControlPoint?,
        val finishBeacon: EvaluationControlPoint?
    ) {
        companion object {
            fun from(controlPoints: List<EvaluationControlPoint>): SprintCourse {
                val spectator = controlPoints.firstOrNull { it.isSprintSpectator }
                val beacon = controlPoints.lastOrNull { it.isSprintBeacon }
                val spectatorIndex = spectator?.let { controlPoints.indexOf(it) } ?: -1
                val controls = controlPoints.withIndex().filter { (_, control) ->
                    control.type == ControlPointType.CONTROL &&
                        !control.isSprintSpectator &&
                        !control.isSprintBeacon
                }
                // When fast-loop labels are available, they are the reliable loop boundary even if import order differs.
                val hasFastLabels = controls.any { it.value.hasSprintFastLabel }

                val slowControls = controls.filter { (index, control) ->
                    !control.hasSprintFastLabel &&
                        when {
                            hasFastLabels -> true
                            spectatorIndex >= 0 -> index < spectatorIndex
                            else -> !control.isSprintFastFoxByCode()
                        }
                }.map { it.value }
                val fastControls = controls.filter { (index, control) ->
                    control.hasSprintFastLabel ||
                        when {
                            hasFastLabels -> false
                            spectatorIndex >= 0 -> index > spectatorIndex
                            else -> control.isSprintFastFoxByCode()
                        }
                }.map { it.value }
                return SprintCourse(
                    slowControls = slowControls,
                    fastControls = fastControls,
                    transitionControl = spectator ?: beacon,
                    finishBeacon = beacon
                )
            }
        }
    }

    private val EvaluationControlPoint.effectiveScored: Boolean
        get() = type == ControlPointType.CONTROL || scored

    private val EvaluationControlPoint.normalizedLabel: String
        get() = label?.trim()?.uppercase().orEmpty()

    private val EvaluationControlPoint.isSprintSpectator: Boolean
        get() = type == ControlPointType.SEPARATOR ||
            normalizedLabel in setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")

    private val EvaluationControlPoint.isSprintBeacon: Boolean
        get() = type == ControlPointType.BEACON ||
            normalizedLabel in setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")

    private val EvaluationControlPoint.hasSprintFastLabel: Boolean
        get() {
            val label = normalizedLabel
            return label.endsWith("F") && label.dropLast(1).all { it.isDigit() } ||
                label.startsWith("F") && label.drop(1).all { it.isDigit() } ||
                label.contains("FAST")
        }

    private fun EvaluationControlPoint.isSprintFastFoxByCode(): Boolean =
        type == ControlPointType.CONTROL && siCode >= 40
}
