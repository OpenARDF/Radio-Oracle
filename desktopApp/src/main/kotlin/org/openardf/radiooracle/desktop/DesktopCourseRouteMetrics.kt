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

import kotlin.math.max

data class DesktopCourseRouteMetrics(
    val horizontalLengthMeters: Double,
    val climbMeters: Double?,
    val effectiveLengthMeters: Double?
) {
    val comparisonLengthMeters: Double
        get() = effectiveLengthMeters ?: horizontalLengthMeters
}

object DesktopCourseRouteMetricsCalculator {
    const val DefaultElevationSmoothingWindowMeters: Double = 50.0
    const val DefaultClimbProminenceMeters: Double = 2.0

    fun metrics(route: List<CourseGeoPoint>): DesktopCourseRouteMetrics {
        val horizontalLengthMeters = horizontalLengthMeters(route)
        val climbMeters = climbMetersOrNull(route)
        return DesktopCourseRouteMetrics(
            horizontalLengthMeters = horizontalLengthMeters,
            climbMeters = climbMeters,
            effectiveLengthMeters = climbMeters?.let { horizontalLengthMeters + 10.0 * it }
        )
    }

    fun horizontalLengthMeters(route: List<CourseGeoPoint>): Double =
        route.zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    fun climbMetersOrNull(route: List<CourseGeoPoint>): Double? =
        profileClimbMetersOrNull(route)

    fun profileClimbMetersOrNull(
        route: List<CourseGeoPoint>,
        smoothingWindowMeters: Double = DefaultElevationSmoothingWindowMeters,
        climbProminenceMeters: Double = DefaultClimbProminenceMeters
    ): Double? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        val distances = cumulativeDistances(route)
        val elevations = smoothedElevations(route, distances, smoothingWindowMeters)
        return prominenceFilteredPositiveClimb(elevations, climbProminenceMeters)
    }

    fun rawPositiveClimbMetersOrNull(route: List<CourseGeoPoint>): Double? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) ->
                max(0.0, requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters))
            }
    }

    fun thresholdedPositiveClimbMetersOrNull(
        route: List<CourseGeoPoint>,
        thresholdMeters: Double
    ): Double? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        val threshold = thresholdMeters.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        return route.zipWithNext()
            .sumOf { (start, end) ->
                val gain = requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters)
                if (gain > threshold) gain else 0.0
            }
    }

    fun effectiveLengthMetersOrNull(route: List<CourseGeoPoint>): Double? =
        metrics(route).effectiveLengthMeters

    private fun cumulativeDistances(route: List<CourseGeoPoint>): List<Double> {
        var total = 0.0
        return buildList(route.size) {
            add(0.0)
            route.zipWithNext().forEach { (start, end) ->
                total += start.distanceMetersTo(end)
                add(total)
            }
        }
    }

    private fun smoothedElevations(
        route: List<CourseGeoPoint>,
        distances: List<Double>,
        smoothingWindowMeters: Double
    ): List<Double> {
        if (!smoothingWindowMeters.isFinite() || smoothingWindowMeters <= 0.0) {
            return route.map { requireNotNull(it.elevationMeters) }
        }
        val radiusMeters = smoothingWindowMeters / 2.0
        val sortedWindowElevations = mutableListOf<Double>()
        var windowStartIndex = 0
        var windowEndExclusive = 0
        return route.indices.map { index ->
            val center = distances[index]
            while (windowEndExclusive < route.size && distances[windowEndExclusive] - center <= radiusMeters) {
                sortedWindowElevations.insertSorted(requireNotNull(route[windowEndExclusive].elevationMeters))
                windowEndExclusive += 1
            }
            while (windowStartIndex < route.size && center - distances[windowStartIndex] > radiusMeters) {
                sortedWindowElevations.removeSorted(requireNotNull(route[windowStartIndex].elevationMeters))
                windowStartIndex += 1
            }
            median(sortedWindowElevations)
        }
    }

    private fun MutableList<Double>.insertSorted(value: Double) {
        val insertionPoint = binarySearch(value).let { if (it >= 0) it else -it - 1 }
        add(insertionPoint, value)
    }

    private fun MutableList<Double>.removeSorted(value: Double) {
        val matchIndex = binarySearch(value)
        if (matchIndex >= 0) {
            removeAt(matchIndex)
        }
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate a median without values." }
        val middle = values.size / 2
        return if (values.size % 2 == 1) {
            values[middle]
        } else {
            (values[middle - 1] + values[middle]) / 2.0
        }
    }

    private fun prominenceFilteredPositiveClimb(elevations: List<Double>, prominenceMeters: Double): Double {
        if (elevations.size < 2) {
            return 0.0
        }
        val threshold = prominenceMeters.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        if (threshold == 0.0) {
            return elevations.zipWithNext().sumOf { (start, end) -> max(0.0, end - start) }
        }

        var referenceLow = elevations.first()
        var candidateHigh = elevations.first()
        var candidateLow = elevations.first()
        var climbing = false
        var total = 0.0

        elevations.drop(1).forEach { elevation ->
            if (climbing) {
                if (elevation > candidateHigh) {
                    candidateHigh = elevation
                } else if (candidateHigh - elevation >= threshold) {
                    total += candidateHigh - referenceLow
                    candidateLow = elevation
                    referenceLow = elevation
                    climbing = false
                }
            } else {
                if (elevation < candidateLow) {
                    candidateLow = elevation
                    referenceLow = elevation
                } else if (elevation - candidateLow >= threshold) {
                    referenceLow = candidateLow
                    candidateHigh = elevation
                    climbing = true
                }
            }
        }

        if (climbing) {
            total += candidateHigh - referenceLow
        }
        return total
    }
}
