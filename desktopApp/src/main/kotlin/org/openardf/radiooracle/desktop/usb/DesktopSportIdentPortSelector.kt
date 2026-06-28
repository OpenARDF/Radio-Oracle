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

enum class DesktopSportIdentPortDiscoveryMode {
    SPORTIDENT_USB_ONLY,
    PROBE_FTDI_ADAPTERS
}

interface DesktopSportIdentPortDiscoverySettings {
    fun sportIdentPortDiscoveryMode(): DesktopSportIdentPortDiscoveryMode
    fun rememberedSportIdentFtdiPortPath(): String?
    fun rememberSportIdentFtdiPortPath(portPath: String)
}

object DesktopSportIdentDefaultPortDiscoverySettings : DesktopSportIdentPortDiscoverySettings {
    override fun sportIdentPortDiscoveryMode(): DesktopSportIdentPortDiscoveryMode =
        DesktopSportIdentPortDiscoveryMode.SPORTIDENT_USB_ONLY

    override fun rememberedSportIdentFtdiPortPath(): String? = null

    override fun rememberSportIdentFtdiPortPath(portPath: String) = Unit
}

class DesktopSportIdentPortSelector(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val discoverySettings: DesktopSportIdentPortDiscoverySettings =
        DesktopSportIdentDefaultPortDiscoverySettings,
    private val probeSportIdentStation: (DesktopSerialPort) -> Boolean = { port ->
        runCatching { DesktopSportIdentStationProbe().probe(port) }.isSuccess
    }
) {
    fun selectPort(): DesktopSerialPort? {
        val mode = discoverySettings.sportIdentPortDiscoveryMode()
        val ports = portProvider.listPorts().preferredSerialPorts()
        val candidates = when (mode) {
            DesktopSportIdentPortDiscoveryMode.SPORTIDENT_USB_ONLY ->
                ports.filter { it.info.matchesSportIdent() }
            DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS ->
                ftdiProbeCandidates(ports)
        }

        for (port in candidates) {
            if (port.info.matchesSportIdent()) {
                return port
            }
            if (mode == DesktopSportIdentPortDiscoveryMode.PROBE_FTDI_ADAPTERS && port.info.matchesFtdiAdapter()) {
                if (probeSportIdentStation(port)) {
                    discoverySettings.rememberSportIdentFtdiPortPath(port.info.systemPortPath)
                    return port
                }
            }
        }
        return null
    }

    private fun ftdiProbeCandidates(ports: List<DesktopSerialPort>): List<DesktopSerialPort> {
        val rememberedPath = discoverySettings.rememberedSportIdentFtdiPortPath()
            ?.takeIf { it.isNotBlank() }
        val remembered = ports.filter { it.info.systemPortPath == rememberedPath && it.info.matchesFtdiAdapter() }
        val sportIdentUsb = ports.filter { it.info.matchesSportIdent() }
        val remainingFtdi = ports.filter { it.info.matchesFtdiAdapter() && it.info.systemPortPath != rememberedPath }
        return (remembered + sportIdentUsb + remainingFtdi).distinctBy { it.info.systemPortPath }
    }
}

fun DesktopSerialPortInfo.matchesFtdiAdapter(): Boolean =
    vendorId == FTDI_VENDOR_ID ||
        descriptivePortName.contains("FTDI", ignoreCase = true)

private fun List<DesktopSerialPort>.preferredSerialPorts(): List<DesktopSerialPort> =
    groupBy { it.info.serialNumber?.takeUnless { serial -> serial.isBlank() || serial == "Unknown" } ?: it.info.systemPortPath }
        .values
        .map { candidates ->
            candidates.sortedWith(
                compareByDescending<DesktopSerialPort> { it.info.systemPortPath.contains("/cu.") }
                    .thenBy { it.info.descriptivePortName.contains("(Dial-In)") }
                    .thenBy { it.info.systemPortPath }
            ).first()
        }
        .sortedBy { it.info.systemPortPath }

private const val FTDI_VENDOR_ID = 0x0403
