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

package org.openardf.radiooracle.shared.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DurationFormatterTest {
    @Test
    fun formatsDurationAsMinuteString() {
        assertEquals("00:00", DurationFormatter.secondsToFormattedString(0, true))
        assertEquals("64:00", DurationFormatter.secondsToFormattedString(64 * 60, true))
        assertEquals("59:22", DurationFormatter.secondsToFormattedString(59 * 60 + 22, true))
        assertEquals("120:00", DurationFormatter.secondsToFormattedString(2 * 60 * 60, true))
        assertEquals("120:25", DurationFormatter.secondsToFormattedString(2 * 60 * 60 + 25, true))
        assertEquals("1000:00", DurationFormatter.secondsToFormattedString(1000 * 60, true))
        assertEquals("-10:00", DurationFormatter.secondsToFormattedString(-10 * 60, true))
        assertEquals("-100:00", DurationFormatter.secondsToFormattedString(-100 * 60, true))
        assertEquals("-10000:00", DurationFormatter.secondsToFormattedString(-10000 * 60, true))
    }

    @Test
    fun formatsDurationAsHourString() {
        assertEquals("00:00:00", DurationFormatter.secondsToFormattedString(0, false))
        assertEquals("00:15:19", DurationFormatter.secondsToFormattedString(15 * 60 + 19, false))
        assertEquals("01:04:00", DurationFormatter.secondsToFormattedString(64 * 60, false))
        assertEquals("00:59:22", DurationFormatter.secondsToFormattedString(59 * 60 + 22, false))
        assertEquals("02:14:22", DurationFormatter.secondsToFormattedString(2 * 60 * 60 + 14 * 60 + 22, false))
    }

    @Test
    fun parsesMinuteStringToSeconds() {
        assertEquals(12 * 60 + 34, DurationFormatter.minuteStringToSeconds("12:34"))
    }

    @Test
    fun rejectsInvalidMinuteStrings() {
        assertFailsWith<IllegalArgumentException> {
            DurationFormatter.minuteStringToSeconds("12")
        }
        assertFailsWith<IllegalArgumentException> {
            DurationFormatter.minuteStringToSeconds("12:xx")
        }
        assertFailsWith<IllegalArgumentException> {
            DurationFormatter.minuteStringToSeconds("12:99")
        }
    }
}
