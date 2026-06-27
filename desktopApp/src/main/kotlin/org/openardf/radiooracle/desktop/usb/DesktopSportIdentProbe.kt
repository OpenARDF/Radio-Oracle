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
    val provider = JSerialCommDesktopSerialPortProvider
    val ports = provider.listPorts()

    println("Radio-Oracle desktop SPORTident serial probe")
    println("Detected serial ports:")
    if (ports.isEmpty()) {
        println("- none")
    } else {
        ports.forEach { port ->
            println("- ${port.info.describe()}")
        }
    }

    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull { it.info.matchesSportIdent() } ?: error("No SPORTident USB serial port found.")
    } else {
        provider.getPort(requestedPort)
    }

    println("Using serial port: ${port.info.describe()}")
    val connection = DesktopSportIdentStationProbe().connect(port)
    println("SPORTident probe OK at ${connection.baudRate} baud: ${connection.probeReply.toHexString()}")
    println(
        "SPORTident station info: serial=${connection.stationInfo.serialNumber} " +
            "extended=${connection.stationInfo.extendedMode} " +
            "codeNumber=${connection.stationInfo.stationCodeNumber ?: "unknown"} " +
            "modeCode=${connection.stationInfo.stationModeCode ?: "unknown"} " +
            "mode=${connection.stationInfo.stationModeLabel ?: "unknown"}"
    )
    if (connection.stationInfo.isReadoutMode == false) {
        println(
            "WARNING: SPORTident station ${connection.stationInfo.serialNumber} is in " +
                "${connection.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER. " +
                "Reprogram it in a download-capable mode before using it for SI-card downloads."
        )
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
