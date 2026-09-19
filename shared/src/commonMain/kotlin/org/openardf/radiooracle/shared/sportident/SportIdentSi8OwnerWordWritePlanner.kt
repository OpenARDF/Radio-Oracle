package org.openardf.radiooracle.shared.sportident

/**
 * Reproduces the one observed Config+ SI-Card8 owner-write shape offline.
 * Nothing calls a serial transport here; other lengths and acknowledgement
 * semantics must be characterized before these frames can be sent to a card.
 */
object SportIdentSi8OwnerWordWritePlanner {
    private const val OWNER_OFFSET = 0x20
    private const val FIRST_OWNER_WORD = 0x08
    private const val WORD_BYTES = 4
    private const val OBSERVED_TEXT_BYTES = 12
    private const val OBSERVED_SHORT_TEXT_BYTES = 11
    private val ERASED_NAME_BYTE = 0xEE.toByte()

    fun plan(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture): List<ByteArray> {
        val read = SportIdentOwnerReadVerification.nativeRead(before)
        val inspection = SportIdentCardOwnerInspection(
            siNumber = read.cardNumber,
            family = SportIdentCardFamily.SI8,
            holder = SportIdentCardHolder(read.firstName.ifBlank { null }, read.lastName.ifBlank { null }),
            status = SportIdentOwnerDataStatus.READ
        )
        val prepared = SportIdentOwnerNameProgramming.prepare(
            inspection, before.stationNumber, request.firstName, request.lastName, request.acceptPossiblePunchLoss
        )
        require(request == prepared) { "Write request does not match the supplied SI-Card8 read." }

        val oldText = requireNotNull(
            SportIdentSi8OwnerNamePlanner.preview(inspection, read.firstName, read.lastName).encodedOwnerText
        ) { "Stored SI-Card8 names cannot be encoded exactly." }
        require(oldText.size <= OBSERVED_TEXT_BYTES) {
            "Writing fewer owner bytes than the current text would require an unverified cleanup rule."
        }
        val block0Hex = before.blocks.single { it.blockNumber == 0 }.hexData
        val storedPrefix = ByteArray(oldText.size) { index ->
            block0Hex.substring((OWNER_OFFSET + index) * 2, (OWNER_OFFSET + index + 1) * 2).toInt(16).toByte()
        }
        require(storedPrefix.contentEquals(oldText)) { "Raw owner bytes differ from the parsed names." }

        val newText = requireNotNull(
            SportIdentSi8OwnerNamePlanner.preview(inspection, request.firstName, request.lastName).encodedOwnerText
        )
        require(newText.size == OBSERVED_TEXT_BYTES || newText.size == OBSERVED_SHORT_TEXT_BYTES) {
            "Only the observed 11- or 12-byte SI-Card8 owner-write shapes can be planned."
        }
        if (newText.size == OBSERVED_SHORT_TEXT_BYTES) {
            require(oldText.size == OBSERVED_TEXT_BYTES) {
                "The 11-byte owner-write shape was observed only after a 12-byte name."
            }
        }
        // A paired native capture after a Mac SDK 12-to-11-byte name change
        // showed 0xEE in the twelfth slot and older residual bytes untouched.
        val paddedText = if (newText.size == OBSERVED_SHORT_TEXT_BYTES) newText + ERASED_NAME_BYTE else newText
        return (0 until OBSERVED_TEXT_BYTES / WORD_BYTES).map { index ->
            val payload = byteArrayOf((FIRST_OWNER_WORD + index).toByte()) +
                paddedText.copyOfRange(index * WORD_BYTES, (index + 1) * WORD_BYTES)
            SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.WRITE_SI_CARD_WORD, payload)
        }
    }
}
