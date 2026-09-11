package org.openardf.radiooracle.desktop

/** One elevation surface and one sampler for both the stored course and route candidates. */
internal class DesktopCourseAnalysisSampling(
    private val storedRoute: List<CourseGeoPoint>,
    private val cachedElevation: (CourseGeoPoint) -> Double?
) {
    private val elevations = mutableMapOf<Pair<Double, Double>, Double?>()

    fun elevation(point: CourseGeoPoint): Double? {
        val key = point.latitude to point.longitude
        if (key !in elevations) {
            elevations[key] = cachedElevation(point)?.takeIf(Double::isFinite) ?: storedElevation(point)
        }
        return elevations[key]
    }

    fun appliedRoute(vertices: List<CourseGeoPoint>): List<CourseGeoPoint> {
        // Remove only old synthetic samples. Never straighten a stored bend or remove a detour
        // that is absent from the course-object list.
        val geometry = if (followsVertices(storedRoute, vertices)) vertices else storedRoute
        return DesktopCourseRouteSampler.sampledStraightRoutePoints(geometry, ::elevation)
    }

    private fun storedElevation(point: CourseGeoPoint): Double? {
        storedRoute.firstOrNull { it.distanceMetersTo(point) <= 0.001 }?.elevationMeters?.let { return it }
        for ((from, to) in storedRoute.zipWithNext()) {
            val fraction = fractionOnSegment(point, from, to) ?: continue
            val a = from.elevationMeters ?: continue
            val b = to.elevationMeters ?: continue
            return a + (b - a) * fraction
        }
        return null
    }

    companion object {
        /** Checks ordered vertices and every intervening sample, including repeated visits. */
        fun followsVertices(route: List<CourseGeoPoint>, vertices: List<CourseGeoPoint>): Boolean {
            // A beacon and finish may share one location; the sampler retains both endpoints.
            val path = route.fold(mutableListOf<CourseGeoPoint>()) { points, point ->
                if (points.lastOrNull()?.distanceMetersTo(point)?.let { it <= 0.001 } != true) points += point
                points
            }
            if (route.size < 2 || vertices.size < 2 || route.first().distanceMetersTo(vertices.first()) > 0.01) return false
            var cursor = 0
            for ((from, to) in vertices.zipWithNext()) {
                var previousFraction = 0.0
                if (from.distanceMetersTo(to) <= 0.001) continue
                var reached = false
                while (cursor < path.lastIndex) {
                    val point = path[++cursor]
                    val fraction = fractionOnSegment(point, from, to) ?: return false
                    if (fraction + 1e-8 < previousFraction) return false
                    previousFraction = fraction
                    if (point.distanceMetersTo(to) <= 0.01) { reached = true; break }
                }
                if (!reached) return false
            }
            return cursor == path.lastIndex
        }

        private fun fractionOnSegment(point: CourseGeoPoint, from: CourseGeoPoint, to: CourseGeoPoint): Double? {
            val lat = to.latitude - from.latitude
            val lon = to.longitude - from.longitude
            val squared = lat * lat + lon * lon
            if (squared == 0.0) return if (point.distanceMetersTo(from) <= 0.001) 0.0 else null
            val fraction = ((point.latitude - from.latitude) * lat + (point.longitude - from.longitude) * lon) / squared
            if (fraction < -1e-8 || fraction > 1.0 + 1e-8) return null
            val bounded = fraction.coerceIn(0.0, 1.0)
            return bounded.takeIf { point.distanceMetersTo(from.interpolate(to, it)) <= 0.01 }
        }
    }
}
