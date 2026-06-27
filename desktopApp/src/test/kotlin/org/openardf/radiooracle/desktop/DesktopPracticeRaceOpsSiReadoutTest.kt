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
import org.junit.Assert.assertNull
import org.junit.Test
import org.openardf.radiooracle.shared.domain.RaceLevel

class DesktopPracticeRaceOpsSiReadoutTest {
    @Test
    fun practiceRaceOpsWithConnectedStationProvidesAutoStartContext() {
        assertEquals(
            "race:SI station 554896 connected in SI MASTER mode",
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
    }

    @Test
    fun practiceAutoStartContextRequiresPracticeRaceOpsAndConnectedStation() {
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = null,
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.Setup,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.REGIONAL,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = true,
                siReaderStatusText = "SI station 554896 connected in SI MASTER mode"
            )
        )
        assertNull(
            practiceRaceOpsSiReadoutContextKey(
                raceId = "race",
                raceLevel = RaceLevel.PRACTICE,
                workflow = DesktopWorkflow.RaceOps,
                isSiReaderConnected = false,
                siReaderStatusText = "SI station disconnected"
            )
        )
    }
}
