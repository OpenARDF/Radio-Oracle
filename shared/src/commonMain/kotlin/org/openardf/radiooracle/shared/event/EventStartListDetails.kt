package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.time.DurationFormatter

data class EventStartListRow(
    val competitorId: String,
    val startTimeText: String,
    val startNumberText: String,
    val competitorName: String,
    val categoryName: String,
    val siNumberText: String,
    val ruleSeverity: EventStartListRuleSeverity = EventStartListRuleSeverity.GREEN,
    val ruleMessage: String = ""
)

data class EventStartListDetails(
    val rows: List<EventStartListRow>,
    val scheduledCount: Int,
    val unscheduledCount: Int,
    val settings: StartDrawSettings,
    val quality: EventStartListQuality
) {
    companion object {
        fun from(raceData: EventRaceData): EventStartListDetails {
            val settings = raceData.effectiveStartDrawSettings()
            val quality = EventStartListQuality.evaluate(raceData, settings)
            val rowQualityByCompetitorId = quality.rowFindings.groupBy { it.competitorId }
            val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
            val rowsWithStart = raceData.competitorData.map { competitorData ->
                val competitorCategory = competitorData.competitorCategory
                val competitor = competitorCategory.competitor
                val categoryName = competitorCategory.category?.name
                    ?: competitor.categoryId?.let { categoryNamesById[it] }
                    ?: ""
                val rowFindings = rowQualityByCompetitorId[competitor.id].orEmpty()
                val rowSeverity = rowFindings.maxOfOrNull { it.severity } ?: EventStartListRuleSeverity.GREEN
                StartListSortRow(
                    startSeconds = competitor.drawnStartTimeSeconds,
                    categoryName = categoryName,
                    startNumber = competitor.startNumber,
                    row = EventStartListRow(
                        competitorId = competitor.id,
                        startTimeText = competitor.drawnStartTimeSeconds?.let {
                            DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                        } ?: "",
                        startNumberText = competitor.startNumber.toString(),
                        competitorName = competitor.fullName(),
                        categoryName = categoryName,
                        siNumberText = competitor.siNumber?.toString() ?: "",
                        ruleSeverity = rowSeverity,
                        ruleMessage = rowFindings.joinToString("; ") { it.text }
                    )
                )
            }

            return EventStartListDetails(
                rows = rowsWithStart
                    .sortedWith(
                        compareBy<StartListSortRow> { it.startSeconds == null }
                            .thenBy { it.startSeconds ?: Long.MAX_VALUE }
                            .thenBy { it.categoryName }
                            .thenBy { it.startNumber }
                    )
                    .map { it.row },
                scheduledCount = rowsWithStart.count { it.startSeconds != null },
                unscheduledCount = rowsWithStart.count { it.startSeconds == null },
                settings = settings,
                quality = quality
            )
        }
    }
}

/**
 * Human- and UI-facing evaluation of a drawn start list.
 *
 * Severity is intentionally split from score:
 * - RED means a hard rule or saved capacity setting is violated.
 * - ORANGE means the generated order is legal but did not satisfy every best
 *   practice, usually because the remaining field made spacing impossible.
 * - GREEN means the current evaluator found no violations or compromises.
 *
 * The numerical score is a compact "goodness factor" for comparison and status
 * display. It is not a proof of optimality; it is a weighted summary of the
 * concrete findings emitted in `messages` and `rowFindings`.
 */
data class EventStartListQuality(
    val score: Int,
    val severity: EventStartListRuleSeverity,
    val summary: String,
    val messages: List<String>,
    val rowFindings: List<EventStartListRowFinding>
) {
    companion object {
        /**
         * Evaluates the current start times against the same settings used by
         * the generator. This method deliberately does not re-run or optimize
         * the draw. It only grades the Event File as saved, which lets manual
         * edits and imported start lists receive the same color/score treatment.
         */
        fun evaluate(raceData: EventRaceData, settings: StartDrawSettings): EventStartListQuality {
            val categoryById = raceData.categories.associateBy { it.category.id }
            val scheduled = raceData.competitorData
                .mapNotNull { data ->
                    val competitor = data.competitorCategory.competitor
                    val startSeconds = competitor.drawnStartTimeSeconds ?: return@mapNotNull null
                    val categoryId = data.competitorCategory.category?.id ?: competitor.categoryId
                    val categoryName = data.competitorCategory.category?.name
                        ?: categoryId?.let { categoryById[it]?.category?.name }
                        ?: ""
                    StartListEvaluationRow(
                        competitorId = competitor.id,
                        startSeconds = startSeconds,
                        startNumber = competitor.startNumber,
                        categoryId = categoryId,
                        categoryName = categoryName,
                        club = competitor.club.trim().takeIf { it.isNotEmpty() }
                    )
                }
                .sortedWith(compareBy({ it.startSeconds }, { it.startNumber }, { it.competitorId }))

            if (scheduled.isEmpty()) {
                return EventStartListQuality(
                    score = 100,
                    severity = EventStartListRuleSeverity.GREEN,
                    summary = "No drawn start times to evaluate.",
                    messages = listOf("No drawn start times to evaluate."),
                    rowFindings = emptyList()
                )
            }

            val messages = mutableListOf<String>()
            val rowFindings = mutableListOf<EventStartListRowFinding>()
            var redCount = 0
            var orangeCount = 0

            scheduled.groupBy { it.startSeconds }.forEach { (startSeconds, starters) ->
                if (starters.size > settings.options.startersPerStartTime) {
                    redCount += starters.size - settings.options.startersPerStartTime
                    messages += "Start time ${formatSeconds(startSeconds)} has ${starters.size} starters; limit is ${settings.options.startersPerStartTime}."
                    starters.forEach {
                        rowFindings += EventStartListRowFinding(
                            competitorId = it.competitorId,
                            severity = EventStartListRuleSeverity.RED,
                            text = "Too many starters at ${formatSeconds(startSeconds)}"
                        )
                    }
                }
                val repeatedCategories = starters.groupBy { it.categoryId }.filterKeys { it != null }.filterValues { it.size > 1 }
                repeatedCategories.forEach { (_, rows) ->
                    orangeCount += rows.size - 1
                    messages += "Start time ${formatSeconds(startSeconds)} has multiple ${rows.first().categoryName} competitors."
                    rows.forEach {
                        rowFindings += EventStartListRowFinding(
                            competitorId = it.competitorId,
                            severity = EventStartListRuleSeverity.ORANGE,
                            text = "Same category at same start time"
                        )
                    }
                }
            }

            scheduled.zipWithNext().forEach { (previous, current) ->
                if (previous.categoryId != null && previous.categoryId == current.categoryId) {
                    orangeCount += 1
                    messages += "Consecutive starts include ${current.categoryName} competitors at ${formatSeconds(previous.startSeconds)} and ${formatSeconds(current.startSeconds)}."
                    rowFindings += EventStartListRowFinding(
                        competitorId = previous.competitorId,
                        severity = EventStartListRuleSeverity.ORANGE,
                        text = "Consecutive same category"
                    )
                    rowFindings += EventStartListRowFinding(
                        competitorId = current.competitorId,
                        severity = EventStartListRuleSeverity.ORANGE,
                        text = "Consecutive same category"
                    )
                }
                if (
                    settings.options.clubHandling == StartDrawClubHandling.AVOID_BACK_TO_BACK &&
                    previous.club != null &&
                    previous.club == current.club
                ) {
                    orangeCount += 1
                    messages += "Consecutive starts include ${current.club} club competitors at ${formatSeconds(previous.startSeconds)} and ${formatSeconds(current.startSeconds)}."
                    rowFindings += EventStartListRowFinding(
                        competitorId = previous.competitorId,
                        severity = EventStartListRuleSeverity.ORANGE,
                        text = "Consecutive same club"
                    )
                    rowFindings += EventStartListRowFinding(
                        competitorId = current.competitorId,
                        severity = EventStartListRuleSeverity.ORANGE,
                        text = "Consecutive same club"
                    )
                }
            }

            val severity = when {
                redCount > 0 -> EventStartListRuleSeverity.RED
                orangeCount > 0 -> EventStartListRuleSeverity.ORANGE
                else -> EventStartListRuleSeverity.GREEN
            }
            val score = (100 - redCount * 25 - orangeCount * 8).coerceIn(1, 100)
            val summary = when (severity) {
                EventStartListRuleSeverity.RED -> "Start order rule violation."
                EventStartListRuleSeverity.ORANGE -> "Start order rules met, but not ideal."
                EventStartListRuleSeverity.GREEN -> "All start order rules and best practices met."
            }

            return EventStartListQuality(
                score = score,
                severity = severity,
                summary = summary,
                messages = messages.ifEmpty { listOf(summary) },
                rowFindings = rowFindings
            )
        }

        private fun formatSeconds(seconds: Long): String =
            DurationFormatter.secondsToFormattedString(seconds, useMinutes = true)
    }
}

enum class EventStartListRuleSeverity {
    GREEN,
    ORANGE,
    RED
}

data class EventStartListRowFinding(
    val competitorId: String,
    val severity: EventStartListRuleSeverity,
    val text: String
)

private data class StartListSortRow(
    val startSeconds: Long?,
    val categoryName: String,
    val startNumber: Int,
    val row: EventStartListRow
)

private data class StartListEvaluationRow(
    val competitorId: String,
    val startSeconds: Long,
    val startNumber: Int,
    val categoryId: String?,
    val categoryName: String,
    val club: String?
)
