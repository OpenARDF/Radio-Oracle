package org.openardf.radiooracle.shared.sportident

enum class SportIdentOwnerDataStatus { READ, INCOMPLETE, NOT_SUPPORTED, UNSUPPORTED_ENCODING }

enum class SportIdentCardFamily(val label: String, val supportsClub: Boolean = true) {
    SI5("SI-Card5", false), SI6("SI-Card6 / SI-Card6*"),
    SI8("SI-Card8", false), SI9("SI-Card9", false), PCARD("pCard"),
    SI10("SI-Card10"), SI11("SI-Card11"), SIAC("SIAC"),
    MODERN_UNKNOWN("SI-Card10/11/SIAC (unrecognized number range)"), UNKNOWN("Unknown card family", false)
}

/** Owner inspection stays separate from the punch layout used by race readouts. */
data class SportIdentCardOwnerInspection(
    val siNumber: Int,
    val family: SportIdentCardFamily,
    val holder: SportIdentCardHolder?,
    val status: SportIdentOwnerDataStatus,
    val characterSet: Int? = null
)

object SportIdentCardOwnerInspector {
    /** Additional blocks required beyond the existing race download. */
    fun additionalBlocks(series: Int): List<Int> = when (series) {
        6 -> listOf(1)
        15 -> listOf(1, 2, 3)
        else -> emptyList()
    }

    fun inspect(readout: SportIdentCardReadout, blocks: List<SportIdentCardBlock>): SportIdentCardOwnerInspection {
        val byNumber = blocks.associateBy { it.blockNumber }
        val family = family(readout.series, readout.siNumber)
        if (readout.series == 5) {
            return SportIdentCardOwnerInspection(readout.siNumber, family, null, SportIdentOwnerDataStatus.NOT_SUPPORTED)
        }
        val required = when (readout.series) {
            6, 4 -> listOf(0, 1)
            1, 2 -> listOf(0)
            15 -> listOf(0, 1, 2, 3)
            else -> emptyList()
        }
        if (required.isEmpty() || required.any { it !in byNumber }) {
            return SportIdentCardOwnerInspection(readout.siNumber, family, readout.cardHolder, SportIdentOwnerDataStatus.INCOMPLETE)
        }
        val data = ByteArray((required.max() + 1) * SportIdentProtocol.SI_CARD_BLOCK_SIZE)
        required.forEach { byNumber.getValue(it).data.copyInto(data, it * SportIdentProtocol.SI_CARD_BLOCK_SIZE) }
        val characterSet = if (readout.series == 15) data[447].toInt() and 0xff else null
        val ownerBytes = when (readout.series) {
            6 -> data.copyOfRange(48, 132)
            1 -> data.copyOfRange(32, 56)
            2 -> data.copyOfRange(32, 128)
            else -> data.copyOfRange(32, 160)
        }
        // ASCII is safe across the supported layouts. For modern cards, accept
        // ISO-8859-1 explicitly; other non-ASCII encodings need a verified codec.
        val textBytes = ownerBytes.takeWhile { it != 0.toByte() && it != 0xEE.toByte() }
        val unsupportedEncoding = readout.series == 15 && characterSet != 1 &&
            textBytes.any { (it.toInt() and 0xff) >= 128 }
        if (unsupportedEncoding) {
            return SportIdentCardOwnerInspection(readout.siNumber, family, null,
                SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING, characterSet)
        }
        val holder = when (readout.series) {
            6 -> SportIdentCardHolder(text(data, 68, 20), text(data, 48, 20), text(data, 96, 36))
            1, 2 -> SportIdentCardReadoutParser.parseSemicolonCardHolder(data, readout.series)
                ?.copy(club = null)
            else -> {
                val fields = text(data, 32, 128)?.split(';').orEmpty()
                SportIdentCardHolder(fields.getOrNull(0)?.ifBlank { null },
                    fields.getOrNull(1)?.ifBlank { null }, fields.getOrNull(4)?.ifBlank { null })
            }
        }?.takeIf { it.displayName != null || !it.club.isNullOrBlank() }
        return SportIdentCardOwnerInspection(readout.siNumber, family, holder, SportIdentOwnerDataStatus.READ, characterSet)
    }

    private fun text(data: ByteArray, offset: Int, length: Int): String? =
        data.copyOfRange(offset, offset + length).toSportIdentOwnerText()

    private fun family(series: Int, number: Int): SportIdentCardFamily = when (series) {
        5 -> SportIdentCardFamily.SI5
        6 -> SportIdentCardFamily.SI6
        2 -> SportIdentCardFamily.SI8
        1 -> SportIdentCardFamily.SI9
        4 -> SportIdentCardFamily.PCARD
        15 -> when (number) {
            in 7_000_000..7_999_999 -> SportIdentCardFamily.SI10
            in 8_000_000..8_999_999 -> SportIdentCardFamily.SIAC
            in 9_000_000..9_999_999 -> SportIdentCardFamily.SI11
            else -> SportIdentCardFamily.MODERN_UNKNOWN
        }
        else -> SportIdentCardFamily.UNKNOWN
    }
}
