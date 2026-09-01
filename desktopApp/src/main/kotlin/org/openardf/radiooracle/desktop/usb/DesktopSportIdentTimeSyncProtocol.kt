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
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncCommandStep
import org.openardf.radiooracle.shared.sportident.SportIdentTimeSyncProtocol

internal typealias DesktopSportIdentTimeSyncCommandStep = SportIdentTimeSyncCommandStep

internal object DesktopSportIdentTimeSyncProtocol {
    val SET_STATION_TIME_COMMAND: Byte = SportIdentTimeSyncProtocol.SET_STATION_TIME_COMMAND
    val GET_STATION_TIME_COMMAND: Byte = SportIdentTimeSyncProtocol.GET_STATION_TIME_COMMAND
    val POWER_OFF_COMMAND: Byte = SportIdentTimeSyncProtocol.POWER_OFF_COMMAND
    val APPLY_STATION_TIME_COMMAND: Byte = SportIdentTimeSyncProtocol.APPLY_STATION_TIME_COMMAND
    val REMOTE_POWER_OFF_BYTES: ByteArray = byteArrayOf(
        0xFF.toByte(),
        0x40,
        0x0F,
        0x80.toByte(),
        0xB2.toByte(),
        0xB6.toByte(),
        0x50,
        0xC0.toByte()
    )

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
            readStationTimeStep("Read station time after apply"),
            exitRemoteModeStep()
        )

    fun enterRemoteModeStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.enterRemoteModeStep()

    fun selectDirectStationStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.selectDirectStationStep()

    fun readSystemInfoStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.readSystemInfoStep()

    fun readCompatibleSystemInfoStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.readCompatibleSystemInfoStep()

    fun readStationTimeStep(label: String): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.readStationTimeStep(label)

    fun writeStationTimeStep(sourceTime: LocalDateTime): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.writeStationTimeStep(sourceTime)

    fun applyStationTimeStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.applyStationTimeStep()

    fun powerOffStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.powerOffStep()

    fun exitRemoteModeStep(): DesktopSportIdentTimeSyncCommandStep =
        SportIdentTimeSyncProtocol.exitRemoteModeStep()
}
