package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.*
import java.nio.file.Path

internal data class CourseStationChoice(
    val categoryId: String, val categoryName: String, val placementId: String, val label: String,
    val role: org.openardf.radiooracle.shared.domain.ControlPointType, val controlId: String?
) {
    val key get() = categoryId to placementId
}

/** Reuse explicit or immutable-identity bindings; labels and coordinates never guess station identity. */
internal fun courseStationChoices(project: EventProjectFile?, infos: Map<String, ProtectedCourseInfo>): List<CourseStationChoice> {
    val categories = (project?.raceData?.categories.orEmpty() + project?.raceData?.courseMappings.orEmpty()).associateBy { it.category.id }
    return infos.flatMap { (categoryId, info) -> info.validatedPlacements().values.mapNotNull { point ->
        val role = point.type.controlRole() ?: return@mapNotNull null
        val explicit = info.appliedBindings?.controls?.singleOrNull { it.placementId == point.id }?.controlId
        val station = project?.raceData?.controls?.singleOrNull { it.id == (explicit ?: point.id) && it.type == role }
        CourseStationChoice(categoryId, categories[categoryId]?.category?.name.orEmpty(), point.id, point.label, role, station?.id)
    } }
}

/** Both the on-screen diagram and KML use the same labeled locations and complete route geometry. */
internal fun courseStationPreviewFolders(
    project: EventProjectFile, infos: Map<String, ProtectedCourseInfo>,
    bindings: Map<Pair<String, String>, String>, application: DesktopCourseCalculatedRouteApplication
): List<DesktopCourseKmlExportFolder> {
    val categories = (project.raceData.categories + project.raceData.courseMappings).associateBy { it.category.id }
    val controls = project.raceData.controls.associateBy { it.id }
    val acceptedLabels = application.foxAssignments.mapNotNull { assignment ->
        bindings[application.categoryId to assignment.controlId]?.takeIf(String::isNotBlank)?.let { it to assignment.calculatedLabel }
    }.toMap()
    return infos.map { (id, info) ->
        val name = categories.getValue(id).category.name
        val points = info.validatedPlacements().values.map { point ->
            val control = bindings[id to point.id]?.let(controls::get)
            val proposedLabel = acceptedLabels[control?.id] ?: point.label
            val label = if (proposedLabel == point.label) point.label else "${point.label} → $proposedLabel"
            DesktopCourseKmlExportPoint(
                label = if (point.type.controlRole() == null) label else "$label (${control?.let { "SI ${it.siCode}" } ?: "station unassigned"})",
                originalLabel = control?.let { it.publicLabel ?: it.label },
                point = CourseGeoPoint(point.latitude, point.longitude, point.elevationMeters),
                type = DesktopCourseKmlExportPointType.valueOf(point.type.name), siCode = control?.siCode,
                description = "Course: $name\nDraft location: ${point.label}")
        }
        DesktopCourseKmlExportFolder("$name — course locations", "Draft route", listOf(name), emptyList(),
            info.lengthMeters, info.climbMeters, null,
            info.route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) }, emptyList(), points)
    }
}

internal fun courseStationPreviewMap(folder: DesktopCourseKmlExportFolder): DesktopCourseRouteMap {
    val data = DesktopCourseKmlData(
        folder.courseObjects.map { CourseControlPoint(it.label, it.point) },
        listOf(CourseRoute("Draft route", folder.routePoints, lineStyle = CourseLineStyle(0xff000000, 2.0))))
    val map = DesktopCourseGraphic.routeMap(Path.of("Course locations.kml"), data)
    return map.copy(title = folder.title, points = map.points.mapIndexed { index, point ->
        point.copy(type = when (folder.courseObjects[index].type) {
            DesktopCourseKmlExportPointType.START -> DesktopCourseRouteMapPointType.Start
            DesktopCourseKmlExportPointType.FINISH -> DesktopCourseRouteMapPointType.Finish
            DesktopCourseKmlExportPointType.CONTROL -> DesktopCourseRouteMapPointType.Control
            DesktopCourseKmlExportPointType.BEACON -> DesktopCourseRouteMapPointType.Beacon
            DesktopCourseKmlExportPointType.SPECTATOR -> DesktopCourseRouteMapPointType.Spectator
            DesktopCourseKmlExportPointType.WAYPOINT -> DesktopCourseRouteMapPointType.Waypoint
        })
    }.filterNot { it.type == DesktopCourseRouteMapPointType.Waypoint })
}
