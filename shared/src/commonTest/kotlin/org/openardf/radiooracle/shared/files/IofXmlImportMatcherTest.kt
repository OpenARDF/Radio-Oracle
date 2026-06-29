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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData

class IofXmlImportMatcherTest {

    @Test
    fun startListMatchesByPersonIdBeforeOtherIdentifiers() {
        val preview = IofStartListPreview(
            eventName = null,
            startDate = null,
            startTime = null,
            entries = listOf(
                startEntry(personId = "idx-alice", controlCard = 222222, bibNumber = "99")
            )
        )

        val match = IofXmlImportMatcher.matchStartList(preview, raceData()).entries.single().match

        assertEquals("alice", match.competitorId)
        assertEquals(IofXmlCompetitorMatchBasis.PERSON_ID, match.basis)
        assertTrue(match.issues.isEmpty())
    }

    @Test
    fun startListFallsBackThroughControlCardBibAndUniqueName() {
        val preview = IofStartListPreview(
            eventName = null,
            startDate = null,
            startTime = null,
            entries = listOf(
                startEntry(personId = null, controlCard = 222222, bibNumber = null, familyName = "Wrong", givenName = "Name"),
                startEntry(personId = null, controlCard = null, bibNumber = "1008", familyName = "Wrong", givenName = "Name"),
                startEntry(className = "W21", personId = null, controlCard = null, bibNumber = null, familyName = "Name", givenName = "Cara")
            )
        )

        val matches = IofXmlImportMatcher.matchStartList(preview, raceData()).entries.map { it.match }

        assertEquals("bob", matches[0].competitorId)
        assertEquals(IofXmlCompetitorMatchBasis.CONTROL_CARD, matches[0].basis)
        assertEquals("bob", matches[1].competitorId)
        assertEquals(IofXmlCompetitorMatchBasis.BIB_NUMBER, matches[1].basis)
        assertEquals("cara", matches[2].competitorId)
        assertEquals(IofXmlCompetitorMatchBasis.NAME, matches[2].basis)
        assertTrue(matches[1].issues.contains(IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD))
        assertTrue(matches[2].issues.contains(IofXmlCompetitorMatchIssue.MISSING_CONTROL_CARD))
    }

    @Test
    fun startListDoesNotTreatStartNumberAsBibNumber() {
        val preview = IofStartListPreview(
            eventName = null,
            startDate = null,
            startTime = null,
            entries = listOf(
                startEntry(personId = null, controlCard = null, bibNumber = "8", familyName = "Wrong", givenName = "Name")
            )
        )

        val match = IofXmlImportMatcher.matchStartList(preview, raceData()).entries.single().match

        assertEquals(null, match.competitorId)
        assertEquals(null, match.basis)
        assertTrue(match.issues.contains(IofXmlCompetitorMatchIssue.UNKNOWN_COMPETITOR))
    }

    @Test
    fun startListReportsUnknownClassUnknownCompetitorAndDuplicateName() {
        val duplicateRaceData = raceData(
            extraCompetitors = listOf(
                competitor("duplicate-cara", "W21", "Cara", "Name", "idx-dup", 333334, 9)
            )
        )
        val preview = IofStartListPreview(
            eventName = null,
            startDate = null,
            startTime = null,
            entries = listOf(
                startEntry(className = "M99", personId = "idx-alice", controlCard = 111111),
                startEntry(personId = null, controlCard = 999999, bibNumber = null, familyName = "Missing", givenName = "Runner"),
                startEntry(className = "W21", personId = null, controlCard = null, bibNumber = null, familyName = "Name", givenName = "Cara")
            )
        )

        val matches = IofXmlImportMatcher.matchStartList(preview, duplicateRaceData).entries.map { it.match }

        assertTrue(matches[0].issues.contains(IofXmlCompetitorMatchIssue.UNKNOWN_CLASS))
        assertTrue(matches[1].issues.contains(IofXmlCompetitorMatchIssue.UNKNOWN_COMPETITOR))
        assertTrue(matches[2].issues.contains(IofXmlCompetitorMatchIssue.DUPLICATE_MATCH))
    }

    @Test
    fun resultListUsesSameCompetitorMatchingRules() {
        val preview = IofResultListPreview(
            eventName = null,
            startDate = null,
            startTime = null,
            entries = listOf(
                IofResultListEntryPreview(
                    className = "M21",
                    person = person(personId = null, familyName = "Runner", givenName = "Alice"),
                    controlCard = 111111,
                    startTimeIso = null,
                    finishTimeIso = null,
                    timeSeconds = null,
                    position = null,
                    status = "OK",
                    splitControls = emptyList()
                )
            )
        )

        val match = IofXmlImportMatcher.matchResultList(preview, raceData()).entries.single().match

        assertEquals("alice", match.competitorId)
        assertEquals(IofXmlCompetitorMatchBasis.CONTROL_CARD, match.basis)
    }

    private fun raceData(extraCompetitors: List<EventCompetitor> = emptyList()): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Race",
            apiKey = "",
            startDateTimeIso = "2026-06-29T09:00:00",
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 0
        )
        val m21 = category("M21")
        val w21 = category("W21")
        val competitors = listOf(
            competitor("alice", "M21", "Alice", "Runner", "idx-alice", 111111, 7),
            competitor("bob", "M21", "Bob", "Runner", "idx-bob", 222222, 8),
            competitor("cara", "W21", "Cara", "Name", "idx-cara", 333333, 1)
        ) + extraCompetitors
        return EventRaceData(
            race = race,
            categories = listOf(
                EventCategoryData(m21, controlPoints = emptyList(), competitors = emptyList()),
                EventCategoryData(w21, controlPoints = emptyList(), competitors = emptyList())
            ),
            aliases = emptyList(),
            competitorData = competitors.map { competitor ->
                EventCompetitorData(
                    competitorCategory = EventCompetitorCategory(
                        competitor = competitor,
                        category = if (competitor.categoryId == m21.id) m21 else w21
                    ),
                    readoutData = null
                )
            },
            unmatchedReadoutData = emptyList()
        )
    }

    private fun category(name: String): EventCategory =
        EventCategory(
            id = name,
            raceId = "race",
            name = name,
            isMan = name.startsWith("M"),
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun competitor(
        id: String,
        categoryId: String,
        firstName: String,
        lastName: String,
        index: String,
        siNumber: Int,
        startNumber: Int
    ): EventCompetitor =
        EventCompetitor(
            id = id,
            raceId = "race",
            categoryId = categoryId,
            firstName = firstName,
            lastName = lastName,
            club = "",
            index = index,
            isMan = categoryId.startsWith("M"),
            birthYear = null,
            siNumber = siNumber,
            siRent = false,
            startNumber = startNumber,
            drawnStartTimeSeconds = null,
            bibNumber = (1000 + startNumber).toString()
        )

    private fun startEntry(
        className: String = "M21",
        personId: String? = "idx-alice",
        controlCard: Int? = 111111,
        bibNumber: String? = "7",
        familyName: String = "Runner",
        givenName: String = "Alice"
    ): IofStartListEntryPreview =
        IofStartListEntryPreview(
            className = className,
            person = person(personId, familyName, givenName),
            bibNumber = bibNumber,
            controlCard = controlCard,
            startTimeIso = null,
            relativeStartTimeSeconds = null
        )

    private fun person(
        personId: String?,
        familyName: String,
        givenName: String
    ): IofPersonPreview =
        IofPersonPreview(
            personId = personId,
            personIdType = "TEST",
            familyName = familyName,
            givenName = givenName,
            organisationName = null
        )
}
