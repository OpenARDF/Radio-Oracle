package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType

/** Fox numbering assigns configured stations to locations. It never changes a fox/station pair. */
object CourseStationAssignments {
    fun foxForLabel(controls: List<EventControl>, label: String): EventControl? {
        val key = foxLabelKey(label)
        val matches = controls.filter { it.type == ControlPointType.CONTROL &&
            foxLabelKey(it.publicLabel?.takeIf(String::isNotBlank) ?: it.label) == key }
        require(matches.size <= 1) {
            "Fox label \"$label\" matches multiple stations: ${matches.joinToString { "SI ${it.siCode}" }}. Correct the control labels before applying."
        }
        return matches.singleOrNull()
    }

    // Do not infer numbers from SI codes, and do not merge Sprint slow/fast foxes.
    private fun foxLabelKey(label: String): String {
        val normalized = label.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        val fast = Regex("(?:F(\\d+)|(\\d+)F)").matchEntire(normalized)
        if (fast != null) return "fast:${fast.groupValues.drop(1).first { it.isNotBlank() }.toInt()}"
        return ControlRoleLabelRules.foxNumber(label)?.let { "fox:$it" } ?: normalized
    }
}
