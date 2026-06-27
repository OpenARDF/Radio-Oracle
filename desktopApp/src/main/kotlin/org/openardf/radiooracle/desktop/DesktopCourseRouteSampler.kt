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
