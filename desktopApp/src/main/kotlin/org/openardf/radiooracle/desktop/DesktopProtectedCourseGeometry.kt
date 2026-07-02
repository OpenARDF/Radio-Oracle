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

import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint

internal fun ProtectedCourseInfo.withFiniteCourseGeometry(): ProtectedCourseInfo =
    copy(
        route = route
            .filter { it.hasValidCoordinate() }
            .map { point ->
                point.copy(elevationMeters = point.elevationMeters.finiteCourseValueOrNull())
            },
        controlPoints = controlPoints
            .filter { it.hasValidCoordinate() }
            .map { control ->
                control.copy(
                    elevationMeters = control.elevationMeters.finiteCourseValueOrNull(),
                    speedFactor = control.speedFactor.finiteCourseValueOrNull()
                )
            },
        courseObjects = courseObjects
            .filter { it.hasValidCoordinate() }
            .map { courseObject ->
                courseObject.copy(
                    elevationMeters = courseObject.elevationMeters.finiteCourseValueOrNull(),
                    speedFactor = courseObject.speedFactor.finiteCourseValueOrNull()
                )
            }
    )

internal fun ProtectedCourseInfo.finiteCourseGeoPoints(): List<CourseGeoPoint> =
    route.mapNotNull { point ->
        point.takeIf { it.hasValidCoordinate() }?.let {
            CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters.finiteCourseValueOrNull())
        }
    } +
        controlPoints.mapNotNull { control ->
            control.takeIf { it.hasValidCoordinate() }?.let {
                CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters.finiteCourseValueOrNull())
            }
        } +
        courseObjects.mapNotNull { courseObject ->
            courseObject.takeIf { it.hasValidCoordinate() }?.let {
                CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters.finiteCourseValueOrNull())
            }
        }

private fun ProtectedCourseRoutePoint.hasValidCoordinate(): Boolean =
    latitude.isValidLatitude() && longitude.isValidLongitude()

private fun ProtectedCourseControlPoint.hasValidCoordinate(): Boolean =
    latitude.isValidLatitude() && longitude.isValidLongitude()

private fun ProtectedCourseObjectPoint.hasValidCoordinate(): Boolean =
    latitude.isValidLatitude() && longitude.isValidLongitude()
