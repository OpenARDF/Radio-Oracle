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

fun main() {
    val requestedPort = System.getenv("RADIO_ORACLE_SI_PORT")?.trim()?.takeIf { it.isNotEmpty() }
    val provider = JSerialCommDesktopSerialPortProvider
    val service = DesktopSportIdentTimeSyncService(
        portProvider = if (requestedPort == null) {
            provider
        } else {
            object : DesktopSerialPortProvider {
                override fun listPorts(): List<DesktopSerialPort> = listOf(provider.getPort(requestedPort))
                override fun getPort(systemPortPath: String): DesktopSerialPort = provider.getPort(systemPortPath)
            }
        }
    )

    println("Radio-Oracle desktop SPORTident time sync inspection")
    val inspection = service.inspectDownloadStation()
    println("Status: ${inspection.statusText}")
    inspection.portInfo?.let { println("Port: ${it.describe()}") }
    inspection.baudRate?.let { println("Baud: $it") }
    inspection.stationInfo?.let { station ->
        println("Station serial: ${station.serialNumber}")
        println("Station code: ${station.stationCodeNumber ?: "unknown"}")
        println("Station mode: ${station.stationModeLabel ?: "unknown"}")
        println("Extended mode: ${station.extendedMode}")
    }
    println("Can sync time: ${inspection.canSyncTime}")
    inspection.disabledReason?.let { println("Disabled reason: $it") }
}
