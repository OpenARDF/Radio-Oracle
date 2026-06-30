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
        printStationDiagnostics(station)
    }
    inspection.coupledStationClock?.let { clock ->
        println("Coupled station serial: ${clock.stationInfo.serialNumber}")
        println("Coupled station code: ${clock.stationInfo.stationCodeNumber ?: "unknown"}")
        printStationDiagnostics(clock.stationInfo)
        println("Coupled station time: ${clock.stationTime}")
        println("Computer time at inspection: ${clock.computerTime}")
        println("Coupled station minus computer: ${clock.stationMinusComputerMillis}ms")
    }
    inspection.coupledStationInspectionError?.let { println("Coupled station inspection: $it") }
    println("Can sync time: ${inspection.canSyncTime}")
    inspection.disabledReason?.let { println("Disabled reason: $it") }
}

private fun printStationDiagnostics(station: org.openardf.radiooracle.shared.sportident.SportIdentStationInfo) {
    station.modelName?.let { println("Station model: $it") }
    station.modelId?.let { println("Station model ID: 0x${it.toString(16).uppercase().padStart(4, '0')}") }
    station.firmwareVersion?.let { println("Station firmware: $it") }
    station.buildDate?.let { println("Station build date: $it") }
    station.batteryDate?.let { println("Station battery date: $it") }
    station.batteryVoltage?.let { println("Station battery voltage: ${"%.2f".format(it)}V") }
    station.memorySizeKb?.let { println("Station memory: ${it}KB") }
    station.activeTimeMinutes?.let { println("Station active time: ${it} minutes") }
    station.protocolByte?.let { println("Station protocol byte: 0x${it.toString(16).uppercase().padStart(2, '0')}") }
}
