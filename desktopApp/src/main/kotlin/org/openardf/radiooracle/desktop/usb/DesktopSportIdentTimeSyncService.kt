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
    val disabledReason: String?,
    val coupledStationClock: DesktopSportIdentCoupledStationClock?,
    val requiresCoupledStation: Boolean,
    val coupledStationInspectionError: String?
) {
    companion object {
        fun disconnected(): DesktopSportIdentTimeSyncInspection =
            DesktopSportIdentTimeSyncInspection(
                portInfo = null,
                baudRate = null,
                stationInfo = null,
                statusText = "No SPORTident USB station detected.",
                canSyncTime = false,
                disabledReason = "Connect a SPORTident download station before syncing time.",
                coupledStationClock = null,
                requiresCoupledStation = false,
                coupledStationInspectionError = null
            )
    }
}

data class DesktopSportIdentCoupledStationClock(
    val stationInfo: SportIdentStationInfo,
    val stationTime: LocalDateTime,
    val computerTime: LocalDateTime,
    val stationMinusComputerMillis: Long
)

data class DesktopSportIdentTimeSyncResult(
    val stationInfo: SportIdentStationInfo,
    val sourceTime: LocalDateTime,
    val confirmedTime: LocalDateTime?,
    val beforeTime: LocalDateTime?,
    val toleranceSeconds: Long,
    val computerTimeAfterSync: LocalDateTime?,
    val confirmedStationMinusComputerMillis: Long?,
    val currentTimeOffsetMillis: Long,
    val attempts: Int,
    val secondBoundaryWaitMillis: Long?,
    val secondBoundaryLeadMillis: Long?,
    val stationPowerStateWrite: DesktopSportIdentStationPowerStateWriteResult?
)

data class DesktopSportIdentStationPowerStateWriteResult(
    val confirmed: Boolean?,
    val message: String
)

private data class DesktopSportIdentPostSyncTimeEstimate(
    val computerTime: LocalDateTime,
    val stationMinusComputerMillis: Long
)

private enum class DesktopSportIdentTimeSyncAccessMode {
    DIRECT_ATTACHED,
    RELAY_COUPLED
}

private data class DesktopSportIdentPreparedTimeSyncPort(
    val port: DesktopSerialPort,
    val baudRate: Int,
    val accessMode: DesktopSportIdentTimeSyncAccessMode
)

internal data class DesktopSportIdentTimeSyncDryRun(
    val sourceTime: LocalDateTime,
    val configPlusSequence: List<DesktopSportIdentTimeSyncCommandStep>,
    val validatedWriteSequence: List<DesktopSportIdentTimeSyncCommandStep>
)

internal class DesktopSportIdentTimeSyncService(
    private val portProvider: DesktopSerialPortProvider = JSerialCommDesktopSerialPortProvider,
    private val portSelector: DesktopSportIdentPortSelector = DesktopSportIdentPortSelector(portProvider),
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connect(it)
    },
    private val commandClient: DesktopSportIdentStationCommandClient = DesktopSportIdentStationCommandClient(
        readTimeoutMs = READ_TIMEOUT_MS,
        maxReplyBytes = MAX_REPLY_BYTES
    ),
    private val currentTime: () -> LocalDateTime = { LocalDateTime.now() },
    private val sleepMillis: (Long) -> Unit = { Thread.sleep(it) }
) {
    fun inspectDownloadStation(): DesktopSportIdentTimeSyncInspection {
        val port = portSelector.selectPort()
            ?: return DesktopSportIdentTimeSyncInspection.disconnected()

        return runCatching {
            val connection = connectStation(port)
            val stationInfo = connection.stationInfo
            val modeLabel = stationInfo.stationModeLabel ?: "unknown"
            val accessMode = port.timeSyncAccessMode(stationInfo)
            val canSyncTime = true
            val coupledStationClockResult = readStationClockForInspection(
                port = port,
                baudRate = connection.baudRate,
                accessMode = accessMode
            )
            val coupledStationClock = coupledStationClockResult?.getOrNull()
            val coupledStationInspectionError = if (coupledStationClock == null) {
                if (accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                    "No coupled station found."
                } else {
                    coupledStationClockResult?.exceptionOrNull()?.message
                        ?: "SPORTident station time read failed."
                }
            } else {
                null
            }
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = connection.baudRate,
                stationInfo = stationInfo,
                statusText = "SI station ${stationInfo.serialNumber} connected in $modeLabel mode.",
                canSyncTime = canSyncTime,
                disabledReason = null,
                coupledStationClock = coupledStationClock,
                requiresCoupledStation = accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED,
                coupledStationInspectionError = coupledStationInspectionError
            )
        }.getOrElse { error ->
            DesktopSportIdentTimeSyncInspection(
                portInfo = port.info,
                baudRate = null,
                stationInfo = null,
                statusText = "SI station inspection failed: ${error.message ?: error::class.simpleName}",
                canSyncTime = false,
                disabledReason = "Resolve the station connection error before syncing time.",
                coupledStationClock = null,
                requiresCoupledStation = false,
                coupledStationInspectionError = null
            )
        }
    }

    fun syncTime(
        sourceTime: LocalDateTime? = null,
        putStationToSleepAfterSync: Boolean = false
    ): DesktopSportIdentTimeSyncResult {
        return writeTimeWithReadBack(
            sourceTime = sourceTime,
            writeEnabled = true,
            toleranceSeconds = DEFAULT_TOLERANCE_SECONDS,
            putStationToSleepAfterSync = putStationToSleepAfterSync
        )
    }

    fun sleepStation(): DesktopSportIdentStationPowerStateWriteResult =
        attemptStationPowerOffInSeparateTransaction()

    fun dryRun(sourceTime: LocalDateTime = LocalDateTime.now()): DesktopSportIdentTimeSyncDryRun {
        val normalizedTime = sourceTime.truncatedTo(ChronoUnit.MILLIS)
        return DesktopSportIdentTimeSyncDryRun(
            sourceTime = normalizedTime,
            configPlusSequence = DesktopSportIdentTimeSyncProtocol.configPlusWriteSequence(normalizedTime),
            validatedWriteSequence = DesktopSportIdentTimeSyncProtocol.validatedWriteSequence(normalizedTime)
        )
    }

    fun writeTimeWithReadBack(
        sourceTime: LocalDateTime? = null,
        writeEnabled: Boolean,
        toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS,
        currentTimeOffsetMillis: Long = DEFAULT_CURRENT_TIME_OFFSET_MILLIS,
        secondBoundaryLeadMillis: Long = DEFAULT_SECOND_BOUNDARY_LEAD_MILLIS,
        maxAttempts: Int = DEFAULT_WRITE_ATTEMPTS,
        correctionThresholdMillis: Long = DEFAULT_CORRECTION_THRESHOLD_MILLIS,
        alignToSecondBoundary: Boolean = DEFAULT_ALIGN_TO_SECOND_BOUNDARY,
        putStationToSleepAfterSync: Boolean = false
    ): DesktopSportIdentTimeSyncResult {
        require(writeEnabled) {
            "SPORTident time sync writes require explicit hardware opt-in."
        }
        require(toleranceSeconds >= 0) {
            "Time sync tolerance must not be negative."
        }
        require(maxAttempts > 0) {
            "Time sync attempts must be positive."
        }
        require(secondBoundaryLeadMillis in 0..999) {
            "Time sync second-boundary lead must be between 0ms and 999ms."
        }
        require(correctionThresholdMillis >= 0) {
            "Time sync correction threshold must not be negative."
        }

        var lastFailure: Throwable? = null
        var leadForAttempt = secondBoundaryLeadMillis
        repeat(maxAttempts) { attemptIndex ->
            var writeCommandStarted = false
            try {
                val initialResult = writeTimeWithReadBackOnce(
                    sourceTime = sourceTime,
                    toleranceSeconds = toleranceSeconds,
                    currentTimeOffsetMillis = currentTimeOffsetMillis,
                    secondBoundaryLeadMillis = leadForAttempt,
                    alignToSecondBoundary = alignToSecondBoundary,
                    attemptCount = attemptIndex + 1,
                    putStationToSleepAfterSync = putStationToSleepAfterSync,
                    correctionThresholdMillis = correctionThresholdMillis,
                    canUseCorrectionAttempt = attemptIndex < maxAttempts - 1,
                    onWriteCommandStarted = { writeCommandStarted = true }
                )
                val result = initialResult.withFallbackPostSyncEstimate()
                val postSyncDeltaMillis = result.confirmedStationMinusComputerMillis
                if (
                    sourceTime == null &&
                    postSyncDeltaMillis != null &&
                    abs(postSyncDeltaMillis) > correctionThresholdMillis &&
                    attemptIndex < maxAttempts - 1
                ) {
                    leadForAttempt = (leadForAttempt - postSyncDeltaMillis).coerceIn(0L, 999L)
                    sleepMillis(RETRY_DELAY_MS)
                } else {
                    return result.withOptionalSeparateStationPowerOff(putStationToSleepAfterSync)
                }
            } catch (error: Throwable) {
                if (writeCommandStarted || attemptIndex == maxAttempts - 1) {
                    throw error
                }
                lastFailure = error
                sleepMillis(RETRY_DELAY_MS)
            }
        }
        throw lastFailure ?: IllegalStateException("SPORTident time sync failed.")
    }

    private fun writeTimeWithReadBackOnce(
        sourceTime: LocalDateTime?,
        toleranceSeconds: Long,
        currentTimeOffsetMillis: Long,
        secondBoundaryLeadMillis: Long,
        alignToSecondBoundary: Boolean,
        attemptCount: Int,
        putStationToSleepAfterSync: Boolean,
        correctionThresholdMillis: Long,
        canUseCorrectionAttempt: Boolean,
        onWriteCommandStarted: () -> Unit
    ): DesktopSportIdentTimeSyncResult {
        val preparedPort = prepareTimeSyncPort()
        val port = preparedPort.port

        try {
            configure(port, preparedPort.baudRate)
            if (!port.open(OPEN_WAIT_TIME_MS)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            requireReply(
                port = port,
                step = preparedPort.accessMode.initialStep()
            )

            val systemInfoFrame = requireReply(
                port = port,
                step = preparedPort.accessMode.systemInfoStep(),
                attempts = READ_ONLY_COMMAND_ATTEMPTS,
                failureMessage = preparedPort.accessMode.systemInfoFailureMessage()
            )
            val stationInfo = SportIdentStationInfoParser.fromSystemInfoFrame(systemInfoFrame)
                ?: error("SPORTident station returned unreadable system info.")

            val beforeTime = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.readStationTimeStep("Read station time before write")
            ).data.decodeStationTime("before-write station time").dateTime

            val targetTimeSelection = selectTargetTime(
                sourceTime = sourceTime,
                currentTimeOffsetMillis = currentTimeOffsetMillis,
                secondBoundaryLeadMillis = secondBoundaryLeadMillis,
                alignToSecondBoundary = alignToSecondBoundary
            )
            val targetTime = targetTimeSelection.targetTime
            onWriteCommandStarted()
            val confirmedStationTime = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.writeStationTimeStep(targetTime)
            ).data.decodeStationTime("write acknowledgement station time")
            val confirmedTime = confirmedStationTime.dateTime

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
            val postApplyEstimate = readPostApplyStationTimeEstimate(port)
            val computerTimeAfterSync = postApplyEstimate?.computerTime
                ?: currentTime().truncatedTo(ChronoUnit.MILLIS)

            val result = DesktopSportIdentTimeSyncResult(
                stationInfo = stationInfo,
                sourceTime = targetTime,
                confirmedTime = confirmedTime,
                beforeTime = beforeTime,
                toleranceSeconds = toleranceSeconds,
                computerTimeAfterSync = computerTimeAfterSync,
                confirmedStationMinusComputerMillis = postApplyEstimate?.stationMinusComputerMillis,
                currentTimeOffsetMillis = if (sourceTime == null) currentTimeOffsetMillis else 0L,
                attempts = attemptCount,
                secondBoundaryWaitMillis = targetTimeSelection.secondBoundaryWaitMillis,
                secondBoundaryLeadMillis = targetTimeSelection.secondBoundaryLeadMillis,
                stationPowerStateWrite = null
            )
            val shouldDeferPowerOffForCorrection =
                sourceTime == null &&
                    result.confirmedStationMinusComputerMillis != null &&
                    abs(result.confirmedStationMinusComputerMillis) > correctionThresholdMillis &&
                    canUseCorrectionAttempt
            return if (
                putStationToSleepAfterSync &&
                !shouldDeferPowerOffForCorrection &&
                preparedPort.accessMode == DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED
            ) {
                result.copy(stationPowerStateWrite = attemptStationPowerOff(port, preparedPort.accessMode))
            } else {
                result
            }
        } finally {
            if (port.isOpen) {
                if (preparedPort.accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                    runCatching {
                        val exitStep = DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep()
                        commandClient.sendCommand(
                            port = port,
                            command = exitStep.command,
                            data = exitStep.payload
                        )
                    }
                }
                port.close()
            }
        }
    }

    private fun prepareTimeSyncPort(): DesktopSportIdentPreparedTimeSyncPort {
        val port = portSelector.selectPort()
            ?: error("No SPORTident USB station detected.")
        if (!port.info.matchesFtdiAdapter()) {
            return DesktopSportIdentPreparedTimeSyncPort(
                port = port,
                baudRate = selectBaudRate(port, DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED),
                accessMode = DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED
            )
        }

        val connection = runCatching { connectStation(port) }.getOrNull()
        val accessMode = connection
            ?.stationInfo
            ?.let { port.timeSyncAccessMode(it) }
            ?: port.fallbackTimeSyncAccessMode()
        val baudRate = connection?.baudRate ?: selectBaudRate(port, accessMode)
        return DesktopSportIdentPreparedTimeSyncPort(
            port = port,
            baudRate = baudRate,
            accessMode = accessMode
        )
    }

    private fun selectBaudRate(port: DesktopSerialPort, accessMode: DesktopSportIdentTimeSyncAccessMode): Int {
        for (baudRate in listOf(SportIdentProtocol.BAUDRATE_HIGH, SportIdentProtocol.BAUDRATE_LOW)) {
            runCatching {
                configure(port, baudRate)
                if (!port.open(OPEN_WAIT_TIME_MS)) {
                    error("Failed to open serial port ${port.info.systemPortPath}.")
                }
                val reply = commandClient.sendCommand(
                    port = port,
                    command = accessMode.initialStep().command,
                    data = accessMode.initialStep().payload
                )
                if (reply != null && accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                    val exitStep = DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep()
                    commandClient.sendCommand(
                        port = port,
                        command = exitStep.command,
                        data = exitStep.payload
                    )
                    sleepMillis(INSPECTION_PRIME_SETTLE_DELAY_MS)
                }
                reply
            }.getOrNull()?.let {
                port.close()
                return baudRate
            }
            if (port.isOpen) {
                port.close()
            }
        }
        error("SPORTident station did not respond to the time-sync probe.")
    }

    private fun DesktopSportIdentTimeSyncResult.withOptionalSeparateStationPowerOff(
        putStationToSleepAfterSync: Boolean
    ): DesktopSportIdentTimeSyncResult {
        if (!putStationToSleepAfterSync || stationPowerStateWrite != null) {
            return this
        }
        return copy(stationPowerStateWrite = attemptStationPowerOffInSeparateTransaction())
    }

    private fun DesktopSportIdentTimeSyncResult.withFallbackPostSyncEstimate(): DesktopSportIdentTimeSyncResult {
        if (confirmedStationMinusComputerMillis != null) {
            return this
        }
        val estimate = estimatePostSyncTimeInSeparateTransaction() ?: return this
        return copy(
            computerTimeAfterSync = estimate.computerTime,
            confirmedStationMinusComputerMillis = estimate.stationMinusComputerMillis
        )
    }

    private fun estimatePostSyncTimeInSeparateTransaction(): DesktopSportIdentPostSyncTimeEstimate? {
        val preparedPort = runCatching { prepareTimeSyncPort() }.getOrNull() ?: return null
        return readStationClockForInspection(
            port = preparedPort.port,
            baudRate = preparedPort.baudRate,
            accessMode = preparedPort.accessMode
        ).getOrNull()?.let { clock ->
            DesktopSportIdentPostSyncTimeEstimate(
                computerTime = clock.computerTime,
                stationMinusComputerMillis = clock.stationMinusComputerMillis
            )
        }
    }

    private fun attemptStationPowerOffInSeparateTransaction(): DesktopSportIdentStationPowerStateWriteResult {
        var lastResult: DesktopSportIdentStationPowerStateWriteResult? = null
        repeat(SEPARATE_POWER_OFF_ATTEMPTS) { attemptIndex ->
            val result = attemptStationPowerOffInSingleSeparateTransaction()
            if (result.confirmed == true) {
                return if (attemptIndex == 0) {
                    result
                } else {
                    result.copy(message = "${result.message} Succeeded on attempt ${attemptIndex + 1}.")
                }
            }
            lastResult = result
            if (attemptIndex < SEPARATE_POWER_OFF_ATTEMPTS - 1) {
                sleepMillis(SEPARATE_POWER_OFF_RETRY_DELAY_MS)
            }
        }
        return lastResult ?: DesktopSportIdentStationPowerStateWriteResult(
            confirmed = false,
            message = "Station sleep command was not attempted."
        )
    }

    private fun attemptStationPowerOffInSingleSeparateTransaction(): DesktopSportIdentStationPowerStateWriteResult {
        val preparedPort = runCatching { prepareTimeSyncPort() }.getOrElse { error ->
            return DesktopSportIdentStationPowerStateWriteResult(
                confirmed = false,
                message = "Station sleep command was not sent: ${error.message ?: error::class.simpleName}."
            )
        }
        val port = preparedPort.port
        return try {
            configure(port, preparedPort.baudRate)
            if (!port.open(OPEN_WAIT_TIME_MS)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }
            requireReply(
                port = port,
                step = preparedPort.accessMode.initialStep()
            )
            attemptStationPowerOff(port, preparedPort.accessMode)
        } catch (error: Throwable) {
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = false,
                message = "Station sleep command failed: ${error.message ?: error::class.simpleName}."
            )
        } finally {
            if (port.isOpen) {
                if (preparedPort.accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                    runCatching {
                        val exitStep = DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep()
                        commandClient.sendCommand(
                            port = port,
                            command = exitStep.command,
                            data = exitStep.payload
                        )
                    }
                }
                port.close()
            }
        }
    }

    private fun attemptStationPowerOff(
        port: DesktopSerialPort,
        accessMode: DesktopSportIdentTimeSyncAccessMode
    ): DesktopSportIdentStationPowerStateWriteResult {
        return runCatching {
            when (accessMode) {
                DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                    sendDirectStationPowerOff(port)
                DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                    sendRemoteStationPowerOff(port)
            }
        }.getOrElse { error ->
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = false,
                message = "Station sleep command failed: ${error.message ?: error::class.simpleName}."
            )
        }
    }

    private fun sendDirectStationPowerOff(port: DesktopSerialPort): DesktopSportIdentStationPowerStateWriteResult {
        val reply = commandClient.sendCommand(
            port = port,
            command = DesktopSportIdentTimeSyncProtocol.powerOffStep().command,
            data = DesktopSportIdentTimeSyncProtocol.powerOffStep().payload
        )
        return if (reply != null) {
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = true,
                message = "Station sleep command confirmed."
            )
        } else {
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = false,
                message = "Station sleep command was sent but not confirmed."
            )
        }
    }

    private fun sendRemoteStationPowerOff(port: DesktopSerialPort): DesktopSportIdentStationPowerStateWriteResult {
        val reply = commandClient.sendCommand(
            port = port,
            command = DesktopSportIdentTimeSyncProtocol.powerOffStep().command,
            data = DesktopSportIdentTimeSyncProtocol.powerOffStep().payload
        )
        return if (reply != null) {
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = true,
                message = "Remote station sleep command confirmed."
            )
        } else {
            DesktopSportIdentStationPowerStateWriteResult(
                confirmed = false,
                message = "Remote station sleep command was sent but not confirmed."
            )
        }
    }

    private fun readStationClock(
        port: DesktopSerialPort,
        baudRate: Int,
        accessMode: DesktopSportIdentTimeSyncAccessMode
    ): Result<DesktopSportIdentCoupledStationClock> =
        runCatching {
            configure(port, baudRate)
            if (!port.open(OPEN_WAIT_TIME_MS)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }

            sendWakePulse(port)
            requireReply(
                port = port,
                step = accessMode.initialStep()
            )
            val systemInfoFrame = requireReply(
                port = port,
                step = accessMode.systemInfoStep(),
                attempts = READ_ONLY_COMMAND_ATTEMPTS,
                failureMessage = accessMode.systemInfoFailureMessage()
            )
            val stationInfo = SportIdentStationInfoParser.fromSystemInfoFrame(systemInfoFrame)
                ?: error("SPORTident station returned unreadable system info.")
            val computerTime = currentTime().truncatedTo(ChronoUnit.MILLIS)
            val stationTime = requireReply(
                port = port,
                step = DesktopSportIdentTimeSyncProtocol.readStationTimeStep("Read station time for inspection")
            ).data.decodeStationTime("inspection station time")

            DesktopSportIdentCoupledStationClock(
                stationInfo = stationInfo,
                stationTime = stationTime.preciseDateTime,
                computerTime = computerTime,
                stationMinusComputerMillis = Duration.between(computerTime, stationTime.preciseDateTime).toMillis()
            )
        }.also {
            if (port.isOpen) {
                if (accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                    runCatching {
                        val exitStep = DesktopSportIdentTimeSyncProtocol.exitRemoteModeStep()
                        commandClient.sendCommand(
                            port = port,
                            command = exitStep.command,
                            data = exitStep.payload
                        )
                    }
                }
                port.close()
            }
        }

    private fun readPostApplyStationTimeEstimate(port: DesktopSerialPort): DesktopSportIdentPostSyncTimeEstimate? =
        runCatching {
            val computerTime = currentTime().truncatedTo(ChronoUnit.MILLIS)
            commandClient.sendCommand(
                port = port,
                command = DesktopSportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND
            )?.data?.decodeStationTime("post-apply station time")?.let { stationTime ->
                DesktopSportIdentPostSyncTimeEstimate(
                    computerTime = computerTime,
                    stationMinusComputerMillis = Duration.between(
                        computerTime,
                        stationTime.preciseDateTime
                    ).toMillis()
                )
            }
        }.getOrNull()

    private fun readStationClockForInspection(
        port: DesktopSerialPort,
        baudRate: Int,
        accessMode: DesktopSportIdentTimeSyncAccessMode
    ): Result<DesktopSportIdentCoupledStationClock> {
        val attempts = if (accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
            INSPECTION_CLOCK_READ_ATTEMPTS
        } else {
            1
        }
        var lastResult: Result<DesktopSportIdentCoupledStationClock>? = null
        repeat(attempts) { attemptIndex ->
            if (accessMode == DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                primeCoupledStation(port, baudRate)
            }
            val result = readStationClock(
                port = port,
                baudRate = baudRate,
                accessMode = accessMode
            )
            if (result.isSuccess) {
                return result
            }
            lastResult = result
            if (attemptIndex < attempts - 1) {
                sleepMillis(INSPECTION_WAKE_SETTLE_DELAY_MS)
            }
        }
        return lastResult ?: Result.failure(IllegalStateException("SPORTident station time read was not attempted."))
    }

    private fun primeCoupledStation(port: DesktopSerialPort, baudRate: Int) {
        runCatching {
            configure(port, baudRate)
            if (!port.open(OPEN_WAIT_TIME_MS)) {
                error("Failed to open serial port ${port.info.systemPortPath}.")
            }
            val enterStep = DesktopSportIdentTimeSyncProtocol.enterRemoteModeStep()
            commandClient.sendCommand(
                port = port,
                command = enterStep.command,
                data = enterStep.payload
            )
        }.also {
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
        sleepMillis(INSPECTION_PRIME_SETTLE_DELAY_MS)
    }

    private fun configure(port: DesktopSerialPort, baudRate: Int) {
        port.configure(baudRate, READ_TIMEOUT_MS, WRITE_TIMEOUT_MS)
    }

    private fun sendWakePulse(port: DesktopSerialPort) {
        port.write(byteArrayOf(SportIdentProtocol.WAKEUP))
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

    private fun selectTargetTime(
        sourceTime: LocalDateTime?,
        currentTimeOffsetMillis: Long,
        secondBoundaryLeadMillis: Long,
        alignToSecondBoundary: Boolean
    ): TargetTimeSelection =
        if (sourceTime != null) {
            TargetTimeSelection(
                targetTime = sourceTime.truncatedTo(ChronoUnit.MILLIS),
                secondBoundaryWaitMillis = null,
                secondBoundaryLeadMillis = null
            )
        } else {
            if (alignToSecondBoundary) {
                val boundaryTime = waitForSecondBoundaryLead(secondBoundaryLeadMillis)
                TargetTimeSelection(
                    targetTime = boundaryTime.targetBoundary
                        .plus(Duration.ofMillis(currentTimeOffsetMillis))
                        .roundToNearestSecond(),
                    secondBoundaryWaitMillis = boundaryTime.waitMillis,
                    secondBoundaryLeadMillis = secondBoundaryLeadMillis
                )
            } else {
                val now = currentTime().truncatedTo(ChronoUnit.MILLIS)
                TargetTimeSelection(
                    targetTime = now.plus(Duration.ofMillis(currentTimeOffsetMillis)),
                    secondBoundaryWaitMillis = 0L,
                    secondBoundaryLeadMillis = null
                )
            }
        }

    private fun waitForSecondBoundaryLead(leadMillis: Long): BoundaryTime {
        val beforeWait = currentTime().truncatedTo(ChronoUnit.MILLIS)
        val millisIntoSecond = beforeWait.nano / 1_000_000L
        val currentSecond = beforeWait.truncatedTo(ChronoUnit.SECONDS)
        val targetBoundary = if (millisIntoSecond <= 1_000L - leadMillis) {
            currentSecond.plusSeconds(1)
        } else {
            currentSecond.plusSeconds(2)
        }
        val wakeTime = targetBoundary.minus(Duration.ofMillis(leadMillis))
        val waitMillis = Duration.between(beforeWait, wakeTime).toMillis().coerceAtLeast(0L)
        if (waitMillis > 0) {
            sleepMillis(waitMillis)
        }
        return BoundaryTime(
            time = currentTime().truncatedTo(ChronoUnit.MILLIS),
            waitMillis = waitMillis,
            targetBoundary = targetBoundary
        )
    }

    private fun LocalDateTime.roundToNearestSecond(): LocalDateTime {
        val truncated = truncatedTo(ChronoUnit.SECONDS)
        return if (nano >= 500_000_000) truncated.plusSeconds(1) else truncated
    }

    private fun DesktopSerialPort.timeSyncAccessMode(
        stationInfo: SportIdentStationInfo
    ): DesktopSportIdentTimeSyncAccessMode =
        if (info.matchesFtdiAdapter()) {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED
        } else if (stationInfo.canRelayTimeSync()) {
            DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED
        } else {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED
        }

    private fun DesktopSerialPort.fallbackTimeSyncAccessMode(): DesktopSportIdentTimeSyncAccessMode =
        if (info.matchesFtdiAdapter()) {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED
        } else {
            DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED
        }

    private fun DesktopSportIdentTimeSyncAccessMode.initialStep(): DesktopSportIdentTimeSyncCommandStep =
        when (this) {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                DesktopSportIdentTimeSyncProtocol.selectDirectStationStep()
            DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                DesktopSportIdentTimeSyncProtocol.enterRemoteModeStep()
        }

    private fun DesktopSportIdentTimeSyncAccessMode.systemInfoStep(): DesktopSportIdentTimeSyncCommandStep =
        when (this) {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                DesktopSportIdentTimeSyncProtocol.readSystemInfoStep()
            DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                DesktopSportIdentTimeSyncProtocol.readCompatibleSystemInfoStep()
        }

    private fun DesktopSportIdentTimeSyncAccessMode.systemInfoFailureMessage(): String =
        when (this) {
            DesktopSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                "Direct SPORTident station did not answer system-info read. Confirm the station is awake."
            DesktopSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                "Remote/coupled SPORTident station did not answer system-info read. " +
                    "Confirm the target station is awake and coupled to the download station."
        }

    private fun SportIdentStationInfo.canRelayTimeSync(): Boolean =
        stationModeLabel?.startsWith("SI MASTER") == true

    companion object {
        const val DEFAULT_TOLERANCE_SECONDS = 2L
        const val DEFAULT_CURRENT_TIME_OFFSET_MILLIS = 0L
        const val DEFAULT_SECOND_BOUNDARY_LEAD_MILLIS = 225L
        const val DEFAULT_WRITE_ATTEMPTS = 2
        const val DEFAULT_CORRECTION_THRESHOLD_MILLIS = 100L
        const val DEFAULT_ALIGN_TO_SECOND_BOUNDARY = false
        private const val READ_TIMEOUT_MS = 1200
        private const val WRITE_TIMEOUT_MS = 1200
        private const val OPEN_WAIT_TIME_MS = 200
        private const val MAX_REPLY_BYTES = 256
        private const val READ_ONLY_COMMAND_ATTEMPTS = 3
        private const val INSPECTION_CLOCK_READ_ATTEMPTS = 4
        private const val INSPECTION_WAKE_SETTLE_DELAY_MS = 250L
        private const val INSPECTION_PRIME_SETTLE_DELAY_MS = 250L
        private const val SEPARATE_POWER_OFF_ATTEMPTS = 2
        private const val SEPARATE_POWER_OFF_RETRY_DELAY_MS = 150L
        private const val RETRY_DELAY_MS = 150L
    }
}

private data class TargetTimeSelection(
    val targetTime: LocalDateTime,
    val secondBoundaryWaitMillis: Long?,
    val secondBoundaryLeadMillis: Long?
)

private data class BoundaryTime(
    val time: LocalDateTime,
    val waitMillis: Long,
    val targetBoundary: LocalDateTime
)
