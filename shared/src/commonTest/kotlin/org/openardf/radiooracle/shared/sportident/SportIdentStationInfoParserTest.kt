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
        assertEquals(5, info.stationCode)
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
        assertEquals(null, info.stationCode)
        assertEquals(null, info.stationModeLabel)
        assertEquals(null, info.isReadoutMode)
    }

    @Test
    fun labelsNonReadoutStationCodeAsControlMode() {
        val frame = assertNotNull(
            SportIdentFrameParser.firstFrame(
                SportIdentProtocol.buildExtendedMessage(
                    command = SportIdentProtocol.GET_SYSTEM_INFO,
                    data = systemInfoData(serialNumber = 554896, extendedMode = true, stationCode = 40, size = 120)
                ),
                commandFilter = SportIdentProtocol.GET_SYSTEM_INFO
            )
        )

        val info = assertNotNull(SportIdentStationInfoParser.fromSystemInfoFrame(frame))

        assertEquals(40, info.stationCode)
        assertEquals("CONTROL 40", info.stationModeLabel)
        assertEquals(false, info.isReadoutMode)
    }

    private fun systemInfoData(
        serialNumber: Int,
        extendedMode: Boolean,
        stationCode: Int = 5,
        size: Int
    ): ByteArray =
        ByteArray(size).also { data ->
            data[3] = ((serialNumber ushr 24) and 0xff).toByte()
            data[4] = ((serialNumber ushr 16) and 0xff).toByte()
            data[5] = ((serialNumber ushr 8) and 0xff).toByte()
            data[6] = (serialNumber and 0xff).toByte()
            if (data.size > 20) {
                data[20] = (stationCode and 0xff).toByte()
            }
            if (data.size > 119 && extendedMode) {
                data[119] = 0x01
            }
        }
}
