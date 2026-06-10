package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventAssignedControlWarningsTest {
    @Test
    fun warnsWhenDefinedBeaconIsMissingFromCategory() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.CLASSIC,
                controls = listOf(beacon()),
                categoryControlPoints = listOf(controlPoint("cp-31", "control-31", 31, ControlPointType.CONTROL))
            ),
            "cat-1"
        )

        assertEquals(listOf("Beacon"), warning?.missingBeaconLabels)
        assertEquals(false, warning?.hasNoAssignedFoxes)
        assertEquals(false, warning?.isClearingAllAssignments)
    }

    @Test
    fun doesNotRequireDefinedSpectatorForSprintCategory() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.SPRINT,
                controls = listOf(spectator(), beacon()),
                categoryControlPoints = listOf(
                    controlPoint("cp-31", "control-31", 31, ControlPointType.CONTROL),
                    controlPoint("cp-99", "control-m", 99, ControlPointType.BEACON)
                )
            ),
            "cat-1"
        )

        assertNull(warning)
    }

    @Test
    fun warnsWhenSprintCategoryHasSpectatorButNoBeacon() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.SPRINT,
                controls = listOf(spectator(), beacon()),
                categoryControlPoints = listOf(
                    controlPoint("cp-31", "control-31", 31, ControlPointType.CONTROL),
                    controlPoint("cp-s", "control-s", 46, ControlPointType.SEPARATOR)
                )
            ),
            "cat-1"
        )

        assertEquals(false, warning?.hasNoAssignedFoxes)
        assertEquals(listOf("Beacon"), warning?.missingBeaconLabels)
    }

    @Test
    fun warnsWhenRadioOCategoryHasNoDefinedOrAssignedBeacon() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.SPRINT,
                controls = listOf(spectator()),
                categoryControlPoints = listOf(
                    controlPoint("cp-31", "control-31", 31, ControlPointType.CONTROL),
                    controlPoint("cp-s", "control-s", 46, ControlPointType.SEPARATOR)
                )
            ),
            "cat-1"
        )

        assertEquals(false, warning?.hasNoAssignedFoxes)
        assertEquals(listOf("Beacon"), warning?.missingBeaconLabels)
    }

    @Test
    fun warnsWhenClassicCategoryHasBeaconButNoFoxes() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.CLASSIC,
                controls = listOf(spectator(), beacon()),
                categoryControlPoints = listOf(controlPoint("cp-99", "control-m", 99, ControlPointType.BEACON))
            ),
            "cat-1"
        )

        assertEquals(true, warning?.hasNoAssignedFoxes)
        assertEquals(emptyList(), warning?.missingBeaconLabels)
    }

    @Test
    fun warnsWhenAllAssignmentsAreCleared() {
        val warning = EventAssignedControlWarnings.forCategory(
            raceData(
                raceType = RaceType.SPRINT,
                controls = listOf(spectator(), beacon()),
                categoryControlPoints = emptyList()
            ),
            "cat-1"
        )

        assertEquals(true, warning?.hasNoAssignedFoxes)
        assertEquals(true, warning?.isClearingAllAssignments)
        assertEquals(listOf("Beacon"), warning?.missingBeaconLabels)
    }

    private fun raceData(
        raceType: RaceType,
        controls: List<EventControl>,
        categoryControlPoints: List<EventControlPoint>
    ): EventRaceData =
        EventRaceData(
            race = EventRace(
                id = "race",
                name = "Warning Race",
                apiKey = "",
                startDateTimeIso = "2026-05-31T10:00",
                raceType = raceType,
                raceLevel = RaceLevel.PRACTICE,
                raceBand = RaceBand.M80,
                timeLimitSeconds = 7_200
            ),
            categories = listOf(
                EventCategoryData(
                    category = EventCategory(
                        id = "cat-1",
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
                    controlPoints = categoryControlPoints,
                    competitors = emptyList()
                )
            ),
            aliases = emptyList(),
            controls = controls,
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    private fun controlPoint(
        id: String,
        controlId: String,
        siCode: Int,
        type: ControlPointType
    ): EventControlPoint =
        EventControlPoint(
            id = id,
            categoryId = "cat-1",
            siCode = siCode,
            type = type,
            order = 0,
            controlId = controlId
        )

    private fun spectator(): EventControl =
        EventControl("control-s", "race", "S", 46, ControlPointType.SEPARATOR, publicLabel = "Spectator")

    private fun beacon(): EventControl =
        EventControl("control-m", "race", "M", 99, ControlPointType.BEACON, publicLabel = "Beacon")
}
