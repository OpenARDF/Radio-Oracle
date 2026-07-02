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

internal fun Double?.finiteCourseValueOrNull(): Double? =
    this?.takeIf { it.isFinite() }

internal fun Double?.validLatitudeOrNull(): Double? =
    finiteCourseValueOrNull()?.takeIf { it in -90.0..90.0 }

internal fun Double?.validLongitudeOrNull(): Double? =
    finiteCourseValueOrNull()?.takeIf { it in -180.0..180.0 }

internal fun Double.isValidLatitude(): Boolean =
    validLatitudeOrNull() != null

internal fun Double.isValidLongitude(): Boolean =
    validLongitudeOrNull() != null

internal fun CourseGeoPoint.hasValidWgs84Coordinate(): Boolean =
    latitude.isValidLatitude() && longitude.isValidLongitude()

internal fun DesktopKmlToolsPoint.hasValidWgs84Coordinate(): Boolean =
    latitude.isValidLatitude() && longitude.isValidLongitude()
