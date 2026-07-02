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
 * DEALINGS IN THE SOFTWARE.
 */

package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCourseCoordinateValidationTest {
    @Test
    fun wgs84CoordinateHelpersRejectNonFiniteAndOutOfRangeValues() {
        assertEquals(90.0, 90.0.validLatitudeOrNull())
        assertEquals(-180.0, (-180.0).validLongitudeOrNull())
        assertNull(Double.NaN.validLatitudeOrNull())
        assertNull(Double.POSITIVE_INFINITY.validLongitudeOrNull())
        assertNull(90.000001.validLatitudeOrNull())
        assertNull((-180.000001).validLongitudeOrNull())

        assertTrue(CourseGeoPoint(39.0, -95.0).hasValidWgs84Coordinate())
        assertFalse(CourseGeoPoint(Double.NaN, -95.0).hasValidWgs84Coordinate())
        assertFalse(CourseGeoPoint(39.0, 181.0).hasValidWgs84Coordinate())
        assertTrue(DesktopKmlToolsPoint(39.0, -95.0).hasValidWgs84Coordinate())
        assertFalse(DesktopKmlToolsPoint(91.0, -95.0).hasValidWgs84Coordinate())
    }
}
