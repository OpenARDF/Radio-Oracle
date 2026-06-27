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

import java.util.Locale

object DesktopExportPrimitives {
    fun kmlCoordinate(point: CourseGeoPoint): String =
        if (point.elevationMeters == null) {
            String.format(Locale.US, "%.8f,%.8f", point.longitude, point.latitude)
        } else {
            String.format(Locale.US, "%.8f,%.8f,%.2f", point.longitude, point.latitude, point.elevationMeters)
        }

    fun compactKmlCoordinate(
        longitude: Double,
        latitude: Double,
        elevationMeters: Double?
    ): String =
        listOfNotNull(
            compactDecimal(longitude),
            compactDecimal(latitude),
            elevationMeters?.let(::compactDecimal)
        ).joinToString(",")

    fun compactDecimal(value: Double, maxDecimalPlaces: Int = 8): String =
        String.format(Locale.US, "%.${maxDecimalPlaces}f", value).trimEnd('0').trimEnd('.')

    fun xmlText(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    fun pdfText(text: String): String =
        text.map { character ->
            when (character) {
                '\\' -> "\\\\"
                '(' -> "\\("
                ')' -> "\\)"
                in ' '..'~' -> character.toString()
                else -> "?"
            }
        }.joinToString("")
}
