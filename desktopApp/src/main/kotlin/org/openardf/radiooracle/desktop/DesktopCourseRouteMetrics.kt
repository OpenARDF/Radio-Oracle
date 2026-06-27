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

    fun climbMetersOrNull(route: List<CourseGeoPoint>): Double? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) -> max(0.0, requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters)) }
    }

    fun effectiveLengthMetersOrNull(route: List<CourseGeoPoint>): Double? =
        metrics(route).effectiveLengthMeters
}
