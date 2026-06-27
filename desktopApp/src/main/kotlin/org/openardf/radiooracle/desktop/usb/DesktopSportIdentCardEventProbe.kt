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

import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardEventParser

fun main(args: Array<String>) {
    val requestedPort = args.firstOrNull() ?: System.getenv("RADIO_ORACLE_SI_PORT")
    val provider = JSerialCommDesktopSerialPortProvider
    val ports = provider.listPorts()
    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull { it.info.matchesSportIdent() } ?: error("No SPORTident USB serial port found.")
    } else {
        provider.getPort(requestedPort)
    }

    println("Radio-Oracle desktop SPORTident card-event probe")
    println("Using serial port: ${port.info.describe()}")

    val station = DesktopSportIdentStationProbe().connect(port)
    println(
        "Station ready at ${station.baudRate} baud: serial=${station.stationInfo.serialNumber} " +
            "extended=${station.stationInfo.extendedMode} " +
            "codeNumber=${station.stationInfo.stationCodeNumber ?: "unknown"} " +
            "modeCode=${station.stationInfo.stationModeCode ?: "unknown"} " +
            "mode=${station.stationInfo.stationModeLabel ?: "unknown"}"
    )
    warnIfNotReadoutMode(station)
    println("Waiting up to ${DesktopSportIdentCardEventMonitor.defaultMaxWaitMs / 1000} seconds for one card insert/remove event...")

    val event = DesktopSportIdentCardEventMonitor().waitForOneEvent(port, station.baudRate)
        ?: error("No SPORTident card event received before timeout.")

    when (event) {
        is SportIdentCardEvent.Inserted -> {
            println("Card inserted: type=${event.cardType.toHexString()} si=${event.siNumber}")
        }
        is SportIdentCardEvent.Removed -> {
            println("Card removed: si=${event.siNumber}")
        }
    }
}

private fun warnIfNotReadoutMode(station: DesktopSportIdentStationConnection) {
    if (station.stationInfo.isReadoutMode == false) {
        println(
            "WARNING: SPORTident station ${station.stationInfo.serialNumber} is in " +
                "${station.stationInfo.stationModeLabel} mode instead of READOUT/SI MASTER. " +
                "Reprogram it in a download-capable mode before using it for SI-card downloads."
        )
    }
}

class DesktopSportIdentCardEventMonitor(
    private val readTimeoutMs: Int = 500,
    private val writeTimeoutMs: Int = 500,
    private val openWaitTimeMs: Int = 200,
    private val maxWaitMs: Long = defaultMaxWaitMs
) {
    fun waitForOneEvent(port: DesktopSerialPort, baudRate: Int): SportIdentCardEvent? {
        try {
            port.configure(baudRate, readTimeoutMs, writeTimeoutMs)
            if (!port.open(openWaitTimeMs)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            val deadline = System.currentTimeMillis() + maxWaitMs
            return waitForOneEventOnOpenPort(port, deadline)
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }

    fun waitForOneEventOnOpenPort(port: DesktopSerialPort, deadlineMillis: Long): SportIdentCardEvent? {
        val stream = DesktopSportIdentFrameStream(port, maxReadBytes = MAX_FRAME_BYTES)
        return waitForOneEventOnOpenPort(stream, deadlineMillis)
    }

    private fun waitForOneEventOnOpenPort(
        stream: DesktopSportIdentFrameStream,
        deadlineMillis: Long
    ): SportIdentCardEvent? {
        while (System.currentTimeMillis() < deadlineMillis) {
            val frame = stream.nextFrame(deadlineMillis, requireValidCrc = false) ?: return null
            val event = SportIdentCardEventParser.fromFrame(frame)
            if (event != null) {
                return event
            }
        }
        return null
    }

    fun waitForInsertEventOnOpenPort(
        port: DesktopSerialPort,
        deadlineMillis: Long
    ): SportIdentCardEvent.Inserted? {
        val stream = DesktopSportIdentFrameStream(port, maxReadBytes = MAX_FRAME_BYTES)
        while (System.currentTimeMillis() < deadlineMillis) {
            when (val event = waitForOneEventOnOpenPort(stream, deadlineMillis)) {
                is SportIdentCardEvent.Inserted -> return event
                is SportIdentCardEvent.Removed -> continue
                null -> return null
            }
        }
        return null
    }

    fun waitForRemoveEventOnOpenPort(
        port: DesktopSerialPort,
        siNumber: Int?,
        deadlineMillis: Long
    ): SportIdentCardEvent.Removed? {
        val stream = DesktopSportIdentFrameStream(port, maxReadBytes = MAX_FRAME_BYTES)
        while (System.currentTimeMillis() < deadlineMillis) {
            when (val event = waitForOneEventOnOpenPort(stream, deadlineMillis)) {
                is SportIdentCardEvent.Removed ->
                    if (siNumber == null || event.siNumber == siNumber) {
                        return event
                    }
                is SportIdentCardEvent.Inserted -> continue
                null -> return null
            }
        }
        return null
    }

    companion object {
        val defaultMaxWaitMs: Long =
            System.getenv("RADIO_ORACLE_SI_CARD_EVENT_TIMEOUT_MS")?.toLongOrNull() ?: 60_000

        const val MAX_FRAME_BYTES = 512
    }
}

private fun Byte.toHexString(): String =
    "0x%02x".format(toInt() and 0xff)
