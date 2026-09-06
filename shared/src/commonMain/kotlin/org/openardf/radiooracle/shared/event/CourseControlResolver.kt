package org.openardf.radiooracle.shared.event

import kotlinx.serialization.Serializable
import org.openardf.radiooracle.shared.domain.ControlPointType

@Serializable
enum class CourseResolutionStatus { RESOLVED, MISSING, AMBIGUOUS, INVALID }

@Serializable
data class CourseControlLocation(val latitude: Double, val longitude: Double, val elevationMeters: Double? = null)

@Serializable
data class CourseControlResolution(
    val status: CourseResolutionStatus,
    val location: CourseControlLocation? = null,
    val provenance: String? = null,
    val explanation: String? = null
)

/** One field-coordinate resolver for portable race data; conflicting evidence never picks a winner. */
object CourseControlResolver {
    private data class Candidate(val id: String, val label: String, val type: ControlPointType, val location: CourseControlLocation)

    fun resolve(control: EventControl, infos: List<ProtectedCourseInfo>): CourseControlResolution {
        val applied = infos.filter { it.appliedBindings != null }
        if (applied.isNotEmpty()) return resolveApplied(control, applied)
        val evidence = infos.flatMap { info ->
            val candidates = candidates(info)
            val exact = candidates.filter { it.id == control.id }
            if (exact.any { it.type != control.type }) return failure(control, CourseResolutionStatus.AMBIGUOUS)
            val recorded = candidates.filter { it.type == control.type && matches(control, info, it, 1) }
            // A recorded field label is evidence even when an exact ID exists. Analyzer's
            // proposed label is not: it describes the draft transmitter numbering.
            if (exact.any { candidate -> info.resultControlLabelsById[candidate.id]?.let { label ->
                label.resultControlLabelKey(control.type) !in listOfNotNull(control.label, control.publicLabel)
                    .map { it.resultControlLabelKey(control.type) }
            } == true }) return failure(control, CourseResolutionStatus.AMBIGUOUS)
            if (!info.sourceName.startsWith("Course Analyzer", ignoreCase = true) && exact.any { candidate ->
                info.resultControlLabelsById[candidate.id] == null && candidate.label.resultControlLabelKey(control.type) !in
                    listOfNotNull(control.label, control.publicLabel).map { it.resultControlLabelKey(control.type) }
            }) return failure(control, CourseResolutionStatus.AMBIGUOUS)
            exact + recorded
        }
        if (evidence.isNotEmpty()) return resolved(control, evidence, "recorded-identity")
        for (tier in 2..3) {
            val candidates = infos.flatMap { info -> candidates(info).filter { candidate ->
                candidate.type == control.type && matches(control, info, candidate, tier)
            } }
            if (candidates.isEmpty()) continue
            return resolved(control, candidates,
                listOf("control-id", "recorded-field-label", "legacy-import-id", "imported-label")[tier])
        }
        return failure(control, CourseResolutionStatus.MISSING)
    }

    private fun resolveApplied(control: EventControl, infos: List<ProtectedCourseInfo>): CourseControlResolution {
        val locations = mutableListOf<Candidate>()
        for (info in infos) {
            CourseDesignBindings.validationError(info)?.let {
                return CourseControlResolution(CourseResolutionStatus.INVALID, explanation = it)
            }
            val bound = info.appliedBindings!!.controls.singleOrNull { it.controlId == control.id } ?: continue
            if (bound.type != control.type || bound.siCode != control.siCode || bound.label != (control.publicLabel ?: control.label)) {
                return CourseControlResolution(CourseResolutionStatus.INVALID,
                    explanation = "Applied binding differs from the race catalog for ${control.publicLabel ?: control.label}.")
            }
            locations += candidates(info).filter { it.id == bound.placementId && it.type == bound.type }
        }
        return resolved(control, locations, "applied-binding")
    }

    private fun resolved(control: EventControl, candidates: List<Candidate>, provenance: String): CourseControlResolution {
        if (candidates.isEmpty()) return failure(control, CourseResolutionStatus.MISSING)
        if (candidates.any { !it.location.isValid() }) return failure(control, CourseResolutionStatus.INVALID)
        val positions = candidates.map { it.location.latitude to it.location.longitude }.distinct()
        val elevations = candidates.mapNotNull { it.location.elevationMeters }.distinct()
        if (positions.size != 1 || elevations.size > 1) return failure(control, CourseResolutionStatus.AMBIGUOUS)
        return CourseControlResolution(CourseResolutionStatus.RESOLVED,
            candidates.first().location.copy(elevationMeters = elevations.singleOrNull()), provenance)
    }

    private fun matches(control: EventControl, info: ProtectedCourseInfo, candidate: Candidate, tier: Int): Boolean {
        val fieldLabels = listOfNotNull(control.label, control.publicLabel).map { it.resultControlLabelKey(control.type) }
        val recorded = info.resultControlLabelsById[candidate.id]
        val analyzer = info.sourceName.startsWith("Course Analyzer", ignoreCase = true)
        return when (tier) {
            0 -> candidate.id == control.id
            1 -> recorded?.resultControlLabelKey(control.type) in fieldLabels
            2 -> analyzer && recorded == null && control.id.stableResultControlIdentity(control.type)?.let {
                it == candidate.id.stableResultControlIdentity(candidate.type)
            } == true
            3 -> !analyzer && recorded == null && candidate.label.trim().lowercase() in
                listOfNotNull(control.label, control.publicLabel).map { it.trim().lowercase() }
            else -> false
        }
    }

    private fun candidates(info: ProtectedCourseInfo): List<Candidate> = info.placements().mapNotNull {
        val type = it.type.controlRole() ?: return@mapNotNull null
        Candidate(it.id, it.label, type, CourseControlLocation(it.latitude, it.longitude, it.elevationMeters))
    }

    private fun failure(control: EventControl, status: CourseResolutionStatus) = CourseControlResolution(status,
        explanation = "${when (status) {
            CourseResolutionStatus.AMBIGUOUS -> "Ambiguous location"
            CourseResolutionStatus.INVALID -> "Invalid course coordinates"
            else -> "No location"
        }} for ${control.publicLabel ?: control.label} (SI ${control.siCode}).")
}
