package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopMagneticDeclinationTest {
    @Test
    fun wmm2025DeclinationMatchesNoaaTestValuesAtSeaLevel() {
        assertEquals(
            1.28,
            requireNotNull(
                DesktopMagneticDeclination.degrees(
                    CourseGeoPoint(latitude = 80.0, longitude = 0.0),
                    decimalYear = 2025.0
                )
            ),
            0.01
        )
        assertEquals(
            -0.16,
            requireNotNull(
                DesktopMagneticDeclination.degrees(
                    CourseGeoPoint(latitude = 0.0, longitude = 120.0),
                    decimalYear = 2025.0
                )
            ),
            0.01
        )
        assertEquals(
            68.49,
            requireNotNull(
                DesktopMagneticDeclination.degrees(
                    CourseGeoPoint(latitude = -80.0, longitude = 240.0),
                    decimalYear = 2027.5
                )
            ),
            0.01
        )
    }

    @Test
    fun wmm2025DeclinationReturnsNullOutsideValidEpoch() {
        assertNull(
            DesktopMagneticDeclination.degrees(
                CourseGeoPoint(latitude = 39.0, longitude = -95.0),
                decimalYear = 2030.1
            )
        )
    }
}
