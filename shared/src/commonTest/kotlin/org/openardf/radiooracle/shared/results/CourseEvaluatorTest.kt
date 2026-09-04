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
    fun classicBeaconOnlyIsDnf() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.CLASSIC,
            punches = punches(136),
            controlPoints = controls(
                223 to ControlPointType.CONTROL,
                224 to ControlPointType.CONTROL,
                225 to ControlPointType.CONTROL,
                136 to ControlPointType.BEACON
            )
        )

        assertEquals(0, evaluation.points)
        assertEquals(ResultStatus.DID_NOT_FINISH, evaluation.resultStatus)
        assertEquals(listOf(PunchStatus.VALID), evaluation.punchStatuses)
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
    fun sprintUsesFastLabelsWhenSpectatorIsFirstInCourseControls() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(161, 162, 165, 137, 172, 173, 172, 136),
            controlPoints = listOf(
                EvaluationControlPoint(137, ControlPointType.SEPARATOR, scored = false, label = "S"),
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(162, ControlPointType.CONTROL, label = "2"),
                EvaluationControlPoint(172, ControlPointType.CONTROL, label = "2F"),
                EvaluationControlPoint(163, ControlPointType.CONTROL, label = "3"),
                EvaluationControlPoint(173, ControlPointType.CONTROL, label = "3F"),
                EvaluationControlPoint(164, ControlPointType.CONTROL, label = "4"),
                EvaluationControlPoint(174, ControlPointType.CONTROL, label = "4F"),
                EvaluationControlPoint(165, ControlPointType.CONTROL, label = "5"),
                EvaluationControlPoint(175, ControlPointType.CONTROL, label = "5F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(5, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.DUPLICATE,
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
    fun sprintDoesNotScoreSpectatorOrBeaconMarkersAsFoxes() {
        val evaluation = CourseEvaluator.evaluate(
            RaceType.SPRINT,
            punches = punches(165, 161, 162, 137, 174, 175, 172, 136),
            controlPoints = listOf(
                EvaluationControlPoint(161, ControlPointType.CONTROL, label = "1"),
                EvaluationControlPoint(163, ControlPointType.CONTROL, label = "3"),
                EvaluationControlPoint(165, ControlPointType.CONTROL, label = "5"),
                EvaluationControlPoint(137, ControlPointType.CONTROL, label = "S"),
                EvaluationControlPoint(171, ControlPointType.CONTROL, label = "1F"),
                EvaluationControlPoint(174, ControlPointType.CONTROL, label = "4F"),
                EvaluationControlPoint(175, ControlPointType.CONTROL, label = "5F"),
                EvaluationControlPoint(136, ControlPointType.BEACON, scored = false, label = "B")
            )
        )

        assertEquals(4, evaluation.points)
        assertEquals(ResultStatus.OK, evaluation.resultStatus)
        assertEquals(
            listOf(
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.UNKNOWN,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.VALID,
                PunchStatus.UNKNOWN,
                PunchStatus.VALID
            ),
            evaluation.punchStatuses
        )
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
