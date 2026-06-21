package org.openardf.radiooracle.shared.importing

import org.openardf.radiooracle.shared.domain.SIRecordType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportValidationRulesTest {
    @Test
    fun findsDuplicateNamesAndNumbers() {
        assertEquals(setOf("M21", "W21"), ImportValidationRules.duplicateCategoryNames(listOf("M21", "W21", "M21", "W21")))
        assertEquals(setOf("F1"), ImportValidationRules.duplicateAliasNames(listOf("F1", "F2", "F1")))
        assertEquals(setOf(31), ImportValidationRules.duplicateAliasCodes(listOf(31, 32, 31)))
        assertEquals(setOf(123456), ImportValidationRules.duplicateSINumbers(listOf(123456, null, 123456)))
    }

    @Test
    fun ignoresUniqueValuesAndNullSINumbers() {
        assertTrue(ImportValidationRules.duplicateCategoryNames(listOf("M21", "W21")).isEmpty())
        assertTrue(ImportValidationRules.duplicateSINumbers(listOf(null, null, 123456)).isEmpty())
    }

    @Test
    fun validatesSingleStartAndFinishPunches() {
        assertEquals(
            emptySet(),
            ImportValidationRules.validateReadoutPunchTypes(
                listOf(SIRecordType.CHECK, SIRecordType.START, SIRecordType.CONTROL, SIRecordType.FINISH)
            )
        )
        assertEquals(
            setOf(ReadoutPunchValidationError.MULTIPLE_START, ReadoutPunchValidationError.MULTIPLE_FINISH),
            ImportValidationRules.validateReadoutPunchTypes(
                listOf(SIRecordType.START, SIRecordType.START, SIRecordType.FINISH, SIRecordType.FINISH)
            )
        )
    }
}
