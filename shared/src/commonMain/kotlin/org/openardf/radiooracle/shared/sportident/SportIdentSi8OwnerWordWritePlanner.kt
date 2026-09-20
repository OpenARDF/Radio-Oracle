package org.openardf.radiooracle.shared.sportident

import kotlinx.serialization.Serializable

@Serializable
data class SportIdentSi8OwnerWritePlanComparison(
    val plannedFramesHex: List<String>,
    val predictedVersusObserved: SportIdentOwnerNativeReadDiff
) {
    val matches: Boolean = predictedVersusObserved.sameStation && predictedVersusObserved.sameCard &&
        predictedVersusObserved.byteChanges.isEmpty()
    val scope: String = "Offline planned card bytes versus an independent native read; transmitted frames and write success are not proven"
}

/** A fresh raw read is compared with every possible prefix, never treated as permission to retry. */
data class SportIdentSi8OwnerInterruptionAssessment(
    val attemptedWordsUpperBound: Int,
    val matchingWordPrefixes: List<Int>,
    val byteChangesFromBaseline: List<SportIdentOwnerReadByteChange>,
    val changesOutsideOwnerWords: List<SportIdentOwnerReadByteChange>
)

@Serializable
data class SportIdentSi8OwnerNativeAttempt(
    val schemaVersion: Int,
    val request: SportIdentOwnerNameWriteRequest,
    val before: SportIdentOwnerReadFixture,
    /** Upper bound: persistence precedes serial write, so the last word may not have been sent. */
    val attemptedWords: Int
)

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

    fun assessInterruption(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture,
        attemptedWordsUpperBound: Int, fresh: SportIdentOwnerReadFixture): SportIdentSi8OwnerInterruptionAssessment {
        require(attemptedWordsUpperBound in 0..3)
        val frames = plan(request, before)
        require(fresh.stationNumber == before.stationNumber &&
            SportIdentOwnerReadVerification.rawCardNumber(fresh) == request.cardNumber) {
            "Fresh read must contain the expected SI-Card8 and station."
        }
        val baseline = (0..1).map { SportIdentOwnerReadVerification.blockBytes(before, it) }
        val observed = (0..1).map { SportIdentOwnerReadVerification.blockBytes(fresh, it) }
        val changes = (0..1).flatMap { block ->
            baseline[block].indices.mapNotNull { offset ->
                val old = baseline[block][offset].toInt() and 0xff
                val new = observed[block][offset].toInt() and 0xff
                if (old == new) null else SportIdentOwnerReadByteChange(block, offset, old, new)
            }
        }
        val matches = (0..3).filter { count ->
            val predicted = baseline.map { it.copyOf() }
            frames.take(count).forEach { bytes ->
                val frame = requireNotNull(SportIdentFrameParser.firstFrame(bytes))
                val address = frame.data[0].toInt() and 0xff
                frame.data.copyOfRange(1, 5).copyInto(predicted[0], address * WORD_BYTES)
            }
            predicted[0].contentEquals(observed[0]) && predicted[1].contentEquals(observed[1])
        }
        return SportIdentSi8OwnerInterruptionAssessment(attemptedWordsUpperBound, matches, changes,
            changes.filter { it.blockNumber != 0 || it.offset !in OWNER_OFFSET until OWNER_OFFSET + OBSERVED_TEXT_BYTES })
    }

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

    /** Replay planned word payloads into a copy of the before blocks; never opens a reader. */
    fun compareToObserved(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture,
        observedAfter: SportIdentOwnerReadFixture): SportIdentSi8OwnerWritePlanComparison {
        val frames = plan(request, before)
        val predictedBlocks = before.blocks.map { block ->
            SportIdentCardBlock(block.blockNumber, ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { offset ->
                block.hexData.substring(offset * 2, offset * 2 + 2).toInt(16).toByte()
            })
        }
        val ownerBlock = predictedBlocks.single { it.blockNumber == 0 }.data
        frames.forEach { bytes ->
            val frame = requireNotNull(SportIdentFrameParser.firstFrame(bytes)) { "Planned word frame is invalid." }
            require(frame.command == SportIdentProtocol.WRITE_SI_CARD_WORD && frame.extended &&
                frame.crcValid == true && frame.data.size == WORD_BYTES + 1) { "Planned word frame is invalid." }
            val wordAddress = frame.data[0].toInt() and 0xff
            require(wordAddress in FIRST_OWNER_WORD until FIRST_OWNER_WORD + frames.size) {
                "Planned word address is outside the owner text."
            }
            frame.data.copyOfRange(1, WORD_BYTES + 1).copyInto(ownerBlock, wordAddress * WORD_BYTES)
        }
        val predicted = SportIdentOwnerReadVerification.capture(before.stationNumber, predictedBlocks)
        val predictedRead = SportIdentOwnerReadVerification.nativeRead(predicted)
        require(predictedRead.firstName == request.firstName && predictedRead.lastName == request.lastName) {
            "Planned owner bytes do not parse as the requested names."
        }
        return SportIdentSi8OwnerWritePlanComparison(
            frames.map { bytes -> bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } },
            SportIdentOwnerReadVerification.diffNative(predicted, observedAfter)
        )
    }
}
