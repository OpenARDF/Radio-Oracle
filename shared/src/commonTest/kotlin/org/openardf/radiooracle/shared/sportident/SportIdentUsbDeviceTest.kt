package org.openardf.radiooracle.shared.sportident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SportIdentUsbDeviceTest {
    @Test
    fun exposesKnownSportIdentUsbBridgeIdentity() {
        assertEquals(4292, SportIdentUsbDevice.VENDOR_ID)
        assertEquals(32778, SportIdentUsbDevice.PRODUCT_ID)
        assertEquals("10c4", SportIdentUsbDevice.VENDOR_ID_HEX)
        assertEquals("800a", SportIdentUsbDevice.PRODUCT_ID_HEX)
    }

    @Test
    fun matchesOnlyKnownSportIdentUsbBridge() {
        assertTrue(SportIdentUsbDevice.matches(4292, 32778))
        assertFalse(SportIdentUsbDevice.matches(4292, 24577))
        assertFalse(SportIdentUsbDevice.matches(1027, 32778))
    }
}
