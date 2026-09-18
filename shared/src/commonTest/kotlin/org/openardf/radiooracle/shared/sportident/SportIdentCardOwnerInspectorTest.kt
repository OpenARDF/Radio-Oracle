package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class SportIdentCardOwnerInspectorTest {
    @Test
    fun preservesSpacesAndReadsSi6ClubAcrossBlockBoundary() {
        val data = ByteArray(256) { 0x20 }
        put(data, 48, "Van der Runner")
        put(data, 68, "Mary Jane")
        put(data, 96, "A club name that crosses two blocks")
        val result = inspect(6, 1_000_001, data)
        assertEquals(SportIdentOwnerDataStatus.READ, result.status)
        assertEquals("Mary Jane", result.holder?.firstName)
        assertEquals("Van der Runner", result.holder?.lastName)
        assertEquals("A club name that crosses two blocks", result.holder?.club)
    }

    @Test
    fun readsModernNameAndClubBeyondTheFirstBlockWithoutUsingPunchBytes() {
        val data = ByteArray(512)
        val first = "A".repeat(70)
        val last = "B".repeat(30)
        put(data, 32, "$first;$last;F;2000;Test Club;")
        data[447] = 1
        val result = inspect(15, 8_000_001, data)
        assertEquals(SportIdentCardFamily.SIAC, result.family)
        assertEquals(SportIdentOwnerDataStatus.READ, result.status)
        assertEquals(first, result.holder?.firstName)
        assertEquals(last, result.holder?.lastName)
        assertEquals("Test Club", result.holder?.club)
    }

    @Test
    fun readsPcardClubAfterSexAndBirthYear() {
        val data = ByteArray(256)
        put(data, 32, "Alice;Runner;F;2000;OK Test;")
        assertEquals("OK Test", inspect(4, 4_000_001, data).holder?.club)
    }

    @Test
    fun doesNotPresentUnsupportedModernEncodingAsAnEmptyName() {
        val data = ByteArray(512)
        put(data, 32, "André;Runner;;;")
        data[447] = 2
        val unsupported = inspect(15, 8_000_001, data)
        assertEquals(SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING, unsupported.status)
        assertNull(unsupported.holder)
        data[447] = 1
        assertEquals("André", inspect(15, 8_000_001, data).holder?.firstName)
    }

    @Test
    fun distinguishesMissingOwnerBlocksEmptyOwnerDataAndUnsupportedCards() {
        val incomplete = inspect(15, 8_000_001, ByteArray(128))
        assertEquals(SportIdentOwnerDataStatus.INCOMPLETE, incomplete.status)
        val empty = inspect(15, 8_000_001, ByteArray(512))
        assertEquals(SportIdentOwnerDataStatus.READ, empty.status)
        assertNull(empty.holder)
        assertEquals(SportIdentOwnerDataStatus.NOT_SUPPORTED, inspect(5, 234_567, ByteArray(0)).status)
    }

    @Test
    fun sanitizesTerminatedNamesAndDoesNotInventAClubForSi9() {
        val data = ByteArray(128)
        put(data, 32, "Ali\u0001ce;Runner;Bogus;")
        val result = inspect(1, 1_000_001, data)
        assertEquals("Alice", result.holder?.firstName)
        assertNull(result.holder?.club)
        assertFalse(result.family.supportsClub)
        data.fill(0xEE.toByte(), 32)
        assertNull(inspect(1, 1_000_001, data).holder)
    }

    @Test
    fun identifiesModernCardNumberBoundariesAndLeavesUnknownNumbersUnclassified() {
        val data = ByteArray(512)
        listOf(7_000_000 to SportIdentCardFamily.SI10, 7_999_999 to SportIdentCardFamily.SI10,
            8_000_000 to SportIdentCardFamily.SIAC, 8_999_999 to SportIdentCardFamily.SIAC,
            9_000_000 to SportIdentCardFamily.SI11, 9_999_999 to SportIdentCardFamily.SI11,
            10_000_000 to SportIdentCardFamily.MODERN_UNKNOWN).forEach { (number, family) ->
            assertEquals(family, inspect(15, number, data).family)
        }
    }

    private fun inspect(series: Int, number: Int, data: ByteArray): SportIdentCardOwnerInspection {
        val readout = SportIdentCardReadout(number, series, null, null, null, emptyList())
        val blocks = data.toList().chunked(128).mapIndexed { index, bytes -> SportIdentCardBlock(index, bytes.toByteArray()) }
        return SportIdentCardOwnerInspector.inspect(readout, blocks)
    }

    private fun put(data: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { index, c -> data[offset + index] = c.code.toByte() }
    }
}
