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
            SerialPort.TIMEOUT_READ_SEMI_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
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
