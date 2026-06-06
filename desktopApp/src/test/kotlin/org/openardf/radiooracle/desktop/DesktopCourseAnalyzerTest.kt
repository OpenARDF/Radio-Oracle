package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

class DesktopCourseAnalyzerTest {
    @Test
    fun calculatesShortestRouteAndFlagsProvidedOrderMismatch() {
        val projectFile = projectFile(foxCount = 5)
        val protectedInfo = protectedInfo(foxCount = 5)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "35 34 33 32 31 Beacon"
        )

        assertEquals(emptyList<String>(), summary.missingElements)
        assertEquals(120, summary.calculatedRouteCount)
        assertEquals(listOf("31", "32", "33", "34", "35", "Beacon"), summary.calculatedIdealOrder)
        assertEquals(listOf("35", "34", "33", "32", "31", "Beacon"), summary.providedIdealOrder)
        assertFalse(summary.idealOrderMatches!!)
        assertTrue(summary.calculatedStraightLineMeters!! < summary.providedStraightLineMeters!!)
    }

    @Test
    fun calculatesProtectedEffectiveLengthIdealTimeAndClassicWaitRows() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertEquals(6, summary.calculatedRouteCount)
        assertEquals(true, summary.idealOrderMatches)
        assertEquals(4_000, summary.routeLengthMeters)
        assertEquals(100, summary.climbMeters)
        assertEquals(5_000, summary.effectiveLengthMeters)
        assertNotNull(summary.estimatedIdealSeconds)
        assertEquals(5, summary.elevationProfile.size)
        assertEquals(0, summary.elevationProfile.first().distanceMeters)
        assertEquals(100.0, summary.elevationProfile.first().elevationMeters, 0.001)
        assertEquals(listOf("31", "32", "33"), summary.waitRows.map { it.controlLabel })
        assertTrue(summary.metrics.any { it.label == "Effective length" && it.value == "5.0 km" })
    }

    @Test
    fun checksWhetherRenumberingCanReduceClassicWaitTime() {
        val projectFile = projectFile(
            foxCount = 3,
            publicLabels = listOf("33", "32", "31")
        )
        val protectedInfo = protectedInfo(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        val renumbering = requireNotNull(summary.waitRenumbering)
        assertTrue(renumbering.improvesWait)
        assertTrue(renumbering.currentTotalWaitSeconds > renumbering.bestTotalWaitSeconds)
        assertEquals(listOf("33", "32", "31"), renumbering.assignments.map { it.controlLabel })
        assertEquals(listOf("33", "32", "31"), renumbering.assignments.map { it.currentSlotLabel })
        assertTrue(renumbering.assignments.map { it.suggestedSlotLabel } != renumbering.assignments.map { it.currentSlotLabel })
    }

    @Test
    fun reportsMissingProtectedDataBeforePartialAnalysis() {
        val projectFile = projectFile(foxCount = 3)

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = null,
            protectedIdealOrderText = null
        )

        assertTrue(summary.missingElements.any { it.contains("Protected route data") })
        assertTrue(summary.missingElements.any { it.contains("Protected route geometry") })
        assertTrue(summary.missingElements.any { it.contains("Protected ideal order") })
        assertEquals(0, summary.calculatedRouteCount)
        assertEquals(null, summary.estimatedIdealSeconds)
    }

    @Test
    fun reportsMissingElevationSamples() {
        val projectFile = projectFile(foxCount = 3)
        val protectedInfo = protectedInfo(foxCount = 3).copy(
            route = protectedInfo(foxCount = 3).route.mapIndexed { index, point ->
                if (index == 1) point.copy(elevationMeters = null) else point
            }
        )

        val summary = DesktopCourseAnalyzer.analyze(
            projectFile = projectFile,
            categoryId = CATEGORY_ID,
            protectedCourseInfo = protectedInfo,
            protectedIdealOrderText = "31 32 33 Beacon"
        )

        assertTrue(summary.missingElements.any { it.contains("Route elevation samples") })
        assertEquals(null, summary.estimatedIdealSeconds)
        assertEquals(emptyList<DesktopCourseElevationProfilePoint>(), summary.elevationProfile)
    }

    private fun projectFile(foxCount: Int, publicLabels: List<String>? = null): EventProjectFile {
        val controls = (1..foxCount).map { number ->
            EventControl(
                id = "control-$number",
                raceId = RACE_ID,
                label = (30 + number).toString(),
                siCode = 30 + number,
                type = ControlPointType.CONTROL,
                publicLabel = publicLabels?.getOrNull(number - 1) ?: (30 + number).toString()
            )
        } + EventControl(
            id = "control-beacon",
            raceId = RACE_ID,
            label = "Beacon",
            siCode = 99,
            type = ControlPointType.BEACON,
            publicLabel = "Beacon"
        )
        val category = EventCategory(
            id = CATEGORY_ID,
            raceId = RACE_ID,
            name = "M21",
            isMan = true,
            maxAge = 21,
            lengthMeters = 0,
            climbMeters = 0,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )
        val controlPoints = controls.mapIndexed { index, control ->
            EventControlPoint(
                id = "cp-${control.id}",
                categoryId = CATEGORY_ID,
                siCode = control.siCode,
                type = control.type,
                order = index + 1,
                controlId = control.id
            )
        }
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = RACE_ID,
                    name = "Test Event",
                    apiKey = "",
                    startDateTimeIso = "2026-06-06T09:00:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7_200
                ),
                categories = listOf(
                    EventCategoryData(
                        category = category,
                        controlPoints = controlPoints,
                        competitors = emptyList()
                    )
                ),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList(),
                controls = controls
            )
        )
    }

    private fun protectedInfo(foxCount: Int): ProtectedCourseInfo {
        val routeLongitudes = (0..(foxCount + 1)).map { it * 0.01 }
        val route = routeLongitudes.mapIndexed { index, longitude ->
            ProtectedCourseRoutePoint(
                latitude = 39.0,
                longitude = -95.0 + longitude,
                elevationMeters = 100.0 + index * 10.0
            )
        }
        val controls = (1..foxCount).map { number ->
            ProtectedCourseControlPoint(
                controlId = "control-$number",
                label = (30 + number).toString(),
                latitude = 39.0,
                longitude = -95.0 + number * 0.01,
                type = ControlPointType.CONTROL,
                elevationMeters = 100.0 + number * 10.0
            )
        } + ProtectedCourseControlPoint(
            controlId = "control-beacon",
            label = "Beacon",
            latitude = 39.0,
            longitude = -95.0 + (foxCount + 1) * 0.01,
            type = ControlPointType.BEACON,
            elevationMeters = 100.0 + (foxCount + 1) * 10.0
        )
        val courseObjects = listOf(
            ProtectedCourseObjectPoint(
                id = "start",
                label = "Start",
                type = ProtectedCourseObjectType.START,
                latitude = route.first().latitude,
                longitude = route.first().longitude,
                elevationMeters = route.first().elevationMeters
            )
        ) + controls.map { control ->
            ProtectedCourseObjectPoint(
                id = control.controlId,
                label = control.label,
                type = if (control.type == ControlPointType.BEACON) {
                    ProtectedCourseObjectType.BEACON
                } else {
                    ProtectedCourseObjectType.CONTROL
                },
                latitude = control.latitude,
                longitude = control.longitude,
                elevationMeters = control.elevationMeters
            )
        } + ProtectedCourseObjectPoint(
            id = "finish",
            label = "Finish",
            type = ProtectedCourseObjectType.FINISH,
            latitude = route.last().latitude,
            longitude = route.last().longitude,
            elevationMeters = route.last().elevationMeters
        )
        return ProtectedCourseInfo(
            idealOrder = (1..foxCount).joinToString(" ") { (30 + it).toString() } + " Beacon",
            lengthMeters = 4_000,
            climbMeters = 100,
            sourceName = "test.kml",
            sampledPointCount = route.size,
            route = route,
            controlPoints = controls,
            courseObjects = courseObjects
        )
    }

    private companion object {
        const val RACE_ID = "race"
        const val CATEGORY_ID = "category-m21"
    }
}
