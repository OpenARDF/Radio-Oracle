package org.openardf.radiooracle.shared.results

import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventReadoutData
import org.openardf.radiooracle.shared.event.EventResult

/** Shared result ordering and place-assignment service. */
object EventResultPlacement {
    /** Sorts competitors by result ranking and assigns places to readouts. */
    fun sortByPlace(competitors: List<EventCompetitorData>): List<EventCompetitorData> {
        val sorted = competitors.sortedWith(::compareCompetitorData)
        var place = 0

        return sorted.mapIndexed { index, competitorData ->
            val current = competitorData.readoutData
            if (current == null) {
                competitorData
            } else {
                val previous = sorted.getOrNull(index - 1)?.readoutData
                place = if (previous != null &&
                    current.result.runTimeSeconds == previous.result.runTimeSeconds &&
                    current.result.points == previous.result.points
                ) {
                    place
                } else {
                    place + 1
                }
                competitorData.withResultPlace(place)
            }
        }
    }

    /** Groups competitors by category id, then sorts and places each category independently. */
    fun groupByCategoryAndSortByPlace(
        competitors: List<EventCompetitorData>
    ): Map<String?, List<EventCompetitorData>> {
        return competitors
            .groupBy { it.competitionCategoryId() }
            .mapValues { (_, categoryCompetitors) -> sortByPlace(categoryCompetitors) }
    }

    /** Assigns places within each category while preserving the caller's competitor order. */
    fun assignPlacesByCategory(competitors: List<EventCompetitorData>): List<EventCompetitorData> {
        val placedByCompetitorId = groupByCategoryAndSortByPlace(competitors)
            .values
            .flatten()
            .associateBy { it.competitorCategory.competitor.id }
        return competitors.map { competitorData ->
            placedByCompetitorId[competitorData.competitorCategory.competitor.id] ?: competitorData
        }
    }

    private fun compareCompetitorData(first: EventCompetitorData, second: EventCompetitorData): Int {
        val firstReadout = first.readoutData
        val secondReadout = second.readoutData

        return when {
            firstReadout == null && secondReadout == null -> 0
            firstReadout == null -> 1
            secondReadout == null -> -1
            else -> firstReadout.result.compareRanking(secondReadout.result)
        }
    }

    private fun EventCompetitorData.competitionCategoryId(): String? =
        readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId

    private fun EventResult.compareRanking(other: EventResult): Int {
        return when {
            resultStatus != other.resultStatus -> resultStatus.compareTo(other.resultStatus)
            points != other.points -> other.points.compareTo(points)
            else -> runTimeSeconds.compareTo(other.runTimeSeconds)
        }
    }

    private fun EventCompetitorData.withResultPlace(place: Int): EventCompetitorData {
        val readoutData = readoutData ?: return this
        return copy(readoutData = readoutData.withPlace(place))
    }

    private fun EventReadoutData.withPlace(place: Int): EventReadoutData =
        copy(result = result.copy(place = place))
}
