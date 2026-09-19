package org.openardf.radiooracle.shared.sportident

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentOwnerReadVerificationTest {
    private val reference = SportIdentOwnerReferenceRead(1, 593927, 2450662, "SI-Card8", "Daisy", "Duck", 1)

    @Test fun replaysProductionParsersAndMatchesTheSdkReadContract() {
        val sdkJson = """{"SchemaVersion":1,"StationNumber":593927,"CardNumber":2450662,"CardType":"SI-Card8","FirstName":"Daisy","LastName":"Duck","ControlPunchCount":1}"""
        val sdk = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReferenceRead>(sdkJson)
        val fixture = fixture()
        assertEquals(reference, SportIdentOwnerReadVerification.nativeRead(fixture))
        assertTrue(SportIdentOwnerReadVerification.compare(sdk, fixture).matches)
        val encoded = SportIdentOwnerNameProgramming.json.encodeToString(fixture)
        assertEquals(fixture, SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReadFixture>(encoded))
        assertEquals(reference, SportIdentOwnerReadVerification.nativeRead(fixture.copy(blocks = fixture.blocks.reversed())))
    }

    @Test fun reportsEveryMismatchWithoutNormalizingAwayDifferentNames() {
        val report = SportIdentOwnerReadVerification.compare(reference.copy(stationNumber = 593928, cardNumber = 2450663,
            firstName = " Daisy", lastName = "DUCK", controlPunchCount = 0), fixture())
        assertFalse(report.matches)
        assertEquals(SportIdentOwnerReadDifference.entries.toSet(), report.differences)
        val encoded = SportIdentOwnerNameProgramming.json.encodeToString(report)
        assertTrue(encoded.contains("\"matches\":false"))
        assertTrue(encoded.contains("count only"))
    }

    @Test fun emptyOwnerDataIsAValidReadRatherThanAMissingRead() {
        val fixture = fixture(first = "", last = "", count = 0)
        assertTrue(SportIdentOwnerReadVerification.compare(reference.copy(firstName = "", lastName = "", controlPunchCount = 0), fixture).matches)
    }

    @Test fun rejectsIncompleteDuplicateMalformedAndUnsupportedNativeReads() {
        val valid = fixture()
        listOf(valid.copy(schemaVersion = 2), valid.copy(stationNumber = 0), valid.copy(blocks = valid.blocks.take(1)),
            valid.copy(blocks = listOf(valid.blocks[0], valid.blocks[0])),
            valid.copy(blocks = listOf(valid.blocks[0].copy(hexData = "GG".repeat(128)), valid.blocks[1])),
            valid.copy(blocks = listOf(valid.blocks[0].copy(hexData = "00"), valid.blocks[1])),
            fixture(series = 1), fixture(count = 31), fixture(cardNumber = 0))
            .forEach { assertFailsWith<IllegalArgumentException> { SportIdentOwnerReadVerification.nativeRead(it) } }
    }

    @Test fun invalidReferenceReadsAndMissingOrUnknownJsonFieldsCannotPass() {
        listOf(reference.copy(schemaVersion = 2), reference.copy(stationNumber = 0), reference.copy(cardNumber = 0),
            reference.copy(cardType = "SI-Card9"), reference.copy(controlPunchCount = -1), reference.copy(controlPunchCount = 31))
            .forEach { assertFailsWith<IllegalArgumentException> { SportIdentOwnerReadVerification.compare(it, fixture()) } }
        val encoded = SportIdentOwnerNameProgramming.json.encodeToString(reference)
        listOf(encoded.replace(",\"ControlPunchCount\":1", ""), encoded.dropLast(1) + ",\"Unexpected\":true}")
            .forEach { assertFailsWith<IllegalArgumentException> {
                SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerReferenceRead>(it)
            } }
    }

    @Test fun captureFreezesMutableBlockDataButCountEqualityDoesNotProvePunchValues() {
        val blocks = blocks()
        val original = SportIdentOwnerReadVerification.capture(593927, blocks)
        blocks[1].data[8] = 99 // A different control code with the same punch count.
        val changed = SportIdentOwnerReadVerification.capture(593927, blocks)
        assertFalse(original == changed)
        assertTrue(SportIdentOwnerReadVerification.compare(reference, original).matches)
        assertTrue(SportIdentOwnerReadVerification.compare(reference, changed).matches)
        val diff = SportIdentOwnerReadVerification.diffNative(original, changed)
        assertEquals(listOf(SportIdentOwnerReadByteChange(1, 8, 0, 99)), diff.byteChanges)
        assertEquals(diff.byteChanges, diff.changesOutsideOwnerRegion)
        assertTrue(diff.samePunchCount)
    }

    @Test fun immutableBlockBytesRejectMissingOrMalformedEvidence() {
        val originalBlocks = blocks()
        val captured = SportIdentOwnerReadVerification.capture(593927, originalBlocks)
        assertContentEquals(originalBlocks[0].data, SportIdentOwnerReadVerification.blockBytes(captured, 0))
        originalBlocks[0].data[27] = 0
        assertEquals(2450662 and 0xff,
            SportIdentOwnerReadVerification.blockBytes(captured, 0)[27].toInt() and 0xff)
        assertFailsWith<IllegalArgumentException> {
            SportIdentOwnerReadVerification.blockBytes(captured.copy(blocks = captured.blocks.take(1)), 1)
        }
        assertFailsWith<IllegalArgumentException> {
            SportIdentOwnerReadVerification.blockBytes(captured.copy(blocks = listOf(
                captured.blocks[0].copy(hexData = "GG".repeat(128)), captured.blocks[1])), 0)
        }
    }

    @Test fun nativeDiffReportsOwnerChangesAndIdentityWithoutCallingThemVerified() {
        val before = fixture()
        val after = fixture(first = "Donald")
        val diff = SportIdentOwnerReadVerification.diffNative(before, after)
        assertEquals("Daisy", diff.before.firstName)
        assertEquals("Donald", diff.after.firstName)
        assertTrue(diff.sameStation && diff.sameCard && diff.samePunchCount)
        assertTrue(diff.byteChanges.isNotEmpty())
        assertTrue(diff.byteChanges.all { it.blockNumber == 0 && it.offset in 0x20..0x7f })
        assertTrue(diff.changesOutsideOwnerRegion.isEmpty())
        assertTrue(SportIdentOwnerNameProgramming.json.encodeToString(diff).contains("not write verification"))

        val wrongIdentity = SportIdentOwnerReadVerification.diffNative(before, fixture(cardNumber = 2450663))
        assertFalse(wrongIdentity.sameCard)
        assertTrue(wrongIdentity.changesOutsideOwnerRegion.isNotEmpty())
        assertFailsWith<IllegalArgumentException> {
            SportIdentOwnerReadVerification.diffNative(before, after.copy(blocks = after.blocks.take(1)))
        }
    }

    private fun fixture(first: String = "Daisy", last: String = "Duck", count: Int = 1, series: Int = 2,
        cardNumber: Int = 2450662): SportIdentOwnerReadFixture {
        // Preserve deliberately invalid fixtures for refusal tests without passing capture validation.
        return SportIdentOwnerReadFixture(1, 593927, blocks(first, last, count, series, cardNumber).map { block ->
            SportIdentOwnerReadBlock(block.blockNumber, block.data.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        })
    }

    private fun blocks(first: String = "Daisy", last: String = "Duck", count: Int = 1, series: Int = 2,
        cardNumber: Int = 2450662): List<SportIdentCardBlock> {
        val data = ByteArray(256)
        data[22] = count.toByte()
        data[24] = series.toByte()
        data[25] = (cardNumber shr 16).toByte()
        data[26] = (cardNumber shr 8).toByte()
        data[27] = cardNumber.toByte()
        "$first;$last;".forEachIndexed { index, c -> data[32 + index] = c.code.toByte() }
        return listOf(SportIdentCardBlock(0, data.copyOfRange(0, 128)), SportIdentCardBlock(1, data.copyOfRange(128, 256)))
    }
}
