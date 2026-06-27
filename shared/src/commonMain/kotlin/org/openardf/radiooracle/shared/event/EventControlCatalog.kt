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

/** Builds and backfills the race-level logical control catalog. */
object EventControlCatalog {
    /** Conservative classic radio-orienteering controls: 1..5 plus beacon M. */
    fun classicPreset(raceId: String): List<EventControl> =
        numberedControls(raceId, labels = (1..5).map { it.toString() }, firstSiCode = 31) +
            EventControl(
                id = stableId("M", 99, ControlPointType.BEACON),
                raceId = raceId,
                label = "M",
                siCode = 99,
                type = ControlPointType.BEACON
            )

    /** Sprint radio-orienteering controls: slow 1..5, fast F1..F5, spectator S, and beacon M. */
    fun sprintPreset(raceId: String): List<EventControl> =
        numberedControls(raceId, labels = (1..5).map { it.toString() }, firstSiCode = 31) +
            numberedControls(raceId, labels = (1..5).map { "F$it" }, firstSiCode = 41) +
            EventControl(
                id = stableId("S", 46, ControlPointType.SEPARATOR),
                raceId = raceId,
                label = "S",
                siCode = 46,
                type = ControlPointType.SEPARATOR
            ) +
            EventControl(
                id = stableId("M", 99, ControlPointType.BEACON),
                raceId = raceId,
                label = "M",
                siCode = 99,
                type = ControlPointType.BEACON
            )

    /** Derives a catalog from existing category courses and aliases without changing course behavior. */
    fun deriveFromRaceData(raceData: EventRaceData): List<EventControl> {
        val aliasesByCode = raceData.aliases.associateBy { it.siCode }
        val derivedControls = raceData.categories
            .flatMap { it.controlPoints }
            .map { controlPoint ->
                val label = aliasesByCode[controlPoint.siCode]?.name ?: defaultLabel(controlPoint)
                EventControl(
                    id = stableId(label, controlPoint.siCode, controlPoint.type),
                    raceId = raceData.race.id,
                    label = label,
                    siCode = controlPoint.siCode,
                    type = controlPoint.type
                )
            }
            .distinctBy { Triple(it.label, it.siCode, it.type) }
            .sortedWith(compareBy<EventControl>({ it.siCode }, { it.type.name }, { it.label }))
        return (raceData.controls + derivedControls)
            .distinctBy { it.id }
            .sortedWith(compareBy<EventControl>({ it.siCode }, { it.type.name }, { it.label }))
    }

    /** Returns a copy with controls and course control references populated for schema migration. */
    fun backfillControls(projectFile: EventProjectFile): EventProjectFile {
        val raceData = projectFile.raceData
        val controls = deriveFromRaceData(raceData)
        val controlsByLegacyKey = controls.associateBy { LegacyControlKey(it.siCode, it.type, it.label) }
        val aliasesByCode = raceData.aliases.associateBy { it.siCode }
        val categories = raceData.categories.map { categoryData ->
            val controlPoints = categoryData.controlPoints.map { controlPoint ->
                if (controlPoint.controlId.isNotBlank()) {
                    controlPoint
                } else {
                    val label = aliasesByCode[controlPoint.siCode]?.name ?: defaultLabel(controlPoint)
                    controlPoint.copy(
                        controlId = controlsByLegacyKey[LegacyControlKey(controlPoint.siCode, controlPoint.type, label)]?.id
                            ?: stableId(label, controlPoint.siCode, controlPoint.type)
                    )
                }
            }
            val publicControlIds = categoryData.publicControlIds.ifEmpty {
                orderedAssignedControlPoints(
                    controlPoints,
                    categoryData.category.effectiveRaceType(raceData.race)
                ).map { it.controlId }
            }
            categoryData.copy(controlPoints = controlPoints, publicControlIds = publicControlIds)
        }
        return projectFile.copy(
            raceData = raceData.copy(
                categories = categories,
                controls = controls
            )
        )
    }

    /**
     * Converts pre-scored-control Event Files from the old, unenforced
     * "mandatory" checkbox into the new radio-o scoring model.
     *
     * Legacy mandatory=true is preserved as "not scored" because that was the
     * user-facing intent of the old label. Other controls use the role default:
     * ordinary controls are foxes, while Beacon and assigned Spectator controls
     * are zero-point controls.
     */
    fun migrateLegacyControlScoring(projectFile: EventProjectFile): EventProjectFile =
        projectFile.copy(
            raceData = projectFile.raceData.copy(
                controls = projectFile.raceData.controls.map { control ->
                    control.copy(
                        scored = if (control.mandatory) false else control.type.defaultScored(),
                        mandatory = false
                    )
                }
            )
        )

    fun controlForDefinition(
        raceId: String,
        definition: org.openardf.radiooracle.shared.course.ControlPointDefinition,
        preferredLabel: String? = null
    ): EventControl {
        val label = preferredLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultLabel(definition.siCode, definition.type)
        return EventControl(
            id = stableId(label, definition.siCode, definition.type),
            raceId = raceId,
            label = label,
            siCode = definition.siCode,
            type = definition.type,
            scored = definition.type.defaultScored()
        )
    }

    fun mergeControls(existing: List<EventControl>, additions: List<EventControl>): List<EventControl> =
        (existing + additions)
            .distinctBy { it.id }
            .sortedWith(compareBy<EventControl>({ it.siCode }, { it.type.name }, { it.label }))

    fun displayLabel(control: EventControl): String = control.publicName

    private fun numberedControls(raceId: String, labels: List<String>, firstSiCode: Int): List<EventControl> =
        labels.mapIndexed { index, label ->
            val siCode = firstSiCode + index
            EventControl(
                id = stableId(label, siCode, ControlPointType.CONTROL),
                raceId = raceId,
                label = label,
                siCode = siCode,
                type = ControlPointType.CONTROL
            )
        }

    private val EventControl.publicName: String
        get() = publicLabel?.takeIf { it.isNotBlank() } ?: label

    private fun defaultLabel(controlPoint: EventControlPoint): String =
        defaultLabel(controlPoint.siCode, controlPoint.type)

    private fun orderedAssignedControlPoints(
        controlPoints: List<EventControlPoint>,
        raceType: org.openardf.radiooracle.shared.domain.RaceType
    ): List<EventControlPoint> =
        if (raceType == org.openardf.radiooracle.shared.domain.RaceType.ORIENTEERING) {
            controlPoints.sortedBy { it.order }
        } else {
            controlPoints.sortedWith(
                compareBy<EventControlPoint> {
                    org.openardf.radiooracle.shared.course.ControlPointRules.assignedControlSortGroup(
                        it.siCode,
                        it.type,
                        raceType
                    )
                }
                    .thenBy { it.siCode }
                    .thenBy { it.order }
            )
        }

    fun defaultLabel(siCode: Int, type: ControlPointType): String {
        val suffix = when (type) {
            ControlPointType.BEACON -> "B"
            ControlPointType.SEPARATOR -> "S"
            ControlPointType.CONTROL -> ""
        }
        return "$siCode$suffix"
    }

    fun stableId(label: String, siCode: Int, type: ControlPointType): String =
        "control-${label.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "unnamed" }}-$siCode-${type.name.lowercase()}"

    private fun controlById(controls: List<EventControl>, controlId: String): EventControl? =
        controls.firstOrNull { it.id == controlId }

    private data class LegacyControlKey(
        val siCode: Int,
        val type: ControlPointType,
        val label: String
    )
}
