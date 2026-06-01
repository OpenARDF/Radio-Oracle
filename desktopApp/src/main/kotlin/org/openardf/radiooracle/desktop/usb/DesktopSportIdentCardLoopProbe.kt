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

    var displayedCardsRead = 0
    val service = DesktopSportIdentReadoutService(
        portProvider = object : DesktopSerialPortProvider {
            override fun listPorts(): List<DesktopSerialPort> = listOf(port)
            override fun getPort(systemPortPath: String): DesktopSerialPort = port
        },
        connectStation = {
            DesktopSportIdentStationProbe().connectKeepingPortOpen(it).also { connection ->
                println(
                    "Station ready at ${connection.baudRate} baud: serial=${connection.stationInfo.serialNumber} " +
                        "extended=${connection.stationInfo.extendedMode} " +
                        "codeNumber=${connection.stationInfo.stationCodeNumber ?: "unknown"} " +
                        "modeCode=${connection.stationInfo.stationModeCode ?: "unknown"} " +
                        "mode=${connection.stationInfo.stationModeLabel ?: "unknown"}"
                )
                warnIfStationLooksNonDownload(connection)
            }
        },
        readCard = {
            DesktopSportIdentCardBlockReader(onProgress = ::println).readFirstSupportedCardAfterInsertOnOpenPort(it)
        }
    )
    val cardsRead = service.downloadUntilTimeout(
        maxCards = maxCards,
        onDownload = { download ->
            displayedCardsRead += 1
            println(
                "Read $displayedCardsRead/$maxCards: si=${download.readout.siNumber} " +
                    "type=${download.inserted.cardType.toHexString()} " +
                    "series=${download.readout.series} punches=${download.readout.punches.size} " +
                    "finish=${download.readout.finishTime?.getTimeString() ?: "none"}"
            )
        },
        onTimeout = {
            println("No additional card insert event received before timeout.")
        }
    )

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
