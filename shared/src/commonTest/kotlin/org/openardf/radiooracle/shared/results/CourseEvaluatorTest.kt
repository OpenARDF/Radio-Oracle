package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals

class CourseEvaluatorTest {
    @Test
    fun evaluatesClassicControlsDuplicatesAndBeacon() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.CLASSIC,
            punches = punches(31, 31, 36, 32, 36),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                36 to ControlPointType.BEACON
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.VALID,
                PunchStatus.DUPLICATE,
                PunchStatus.INVALID,
                PunchStatus.VALID,
                PunchStatus.VALID
            ),
            evaluation.punchStatuses
        )
    }

    @Test
    fun evaluatesSprintBySeparatingLoops() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(31, 32, 40, 31, 32, 36),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                40 to ControlPointType.SEPARATOR,
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                36 to ControlPointType.BEACON
            )
        )

        assertEquals(4, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID
            ),
            evaluation.punchStatuses
        )
    }

    @Test
    fun missingRadioOMandatoryZeroPointControlIsDnf() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.CLASSIC,
            punches = punches(31, 32),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                36 to ControlPointType.BEACON
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.DID_NOT_FINISH, evaluation.resultStatus)
        assertEquals(
            listOf(PunchStatus.VALID, PunchStatus.VALID),
            evaluation.punchStatuses
        )
    }

    @Test
    fun radioOControlRoleScoresEvenIfLegacyDataMarksItUnscored() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.CLASSIC,
            punches = punches(31, 32, 36),
            controlPoints = listOf(
                EvaluationControlPoint(31, ControlPointType.CONTROL, scored = false),
                EvaluationControlPoint(32, ControlPointType.CONTROL, scored = false),
                EvaluationControlPoint(36, ControlPointType.BEACON, scored = false)
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(PunchStatus.VALID, PunchStatus.VALID, PunchStatus.VALID),
            evaluation.punchStatuses
        )
    }

    @Test
    fun sprintWithoutSpectatorUsesBeaconAsLoopTransitionAndFinish() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(31, 32, 36, 41, 42, 36),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                41 to ControlPointType.CONTROL,
                42 to ControlPointType.CONTROL,
                36 to ControlPointType.BEACON
            )
        )

        assertEquals(4, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID
            ),
            evaluation.punchStatuses
        )
    }

    @Test
    fun sprintWithoutSpectatorDoesNotRequireSecondBeaconAtFinish() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(31, 32, 36, 41, 42),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                41 to ControlPointType.CONTROL,
                42 to ControlPointType.CONTROL,
                36 to ControlPointType.BEACON
            )
        )

        assertEquals(4, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
    }

    @Test
    fun sprintWithSpectatorDoesNotRequireFinishBeaconControl() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(31, 32, 46, 41, 42),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL,
                46 to ControlPointType.SEPARATOR,
                41 to ControlPointType.CONTROL,
                42 to ControlPointType.CONTROL
            )
        )

        assertEquals(4, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
    }

    @Test
    fun sprintScoresOnlyAssignedLoopFoxesOnCorrectSideOfSpectator() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(171, 161, 137, 162, 172, 136),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(162, ControlPointType.CONTROL, label = "2"),
                EvaluationControlPoint(137, ControlPointType.SEPARATOR, scored = false, label = "S"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(172, ControlPointType.CONTROL, label = "2F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.UNKNOWN,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.UNKNOWN,
                PunchStatus.VALID,
                PunchStatus.VALID
            ),
            evaluation.punchStatuses
        )
    }

    @Test
    fun sprintMissingSpectatorIsDnfAndFastPunchesDoNotScore() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(161, 171, 136),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(137, ControlPointType.SEPARATOR, scored = false, label = "S"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(1, evaluation.points)
        assertEquals(ResultStatus.DID_NOT_FINISH, evaluation.resultStatus)
        assertEquals(
            listOf(PunchStatus.VALID, PunchStatus.UNKNOWN, PunchStatus.UNKNOWN),
            evaluation.punchStatuses
        )
    }

    @Test
    fun sprintRequiresAtLeastOneSlowAndOneFastFox() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(161, 137, 136),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(137, ControlPointType.SEPARATOR, scored = false, label = "S"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(1, evaluation.points)
        assertEquals(ResultStatus.DID_NOT_FINISH, evaluation.resultStatus)
    }

    @Test
    fun sprintWithoutSpectatorUsesBeaconAsTransitionWithLabeledFastFoxes() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(161, 136, 171, 136),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
    }

    @Test
    fun sprintDoesNotRequireFinalBeaconAfterFastLoop() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(161, 137, 171),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(137, ControlPointType.SEPARATOR, scored = false, label = "S"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(2, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
    }

    @Test
    fun evaluatesOrienteeringInOrder() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.ORIENTEERING,
            punches = punches(31, 33),
            controlPoints = controls(
                31 to ControlPointType.CONTROL,
                32 to ControlPointType.CONTROL
            )
        )

        assertEquals(1, evaluation.points)
        assertEquals(ResultStatus.MISPUNCHED, evaluation.resultStatus)
        assertEquals(
            listOf(PunchStatus.VALID, PunchStatus.INVALID),
            evaluation.punchStatuses
        )
    }

    private fun punches(vararg codes: Int): List<EvaluationPunch> =
        codes.map { EvaluationPunch(it, SIRecordType.CONTROL) }

    private fun controls(vararg controls: Pair<Int, ControlPointType>): List<EvaluationControlPoint> =
        controls.map { EvaluationControlPoint(it.first, it.second) }
}
