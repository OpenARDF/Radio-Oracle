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
