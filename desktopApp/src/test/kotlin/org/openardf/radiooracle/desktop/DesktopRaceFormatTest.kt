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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(RaceBand.M80, DesktopRaceFormat.LegacyShort.raceBand)
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

    @Test
    fun legacyShortCanDisplayExistingFilesButIsNotSelectableForNewEventFiles() {
        assertEquals(DesktopRaceFormat.LegacyShort, DesktopRaceFormat.from(RaceType.SHORT, RaceBand.M80))
        assertFalse(DesktopRaceFormat.LegacyShort.selectable)
        assertFalse(DesktopRaceFormat.LegacyShort in DesktopRaceFormat.selectableEntries)
    }

    @Test
    fun orienteeringCanDisplayExistingFilesButIsNotSelectableForNewEventFiles() {
        assertEquals(DesktopRaceFormat.Orienteering, DesktopRaceFormat.from(RaceType.ORIENTEERING, RaceBand.NONE))
        assertFalse(DesktopRaceFormat.Orienteering.selectable)
        assertFalse(DesktopRaceFormat.Orienteering in DesktopRaceFormat.selectableEntries)
    }
}
