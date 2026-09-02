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

import org.openardf.radiooracle.shared.sportident.SportIdentFrame
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupProtocol
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupRecord
import org.openardf.radiooracle.shared.sportident.SportIdentStationBackupSnapshot
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfo
import org.openardf.radiooracle.shared.sportident.SportIdentStationInfoParser
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncCommandStep
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncProtocol

private enum class DesktopSportIdentBackupAccessMode {
    DIRECT_ATTACHED,
    RELAY_COUPLED
}

/** Downloads punch records from a field station without changing or clearing the station. */
internal class DesktopSportIdentStationBackupService(
    private val portSelector: DesktopSportIdentPortSelector = DesktopSportIdentPortSelector(),
    private val connectStation: (DesktopSerialPort) -> DesktopSportIdentStationConnection = {
        DesktopSportIdentStationProbe().connectKeepingPortOpen(it)
    },
    private val commandClient: DesktopSportIdentStationCommandClient = DesktopSportIdentStationCommandClient(
        readTimeoutMs = READ_TIMEOUT_MS,
        maxReplyBytes = MAX_REPLY_BYTES
    ),
    private val sleepMillis: (Long) -> Unit = Thread::sleep
) {
    fun readBackup(
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): SportIdentStationBackupSnapshot {
        val port = portSelector.selectPort()
            ?: error("No SPORTident USB station detected.")
        val connection = connectStation(port)
        val accessMode = port.backupAccessMode(connection.stationInfo)
        try {
            val systemInfoFrame = selectAwakeStationAndReadMetadata(port, accessMode)
            val stationInfo = SportIdentStationInfoParser.fromSystemInfoFrame(systemInfoFrame)
                ?: error("SPORTident station returned unreadable system info.")
            val metadata = SportIdentStationBackupProtocol.parseMetadata(systemInfoFrame)
                ?: error("SPORTident station returned incomplete backup metadata.")
            val requests = SportIdentStationBackupProtocol.readRequests(metadata)
            val records = ArrayList<SportIdentStationBackupRecord>(requests.size)
            val unreadableAddresses = ArrayList<Int>()
            requests.forEachIndexed { index, request ->
                val frame = requireReply(
                    port = port,
                    step = SportIdentStationBackupProtocol.readRecordStep(request),
                    attempts = READ_ONLY_COMMAND_ATTEMPTS,
                    retryDelayMillis = READ_ONLY_RETRY_DELAY_MS
                )
                val record = SportIdentStationBackupProtocol.parseRecordReply(
                    request = request,
                    frame = frame,
                    extendedPunchRecords = metadata.extendedPunchRecords
                )
                if (record == null) unreadableAddresses += request.address else records += record
                onProgress(index + 1, requests.size)
            }
            return SportIdentStationBackupSnapshot(
                stationInfo = stationInfo,
                metadata = metadata,
                records = records,
                unreadableRecordAddresses = unreadableAddresses
            )
        } finally {
            if (port.isOpen) {
                if (accessMode == DesktopSportIdentBackupAccessMode.RELAY_COUPLED) {
                    runCatching {
                        val exitStep = SportIdentTimeSyncProtocol.exitRemoteModeStep()
                        commandClient.sendCommand(port, exitStep.command, exitStep.payload)
                    }
                }
                port.close()
            }
        }
    }

    private fun selectAwakeStationAndReadMetadata(
        port: DesktopSerialPort,
        accessMode: DesktopSportIdentBackupAccessMode
    ): SportIdentFrame {
        repeat(STATION_WAKE_ATTEMPTS) { attemptIndex ->
            val initialStep = accessMode.initialStep()
            val wakeSent = port.write(byteArrayOf(SportIdentProtocol.WAKEUP)) == 1
            var selectedThisAttempt = false
            var keepSelected = false
            try {
                selectedThisAttempt = wakeSent && commandClient.sendCommand(
                    port,
                    initialStep.command,
                    initialStep.payload
                ) != null
                if (selectedThisAttempt) {
                    if (accessMode == DesktopSportIdentBackupAccessMode.RELAY_COUPLED) {
                        sleepMillis(REMOTE_MODE_SETTLE_DELAY_MS)
                    }
                    optionalReply(
                        port = port,
                        step = SportIdentStationBackupProtocol.readSystemInfoStep(),
                        attempts = READ_ONLY_COMMAND_ATTEMPTS,
                        retryDelayMillis = READ_ONLY_RETRY_DELAY_MS
                    )?.let { frame ->
                        keepSelected = true
                        return frame
                    }
                }
            } finally {
                if (
                    selectedThisAttempt &&
                    !keepSelected &&
                    accessMode == DesktopSportIdentBackupAccessMode.RELAY_COUPLED
                ) {
                    runCatching {
                        val exitStep = SportIdentTimeSyncProtocol.exitRemoteModeStep()
                        commandClient.sendCommand(port, exitStep.command, exitStep.payload)
                    }
                }
            }
            if (attemptIndex < STATION_WAKE_ATTEMPTS - 1) {
                sleepMillis(STATION_WAKE_RETRY_DELAY_MS)
            }
        }
        error(accessMode.wakeFailureMessage())
    }

    private fun optionalReply(
        port: DesktopSerialPort,
        step: SportIdentTimeSyncCommandStep,
        attempts: Int,
        retryDelayMillis: Long
    ): SportIdentFrame? {
        repeat(attempts) { attemptIndex ->
            commandClient.sendCommand(port, step.command, step.payload)?.let { return it }
            if (attemptIndex < attempts - 1) sleepMillis(retryDelayMillis)
        }
        return null
    }

    private fun requireReply(
        port: DesktopSerialPort,
        step: SportIdentTimeSyncCommandStep,
        attempts: Int = 1,
        failureMessage: String = "SPORTident station did not reply to ${step.label}.",
        retryDelayMillis: Long = 0L
    ): SportIdentFrame {
        repeat(attempts) { attemptIndex ->
            commandClient.sendCommand(port, step.command, step.payload)?.let { return it }
            if (attemptIndex < attempts - 1 && retryDelayMillis > 0L) {
                sleepMillis(retryDelayMillis)
            }
        }
        error(failureMessage)
    }

    private fun DesktopSerialPort.backupAccessMode(
        stationInfo: SportIdentStationInfo
    ): DesktopSportIdentBackupAccessMode =
        if (info.matchesFtdiAdapter()) {
            DesktopSportIdentBackupAccessMode.DIRECT_ATTACHED
        } else if (stationInfo.stationModeLabel?.startsWith("SI MASTER") == true) {
            DesktopSportIdentBackupAccessMode.RELAY_COUPLED
        } else {
            DesktopSportIdentBackupAccessMode.DIRECT_ATTACHED
        }

    private fun DesktopSportIdentBackupAccessMode.initialStep(): SportIdentTimeSyncCommandStep =
        when (this) {
            DesktopSportIdentBackupAccessMode.DIRECT_ATTACHED ->
                SportIdentTimeSyncProtocol.selectDirectStationStep()
            DesktopSportIdentBackupAccessMode.RELAY_COUPLED ->
                SportIdentTimeSyncProtocol.enterRemoteModeStep()
        }

    private fun DesktopSportIdentBackupAccessMode.wakeFailureMessage(): String =
        when (this) {
            DesktopSportIdentBackupAccessMode.DIRECT_ATTACHED ->
                "Direct SPORTident station did not answer after wake-up."
            DesktopSportIdentBackupAccessMode.RELAY_COUPLED ->
                "The coupled SPORTident field station could not be woken or reached. " +
                    "Check the coupling stick and retry."
        }

    private companion object {
        const val READ_TIMEOUT_MS = 1_500
        const val MAX_REPLY_BYTES = 256
        const val READ_ONLY_COMMAND_ATTEMPTS = 3
        const val READ_ONLY_RETRY_DELAY_MS = 150L
        const val REMOTE_MODE_SETTLE_DELAY_MS = 250L
        const val STATION_WAKE_ATTEMPTS = 4
        const val STATION_WAKE_RETRY_DELAY_MS = 250L
    }
}
