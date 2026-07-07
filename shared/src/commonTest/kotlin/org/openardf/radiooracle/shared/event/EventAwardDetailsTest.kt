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

import org.openardf.radiooracle.shared.domain.PunchStatus
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventAwardDetailsTest {
    @Test
    fun suppressesAwardsAndNoticeForPracticeRaces() {
        val awards = EventAwardDetails.from(
            raceData(
                raceLevel = RaceLevel.PRACTICE,
                competitors = listOf(competitorData("usa", "Alice", "Usa", usaEligible = true, points = 3))
            )
        )

        assertNull(awards.publicationNotice)
        assertFalse(awards.hasAwards)
        assertEquals(emptyList(), awards.categories)
    }

    @Test
    fun marksNonPracticeResultsAsPreliminary() {
        val awards = EventAwardDetails.from(
            raceData(
                raceLevel = RaceLevel.NATIONAL,
                competitors = listOf(competitorData("usa", "Alice", "Usa", usaEligible = true, points = 3))
            )
        )

        assertEquals(PRELIMINARY_RESULT_NOTICE, awards.publicationNotice)
    }

    @Test
    fun derivesUsaAndRegion2MedalsFromEligibility() {
        val awards = EventAwardDetails.from(
            raceData(
                competitors = listOf(
                    competitorData("canada", "Cara", "Canada", region2Eligible = true, points = 5),
                    competitorData("usa-one", "Alice", "Usa", usaEligible = true, points = 4),
                    competitorData("guest", "Gary", "Guest", points = 3),
                    competitorData("usa-two", "Bob", "Usa", usaEligible = true, points = 2)
                )
            )
        )
        val category = awards.categories.single()

        assertEquals(listOf("USA Alice", "USA Bob"), category.usaAwards.map { it.competitorName })
        assertEquals(listOf("Gold", "Silver"), category.usaAwards.map { it.medal })
        assertEquals(listOf(2, 4), category.usaAwards.map { it.overallPlace })

        assertEquals(listOf("CANADA Cara", "USA Alice", "USA Bob"), category.region2Awards.map { it.competitorName })
        assertEquals(listOf("Gold", "Silver", "Bronze"), category.region2Awards.map { it.medal })
        assertEquals(listOf(1, 2, 4), category.region2Awards.map { it.overallPlace })
    }

    @Test
    fun onlyOkResultsCanReceiveAwards() {
        val awards = EventAwardDetails.from(
            raceData(
                competitors = listOf(
                    competitorData("mp", "Mira", "Mp", usaEligible = true, points = 5, resultStatus = ResultStatus.MISPUNCHED),
                    competitorData("ok", "Alice", "Ok", usaEligible = true, points = 3)
                )
            )
        )

        assertEquals(listOf("OK Alice"), awards.categories.single().usaAwards.map { it.competitorName })
    }

    @Test
    fun keepsTiedEligibleCompetitorsOnTheSameMedal() {
        val awards = EventAwardDetails.from(
            raceData(
                competitors = listOf(
                    competitorData("first", "Alice", "Tie", usaEligible = true, points = 3),
                    competitorData("second", "Bob", "Tie", usaEligible = true, points = 3),
                    competitorData("third", "Cara", "Next", usaEligible = true, points = 2)
                )
            )
        )

        assertEquals(listOf("Gold", "Gold", "Silver"), awards.categories.single().usaAwards.map { it.medal })
        assertEquals(listOf(1, 1, 2), awards.categories.single().usaAwards.map { it.awardPlace })
    }

    @Test
    fun allSuccessfulFinishersModeIncludesAwardLevelsBeyondThird() {
        val competitors = listOf(
            competitorData("first", "Alice", "First", usaEligible = true, points = 6),
            competitorData("second", "Bob", "Second", usaEligible = true, points = 5),
            competitorData("third", "Cara", "Third", usaEligible = true, points = 4),
            competitorData("fourth", "Dina", "Fourth", usaEligible = true, points = 3),
            competitorData("fifth", "Eli", "Fifth", usaEligible = true, points = 2),
            competitorData("dnf", "Nora", "Dnf", usaEligible = true, points = 1, resultStatus = ResultStatus.DID_NOT_FINISH)
        )

        val firstToThird = EventAwardDetails.from(raceData(competitors = competitors))
        val allFinishers = EventAwardDetails.from(
            raceData(competitors = competitors),
            EventAwardDisplayMode.ALL_SUCCESSFUL_FINISHERS
        )

        assertEquals(listOf("Gold", "Silver", "Bronze"), firstToThird.categories.single().usaAwards.map { it.awardLevel })
        assertEquals(
            listOf("Gold", "Silver", "Bronze", "4th", "5th"),
            allFinishers.categories.single().usaAwards.map { it.awardLevel }
        )
        assertEquals(
            listOf("FIRST Alice", "SECOND Bob", "THIRD Cara", "FOURTH Dina", "FIFTH Eli"),
            allFinishers.categories.single().usaAwards.map { it.competitorName }
        )
    }

    private fun raceData(
        raceLevel: RaceLevel = RaceLevel.NATIONAL,
        competitors: List<EventCompetitorData>
    ): EventRaceData {
        val race = EventRace(
            id = "race",
            name = "Awards Race",
            apiKey = "",
            startDateTimeIso = "2026-06-01T10:00",
            raceType = RaceType.CLASSIC,
            raceLevel = raceLevel,
            raceBand = RaceBand.M80,
            timeLimitSeconds = 7_200
        )
        val category = category()
        return EventRaceData(
            race = race,
            categories = listOf(EventCategoryData(category, controlPoints = emptyList(), competitors = competitors.map { it.competitorCategory.competitor })),
            aliases = emptyList(),
            competitorData = competitors,
            unmatchedReadoutData = emptyList()
        )
    }

    private fun category(): EventCategory =
        EventCategory(
            id = "category",
            raceId = "race",
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun competitorData(
        id: String,
        firstName: String,
        lastName: String,
        usaEligible: Boolean? = null,
        region2Eligible: Boolean? = null,
        points: Int,
        resultStatus: ResultStatus = ResultStatus.OK
    ): EventCompetitorData {
        val category = category()
        val competitor = EventCompetitor(
            id = id,
            raceId = "race",
            categoryId = category.id,
            firstName = firstName,
            lastName = lastName,
            club = "Club",
            index = "IDX-$id",
            isMan = true,
            birthYear = null,
            siNumber = 1000 + points,
            siRent = false,
            startNumber = null,
            drawnStartTimeSeconds = null,
            usaChampEligible = usaEligible,
            region2ChampEligible = region2Eligible
        )
        return EventCompetitorData(
            competitorCategory = EventCompetitorCategory(competitor, category),
            readoutData = EventReadoutData(
                result = EventResult(
                    id = "result-$id",
                    raceId = "race",
                    competitorId = id,
                    siNumber = competitor.siNumber,
                    cardType = 5,
                    checkTimeSeconds = null,
                    startTimeSeconds = 36_000,
                    finishTimeSeconds = 36_000 + (10 - points) * 60L,
                    readoutDateTimeIso = "2026-06-01T10:30",
                    automaticStatus = true,
                    resultStatus = resultStatus,
                    points = points,
                    runTimeSeconds = (10 - points) * 60L,
                    modified = false,
                    sent = false,
                    place = 0,
                    categoryId = category.id
                ),
                punches = listOf(
                    EventAliasPunch(
                        punch = EventPunch(
                            id = "punch-$id",
                            raceId = "race",
                            resultId = "result-$id",
                            cardNumber = competitor.siNumber,
                            siCode = 31,
                            siTimeSeconds = 36_100,
                            originalSiTimeSeconds = 36_100,
                            punchType = SIRecordType.CONTROL,
                            order = 1,
                            punchStatus = PunchStatus.VALID,
                            splitSeconds = 60
                        ),
                        alias = null
                    )
                )
            )
        )
    }
}
