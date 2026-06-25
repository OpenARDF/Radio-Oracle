package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCompetitor
import org.openardf.radiooracle.shared.event.EventCompetitorCategory
import org.openardf.radiooracle.shared.event.EventCompetitorData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData

class DesktopCategoryActionsTest {
    @Test
    fun removesProtectedCategoryAndAssignedCompetitorsWhenRequested() {
        val projectFile = projectFile()

        val result = DesktopCategoryActions.removeCategory(
            projectFile = projectFile,
            categoryIdOrName = "M21",
            deleteCompetitors = true
        )

        assertTrue(result.hadProtectedCourseData)
        assertEquals("cat-m21", result.categoryId)
        assertEquals("M21", result.categoryName)
        assertEquals(1, result.removedCompetitorCount)
        assertFalse(result.projectFile.raceData.categories.any { it.category.id == "cat-m21" })
        assertEquals(listOf("cat-w21"), result.projectFile.raceData.categories.map { it.category.id })
        assertEquals(listOf("comp-w21"), result.projectFile.raceData.competitorData.map {
            it.competitorCategory.competitor.id
        })
    }

    private fun projectFile(): EventProjectFile {
        val m21 = category("cat-m21", "M21", encryptedCourseInfo = "encrypted-course")
        val w21 = category("cat-w21", "W21")
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = "race",
                    name = "Category Action Test",
                    apiKey = "",
                    startDateTimeIso = "2026-06-03T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(m21, controlPoints = emptyList(), competitors = emptyList()),
                    EventCategoryData(w21, controlPoints = emptyList(), competitors = emptyList())
                ),
                aliases = emptyList(),
                competitorData = listOf(
                    competitor("comp-m21", m21),
                    competitor("comp-w21", w21)
                ),
                unmatchedReadoutData = emptyList()
            )
        )
    }

    private fun category(id: String, name: String, encryptedCourseInfo: String? = null): EventCategory =
        EventCategory(
            id = id,
            raceId = "race",
            name = name,
            isMan = true,
            maxAge = null,
            lengthMeters = 0,
            climbMeters = 0,
            order = 0,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = "",
            encryptedIdealOrder = null,
            encryptedCourseInfo = encryptedCourseInfo
        )

    private fun competitor(id: String, category: EventCategory): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = category.id,
                    firstName = id,
                    lastName = "Runner",
                    club = "OPEN",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    startNumber = null,
                    drawnStartTimeSeconds = null
                ),
                category = category
            ),
            readoutData = null
        )
}
