package org.openardf.radiooracle.desktop.usb

import com.fazecast.jSerialComm.SerialPort
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

private const val READ_TIMEOUT_MS = 1200
private const val WRITE_TIMEOUT_MS = 1200

fun main(args: Array<String>) {
    val requestedPort = args.firstOrNull() ?: System.getenv("RADIO_ORACLE_SI_PORT")
    val ports = SerialPort.getCommPorts().toList()

    println("Radio-Oracle desktop SPORTident serial probe")
    println("Detected serial ports:")
    if (ports.isEmpty()) {
        println("- none")
    } else {
        ports.forEach { port ->
            println("- ${port.describe()}")
        }
    }

    val port = if (requestedPort.isNullOrBlank()) {
        ports.firstOrNull {
            SportIdentUsbDevice.matches(it.vendorID, it.productID)
        } ?: error("No SPORTident USB serial port found.")
    } else {
        SerialPort.getCommPort(requestedPort)
    }

    println("Using serial port: ${port.describe()}")
    probeSportIdentStation(port)
}

private fun probeSportIdentStation(port: SerialPort) {
    try {
        configure(port, SportIdentProtocol.BAUDRATE_HIGH)
        if (!port.openPort(200)) {
            error("Failed to open serial port ${port.systemPortPath}.")
        }

        val highBaudReply = sendProbe(port)
        if (highBaudReply.isNotEmpty()) {
            println("SPORTident probe OK at ${SportIdentProtocol.BAUDRATE_HIGH} baud: ${highBaudReply.toHexString()}")
            return
        }

        configure(port, SportIdentProtocol.BAUDRATE_LOW)
        val lowBaudReply = sendProbe(port)
        if (lowBaudReply.isNotEmpty()) {
            println("SPORTident probe OK at ${SportIdentProtocol.BAUDRATE_LOW} baud: ${lowBaudReply.toHexString()}")
            return
        }

        error("SPORTident station did not respond to the probe at either supported baud rate.")
    } finally {
        if (port.isOpen) {
            port.closePort()
        }
    }
}

private fun configure(port: SerialPort, baudRate: Int) {
    port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
    port.setComPortTimeouts(
        SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
        READ_TIMEOUT_MS,
        WRITE_TIMEOUT_MS
    )
}

private fun sendProbe(port: SerialPort): ByteArray {
    val request = SportIdentProtocol.buildExtendedMessage(
        SportIdentProtocol.PROBE_COMMAND,
        byteArrayOf(0x4d)
    )
    val written = port.writeBytes(request, request.size)
    if (written != request.size) {
        return byteArrayOf()
    }

    val buffer = ByteArray(256)
    val read = port.readBytes(buffer, buffer.size)
    return if (read > 0) buffer.copyOf(read) else byteArrayOf()
}

private fun SerialPort.describe(): String {
    val ids = if (vendorID >= 0 && productID >= 0) {
        " VID:PID=${vendorID}:${productID}"
    } else {
        ""
    }
    val serial = serialNumber
        .takeUnless { it.isNullOrBlank() || it == "Unknown" }
        ?.let { " serial=$it" }
        ?: ""
    return "$systemPortPath ($descriptivePortName)$ids$serial"
}

private fun ByteArray.toHexString(): String {
    return joinToString(" ") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
