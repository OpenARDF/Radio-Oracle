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

/** Neutral display order for assigned radio-o controls shared by desktop and Android imports. */
@Suppress("DEPRECATION")
object EventAssignedControlOrder {
    fun sort(
        controlPoints: List<EventControlPoint>,
        controlsById: Map<String, EventControl>,
        raceType: RaceType
    ): List<EventControlPoint> =
        if (raceType == RaceType.ORIENTEERING) {
            controlPoints.sortedBy { it.order }
        } else {
            controlPoints.sortedWith(comparator(controlsById, raceType))
        }

    private fun comparator(
        controlsById: Map<String, EventControl>,
        raceType: RaceType
    ): Comparator<EventControlPoint> =
        compareBy<EventControlPoint> { controlPoint ->
            controlPoint.assignedControlGroup(controlsById, raceType)
        }
            .thenBy { controlPoint -> controlPoint.publicSortNumber(controlsById) }
            .thenBy { controlPoint -> controlsById[controlPoint.controlId]?.siCode ?: controlPoint.siCode }
            .thenBy { controlPoint -> controlsById[controlPoint.controlId]?.publicControlLabel().orEmpty() }
            .thenBy { controlPoint -> controlPoint.order }

    private fun EventControlPoint.assignedControlGroup(
        controlsById: Map<String, EventControl>,
        raceType: RaceType
    ): Int {
        val control = controlsById[controlId]
        val label = control?.publicControlLabel()?.trim()?.uppercase().orEmpty()
        val isBeacon = control?.type == ControlPointType.BEACON ||
            type == ControlPointType.BEACON ||
            label in BEACON_PUBLIC_LABELS
        val isSeparator = control?.type == ControlPointType.SEPARATOR ||
            type == ControlPointType.SEPARATOR ||
            label in SPECTATOR_PUBLIC_LABELS
        return when {
            raceType != RaceType.SPRINT && isBeacon -> 2
            raceType != RaceType.SPRINT && isSeparator -> 1
            raceType == RaceType.SPRINT && isBeacon -> 3
            raceType == RaceType.SPRINT && isSeparator -> 1
            raceType == RaceType.SPRINT && label.isFastSprintLabel() -> 2
            else -> 0
        }
    }

    private fun EventControlPoint.publicSortNumber(controlsById: Map<String, EventControl>): Int {
        val label = controlsById[controlId]?.publicControlLabel().orEmpty()
        return PUBLIC_NUMBER_REGEX.find(label)?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun String.isFastSprintLabel(): Boolean =
        FAST_PUBLIC_LABEL_REGEX.matches(this) || contains("FAST")

    private fun EventControl.publicControlLabel(): String =
        publicLabel?.takeIf { it.isNotBlank() } ?: label

    private val PUBLIC_NUMBER_REGEX = Regex("\\d+")
    private val FAST_PUBLIC_LABEL_REGEX = Regex("(?:\\d+F|F\\d+)")
    private val SPECTATOR_PUBLIC_LABELS = setOf("S", "SPECTATOR", "SEP", "SEPARATOR")
    private val BEACON_PUBLIC_LABELS = setOf("B", "M", "BEACON")
}
