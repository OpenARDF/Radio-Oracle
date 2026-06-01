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
