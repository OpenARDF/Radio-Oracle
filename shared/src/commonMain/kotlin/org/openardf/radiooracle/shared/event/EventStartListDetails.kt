package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.time.DurationFormatter

data class EventStartListRow(
    val competitorId: String,
    val startTimeText: String,
    val startNumberText: String,
    val competitorName: String,
    val categoryName: String,
    val siNumberText: String
)

data class EventStartListDetails(
    val rows: List<EventStartListRow>,
    val scheduledCount: Int,
    val unscheduledCount: Int
) {
    companion object {
        fun from(raceData: EventRaceData): EventStartListDetails {
            val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
            val rowsWithStart = raceData.competitorData.map { competitorData ->
                val competitorCategory = competitorData.competitorCategory
                val competitor = competitorCategory.competitor
                val categoryName = competitorCategory.category?.name
                    ?: competitor.categoryId?.let { categoryNamesById[it] }
                    ?: ""
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
                        siNumberText = competitor.siNumber?.toString() ?: ""
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
                unscheduledCount = rowsWithStart.count { it.startSeconds == null }
            )
        }
    }
}

private data class StartListSortRow(
    val startSeconds: Long?,
    val categoryName: String,
    val startNumber: Int,
    val row: EventStartListRow
)
