package org.openardf.radiooracle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenBrightnessOverrideTest {
    @Test
    fun `normalizes current system brightness`() {
        assertEquals(94f / 255f, ScreenBrightnessOverride.fromSystemSetting(94)!!, 0.0001f)
    }

    @Test
    fun `keeps fallback above the screen-off override`() {
        assertEquals(1f / 255f, ScreenBrightnessOverride.fromSystemSetting(0)!!, 0.0001f)
    }

    @Test
    fun `returns null when no brightness is available`() {
        assertNull(ScreenBrightnessOverride.fromSystemSetting(null))
    }
}
