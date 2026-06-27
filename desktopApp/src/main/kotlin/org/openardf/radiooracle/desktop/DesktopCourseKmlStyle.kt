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

internal object DesktopCourseKmlStyle {
    const val MarkerScale = "1.2"

    // KML colors are encoded as AABBGGRR; this is 100% opacity with RGB 237:114:239.
    const val MarkerColor = "ffef72ed"

    const val DonutIconUrl = "http://maps.google.com/mapfiles/kml/shapes/donut.png"
    const val StartIconUrl = "http://maps.google.com/mapfiles/kml/shapes/triangle.png"
    const val FinishIconUrl = "http://maps.google.com/mapfiles/kml/shapes/target.png"
    const val WaypointIconUrl = "http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png"

    const val DonutStyleId = "courseControlDoughnutStyle"
    const val StartStyleId = "courseStartStyle"
    const val FinishStyleId = "courseFinishStyle"
    const val WaypointStyleId = "courseWaypointCircleStyle"

    fun pointStyleDefinitions(indent: String = "    ", includeWaypoint: Boolean = true): String =
        buildString {
            appendPointStyle(indent, DonutStyleId, DonutIconUrl)
            appendPointStyle(indent, FinishStyleId, FinishIconUrl)
            appendPointStyle(indent, StartStyleId, StartIconUrl)
            if (includeWaypoint) {
                appendPointStyle(indent, WaypointStyleId, WaypointIconUrl)
            }
        }

    private fun StringBuilder.appendPointStyle(indent: String, styleId: String, iconUrl: String) {
        appendLine("$indent<Style id=\"$styleId\">")
        appendLine("$indent  <IconStyle><scale>$MarkerScale</scale><color>$MarkerColor</color><colorMode>normal</colorMode>")
        appendLine("$indent    <Icon><href>$iconUrl</href></Icon>")
        appendLine("$indent  </IconStyle>")
        appendLine("$indent  <LabelStyle><color>$MarkerColor</color><colorMode>normal</colorMode></LabelStyle>")
        appendLine("$indent</Style>")
    }
}
