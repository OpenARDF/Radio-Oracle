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

package org.openardf.radiooracle.shared.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SIReadoutReadinessRulesTest {
    @Test
    fun blocksWhenNoSportIdentUsbDeviceIsAttached() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = connectedReadoutStation(),
            hasSelectedRace = true,
            attachedSportIdentDeviceCount = 0,
            hasUsbPermission = null
        )

        assertFalse(readiness.ready)
        assertEquals(SIReadoutReadinessReason.NO_SI_USB_DEVICE, readiness.reason)
    }

    @Test
    fun blocksWhenUsbPermissionIsMissing() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = connectedReadoutStation(),
            hasSelectedRace = true,
            attachedSportIdentDeviceCount = 1,
            hasUsbPermission = false
        )

        assertFalse(readiness.ready)
        assertEquals(SIReadoutReadinessReason.USB_PERMISSION_MISSING, readiness.reason)
    }

    @Test
    fun blocksDisconnectedReader() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = SIReaderState(SIReaderStatus.DISCONNECTED),
            hasSelectedRace = true,
            attachedSportIdentDeviceCount = 1,
            hasUsbPermission = true
        )

        assertFalse(readiness.ready)
        assertEquals(SIReadoutReadinessReason.READER_DISCONNECTED, readiness.reason)
    }

    @Test
    fun blocksWrongStationMode() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = connectedReadoutStation().copy(stationModeCode = 2),
            hasSelectedRace = true,
            attachedSportIdentDeviceCount = 1,
            hasUsbPermission = true
        )

        assertFalse(readiness.ready)
        assertEquals(SIReadoutReadinessReason.STATION_WRONG_MODE, readiness.reason)
    }

    @Test
    fun blocksWhenNoRaceIsSelected() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = connectedReadoutStation(),
            hasSelectedRace = false,
            attachedSportIdentDeviceCount = 1,
            hasUsbPermission = true
        )

        assertFalse(readiness.ready)
        assertEquals(SIReadoutReadinessReason.NO_SELECTED_RACE, readiness.reason)
    }

    @Test
    fun reportsReadyWhenStationAndRaceAreUsable() {
        val readiness = SIReadoutReadinessRules.evaluate(
            readerState = connectedReadoutStation(),
            hasSelectedRace = true,
            attachedSportIdentDeviceCount = 1,
            hasUsbPermission = true
        )

        assertTrue(readiness.ready)
        assertEquals(SIReadoutReadinessReason.READY, readiness.reason)
    }

    private fun connectedReadoutStation(): SIReaderState =
        SIReaderState(
            status = SIReaderStatus.CONNECTED,
            stationId = 554896,
            stationModeCode = 8
        )
}
