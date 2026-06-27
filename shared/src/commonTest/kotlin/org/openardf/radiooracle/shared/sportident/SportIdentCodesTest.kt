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

package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentCodesTest {
    @Test
    fun validatesSportIdentNumberRange() {
        assertFalse(SportIdentCodes.isSINumberValid(999))
        assertTrue(SportIdentCodes.isSINumberValid(1000))
        assertTrue(SportIdentCodes.isSINumberValid(9999999))
        assertFalse(SportIdentCodes.isSINumberValid(10000000))
    }

    @Test
    fun validatesControlCodeRange() {
        assertFalse(SportIdentCodes.isSICodeValid(0))
        assertTrue(SportIdentCodes.isSICodeValid(1))
        assertTrue(SportIdentCodes.isSICodeValid(255))
        assertTrue(SportIdentCodes.isSICodeValid(256))
        assertTrue(SportIdentCodes.isSICodeValid(511))
        assertFalse(SportIdentCodes.isSICodeValid(512))
        assertTrue(SportIdentCodes.isLegacyCompatibleSICode(255))
        assertFalse(SportIdentCodes.isLegacyCompatibleSICode(256))
    }
}
