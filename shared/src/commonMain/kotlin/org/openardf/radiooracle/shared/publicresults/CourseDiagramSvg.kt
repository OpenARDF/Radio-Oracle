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

package org.openardf.radiooracle.shared.publicresults

import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import kotlin.math.cos
import kotlin.math.max

/** Browser-native 2D course renderer shared by every public-results publisher. */
object CourseDiagramSvg {
    private const val WIDTH = 1000.0
    private const val HEIGHT = 720.0
    private const val LEFT = 90.0
    private const val TOP = 110.0
    private const val RIGHT = 150.0
    private const val BOTTOM = 70.0

    fun render(title: String, courseInfo: ProtectedCourseInfo): String {
        val geoPoints = buildList {
            courseInfo.route.forEach { add(GeoPoint(it.latitude, it.longitude)) }
            courseInfo.controlPoints.forEach { add(GeoPoint(it.latitude, it.longitude)) }
            courseInfo.courseObjects.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        }
        require(geoPoints.isNotEmpty()) {
            "The protected course does not contain route or control coordinates."
        }

        val centerLatitude = geoPoints.map(GeoPoint::latitude).average()
        val longitudeScale = cos(Math.toRadians(centerLatitude)).coerceAtLeast(0.01)
        val projected = geoPoints.map {
            ProjectedPoint(it.longitude * longitudeScale, -it.latitude)
        }
        val minX = projected.minOf(ProjectedPoint::x)
        val maxX = projected.maxOf(ProjectedPoint::x)
        val minY = projected.minOf(ProjectedPoint::y)
        val maxY = projected.maxOf(ProjectedPoint::y)
        val sourceWidth = max(maxX - minX, 0.00001)
        val sourceHeight = max(maxY - minY, 0.00001)
        val scale = minOf(
            (WIDTH - LEFT - RIGHT) / sourceWidth,
            (HEIGHT - TOP - BOTTOM) / sourceHeight
        )
        val usedWidth = sourceWidth * scale
        val usedHeight = sourceHeight * scale
        val offsetX = LEFT + ((WIDTH - LEFT - RIGHT) - usedWidth) / 2.0
        val offsetY = TOP + ((HEIGHT - TOP - BOTTOM) - usedHeight) / 2.0

        fun point(latitude: Double, longitude: Double): ProjectedPoint =
            ProjectedPoint(
                x = offsetX + (longitude * longitudeScale - minX) * scale,
                y = offsetY + (-latitude - minY) * scale
            )

        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 720" role="img" aria-labelledby="title description">""")
            append("<title id=\"title\">${xml(title)}</title>")
            append("<desc id=\"description\">Two-dimensional course route and control diagram.</desc>")
            append("""<rect width="1000" height="720" fill="#f8fafc"/>""")
            append("""<rect x="24" y="24" width="952" height="672" rx="12" fill="#fff" stroke="#cbd5e1" stroke-width="2"/>""")
            append("""<text x="52" y="62" font-family="Arial,sans-serif" font-size="26" font-weight="700" fill="#111827">${xml(title)}</text>""")

            if (courseInfo.route.size >= 2) {
                val routePoints = courseInfo.route.joinToString(" ") {
                    val routePoint = point(it.latitude, it.longitude)
                    "${routePoint.x.svg()},${routePoint.y.svg()}"
                }
                append("""<polyline points="$routePoints" fill="none" stroke="#7c3aed" stroke-width="6" stroke-linecap="round" stroke-linejoin="round" opacity=".76"/>""")
            }

            courseInfo.controlPoints.forEachIndexed { index, control ->
                val controlPoint = point(control.latitude, control.longitude)
                appendControl(controlPoint)
                appendLabel(controlPoint, control.label.ifBlank { (index + 1).toString() })
            }

            courseInfo.courseObjects.forEach { courseObject ->
                val objectPoint = point(courseObject.latitude, courseObject.longitude)
                when (courseObject.type) {
                    ProtectedCourseObjectType.START -> appendStart(objectPoint)
                    ProtectedCourseObjectType.FINISH -> appendFinish(objectPoint)
                    ProtectedCourseObjectType.CONTROL,
                    ProtectedCourseObjectType.BEACON -> appendControl(objectPoint)
                    ProtectedCourseObjectType.SPECTATOR,
                    ProtectedCourseObjectType.WAYPOINT -> appendWaypoint(objectPoint)
                }
                appendLabel(objectPoint, courseObject.label)
            }

            append("""<g transform="translate(54 666)" font-family="Arial,sans-serif" font-size="15" fill="#475569">""")
            append("""<line x1="0" y1="-5" x2="44" y2="-5" stroke="#7c3aed" stroke-width="5"/><text x="54" y="0">Route</text>""")
            append("""<circle cx="144" cy="-5" r="10" fill="none" stroke="#d946ef" stroke-width="3"/><text x="162" y="0">Control</text>""")
            append("</g></svg>\n")
        }
    }

    private fun StringBuilder.appendStart(point: ProjectedPoint) {
        append(
            """<polygon points="${point.x.svg()},${(point.y - 24).svg()} ${(point.x - 22).svg()},${(point.y + 20).svg()} ${(point.x + 22).svg()},${(point.y + 20).svg()}" fill="#fff" stroke="#d946ef" stroke-width="6"/>"""
        )
    }

    private fun StringBuilder.appendFinish(point: ProjectedPoint) {
        append("""<circle cx="${point.x.svg()}" cy="${point.y.svg()}" r="24" fill="#fff" stroke="#d946ef" stroke-width="6"/>""")
        append("""<circle cx="${point.x.svg()}" cy="${point.y.svg()}" r="14" fill="none" stroke="#d946ef" stroke-width="5"/>""")
    }

    private fun StringBuilder.appendControl(point: ProjectedPoint) {
        append("""<circle class="course-control" cx="${point.x.svg()}" cy="${point.y.svg()}" r="22" fill="#fff" stroke="#d946ef" stroke-width="6"/>""")
    }

    private fun StringBuilder.appendWaypoint(point: ProjectedPoint) {
        append("""<rect x="${(point.x - 14).svg()}" y="${(point.y - 14).svg()}" width="28" height="28" fill="#fff" stroke="#0f766e" stroke-width="5"/>""")
    }

    private fun StringBuilder.appendLabel(point: ProjectedPoint, label: String) {
        if (label.isBlank()) return
        append(
            """<text class="course-label" x="${(point.x + 30).svg()}" y="${(point.y - 16).svg()}" font-family="Arial,sans-serif" font-size="26" font-weight="700" fill="#581c87" paint-order="stroke" stroke="#fff" stroke-width="6">${xml(label)}</text>"""
        )
    }

    private fun Double.svg(): String = "%.2f".format(java.util.Locale.US, this)

    private fun xml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private data class GeoPoint(val latitude: Double, val longitude: Double)
    private data class ProjectedPoint(val x: Double, val y: Double)
}
