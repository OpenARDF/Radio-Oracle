package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SportIdentOwnerNameProgrammingTest {
    private val card = SportIdentCardOwnerInspection(2450662, SportIdentCardFamily.SI8,
        SportIdentCardHolder("Mickey", "Mouse", null), SportIdentOwnerDataStatus.READ)
    private val request = SportIdentOwnerNameProgramming.prepare(card, 593927, " Minnie ", "Mouse", true)
    private val result = SportIdentOwnerNameWriteResult(1, 593927, 2450662, "SI-Card8", "Minnie", "Mouse",
        11, 11, true, true, true)

    @Test fun requestUsesTheDocumentedHelperContractAndNormalizedNames() {
        val encoded = SportIdentOwnerNameProgramming.json.encodeToString(request)
        val decoded = SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteRequest>(encoded)
        assertEquals(request, decoded)
        assertEquals("Mickey", decoded.expectedFirstName)
        assertEquals("Minnie", decoded.firstName)
        assertEquals(1, decoded.schemaVersion)
    }

    @Test fun cannotPrepareWithoutReadyCardConsentValidNamesAndRealChange() {
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card, 593927, "Minnie", "Mouse", false) }
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card, 593927, "Mickey", "Mouse", true) }
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card, 0, "Minnie", "Mouse", true) }
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card, 593927, "A".repeat(24), "", true) }
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card.copy(status = SportIdentOwnerDataStatus.INCOMPLETE), 593927, "Minnie", "Mouse", true) }
        assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.prepare(card.copy(family = SportIdentCardFamily.SI9), 593927, "Minnie", "Mouse", true) }
    }

    @Test fun corruptedIdentityNamesCountsOrPreservationNeverPassVerification() {
        SportIdentOwnerNameProgramming.verify(request, result)
        listOf(result.copy(schemaVersion = 2), result.copy(stationNumber = 593928), result.copy(cardNumber = 2450663),
            result.copy(cardType = "SI-Card9"), result.copy(firstName = "Mickey"), result.copy(lastName = "Mous"),
            result.copy(controlPunchCountBefore = -1, controlPunchCountAfter = -1), result.copy(controlPunchCountAfter = 0),
            result.copy(punchesPreserved = false), result.copy(feedbackPreserved = false), result.copy(characterSetPreserved = false))
            .forEach { corrupted -> assertFailsWith<IllegalArgumentException> { SportIdentOwnerNameProgramming.verify(request, corrupted) } }
    }

    @Test fun incompleteOrUnexpectedWireResultsAreRejected() {
        val encoded = SportIdentOwnerNameProgramming.json.encodeToString(result)
        assertEquals(result, SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteResult>(encoded))
        assertFailsWith<IllegalArgumentException> {
            SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteResult>(encoded.replace(",\"PunchesPreserved\":true", ""))
        }
        assertFailsWith<IllegalArgumentException> {
            SportIdentOwnerNameProgramming.json.decodeFromString<SportIdentOwnerNameWriteResult>(encoded.dropLast(1) + ",\"Unknown\":true}")
        }
    }

    @Test fun independentReadBackIsRequiredAndProgressCannotRepeatOrSkip() {
        val sequence = SportIdentOwnerWriteSequence()
        assertFailsWith<IllegalArgumentException> { sequence.verify(request, result) }
        assertFailsWith<IllegalArgumentException> { sequence.advance(progress(SportIdentOwnerWritePhase.WRITING)) }
        sequence.advance(progress(SportIdentOwnerWritePhase.WAITING_FOR_CARD))
        assertFailsWith<IllegalArgumentException> { sequence.advance(progress(SportIdentOwnerWritePhase.WAITING_FOR_CARD)) }
        sequence.advance(progress(SportIdentOwnerWritePhase.WRITING))
        assertFailsWith<IllegalArgumentException> { sequence.verify(request, result) }
        sequence.advance(progress(SportIdentOwnerWritePhase.WAITING_FOR_READ_BACK))
        sequence.verify(request, result)
    }

    private fun progress(phase: SportIdentOwnerWritePhase) = SportIdentOwnerWriteProgress(1, "Phase", phase)
}
