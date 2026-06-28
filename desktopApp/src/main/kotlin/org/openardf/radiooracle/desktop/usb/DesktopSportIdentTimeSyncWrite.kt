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

import java.time.LocalDateTime

fun main(args: Array<String>) {
    val requestedTime = args.timeArgument()
        ?: System.getenv("RADIO_ORACLE_SI_TIME_SYNC_AT")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let(LocalDateTime::parse)
    val toleranceSeconds = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_TOLERANCE_SECONDS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLong()
        ?: DEFAULT_TOLERANCE_SECONDS
    val currentTimeOffsetMillis = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_OFFSET_MILLIS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLong()
        ?: DesktopSportIdentTimeSyncService.DEFAULT_CURRENT_TIME_OFFSET_MILLIS
    val secondBoundaryLeadMillis = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_BOUNDARY_LEAD_MILLIS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLong()
        ?: DesktopSportIdentTimeSyncService.DEFAULT_SECOND_BOUNDARY_LEAD_MILLIS
    val maxAttempts = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_ATTEMPTS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toInt()
        ?: DesktopSportIdentTimeSyncService.DEFAULT_WRITE_ATTEMPTS
    val correctionThresholdMillis = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_CORRECTION_THRESHOLD_MILLIS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLong()
        ?: DesktopSportIdentTimeSyncService.DEFAULT_CORRECTION_THRESHOLD_MILLIS
    val alignToSecondBoundary = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_ALIGN_SECOND")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::parseEnabledFlag)
        ?: DesktopSportIdentTimeSyncService.DEFAULT_ALIGN_TO_SECOND_BOUNDARY
    val writeEnabled = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_WRITE") == "YES"
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

    println("Radio-Oracle desktop SPORTident time sync writer")
    if (!writeEnabled) {
        val dryRun = service.dryRun(requestedTime ?: LocalDateTime.now())
        println("DRY RUN: no SPORTident station writes will be sent.")
        println("Target time: ${dryRun.sourceTime}")
        println("Current-time offset for write mode: ${currentTimeOffsetMillis}ms")
        println("Second-boundary lead for write mode: ${secondBoundaryLeadMillis}ms")
        println("Write attempts for write mode: $maxAttempts")
        println("Correction threshold for write mode: ${correctionThresholdMillis}ms")
        println("Align current-time writes to second boundary: $alignToSecondBoundary")
        println()
        println("Captured SI Config+ sequence:")
        dryRun.configPlusSequence.printSteps()
        println()
        println("Hardware validation sequence decodes the F6 acknowledgement before applying the write:")
        dryRun.validatedWriteSequence.printSteps()
        println()
        println("Set RADIO_ORACLE_SI_TIME_SYNC_WRITE=YES to run the hardware write/acknowledgement validation path.")
        return
    }

    println("WRITE ENABLED: SPORTident station writes are active for this run.")
    println("Target time: ${requestedTime ?: "next computer second, final write ${secondBoundaryLeadMillis}ms early"}")
    println("Tolerance: ${toleranceSeconds}s")
    println("Current-time offset: ${currentTimeOffsetMillis}ms")
    println("Second-boundary lead: ${secondBoundaryLeadMillis}ms")
    println("Max attempts: $maxAttempts")
    println("Correction threshold: ${correctionThresholdMillis}ms")
    println("Align current-time writes to second boundary: $alignToSecondBoundary")
    requestedPort?.let { println("Port: $it") }
    val result = service.writeTimeWithReadBack(
        sourceTime = requestedTime,
        writeEnabled = true,
        toleranceSeconds = toleranceSeconds,
        currentTimeOffsetMillis = currentTimeOffsetMillis,
        secondBoundaryLeadMillis = secondBoundaryLeadMillis,
        maxAttempts = maxAttempts,
        correctionThresholdMillis = correctionThresholdMillis,
        alignToSecondBoundary = alignToSecondBoundary
    )
    println("Station serial: ${result.stationInfo.serialNumber}")
    println("Before write: ${result.beforeTime ?: "unknown"}")
    println("Confirmed time: ${result.confirmedTime ?: "unknown"}")
    println("Requested time: ${result.sourceTime}")
    println("Applied current-time offset: ${result.currentTimeOffsetMillis}ms")
    println("Second-boundary lead: ${result.secondBoundaryLeadMillis?.let { "${it}ms" } ?: "none"}")
    println("Second-boundary wait: ${result.secondBoundaryWaitMillis?.let { "${it}ms" } ?: "none"}")
    println("Attempts: ${result.attempts}")
    println("Computer time after sync: ${result.computerTimeAfterSync ?: "unknown"}")
    println("Post-sync station minus computer: ${result.confirmedStationMinusComputerMillis?.let { "${it}ms" } ?: "unknown"}")
    println("Read-back tolerance: ${result.toleranceSeconds}s")
}

private fun Array<String>.timeArgument(): LocalDateTime? =
    firstOrNull { it.startsWith("--time=") }
        ?.substringAfter("=")
        ?.let(LocalDateTime::parse)

private fun parseEnabledFlag(value: String): Boolean =
    when (value.uppercase()) {
        "0", "NO", "N", "FALSE", "OFF" -> false
        else -> true
    }

private fun List<DesktopSportIdentTimeSyncCommandStep>.printSteps() {
    forEachIndexed { index, step ->
        println(
            "${index + 1}. ${step.label}: command=${step.command.toHexByte()} " +
                "payload=${step.payload.toHexString()} frame=${step.frameBytes.toHexString()}"
        )
    }
}

private fun Byte.toHexByte(): String =
    "0x${(toInt() and 0xff).toString(16).uppercase().padStart(2, '0')}"

private fun ByteArray.toHexString(): String =
    if (isEmpty()) "(none)" else joinToString(" ") {
        (it.toInt() and 0xff).toString(16).uppercase().padStart(2, '0')
    }

private const val DEFAULT_TOLERANCE_SECONDS = 2L
