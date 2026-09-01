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

package org.openardf.radiooracle.shared.sportident

import java.time.LocalDateTime

data class SportIdentTimeSyncCommandStep(
    val label: String,
    val command: Byte,
    val payload: ByteArray
) {
    val frameBytes: ByteArray
        get() = SportIdentProtocol.buildExtendedMessage(command, payload)
}

/** Shared command catalog for the validated desktop and Android time-sync transaction. */
object SportIdentTimeSyncProtocol {
    val SET_STATION_TIME_COMMAND: Byte = 0xF6.toByte()
    val GET_STATION_TIME_COMMAND: Byte = 0xF7.toByte()
    val POWER_OFF_COMMAND: Byte = 0xF8.toByte()
    val APPLY_STATION_TIME_COMMAND: Byte = 0xF9.toByte()

    fun enterRemoteModeStep() = SportIdentTimeSyncCommandStep(
        label = "Enter remote/config mode",
        command = SportIdentProtocol.PROBE_COMMAND,
        payload = byteArrayOf(0x53)
    )

    fun selectDirectStationStep() = SportIdentTimeSyncCommandStep(
        label = "Select direct station mode",
        command = SportIdentProtocol.PROBE_COMMAND,
        payload = byteArrayOf(0x4D)
    )

    fun readSystemInfoStep() = SportIdentTimeSyncCommandStep(
        label = "Read long system information",
        command = SportIdentProtocol.GET_SYSTEM_INFO,
        payload = byteArrayOf(0x00, 0x80.toByte())
    )

    fun readCompatibleSystemInfoStep() = SportIdentTimeSyncCommandStep(
        label = "Read compatible system information",
        command = SportIdentProtocol.GET_SYSTEM_INFO,
        payload = byteArrayOf(0x00, 0x75)
    )

    fun readStationTimeStep(label: String) = SportIdentTimeSyncCommandStep(
        label = label,
        command = GET_STATION_TIME_COMMAND,
        payload = byteArrayOf()
    )

    fun writeStationTimeStep(sourceTime: LocalDateTime) = SportIdentTimeSyncCommandStep(
        label = "Write station time",
        command = SET_STATION_TIME_COMMAND,
        payload = SportIdentStationTimeCodec.encodePayload(sourceTime)
    )

    fun applyStationTimeStep() = SportIdentTimeSyncCommandStep(
        label = "Apply station time write",
        command = APPLY_STATION_TIME_COMMAND,
        payload = byteArrayOf(0x01)
    )

    fun powerOffStep() = SportIdentTimeSyncCommandStep(
        label = "Put station to sleep",
        command = POWER_OFF_COMMAND,
        payload = byteArrayOf()
    )

    fun exitRemoteModeStep() = SportIdentTimeSyncCommandStep(
        label = "Exit remote/config mode",
        command = SportIdentProtocol.PROBE_COMMAND,
        payload = byteArrayOf(0x4D)
    )
}
