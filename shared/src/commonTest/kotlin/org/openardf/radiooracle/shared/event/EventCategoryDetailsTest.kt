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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCategoryDetailsTest {
    @Test
    fun buildsDisplayRowsUsingEventRaceSettings() {
        val rows = EventCategoryDetails.from(raceData())

        assertEquals(2, rows.size)
        assertEquals("W21", rows[0].id)
        assertEquals("W21", rows[0].name)
        assertEquals(false, rows[0].isMan)
        assertEquals("5000", rows[0].lengthMetersText)
        assertEquals("100", rows[0].climbMetersText)
        assertEquals("Classic", rows[0].raceTypeLabel)
        assertEquals("80m", rows[0].raceBandLabel)
        assertEquals("120:00", rows[0].timeLimitText)
        assertEquals("Foxhole 32", rows[0].controlPointsText)
        assertEquals(0, rows[0].assignedCompetitorCount)

        assertEquals("M21", rows[1].id)
        assertEquals("M21", rows[1].name)
        assertEquals(true, rows[1].isMan)
        assertEquals("Classic", rows[1].raceTypeLabel)
        assertEquals("80m", rows[1].raceBandLabel)
        assertEquals("120:00", rows[1].timeLimitText)
        assertEquals(0, rows[1].assignedCompetitorCount)
    }

    @Test
    fun sortsDisplayRowsByNaturalCategoryName() {
        val rows = EventCategoryDetails.from(
            raceData().copy(
                categories = listOf(
                    categoryData("W55", order = 1, differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null),
                    categoryData("M60", order = 2, differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null),
                    categoryData("M21", order = 3, differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null),
                    categoryData("W12", order = 4, differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null),
                    categoryData("M50", order = 5, differentProperties = false, raceType = null, raceBand = null, timeLimitSeconds = null)
                )
            )
        )

        assertEquals(listOf("W12", "W55", "M21", "M50", "M60"), rows.map { it.name })
    }

    @Test
    fun buildsDisplayRowsWithRawControlsWhenAliasesAreDisabled() {
        val rows = EventCategoryDetails.from(raceData(), useAliases = false)

        assertEquals("31 32", rows[1].controlPointsText)
    }

    @Test
    fun leavesOrienteeringControlDisplayUnchanged() {
        val rows = EventCategoryDetails.from(raceData(defaultRaceType = RaceType.ORIENTEERING))

        assertEquals("31 32", rows.first { it.name == "M21" }.controlPointsText)
    }

    @Test
    fun reportsAssignedCompetitorCountsFromTopLevelCompetitorData() {
        val rows = EventCategoryDetails.from(
            raceData().copy(
                competitorData = listOf(
                    competitorData("competitor-1", "M21"),
                    competitorData("competitor-2", "M21"),
                    competitorData("competitor-3", "W21")
                )
            )
        )

        assertEquals(2, rows.first { it.name == "M21" }.assignedCompetitorCount)
        assertEquals(1, rows.first { it.name == "W21" }.assignedCompetitorCount)
    }

    @Test
    fun formatsAssignedPublicLabelsSoTheyCanBeParsedAfterReopening() {
        val controls = listOf(
            EventControl("control-f1", "race", "F1", 31, ControlPointType.CONTROL, publicLabel = "Fox 1"),
            EventControl("control-f2", "race", "F2", 32, ControlPointType.CONTROL, publicLabel = "Fox 2"),
            EventControl("control-m", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
        )
        val categoryData = categoryData(
            name = "M21",
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null
        ).copy(
            controlPoints = listOf(
                EventControlPoint("cp-1", "M21", 32, ControlPointType.CONTROL, 1, "control-f2"),
                EventControlPoint("cp-2", "M21", 99, ControlPointType.BEACON, 2, "control-m"),
                EventControlPoint("cp-3", "M21", 31, ControlPointType.CONTROL, 3, "control-f1")
            ),
            publicControlIds = listOf("control-f2", "control-m", "control-f1")
        )
        val raceData = raceData().copy(categories = listOf(categoryData), controls = controls)

        val displayedText = EventCategoryDetails.from(raceData).single().controlPointsText
        val reparsed = EventProjectEditor.updateCategoryControlPoints(
            EventProjectFile(raceData = raceData),
            "M21",
            displayedText
        ) { index -> "new-cp-$index" }

        assertEquals("'Fox 1' 'Fox 2' Beacon", displayedText)
        assertEquals(listOf("control-f1", "control-f2", "control-m"), reparsed.raceData.categories.single().controlPoints.map { it.controlId })
    }

    @Test
    fun displaysAssignedControlsByPublicFoxLabelOrderBeforeSiCodeOrder() {
        val controls = listOf(
            EventControl("control-f1", "race", "1", 35, ControlPointType.CONTROL, publicLabel = "Fox 1"),
            EventControl("control-f2", "race", "2", 34, ControlPointType.CONTROL, publicLabel = "Fox 2"),
            EventControl("control-f3", "race", "3", 33, ControlPointType.CONTROL, publicLabel = "Fox 3"),
            EventControl("control-f4", "race", "4", 32, ControlPointType.CONTROL, publicLabel = "Fox 4"),
            EventControl("control-f5", "race", "5", 31, ControlPointType.CONTROL, publicLabel = "Fox 5"),
            EventControl("control-m", "race", "M", 99, ControlPointType.BEACON, publicLabel = "B")
        )
        val categoryData = categoryData(
            name = "M21",
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null
        ).copy(
            controlPoints = listOf(
                EventControlPoint("cp-1", "M21", 31, ControlPointType.CONTROL, 1, "control-f5"),
                EventControlPoint("cp-2", "M21", 32, ControlPointType.CONTROL, 2, "control-f4"),
                EventControlPoint("cp-3", "M21", 33, ControlPointType.CONTROL, 3, "control-f3"),
                EventControlPoint("cp-4", "M21", 34, ControlPointType.CONTROL, 4, "control-f2"),
                EventControlPoint("cp-5", "M21", 35, ControlPointType.CONTROL, 5, "control-f1"),
                EventControlPoint("cp-6", "M21", 99, ControlPointType.BEACON, 6, "control-m")
            ),
            publicControlIds = listOf("control-f5", "control-f4", "control-f3", "control-f2", "control-f1", "control-m")
        )
        val raceData = raceData().copy(categories = listOf(categoryData), controls = controls)

        val displayedText = EventCategoryDetails.from(raceData).single().controlPointsText

        assertEquals("'Fox 1' 'Fox 2' 'Fox 3' 'Fox 4' 'Fox 5' B", displayedText)
    }

    @Test
    fun formatsSprintAssignedControlsWithSpectatorBetweenSlowAndFastFoxes() {
        val controls = listOf(
            EventControl("control-slow-1", "race", "1", 31, ControlPointType.CONTROL, publicLabel = "Slow 1"),
            EventControl("control-slow-2", "race", "2", 32, ControlPointType.CONTROL, publicLabel = "Slow 2"),
            EventControl("control-fast-1", "race", "F1", 41, ControlPointType.CONTROL, publicLabel = "Fast 1"),
            EventControl("control-fast-2", "race", "F2", 42, ControlPointType.CONTROL, publicLabel = "Fast 2"),
            EventControl("control-s", "race", "S", 46, ControlPointType.SEPARATOR, publicLabel = "Spectator"),
            EventControl("control-m", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
        )
        val categoryData = categoryData(
            name = "M21",
            order = 1,
            differentProperties = true,
            raceType = RaceType.SPRINT,
            raceBand = null,
            timeLimitSeconds = null
        ).copy(
            controlPoints = listOf(
                EventControlPoint("cp-1", "M21", 42, ControlPointType.CONTROL, 1, "control-fast-2"),
                EventControlPoint("cp-2", "M21", 31, ControlPointType.CONTROL, 2, "control-slow-1"),
                EventControlPoint("cp-3", "M21", 99, ControlPointType.BEACON, 3, "control-m"),
                EventControlPoint("cp-4", "M21", 46, ControlPointType.SEPARATOR, 4, "control-s"),
                EventControlPoint("cp-5", "M21", 41, ControlPointType.CONTROL, 5, "control-fast-1"),
                EventControlPoint("cp-6", "M21", 32, ControlPointType.CONTROL, 6, "control-slow-2")
            )
        )
        val raceData = raceData(defaultRaceType = RaceType.SPRINT).copy(categories = listOf(categoryData), controls = controls)

        val displayedText = EventCategoryDetails.from(raceData).single().controlPointsText

        assertEquals("'Slow 1' 'Slow 2' Spectator 'Fast 1' 'Fast 2' Beacon", displayedText)
    }

    @Test
    fun formatsSprintAssignedControlsWithCustomSiCodesInTraditionalOrder() {
        val controls = listOf(
            EventControl("control-s", "race", "S", 137, ControlPointType.CONTROL, publicLabel = "S"),
            EventControl("control-1", "race", "1", 161, ControlPointType.CONTROL, publicLabel = "1"),
            EventControl("control-2", "race", "2", 162, ControlPointType.CONTROL, publicLabel = "2"),
            EventControl("control-1f", "race", "1F", 171, ControlPointType.CONTROL, publicLabel = "1F"),
            EventControl("control-2f", "race", "2F", 172, ControlPointType.CONTROL, publicLabel = "2F"),
            EventControl("control-b", "race", "B", 136, ControlPointType.BEACON, publicLabel = "B")
        )
        val categoryData = categoryData(
            name = "M21",
            order = 1,
            differentProperties = true,
            raceType = RaceType.SPRINT,
            raceBand = null,
            timeLimitSeconds = null
        ).copy(
            controlPoints = listOf(
                EventControlPoint("cp-s", "M21", 137, ControlPointType.CONTROL, 1, "control-s"),
                EventControlPoint("cp-1f", "M21", 171, ControlPointType.CONTROL, 2, "control-1f"),
                EventControlPoint("cp-1", "M21", 161, ControlPointType.CONTROL, 3, "control-1"),
                EventControlPoint("cp-2f", "M21", 172, ControlPointType.CONTROL, 4, "control-2f"),
                EventControlPoint("cp-2", "M21", 162, ControlPointType.CONTROL, 5, "control-2"),
                EventControlPoint("cp-b", "M21", 136, ControlPointType.BEACON, 6, "control-b")
            ),
            publicControlIds = listOf("control-s", "control-1", "control-2", "control-1f", "control-2f", "control-b")
        )
        val raceData = raceData(defaultRaceType = RaceType.SPRINT).copy(categories = listOf(categoryData), controls = controls)

        val displayedText = EventCategoryDetails.from(raceData).single().controlPointsText

        assertEquals("1 2 S 1F 2F B", displayedText)
    }

    private fun raceData(defaultRaceType: RaceType = RaceType.CLASSIC): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Category Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = defaultRaceType,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                categoryData(
                    name = "M21",
                    order = 2,
                    differentProperties = false,
                    raceType = null,
                    raceBand = null,
                    timeLimitSeconds = null
                ),
                categoryData(
                    name = "W21",
                    order = 1,
                    differentProperties = true,
                    raceType = RaceType.SPRINT,
                    raceBand = RaceBand.M2,
                    timeLimitSeconds = 3_600
                )
            ),
            aliases = listOf(EventAlias("alias-31", "race", 31, "Foxhole")),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun categoryData(
        name: String,
        order: Int,
        differentProperties: Boolean,
        raceType: RaceType?,
        raceBand: RaceBand?,
        timeLimitSeconds: Long?
    ): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = name,
                raceId = "race",
                name = name,
                isMan = name.startsWith("M"),
                maxAge = null,
                lengthMeters = 5_000,
                climbMeters = 100,
                order = order,
                differentProperties = differentProperties,
                raceType = raceType,
                raceBand = raceBand,
                timeLimitSeconds = timeLimitSeconds,
                controlPointsString = "31 32"
            ),
            controlPoints = listOf(
                EventControlPoint("cp-31-$name", name, 31, ControlPointType.CONTROL, 0),
                EventControlPoint("cp-32-$name", name, 32, ControlPointType.CONTROL, 1)
            ),
            competitors = emptyList()
        )

    private fun competitorData(id: String, categoryId: String): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = categoryId,
                    firstName = id,
                    lastName = "Runner",
                    club = "",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = null,
                    siRent = false,
                    startNumber = null,
                    drawnStartTimeSeconds = null
                ),
                category = null
            ),
            readoutData = null
        )
}
