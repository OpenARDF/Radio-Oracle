package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType

/** Shared public-label heuristics for controls whose label implies a non-scoring role. */
object ControlRoleLabelRules {
    fun inferredRole(publicLabel: String?): ControlPointType? {
        val label = publicLabel.normalizedRoleLabel()
        return when {
            label.isEmpty() -> null
            label in BEACON_LABELS || "BEACON" in label -> ControlPointType.BEACON
            label in SPECTATOR_LABELS || "SPECTATOR" in label || "SEPARATOR" in label -> ControlPointType.SEPARATOR
            label.isFoxLabel() -> ControlPointType.CONTROL
            else -> null
        }
    }

    fun inferredSpecialRole(publicLabel: String?): ControlPointType? {
        return inferredRole(publicLabel).takeIf { it != ControlPointType.CONTROL }
    }

    fun mismatchWarning(publicLabel: String?, selectedRole: ControlPointType, inferredRole: ControlPointType): String {
        val label = publicLabel?.trim().orEmpty()
        val inferredLabel = roleLabel(inferredRole)
        val selectedLabel = roleLabel(selectedRole)
        return "Public label \"$label\" looks like $inferredLabel; set Role to $inferredLabel unless this station should be $selectedLabel."
    }

    fun roleLabel(type: ControlPointType): String =
        when (type) {
            ControlPointType.CONTROL -> "Fox"
            ControlPointType.SEPARATOR -> "Spectator"
            ControlPointType.BEACON -> "Beacon"
        }

    private fun String.isFoxLabel(): Boolean =
        this == "FOX" ||
            startsWith("FOX ") ||
            matches(FOX_NUMBER_LABEL_REGEX) ||
            matches(SPRINT_FAST_FOX_LABEL_REGEX)

    private fun String?.normalizedRoleLabel(): String =
        orEmpty().trim().uppercase().replace(Regex("\\s+"), " ")

    private val FOX_NUMBER_LABEL_REGEX = Regex("(?:FOX )?\\d+")
    private val SPRINT_FAST_FOX_LABEL_REGEX = Regex("(?:\\d+F|F\\d+)")
    private val SPECTATOR_LABELS = setOf("S", "SP", "SPEC", "SPECTATOR", "SEP", "SEPARATOR")
    private val BEACON_LABELS = setOf("B", "BB", "M", "MO", "BEACON", "FINISH BEACON")
}
