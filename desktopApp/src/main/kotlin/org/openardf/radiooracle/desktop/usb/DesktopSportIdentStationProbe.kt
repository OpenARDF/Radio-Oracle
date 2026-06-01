package org.openardf.radiooracle.desktop.usb

import org.openardf.radiooracle.shared.sportident.SportIdentFrameParser
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfoParser

data class DesktopSportIdentProbeResult(
    val baudRate: Int,
    val reply: ByteArray
)

data class DesktopSportIdentStationConnection(
    val baudRate: Int,
    val probeReply: ByteArray,
    val stationInfo: SportIdentStationInfo
)

class DesktopSportIdentStationProbe(
    private val readTimeoutMs: Int = 1200,
    private val writeTimeoutMs: Int = 1200,
    private val openWaitTimeMs: Int = 200
) {
    fun probe(port: DesktopSerialPort): DesktopSportIdentProbeResult {
        try {
            configure(port, SportIdentProtocol.BAUDRATE_HIGH)
            if (!port.open(openWaitTimeMs)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            val highBaudReply = sendProbe(port)
            if (highBaudReply.isNotEmpty()) {
                return DesktopSportIdentProbeResult(SportIdentProtocol.BAUDRATE_HIGH, highBaudReply)
            }

            configure(port, SportIdentProtocol.BAUDRATE_LOW)
            val lowBaudReply = sendProbe(port)
            if (lowBaudReply.isNotEmpty()) {
                return DesktopSportIdentProbeResult(SportIdentProtocol.BAUDRATE_LOW, lowBaudReply)
            }

            error("SPORTident station did not respond to the probe at either supported baud rate.")
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }

    fun connect(port: DesktopSerialPort): DesktopSportIdentStationConnection {
        try {
            return connectKeepingPortOpen(port)
        } finally {
            if (port.isOpen) {
                port.close()
            }
        }
    }

    fun connectKeepingPortOpen(port: DesktopSerialPort): DesktopSportIdentStationConnection {
        try {
            configure(port, SportIdentProtocol.BAUDRATE_HIGH)
            if (!port.open(openWaitTimeMs)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            val highBaudReply = sendCommand(port, SportIdentProtocol.PROBE_COMMAND, PROBE_PAYLOAD)
            if (highBaudReply != null) {
                return DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_HIGH,
                    probeReply = highBaudReply.raw,
                    stationInfo = readStationInfo(port)
                )
            }

            configure(port, SportIdentProtocol.BAUDRATE_LOW)
            val lowBaudReply = sendCommand(port, SportIdentProtocol.PROBE_COMMAND, PROBE_PAYLOAD)
            if (lowBaudReply != null) {
                return DesktopSportIdentStationConnection(
                    baudRate = SportIdentProtocol.BAUDRATE_LOW,
                    probeReply = lowBaudReply.raw,
                    stationInfo = readStationInfo(port)
                )
            }

            error("SPORTident station did not respond to the probe at either supported baud rate.")
        } catch (error: Throwable) {
            if (port.isOpen) {
                port.close()
            }
            throw error
        }
    }

    private fun configure(port: DesktopSerialPort, baudRate: Int) {
        port.configure(baudRate, readTimeoutMs, writeTimeoutMs)
    }

    private fun sendProbe(port: DesktopSerialPort): ByteArray {
        return sendCommand(port, SportIdentProtocol.PROBE_COMMAND, PROBE_PAYLOAD)?.raw ?: byteArrayOf()
    }

    private fun readStationInfo(port: DesktopSerialPort): SportIdentStationInfo {
        val longInfo = sendCommand(port, SportIdentProtocol.GET_SYSTEM_INFO, byteArrayOf(0x00, 0x75))
            ?.let(SportIdentStationInfoParser::fromSystemInfoFrame)
        if (longInfo != null) {
            return longInfo
        }

        val shortInfo = sendCommand(port, SportIdentProtocol.GET_SYSTEM_INFO, byteArrayOf(0x00, 0x07))
            ?.let(SportIdentStationInfoParser::fromSystemInfoFrame)
        return shortInfo ?: error("SPORTident station did not return readable system info.")
    }

    private fun sendCommand(
        port: DesktopSerialPort,
        command: Byte,
        data: ByteArray
    ): org.openardf.radiooracle.shared.sportident.SportIdentFrame? {
        val request = SportIdentProtocol.buildExtendedMessage(command, data)
        val written = port.write(request)
        if (written != request.size) {
            return null
        }

        val rawReply = port.read(MAX_REPLY_BYTES)
        return SportIdentFrameParser.firstFrame(
            rawReply,
            commandFilter = command
        )
    }

    private companion object {
        const val MAX_REPLY_BYTES = 256
        val PROBE_PAYLOAD = byteArrayOf(0x4d)
    }
}
