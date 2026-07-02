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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

class DesktopProtectedCourseGeometryTest {
    @Test
    fun finiteGeometryDropsInvalidCoordinatesAndClearsNonFiniteMeasurements() {
        val courseInfo = ProtectedCourseInfo(
            route = listOf(
                ProtectedCourseRoutePoint(39.0, -95.0, Double.POSITIVE_INFINITY),
                ProtectedCourseRoutePoint(Double.NaN, -95.1, 120.0),
                ProtectedCourseRoutePoint(39.2, Double.NEGATIVE_INFINITY, 130.0)
            ),
            controlPoints = listOf(
                ProtectedCourseControlPoint(
                    controlId = "control-31",
                    label = "31",
                    latitude = 39.1,
                    longitude = -95.1,
                    type = ControlPointType.CONTROL,
                    elevationMeters = Double.NaN,
                    speedFactor = Double.POSITIVE_INFINITY
                ),
                ProtectedCourseControlPoint(
                    controlId = "bad-control",
                    label = "Bad",
                    latitude = 91.0,
                    longitude = -95.2,
                    type = ControlPointType.CONTROL,
                    elevationMeters = 100.0,
                    speedFactor = 1.0
                )
            ),
            courseObjects = listOf(
                ProtectedCourseObjectPoint(
                    id = "start",
                    label = "Start",
                    type = ProtectedCourseObjectType.START,
                    latitude = 39.0,
                    longitude = -95.0,
                    elevationMeters = 100.0,
                    speedFactor = Double.NaN
                ),
                ProtectedCourseObjectPoint(
                    id = "bad-object",
                    label = "Bad",
                    type = ProtectedCourseObjectType.WAYPOINT,
                    latitude = 39.2,
                    longitude = -181.0,
                    elevationMeters = 110.0,
                    speedFactor = 1.0
                )
            )
        )

        val sanitized = courseInfo.withFiniteCourseGeometry()

        assertEquals(1, sanitized.route.size)
        assertNull(sanitized.route.single().elevationMeters)
        assertEquals(listOf("control-31"), sanitized.controlPoints.map { it.controlId })
        assertNull(sanitized.controlPoints.single().elevationMeters)
        assertNull(sanitized.controlPoints.single().speedFactor)
        assertEquals(listOf("start"), sanitized.courseObjects.map { it.id })
        assertNull(sanitized.courseObjects.single().speedFactor)
        assertTrue(sanitized.finiteCourseGeoPoints().all { point ->
            point.latitude.isFinite() &&
                point.latitude in -90.0..90.0 &&
                point.longitude.isFinite() &&
                point.longitude in -180.0..180.0
        })
    }
}
