package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType

/** Desktop Event File format choices that determine both rule family and radio band. */
enum class DesktopRaceFormat(
    val label: String,
    val raceType: RaceType,
    val raceBand: RaceBand,
    val selectable: Boolean = true
) {
    Classic80m("Classic 80m", RaceType.CLASSIC, RaceBand.M80),
    Classic2m("Classic 2m", RaceType.CLASSIC, RaceBand.M2),
    LegacyShort("Legacy Short", RaceType.SHORT, RaceBand.M80, selectable = false),
    Sprint("Sprint", RaceType.SPRINT, RaceBand.NONE),
    Foxoring("Foxoring", RaceType.FOXORING, RaceBand.COMBINED),
    Orienteering("Orienteering", RaceType.ORIENTEERING, RaceBand.NONE);

    companion object {
        val selectableEntries: List<DesktopRaceFormat> = entries.filter { it.selectable }

        fun from(raceType: RaceType, raceBand: RaceBand): DesktopRaceFormat =
            when (raceType) {
                RaceType.CLASSIC -> if (raceBand == RaceBand.M2) Classic2m else Classic80m
                RaceType.SHORT -> LegacyShort
                RaceType.SPRINT -> Sprint
                RaceType.FOXORING -> Foxoring
                RaceType.ORIENTEERING -> Orienteering
            }
    }
}
