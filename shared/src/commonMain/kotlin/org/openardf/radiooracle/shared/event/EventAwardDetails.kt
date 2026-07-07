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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.results.EventResultPlacement
import org.openardf.radiooracle.shared.time.DurationFormatter

const val PRELIMINARY_RESULT_NOTICE = "Preliminary Results - Pending Organizer Review"

fun EventRace.resultPublicationNotice(): String? =
    if (raceLevel == RaceLevel.PRACTICE) null else PRELIMINARY_RESULT_NOTICE

fun EventRace.awardScopes(): Set<EventAwardScope> =
    when {
        raceLevel == RaceLevel.PRACTICE -> emptySet()
        combinedNationalRegionalAwards -> setOf(EventAwardScope.NATIONAL, EventAwardScope.REGIONAL)
        raceLevel == RaceLevel.NATIONAL -> setOf(EventAwardScope.NATIONAL)
        raceLevel == RaceLevel.REGIONAL -> setOf(EventAwardScope.REGIONAL)
        else -> emptySet()
    }

fun EventRace.supportsChampionshipAwards(): Boolean =
    awardScopes().isNotEmpty()

enum class EventAwardScope(val displayLabel: String) {
    NATIONAL("National Awards"),
    REGIONAL("Regional Awards")
}

fun EventAwardCategoryDetails.awardsForScope(scope: EventAwardScope): List<EventAwardWinnerDetails> =
    when (scope) {
        EventAwardScope.NATIONAL -> usaAwards
        EventAwardScope.REGIONAL -> region2Awards
    }

enum class EventAwardDisplayMode(val displayLabel: String) {
    FIRST_TO_THIRD("Show only first to third place awards"),
    ALL_SUCCESSFUL_FINISHERS("Show all finish award levels")
}

data class EventAwardDetails(
    val publicationNotice: String?,
    val awardScopes: Set<EventAwardScope>,
    val categories: List<EventAwardCategoryDetails>
) {
    val hasAwards: Boolean
        get() = categories.any { it.usaAwards.isNotEmpty() || it.region2Awards.isNotEmpty() }

    companion object {
        fun from(
            raceData: EventRaceData,
            displayMode: EventAwardDisplayMode = EventAwardDisplayMode.FIRST_TO_THIRD
        ): EventAwardDetails {
            val publicationNotice = raceData.race.resultPublicationNotice()
            val awardScopes = raceData.race.awardScopes()
            if (awardScopes.isEmpty()) {
                return EventAwardDetails(publicationNotice, awardScopes, emptyList())
            }

            val categoriesById = raceData.categories.associateBy { it.category.id }
            val grouped = raceData.competitorData.groupBy { it.resultCategoryId() }
            val categories = grouped.mapNotNull { (categoryId, competitors) ->
                val categoryData = categoryId?.let(categoriesById::get)
                val categoryName = categoryData?.category?.name
                    ?: competitors.firstNotNullOfOrNull { it.competitorCategory.category?.name }
                    ?: categoryId?.let { "Unknown category" }
                    ?: "Uncategorized"
                val categoryOrder = categoryData?.category?.order ?: Int.MAX_VALUE
                val overallPlaced = EventResultPlacement.sortByPlace(competitors)
                val overallPlaceByResultId = overallPlaced.mapNotNull { competitorData ->
                    val result = competitorData.readoutData?.result ?: return@mapNotNull null
                    result.id to result.place
                }.toMap()

                val usaAwards = if (EventAwardScope.NATIONAL in awardScopes) {
                    awardWinners(
                        scope = EventAwardScope.NATIONAL,
                        competitors = competitors,
                        overallPlaceByResultId = overallPlaceByResultId,
                        displayMode = displayMode,
                        isEligible = { it.usaChampEligible == true }
                    )
                } else {
                    emptyList()
                }
                val region2Awards = if (EventAwardScope.REGIONAL in awardScopes) {
                    awardWinners(
                        scope = EventAwardScope.REGIONAL,
                        competitors = competitors,
                        overallPlaceByResultId = overallPlaceByResultId,
                        displayMode = displayMode,
                        isEligible = { it.region2ChampEligible == true || it.usaChampEligible == true }
                    )
                } else {
                    emptyList()
                }
                if (usaAwards.isEmpty() && region2Awards.isEmpty()) {
                    null
                } else {
                    EventAwardCategoryDetails(
                        categoryId = categoryId,
                        categoryName = categoryName,
                        categorySortOrder = categoryOrder,
                        usaAwards = usaAwards,
                        region2Awards = region2Awards
                    )
                }
            }.sortedWith(compareBy<EventAwardCategoryDetails> { it.categorySortOrder }.thenBy { it.categoryName })

            return EventAwardDetails(publicationNotice, awardScopes, categories)
        }

        private fun awardWinners(
            scope: EventAwardScope,
            competitors: List<EventCompetitorData>,
            overallPlaceByResultId: Map<String, Int>,
            displayMode: EventAwardDisplayMode,
            isEligible: (EventCompetitor) -> Boolean
        ): List<EventAwardWinnerDetails> =
            EventResultPlacement.sortByPlace(
                competitors.filter { competitorData ->
                    val competitor = competitorData.competitorCategory.competitor
                    val result = competitorData.readoutData?.result
                    result?.resultStatus == ResultStatus.OK && isEligible(competitor)
                }
            ).mapNotNull { competitorData ->
                val readoutData = competitorData.readoutData ?: return@mapNotNull null
                val result = readoutData.result
                if (!displayMode.includesAwardPlace(result.place)) {
                    return@mapNotNull null
                }
                val competitor = competitorData.competitorCategory.competitor
                EventAwardWinnerDetails(
                    scope = scope,
                    awardLevel = result.place.toAwardLevelLabel(),
                    medal = result.place.toMedalLabel(),
                    awardPlace = result.place,
                    overallPlace = overallPlaceByResultId[result.id]?.takeIf { it > 0 },
                    resultId = result.id,
                    competitorId = competitor.id,
                    competitorName = competitor.fullName(),
                    club = competitor.club,
                    personId = competitor.index,
                    statusLabel = result.resultStatus.toDisplayLabel(),
                    pointsText = result.points.toString(),
                    runTimeText = DurationFormatter.secondsToFormattedString(result.runTimeSeconds, useMinutes = false)
                )
            }

        private fun EventAwardDisplayMode.includesAwardPlace(place: Int): Boolean =
            place > 0 && (this == EventAwardDisplayMode.ALL_SUCCESSFUL_FINISHERS || place <= 3)

        private fun Int.toAwardLevelLabel(): String =
            toMedalLabel() ?: ordinalText()

        private fun Int.toMedalLabel(): String? =
            when (this) {
                1 -> "Gold"
                2 -> "Silver"
                3 -> "Bronze"
                else -> null
            }

        private fun Int.ordinalText(): String {
            val suffix = if (this % 100 in 11..13) {
                "th"
            } else {
                when (this % 10) {
                    1 -> "st"
                    2 -> "nd"
                    3 -> "rd"
                    else -> "th"
                }
            }
            return "$this$suffix"
        }
    }
}

data class EventAwardCategoryDetails(
    val categoryId: String?,
    val categoryName: String,
    val categorySortOrder: Int,
    val usaAwards: List<EventAwardWinnerDetails>,
    val region2Awards: List<EventAwardWinnerDetails>
)

data class EventAwardWinnerDetails(
    val scope: EventAwardScope,
    val awardLevel: String,
    val medal: String?,
    val awardPlace: Int,
    val overallPlace: Int?,
    val resultId: String,
    val competitorId: String,
    val competitorName: String,
    val club: String,
    val personId: String,
    val statusLabel: String,
    val pointsText: String,
    val runTimeText: String
) {
    val awardText: String
        get() = "$awardLevel ($awardPlace)"
}

private fun EventCompetitorData.resultCategoryId(): String? =
    readoutData?.result?.categoryId ?: competitorCategory.category?.id ?: competitorCategory.competitor.categoryId
