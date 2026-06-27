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

/** Shared global-control row prepared for desktop and other event-admin surfaces. */
data class EventControlDetails(
    val id: String,
    val label: String,
    val siCode: Int,
    val siCodeText: String,
    val type: ControlPointType,
    val typeLabel: String,
    val scored: Boolean,
    val publicLabel: String,
    val notes: String
) {
    companion object {
        fun from(raceData: EventRaceData): List<EventControlDetails> =
            raceData.controls
                .sortedWith(compareBy({ it.siCode }, { it.type.name }, { it.label }))
                .map { control ->
                    EventControlDetails(
                        id = control.id,
                        label = control.label,
                        siCode = control.siCode,
                        siCodeText = control.siCode.toString(),
                        type = control.type,
                        typeLabel = control.type.controlLabel(),
                        scored = control.scored,
                        publicLabel = control.publicLabel.orEmpty(),
                        notes = control.notes.orEmpty()
                    )
                }

        fun typeLabel(type: ControlPointType): String = type.controlLabel()

        fun typeFromLabel(label: String): ControlPointType =
            when (label.trim().lowercase()) {
                "fox", "control" -> ControlPointType.CONTROL
                "spectator", "separator" -> ControlPointType.SEPARATOR
                "beacon" -> ControlPointType.BEACON
                else -> ControlPointType.entries.firstOrNull { it.name.equals(label, ignoreCase = true) }
            }
                ?: ControlPointType.CONTROL

        private fun ControlPointType.controlLabel(): String =
            when (this) {
                ControlPointType.CONTROL -> "Fox"
                ControlPointType.SEPARATOR -> "Spectator"
                ControlPointType.BEACON -> "Beacon"
            }
    }
}
