package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import kotlin.math.abs

/** One portable definition of valid decimal-degree course coordinates. */
object CourseCoordinateRules {
    fun isValidLatitude(value: Double): Boolean = value.isFinite() && value in -90.0..90.0

    fun isValidLongitude(value: Double): Boolean = value.isFinite() && value in -180.0..180.0

    fun latitudeOrNull(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf(::isValidLatitude)

    fun longitudeOrNull(text: String): Double? =
        text.trim().toDoubleOrNull()?.takeIf(::isValidLongitude)

    fun requireLatitude(text: String): Double {
        val value = text.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Latitude must be a number.")
        require(isValidLatitude(value)) { "Latitude must be between -90 and 90." }
        return value
    }

    fun requireLongitude(text: String): Double {
        val value = text.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Longitude must be a number.")
        require(isValidLongitude(value)) { "Longitude must be between -180 and 180." }
        return value
    }

    fun same(first: Double, second: Double): Boolean = abs(first - second) < 0.0000001
}

data class CourseControlLocationUpdate(
    val controlId: String,
    val latitude: Double,
    val longitude: Double
)

data class CourseControlLocationMutation(
    val controls: List<EventControl>,
    val courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
    val affectedCategoryIds: List<String>
)

data class CourseControlLocationSummary(
    val controlId: String,
    val label: String,
    val latitude: Double?,
    val longitude: Double?,
    val affectedCategoryCount: Int
)

data class CourseControlLocationCourseChange(
    val categoryId: String,
    val categoryName: String,
    val usesMovedControl: Boolean,
    val previousIdealOrder: String,
    val updatedIdealOrder: String,
    val previousHorizontalLengthMeters: Int?,
    val updatedHorizontalLengthMeters: Int?,
    val previousClimbMeters: Int?,
    val updatedClimbMeters: Int?,
    val previousEffectiveLengthMeters: Int?,
    val updatedEffectiveLengthMeters: Int?
)

/** One validated Controls-row edit that can be reviewed identically on every platform. */
data class CourseControlEditRequest(
    val controlId: String,
    val label: String,
    val siCode: Int,
    val type: ControlPointType,
    val scored: Boolean,
    val publicLabel: String,
    val notes: String,
    val location: CourseControlLocation? = null
)

/** A user-visible before/after value for the mandatory control-edit review. */
data class CourseControlFieldChange(
    val field: String,
    val previousValue: String,
    val updatedValue: String
)

/** Portable control-row comparison and catalog mutation shared by platform UIs. */
object CourseControlEdits {
    fun changes(
        control: EventControl,
        currentLocation: CourseControlLocation?,
        edit: CourseControlEditRequest
    ): List<CourseControlFieldChange> {
        require(control.id == edit.controlId) { "Control edit does not match the selected control." }
        edit.location?.let { location ->
            require(CourseCoordinateRules.isValidLatitude(location.latitude)) {
                "Latitude must be between -90 and 90."
            }
            require(CourseCoordinateRules.isValidLongitude(location.longitude)) {
                "Longitude must be between -180 and 180."
            }
        }
        return buildList {
            addChange("SI code", control.siCode.toString(), edit.siCode.toString())
            addChange("Role", control.type.name, edit.type.name)
            addChange("Public label", control.publicLabel.orEmpty().trim(), edit.publicLabel.trim())
            addChange("Notes", control.notes.orEmpty().trim(), edit.notes.trim())
            edit.location?.let { location ->
                addCoordinateChange("Latitude", currentLocation?.latitude, location.latitude)
                addCoordinateChange("Longitude", currentLocation?.longitude, location.longitude)
            }
        }
    }

    fun applyDetails(projectFile: EventProjectFile, edit: CourseControlEditRequest): EventProjectFile =
        EventProjectEditor.updateControl(
            projectFile = projectFile,
            controlId = edit.controlId,
            label = edit.label,
            siCode = edit.siCode.toString(),
            type = edit.type,
            scored = edit.scored,
            publicLabel = edit.publicLabel,
            notes = edit.notes
        )

    private fun MutableList<CourseControlFieldChange>.addChange(
        field: String,
        previousValue: String,
        updatedValue: String
    ) {
        if (previousValue != updatedValue) {
            add(CourseControlFieldChange(field, previousValue.ifBlank { "Blank" }, updatedValue.ifBlank { "Blank" }))
        }
    }

    private fun MutableList<CourseControlFieldChange>.addCoordinateChange(
        field: String,
        previousValue: Double?,
        updatedValue: Double
    ) {
        if (previousValue == null || !CourseCoordinateRules.same(previousValue, updatedValue)) {
            add(
                CourseControlFieldChange(
                    field = field,
                    previousValue = previousValue?.toString() ?: "Unavailable",
                    updatedValue = updatedValue.toString()
                )
            )
        }
    }
}

/** Portable course-location mutation and review projections used by every platform. */
object CourseControlLocationEdits {
    fun apply(
        controls: List<EventControl>,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        updates: List<CourseControlLocationUpdate>,
        elevationLookup: (CourseControlLocation) -> Double? = { null }
    ): CourseControlLocationMutation {
        require(updates.groupBy { it.controlId }.values.all { it.distinct().size == 1 }) {
            "Conflicting locations were supplied for the same control."
        }
        val uniqueUpdates = updates.distinctBy { it.controlId }
        require(uniqueUpdates.isNotEmpty()) { "No control location updates were provided." }
        uniqueUpdates.forEach { update ->
            require(CourseCoordinateRules.isValidLatitude(update.latitude)) {
                "Latitude must be between -90 and 90."
            }
            require(CourseCoordinateRules.isValidLongitude(update.longitude)) {
                "Longitude must be between -180 and 180."
            }
        }
        val controlsById = controls.associateBy { it.id }
        val missingControlId = uniqueUpdates.firstOrNull { it.controlId !in controlsById }?.controlId
        require(missingControlId == null) { "Control was not found: $missingControlId" }

        val updatesByControlId = uniqueUpdates.associateBy { it.controlId }
        val elevationsByControlId = updatesByControlId.mapValues { (_, update) ->
            elevationLookup(CourseControlLocation(update.latitude, update.longitude))
        }
        val updatedInfos = courseInfoByCategoryId.toMutableMap()
        val affectedCategoryIds = linkedSetOf<String>()
        courseInfoByCategoryId.forEach { (categoryId, courseInfo) ->
            val placementUpdates = updatesByControlId + courseInfo.appliedBindings?.controls.orEmpty().mapNotNull { binding ->
                updatesByControlId[binding.controlId]?.let { binding.placementId to it }
            }.toMap()
            val hasMovedPoint = courseInfo.controlPoints.any { point ->
                placementUpdates[point.controlId]?.let { update ->
                    !CourseCoordinateRules.same(point.latitude, update.latitude) ||
                        !CourseCoordinateRules.same(point.longitude, update.longitude)
                } == true
            } || courseInfo.courseObjects.any { point ->
                placementUpdates[point.id]?.let { update ->
                    !CourseCoordinateRules.same(point.latitude, update.latitude) ||
                        !CourseCoordinateRules.same(point.longitude, update.longitude)
                } == true
            }
            if (!hasMovedPoint) return@forEach

            val movedInfo = courseInfo.copy(
                idealOrder = "",
                lengthMeters = null,
                climbMeters = null,
                sourceName = if (courseInfo.sourceName.startsWith("Course Analyzer", ignoreCase = true)) {
                    "Course Analyzer; control location update; stored route invalidated"
                } else {
                    "Control location update; stored route invalidated"
                },
                sourceSha256 = "",
                sampledPointCount = 0,
                route = emptyList(),
                controlPoints = courseInfo.controlPoints.map { point ->
                    placementUpdates[point.controlId]?.let { update ->
                        point.copy(
                            latitude = update.latitude,
                            longitude = update.longitude,
                            elevationMeters = elevationsByControlId[update.controlId]
                        )
                    } ?: point
                },
                courseObjects = courseInfo.courseObjects.map { point ->
                    placementUpdates[point.id]?.let { update ->
                        point.copy(
                            latitude = update.latitude,
                            longitude = update.longitude,
                            elevationMeters = elevationsByControlId[update.controlId]
                        )
                    } ?: point
                }
            )
            updatedInfos[categoryId] = AppliedCourseEdits.refreshLocations(movedInfo, controls)
            affectedCategoryIds += categoryId
        }

        return CourseControlLocationMutation(
            controls = controls.map { control ->
                if (control.latitude != null || control.longitude != null) {
                    control.copy(latitude = null, longitude = null)
                } else {
                    control
                }
            },
            courseInfoByCategoryId = updatedInfos,
            affectedCategoryIds = affectedCategoryIds.toList()
        )
    }

    fun summaries(
        race: EventRaceData,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): List<CourseControlLocationSummary> = race.controls
        .sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.displayLabel() })
        .map { control ->
            val resolution = CourseControlResolver.resolve(control, courseInfoByCategoryId.values.toList())
            CourseControlLocationSummary(
                controlId = control.id,
                label = control.displayLabel().ifBlank { control.siCode.toString() },
                latitude = resolution.location?.latitude,
                longitude = resolution.location?.longitude,
                affectedCategoryCount = courseInfoByCategoryId.values.count { info ->
                    info.appliedBindings?.controls.orEmpty().any { it.controlId == control.id } ||
                        info.controlPoints.any { it.controlId == control.id } ||
                        info.courseObjects.any { it.id == control.id }
                }
            )
        }

    fun changes(
        race: EventRaceData,
        beforeByCategoryId: Map<String, ProtectedCourseInfo>,
        afterByCategoryId: Map<String, ProtectedCourseInfo>,
        directlyAffectedCategoryIds: Set<String>
    ): List<CourseControlLocationCourseChange> {
        val categoryNamesById = (race.categories + race.courseMappings)
            .associate { it.category.id to it.category.name }
        return (beforeByCategoryId.keys + afterByCategoryId.keys)
            .distinct()
            .filter { beforeByCategoryId[it] != afterByCategoryId[it] }
            .map { categoryId ->
                val before = beforeByCategoryId[categoryId]
                val after = afterByCategoryId[categoryId]
                CourseControlLocationCourseChange(
                    categoryId = categoryId,
                    categoryName = categoryNamesById[categoryId].orEmpty(),
                    usesMovedControl = categoryId in directlyAffectedCategoryIds,
                    previousIdealOrder = before?.idealOrder.orEmpty(),
                    updatedIdealOrder = after?.idealOrder.orEmpty(),
                    previousHorizontalLengthMeters = before?.lengthMeters,
                    updatedHorizontalLengthMeters = after?.lengthMeters,
                    previousClimbMeters = before?.climbMeters,
                    updatedClimbMeters = after?.climbMeters,
                    previousEffectiveLengthMeters = before?.effectiveLengthMeters(),
                    updatedEffectiveLengthMeters = after?.effectiveLengthMeters()
                )
            }
    }

    private fun EventControl.displayLabel(): String =
        publicLabel?.trim()?.takeIf(String::isNotEmpty) ?: label.trim()
}
