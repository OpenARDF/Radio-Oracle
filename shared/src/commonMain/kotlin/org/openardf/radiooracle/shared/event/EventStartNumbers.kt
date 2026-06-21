package org.openardf.radiooracle.shared.event

/**
 * Derives event start numbers from assigned start times.
 *
 * In Radio-Oracle, a start number is the departure-slot sequence within a
 * specific Event File. Competitors starting together share the same start
 * number; competitors without a drawn start time have no start number yet.
 */
object EventStartNumbers {
    fun numberByStartTime(raceData: EventRaceData): Map<Long, Int> =
        raceData.competitorData
            .mapNotNull { it.competitorCategory.competitor.drawnStartTimeSeconds }
            .distinct()
            .sorted()
            .withIndex()
            .associate { (index, startSeconds) -> startSeconds to index + 1 }

    fun assignFromDrawnStartTimes(projectFile: EventProjectFile): EventProjectFile =
        projectFile.copy(raceData = assignFromDrawnStartTimes(projectFile.raceData))

    fun assignFromDrawnStartTimes(raceData: EventRaceData): EventRaceData {
        val numberByStartTime = numberByStartTime(raceData)
        val competitorsById = mutableMapOf<String, EventCompetitor>()
        fun EventCompetitor.withDerivedStartNumber(): EventCompetitor =
            copy(startNumber = drawnStartTimeSeconds?.let(numberByStartTime::get))

        val competitorData = raceData.competitorData.map { data ->
            val competitor = data.competitorCategory.competitor.withDerivedStartNumber()
            competitorsById[competitor.id] = competitor
            data.copy(
                competitorCategory = data.competitorCategory.copy(
                    competitor = competitor
                )
            )
        }

        return raceData.copy(
            categories = raceData.categories.map { categoryData ->
                categoryData.copy(
                    competitors = categoryData.competitors.map { competitor ->
                        competitorsById[competitor.id] ?: competitor.withDerivedStartNumber()
                    }
                )
            },
            competitorData = competitorData
        )
    }
}
