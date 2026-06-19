package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType

/** Shared public-label heuristics for controls whose label implies a non-scoring role. */
object ControlRoleLabelRules {
    fun inferredSpecialRole(publicLabel: String?): ControlPointType? {
        val label = publicLabel.normalizedRoleLabel()
        return when {
            label.isEmpty() -> null
            label in BEACON_LABELS || "BEACON" in label -> ControlPointType.BEACON
            label in SPECTATOR_LABELS || "SPECTATOR" in label || "SEPARATOR" in label -> ControlPointType.SEPARATOR
            else -> null
        }
    }

    fun foxRoleWarning(publicLabel: String?, inferredRole: ControlPointType): String {
        val label = publicLabel?.trim().orEmpty()
        val role = roleLabel(inferredRole)
        return "Public label \"$label\" looks like $role; set Role to $role unless this station should score as a fox."
    }

    fun roleLabel(type: ControlPointType): String =
        when (type) {
            ControlPointType.CONTROL -> "Fox"
            ControlPointType.SEPARATOR -> "Spectator"
            ControlPointType.BEACON -> "Beacon"
        }

    private fun String?.normalizedRoleLabel(): String =
        orEmpty().trim().uppercase().replace(Regex("\\s+"), " ")

    private val SPECTATOR_LABELS = setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")
    private val BEACON_LABELS = setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")
}
