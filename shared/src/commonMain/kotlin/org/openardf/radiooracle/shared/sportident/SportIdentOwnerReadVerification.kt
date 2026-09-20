package org.openardf.radiooracle.shared.sportident

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Matches the existing SDK probe's read-only JSON output, not its write result. */
@Serializable
data class SportIdentOwnerReferenceRead(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("StationNumber") val stationNumber: Int,
    @SerialName("CardNumber") val cardNumber: Int,
    @SerialName("CardType") val cardType: String,
    @SerialName("FirstName") val firstName: String,
    @SerialName("LastName") val lastName: String,
    @SerialName("ControlPunchCount") val controlPunchCount: Int
)

@Serializable
data class SportIdentOwnerReadBlock(
    @SerialName("BlockNumber") val blockNumber: Int,
    @SerialName("HexData") val hexData: String
)

/** Immutable native read evidence; no license material or SDK internals. */
@Serializable
data class SportIdentOwnerReadFixture(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("StationNumber") val stationNumber: Int,
    @SerialName("Blocks") val blocks: List<SportIdentOwnerReadBlock>
)

@Serializable
enum class SportIdentOwnerReadDifference { STATION_NUMBER, CARD_NUMBER, FIRST_NAME, LAST_NAME, CONTROL_PUNCH_COUNT }

@Serializable
data class SportIdentOwnerReadComparison(val differences: Set<SportIdentOwnerReadDifference>) {
    val matches: Boolean = differences.isEmpty()
    // Count equality is not evidence that punch values or unrelated settings were preserved.
    val scope: String = "Owner names, station/card identity, and control punch count only"
}

@Serializable
data class SportIdentOwnerReadByteChange(
    val blockNumber: Int,
    val offset: Int,
    val before: Int,
    val after: Int
)

/** Reports every byte change; owner-region changes still need interpretation. */
@Serializable
data class SportIdentOwnerNativeReadDiff(
    val before: SportIdentOwnerReferenceRead,
    val after: SportIdentOwnerReferenceRead,
    val byteChanges: List<SportIdentOwnerReadByteChange>
) {
    val sameStation: Boolean = before.stationNumber == after.stationNumber
    val sameCard: Boolean = before.cardNumber == after.cardNumber
    val samePunchCount: Boolean = before.controlPunchCount == after.controlPunchCount
    val changesOutsideOwnerRegion: List<SportIdentOwnerReadByteChange> = byteChanges.filter {
        it.blockNumber != 0 || it.offset !in 0x20..0x7f
    }
    val scope: String = "Complete SI-Card8 blocks 0 and 1; byte changes are observations, not write verification"
}

/** Replay ordinary card reads through the production Kotlin parsers for SDK comparison. */
object SportIdentOwnerReadVerification {
    /** Decode one immutable evidence block for a later same-card read-only recheck. */
    fun blockBytes(fixture: SportIdentOwnerReadFixture, blockNumber: Int): ByteArray {
        require(blockNumber in 0..1) { "Only SI-Card8 blocks 0 and 1 are supported." }
        require(fixture.blocks.count { it.blockNumber == blockNumber } == 1) {
            "The requested SI-Card8 block must appear exactly once."
        }
        val hex = fixture.blocks.single { it.blockNumber == blockNumber }.hexData
        require(hex.length == SportIdentProtocol.SI_CARD_BLOCK_SIZE * 2 &&
            hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid card-block hex data." }
        return ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) { offset ->
            hex.substring(offset * 2, offset * 2 + 2).toInt(16).toByte()
        }
    }

    fun capture(stationNumber: Int, blocks: List<SportIdentCardBlock>): SportIdentOwnerReadFixture {
        val fixture = captureRaw(stationNumber, blocks)
        nativeRead(fixture)
        return fixture
    }

    /** Preserve complete SI-Card8 evidence even when interrupted owner bytes are not parseable. */
    fun captureRaw(stationNumber: Int, blocks: List<SportIdentCardBlock>): SportIdentOwnerReadFixture {
        val fixture = SportIdentOwnerReadFixture(1, stationNumber, blocks.map { block ->
            SportIdentOwnerReadBlock(block.blockNumber, block.data.joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            })
        })
        rawCardNumber(fixture)
        return fixture
    }

    fun rawCardNumber(fixture: SportIdentOwnerReadFixture): Int {
        require(fixture.schemaVersion == 1 && fixture.stationNumber > 0 &&
            fixture.blocks.size == 2 && fixture.blocks.map { it.blockNumber }.toSet() == setOf(0, 1)) {
            "A complete SI-Card8 read requires blocks 0 and 1 without duplicates."
        }
        val data = blockBytes(fixture, 0) + blockBytes(fixture, 1)
        require((data[24].toInt() and 0x0f) == 2 && (data[22].toInt() and 0xff) <= 30) {
            "Read evidence must contain an SI-Card8 with a valid punch count."
        }
        return requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(data)) {
            "Card data could not be parsed."
        }.siNumber.also { require(it > 0) }
    }

    fun nativeRead(fixture: SportIdentOwnerReadFixture): SportIdentOwnerReferenceRead {
        require(fixture.schemaVersion == 1 && fixture.stationNumber > 0) { "Invalid native read version or station." }
        require(fixture.blocks.size == 2 && fixture.blocks.map { it.blockNumber }.toSet() == setOf(0, 1)) {
            "A complete SI-Card8 read requires blocks 0 and 1 without duplicates."
        }
        val blocks = fixture.blocks.sortedBy { it.blockNumber }.map { block ->
            SportIdentCardBlock(block.blockNumber, blockBytes(fixture, block.blockNumber))
        }
        val data = blocks.flatMap { it.data.toList() }.toByteArray()
        require((data[24].toInt() and 0x0f) == 2 && (data[22].toInt() and 0xff) <= 30) {
            "Read evidence must contain an SI-Card8 with a valid punch count."
        }
        val readout = requireNotNull(SportIdentCardReadoutParser.parseSi8Or9OrSiac(data)) { "Card data could not be parsed." }
        val inspection = SportIdentCardOwnerInspector.inspect(readout, blocks)
        require(inspection.status == SportIdentOwnerDataStatus.READ && inspection.family == SportIdentCardFamily.SI8)
        return SportIdentOwnerReferenceRead(1, fixture.stationNumber, readout.siNumber, inspection.family.label,
            inspection.holder?.firstName.orEmpty(), inspection.holder?.lastName.orEmpty(), readout.punches.size)
            .also(::validate)
    }

    fun compare(reference: SportIdentOwnerReferenceRead, fixture: SportIdentOwnerReadFixture): SportIdentOwnerReadComparison {
        validate(reference)
        val native = nativeRead(fixture)
        return SportIdentOwnerReadComparison(buildSet {
            if (reference.stationNumber != native.stationNumber) add(SportIdentOwnerReadDifference.STATION_NUMBER)
            if (reference.cardNumber != native.cardNumber) add(SportIdentOwnerReadDifference.CARD_NUMBER)
            if (reference.firstName != native.firstName) add(SportIdentOwnerReadDifference.FIRST_NAME)
            if (reference.lastName != native.lastName) add(SportIdentOwnerReadDifference.LAST_NAME)
            if (reference.controlPunchCount != native.controlPunchCount) add(SportIdentOwnerReadDifference.CONTROL_PUNCH_COUNT)
        })
    }

    /** Compare two independently captured native reads without opening a station. */
    fun diffNative(before: SportIdentOwnerReadFixture, after: SportIdentOwnerReadFixture): SportIdentOwnerNativeReadDiff {
        val beforeRead = nativeRead(before)
        val afterRead = nativeRead(after)
        val beforeBlocks = before.blocks.associateBy { it.blockNumber }
        val afterBlocks = after.blocks.associateBy { it.blockNumber }
        val changes = (0..1).flatMap { blockNumber ->
            val beforeHex = beforeBlocks.getValue(blockNumber).hexData
            val afterHex = afterBlocks.getValue(blockNumber).hexData
            (0 until SportIdentProtocol.SI_CARD_BLOCK_SIZE).mapNotNull { offset ->
                val byteIndex = offset * 2
                val old = beforeHex.substring(byteIndex, byteIndex + 2).toInt(16)
                val new = afterHex.substring(byteIndex, byteIndex + 2).toInt(16)
                if (old == new) null else SportIdentOwnerReadByteChange(blockNumber, offset, old, new)
            }
        }
        return SportIdentOwnerNativeReadDiff(beforeRead, afterRead, changes)
    }

    private fun validate(read: SportIdentOwnerReferenceRead) {
        require(read.schemaVersion == 1 && read.stationNumber > 0 && read.cardNumber > 0 &&
            read.cardType == SportIdentCardFamily.SI8.label && read.controlPunchCount in 0..30) { "Invalid SI-Card8 reference read." }
    }
}
