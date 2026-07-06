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

/** Shared low-level validation helpers for import and project-file data. */
object ImportValidationRules {
    /** Returns category names that occur more than once. */
    fun duplicateCategoryNames(names: List<String>): Set<String> = duplicateValues(names)

    /** Returns alias names that occur more than once. */
    fun duplicateAliasNames(names: List<String>): Set<String> = duplicateValues(names)

    /** Returns alias SI codes that occur more than once. */
    fun duplicateAliasCodes(codes: List<Int>): Set<Int> = duplicateValues(codes)

    /** Returns non-null SI numbers that occur more than once. */
    fun duplicateSINumbers(siNumbers: List<Int?>): Set<Int> = duplicateValues(siNumbers.filterNotNull())

    /** Returns non-blank bib numbers that occur more than once. */
    fun duplicateBibNumbers(bibNumbers: List<String>): Set<String> =
        duplicateValues(bibNumbers.map { it.trim() }.filter { it.isNotEmpty() })

    /** Returns normalized real call signs; SWL is a no-callsign marker, not a unique identity. */
    fun normalizedUniqueCallSign(callSign: String): String? =
        callSign.trim().uppercase().takeIf { it.isNotEmpty() && it != "SWL" }

    /** Returns non-blank real call signs that occur more than once, using case-insensitive comparison. */
    fun duplicateCallSigns(callSigns: List<String>): Set<String> =
        duplicateValues(callSigns.mapNotNull(::normalizedUniqueCallSign))

    /** Detects unsupported readout punch combinations, such as multiple starts or finishes. */
    fun validateReadoutPunchTypes(punchTypes: List<SIRecordType>): Set<ReadoutPunchValidationError> {
        val errors = LinkedHashSet<ReadoutPunchValidationError>()
        if (punchTypes.count { it == SIRecordType.START } > 1) {
            errors.add(ReadoutPunchValidationError.MULTIPLE_START)
        }
        if (punchTypes.count { it == SIRecordType.FINISH } > 1) {
            errors.add(ReadoutPunchValidationError.MULTIPLE_FINISH)
        }
        return errors
    }

    private fun <T> duplicateValues(values: List<T>): Set<T> {
        return values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
    }
}

/** Machine-readable readout punch validation failure. */
enum class ReadoutPunchValidationError {
    MULTIPLE_START,
    MULTIPLE_FINISH
}
