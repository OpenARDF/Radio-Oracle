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

import kotlin.test.Test
import kotlin.test.assertEquals

class AliasRulesTest {
    @Test
    fun validatesAliasNames() {
        assertEquals(AliasValidationResult.Required, AliasRules.validateName("", emptyList(), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateName("F1", listOf("F1"), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateName("A/B", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateName("TOOLONG", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateName("F-1", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateName("31", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateName("511", emptyList(), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateName("512", emptyList(), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateName("90B", emptyList(), 0))
        assertEquals(AliasValidationResult.Duplicate, AliasRules.validateName("F1", listOf("F1", "F1"), 1))
    }

    @Test
    fun validatesAliasControlCodes() {
        assertEquals(AliasValidationResult.Required, AliasRules.validateCode("", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateCode("abc", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateCode("0", emptyList(), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateCode("256", emptyList(), 0))
        assertEquals(AliasValidationResult.Invalid, AliasRules.validateCode("512", emptyList(), 0))
        assertEquals(AliasValidationResult.Valid, AliasRules.validateCode("31", listOf(31), 0))
        assertEquals(AliasValidationResult.Duplicate, AliasRules.validateCode("31", listOf(31, 31), 1))
    }
}
