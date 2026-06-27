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

/** Shared SportIdent numeric limits and range-checking helpers. */
object SportIdentCodes {
    const val SI_MIN_NUMBER = 1000
    const val SI_MAX_NUMBER = 9999999

    const val SI_MIN_CODE = 1
    const val SI_LEGACY_MAX_CODE = 255
    const val SI_MAX_CODE = 511

    const val SECONDS_DAY = 86400L
    const val SECONDS_WEEK = 604800L

    /** Returns true when the supplied SI card number is within the supported card range. */
    fun isSINumberValid(siNumber: Int): Boolean {
        return siNumber in SI_MIN_NUMBER..SI_MAX_NUMBER
    }

    /** Returns true when the supplied control code is within the supported station-code range. */
    fun isSICodeValid(siCode: Int): Boolean {
        return siCode in SI_MIN_CODE..SI_MAX_CODE
    }

    /** Returns true when the code is in the legacy SI-Card5 / older software compatibility range. */
    fun isLegacyCompatibleSICode(siCode: Int): Boolean {
        return siCode in SI_MIN_CODE..SI_LEGACY_MAX_CODE
    }
}
