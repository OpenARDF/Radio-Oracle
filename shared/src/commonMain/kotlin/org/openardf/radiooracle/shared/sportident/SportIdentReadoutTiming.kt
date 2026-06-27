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

/** Shared SPORTident readout timing policy used before storing result run times. */
object SportIdentReadoutTiming {
    fun calculate(
        startSeconds: Long?,
        finishSeconds: Long?,
        controlSeconds: List<Long> = emptyList()
    ): SportIdentRunTiming {
        val issues = mutableListOf<SportIdentTimingIssue>()

        if (startSeconds == null || finishSeconds == null) {
            return SportIdentRunTiming(
                runTimeSeconds = 0,
                issues = listOf(SportIdentTimingIssue(SportIdentRunTimingStatus.MISSING_START_OR_FINISH))
            )
        }

        controlSeconds.forEachIndexed { index, controlTime ->
            if (controlTime <= startSeconds) {
                issues += SportIdentTimingIssue(
                    status = SportIdentRunTimingStatus.CONTROL_NOT_AFTER_START,
                    controlIndex = index
                )
            }
            val previousControlTime = controlSeconds.getOrNull(index - 1)
            if (previousControlTime != null && controlTime <= previousControlTime) {
                issues += SportIdentTimingIssue(
                    status = SportIdentRunTimingStatus.CONTROL_NOT_AFTER_PREVIOUS_CONTROL,
                    controlIndex = index,
                    previousControlIndex = index - 1
                )
            }
            if (finishSeconds < controlTime) {
                issues += SportIdentTimingIssue(
                    status = SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL,
                    controlIndex = index
                )
            }
        }

        val runTimeSeconds = finishSeconds - startSeconds
        if (runTimeSeconds < 0) {
            issues += SportIdentTimingIssue(SportIdentRunTimingStatus.FINISH_BEFORE_START)
        }

        return SportIdentRunTiming(
            runTimeSeconds = if (issues.any { it.blocksResult }) 0 else runTimeSeconds,
            issues = issues
        )
    }
}

/** Conservative repair for edited readout times whose SI day/week fields are stale. */
object SportIdentReadoutTimingRepair {
    private const val ROLLOVER_REPAIR_THRESHOLD_SECONDS = SportIdentCodes.SECONDS_DAY / 2

    fun normalizeEditedTimes(
        startSeconds: Long?,
        controlSeconds: List<Long>,
        finishSeconds: Long?
    ): SportIdentReadoutTimingRepairResult {
        val start = startSeconds ?: run {
            return SportIdentReadoutTimingRepairResult(controlSeconds, finishSeconds)
        }

        val normalizedControls = mutableListOf<Long>()
        var previousControlMinimum = start
        controlSeconds.forEach { controlSecondsValue ->
            val normalizedControl = rollForwardIfStaleDayWeek(
                valueSeconds = controlSecondsValue,
                minimumSeconds = previousControlMinimum,
                strictlyAfterMinimum = true
            )
            normalizedControls += normalizedControl
            previousControlMinimum = maxOf(previousControlMinimum, normalizedControl)
        }

        val finishMinimum = maxOf(
            start,
            normalizedControls.maxOrNull() ?: start
        )
        val normalizedFinish = finishSeconds?.let { finishSecondsValue ->
            rollForwardIfStaleDayWeek(
                valueSeconds = finishSecondsValue,
                minimumSeconds = finishMinimum,
                strictlyAfterMinimum = false
            )
        }

        return SportIdentReadoutTimingRepairResult(normalizedControls, normalizedFinish)
    }

    private fun rollForwardIfStaleDayWeek(
        valueSeconds: Long,
        minimumSeconds: Long,
        strictlyAfterMinimum: Boolean
    ): Long {
        val targetSeconds = if (strictlyAfterMinimum) minimumSeconds + 1 else minimumSeconds
        if (valueSeconds >= targetSeconds) {
            return valueSeconds
        }
        if (minimumSeconds - valueSeconds <= ROLLOVER_REPAIR_THRESHOLD_SECONDS) {
            return valueSeconds
        }

        var repairedSeconds = valueSeconds
        while (repairedSeconds < targetSeconds) {
            repairedSeconds += SportIdentCodes.SECONDS_DAY
        }
        return repairedSeconds
    }
}

data class SportIdentReadoutTimingRepairResult(
    val controlSeconds: List<Long>,
    val finishSeconds: Long?
) {
    fun changedFrom(originalControlSeconds: List<Long>, originalFinishSeconds: Long?): Boolean =
        controlSeconds != originalControlSeconds || finishSeconds != originalFinishSeconds
}

data class SportIdentRunTiming(
    val runTimeSeconds: Long,
    val issues: List<SportIdentTimingIssue>
) {
    val status: SportIdentRunTimingStatus
        get() = issues.firstOrNull()?.status ?: SportIdentRunTimingStatus.VALID

    val isValid: Boolean
        get() = issues.isEmpty()

    val blocksResult: Boolean
        get() = issues.any { it.blocksResult }
}

data class SportIdentTimingIssue(
    val status: SportIdentRunTimingStatus,
    val controlIndex: Int? = null,
    val previousControlIndex: Int? = null
) {
    val blocksResult: Boolean
        get() = status == SportIdentRunTimingStatus.MISSING_START_OR_FINISH ||
            status == SportIdentRunTimingStatus.FINISH_BEFORE_START ||
            status == SportIdentRunTimingStatus.FINISH_BEFORE_CONTROL
}

enum class SportIdentRunTimingStatus {
    VALID,
    MISSING_START_OR_FINISH,
    FINISH_BEFORE_START,
    CONTROL_NOT_AFTER_START,
    CONTROL_NOT_AFTER_PREVIOUS_CONTROL,
    FINISH_BEFORE_CONTROL
}
