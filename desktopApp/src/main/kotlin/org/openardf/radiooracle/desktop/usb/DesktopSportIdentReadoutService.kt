package org.openardf.radiooracle.desktop.usb

/** Desktop SPORTident readout boundary shared by UI actions and diagnostics. */
class DesktopSportIdentReadoutService(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connectKeepingPortOpen(it)
    },
    private val readCard: (DesktopSerialPort) -> DesktopSportIdentCardBlockDownload = {
        DesktopSportIdentCardBlockReader().readFirstSupportedCardAfterInsertOnOpenPort(it)
    }
) {
    fun downloadOne(): DesktopSportIdentCardBlockDownload {
        val port = firstSportIdentPort()
        return withOpenDownloadStation(port) {
            readCard(port)
        }
    }

    fun downloadUntilTimeout(
        maxCards: Int,
        onDownload: (DesktopSportIdentCardBlockDownload) -> Unit,
        onTimeout: () -> Unit = {},
        shouldContinue: () -> Boolean = { true },
        isTimeoutError: (Throwable) -> Boolean = ::isNoCardInsertTimeout
    ): Int {
        val port = firstSportIdentPort()
        var cardsRead = 0
        withOpenDownloadStation(port) {
            while (cardsRead < maxCards && shouldContinue()) {
                val result = runCatching { readCard(port) }
                val download = result.getOrNull()
                if (download == null) {
                    val error = result.exceptionOrNull()
                    if (error != null && isTimeoutError(error)) {
                        onTimeout()
                        break
                    }
                    throw error ?: IllegalStateException("SPORTident card download failed.")
                }
                onDownload(download)
                cardsRead += 1
            }
        }
        return cardsRead
    }

    private fun firstSportIdentPort(): DesktopSerialPort =
        portProvider.listPorts().firstOrNull { it.info.matchesSportIdent() }
            ?: error("No SPORTident USB station found.")

    private fun <T> withOpenDownloadStation(port: DesktopSerialPort, action: () -> T): T {
        try {
            val station = connectStation(port)
            if (station.stationInfo.isDownloadCapableMode == false) {
                error(
                    "SI station ${station.stationInfo.serialNumber} is in " +
                        "${station.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER."
                )
            }
            return action()
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }
}

private fun isNoCardInsertTimeout(error: Throwable): Boolean =
    (error.message ?: "").contains("No SPORTident card insert event")
