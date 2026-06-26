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
