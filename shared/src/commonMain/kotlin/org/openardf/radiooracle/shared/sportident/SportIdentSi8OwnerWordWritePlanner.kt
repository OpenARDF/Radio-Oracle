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
) {
    val plausibleWordPrefixes: List<Int> = matchingWordPrefixes.filter { it <= attemptedWordsUpperBound }
    val consistentWithRecordedAttempt: Boolean = plausibleWordPrefixes.isNotEmpty() &&
        changesOutsideOwnerWords.isEmpty()
}

@Serializable
data class SportIdentSi8OwnerNativeAttempt(
    val schemaVersion: Int,
    val request: SportIdentOwnerNameWriteRequest,
    val before: SportIdentOwnerReadFixture,
    /** Upper bound: persistence precedes serial write, so the last word may not have been sent. */
    val attemptedWords: Int
)

/**
 * Plans SI-Card8 owner words only. This is shared, transport-free code; a
 * caller must confirm station/card identity and independently verify both
 * complete card blocks after transmitting any frames.
 */
object SportIdentSi8OwnerWordWritePlanner {
    private const val OWNER_OFFSET = 0x20
    private const val FIRST_OWNER_WORD = 0x08
    private const val WORD_BYTES = 4
    private const val MAX_OWNER_TEXT_BYTES = SportIdentSi8OwnerNamePlanner.MAX_NAME_CHARACTERS + 2
    private val ERASED_NAME_BYTE = 0xEE.toByte()

    /** Limit real direct writes to the name transitions already completed on hardware. */
    fun hasPreviouslyVerifiedDirectShape(request: SportIdentOwnerNameWriteRequest,
        before: SportIdentOwnerReadFixture): Boolean {
        val frames = plan(request, before)
        val old = SportIdentOwnerReadVerification.nativeRead(before)
        if ((request.firstName + request.lastName + old.firstName + old.lastName).any { it !in ' '..'~' }) {
            return false
        }
        val oldBytes = old.firstName.length + old.lastName.length + 2
        val newBytes = request.firstName.length + request.lastName.length + 2
        return frames.size == 3 &&
            ((newBytes == 12 && oldBytes <= 12) || (newBytes == 11 && oldBytes == 12))
    }

    fun assessInterruption(request: SportIdentOwnerNameWriteRequest, before: SportIdentOwnerReadFixture,
        attemptedWordsUpperBound: Int, fresh: SportIdentOwnerReadFixture): SportIdentSi8OwnerInterruptionAssessment {
        val frames = plan(request, before)
        require(attemptedWordsUpperBound in 0..frames.size)
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
        val matches = (0..frames.size).filter { count ->
            val predicted = baseline.map { it.copyOf() }
            frames.take(count).forEach { bytes ->
                val frame = requireNotNull(SportIdentFrameParser.firstFrame(bytes))
                val address = frame.data[0].toInt() and 0xff
                frame.data.copyOfRange(1, 5).copyInto(predicted[0], address * WORD_BYTES)
            }
            predicted[0].contentEquals(observed[0]) && predicted[1].contentEquals(observed[1])
        }
        return SportIdentSi8OwnerInterruptionAssessment(attemptedWordsUpperBound, matches, changes,
            changes.filter { it.blockNumber != 0 || it.offset !in OWNER_OFFSET until OWNER_OFFSET + frames.size * WORD_BYTES })
    }

    /** Offline proposal only: restore the saved owner words after a compatible fresh raw read. */
    fun planBaselineRestoreAfterInterruption(request: SportIdentOwnerNameWriteRequest,
        before: SportIdentOwnerReadFixture, attemptedWordsUpperBound: Int,
        fresh: SportIdentOwnerReadFixture): List<ByteArray> {
        val assessment = assessInterruption(request, before, attemptedWordsUpperBound, fresh)
        require(assessment.consistentWithRecordedAttempt) {
            "Fresh card bytes do not match a possible saved word prefix without other changes."
        }
        if (assessment.byteChangesFromBaseline.isEmpty()) return emptyList()
        val originalOwnerBlock = SportIdentOwnerReadVerification.blockBytes(before, 0)
        return (0 until plan(request, before).size).map { index ->
            val offset = OWNER_OFFSET + index * WORD_BYTES
            val payload = byteArrayOf((FIRST_OWNER_WORD + index).toByte()) +
                originalOwnerBlock.copyOfRange(offset, offset + WORD_BYTES)
            SportIdentProtocol.buildExtendedMessage(SportIdentProtocol.WRITE_SI_CARD_WORD, payload)
        }
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
        require(oldText.size in 2..MAX_OWNER_TEXT_BYTES)
        val block0Hex = before.blocks.single { it.blockNumber == 0 }.hexData
        val storedPrefix = ByteArray(oldText.size) { index ->
            block0Hex.substring((OWNER_OFFSET + index) * 2, (OWNER_OFFSET + index + 1) * 2).toInt(16).toByte()
        }
        require(storedPrefix.contentEquals(oldText)) { "Raw owner bytes differ from the parsed names." }

        val newText = requireNotNull(
            SportIdentSi8OwnerNamePlanner.preview(inspection, request.firstName, request.lastName).encodedOwnerText
        )
        require(newText.size in 2..MAX_OWNER_TEXT_BYTES)
        // Independent SDK card images confirmed 0xEE final-word padding for
        // 10 and 25 bytes, and that a 25-to-12-byte change leaves older words
        // untouched. Only the words covering the new text are transmitted.
        val wordCount = (newText.size + WORD_BYTES - 1) / WORD_BYTES
        val paddedText = ByteArray(wordCount * WORD_BYTES) { index ->
            if (index < newText.size) newText[index] else ERASED_NAME_BYTE
        }
        return (0 until wordCount).map { index ->
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
