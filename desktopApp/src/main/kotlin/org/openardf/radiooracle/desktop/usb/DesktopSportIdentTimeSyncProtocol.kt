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
import org.openardf.radiooracle.shared.sportident.SportIdentProtocol

internal data class DesktopSportIdentTimeSyncCommandStep(
    val label: String,
    val command: Byte,
    val payload: ByteArray
) {
    val frameBytes: ByteArray
        get() = SportIdentProtocol.buildExtendedMessage(command, payload)
}

internal object DesktopSportIdentTimeSyncProtocol {
    val SET_STATION_TIME_COMMAND: Byte = 0xF6.toByte()
    val GET_STATION_TIME_COMMAND: Byte = 0xF7.toByte()
    val APPLY_STATION_TIME_COMMAND: Byte = 0xF9.toByte()

    fun configPlusWriteSequence(sourceTime: LocalDateTime): List<DesktopSportIdentTimeSyncCommandStep> =
        listOf(
            enterRemoteModeStep(),
            readSystemInfoStep(),
            readStationTimeStep("Read station time before write"),
            writeStationTimeStep(sourceTime),
            applyStationTimeStep(),
            exitRemoteModeStep()
        )

    fun validatedWriteSequence(sourceTime: LocalDateTime): List<DesktopSportIdentTimeSyncCommandStep> =
        listOf(
            enterRemoteModeStep(),
            readCompatibleSystemInfoStep(),
            readStationTimeStep("Read station time before write"),
            writeStationTimeStep(sourceTime),
            applyStationTimeStep(),
            exitRemoteModeStep()
        )

    fun enterRemoteModeStep(): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Enter remote/config mode",
            command = SportIdentProtocol.PROBE_COMMAND,
            payload = byteArrayOf(0x53)
        )

    fun readSystemInfoStep(): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Read long system information",
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            payload = byteArrayOf(0x00, 0x80.toByte())
        )

    fun readCompatibleSystemInfoStep(): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Read compatible system information",
            command = SportIdentProtocol.GET_SYSTEM_INFO,
            payload = byteArrayOf(0x00, 0x75)
        )

    fun readStationTimeStep(label: String): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = label,
            command = GET_STATION_TIME_COMMAND,
            payload = byteArrayOf()
        )

    fun writeStationTimeStep(sourceTime: LocalDateTime): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Write station time",
            command = SET_STATION_TIME_COMMAND,
            payload = DesktopSportIdentStationTimeCodec.encodePayload(sourceTime)
        )

    fun applyStationTimeStep(): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Apply station time write",
            command = APPLY_STATION_TIME_COMMAND,
            payload = byteArrayOf(0x01)
        )

    fun exitRemoteModeStep(): DesktopSportIdentTimeSyncCommandStep =
        DesktopSportIdentTimeSyncCommandStep(
            label = "Exit remote/config mode",
            command = SportIdentProtocol.PROBE_COMMAND,
            payload = byteArrayOf(0x4D)
        )
}
