package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SportIdentCardReadoutParserTest {
    @Test
    fun parsesSi6CardReadout() {
        val data = ByteArray(2 * SportIdentProtocol.SI_CARD_BLOCK_SIZE) { 0xEE.toByte() }
        val cardNumber = 2_005_010
        data[10] = ((cardNumber ushr 24) and 0xff).toByte()
        data[11] = ((cardNumber ushr 16) and 0xff).toByte()
        data[12] = ((cardNumber ushr 8) and 0xff).toByte()
        data[13] = (cardNumber and 0xff).toByte()
        data[18] = 2
        writePunch(data, 28, 31, 10 * 3600)
        writePunch(data, 24, 32, 10 * 60 + 30)
        writePunch(data, 20, 33, 11 * 3600)
        writePunch(data, SportIdentProtocol.SI_CARD_BLOCK_SIZE, 41, 12 * 60)
        writePunch(data, SportIdentProtocol.SI_CARD_BLOCK_SIZE + 4, 42, 20 * 60)

        val readout = assertNotNull(SportIdentCardReadoutParser.parseSi6(data))

        assertEquals(cardNumber, readout.siNumber)
        assertEquals(6, readout.series)
        assertEquals("10:00:00", readout.checkTime?.getTimeString())
        assertEquals("00:10:30", readout.startTime?.getTimeString())
        assertEquals("11:00:00", readout.finishTime?.getTimeString())
        assertEquals(listOf(41, 42), readout.punches.map { it.siCode })
    }

    @Test
    fun parsesSi8CardReadout() {
        val data = ByteArray(2 * SportIdentProtocol.SI_CARD_BLOCK_SIZE) { 0xEE.toByte() }
        val cardNumber = 2_005_010
        data[22] = 2
        data[24] = 2
        data[25] = ((cardNumber ushr 16) and 0xff).toByte()
        data[26] = ((cardNumber ushr 8) and 0xff).toByte()
        data[27] = (cardNumber and 0xff).toByte()
        writePunch(data, 8, 31, 10 * 3600)
        writePunch(data, 12, 32, 10 * 60 + 30)
        writePunch(data, 16, 33, 11 * 3600)
        writePunch(data, 34 * 4, 41, 12 * 60)
        writePunch(data, 34 * 4 + 4, 42, 20 * 60)

        val readout = assertNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(data))

        assertEquals(cardNumber, readout.siNumber)
        assertEquals(2, readout.series)
        assertEquals("10:00:00", readout.checkTime?.getTimeString())
        assertEquals("00:10:30", readout.startTime?.getTimeString())
        assertEquals("11:00:00", readout.finishTime?.getTimeString())
        assertEquals(listOf(41, 42), readout.punches.map { it.siCode })
    }

    private fun writePunch(data: ByteArray, offset: Int, code: Int, seconds: Int) {
        data[offset] = ((code / 256) shl 6).toByte()
        data[offset + 1] = (code and 0xff).toByte()
        data[offset + 2] = ((seconds ushr 8) and 0xff).toByte()
        data[offset + 3] = (seconds and 0xff).toByte()
    }
}
