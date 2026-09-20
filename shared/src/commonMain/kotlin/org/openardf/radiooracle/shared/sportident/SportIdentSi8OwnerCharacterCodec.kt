package org.openardf.radiooracle.shared.sportident

/**
 * SI-Card8 default-character-set owner text. The substitutions match the public
 * SPORTident CardPersonalData.ReplacePrinterCharsetBytes conversion. Hardware
 * reads of José and Bjørn confirmed 0x82 for é and 0xF8 for ø respectively.
 */
internal object SportIdentSi8OwnerCharacterCodec {
    private val replacements = mapOf(
        0xC4 to 0x8E, 0xC5 to 0x8F, 0xC6 to 0x92, 0xC7 to 0x80, 0xC9 to 0x90,
        0xD1 to 0xA5, 0xD6 to 0x99, 0xDC to 0x9A, 0xDF to 0xE1,
        0xE0 to 0x85, 0xE1 to 0xA0, 0xE2 to 0x83, 0xE4 to 0x84, 0xE5 to 0x86,
        0xE6 to 0x91, 0xE7 to 0x87, 0xE8 to 0x8A, 0xE9 to 0x82, 0xEA to 0x88,
        0xEB to 0x89, 0xEC to 0x8D, 0xED to 0xA1, 0xEE to 0x8C, 0xEF to 0x8B,
        0xF1 to 0xA4, 0xF2 to 0x95, 0xF3 to 0xA2, 0xF4 to 0x93, 0xF6 to 0x94,
        0xF9 to 0x97, 0xFA to 0xA3, 0xFB to 0x96, 0xFC to 0x81, 0xFF to 0x98
    )
    private val reverse = replacements.entries.associate { (source, stored) -> stored to source }

    fun encode(text: String): ByteArray? {
        val bytes = ByteArray(text.length)
        text.forEachIndexed { index, char ->
            val code = char.code
            if (code !in 0x20..0x7E && code !in 0xA0..0xFF) return null
            val stored = replacements[code] ?: code
            if (stored == 0xEE || decodeByte(stored) != char) return null
            bytes[index] = stored.toByte()
        }
        return bytes
    }

    fun decode(bytes: ByteArray): String? = bytes
        .takeWhile { it != 0.toByte() && it != 0xEE.toByte() }
        .map { decodeByte(it.toInt() and 0xFF) }
        .filterNot { it.isISOControl() || it == '\u007f' }
        .joinToString("").trim().ifBlank { null }

    private fun decodeByte(stored: Int): Char = (reverse[stored] ?: stored).toChar()
}
