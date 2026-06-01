package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentCardEvent
import org.openardf.radiooracle.shared.sportident.SportIdentCardEventParser
import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser

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
        while (System.currentTimeMillis() < deadlineMillis) {
            val raw = port.read(MAX_FRAME_BYTES)
            if (raw.isEmpty()) {
                continue
            }

            val frame = SportIdentFrameParser.firstFrame(raw, requireValidCrc = true)
            val event = frame?.let(SportIdentCardEventParser::fromFrame)
            if (event != null) {
                return event
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
