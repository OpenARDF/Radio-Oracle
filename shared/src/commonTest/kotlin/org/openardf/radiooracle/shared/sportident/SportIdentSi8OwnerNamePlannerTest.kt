package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SportIdentSi8OwnerNamePlannerTest {
    private val card = SportIdentCardOwnerInspection(
        2_000_001, SportIdentCardFamily.SI8, null, SportIdentOwnerDataStatus.READ
    )

    @Test
    fun encodedPreviewRoundTripsThroughExistingSi8Reader() {
        val preview = SportIdentSi8OwnerNamePlanner.preview(card, " Mary Jane ", " Van der Meer ")
        assertTrue(preview.problems.isEmpty())
        val bytes = assertNotNull(preview.encodedOwnerText)
        val data = ByteArray(128)
        bytes.copyInto(data, 32)
        val holder = SportIdentCardReadoutParser.parseSemicolonCardHolder(data, 2)
        assertEquals(preview.firstName, holder?.firstName)
        assertEquals(preview.lastName, holder?.lastName)
        assertEquals("Mary Jane", preview.firstName)
        assertEquals("Van der Meer", preview.lastName)
        assertEquals(bytes.size - 2, preview.nameCharacterCount)
        assertEquals(card.siNumber, preview.siNumber)
    }

    @Test
    fun matchesVendorValidationAtTheCombinedNameLimit() {
        listOf(12 to 11, 23 to 0, 0 to 23).forEach { (firstLength, lastLength) ->
            val exact = SportIdentSi8OwnerNamePlanner.preview(card, "A".repeat(firstLength), "B".repeat(lastLength))
            assertEquals(23, exact.nameCharacterCount)
            assertNotNull(exact.encodedOwnerText)
        }
        listOf(12 to 12, 24 to 0, 0 to 24).forEach { (firstLength, lastLength) ->
            val overflow = SportIdentSi8OwnerNamePlanner.preview(card, "A".repeat(firstLength), "B".repeat(lastLength))
            assertEquals(24, overflow.nameCharacterCount)
            assertEquals(setOf(SportIdentOwnerNameProblem.TOO_LONG), overflow.problems)
            assertNull(overflow.encodedOwnerText)
        }
        val overflow = SportIdentSi8OwnerNamePlanner.preview(card, "A".repeat(12), "B".repeat(12))
        assertEquals("A".repeat(12), overflow.firstName)
    }

    @Test
    fun rejectsDelimitersControlsAndNonAsciiInsteadOfSilentlyChangingNames() {
        listOf("Anne;Marie", "Anne\n", "Anne\t", "André", "Runner😀", "Anne\u0000", "Anne\u007f").forEach { name ->
            val preview = SportIdentSi8OwnerNamePlanner.preview(card, name, "Runner")
            assertEquals(name, preview.firstName)
            assertEquals(setOf(SportIdentOwnerNameProblem.UNSUPPORTED_CHARACTERS), preview.problems)
            assertNull(preview.nameCharacterCount)
            assertNull(preview.encodedOwnerText)
        }
    }

    @Test
    fun allowsPunctuationAndExplicitEmptyNameDrafts() {
        assertNotNull(SportIdentSi8OwnerNamePlanner.preview(card, "Jo-Anne", "O'Brien").encodedOwnerText)
        val cleared = SportIdentSi8OwnerNamePlanner.preview(card, "  ", "")
        val bytes = assertNotNull(cleared.encodedOwnerText)
        assertEquals(";;", bytes.decodeToString())
        val data = ByteArray(128)
        bytes.copyInto(data, 32)
        assertNull(SportIdentCardReadoutParser.parseSemicolonCardHolder(data, 2))
    }

    @Test
    fun requiresAnSi8WithCompletelyReadOwnerData() {
        listOf(
            card.copy(family = SportIdentCardFamily.SI9),
            card.copy(status = SportIdentOwnerDataStatus.INCOMPLETE),
            card.copy(status = SportIdentOwnerDataStatus.NOT_SUPPORTED),
            card.copy(status = SportIdentOwnerDataStatus.UNSUPPORTED_ENCODING)
        ).forEach { inspection ->
            val preview = SportIdentSi8OwnerNamePlanner.preview(inspection, "Alice", "Runner")
            assertEquals(setOf(SportIdentOwnerNameProblem.CARD_NOT_READY), preview.problems)
            assertNull(preview.encodedOwnerText)
        }
    }
}
