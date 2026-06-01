package org.openardf.radiooracle.desktop.usb

import com.fazecast.jSerialComm.SerialPort

object JSerialCommDesktopSerialPortProvider : DesktopSerialPortProvider {
    override fun listPorts(): List<DesktopSerialPort> =
        SerialPort.getCommPorts().map(::JSerialCommDesktopSerialPort)

    override fun getPort(systemPortPath: String): DesktopSerialPort =
        JSerialCommDesktopSerialPort(SerialPort.getCommPort(systemPortPath))
}

private class JSerialCommDesktopSerialPort(
    private val port: SerialPort
) : DesktopSerialPort {
    override val info: DesktopSerialPortInfo
        get() = DesktopSerialPortInfo(
            systemPortPath = port.systemPortPath,
            descriptivePortName = port.descriptivePortName,
            vendorId = port.vendorID,
            productId = port.productID,
            serialNumber = port.serialNumber
        )

    override val isOpen: Boolean
        get() = port.isOpen

    override fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int) {
        port.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        port.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
            readTimeoutMs,
            writeTimeoutMs
        )
    }

    override fun open(waitTimeMillis: Int): Boolean =
        port.openPort(waitTimeMillis)

    override fun close() {
        port.closePort()
    }

    override fun write(bytes: ByteArray): Int =
        port.writeBytes(bytes, bytes.size)

    override fun read(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        val read = port.readBytes(buffer, buffer.size)
        return if (read > 0) buffer.copyOf(read) else byteArrayOf()
    }
}
