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
import kotlin.test.assertTrue

class EventControlCatalogTest {
    @Test
    fun classicPresetUsesFiveControlsAndBeacon() {
        val controls = EventControlCatalog.classicPreset("race")

        assertEquals(listOf("1", "2", "3", "4", "5", "M"), controls.map { it.label })
        assertEquals(listOf(31, 32, 33, 34, 35, 99), controls.map { it.siCode })
        assertEquals(ControlPointType.BEACON, controls.last().type)
    }

    @Test
    fun sprintPresetUsesEnglishFastControlLabels() {
        val controls = EventControlCatalog.sprintPreset("race")

        assertEquals(
            listOf("1", "2", "3", "4", "5", "F1", "F2", "F3", "F4", "F5", "S", "M"),
            controls.map { it.label }
        )
        assertEquals(listOf(31, 32, 33, 34, 35, 41, 42, 43, 44, 45, 46, 99), controls.map { it.siCode })
        assertEquals(ControlPointType.SEPARATOR, controls.first { it.label == "S" }.type)
        assertEquals(ControlPointType.BEACON, controls.first { it.label == "M" }.type)
    }

    @Test
    fun modelAllowsMergedLogicalControlsSharingSportIdentCode() {
        val controls = listOf(
            EventControl("control-1", "race", "1", 31, ControlPointType.CONTROL),
            EventControl("control-f1", "race", "F1", 31, ControlPointType.CONTROL)
        )

        assertEquals(2, controls.distinctBy { it.label }.size)
        assertEquals(1, controls.distinctBy { it.siCode }.size)
    }

    @Test
    fun assignedControlsUseCourseOrderAndCanonicalControlCodeAndRole() {
        val control31 = EventControl("control-31", "race", "1", 31, ControlPointType.CONTROL)
        val control32 = EventControl("control-32", "race", "2", 32, ControlPointType.BEACON)
        val category = categoryData(
            categoryId = "m21",
            controlPoints = listOf(
                EventControlPoint(
                    id = "point-31",
                    categoryId = "m21",
                    siCode = 999,
                    type = ControlPointType.SEPARATOR,
                    order = 2,
                    controlId = control31.id
                ),
                EventControlPoint(
                    id = "point-32",
                    categoryId = "m21",
                    siCode = 998,
                    type = ControlPointType.SEPARATOR,
                    order = 1,
                    controlId = control32.id
                )
            )
        )
        val controls = listOf(control31, control32)

        val assignedControls = EventControlCatalog.assignedControls(category, controls)
        val definitions = EventControlCatalog.assignedControlDefinitions(category, controls)

        assertEquals(listOf(32, 31), assignedControls.map { it.siCode })
        assertEquals(listOf(32, 31), definitions.map { it.siCode })
        assertEquals(listOf(ControlPointType.BEACON, ControlPointType.CONTROL), definitions.map { it.type })
    }

    @Test
    fun derivesControlsFromExistingCoursesAndAliases() {
        val raceData = raceData(
            categories = listOf(
                categoryData(
                    categoryId = "m21",
                    controlPoints = listOf(
                        EventControlPoint("cp-31", "m21", 31, ControlPointType.CONTROL, 1),
                        EventControlPoint("cp-90", "m21", 90, ControlPointType.BEACON, 2)
                    )
                )
            ),
            aliases = listOf(EventAlias("alias-31", "race", 31, "F1"))
        )

        val controls = EventControlCatalog.deriveFromRaceData(raceData)

        assertEquals(listOf("F1", "90B"), controls.map { it.label })
        assertEquals(listOf(31, 90), controls.map { it.siCode })
    }

    @Test
    fun backfillLeavesExistingControlsUntouched() {
        val existingControl = EventControl("control-custom", "race", "Custom", 31, ControlPointType.CONTROL)
        val projectFile = EventProjectFile(raceData = raceData(controls = listOf(existingControl)))

        assertEquals(projectFile, EventControlCatalog.backfillControls(projectFile))
    }

    private fun raceData(
        categories: List<EventCategoryData> = emptyList(),
        aliases: List<EventAlias> = emptyList(),
        controls: List<EventControl> = emptyList()
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Race",
                apiKey = "",
                startDateTimeIso = "2026-05-30T10:00",
                raceType = RaceType.CLASSIC,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = categories,
            aliases = aliases,
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList(),
            controls = controls
        )

    private fun categoryData(categoryId: String, controlPoints: List<EventControlPoint>): EventCategoryData =
        EventCategoryData(
            category = EventCategory(
                id = categoryId,
                raceId = "race",
                name = "M21",
                isMan = true,
                maxAge = null,
                lengthMeters = 0,
                climbMeters = 0,
                order = 0,
                differentProperties = false,
                raceType = null,
                raceBand = null,
                timeLimitSeconds = null,
                controlPointsString = ""
            ),
            controlPoints = controlPoints,
            competitors = emptyList()
        )
}
