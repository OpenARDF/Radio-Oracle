package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.ControlPointType
import java.security.MessageDigest
import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** A checked reference to the existing race catalog, not another catalog to edit. */
@Serializable
data class AppliedCourseControl(
    val placementId: String,
    val controlId: String,
    val label: String,
    val siCode: Int,
    val type: ControlPointType
)

/** Stored within the protected payload so the placement/identity relationship stays protected. */
@Serializable
data class AppliedCourseBindings(
    val version: Int = 1,
    val revision: String,
    val inputFingerprint: String,
    val controls: List<AppliedCourseControl>,
    /** Includes Start, Finish and required waypoints; repeated visits are retained. */
    val orderedPlacementIds: List<String>
) {
    val orderedControlIds: List<String>
        get() {
            val byPlacement = controls.associateBy { it.placementId }
            return orderedPlacementIds.mapNotNull { byPlacement[it]?.controlId }
        }
}

object CourseDesignBindings {
    private val json = Json { encodeDefaults = true }

    /** Caller supplies reviewed bindings and order. Labels and SI numbers are never guessed here. */
    fun prepare(info: ProtectedCourseInfo, controls: List<EventControl>,
                controlIdsByPlacementId: Map<String, String>, orderedPlacementIds: List<String>, revision: String): ProtectedCourseInfo {
        require(revision.isNotBlank()) { "Course revision is required." }
        require(controls.map { it.id }.distinct().size == controls.size) { "Duplicate race control IDs." }
        val byId = controls.associateBy { it.id }
        val allPlacements = info.validatedPlacements()
        val placements = allPlacements.mapNotNull { (id, point) -> point.type.controlRole()?.let { id to it } }.toMap()
        require(placements.isNotEmpty()) { "Course has no control placements." }
        require(controlIdsByPlacementId.keys == placements.keys) { "Every course placement needs an explicit control binding." }
        require(controlIdsByPlacementId.values.distinct().size == placements.size) { "Different placements cannot share one race control." }
        val bound = placements.map { (id, type) ->
            val control = requireNotNull(byId[controlIdsByPlacementId[id]]) { "Unknown control for placement $id." }
            require(control.type == type) { "Control role differs for ${control.publicLabel ?: control.label}." }
            AppliedCourseControl(id, control.id, control.publicLabel ?: control.label, control.siCode, control.type)
        }
        require(orderedPlacementIds.toSet() == allPlacements.keys) { "Course order must include every placement, including Start, Finish and waypoints." }
        val order = orderedPlacementIds.mapNotNull { controlIdsByPlacementId[it] }
        require(order.toSet() == bound.map { it.controlId }.toSet()) { "Course order must include every bound control." }
        val candidate = info.copy(appliedBindings = AppliedCourseBindings(revision = revision, inputFingerprint = "",
            controls = bound, orderedPlacementIds = orderedPlacementIds))
        val prepared = candidate.copy(appliedBindings = candidate.appliedBindings!!.copy(inputFingerprint = fingerprint(candidate)))
        require(validationError(prepared) == null) { validationError(prepared).orEmpty() }
        return prepared
    }

    /** Excludes import filename, timestamps, random identifiers and encryption bytes. */
    fun fingerprint(info: ProtectedCourseInfo): String {
        val canonical = info.copy(sourceName = "", sourceSha256 = "", sampledPointCount = 0,
            idealOrder = if (info.appliedBindings == null) info.idealOrder.trim() else "",
            resultControlLabelsById = emptyMap(), appliedBindings = null, controlPoints = emptyList(),
            courseObjects = info.validatedPlacements().values.map { it.copy(id = "", description = null) }
                .sortedWith(compareBy({ it.type.name }, { it.label }, { it.latitude }, { it.longitude })))
        val binding = info.appliedBindings
        val bound = binding?.controls.orEmpty().associateBy { it.controlId }
        val order = binding?.orderedControlIds.orEmpty().map { id -> bound[id]?.let { listOf(it.label, it.siCode.toString(), it.type.name) } ?: listOf("missing") }
        val coordinates = (info.controlPoints.map { it.controlId to listOf(it.latitude, it.longitude) } +
            info.courseObjects.map { it.id to listOf(it.latitude, it.longitude) }).groupBy({ it.first }, { it.second })
        val objects = info.placements().groupBy { it.id }
        val visitOrder = binding?.orderedPlacementIds.orEmpty().map { id ->
            objects[id].orEmpty().map { listOf(it.type.name, it.label, it.latitude.toString(), it.longitude.toString()) }
                .distinct().sortedBy { json.encodeToString(it) }
        }
        val inventory = binding?.controls.orEmpty().map {
            listOf(it.label, it.siCode.toString(), it.type.name,
                json.encodeToString(coordinates[it.placementId].orEmpty().distinct().sortedBy { p -> json.encodeToString(p) }))
        }.sortedBy { json.encodeToString(it) }
        val bytes = (json.encodeToString(canonical) + json.encodeToString(inventory) + json.encodeToString(order) + json.encodeToString(visitOrder)).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun validationError(info: ProtectedCourseInfo): String? {
        val binding = info.appliedBindings ?: return null
        if (binding.version != 1) return "Unsupported applied course binding version ${binding.version}."
        if (binding.revision.isBlank()) return "Course revision is missing."
        if (binding.inputFingerprint != runCatching { fingerprint(info) }.getOrNull()) return "Applied course data changed; apply the revised design before exporting it."
        return runCatching {
            val objects = info.validatedPlacements()
            val locations = objects.mapNotNull { (id, point) -> point.type.controlRole()?.let { id to it } }.toMap()
            require(locations.isNotEmpty()) { "Course has no control placements." }
            require(binding.controls.all { it.controlId.isNotBlank() && it.label.isNotBlank() }) { "Applied course identity is missing." }
            require(binding.controls.all { SportIdentCodes.isSICodeValid(it.siCode) }) { "Applied SI assignment is outside the supported station range." }
            require(binding.controls.all { objects[it.placementId]?.label == it.label }) { "Applied placement labels differ from the accepted field labels." }
            require(info.route.all { CourseControlLocation(it.latitude, it.longitude, it.elevationMeters).isValid() }) { "Invalid route coordinates." }
            require(info.lengthMeters == null || info.lengthMeters >= 0) { "Invalid course length." }
            require(info.climbMeters == null || info.climbMeters >= 0) { "Invalid course climb." }
            require(binding.orderedPlacementIds.toSet() == objects.keys) { "Applied course visits are incomplete." }
            val startIds = objects.filterValues { it.type == ProtectedCourseObjectType.START }.keys
            val finishIds = objects.filterValues { it.type == ProtectedCourseObjectType.FINISH }.keys
            require(startIds.isEmpty() || (startIds.size == 1 && binding.orderedPlacementIds.firstOrNull() in startIds)) { "Course must begin at its Start." }
            require(finishIds.isEmpty() || (finishIds.size == 1 && binding.orderedPlacementIds.lastOrNull() in finishIds)) { "Course must end at its Finish." }
            require(binding.controls.map { it.placementId }.toSet() == locations.keys) { "Applied course bindings are incomplete." }
            require(binding.controls.map { it.placementId }.distinct().size == binding.controls.size &&
                binding.controls.map { it.controlId }.distinct().size == binding.controls.size) { "Applied course bindings are ambiguous." }
            require(binding.controls.all { locations[it.placementId] == it.type }) { "Applied course roles differ." }
            require(binding.orderedControlIds.toSet() == binding.controls.map { it.controlId }.toSet()) { "Applied course order is incomplete." }
        }.exceptionOrNull()?.message
    }

}
