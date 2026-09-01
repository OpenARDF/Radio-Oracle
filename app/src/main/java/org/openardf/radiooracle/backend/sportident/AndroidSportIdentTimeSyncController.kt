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

package org.openardf.radiooracle.backend.sportident

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfoParser
import org.openardf.radiooracle.shared.sportident.SportIdentStationTime
import org.openardf.radiooracle.shared.sportident.SportIdentStationTimeCodec
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncCommandStep
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncProtocol

data class AndroidSportIdentTimeSyncInspection(
    val readerStationInfo: SportIdentStationInfo,
    val stationInfo: SportIdentStationInfo,
    val stationTime: LocalDateTime,
    val computerTime: LocalDateTime,
    val stationMinusComputerMillis: Long,
    val requiresCoupledStation: Boolean
)

data class AndroidSportIdentTimeSyncResult(
    val stationInfo: SportIdentStationInfo,
    val sourceTime: LocalDateTime,
    val beforeTime: LocalDateTime,
    val confirmedTime: LocalDateTime,
    val computerTimeAfterSync: LocalDateTime,
    val confirmedStationMinusComputerMillis: Long?,
    val attempts: Int,
    val stationPowerStateWrite: AndroidSportIdentStationPowerStateWriteResult?
)

data class AndroidSportIdentStationPowerStateWriteResult(
    val confirmed: Boolean,
    val message: String
)

internal interface AndroidSportIdentCommandTransport {
    fun sendWakePulse(): Boolean
    fun sendCommand(step: SportIdentTimeSyncCommandStep): SportIdentFrame?
}

private enum class AndroidSportIdentTimeSyncAccessMode {
    DIRECT_ATTACHED,
    RELAY_COUPLED
}

/**
 * Performs the same inspect-before-write and verified read-back transaction as desktop.
 * The caller must serialize this controller with card readout access to the USB port.
 */
internal class AndroidSportIdentTimeSyncController(
    private val transport: AndroidSportIdentCommandTransport,
    private val readerStationInfo: () -> SportIdentStationInfo,
    private val currentTime: () -> LocalDateTime = LocalDateTime::now,
    private val sleepMillis: (Long) -> Unit = Thread::sleep
) {
    fun inspectDownloadStation(): AndroidSportIdentTimeSyncInspection {
        val readerInfo = readerStationInfo()
        val accessMode = readerInfo.timeSyncAccessMode()
        val attempts = if (accessMode == AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
            INSPECTION_ATTEMPTS
        } else {
            1
        }
        var lastFailure: Throwable? = null
        repeat(attempts) { attemptIndex ->
            runCatching { inspectOnce(readerInfo, accessMode) }
                .onSuccess { return it }
                .onFailure { lastFailure = it }
            if (attemptIndex < attempts - 1) sleepMillis(INSPECTION_RETRY_DELAY_MS)
        }
        throw lastFailure ?: IllegalStateException("SPORTident station inspection was not attempted.")
    }

    fun syncTime(
        writeEnabled: Boolean,
        putStationToSleepAfterSync: Boolean = true,
        expectedStationSerialNumber: Int? = null
    ): AndroidSportIdentTimeSyncResult {
        require(writeEnabled) { "SPORTident time sync writes require explicit user confirmation." }

        var lastFailure: Throwable? = null
        repeat(MAX_WRITE_ATTEMPTS) { attemptIndex ->
            var writeStarted = false
            val outcome = runCatching {
                syncOnce(
                    attempt = attemptIndex + 1,
                    expectedStationSerialNumber = expectedStationSerialNumber,
                    onWriteStarted = { writeStarted = true }
                )
            }
            val initialResult = outcome.getOrNull()
            if (initialResult != null) {
                val result = initialResult.withFallbackPostSyncEstimate(
                    expectedStationSerialNumber = expectedStationSerialNumber
                )
                val postSyncDeltaMillis = result.confirmedStationMinusComputerMillis
                if (
                    postSyncDeltaMillis == null ||
                    abs(postSyncDeltaMillis) <= CORRECTION_THRESHOLD_MILLIS ||
                    attemptIndex == MAX_WRITE_ATTEMPTS - 1
                ) {
                    return if (putStationToSleepAfterSync) {
                        result.copy(
                            stationPowerStateWrite = sleepStationInSeparateTransaction(
                                expectedStationSerialNumber = result.stationInfo.serialNumber
                            )
                        )
                    } else {
                        result
                    }
                }
                sleepMillis(WRITE_RETRY_DELAY_MS)
                return@repeat
            }

            val failure = outcome.exceptionOrNull()
                ?: IllegalStateException("SPORTident time sync failed.")
            if (writeStarted || attemptIndex == MAX_WRITE_ATTEMPTS - 1) throw failure
            lastFailure = failure
            sleepMillis(WRITE_RETRY_DELAY_MS)
        }
        throw lastFailure ?: IllegalStateException("SPORTident time sync failed.")
    }

    private fun inspectOnce(
        readerInfo: SportIdentStationInfo,
        accessMode: AndroidSportIdentTimeSyncAccessMode
    ): AndroidSportIdentTimeSyncInspection =
        withStationTransaction(accessMode) {
            val stationInfo = readStationInfo(accessMode)
            val computerTime = currentTime().truncatedTo(ChronoUnit.MILLIS)
            val stationTime = requireReply(
                SportIdentTimeSyncProtocol.readStationTimeStep("Read station time for inspection")
            ).data.decodeStationTime("inspection station time")

            AndroidSportIdentTimeSyncInspection(
                readerStationInfo = readerInfo,
                stationInfo = stationInfo,
                stationTime = stationTime.preciseDateTime,
                computerTime = computerTime,
                stationMinusComputerMillis = Duration.between(
                    computerTime,
                    stationTime.preciseDateTime
                ).toMillis(),
                requiresCoupledStation = accessMode == AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED
            )
        }

    private fun syncOnce(
        attempt: Int,
        expectedStationSerialNumber: Int?,
        onWriteStarted: () -> Unit
    ): AndroidSportIdentTimeSyncResult {
        val accessMode = readerStationInfo().timeSyncAccessMode()
        return withStationTransaction(accessMode) {
            val stationInfo = readStationInfo(accessMode)
            check(
                expectedStationSerialNumber == null ||
                    stationInfo.serialNumber == expectedStationSerialNumber
            ) {
                "The connected target changed after inspection: expected station " +
                    "$expectedStationSerialNumber but found ${stationInfo.serialNumber}. Inspect again before syncing."
            }
            val beforeTime = requireReply(
                SportIdentTimeSyncProtocol.readStationTimeStep("Read station time before write")
            ).data.decodeStationTime("before-write station time").dateTime

            val targetTime = currentTime().truncatedTo(ChronoUnit.MILLIS)
            onWriteStarted()
            val confirmedTime = requireReply(
                SportIdentTimeSyncProtocol.writeStationTimeStep(targetTime)
            ).data.decodeStationTime("write acknowledgement station time").dateTime
            val confirmationErrorMillis = abs(Duration.between(targetTime, confirmedTime).toMillis())
            check(confirmationErrorMillis <= CONFIRMATION_TOLERANCE_MILLIS) {
                "SPORTident station time read-back $confirmedTime differed from requested " +
                    "$targetTime by ${confirmationErrorMillis}ms."
            }

            requireReply(SportIdentTimeSyncProtocol.applyStationTimeStep())
            val computerTimeAfterSync = currentTime().truncatedTo(ChronoUnit.MILLIS)
            val postSyncDelta = transport.sendCommand(
                SportIdentTimeSyncProtocol.readStationTimeStep("Read station time after apply")
            )?.data?.let { data ->
                SportIdentStationTimeCodec.decodePayload(data)?.let { appliedTime ->
                    Duration.between(
                        computerTimeAfterSync,
                        appliedTime.preciseDateTime
                    ).toMillis()
                }
            }

            AndroidSportIdentTimeSyncResult(
                stationInfo = stationInfo,
                sourceTime = targetTime,
                beforeTime = beforeTime,
                confirmedTime = confirmedTime,
                computerTimeAfterSync = computerTimeAfterSync,
                confirmedStationMinusComputerMillis = postSyncDelta,
                attempts = attempt,
                stationPowerStateWrite = null
            )
        }
    }

    private fun AndroidSportIdentTimeSyncResult.withFallbackPostSyncEstimate(
        expectedStationSerialNumber: Int?
    ): AndroidSportIdentTimeSyncResult {
        if (confirmedStationMinusComputerMillis != null) return this

        val inspection = runCatching { inspectDownloadStation() }.getOrNull()
            ?.takeIf { inspected ->
                expectedStationSerialNumber == null ||
                    inspected.stationInfo.serialNumber == expectedStationSerialNumber
            }
            ?: return this
        return copy(
            computerTimeAfterSync = inspection.computerTime,
            confirmedStationMinusComputerMillis = inspection.stationMinusComputerMillis
        )
    }

    private fun sleepStationInSeparateTransaction(
        expectedStationSerialNumber: Int
    ): AndroidSportIdentStationPowerStateWriteResult {
        val accessMode = readerStationInfo().timeSyncAccessMode()
        var lastResult: AndroidSportIdentStationPowerStateWriteResult? = null
        repeat(SEPARATE_POWER_OFF_ATTEMPTS) { attemptIndex ->
            val result = runCatching {
                withStationTransaction(accessMode) {
                    val stationInfo = readStationInfo(accessMode)
                    check(stationInfo.serialNumber == expectedStationSerialNumber) {
                        "The connected target changed before sleep: expected station " +
                            "$expectedStationSerialNumber but found ${stationInfo.serialNumber}."
                    }
                    attemptStationPowerOff()
                }
            }.getOrElse { error ->
                AndroidSportIdentStationPowerStateWriteResult(
                    confirmed = false,
                    message = "Station sleep command failed: ${error.message ?: error::class.simpleName}."
                )
            }
            if (result.confirmed) {
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
        return lastResult ?: AndroidSportIdentStationPowerStateWriteResult(
            confirmed = false,
            message = "Station sleep command was not attempted."
        )
    }

    private fun attemptStationPowerOff(): AndroidSportIdentStationPowerStateWriteResult {
        val confirmed = transport.sendCommand(SportIdentTimeSyncProtocol.powerOffStep()) != null
        return AndroidSportIdentStationPowerStateWriteResult(
            confirmed = confirmed,
            message = if (confirmed) {
                "Station sleep command confirmed."
            } else {
                "Station sleep command was sent but not confirmed."
            }
        )
    }

    private inline fun <T> withStationTransaction(
        accessMode: AndroidSportIdentTimeSyncAccessMode,
        block: () -> T
    ): T {
        check(transport.sendWakePulse()) { "SPORTident station wake pulse could not be sent." }
        try {
            requireReply(accessMode.initialStep())
            return block()
        } finally {
            if (accessMode == AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED) {
                runCatching { transport.sendCommand(SportIdentTimeSyncProtocol.exitRemoteModeStep()) }
            }
        }
    }

    private fun readStationInfo(accessMode: AndroidSportIdentTimeSyncAccessMode): SportIdentStationInfo {
        val frame = requireReply(
            step = accessMode.systemInfoStep(),
            attempts = READ_ONLY_COMMAND_ATTEMPTS,
            failureMessage = accessMode.systemInfoFailureMessage()
        )
        return SportIdentStationInfoParser.fromSystemInfoFrame(frame)
            ?: error("SPORTident station returned unreadable system info.")
    }

    private fun requireReply(
        step: SportIdentTimeSyncCommandStep,
        attempts: Int = 1,
        failureMessage: String = "SPORTident station did not reply to ${step.label}."
    ): SportIdentFrame {
        repeat(attempts) {
            transport.sendCommand(step)?.let { return it }
        }
        error(failureMessage)
    }

    private fun ByteArray.decodeStationTime(context: String): SportIdentStationTime =
        SportIdentStationTimeCodec.decodePayload(this)
            ?: error("SPORTident station returned unreadable $context.")

    private fun SportIdentStationInfo.timeSyncAccessMode(): AndroidSportIdentTimeSyncAccessMode =
        if (stationModeLabel?.startsWith("SI MASTER") == true) {
            AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED
        } else {
            AndroidSportIdentTimeSyncAccessMode.DIRECT_ATTACHED
        }

    private fun AndroidSportIdentTimeSyncAccessMode.initialStep(): SportIdentTimeSyncCommandStep =
        when (this) {
            AndroidSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                SportIdentTimeSyncProtocol.selectDirectStationStep()
            AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                SportIdentTimeSyncProtocol.enterRemoteModeStep()
        }

    private fun AndroidSportIdentTimeSyncAccessMode.systemInfoStep(): SportIdentTimeSyncCommandStep =
        when (this) {
            AndroidSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                SportIdentTimeSyncProtocol.readSystemInfoStep()
            AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                SportIdentTimeSyncProtocol.readCompatibleSystemInfoStep()
        }

    private fun AndroidSportIdentTimeSyncAccessMode.systemInfoFailureMessage(): String =
        when (this) {
            AndroidSportIdentTimeSyncAccessMode.DIRECT_ATTACHED ->
                "Direct SPORTident station did not answer system-info read. Confirm the station is awake."
            AndroidSportIdentTimeSyncAccessMode.RELAY_COUPLED ->
                "No coupled SPORTident station answered. Confirm the target station is awake and coupled " +
                    "to the download station."
        }

    companion object {
        private const val READ_ONLY_COMMAND_ATTEMPTS = 3
        private const val INSPECTION_ATTEMPTS = 4
        private const val INSPECTION_RETRY_DELAY_MS = 250L
        private const val MAX_WRITE_ATTEMPTS = 2
        private const val WRITE_RETRY_DELAY_MS = 150L
        private const val SEPARATE_POWER_OFF_ATTEMPTS = 2
        private const val SEPARATE_POWER_OFF_RETRY_DELAY_MS = 250L
        private const val CORRECTION_THRESHOLD_MILLIS = 100L
        private const val CONFIRMATION_TOLERANCE_MILLIS = 2_000L
    }
}
