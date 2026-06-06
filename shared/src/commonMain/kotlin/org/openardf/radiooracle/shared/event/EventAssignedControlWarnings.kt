package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType

/** Warning shown when a category omits required zero-point radio-o controls. */
data class EventAssignedControlWarning(
    val categoryId: String,
    val categoryName: String,
    val missingBeaconLabels: List<String>,
    val missingSpectatorLabels: List<String>
) {
    val hasWarnings: Boolean =
        missingBeaconLabels.isNotEmpty() || missingSpectatorLabels.isNotEmpty()
}

/** Computes non-fatal Assigned Controls warnings for desktop setup workflows. */
object EventAssignedControlWarnings {
    fun forCategory(raceData: EventRaceData, categoryId: String): EventAssignedControlWarning? {
        val categoryData = raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: return null
        val raceType = categoryData.category.effectiveRaceType(raceData.race)
        val assignedControls = categoryData.controlPoints

        val missingBeaconLabels = raceData.controls
            .filter { it.type == ControlPointType.BEACON }
            .filterNot { control -> assignedControls.any { it.matches(control) } }
            .map { it.displayLabel() }

        val missingSpectatorLabels = if (raceType == RaceType.SPRINT) {
            raceData.controls
                .filter { it.type == ControlPointType.SEPARATOR }
                .filterNot { control -> assignedControls.any { it.matches(control) } }
                .map { it.displayLabel() }
        } else {
            emptyList()
        }

        return EventAssignedControlWarning(
            categoryId = categoryId,
            categoryName = categoryData.category.name,
            missingBeaconLabels = missingBeaconLabels,
            missingSpectatorLabels = missingSpectatorLabels
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
