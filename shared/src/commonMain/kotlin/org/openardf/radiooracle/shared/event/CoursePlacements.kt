package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType

/** Geometry has one identity even when older payloads contain both marker representations. */
internal fun ProtectedCourseInfo.placements(): List<ProtectedCourseObjectPoint> = courseObjects + controlPoints.map {
    ProtectedCourseObjectPoint(it.controlId, it.label, when (it.type) {
        ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
        ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
        ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
    }, it.latitude, it.longitude, it.elevationMeters, it.speedFactor, it.description)
}

fun ProtectedCourseObjectType.controlRole(): ControlPointType? = when (this) {
    ProtectedCourseObjectType.CONTROL -> ControlPointType.CONTROL
    ProtectedCourseObjectType.BEACON -> ControlPointType.BEACON
    ProtectedCourseObjectType.SPECTATOR -> ControlPointType.SEPARATOR
    else -> null
}

internal fun CourseControlLocation.isValid(): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
    longitude.isFinite() && longitude in -180.0..180.0 && (elevationMeters == null || elevationMeters.isFinite())

fun ProtectedCourseInfo.validatedPlacements(): Map<String, ProtectedCourseObjectPoint> =
    placements().groupBy { it.id }.mapValues { (_, representations) ->
        val point = representations.first()
        require(point.id.isNotBlank()) { "Course placement ID is missing." }
        require(representations.all { CourseControlLocation(it.latitude, it.longitude, it.elevationMeters).isValid() }) {
            "Invalid course coordinates for ${point.label}."
        }
        require(representations.map { Triple(it.type, it.latitude, it.longitude) }.distinct().size == 1 &&
            representations.mapNotNull { it.elevationMeters }.distinct().size <= 1 &&
            representations.mapNotNull { it.speedFactor }.distinct().size <= 1) {
            "Conflicting course placement records for ${point.label}."
        }
        require(representations.all { it.speedFactor == null || (it.speedFactor.isFinite() && it.speedFactor > 0) }) {
            "Invalid speed factor for ${point.label}."
        }
        point.copy(elevationMeters = representations.mapNotNull { it.elevationMeters }.firstOrNull(),
            speedFactor = representations.mapNotNull { it.speedFactor }.firstOrNull())
    }
