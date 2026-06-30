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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SportIdentStationInfoParserTest {
    @Test
    fun parsesLongSystemInfoFrame() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(serialNumber = 554896, extendedMode = true, size = 120)
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(554896, info.serialNumber)
        assertEquals(true, info.extendedMode)
        assertEquals(17, info.stationCodeNumber)
        assertEquals(5, info.stationModeCode)
        assertEquals("READOUT", info.stationModeLabel)
        assertEquals(true, info.isReadoutMode)
    }

    @Test
    fun parsesShortSystemInfoFrameAsNonExtended() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(serialNumber = 554896, extendedMode = false, size = 7)
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(554896, info.serialNumber)
        assertEquals(false, info.extendedMode)
        assertEquals(17, info.stationCodeNumber)
        assertEquals(null, info.stationModeCode)
        assertEquals(null, info.stationModeLabel)
        assertEquals(null, info.isReadoutMode)
    }

    @Test
    fun labelsFlaggedSiMasterModeAsNonReadoutMode() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(
                        serialNumber = 554896,
                        extendedMode = true,
                        stationCodeNumber = 17,
                        stationModeCode = 0x28,
                        size = 120
                    )
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(17, info.stationCodeNumber)
        assertEquals(0x28, info.stationModeCode)
        assertEquals("SI MASTER + 0x20 flag", info.stationModeLabel)
        assertEquals(false, info.isReadoutMode)
        assertEquals(true, info.isDownloadCapableMode)
    }

    @Test
    fun labelsSiMasterStationCodeAsReadoutMode() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(
                        serialNumber = 554900,
                        extendedMode = true,
                        stationCodeNumber = 14,
                        stationModeCode = 8,
                        size = 120
                    )
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(14, info.stationCodeNumber)
        assertEquals(8, info.stationModeCode)
        assertEquals("SI MASTER", info.stationModeLabel)
        assertEquals(true, info.isReadoutMode)
        assertEquals(true, info.isDownloadCapableMode)
    }

    @Test
    fun parsesExtendedSystemInfoDiagnostics() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(
                        serialNumber = 106128,
                        extendedMode = true,
                        stationCodeNumber = 32,
                        stationModeCode = 8,
                        size = 131
                    ).also { data ->
                        data.writeAscii(offset = 3 + 0x05, text = "656")
                        data.writeBytes(offset = 3 + 0x08, 0x08, 0x06, 0x10)
                        data.writeBytes(offset = 3 + 0x0B, 0x91, 0x97)
                        data[3 + 0x0D] = 0x80.toByte()
                        data.writeBytes(offset = 3 + 0x15, 0x17, 0x03, 0x1F)
                        data.writeBytes(offset = 3 + 0x50, 0x7F, 0xF8)
                        data[3 + 0x74] = 0x01
                        data.writeBytes(offset = 3 + 0x7E, 0x01, 0x2C)
                    }
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(106128, info.serialNumber)
        assertEquals("656", info.firmwareVersion)
        assertEquals(0x9197, info.modelId)
        assertEquals("BSM7-RS232/USB", info.modelName)
        assertEquals("2008-06-16", info.buildDate)
        assertEquals("2023-03-31", info.batteryDate)
        assertEquals(128, info.memorySizeKb)
        assertEquals(2.499, info.batteryVoltage!!, 0.001)
        assertEquals(0x01, info.protocolByte)
        assertEquals(300, info.activeTimeMinutes)
    }

    private fun systemInfoData(
        serialNumber: Int,
        extendedMode: Boolean,
        stationCodeNumber: Int = 17,
        stationModeCode: Int = 5,
        size: Int
    ): ByteArray =
        ByteArray(size).also { data ->
            if (data.size > 1) {
                data[1] = (stationCodeNumber and 0xff).toByte()
            }
            data[3] = ((serialNumber ushr 24) and 0xff).toByte()
            data[4] = ((serialNumber ushr 16) and 0xff).toByte()
            data[5] = ((serialNumber ushr 8) and 0xff).toByte()
            data[6] = (serialNumber and 0xff).toByte()
            if (data.size > 20) {
                data[20] = (stationModeCode and 0xff).toByte()
            }
            if (data.size > 119 && extendedMode) {
                data[119] = 0x01
            }
        }

    private fun ByteArray.writeAscii(offset: Int, text: String) {
        text.encodeToByteArray().copyInto(this, destinationOffset = offset)
    }

    private fun ByteArray.writeBytes(offset: Int, vararg values: Int) {
        values.forEachIndexed { index, value ->
            this[offset + index] = (value and 0xff).toByte()
        }
    }
}
