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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRaceData

/** How a parsed IOF row matched an existing Radio-Oracle competitor. */
enum class IofXmlCompetitorMatchBasis {
    PERSON_ID,
    CONTROL_CARD,
    BIB_NUMBER,
    NAME
}

/** Non-fatal or fatal issue found while matching parsed IOF rows to an Event File. */
enum class IofXmlCompetitorMatchIssue {
    UNKNOWN_CLASS,
    MISSING_CONTROL_CARD,
    UNKNOWN_COMPETITOR,
    DUPLICATE_MATCH
}

/** Match result common to StartList and ResultList preview rows. */
data class IofXmlCompetitorMatch(
    val categoryId: String?,
    val competitorId: String?,
    val basis: IofXmlCompetitorMatchBasis?,
    val issues: Set<IofXmlCompetitorMatchIssue>
)

/** Parsed StartList row plus its best match in the current Event File. */
data class IofStartListMatchedEntry(
    val entry: IofStartListEntryPreview,
    val match: IofXmlCompetitorMatch
)

/** Parsed StartList plus per-row Event File matching information. */
data class IofStartListMatchPreview(
    val source: IofStartListPreview,
    val entries: List<IofStartListMatchedEntry>
)

/** Parsed ResultList row plus its best match in the current Event File. */
data class IofResultListMatchedEntry(
    val entry: IofResultListEntryPreview,
    val match: IofXmlCompetitorMatch
)

/** Parsed ResultList plus per-row Event File matching information. */
data class IofResultListMatchPreview(
    val source: IofResultListPreview,
    val entries: List<IofResultListMatchedEntry>
)

/** Shared matching rules for IOF StartList and ResultList preview/apply workflows. */
object IofXmlImportMatcher {

    fun matchStartList(preview: IofStartListPreview, raceData: EventRaceData): IofStartListMatchPreview =
        IofStartListMatchPreview(
            source = preview,
            entries = preview.entries.map { entry ->
                IofStartListMatchedEntry(
                    entry = entry,
                    match = matchCompetitor(
                        className = entry.className,
                        person = entry.person,
                        controlCard = entry.controlCard,
                        bibNumber = entry.bibNumber,
                        raceData = raceData
                    )
                )
            }
        )

    fun matchResultList(preview: IofResultListPreview, raceData: EventRaceData): IofResultListMatchPreview =
        IofResultListMatchPreview(
            source = preview,
            entries = preview.entries.map { entry ->
                IofResultListMatchedEntry(
                    entry = entry,
                    match = matchCompetitor(
                        className = entry.className,
                        person = entry.person,
                        controlCard = entry.controlCard,
                        bibNumber = null,
                        raceData = raceData
                    )
                )
            }
        )

    private fun matchCompetitor(
        className: String,
        person: IofPersonPreview,
        controlCard: Int?,
        bibNumber: String?,
        raceData: EventRaceData
    ): IofXmlCompetitorMatch {
        val issues = mutableSetOf<IofXmlCompetitorMatchIssue>()
        if (controlCard == null) {
            issues += IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD
        }

        val category = raceData.categories.firstOrNull { it.category.name == className }?.category
        if (category == null) {
            issues += IofXmlCompetitorMatchIssue.UNKNOWN_CLASS
            return IofXmlCompetitorMatch(
                categoryId = null,
                competitorId = null,
                basis = null,
                issues = issues
            )
        }

        val categoryCompetitors = raceData.competitorData.filter { data ->
            data.competitorCategory.category?.id == category.id ||
                data.competitorCategory.competitor.categoryId == category.id
        }

        val matched = firstUniqueMatch(
            candidates = categoryCompetitors,
            basis = IofXmlCompetitorMatchBasis.PERSON_ID,
            valuePresent = !person.personId.isNullOrBlank()
        ) { data ->
            val competitor = data.competitorCategory.competitor
            competitor.index == person.personId || competitor.id == person.personId
        } ?: firstUniqueMatch(
            candidates = categoryCompetitors,
            basis = IofXmlCompetitorMatchBasis.CONTROL_CARD,
            valuePresent = controlCard != null
        ) { data ->
            data.competitorCategory.competitor.siNumber == controlCard
        } ?: firstUniqueMatch(
            candidates = categoryCompetitors,
            basis = IofXmlCompetitorMatchBasis.BIB_NUMBER,
            valuePresent = !bibNumber.isNullOrBlank()
        ) { data ->
            data.competitorCategory.competitor.bibNumber == bibNumber
        } ?: firstUniqueMatch(
            candidates = categoryCompetitors,
            basis = IofXmlCompetitorMatchBasis.NAME,
            valuePresent = person.familyName.isNotBlank() || person.givenName.isNotBlank()
        ) { data ->
            val competitor = data.competitorCategory.competitor
            competitor.lastName.normalizedMatchText() == person.familyName.normalizedMatchText() &&
                competitor.firstName.normalizedMatchText() == person.givenName.normalizedMatchText()
        }

        if (matched == DuplicateMatch) {
            issues += IofXmlCompetitorMatchIssue.DUPLICATE_MATCH
        } else if (matched !is UniqueMatch) {
            issues += IofXmlCompetitorMatchIssue.UNKNOWN_COMPETITOR
        }

        return IofXmlCompetitorMatch(
            categoryId = category.id,
            competitorId = (matched as? UniqueMatch)?.competitorData?.competitorCategory?.competitor?.id,
            basis = (matched as? UniqueMatch)?.basis,
            issues = issues
        )
    }

    private fun firstUniqueMatch(
        candidates: List<EventCompetitorData>,
        basis: IofXmlCompetitorMatchBasis,
        valuePresent: Boolean,
        predicate: (EventCompetitorData) -> Boolean
    ): MatchCandidate? {
        if (!valuePresent) {
            return null
        }
        val matches = candidates.filter(predicate).distinctBy { it.competitorCategory.competitor.id }
        return when (matches.size) {
            0 -> null
            1 -> UniqueMatch(matches.single(), basis)
            else -> DuplicateMatch
        }
    }
}

private sealed interface MatchCandidate

private data class UniqueMatch(
    val competitorData: EventCompetitorData,
    val basis: IofXmlCompetitorMatchBasis
) : MatchCandidate

private data object DuplicateMatch : MatchCandidate

private fun String.normalizedMatchText(): String =
    trim().lowercase()
