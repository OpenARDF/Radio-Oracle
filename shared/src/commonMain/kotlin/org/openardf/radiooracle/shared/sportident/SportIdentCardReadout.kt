package org.openardf.radiooracle.shared.sportident

data class SportIdentCardReadout(
    val siNumber: Int,
    val series: Int,
    val checkTime: SportIdentTime?,
    val startTime: SportIdentTime?,
    val finishTime: SportIdentTime?,
    val punches: List<SportIdentCardPunch>
)

data class SportIdentCardPunch(
    val siCode: Int,
    val siTime: SportIdentTime
)

object SportIdentCardReadoutParser {
    fun parseSi5(data: ByteArray): SportIdentCardReadout? {
        if (data.size < SI5_RESPONSE_DATA_BYTES) {
            return null
        }

        val cardDataOffset = SI5_CARD_DATA_OFFSET
        val siNumber = si5Number(
            lowHigh = data[cardDataOffset + 4],
            lowLow = data[cardDataOffset + 5],
            high = data[cardDataOffset + 6]
        )
        val punchCount = (data[cardDataOffset + 23].toUnsignedInt() - 1)
            .coerceIn(0, SI_CARD5_MAX_PUNCHES)

        val punches = buildList {
            repeat(punchCount) { index ->
                val offset = cardDataOffset + 32 + index / 5 * 16 + 1 + 3 * (index % 5)
                val punch = parseSi5Punch(data.copyOfRange(offset, offset + SI5_PUNCH_BYTES)) ?: return null
                add(punch)
            }
        }

        return SportIdentCardReadout(
            siNumber = siNumber,
            series = SI_CARD5_SERIES,
            checkTime = parseSi5Time(data.copyOfRange(cardDataOffset + 25, cardDataOffset + 27)),
            startTime = parseSi5Time(data.copyOfRange(cardDataOffset + 19, cardDataOffset + 21)),
            finishTime = parseSi5Time(data.copyOfRange(cardDataOffset + 21, cardDataOffset + 23)),
            punches = punches
        )
    }

    fun parseSi6(data: ByteArray): SportIdentCardReadout? {
        if (data.size < SI6_MIN_BYTES) {
            return null
        }

        val punchCount = minOf(data[18].toUnsignedInt(), SI_CARD6_MAX_PUNCHES)
        val punchStartOffset = SportIdentProtocol.SI_CARD_BLOCK_SIZE
        if (data.size < punchStartOffset + punchCount * PUNCH_BYTES) {
            return null
        }

        val punches = buildList {
            repeat(punchCount) { index ->
                val offset = punchStartOffset + index * PUNCH_BYTES
                val punch = parsePunch(data.copyOfRange(offset, offset + PUNCH_BYTES)) ?: return null
                add(punch)
            }
        }

        return SportIdentCardReadout(
            siNumber = (data[10].toUnsignedInt() shl 24) +
                (data[11].toUnsignedInt() shl 16) +
                (data[12].toUnsignedInt() shl 8) +
                data[13].toUnsignedInt(),
            series = SI_CARD6_SERIES,
            checkTime = parsePunch(data.copyOfRange(28, 32))?.siTime,
            startTime = parsePunch(data.copyOfRange(24, 28))?.siTime,
            finishTime = parsePunch(data.copyOfRange(20, 24))?.siTime,
            punches = punches
        )
    }

    fun parseSi8Or9OrSiac(data: ByteArray): SportIdentCardReadout? {
        if (data.size < SportIdentProtocol.SI_CARD_BLOCK_SIZE) {
            return null
        }

        val series = data[24].toUnsignedInt() and SI_CARD10_11_SIAC_SERIES
        val punchCount = data[22].toUnsignedInt()
        val maxPunches = when (series) {
            SI_CARD8_SERIES -> SI_CARD8_MAX_PUNCHES
            SI_CARD9_SERIES -> SI_CARD9_MAX_PUNCHES
            SI_CARD_PCARD_SERIES -> SI_CARD_PCARD_MAX_PUNCHES
            SI_CARD10_11_SIAC_SERIES -> SI_CARD10_11_SIAC_MAX_PUNCHES
            else -> return null
        }
        val punchStartOffset = when (series) {
            SI_CARD8_SERIES -> 34 * PUNCH_BYTES
            SI_CARD9_SERIES -> 14 * PUNCH_BYTES
            SI_CARD_PCARD_SERIES -> 44 * PUNCH_BYTES
            SI_CARD10_11_SIAC_SERIES -> SportIdentProtocol.SI_CARD_BLOCK_SIZE
            else -> return null
        }
        val punchesToRead = minOf(punchCount, maxPunches)
        if (data.size < punchStartOffset + punchesToRead * PUNCH_BYTES) {
            return null
        }

        val punches = buildList {
            repeat(punchesToRead) { index ->
                val offset = punchStartOffset + index * PUNCH_BYTES
                val punch = parsePunch(data.copyOfRange(offset, offset + PUNCH_BYTES)) ?: return null
                add(punch)
            }
        }

        return SportIdentCardReadout(
            siNumber = (data[25].toUnsignedInt() shl 16) +
                (data[26].toUnsignedInt() shl 8) +
                data[27].toUnsignedInt(),
            series = series,
            checkTime = parsePunch(data.copyOfRange(8, 12))?.siTime,
            startTime = parsePunch(data.copyOfRange(12, 16))?.siTime,
            finishTime = parsePunch(data.copyOfRange(16, 20))?.siTime,
            punches = punches
        )
    }

    private fun si5Number(
        lowHigh: Byte,
        lowLow: Byte,
        high: Byte
    ): Int {
        val low = (lowHigh.toUnsignedInt() shl 8) + lowLow.toUnsignedInt()
        val highValue = high.toUnsignedInt()
        return when {
            high == SportIdentProtocol.ZERO || highValue == 0x01 -> low
            highValue < 5 -> highValue * 100_000 + low
            else -> (highValue shl 16) + low
        }
    }

    private fun parseSi5Punch(data: ByteArray): SportIdentCardPunch? {
        if (data.size != SI5_PUNCH_BYTES) {
            return null
        }
        val time = parseSi5Time(data.copyOfRange(1, 3)) ?: return null
        return SportIdentCardPunch(
            siCode = data[0].toUnsignedInt(),
            siTime = time
        )
    }

    private fun parseSi5Time(data: ByteArray): SportIdentTime? {
        if (data.size != SI5_TIME_BYTES || data.all { it == NULL }) {
            return null
        }
        val seconds = (data[0].toUnsignedInt() shl 8) or data[1].toUnsignedInt()
        if (seconds !in 0 until SportIdentCodes.SECONDS_DAY.toInt()) {
            return null
        }
        return SportIdentTime(seconds.toLong())
    }

    private fun parsePunch(data: ByteArray): SportIdentCardPunch? {
        if (data.size != PUNCH_BYTES) {
            return null
        }
        if (data.all { it == NULL }) {
            return null
        }

        val seconds = (data[2].toUnsignedInt() shl 8) or data[3].toUnsignedInt()
        if (seconds !in 0 until SportIdentCodes.SECONDS_DAY.toInt()) {
            return null
        }

        val time = SportIdentTime(seconds.toLong())
        time.setDayOfWeek(data[0].toInt() shr 1 and 0x07)
        if (data[0].toInt() and 0x01 == 0x01) {
            time.addHalfDay()
        }

        return SportIdentCardPunch(
            siCode = data[1].toUnsignedInt() + 256 * (data[0].toUnsignedInt() shr 6 and 0x03),
            siTime = time
        )
    }

    private const val PUNCH_BYTES = 4
    private const val SI5_PUNCH_BYTES = 3
    private const val SI5_TIME_BYTES = 2
    private const val NULL: Byte = 0xEE.toByte()
    private const val SI_CARD5_SERIES = 5
    private const val SI_CARD6_SERIES = 6
    private const val SI_CARD8_SERIES = 2
    private const val SI_CARD9_SERIES = 1
    private const val SI_CARD_PCARD_SERIES = 4
    private const val SI_CARD10_11_SIAC_SERIES = 15
    private const val SI_CARD5_MAX_PUNCHES = 30
    private const val SI_CARD6_MAX_PUNCHES = 192
    private const val SI_CARD8_MAX_PUNCHES = 30
    private const val SI_CARD9_MAX_PUNCHES = 50
    private const val SI_CARD_PCARD_MAX_PUNCHES = 20
    private const val SI_CARD10_11_SIAC_MAX_PUNCHES = 128
    private const val SI5_RESPONSE_DATA_BYTES = 130
    private const val SI5_CARD_DATA_OFFSET = 2
    private const val SI6_MIN_BYTES = SportIdentProtocol.SI_CARD_BLOCK_SIZE * 2
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
