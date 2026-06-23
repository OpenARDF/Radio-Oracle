package org.openardf.radiooracle.desktop

import kotlin.math.ceil
import kotlin.math.max

internal object DesktopCourseRouteSampler {
    const val DefaultStraightRouteSampleMeters: Double = 25.0

    fun sampledStraightRoutePoints(
        routePoints: List<CourseGeoPoint>,
        elevationLookup: (CourseGeoPoint) -> Double?,
        sampleMeters: Double = DefaultStraightRouteSampleMeters,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>? = null
    ): List<CourseGeoPoint> {
        if (routePoints.size < 2) {
            return routePoints.map { it.withCachedElevation(elevationLookup) }
        }
        val sampled = mutableListOf<CourseGeoPoint>()
        routePoints.zipWithNext().forEach { (from, to) ->
            val legPoints = legSampleCache?.getOrPut(from to to) {
                sampledStraightLegPoints(from, to, elevationLookup, sampleMeters)
            } ?: sampledStraightLegPoints(from, to, elevationLookup, sampleMeters)
            if (sampled.isEmpty()) {
                sampled += legPoints
            } else {
                sampled += legPoints.drop(1)
            }
        }
        return sampled
    }

    fun sampledStraightLegPoints(
        start: CourseGeoPoint,
        end: CourseGeoPoint,
        elevationLookup: (CourseGeoPoint) -> Double?,
        sampleMeters: Double = DefaultStraightRouteSampleMeters
    ): List<CourseGeoPoint> {
        val intervals = max(
            1,
            ceil(start.distanceMetersTo(end) / sampleMeters).toInt()
        )
        return (0..intervals).map { index ->
            start.interpolate(end, index.toDouble() / intervals.toDouble())
                .withCachedElevation(elevationLookup)
        }
    }

    private fun CourseGeoPoint.withCachedElevation(elevationLookup: (CourseGeoPoint) -> Double?): CourseGeoPoint =
        copy(elevationMeters = elevationLookup(this) ?: elevationMeters)
}
