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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCourseRuleCatalog

class DesktopCourseSharedRulesTest {
    @Test
    fun ruleCatalogResolvesClassicYouthAdultFoxoringAndSprintRequirements() {
        assertEquals("W65", EventCourseRuleCatalog.categoryRuleKey("Women W65"))
        assertEquals("W45", EventCourseRuleCatalog.categoryRuleKey("D45"))
        assertNull(EventCourseRuleCatalog.categoryRuleKey("Open"))

        val youthClassic = EventCourseRuleCatalog.categoryRequirement("W14", RaceType.CLASSIC)
        assertEquals(4, youthClassic?.minControls)
        assertEquals(4, youthClassic?.maxControls)
        assertEquals("2.5-3 km", youthClassic?.lengthRangeText())

        val adultClassic = EventCourseRuleCatalog.categoryRequirement("M50", RaceType.CLASSIC)
        assertEquals(4, adultClassic?.minControls)
        assertEquals(5, adultClassic?.maxControls)
        assertEquals("6-8 km", adultClassic?.lengthRangeText())

        val foxoring = EventCourseRuleCatalog.categoryRequirement("W21", RaceType.FOXORING)
        assertEquals(6, foxoring?.minControls)
        assertEquals(10, foxoring?.maxControls)
        assertEquals("5-7 km", foxoring?.lengthRangeText())

        val sprint = EventCourseRuleCatalog.categoryRequirement("M21", RaceType.SPRINT)
        assertEquals(10, sprint?.minControls)
        assertEquals(10, sprint?.maxControls)
        assertEquals("9-12 km", sprint?.lengthRangeText())
    }

    @Test
    fun ruleCatalogProvidesSpacingAndClimbPolicy() {
        val youthClassic = EventCourseRuleCatalog.spacingRuleSet(RaceType.CLASSIC, "W14")
        assertEquals("Youth Classic", youthClassic?.formatLabel)
        assertEquals(500, youthClassic?.startMinMeters)
        assertEquals(400, youthClassic?.pairMinMeters)
        assertTrue(youthClassic?.includeBeaconInStartCheck == true)
        assertFalse(youthClassic?.includeSpectatorInPairCheck == true)

        val classic = EventCourseRuleCatalog.spacingRuleSet(RaceType.CLASSIC, "M21")
        assertEquals("Classic", classic?.formatLabel)
        assertEquals(750, classic?.startMinMeters)
        assertEquals(400, classic?.pairMinMeters)

        val sprint = EventCourseRuleCatalog.spacingRuleSet(RaceType.SPRINT, "M21")
        assertEquals("Sprint", sprint?.formatLabel)
        assertEquals(100, sprint?.startMinMeters)
        assertFalse(sprint?.includeBeaconInStartCheck == true)
        assertTrue(sprint?.includeSpectatorInPairCheck == true)

        val foxoring = EventCourseRuleCatalog.spacingRuleSet(RaceType.FOXORING, "M21")
        assertEquals("Foxoring", foxoring?.formatLabel)
        assertEquals(250, foxoring?.startMinMeters)
        assertFalse(foxoring?.includeSpectatorInPairCheck == true)

        assertTrue(EventCourseRuleCatalog.hasClimbLimit(RaceType.CLASSIC))
        assertTrue(EventCourseRuleCatalog.hasClimbLimit(RaceType.SPRINT))
        assertFalse(EventCourseRuleCatalog.hasClimbLimit(RaceType.ORIENTEERING))
    }

    @Test
    fun routeMetricsUseProfileClimbAndFallBackToHorizontalComparisonWhenElevationIsIncomplete() {
        val route = listOf(
            CourseGeoPoint(latitude = 0.0, longitude = 0.0, elevationMeters = 100.0),
            CourseGeoPoint(latitude = 0.0, longitude = 0.001, elevationMeters = 130.0),
            CourseGeoPoint(latitude = 0.0, longitude = 0.002, elevationMeters = 120.0),
            CourseGeoPoint(latitude = 0.0, longitude = 0.003, elevationMeters = 150.0)
        )

        val metrics = DesktopCourseRouteMetricsCalculator.metrics(route)

        assertEquals(route.zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }, metrics.horizontalLengthMeters, 0.001)
        assertEquals(60.0, metrics.climbMeters ?: -1.0, 0.001)
        assertEquals(metrics.horizontalLengthMeters + 600.0, metrics.effectiveLengthMeters ?: -1.0, 0.001)
        assertEquals(metrics.effectiveLengthMeters ?: -1.0, metrics.comparisonLengthMeters, 0.001)

        val incomplete = route.mapIndexed { index, point ->
            if (index == 2) point.copy(elevationMeters = null) else point
        }
        val incompleteMetrics = DesktopCourseRouteMetricsCalculator.metrics(incomplete)
        assertNull(incompleteMetrics.climbMeters)
        assertNull(incompleteMetrics.effectiveLengthMeters)
        assertEquals(incompleteMetrics.horizontalLengthMeters, incompleteMetrics.comparisonLengthMeters, 0.001)
    }

    @Test
    fun routeMetricsIgnoreSmallElevationWigglesButKeepSustainedClimb() {
        val noisyFlatRoute = listOf(
            CourseGeoPoint(latitude = 0.0, longitude = 0.0, elevationMeters = 100.0),
            CourseGeoPoint(latitude = 0.0, longitude = 0.001, elevationMeters = 100.6),
            CourseGeoPoint(latitude = 0.0, longitude = 0.002, elevationMeters = 100.1),
            CourseGeoPoint(latitude = 0.0, longitude = 0.003, elevationMeters = 100.8),
            CourseGeoPoint(latitude = 0.0, longitude = 0.004, elevationMeters = 100.2)
        )

        assertEquals(0.0, DesktopCourseRouteMetricsCalculator.climbMetersOrNull(noisyFlatRoute) ?: -1.0, 0.001)

        val sustainedClimbRoute = noisyFlatRoute + CourseGeoPoint(
            latitude = 0.0,
            longitude = 0.005,
            elevationMeters = 104.0
        )

        assertEquals(4.0, DesktopCourseRouteMetricsCalculator.climbMetersOrNull(sustainedClimbRoute) ?: -1.0, 0.001)
        assertEquals(5.1, DesktopCourseRouteMetricsCalculator.rawPositiveClimbMetersOrNull(sustainedClimbRoute) ?: -1.0, 0.001)
    }

    @Test
    fun routeMetricsHandleDenseElevationProfiles() {
        val denseRoute = (0 until 5_000).map { index ->
            CourseGeoPoint(
                latitude = 35.0,
                longitude = -80.0 + index * 0.00001,
                elevationMeters = 100.0 + (index % 20)
            )
        }

        val metrics = DesktopCourseRouteMetricsCalculator.metrics(denseRoute)

        assertTrue(metrics.horizontalLengthMeters > 0.0)
        assertTrue((metrics.climbMeters ?: -1.0) >= 0.0)
        assertTrue((metrics.effectiveLengthMeters ?: -1.0) >= metrics.horizontalLengthMeters)
    }
}
