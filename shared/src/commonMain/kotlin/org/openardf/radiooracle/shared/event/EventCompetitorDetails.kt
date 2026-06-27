package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.time.DurationFormatter

/** Shared read-only competitor row prepared for desktop and other event-admin surfaces. */
data class EventCompetitorDetails(
    val id: String,
    val firstName: String,
    val lastName: String,
    val fullName: String,
    val club: String,
    val index: String,
    val bibNumber: String,
    val callSign: String,
    val birthYearText: String,
    val categoryId: String?,
    val categoryName: String,
    val startNumber: Int?,
    val startNumberText: String,
    val startTimeText: String,
    val siNumberText: String,
    val hasReadout: Boolean,
    val warningReasons: List<String> = emptyList()
) {
    companion object {
        /** Builds competitor display rows with category names resolved from embedded data or project categories. */
        fun from(raceData: EventRaceData): List<EventCompetitorDetails> {
            val categoryNamesById = raceData.categories.associate { it.category.id to it.category.name }
            val categoriesById = raceData.categories.associate { it.category.id to it.category }
            val eventYear = raceData.race.startDateTimeIso.trim().take(4).toIntOrNull()
            return raceData.competitorData
                .map { competitorData ->
                    val competitorCategory = competitorData.competitorCategory
                    val competitor = competitorCategory.competitor
                    val category = competitor.categoryId
                        ?.let { categoriesById[it] }
                        ?: competitorCategory.category
                    EventCompetitorDetails(
                        id = competitor.id,
                        firstName = competitor.firstName,
                        lastName = competitor.lastName,
                        fullName = competitor.fullName(),
                        club = competitor.club,
                        index = competitor.index,
                        bibNumber = competitor.bibNumber,
                        callSign = competitor.callSign,
                        birthYearText = competitor.birthYear?.toString() ?: "",
                        categoryId = competitor.categoryId,
                        categoryName = competitorCategory.category?.name
                            ?: competitor.categoryId?.let { categoryNamesById[it] }
                            ?: "",
                        startNumber = competitor.startNumber,
                        startNumberText = competitor.startNumber?.toString() ?: "",
                        startTimeText = competitor.drawnStartTimeSeconds?.let {
                            DurationFormatter.secondsToFormattedString(it, useMinutes = true)
                        } ?: "",
                        siNumberText = competitor.siNumber?.toString() ?: "",
                        hasReadout = competitorData.readoutData != null,
                        warningReasons = competitorWarningReasons(competitor, category, eventYear)
                    )
                }
                .sortedWith(compareBy<EventCompetitorDetails> { it.startNumber ?: Int.MAX_VALUE }.thenBy { it.fullName })
        }

        private fun competitorWarningReasons(
            competitor: EventCompetitor,
            category: EventCategory?,
            eventYear: Int?
        ): List<String> = buildList {
            if (competitor.siNumber == null || competitor.siNumber <= 0) {
                add("No SI number is assigned.")
            }
            if (competitor.categoryId.isNullOrBlank()) {
                add("No category is assigned.")
            } else if (category == null) {
                add("Assigned category was not found in Setup > Categories.")
            }
            val minimumAge = category?.name?.adultCategoryMinimumAge()
            val birthYear = competitor.birthYear
            if (minimumAge != null && birthYear != null && eventYear != null) {
                val apparentAge = eventYear - birthYear
                if (apparentAge in 0 until minimumAge) {
                    add(
                        "Apparent birth year/category discrepancy: competitor appears to be " +
                            "$apparentAge on the event date, too young for ${category.name}."
                    )
                }
            }
        }

        /**
         * Adult radio-orienteering category labels such as M21, W35, or M-50 describe a minimum
         * eligible age. Youth categories use maximum ages, and older competitors may run younger
         * adult categories, so this row warning only flags competitors who appear too young.
         */
        private fun String.adultCategoryMinimumAge(): Int? {
            val age = Regex("""[A-Za-z]\s*-?\s*(\d{2})""")
                .find(trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            return age?.takeIf { it >= 21 }
        }
    }
}
