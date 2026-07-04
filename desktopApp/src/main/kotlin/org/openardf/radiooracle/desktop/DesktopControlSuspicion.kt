/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControlDetails

internal const val UnassignedControlReason = "Control is not assigned to any category."
private const val MissingPublicLabelReason = "Control has no Public label."

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
                add(UnassignedControlReason)
            }
            if (control.publicLabel.isBlank()) {
                add(MissingPublicLabelReason)
            } else if (control.normalizedPublicLabel() in duplicatePublicLabels) {
                add("Public label \"${control.publicLabel.trim()}\" is also used by another control.")
            }
        }
        control.id to reasons
    }
}

internal fun unusedControlWarningCount(warningReasonsByControlId: Map<String, List<String>>): Int =
    warningReasonsByControlId.values.count { reasons -> UnassignedControlReason in reasons }

@Suppress("DEPRECATION")
private fun assignedControlIds(
    controls: List<EventControlDetails>,
    categories: List<EventCategoryData>
): Set<String> {
    val controlIds = controls.mapTo(mutableSetOf()) { it.id }
    val idsByLegacyDefinition = controls
        .groupBy { it.siCode to it.type }
        .mapValues { (_, matchingControls) -> matchingControls.map { it.id } }

    return categories
        .flatMap { categoryData ->
            categoryData.publicControlIds +
                categoryData.controlPoints.flatMap { controlPoint ->
                    if (controlPoint.controlId.isNotBlank() && controlPoint.controlId in controlIds) {
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
