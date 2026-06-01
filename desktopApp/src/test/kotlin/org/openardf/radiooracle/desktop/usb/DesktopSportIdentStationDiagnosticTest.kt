package org.openardf.radiooracle.desktop.usb

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopSportIdentStationDiagnosticTest {
    @Test
    fun reportsDifferingSystemInfoOffsets() {
        val baseline = byteArrayOf(0x00, 0x0e, 0x08, 0x05)
        val compare = byteArrayOf(0x00, 0x0f, 0x08, 0x28)

        assertEquals(
            listOf(1, 3),
            DesktopSportIdentStationSettingsComparison.differingOffsets(baseline, compare)
        )
    }
}
