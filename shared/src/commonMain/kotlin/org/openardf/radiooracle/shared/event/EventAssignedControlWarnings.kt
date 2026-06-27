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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType

/** Warning shown when a category omits required radio-o course controls. */
data class EventAssignedControlWarning(
    val categoryId: String,
    val categoryName: String,
    val hasNoAssignedFoxes: Boolean,
    val isClearingAllAssignments: Boolean,
    val missingBeaconLabels: List<String>
) {
    val hasWarnings: Boolean =
        hasNoAssignedFoxes || missingBeaconLabels.isNotEmpty()
}

/** Computes non-fatal Assigned Controls warnings for desktop setup workflows. */
object EventAssignedControlWarnings {
    fun forCategory(raceData: EventRaceData, categoryId: String): EventAssignedControlWarning? {
        val categoryData = raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: return null
        val raceType = categoryData.category.effectiveRaceType(raceData.race)
        val assignedControls = categoryData.controlPoints
        val hasNoAssignedFoxes = raceType != RaceType.ORIENTEERING &&
            assignedControls.none { it.type == ControlPointType.CONTROL }
        val isClearingAllAssignments = assignedControls.isEmpty()

        val requiredBeacons = raceData.controls.filter { it.type == ControlPointType.BEACON }
        val missingBeaconLabels = if (raceType == RaceType.ORIENTEERING) {
            emptyList()
        } else if (requiredBeacons.isEmpty()) {
            listOf("Beacon").takeUnless { assignedControls.any { it.type == ControlPointType.BEACON } }.orEmpty()
        } else {
            requiredBeacons
                .filterNot { control -> assignedControls.any { it.matches(control) } }
                .map { it.displayLabel() }
        }

        return EventAssignedControlWarning(
            categoryId = categoryId,
            categoryName = categoryData.category.name,
            hasNoAssignedFoxes = hasNoAssignedFoxes,
            isClearingAllAssignments = isClearingAllAssignments,
            missingBeaconLabels = missingBeaconLabels
        ).takeIf { it.hasWarnings }
    }

    private fun EventControl.displayLabel(): String =
        publicLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: label.takeIf { it.isNotBlank() }
            ?: siCode.toString()

    @Suppress("DEPRECATION")
    private fun EventControlPoint.matches(control: EventControl): Boolean =
        controlId == control.id || (siCode == control.siCode && type == control.type)
}
