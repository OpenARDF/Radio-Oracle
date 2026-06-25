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
