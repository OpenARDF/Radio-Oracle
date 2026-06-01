package org.openardf.radiooracle.desktop.usb

fun main(args: Array<String>) {
    val requestedPort = args.firstOrNull() ?: System.getenv("RADIO_ORACLE_SI_PORT")
    val maxCards = System.getenv("RADIO_ORACLE_SI_LOOP_MAX_CARDS")?.toIntOrNull() ?: 10
    val provider = JSerialCommDesktopSerialPortProvider
    val ports = provider.listPorts()
    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull { it.info.matchesSportIdent() } ?: error("No SPORTident USB serial port found.")
    } else {
        provider.getPort(requestedPort)
    }

    println("Radio-Oracle desktop SPORTident card-loop probe")
    println("Using serial port: ${port.info.describe()}")
    println("Reading up to $maxCards cards; stop by waiting for the card-event timeout or pressing Ctrl+C.")

    var cardsRead = 0
    try {
        val station = DesktopSportIdentStationProbe().connectKeepingPortOpen(port)
        println(
            "Station ready at ${station.baudRate} baud: serial=${station.stationInfo.serialNumber} " +
                "extended=${station.stationInfo.extendedMode} " +
                "codeNumber=${station.stationInfo.stationCodeNumber ?: "unknown"} " +
                "modeCode=${station.stationInfo.stationModeCode ?: "unknown"} " +
                "mode=${station.stationInfo.stationModeLabel ?: "unknown"}"
        )
        warnIfStationLooksNonDownload(station)

        val reader = DesktopSportIdentCardBlockReader(onProgress = ::println)
        while (cardsRead < maxCards) {
            val result = runCatching {
                reader.readFirstSupportedCardAfterInsertOnOpenPort(port)
            }
            val download = result.getOrNull()
            if (download == null) {
                val error = result.exceptionOrNull()
                if ((error?.message ?: "").contains("No SPORTident card insert event")) {
                    println("No additional card insert event received before timeout.")
                    break
                }
                throw error ?: IllegalStateException("SPORTident card download failed.")
            }
            cardsRead += 1
            println(
                "Read $cardsRead/$maxCards: si=${download.readout.siNumber} " +
                    "type=${download.inserted.cardType.toHexString()} " +
                    "series=${download.readout.series} punches=${download.readout.punches.size} " +
                    "finish=${download.readout.finishTime?.getTimeString() ?: "none"}"
            )
        }
    } finally {
        if (port.isOpen) {
            port.close()
        }
    }

    println("Card-loop probe finished; cards read=$cardsRead.")
}

private fun warnIfStationLooksNonDownload(station: DesktopSportIdentStationConnection) {
    if (station.stationInfo.isDownloadCapableMode == false) {
        println(
            "WARNING: SPORTident station ${station.stationInfo.serialNumber} is in " +
                "${station.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER. " +
                "Card downloads may fail."
        )
    } else if (station.stationInfo.isReadoutMode == false) {
        println(
            "WARNING: SPORTident station ${station.stationInfo.serialNumber} is in " +
                "${station.stationInfo.stationModeLabel} mode. " +
                "Continuing because the base mode appears download-capable."
        )
    }
}

private fun Byte.toHexString(): String =
    "0x%02x".format(toInt() and 0xff)
