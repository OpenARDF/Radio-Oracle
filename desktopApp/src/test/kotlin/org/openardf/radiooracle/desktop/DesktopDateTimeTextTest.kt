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

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class DesktopDateTimeTextTest {
    @Test
    fun parsesDateAndMinuteTimeText() {
        assertEquals(
            LocalDateTime.of(2026, 6, 1, 9, 30),
            DesktopDateTimeText.parseOrNull("2026-06-01", "9:30")
        )
    }

    @Test
    fun parsesDateAndSecondTimeText() {
        assertEquals(
            LocalDateTime.of(2026, 6, 1, 9, 30, 15),
            DesktopDateTimeText.parseOrNull("2026-06-01", "09:30:15")
        )
    }

    @Test
    fun formatsDateTimeForPickerFieldsAndStorage() {
        val value = LocalDateTime.of(2026, 6, 1, 9, 30)

        assertEquals("2026-06-01", DesktopDateTimeText.dateText(value))
        assertEquals("09:30:00", DesktopDateTimeText.timeText(value))
        assertEquals("2026-06-01T09:30", DesktopDateTimeText.isoText(value))
        assertEquals("Mon, Jun 1, 2026 9:30 AM", DesktopDateTimeText.displayText(value))
        assertEquals("Mon, Jun 1, 2026 9:30 AM", DesktopDateTimeText.displayIsoOrRaw("2026-06-01T09:30"))
    }

    @Test
    fun returnsNullForInvalidPickerText() {
        assertNull(DesktopDateTimeText.parseOrNull("2026-06-01", "25:00"))
        assertNull(DesktopDateTimeText.parseOrNull("2026-02-31", "09:00"))
    }
}
