package org.openardf.radiooracle.shared.event

/** Shared read-only competitor row prepared for desktop and other event-admin surfaces. */
data class EventCompetitorDetails(
    val id: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val club: String,
    val index: String,
    val categoryId: String?,
    val categoryName: String,
    val startNumber: Int,
    val startNumberText: String,
    val siNumberText: String
) {
    companion object {
        /** Builds competitor display rows with category names resolved from embedded data or project categories. */
        fun from(raceData: EventRaceData): List<EventCompetitorDetails> {
            val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
            return raceData.competitorData
                .map { competitorData ->
                    val competitorCategory = competitorData.competitorCategory
                    val competitor = competitorCategory.competitor
                    EventCompetitorDetails(
                        id = competitor.id,
                        firstName = competitor.firstName,
                        lastName = competitor.lastName,
                        fullName = competitor.fullName(),
                        club = competitor.club,
                        index = competitor.index,
                        categoryId = competitor.categoryId,
                        categoryName = competitorCategory.category?.name
                            ?: competitor.categoryId?.let { categoryNamesById[it] }
                            ?: "",
                        startNumber = competitor.startNumber,
                        startNumberText = competitor.startNumber.toString(),
                        siNumberText = competitor.siNumber?.toString() ?: ""
                    )
                }
                .sortedWith(compareBy<EventCompetitorDetails> { it.startNumber }.thenBy { it.fullName })
        }
    }
}
