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
