/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
        assertEquals(setOf("K0ABC"), ImportValidationRules.duplicateCallSigns(listOf("K0ABC", "k0abc", "SWL", "swl")))
    }

    @Test
    fun ignoresUniqueValuesAndNullSINumbers() {
        assertTrue(ImportValidationRules.duplicateCategoryNames(listOf("M21", "W21")).isEmpty())
        assertTrue(ImportValidationRules.duplicateSINumbers(listOf(null, null, 123456)).isEmpty())
        assertTrue(ImportValidationRules.duplicateCallSigns(listOf("", " ", "SWL", "swl")).isEmpty())
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
