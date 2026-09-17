package org.openardf.radiooracle.shared.files

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType

/** Explicit import option: these Condes control numbers represent route geometry, never punches. */
fun IofCourseDataPreview.withCondesRouteBends(): IofCourseDataPreview = copy(categories = categories.map { data ->
    val points = data.controlPoints.filter { it.siCode in 900..999 }
    val bends = points.associateBy { it.controlId }
    if (bends.isEmpty()) return@map data
    require(points.size == bends.size) { "A 900–999 route point is visited more than once in ${data.category.name}. Use a different point number for each bend." }
    require(bends.values.all { it.type == ControlPointType.CONTROL }) {
        "A 900–999 point has a Beacon or Spectator role. Leave route points off or correct the XML."
    }
    val info = requireNotNull(data.category.courseInfo) { "Route point coordinates are missing for ${data.category.name}." }
    require(bends.keys.all { id -> info.courseObjects.any { it.id == id } }) {
        "A 900–999 route point has no coordinates in ${data.category.name}. Correct the XML before importing route points."
    }
    // A waypoint must not share the catalog identity of a previously imported SI station.
    val bendIds = bends.keys.associateWith { "iof-route-bend-$it" }
    val objects = info.courseObjects.map { point -> bends[point.id]?.let {
        point.copy(id = bendIds.getValue(point.id), type = ProtectedCourseObjectType.WAYPOINT, label = "Route point ${it.siCode}")
    } ?: point }
    require(objects.firstOrNull()?.type != ProtectedCourseObjectType.WAYPOINT && objects.lastOrNull()?.type != ProtectedCourseObjectType.WAYPOINT) {
        "Route points must lie between course controls, Start or Finish in ${data.category.name}."
    }
    data.copy(category = data.category.copy(courseInfo = info.copy(courseObjects = objects,
        controlPoints = info.controlPoints.filterNot { it.controlId in bends },
        suppliedLegLengths = info.suppliedLegLengths.map { it.copy(
            fromId = bendIds[it.fromId] ?: it.fromId, toId = bendIds[it.toId] ?: it.toId) })),
        controlPoints = data.controlPoints.filterNot { it.controlId in bends }.mapIndexed { index, point -> point.copy(order = index + 1) },
        publicControlIds = data.publicControlIds.filterNot { it in bends })
})
