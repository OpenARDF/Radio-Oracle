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

package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentReadoutTimingTest {
    @Test
    fun calculatesValidRunTime() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = SportIdentTime(10, 0, 0, 4, 0).getSeconds(),
            finishSeconds = SportIdentTime(10, 30, 0, 4, 0).getSeconds()
        )

        assertTrue(timing.isValid)
        assertEquals(SportIdentRunTimingStatus.VALID, timing.status)
        assertEquals(30L * 60L, timing.runTimeSeconds)
    }

    @Test
    fun rejectsMotoSprintReadoutWithFinishBeforeStart() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = SportIdentTime(14, 2, 23, 4, 0).getSeconds(),
            finishSeconds = SportIdentTime(0, 2, 11, 0, 0).getSeconds()
        )

        assertFalse(timing.isValid)
        assertEquals(SportIdentRunTimingStatus.FINISH_BEFORE_START, timing.status)
        assertTrue(timing.blocksResult)
        assertEquals(0L, timing.runTimeSeconds)
    }

    @Test
    fun rejectsMissingStartOrFinish() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = null,
            finishSeconds = SportIdentTime(10, 30, 0, 4, 0).getSeconds()
        )

        assertFalse(timing.isValid)
        assertEquals(SportIdentRunTimingStatus.MISSING_START_OR_FINISH, timing.status)
        assertTrue(timing.blocksResult)
        assertEquals(0L, timing.runTimeSeconds)
    }

    @Test
    fun rejectsFinishBeforeRecordedControlTime() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = SportIdentTime(10, 0, 0, 4, 0).getSeconds(),
            finishSeconds = SportIdentTime(10, 20, 0, 4, 0).getSeconds(),
            controlSeconds = listOf(
                SportIdentTime(10, 10, 0, 4, 0).getSeconds(),
                SportIdentTime(10, 25, 0, 4, 0).getSeconds()
            )
        )

        assertFalse(timing.isValid)
        assertTrue(timing.blocksResult)
        assertEquals(SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL, timing.status)
        assertEquals(1, timing.issues.single().controlIndex)
        assertEquals(0L, timing.runTimeSeconds)
    }

    @Test
    fun flagsControlAtOrBeforeStartWithoutBlockingRunTime() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = SportIdentTime(10, 0, 0, 4, 0).getSeconds(),
            finishSeconds = SportIdentTime(10, 30, 0, 4, 0).getSeconds(),
            controlSeconds = listOf(
                SportIdentTime(10, 0, 0, 4, 0).getSeconds(),
                SportIdentTime(10, 10, 0, 4, 0).getSeconds()
            )
        )

        assertFalse(timing.isValid)
        assertFalse(timing.blocksResult)
        assertEquals(SportIdentRunTimingStatus.CONTROL_NOT_AFTER_START, timing.status)
        assertEquals(0, timing.issues.single().controlIndex)
        assertEquals(30L * 60L, timing.runTimeSeconds)
    }

    @Test
    fun flagsNonSequentialControlTimesWithoutBlockingRunTime() {
        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = SportIdentTime(10, 0, 0, 4, 0).getSeconds(),
            finishSeconds = SportIdentTime(10, 30, 0, 4, 0).getSeconds(),
            controlSeconds = listOf(
                SportIdentTime(10, 12, 0, 4, 0).getSeconds(),
                SportIdentTime(10, 11, 59, 4, 0).getSeconds()
            )
        )

        assertFalse(timing.isValid)
        assertFalse(timing.blocksResult)
        assertEquals(SportIdentRunTimingStatus.CONTROL_NOT_AFTER_PREVIOUS_CONTROL, timing.status)
        assertEquals(1, timing.issues.single().controlIndex)
        assertEquals(0, timing.issues.single().previousControlIndex)
        assertEquals(30L * 60L, timing.runTimeSeconds)
    }

    @Test
    fun repairsStaleFinishDayWeekFromAndroidTimeOnlyEdit() {
        val start = SportIdentTime(14, 2, 23, 4, 0).getSeconds()
        val control = SportIdentTime(14, 5, 0, 4, 0).getSeconds()
        val staleFinish = SportIdentTime(14, 10, 0, 0, 0).getSeconds()

        val repaired = SportIdentReadoutTimingRepair.normalizeEditedTimes(
            startSeconds = start,
            controlSeconds = listOf(control),
            finishSeconds = staleFinish
        )

        assertTrue(repaired.changedFrom(listOf(control), staleFinish))
        assertEquals(SportIdentTime(14, 10, 0, 4, 0).getSeconds(), repaired.finishSeconds)

        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = start,
            finishSeconds = repaired.finishSeconds,
            controlSeconds = repaired.controlSeconds
        )
        assertTrue(timing.isValid)
        assertEquals(7L * 60L + 37L, timing.runTimeSeconds)
    }

    @Test
    fun doesNotRepairFinishClockTimeBeforeStart() {
        val start = SportIdentTime(14, 2, 23, 4, 0).getSeconds()
        val controls = listOf(
            SportIdentTime(14, 5, 0, 4, 0).getSeconds(),
            SportIdentTime(14, 10, 0, 4, 0).getSeconds()
        )
        val finishBeforeStart = SportIdentTime(0, 2, 11, 0, 0).getSeconds()

        val repaired = SportIdentReadoutTimingRepair.normalizeEditedTimes(
            startSeconds = start,
            controlSeconds = controls,
            finishSeconds = finishBeforeStart
        )

        assertFalse(repaired.changedFrom(controls, finishBeforeStart))

        val timing = SportIdentReadoutTiming.calculate(
            startSeconds = start,
            finishSeconds = repaired.finishSeconds,
            controlSeconds = repaired.controlSeconds
        )
        assertEquals(SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL, timing.status)
        assertTrue(timing.blocksResult)
        assertEquals(0L, timing.runTimeSeconds)
    }

    @Test
    fun doesNotRepairShortControlRegression() {
        val start = SportIdentTime(10, 0, 0, 4, 0).getSeconds()
        val controls = listOf(
            SportIdentTime(10, 12, 0, 4, 0).getSeconds(),
            SportIdentTime(10, 11, 59, 4, 0).getSeconds()
        )
        val finish = SportIdentTime(10, 30, 0, 4, 0).getSeconds()

        val repaired = SportIdentReadoutTimingRepair.normalizeEditedTimes(
            startSeconds = start,
            controlSeconds = controls,
            finishSeconds = finish
        )

        assertFalse(repaired.changedFrom(controls, finish))
    }
}
