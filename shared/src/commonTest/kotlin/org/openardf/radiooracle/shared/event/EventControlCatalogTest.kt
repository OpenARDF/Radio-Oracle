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
