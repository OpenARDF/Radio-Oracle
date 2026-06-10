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
    val scored: Boolean = type == ControlPointType.CONTROL
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
        val separators = controlPoints.withIndex()
            .filter { it.value.type == ControlPointType.SEPARATOR }
            .map { it.value.siCode to it.index }
        val beacon = controlPoints.lastOrNull { it.type == ControlPointType.BEACON }

        var missingRequiredControl = false
        val points = if (separators.isNotEmpty()) {
            if (beacon == null) {
                missingRequiredControl = true
            }
            var total = 0
            var prevPunchSep = 0
            var prevControlSep = 0
            var separatorIndex = 0

            for ((punchIndex, punch) in punches.withIndex()) {
                if (separatorIndex < separators.size && punch.siCode == separators[separatorIndex].first) {
                    val loop = evaluateLoopInto(
                        punches,
                        controlPoints,
                        statuses,
                        punchRange = prevPunchSep..<punchIndex,
                        controlRange = prevControlSep..<separators[separatorIndex].second
                    )
                    total += loop.points
                    missingRequiredControl = missingRequiredControl || loop.missingRequiredControl
                    prevPunchSep = punchIndex
                    prevControlSep = separators[separatorIndex].second
                    separatorIndex++
                }
            }

            val finalLoop = evaluateLoopInto(
                punches,
                controlPoints,
                statuses,
                punchRange = prevPunchSep..<punches.size,
                controlRange = prevControlSep..<controlPoints.size
            )
            missingRequiredControl = missingRequiredControl || finalLoop.missingRequiredControl
            total + finalLoop.points
        } else if (beacon != null) {
            evaluateSprintWithBeaconTransition(punches, controlPoints, statuses, beacon).also {
                missingRequiredControl = it.missingRequiredControl
            }.points
        } else {
            val loop = evaluateLoopInto(
                punches,
                controlPoints,
                statuses,
                punchRange = punches.indices,
                controlRange = controlPoints.indices
            )
            missingRequiredControl = loop.missingRequiredControl
            loop.points
        }

        return CourseEvaluation(points, statusForRadioO(controlPoints, missingRequiredControl), statuses)
    }

    private fun evaluateSprintWithBeaconTransition(
        punches: List<EvaluationPunch>,
        controlPoints: List<EvaluationControlPoint>,
        statuses: MutableList<PunchStatus>,
        beacon: EvaluationControlPoint
    ): LoopEvaluation {
        val foxes = controlPoints.filter { it.type == ControlPointType.CONTROL }
        val slowLoopControls = foxes.filterNot { it.isSprintFastFox() } + beacon
        val fastLoopControls = foxes.filter { it.isSprintFastFox() } + beacon
        if (slowLoopControls.size == 1 || fastLoopControls.size == 1) {
            return evaluateLoopWithControls(punches, statuses, punches.indices, controlPoints)
        }
        val transitionPunchIndex = punches.indexOfFirst { it.siCode == beacon.siCode }
        if (transitionPunchIndex < 0) {
            return evaluateLoopWithControls(punches, statuses, punches.indices, slowLoopControls).copy(
                missingRequiredControl = true
            )
        }
        val firstLoop = evaluateLoopWithControls(
            punches,
            statuses,
            punchRange = 0..transitionPunchIndex,
            loopControls = slowLoopControls
        )
        val finalLoop = evaluateLoopWithControls(
            punches,
            statuses,
            punchRange = (transitionPunchIndex + 1)..punches.lastIndex,
            loopControls = fastLoopControls
        )
        return LoopEvaluation(
            points = firstLoop.points + finalLoop.points,
            statuses = statuses,
            missingRequiredControl = firstLoop.missingRequiredControl || finalLoop.missingRequiredControl
        )
    }

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

    private val EvaluationControlPoint.effectiveScored: Boolean
        get() = type == ControlPointType.CONTROL || scored

    private fun EvaluationControlPoint.isSprintFastFox(): Boolean =
        type == ControlPointType.CONTROL && siCode >= 40
}
