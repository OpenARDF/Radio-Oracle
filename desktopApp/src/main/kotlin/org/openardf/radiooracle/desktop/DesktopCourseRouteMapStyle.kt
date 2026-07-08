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

import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class DesktopCourseRouteMapScaleBar(
    val lengthMeters: Double,
    val drawingLength: Double,
    val label: String
)

internal object DesktopCourseRouteMapStyle {
    const val GraphicLineStrokePixels = 5f
    const val GraphicFuchsiaDashPaintPixels = 20f
    const val GraphicFuchsiaDashGapPixels = 10f
    const val GraphicBlackDashPaintPixels = 25f
    const val GraphicBlackDashGapPixels = 25f
    private const val GraphicPolygonArgb = 0x80C8AD7FL
    private const val CourseKmlFuchsiaArgb = 0xFFED72EFL

    fun composeColor(type: DesktopCourseRouteMapPointType): Color =
        Color(argb(type))

    fun awtColor(type: DesktopCourseRouteMapPointType): java.awt.Color =
        java.awt.Color(argb(type).toInt(), true)

    fun lineAwtColor(): java.awt.Color =
        java.awt.Color(CourseKmlFuchsiaArgb.toInt(), true)

    fun awtColor(argb: Long): java.awt.Color =
        java.awt.Color(argb.toInt(), true)

    fun pdfRgb(type: DesktopCourseRouteMapPointType): Triple<Double, Double, Double> {
        val argb = argb(type)
        return Triple(
            ((argb shr 16) and 0xFFL) / 255.0,
            ((argb shr 8) and 0xFFL) / 255.0,
            (argb and 0xFFL) / 255.0
        )
    }

    fun linePdfRgb(): Triple<Double, Double, Double> =
        Triple(237.0 / 255.0, 114.0 / 255.0, 239.0 / 255.0)

    fun pdfRgb(argb: Long): Triple<Double, Double, Double> =
        Triple(
            ((argb shr 16) and 0xFFL) / 255.0,
            ((argb shr 8) and 0xFFL) / 255.0,
            (argb and 0xFFL) / 255.0
        )

    fun lineComposeColor(): Color =
        Color(CourseKmlFuchsiaArgb)

    fun composeColor(argb: Long): Color =
        Color(argb)

    fun markerComposeColor(): Color =
        Color(CourseKmlFuchsiaArgb)

    fun markerAwtColor(): java.awt.Color =
        java.awt.Color(CourseKmlFuchsiaArgb.toInt(), true)

    fun markerPdfRgb(): Triple<Double, Double, Double> =
        linePdfRgb()

    fun polygonComposeColor(): Color =
        Color(GraphicPolygonArgb)

    fun polygonAwtColor(): java.awt.Color =
        java.awt.Color(GraphicPolygonArgb.toInt(), true)

    fun polygonPdfRgb(): Triple<Double, Double, Double> =
        Triple(0.78, 0.68, 0.50)

    fun scaleBar(xRangeMeters: Double?, drawingWidth: Double): DesktopCourseRouteMapScaleBar? {
        val rangeMeters = xRangeMeters?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val targetMeters = rangeMeters * 0.2
        val exponent = floor(log10(targetMeters.coerceAtLeast(1.0)))
        val base = 10.0.pow(exponent)
        val niceMultiplier = listOf(1.0, 2.0, 5.0, 10.0).lastOrNull { it * base <= targetMeters } ?: 1.0
        val lengthMeters = niceMultiplier * base
        return DesktopCourseRouteMapScaleBar(
            lengthMeters = lengthMeters,
            drawingLength = drawingWidth * lengthMeters / rangeMeters,
            label = scaleLabel(lengthMeters)
        )
    }

    private fun scaleLabel(lengthMeters: Double): String =
        if (lengthMeters >= 1000.0) {
            val kilometers = lengthMeters / 1000.0
            if (kilometers >= 10.0 || kilometers == kilometers.roundToInt().toDouble()) {
                "${kilometers.roundToInt()} km"
            } else {
                "${(kilometers * 10.0).roundToInt() / 10.0} km"
            }
        } else {
            "${lengthMeters.roundToInt()} m"
        }

    private fun argb(type: DesktopCourseRouteMapPointType): Long =
        when (type) {
            DesktopCourseRouteMapPointType.Start -> DesktopPalette.CONNECTED_ARGB
            DesktopCourseRouteMapPointType.Finish -> DesktopPalette.ERROR_ARGB
            DesktopCourseRouteMapPointType.Control -> DesktopPalette.PRIMARY_ARGB
            DesktopCourseRouteMapPointType.Beacon -> DesktopPalette.WARNING_ARGB
            DesktopCourseRouteMapPointType.Spectator -> DesktopPalette.DISCONNECTED_ARGB
            DesktopCourseRouteMapPointType.Waypoint -> DesktopPalette.SERIES_NAVIGATION_ARGB
        }
}
