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

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfoParser

data class DesktopSportIdentTimeSyncInspection(
    val portInfo: DesktopSerialPortInfo?,
    val baudRate: Int?,
    val stationInfo: SportIdentStationInfo?,
    val statusText: String,
    val canSyncTime: Boolean,
    val disabledReason: String?
) {
    companion object {
        fun disconnected(): DesktopSportIdentTimeSyncInspection =
            DesktopSportIdentTimeSyncInspection(
                portInfo = null,
                baudRate = null,
                stationInfo = null,
                statusText = "No SPORTident USB station detected.",
                canSyncTime = false,
                disabledReason = "Connect a SPORTident download station before syncing time."
            )
    }
}

data class DesktopSportIdentTimeSyncResult(
    val stationInfo: SportIdentStationInfo,
    val sourceTime: LocalDateTime,
    val confirmedTime: LocalDateTime?,
    val beforeTime: LocalDateTime?,
    val toleranceSeconds: Long
)

internal data class DesktopSportIdentTimeSyncDryRun(
    val sourceTime: LocalDateTime,
    val configPlusSequence: List<DesktopSportIdentTimeSyncCommandStep>,
    val validatedWriteSequence: List<DesktopSportIdentTimeSyncCommandStep>
)

internal class DesktopSportIdentTimeSyncService(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connect(it)
    },
    private val commandClient: DesktopSportIdentStationCommandClient = DesktopSportIdentStationCommandClient(
        readTimeoutMs = READ_TIMEOUT_MS,
        maxReplyBytes = MAX_REPLY_BYTES
    )
) {
    fun inspectDownloadStation(): DesktopSportIdentTimeSyncInspection {
        val port = portProvider.listPorts().firstOrNull { it.info.matchesSportIdent() }
            ?: return DesktopSportIdentTimeSyncInspection.disconnected()

        return runCatching {
            val connection = connectStation(port)
            val stationInfo = connection.stationInfo
            val modeLabel = stationInfo.stationModeLabel ?: "unknown"
            val canSyncTime = stationInfo.canRelayTimeSync()
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = connection.baudRate,
                stationInfo = stationInfo,
                statusText = "SI station ${stationInfo.serialNumber} connected in $modeLabel mode.",
                canSyncTime = canSyncTime,
                disabledReason = if (canSyncTime) {
                    null
                } else {
                    "Configure the attached SPORTident station in SI MASTER mode before syncing time."
                }
            )
        }.getOrElse { error ->
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = null,
                stationInfo = null,
                statusText = "SI station inspection failed: ${error.message ?: error::class.simpleName}",
                canSyncTime = false,
                disabledReason = "Resolve the station connection error before syncing time."
            )
        }
    }

    fun syncTime(sourceTime: LocalDateTime = LocalDateTime.now()): DesktopSportIdentTimeSyncResult {
        return writeTimeWithReadBack(
            sourceTime = sourceTime,
            writeEnabled = true,
            toleranceSeconds = DEFAULT_TOLERANCE_SECONDS
        )
    }

    fun dryRun(sourceTime: LocalDateTime = LocalDateTime.now()): DesktopSportIdentTimeSyncDryRun {
        val normalizedTime = sourceTime.truncatedTo(ChronoUnit.SECONDS)
        return DesktopSportIdentTimeSyncDryRun(
            sourceTime = normalizedTime,
            configPlusSequence = DesktopSportIdentTimeSyncProtocol.configPlusWriteSequence(normalizedTime),
            validatedWriteSequence = DesktopSportIdentTimeSyncProtocol.validatedWriteSequence(normalizedTime)
        )
    }

    fun writeTimeWithReadBack(
        sourceTime: LocalDateTime = LocalDateTime.now(),
        writeEnabled: Boolean,
        toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS
    ): DesktopSportIdentTimeSyncResult {
        require(writeEnabled) {
            "SPORTident time sync writes require explicit hardware opt-in."
        }
        require(toleranceSeconds >= 0) {
            "Time sync tolerance must not be negative."
        }

        val targetTime = sourceTime.truncatedTo(ChronoUnit.SECONDS)
        val port = portProvider.listPorts().firstOrNull { it.info.matchesSportIdent() }
            ?: error("No SPORTident USB station detected.")

        try {
            val baudRate = selectBaudRate(port)
            configure(port, baudRate)
            if (!port.open(OPEN_WAIT_TIME_MS)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.enterRemoteModeStep()
            )

            val systemInfoFrame = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.readCompatibleSystemInfoStep(),
                attempts = READ_ONLY_COMMAND_ATTEMPTS,
                failureMessage = "Remote/coupled SPORTident station did not answer system-info read. " +
                    "Confirm the target station is awake and coupled to the download station."
            )
            val stationInfo = SportIdentStationInfoParser.fromSystemInfoFrame(systemInfoFrame)
                ?: error("SPORTident station returned unreadable system info.")

            val beforeTime = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.readStationTimeStep("Read station time before write")
            ).data.decodeStationTime("before-write station time").dateTime

            val confirmedTime = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.writeStationTimeStep(targetTime)
            ).data.decodeStationTime("write acknowledgement station time").dateTime

            val deltaSeconds = abs(Duration.between(targetTime, confirmedTime).seconds)
            if (deltaSeconds > toleranceSeconds) {
                error(
                    "SPORTident station time read-back $confirmedTime differed from requested " +
                        "$targetTime by ${deltaSeconds}s, tolerance ${toleranceSeconds}s."
                )
            }

            requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.applyStationTimeStep()
            )

            return DesktopSportIdentTimeSyncResult(
                stationInfo = stationInfo,
                sourceTime = targetTime,
                confirmedTime = confirmedTime,
                beforeTime = beforeTime,
                toleranceSeconds = toleranceSeconds
            )
        } finally {
            if (port.isOpen) {
                runCatching {
                    val exitStep = DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep()
                    commandClient.sendCommand(
                        port = port,
                        command = exitStep.command,
                        data = exitStep.payload
                    )
                }
                port.close()
            }
        }
    }

    private fun selectBaudRate(port: DesktopSerialPort): Int {
        for (baudRate in listOf(SportIdentProtocol.BAUDRATE_HIGH, SportIdentProtocol.BAUDRATE_LOW)) {
            runCatching {
                configure(port, baudRate)
                if (!port.open(OPEN_WAIT_TIME_MS)) {
                    error("Failed to open serial port ${port.info.systemPortPath}.")
                }
                commandClient.sendCommand(
                    port = port,
                    command = DesktopSportIdentTimeSyncProtocol.enterRemoteModeStep().command,
                    data = DesktopSportIdentTimeSyncProtocol.enterRemoteModeStep().payload
                )
            }.getOrNull()?.let {
                port.close()
                return baudRate
            }
            if (port.isOpen) {
                port.close()
            }
        }
        error("SPORTident station did not respond to the time-sync remote-mode probe.")
    }

    private fun configure(port: DesktopSerialPort, baudRate: Int) {
        port.configure(baudRate, READ_TIMEOUT_MS, WRITE_TIMEOUT_MS)
    }

    private fun requireReply(
        port: DesktopSerialPort,
        step: DesktopSportIdentTimeSyncCommandStep,
        attempts: Int = 1,
        failureMessage: String = "SPORTident station did not reply to ${step.label}."
    ): org.openardf.radiooracle.shared.sportident.SportIdentFrame {
        require(attempts > 0) { "SPORTident command attempts must be positive." }
        repeat(attempts) {
            commandClient.sendCommand(
                port = port,
                command = step.command,
                data = step.payload
            )?.let { return it }
        }
        error(failureMessage)
    }

    private fun ByteArray.decodeStationTime(context: String): DesktopSportIdentStationTime =
        DesktopSportIdentStationTimeCodec.decodePayload(this)
            ?: error("SPORTident station returned unreadable $context.")

    private fun SportIdentStationInfo.canRelayTimeSync(): Boolean =
        stationModeLabel?.startsWith("SI MASTER") == true

    private companion object {
        const val READ_TIMEOUT_MS = 1200
        const val WRITE_TIMEOUT_MS = 1200
        const val OPEN_WAIT_TIME_MS = 200
        const val MAX_REPLY_BYTES = 256
        const val DEFAULT_TOLERANCE_SECONDS = 2L
        const val READ_ONLY_COMMAND_ATTEMPTS = 3
    }
}
