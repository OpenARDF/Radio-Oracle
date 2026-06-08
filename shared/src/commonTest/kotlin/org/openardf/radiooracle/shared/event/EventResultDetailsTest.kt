package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.ResultStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class EventResultDetailsTest {
    @Test
    fun buildsDisplayRowsForRankedCompetitorResults() {
        val rows = EventResultDetails.from(
            raceData(
                categories = emptyList(),
                competitorData = listOf(
                    competitorData(
                        id = "competitor",
                        categoryId = null,
                        category = null,
                        firstName = "Alice",
                        lastName = "Runner",
                        resultId = "result",
                        points = 3,
                        place = 1
                    )
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals("result", rows[0].id)
        assertEquals("Uncategorized", rows[0].categoryName)
        assertEquals("1", rows[0].placeText)
        assertEquals("RUNNER Alice", rows[0].competitorName)
        assertEquals(ResultStatus.OK, rows[0].resultStatus)
        assertEquals(true, rows[0].automaticStatus)
        assertEquals("OK", rows[0].statusLabel)
        assertEquals("3", rows[0].pointsText)
        assertEquals("00:20:00", rows[0].runTimeText)
    }

    @Test
    fun sortsDisplayRowsByCategoryThenPlace() {
        val firstCategory = category("cat-a", "M21", 1)
        val secondCategory = category("cat-b", "M50", 2)
        val rows = EventResultDetails.from(
            raceData(
                categories = listOf(firstCategory, secondCategory),
                competitorData = listOf(
                    competitorData("second", "cat-b", secondCategory.category, "Bob", "Second", points = 1, place = 0),
                    competitorData("first", "cat-a", firstCategory.category, "Alice", "First", points = 2, place = 0),
                    competitorData("third", "cat-a", firstCategory.category, "Cara", "Third", points = 1, place = 0)
                )
            )
        )

        assertEquals(listOf("M21", "M21", "M50"), rows.map { it.categoryName })
        assertEquals(listOf("1", "2", "1"), rows.map { it.placeText })
        assertEquals(listOf("FIRST Alice", "THIRD Cara", "SECOND Bob"), rows.map { it.competitorName })
    }

    private fun raceData(
        categories: List<EventCategoryData>,
        competitorData: List<EventCompetitorData>
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Result Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = categories,
            aliases = emptyList(),
            competitorData = competitorData,
            unmatchedReadoutData = emptyList()
        )

    private fun category(id: String, name: String, order: Int): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = id,
                raceId = "race",
                name = name,
                isMan = false,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = order,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = emptyList(),
            competitors = emptyList()
        )

    private fun competitorData(
        id: String,
        categoryId: String?,
        category: EventCategory?,
        firstName: String,
        lastName: String,
        resultId: String = "result-$id",
        points: Int = 1,
        place: Int
    ): EventCompetitorData {
        val competitor = EventCompetitor(
            id = id,
            raceId = "race",
            categoryId = categoryId,
            firstName = firstName,
            lastName = lastName,
            club = "",
            index = "",
            isMan = false,
            birthYear = null,
            siNumber = 123456,
            siRent = false,
            startNumber = 1,
            drawnStartTimeSeconds = null
        )
        return EventCompetitorData(
            competitorCategory = EventCompetitorCategory(competitor, category = category),
            readoutData = EventReadoutData(
                result = EventResult(
                    id = resultId,
                    raceId = "race",
                    competitorId = competitor.id,
                    siNumber = 123456,
                    cardType = 10,
                    checkTimeSeconds = null,
                    startTimeSeconds = 600,
                    finishTimeSeconds = 1_800,
                    readoutDateTimeIso = "2026-05-31T11:00",
                    automaticStatus = true,
                    resultStatus = ResultStatus.OK,
                    points = points,
                    runTimeSeconds = 1_200,
                    modified = false,
                    sent = false,
                    place = place
                ),
                punches = emptyList()
            )
        )
    }
}
