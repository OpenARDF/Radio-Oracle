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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.StandardCategoryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StandardCategoryRulesTest {
    @Test
    fun parsesStandardCategoryDefinitions() {
        assertEquals(
            StandardCategoryDefinition(name = "M21", isMan = true, maxAge = 39),
            StandardCategoryRules.parseDefinition(" M21 ; 1 ; 39 ")
        )
        assertEquals(
            StandardCategoryDefinition(name = "W21", isMan = false, maxAge = 34),
            StandardCategoryRules.parseDefinition("W21;0;34")
        )
    }

    @Test
    fun rejectsInvalidStandardCategoryDefinitions() {
        assertNull(StandardCategoryRules.parseDefinition("M21;1"))
        assertNull(StandardCategoryRules.parseDefinition(";1;39"))
        assertNull(StandardCategoryRules.parseDefinition("M21;true;39"))
        assertNull(StandardCategoryRules.parseDefinition("M21;1;0"))
        assertNull(StandardCategoryRules.parseDefinition("M21;1;age"))
    }

    @Test
    fun providesBuiltInStandardCategoryDefinitions() {
        val international = StandardCategoryRules.definitionsFor(StandardCategoryType.INTERNATIONAL)
        val czech = StandardCategoryRules.definitionsFor(StandardCategoryType.CZECH)

        assertEquals(12, international.size)
        assertEquals(StandardCategoryDefinition(name = "W19", isMan = false, maxAge = 19), international.first())
        assertEquals(StandardCategoryDefinition(name = "M70", isMan = true, maxAge = 200), international.last())
        assertEquals(22, czech.size)
        assertEquals(StandardCategoryDefinition(name = "D7", isMan = false, maxAge = 7), czech.first())
        assertEquals(StandardCategoryDefinition(name = "M70", isMan = true, maxAge = 200), czech.last())
    }

    @Test
    fun infersGenderFromStandardCategoryNames() {
        assertEquals(true, StandardCategoryRules.inferIsManFromName("M21"))
        assertEquals(true, StandardCategoryRules.inferIsManFromName("M-21"))
        assertEquals(true, StandardCategoryRules.inferIsManFromName(" m60 "))
        assertEquals(false, StandardCategoryRules.inferIsManFromName("W50"))
        assertEquals(false, StandardCategoryRules.inferIsManFromName("W-65"))
        assertEquals(false, StandardCategoryRules.inferIsManFromName("D20"))
        assertNull(StandardCategoryRules.inferIsManFromName("Beginner"))
    }
}
