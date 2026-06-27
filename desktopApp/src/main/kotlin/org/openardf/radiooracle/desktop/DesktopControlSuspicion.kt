package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlDetails

/**
 * Computes warning reasons for controls that look suspicious on the Setup > Controls screen.
 *
 * These warnings are diagnostic only. Setup > Controls remains the source of truth for the
 * SI-code-to-Public-label mapping, and Setup > Categories remains the source of truth for which
 * controls are assigned to each category. The UI uses these warnings to help the user spot retained
 * import leftovers, missing Public labels, or ambiguous Public labels without silently changing data.
 */
internal fun controlSuspicionReasonsByControlId(
    controls: List<EventControlDetails>,
    categories: List<EventCategoryData>
): Map<String, List<String>> {
    val assignedControlIds = assignedControlIds(controls, categories)
    val duplicatePublicLabels = controls
        .mapNotNull { control ->
            control.normalizedPublicLabel().takeIf { it.isNotEmpty() }?.let { normalized ->
                normalized to control.publicLabel.trim()
            }
        }
        .groupBy({ it.first }, { it.second })
        .filterValues { labels -> labels.size > 1 }
        .keys

    return controls.associate { control ->
        val reasons = buildList {
            if (categories.isNotEmpty() && control.id !in assignedControlIds) {
                add("Control is not assigned to any category.")
            }
            if (control.publicLabel.isBlank()) {
                add("Control has no Public label.")
            } else if (control.normalizedPublicLabel() in duplicatePublicLabels) {
                add("Public label \"${control.publicLabel.trim()}\" is also used by another control.")
            }
        }
        control.id to reasons
    }
}

@Suppress("DEPRECATION")
private fun assignedControlIds(
    controls: List<EventControlDetails>,
    categories: List<EventCategoryData>
): Set<String> {
    val idsByLegacyDefinition = controls
        .groupBy { it.siCode to it.type }
        .mapValues { (_, matchingControls) -> matchingControls.map { it.id } }

    return categories
        .flatMap { categoryData ->
            categoryData.publicControlIds +
                categoryData.controlPoints.flatMap { controlPoint ->
                    if (controlPoint.controlId.isNotBlank()) {
                        listOf(controlPoint.controlId)
                    } else {
                        idsByLegacyDefinition[controlPoint.siCode to controlPoint.type].orEmpty()
                    }
                }
        }
        .toSet()
}

private fun EventControlDetails.normalizedPublicLabel(): String =
    publicLabel.trim().lowercase()
