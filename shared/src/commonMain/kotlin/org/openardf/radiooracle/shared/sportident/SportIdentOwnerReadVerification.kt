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

/** Replay ordinary card reads through the production Kotlin parsers for SDK comparison. */
object SportIdentOwnerReadVerification {
    fun capture(stationNumber: Int, blocks: List<SportIdentCardBlock>): SportIdentOwnerReadFixture {
        val fixture = SportIdentOwnerReadFixture(1, stationNumber, blocks.map { block ->
            SportIdentOwnerReadBlock(block.blockNumber, block.data.joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            })
        })
        nativeRead(fixture)
        return fixture
    }

    fun nativeRead(fixture: SportIdentOwnerReadFixture): SportIdentOwnerReferenceRead {
        require(fixture.schemaVersion == 1 && fixture.stationNumber > 0) { "Invalid native read version or station." }
        require(fixture.blocks.size == 2 && fixture.blocks.map { it.blockNumber }.toSet() == setOf(0, 1)) {
            "A complete SI-Card8 read requires blocks 0 and 1 without duplicates."
        }
        val blocks = fixture.blocks.sortedBy { it.blockNumber }.map { block ->
            require(block.hexData.length == SportIdentProtocol.SI_CARD_BLOCK_SIZE * 2 &&
                block.hexData.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Invalid card-block hex data." }
            SportIdentCardBlock(block.blockNumber, ByteArray(SportIdentProtocol.SI_CARD_BLOCK_SIZE) {
                block.hexData.substring(it * 2, it * 2 + 2).toInt(16).toByte()
            })
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

    private fun validate(read: SportIdentOwnerReferenceRead) {
        require(read.schemaVersion == 1 && read.stationNumber > 0 && read.cardNumber > 0 &&
            read.cardType == SportIdentCardFamily.SI8.label && read.controlPunchCount in 0..30) { "Invalid SI-Card8 reference read." }
    }
}
