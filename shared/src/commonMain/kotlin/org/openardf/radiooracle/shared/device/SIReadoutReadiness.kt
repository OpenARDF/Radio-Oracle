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

import org.openardf.radiooracle.shared.sportident.SportIdentStationMode

/** Common reason codes for SI readout diagnostics across UI and debug hooks. */
enum class SIReadoutReadinessReason {
    READY,
    NO_SI_USB_DEVICE,
    USB_PERMISSION_MISSING,
    READER_DISCONNECTED,
    STATION_WRONG_MODE,
    NO_SELECTED_RACE
}

enum class SIReadoutReadinessSeverity {
    READY,
    BLOCKED
}

data class SIReadoutReadiness(
    val ready: Boolean,
    val severity: SIReadoutReadinessSeverity,
    val reason: SIReadoutReadinessReason,
    val message: String
)

object SIReadoutReadinessRules {
    fun evaluate(
        readerState: SIReaderState,
        hasSelectedRace: Boolean,
        attachedSportIdentDeviceCount: Int? = null,
        hasUsbPermission: Boolean? = null
    ): SIReadoutReadiness {
        if (attachedSportIdentDeviceCount == 0) {
            return blocked(
                SIReadoutReadinessReason.NO_SI_USB_DEVICE,
                "No SPORTident USB reader is attached."
            )
        }
        if (hasUsbPermission == false) {
            return blocked(
                SIReadoutReadinessReason.USB_PERMISSION_MISSING,
                "Android has not granted Radio-Oracle USB permission for the SPORTident reader."
            )
        }
        if (readerState.status == SIReaderStatus.DISCONNECTED) {
            return blocked(
                SIReadoutReadinessReason.READER_DISCONNECTED,
                "SPORTident reader service is disconnected."
            )
        }
        val stationModeCode = readerState.stationModeCode
        if (stationModeCode != null && !SportIdentStationMode.isReadoutModeCode(stationModeCode)) {
            return blocked(
                SIReadoutReadinessReason.STATION_WRONG_MODE,
                "SPORTident station is in ${SportIdentStationMode.labelForModeCode(stationModeCode)} mode."
            )
        }
        if (!hasSelectedRace) {
            return blocked(
                SIReadoutReadinessReason.NO_SELECTED_RACE,
                "Select an event to enable SI card readout."
            )
        }
        return SIReadoutReadiness(
            ready = true,
            severity = SIReadoutReadinessSeverity.READY,
            reason = SIReadoutReadinessReason.READY,
            message = "Ready for SI card readout."
        )
    }

    private fun blocked(reason: SIReadoutReadinessReason, message: String): SIReadoutReadiness =
        SIReadoutReadiness(
            ready = false,
            severity = SIReadoutReadinessSeverity.BLOCKED,
            reason = reason,
            message = message
        )
}
