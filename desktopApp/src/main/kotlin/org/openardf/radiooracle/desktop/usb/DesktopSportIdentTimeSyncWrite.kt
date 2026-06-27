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
        ?: LocalDateTime.now()
    val toleranceSeconds = System.getenv("RADIO_ORACLE_SI_TIME_SYNC_TOLERANCE_SECONDS")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLong()
        ?: DEFAULT_TOLERANCE_SECONDS
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
        val dryRun = service.dryRun(requestedTime)
        println("DRY RUN: no SPORTident station writes will be sent.")
        println("Target time: ${dryRun.sourceTime}")
        println()
        println("Captured SI Config+ sequence:")
        dryRun.configPlusSequence.printSteps()
        println()
        println("Hardware validation sequence adds a read-back step before exit:")
        dryRun.validatedWriteSequence.printSteps()
        println()
        println("Set RADIO_ORACLE_SI_TIME_SYNC_WRITE=YES to run the hardware write/read-back path.")
        return
    }

    println("WRITE ENABLED: SPORTident station writes are active for this run.")
    println("Target time: $requestedTime")
    println("Tolerance: ${toleranceSeconds}s")
    requestedPort?.let { println("Port: $it") }
    val result = service.writeTimeWithReadBack(
        sourceTime = requestedTime,
        writeEnabled = true,
        toleranceSeconds = toleranceSeconds
    )
    println("Station serial: ${result.stationInfo.serialNumber}")
    println("Before write: ${result.beforeTime ?: "unknown"}")
    println("Confirmed time: ${result.confirmedTime ?: "unknown"}")
    println("Requested time: ${result.sourceTime}")
    println("Read-back tolerance: ${result.toleranceSeconds}s")
}

private fun Array<String>.timeArgument(): LocalDateTime? =
    firstOrNull { it.startsWith("--time=") }
        ?.substringAfter("=")
        ?.let(LocalDateTime::parse)

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
