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

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class DesktopCourseRouteMapSourcePoint(
    val label: String,
    val point: CourseGeoPoint,
    val type: DesktopCourseRouteMapPointType
)

internal data class DesktopCourseProjectedRouteMapPoint(
    val source: DesktopCourseRouteMapSourcePoint,
    val xMeters: Double,
    val yMeters: Double
)

internal data class DesktopCourseRouteMapBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
) {
    val xRange: Double = max(0.001, maxX - minX)
    val yRange: Double = max(0.001, maxY - minY)

    fun xFraction(xMeters: Double): Double =
        (xMeters - minX) / xRange

    fun yFraction(yMeters: Double): Double =
        (maxY - yMeters) / yRange
}

internal object DesktopCourseRouteMapProjection {
    fun project(
        sourcePoints: List<DesktopCourseRouteMapSourcePoint>,
        magneticDeclinationDegrees: Double?
    ): List<DesktopCourseProjectedRouteMapPoint> {
        if (sourcePoints.isEmpty()) {
            return emptyList()
        }
        val centerLatitude = sourcePoints.map { it.point.latitude }.average()
        val centerLongitude = sourcePoints.map { it.point.longitude }.average()
        val latitudeMetersPerDegree = 111_320.0
        val longitudeMetersPerDegree = latitudeMetersPerDegree * max(0.000001, cos(Math.toRadians(centerLatitude)))
        val angleRadians = Math.toRadians(magneticDeclinationDegrees ?: 0.0)
        val cosAngle = cos(angleRadians)
        val sinAngle = sin(angleRadians)
        return sourcePoints.map { source ->
            val eastMeters = (source.point.longitude - centerLongitude) * longitudeMetersPerDegree
            val northMeters = (source.point.latitude - centerLatitude) * latitudeMetersPerDegree
            DesktopCourseProjectedRouteMapPoint(
                source = source,
                xMeters = eastMeters * cosAngle - northMeters * sinAngle,
                yMeters = eastMeters * sinAngle + northMeters * cosAngle
            )
        }
    }

    fun bounds(projectedPoints: List<DesktopCourseProjectedRouteMapPoint>): DesktopCourseRouteMapBounds =
        DesktopCourseRouteMapBounds(
            minX = projectedPoints.minOf { it.xMeters },
            maxX = projectedPoints.maxOf { it.xMeters },
            minY = projectedPoints.minOf { it.yMeters },
            maxY = projectedPoints.maxOf { it.yMeters }
        )
}
