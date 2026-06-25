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
