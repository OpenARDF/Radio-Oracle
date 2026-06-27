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

package org.openardf.radiooracle.desktop.usb

import java.time.LocalDateTime
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo

data class DesktopSportIdentTimeSyncInspection(
    val portInfo: DesktopSerialPortInfo?,
    val baudRate: Int?,
    val stationInfo: SportIdentStationInfo?,
    val statusText: String,
    val canSyncTime: Boolean,
    val disabledReason: String?
) {
    companion object {
        fun disconnected(): DesktopSportIdentTimeSyncInspection =
            DesktopSportIdentTimeSyncInspection(
                portInfo = null,
                baudRate = null,
                stationInfo = null,
                statusText = "No SPORTident USB station detected.",
                canSyncTime = false,
                disabledReason = "Connect a SPORTident download station before syncing time."
            )
    }
}

data class DesktopSportIdentTimeSyncResult(
    val stationInfo: SportIdentStationInfo,
    val sourceTime: LocalDateTime,
    val confirmedTime: LocalDateTime?
)

class DesktopSportIdentTimeSyncService(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connect(it)
    }
) {
    fun inspectDownloadStation(): DesktopSportIdentTimeSyncInspection {
        val port = portProvider.listPorts().firstOrNull { it.info.matchesSportIdent() }
            ?: return DesktopSportIdentTimeSyncInspection.disconnected()

        return runCatching {
            val connection = connectStation(port)
            val stationInfo = connection.stationInfo
            val modeLabel = stationInfo.stationModeLabel ?: "unknown"
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = connection.baudRate,
                stationInfo = stationInfo,
                statusText = "SI station ${stationInfo.serialNumber} connected in $modeLabel mode.",
                canSyncTime = false,
                disabledReason = "Time sync write support is pending SPORTident protocol validation."
            )
        }.getOrElse { error ->
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = null,
                stationInfo = null,
                statusText = "SI station inspection failed: ${error.message ?: error::class.simpleName}",
                canSyncTime = false,
                disabledReason = "Resolve the station connection error before syncing time."
            )
        }
    }

    fun syncTime(sourceTime: LocalDateTime = LocalDateTime.now()): DesktopSportIdentTimeSyncResult {
        throw UnsupportedOperationException(
            "SPORTident time sync write support is not implemented yet. Source time was $sourceTime."
        )
    }
}
