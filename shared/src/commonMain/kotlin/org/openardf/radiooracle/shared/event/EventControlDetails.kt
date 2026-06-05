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
    val mandatory: Boolean,
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
                        mandatory = control.mandatory,
                        publicLabel = control.publicLabel.orEmpty(),
                        notes = control.notes.orEmpty()
                    )
                }

        fun typeLabel(type: ControlPointType): String = type.controlLabel()

        fun typeFromLabel(label: String): ControlPointType =
            ControlPointType.entries.firstOrNull { it.controlLabel() == label }
                ?: ControlPointType.CONTROL

        private fun ControlPointType.controlLabel(): String =
            when (this) {
                ControlPointType.CONTROL -> "Control"
                ControlPointType.SEPARATOR -> "Spectator"
                ControlPointType.BEACON -> "Beacon"
            }
    }
}
