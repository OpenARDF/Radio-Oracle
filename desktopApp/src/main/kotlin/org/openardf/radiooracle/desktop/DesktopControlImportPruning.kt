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

import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile

data class DesktopControlImportPruneResult(
    val projectFile: EventProjectFile,
    val deletedControls: List<EventControl>
) {
    val deletedControlNames: List<String>
        get() = deletedControls.map { it.importDeletedControlDisplayName() }
}

object DesktopControlImportPruning {
    const val ImportAllControlsDeletionNotice: String =
        "Importing all controls from the file can delete existing controls that do not clearly match imported controls when keeping them would exceed the allowed number of foxes, beacons, or spectators for this event type. Deleted controls are removed from category assignments."

    fun unmatchedControlsExceedingRaceLimits(
        projectFile: EventProjectFile,
        importedControlIds: Set<String>,
        raceTypeOverride: RaceType? = null
    ): List<EventControl> {
        val raceType = raceTypeOverride ?: projectFile.raceData.race.raceType
        val controlsByType = projectFile.raceData.controls.groupBy { it.type }
        return ControlPointType.values().flatMap { type ->
            val allowedCount = allowedRaceControlCount(raceType, type)
                ?: return@flatMap emptyList()
            val controls = controlsByType[type].orEmpty()
            val excessCount = controls.size - allowedCount
            if (excessCount <= 0) {
                return@flatMap emptyList()
            }
            controls
                .filterNot { it.id in importedControlIds }
                .sortedWith(compareBy<EventControl> { it.siCode }.thenBy { it.importDisplayLabel() })
                .take(excessCount)
        }
    }

    fun pruneUnmatchedControlsExceedingRaceLimits(
        projectFile: EventProjectFile,
        importedControlIds: Set<String>,
        raceTypeOverride: RaceType? = null
    ): DesktopControlImportPruneResult {
        val controlsToDelete = unmatchedControlsExceedingRaceLimits(projectFile, importedControlIds, raceTypeOverride)
        val prunedProject = controlsToDelete.fold(projectFile) { currentProject, control ->
            EventProjectEditor.removeControl(
                projectFile = currentProject,
                controlId = control.id,
                clearProtectedCourseData = false
            )
        }
        return DesktopControlImportPruneResult(prunedProject, controlsToDelete)
    }

    fun allowedRaceControlCount(raceType: RaceType, type: ControlPointType): Int? =
        when (raceType) {
            RaceType.SPRINT -> when (type) {
                ControlPointType.CONTROL -> 10
                ControlPointType.BEACON -> 1
                ControlPointType.SEPARATOR -> 1
            }
            RaceType.CLASSIC,
            RaceType.SHORT -> when (type) {
                ControlPointType.CONTROL -> 5
                ControlPointType.BEACON -> 1
                ControlPointType.SEPARATOR -> 0
            }
            RaceType.FOXORING -> when (type) {
                ControlPointType.CONTROL -> 12
                ControlPointType.BEACON -> 1
                ControlPointType.SEPARATOR -> 0
            }
            RaceType.ORIENTEERING -> when (type) {
                ControlPointType.CONTROL -> null
                ControlPointType.BEACON,
                ControlPointType.SEPARATOR -> 0
            }
        }
}

fun EventControl.importDisplayLabel(): String =
    publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label

fun EventControl.importDeletedControlDisplayName(): String =
    "${importDisplayLabel()} (${siCode})"
