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
