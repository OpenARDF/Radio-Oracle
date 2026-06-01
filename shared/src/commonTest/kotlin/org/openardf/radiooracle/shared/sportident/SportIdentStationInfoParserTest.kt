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
}
