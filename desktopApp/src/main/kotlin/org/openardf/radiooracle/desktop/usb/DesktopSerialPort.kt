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

import org.openardf.radiooracle.shared.sportident.SportIdentUsbDevice

data class DesktopSerialPortInfo(
    val systemPortPath: String,
    val descriptivePortName: String,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?
) {
    fun matchesSportIdent(): Boolean =
        SportIdentUsbDevice.matches(vendorId, productId)

    fun describe(): String {
        val ids = if (vendorId >= 0 && productId >= 0) {
            " VID:PID=$vendorId:$productId"
        } else {
            ""
        }
        val serial = serialNumber
            ?.takeUnless { it.isBlank() || it == "Unknown" }
            ?.let { " serial=$it" }
            ?: ""
        return "$systemPortPath ($descriptivePortName)$ids$serial"
    }
}

interface DesktopSerialPort {
    val info: DesktopSerialPortInfo
    val isOpen: Boolean

    fun configure(baudRate: Int, readTimeoutMs: Int, writeTimeoutMs: Int)
    fun open(waitTimeMillis: Int): Boolean
    fun close()
    fun write(bytes: ByteArray): Int
    fun read(maxBytes: Int): ByteArray
}

interface DesktopSerialPortProvider {
    fun listPorts(): List<DesktopSerialPort>
    fun getPort(systemPortPath: String): DesktopSerialPort
}
