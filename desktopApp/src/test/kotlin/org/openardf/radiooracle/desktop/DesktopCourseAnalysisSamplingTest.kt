package org.openardf.radiooracle.desktop

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class DesktopCourseAnalysisSamplingTest {
    private val vertices = listOf(CourseGeoPoint(35.0, -78.0), CourseGeoPoint(35.00031, -77.99992),
        CourseGeoPoint(35.00051, -77.99961), CourseGeoPoint(35.00077, -77.99974))
    private val elevation: (CourseGeoPoint) -> Double? = { 100.0 + 8.0 * sin((it.latitude - 35.0) * 50000.0) }

    private fun oldSamples() = buildList {
        add(vertices.first().copy(elevationMeters = elevation(vertices.first())))
        vertices.zipWithNext().forEach { (a, b) ->
            val count = max(1, (a.distanceMetersTo(b) / 25.0).roundToInt())
            (1..count).forEach { i -> val p = a.interpolate(b, i.toDouble() / count); add(p.copy(elevationMeters = elevation(p))) }
        }
    }

    @Test fun oldImportedSamplesAndCalculatedVerticesProduceIdenticalProfiles() {
        val old = oldSamples()
        val sampling = DesktopCourseAnalysisSampling(old, elevation)
        val applied = sampling.appliedRoute(vertices)
        val calculated = DesktopCourseRouteSampler.sampledStraightRoutePoints(vertices, sampling::elevation)
        assertNotEquals(old, applied)
        assertEquals(calculated, applied)
        assertEquals(DesktopCourseRouteMetricsCalculator.metrics(calculated), DesktopCourseRouteMetricsCalculator.metrics(applied))
    }

    @Test fun missingCacheUsesTheSameStoredElevationFallbackForBothRoutes() {
        val sampling = DesktopCourseAnalysisSampling(oldSamples()) { null }
        val applied = sampling.appliedRoute(vertices)
        assertEquals(applied, DesktopCourseRouteSampler.sampledStraightRoutePoints(vertices, sampling::elevation))
        assertTrue(applied.all { it.elevationMeters != null })
    }

    @Test fun aStoredDetourCannotBeReplacedByAnUnrelatedStraightLeg() {
        val sampling = DesktopCourseAnalysisSampling(oldSamples(), elevation)
        val direct = listOf(vertices.first(), vertices.last())
        assertFalse(DesktopCourseAnalysisSampling.followsVertices(oldSamples(), direct))
        val applied = sampling.appliedRoute(direct)
        assertTrue(applied.any { it.distanceMetersTo(vertices[1]) < 0.001 })
        assertTrue(applied.any { it.distanceMetersTo(vertices[2]) < 0.001 })
        assertFalse(DesktopCourseAnalysisSampling.followsVertices(applied, direct))
    }

    @Test fun repeatedVisitsAndBacktrackingAreNotCollapsedIntoOneLeg() {
        val loop = listOf(vertices[0], vertices[1], vertices[0], vertices[2])
        assertTrue(DesktopCourseAnalysisSampling.followsVertices(loop, loop))
        assertFalse(DesktopCourseAnalysisSampling.followsVertices(loop, listOf(vertices[0], vertices[2])))
    }
}
