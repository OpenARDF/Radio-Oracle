package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventCourseRuleCatalog

internal data class DesktopPracticeRouteDirection(
    val raceLevel: RaceLevel,
    val raceType: RaceType,
    val categoryName: String,
    val savedControlIds: List<String>
) {
    /** Resolve identities before this check; displayed fox numbers can change after analysis. */
    fun retentionNote(
        start: CourseGeoPoint,
        beacon: CourseGeoPoint?,
        selectedControls: List<EventControl>,
        effectiveLength: (List<String>) -> Double?
    ): String? {
        if (raceLevel != RaceLevel.PRACTICE || beacon == null) return null
        val rule = EventCourseRuleCatalog.spacingRuleSet(raceType, categoryName) ?: return null
        if (!rule.includeBeaconInStartCheck || start.distanceMetersTo(beacon) >= rule.startMinMeters) return null
        val terminalBeaconId = selectedControls.lastOrNull()?.takeIf { it.type == ControlPointType.BEACON }?.id
            ?: return null
        if (savedControlIds.lastOrNull() != terminalBeaconId) return null
        val selectedIds = selectedControls.map { it.id }
        if (savedControlIds.size != selectedIds.size || savedControlIds.toSet() != selectedIds.toSet() ||
            savedControlIds.distinct().size != savedControlIds.size) return null
        val foxIds = selectedControls.filter { it.type == ControlPointType.CONTROL }.map { it.id }
        val savedFoxIds = savedControlIds.filter { it in foxIds }
        if (foxIds.size < 2 || foxIds != savedFoxIds.asReversed()) return null

        // Use the same sampled geometry and effective-length objective as the route search,
        // including mandatory detours. Missing elevations never trigger a horizontal-only exception.
        val selectedLength = effectiveLength(selectedIds)?.takeIf { it.isFinite() } ?: return null
        val savedLength = effectiveLength(savedControlIds)?.takeIf { it.isFinite() } ?: return null
        if (selectedLength >= savedLength) return null
        return "Practice saved-direction exception: Start to finish beacon is below the existing " +
            "${rule.startMinMeters} m separation requirement. The selected candidate exactly reverses the saved fox order " +
            "and has a shorter effective length, including known mandatory detours. " +
            "The calculated route retains the saved direction; it is not the shortest route found."
    }
}

internal data class DesktopCourseOrderSelection(
    val controlIds: List<String>,
    val practiceSavedDirection: Boolean
)
