package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceType

class DesktopRaceFormatTest {

    @Test
    fun classicFormatsDetermineClassicTypeAndSpecificBands() {
        assertEquals(RaceType.CLASSIC, DesktopRaceFormat.Classic80m.raceType)
        assertEquals(RaceBand.M80, DesktopRaceFormat.Classic80m.raceBand)

        assertEquals(RaceType.CLASSIC, DesktopRaceFormat.Classic2m.raceType)
        assertEquals(RaceBand.M2, DesktopRaceFormat.Classic2m.raceBand)
    }

    @Test
    fun nonClassicFormatsDetermineTheirDefaultBands() {
        assertEquals(RaceBand.M80, DesktopRaceFormat.Short.raceBand)
        assertEquals(RaceBand.NONE, DesktopRaceFormat.Sprint.raceBand)
        assertEquals(RaceBand.COMBINED, DesktopRaceFormat.Foxoring.raceBand)
        assertEquals(RaceBand.NONE, DesktopRaceFormat.Orienteering.raceBand)
    }

    @Test
    fun existingRaceTypeAndBandMapToDesktopFormat() {
        assertEquals(DesktopRaceFormat.Classic80m, DesktopRaceFormat.from(RaceType.CLASSIC, RaceBand.M80))
        assertEquals(DesktopRaceFormat.Classic2m, DesktopRaceFormat.from(RaceType.CLASSIC, RaceBand.M2))
        assertEquals(DesktopRaceFormat.Sprint, DesktopRaceFormat.from(RaceType.SPRINT, RaceBand.M2))
        assertEquals(DesktopRaceFormat.Foxoring, DesktopRaceFormat.from(RaceType.FOXORING, RaceBand.M80))
        assertEquals(DesktopRaceFormat.Orienteering, DesktopRaceFormat.from(RaceType.ORIENTEERING, RaceBand.COMBINED))
    }
}
