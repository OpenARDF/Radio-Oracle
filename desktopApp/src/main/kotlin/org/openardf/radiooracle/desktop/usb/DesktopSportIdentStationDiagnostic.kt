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

import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.system.measureNanoTime
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfoParser

fun main() {
    val provider = JSerialCommDesktopSerialPortProvider
    val requestedPorts = System.getenv("RADIO_ORACLE_SI_PORTS")
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: emptyList()
    val ports = if (requestedPorts.isEmpty()) {
        provider.listPorts().filter { it.info.matchesSportIdent() }.preferredSportIdentPorts()
    } else {
        requestedPorts.map(provider::getPort)
    }

    println("Radio-Oracle desktop SPORTident station diagnostic")
    if (ports.isEmpty()) {
        error("No SPORTident USB serial ports found.")
    }

    val diagnostic = DesktopSportIdentStationDiagnostic()
    val results = ports.map { port ->
        val result = diagnostic.run(port)
        println()
        println(result.summary())
        result
    }

    if (results.size > 1) {
        println()
        println("Station comparison:")
        results.drop(1).forEach { other ->
            println(DesktopSportIdentStationSettingsComparison.describe(results.first(), other))
        }
    }
}

data class DesktopSportIdentStationDiagnosticResult(
    val portInfo: DesktopSerialPortInfo,
    val baudRate: Int,
    val stationInfo: SportIdentStationInfo,
    val probeTimingsMs: List<Double>,
    val systemInfoTimingsMs: List<Double>,
    val systemInfoData: ByteArray
) {
    fun summary(): String {
        val modeLabel = stationInfo.stationModeLabel ?: "unknown"
        return buildString {
            appendLine("Port: ${portInfo.describe()}")
            appendLine("  baud=$baudRate")
            appendLine(
                "  station serial=${stationInfo.serialNumber} " +
                    "extended=${stationInfo.extendedMode} " +
                    "codeNumber=${stationInfo.stationCodeNumber ?: "unknown"} " +
                    "modeCode=${stationInfo.stationModeCode ?: "unknown"} " +
                    "mode=$modeLabel"
            )
            appendLine("  probe timing: ${probeTimingsMs.describeTimings()}")
            append("  system-info timing: ${systemInfoTimingsMs.describeTimings()}")
        }
    }
}

class DesktopSportIdentStationDiagnostic(
    private val attempts: Int = 5,
    private val readTimeoutMs: Int = 1200,
    private val writeTimeoutMs: Int = 1200,
    private val openWaitTimeMs: Int = 200
) {
    private val commandClient = DesktopSportIdentStationCommandClient(
        readTimeoutMs = readTimeoutMs,
        maxReplyBytes = SYSTEM_INFO_LONG_REPLY_BYTES
    )

    fun run(port: DesktopSerialPort): DesktopSportIdentStationDiagnosticResult {
        require(attempts > 0) { "Diagnostic attempts must be positive." }

        val baudRate = selectBaudRate(port)
        val probeTimings = mutableListOf<Double>()
        val systemInfoTimings = mutableListOf<Double>()
        var stationInfo: SportIdentStationInfo? = null
        var systemInfoData: ByteArray? = null

        repeat(attempts) {
            val measurement = measureStationInfo(port, baudRate)
            probeTimings += measurement.probeTimingMs
            systemInfoTimings += measurement.systemInfoTimingMs
            stationInfo = measurement.stationInfo
            systemInfoData = measurement.systemInfoData
        }

        return DesktopSportIdentStationDiagnosticResult(
            portInfo = port.info,
            baudRate = baudRate,
            stationInfo = stationInfo ?: error("SPORTident station did not return readable system info."),
            probeTimingsMs = probeTimings,
            systemInfoTimingsMs = systemInfoTimings,
            systemInfoData = systemInfoData ?: byteArrayOf()
        )
    }

    private fun selectBaudRate(port: DesktopSerialPort): Int {
        for (baudRate in listOf(SportIdentProtocol.BAUDRATE_HIGH, SportIdentProtocol.BAUDRATE_LOW)) {
            runCatching {
                openConfigured(port, baudRate)
                commandClient.sendCommand(port, SportIdentProtocol.PROBE_COMMAND, PROBE_PAYLOAD)
            }.getOrNull()?.let {
                closeIfOpen(port)
                return baudRate
            }
            closeIfOpen(port)
        }
        error("SPORTident station did not respond to the probe at either supported baud rate.")
    }

    private fun measureStationInfo(port: DesktopSerialPort, baudRate: Int): StationInfoMeasurement {
        try {
            openConfigured(port, baudRate)

            var probeFrame: SportIdentFrame? = null
            val probeTimingMs = measureMillis {
                probeFrame = commandClient.sendCommand(port, SportIdentProtocol.PROBE_COMMAND, PROBE_PAYLOAD)
            }
            if (probeFrame == null) {
                error("SPORTident station did not respond to the probe.")
            }

            var systemInfoFrame: SportIdentFrame? = null
            val systemInfoTimingMs = measureMillis {
                systemInfoFrame = commandClient.sendCommand(port, SportIdentProtocol.GET_SYSTEM_INFO, SYSTEM_INFO_LONG_PAYLOAD)
            }
            val frame = systemInfoFrame ?: error("SPORTident station did not return long system info.")
            val info = SportIdentStationInfoParser.fromSystemInfoFrame(frame)
                ?: error("SPORTident station returned unreadable system info.")

            return StationInfoMeasurement(
                probeTimingMs = probeTimingMs,
                systemInfoTimingMs = systemInfoTimingMs,
                stationInfo = info,
                systemInfoData = frame.data
            )
        } finally {
            closeIfOpen(port)
        }
    }

    private fun openConfigured(port: DesktopSerialPort, baudRate: Int) {
        port.configure(baudRate, readTimeoutMs, writeTimeoutMs)
        if (!port.open(openWaitTimeMs)) {
            error("Failed to open serial port ${port.info.systemPortPath}.")
        }
    }

    private fun closeIfOpen(port: DesktopSerialPort) {
        if (port.isOpen) {
            port.close()
        }
    }

    private data class StationInfoMeasurement(
        val probeTimingMs: Double,
        val systemInfoTimingMs: Double,
        val stationInfo: SportIdentStationInfo,
        val systemInfoData: ByteArray
    )

    private companion object {
        const val SYSTEM_INFO_LONG_REPLY_BYTES = 126
        val PROBE_PAYLOAD = byteArrayOf(0x4d)
        val SYSTEM_INFO_LONG_PAYLOAD = byteArrayOf(0x00, 0x75)
    }
}

private fun List<DesktopSerialPort>.preferredSportIdentPorts(): List<DesktopSerialPort> =
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

object DesktopSportIdentStationSettingsComparison {
    fun differingOffsets(
        first: ByteArray,
        second: ByteArray
    ): List<Int> =
        first.zip(second)
            .mapIndexedNotNull { index, pair ->
                if (pair.first == pair.second) null else index
            }

    fun describe(
        baseline: DesktopSportIdentStationDiagnosticResult,
        other: DesktopSportIdentStationDiagnosticResult
    ): String {
        val offsets = differingOffsets(baseline.systemInfoData, other.systemInfoData)
        val timingRatio = other.systemInfoTimingsMs.medianOrNull()
            ?.let { otherMedian ->
                baseline.systemInfoTimingsMs.medianOrNull()?.takeIf { it > 0.0 }?.let { baselineMedian ->
                    otherMedian / baselineMedian
                }
            }
        return buildString {
            appendLine("  Baseline: ${baseline.stationInfo.serialNumber} on ${baseline.portInfo.systemPortPath}")
            appendLine("  Compare:  ${other.stationInfo.serialNumber} on ${other.portInfo.systemPortPath}")
            appendLine(
                "  mode: baseline=${baseline.stationInfo.stationModeLabel ?: "unknown"} " +
                    "compare=${other.stationInfo.stationModeLabel ?: "unknown"}"
            )
            appendLine(
                "  codeNumber: baseline=${baseline.stationInfo.stationCodeNumber ?: "unknown"} " +
                    "compare=${other.stationInfo.stationCodeNumber ?: "unknown"}"
            )
            appendLine(
                "  modeCode: baseline=${baseline.stationInfo.stationModeCode ?: "unknown"} " +
                    "compare=${other.stationInfo.stationModeCode ?: "unknown"}"
            )
            if (timingRatio != null) {
                appendLine("  system-info median ratio: ${timingRatio.format(2)}x")
            }
            append("  differing system-info offsets: ")
            append(if (offsets.isEmpty()) "none" else offsets.joinToString(", "))
        }
    }
}

private fun measureMillis(block: () -> Unit): Double {
    val elapsedNanos = measureNanoTime(block)
    return elapsedNanos / 1_000_000.0
}

private fun List<Double>.describeTimings(): String =
    "min=${minOrNull()?.format(1) ?: "n/a"}ms " +
        "median=${medianOrNull()?.format(1) ?: "n/a"}ms " +
        "max=${maxOrNull()?.format(1) ?: "n/a"}ms"

private fun List<Double>.medianOrNull(): Double? {
    if (isEmpty()) {
        return null
    }
    val sorted = sorted()
    val midpoint = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[midpoint - 1] + sorted[midpoint]) / 2.0
    } else {
        sorted[midpoint]
    }
}

private fun Double.format(decimals: Int): String {
    val scale = 10.0.pow(decimals)
    return (this * scale).roundToInt().let { rounded ->
        "%.${decimals}f".format(rounded / scale)
    }
}
