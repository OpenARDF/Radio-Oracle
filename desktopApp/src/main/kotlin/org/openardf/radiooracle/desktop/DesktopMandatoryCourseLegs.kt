package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType

internal data class MandatoryRouteWaypoint(
    val label: String,
    val point: CourseGeoPoint,
    val previousPoint: CourseGeoPoint,
    val nextPoint: CourseGeoPoint
)

/** The same imported leg constraints apply to course optimization and recorded punch orders. */
internal object DesktopMandatoryCourseLegs {
    fun from(info: ProtectedCourseInfo): List<MandatoryRouteWaypoint> {
        val order = info.appliedBindings?.orderedPlacementIds?.withIndex()?.associate { it.value to it.index }
        return from(if (order == null) info.courseObjects else info.courseObjects.sortedBy { order[it.id] ?: Int.MAX_VALUE })
    }

    fun from(objects: List<ProtectedCourseObjectPoint>): List<MandatoryRouteWaypoint> = buildList {
        var previous: ProtectedCourseObjectPoint? = null
        val pending = mutableListOf<ProtectedCourseObjectPoint>()
        objects.forEach { point ->
            if (point.type == ProtectedCourseObjectType.WAYPOINT) {
                pending += point
            } else {
                previous?.let { from ->
                    pending.forEach { add(MandatoryRouteWaypoint(it.label, it.geo(), from.geo(), point.geo())) }
                }
                pending.clear()
                previous = point
            }
        }
    }

    fun between(from: CourseGeoPoint, to: CourseGeoPoint, waypoints: List<MandatoryRouteWaypoint>): List<MandatoryRouteWaypoint> =
        waypoints.filter { it.previousPoint.matches(from) && it.nextPoint.matches(to) }.ifEmpty {
            waypoints.filter { it.previousPoint.matches(to) && it.nextPoint.matches(from) }.asReversed()
        }

    fun expand(endpoints: List<CourseGeoPoint>, waypoints: List<MandatoryRouteWaypoint>): List<CourseGeoPoint> = buildList {
        endpoints.firstOrNull()?.let(::add)
        endpoints.zipWithNext().forEach { (from, to) ->
            between(from, to, waypoints).forEach { add(it.point) }
            add(to)
        }
    }

    private fun ProtectedCourseObjectPoint.geo() = CourseGeoPoint(latitude, longitude, elevationMeters)
    private fun CourseGeoPoint.matches(other: CourseGeoPoint) = distanceMetersTo(other) <= 5.0
}
