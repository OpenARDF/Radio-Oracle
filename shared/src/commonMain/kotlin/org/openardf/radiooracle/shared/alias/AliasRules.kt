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

package org.openardf.radiooracle.shared.alias

import org.openardf.radiooracle.shared.sportident.SportIdentCodes

/** Shared validation rules for SportIdent control aliases. */
object AliasRules {
    const val MAX_NAME_LENGTH = 6
    const val ALLOWED_NAME_CHARACTERS = "abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ/"
    private val numericName = Regex("""\d+""")

    /** Validates an alias display name against length, character-set, and duplicate rules. */
    fun validateName(name: String, existingNames: List<String>, position: Int): AliasValidationResult {
        if (name.isEmpty()) {
            return AliasValidationResult.Required
        }
        if (name.length > MAX_NAME_LENGTH || name.any { it !in ALLOWED_NAME_CHARACTERS }) {
            return AliasValidationResult.Invalid
        }
        val numericValue = name.takeIf { it.matches(numericName) }?.toIntOrNull()
        if (numericValue != null && numericValue in SportIdentCodes.SI_MIN_CODE..SportIdentCodes.SI_MAX_CODE) {
            return AliasValidationResult.Invalid
        }
        if (existingNames.withIndex().any { (index, value) -> index != position && value == name }) {
            return AliasValidationResult.Duplicate
        }
        return AliasValidationResult.Valid
    }

    /** Validates an alias SI code against SportIdent range and duplicate rules. */
    fun validateCode(code: String, existingCodes: List<Int>, position: Int): AliasValidationResult {
        if (code.isEmpty()) {
            return AliasValidationResult.Required
        }

        val codeValue = code.toIntOrNull() ?: return AliasValidationResult.Invalid

        if (!SportIdentCodes.isSICodeValid(codeValue)) {
            return AliasValidationResult.Invalid
        }
        if (existingCodes.withIndex().any { (index, value) -> index != position && value == codeValue }) {
            return AliasValidationResult.Duplicate
        }
        return AliasValidationResult.Valid
    }
}

/** Machine-readable alias validation result. */
enum class AliasValidationResult {
    Valid,
    Required,
    Invalid,
    Duplicate
}
