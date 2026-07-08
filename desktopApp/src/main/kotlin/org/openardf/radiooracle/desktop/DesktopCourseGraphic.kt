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

import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

data class DesktopCourseGraphicResult(
    val routeMap: DesktopCourseRouteMap,
    val visiblePointCount: Int,
    val visibleLineStringCount: Int,
    val visiblePolygonCount: Int,
    val hiddenObjectCount: Int,
    val outputPaths: DesktopCourseGraphicOutputPaths
)

data class DesktopCourseGraphicOutputPaths(
    val pngPath: Path,
    val jpgPath: Path,
    val pdfPath: Path
)

object DesktopCourseGraphic {
    private const val ImageWidth = 1400
    private const val ImageHeight = 1000
    private const val ImageMapLeft = 70
    private const val ImageMapTop = 130
    private const val ImageMapWidth = 1260
    private const val ImageMapHeight = 800
    private const val PointRadius = 8

    fun generate(
        path: Path,
        magneticDeclinationProvider: (CourseGeoPoint) -> DesktopMagneticDeclinationResult? = DesktopMagneticDeclination::result
    ): DesktopCourseGraphicResult {
        val courseData = DesktopCourseFileReader.read(path)
        val routeMap = routeMap(path, courseData, magneticDeclinationProvider)
        val outputPaths = outputPaths(path)
        outputPaths.pngPath.parent?.let(Files::createDirectories)
        writePng(outputPaths.pngPath, routeMap)
        writeJpg(outputPaths.jpgPath, routeMap)
        writePdf(outputPaths.pdfPath, routeMap)
        return DesktopCourseGraphicResult(
            routeMap = routeMap,
            visiblePointCount = courseData.controls.count { it.isVisible },
            visibleLineStringCount = courseData.routes.count { it.isVisible },
            visiblePolygonCount = courseData.polygons.count { it.isVisible },
            hiddenObjectCount = courseData.controls.count { !it.isVisible } +
                courseData.routes.count { !it.isVisible } +
                courseData.polygons.count { !it.isVisible },
            outputPaths = outputPaths
        )
    }

    internal fun routeMap(
        path: Path,
        courseData: DesktopCourseKmlData,
        magneticDeclinationProvider: (CourseGeoPoint) -> DesktopMagneticDeclinationResult? = { null }
    ): DesktopCourseRouteMap {
        val visiblePoints = courseData.controls.filter { it.isVisible }
        val visibleRoutes = courseData.routes.filter { it.isVisible }
        val visiblePolygons = courseData.polygons.filter { it.isVisible }
        val objectSources = visiblePoints.map { control ->
            DesktopCourseRouteMapSourcePoint(
                label = control.displayLabel,
                point = control.point,
                type = control.graphicPointType()
            )
        }
        val lineSources = visibleRoutes.flatMap { route ->
            route.points.mapIndexed { index, point ->
                DesktopCourseRouteMapSourcePoint(
                    label = "${route.displayLabel} point ${index + 1}",
                    point = point,
                    type = DesktopCourseRouteMapPointType.Waypoint
                )
            }
        }
        val polygonSources = visiblePolygons.flatMap { polygon ->
            polygon.points.mapIndexed { index, point ->
                DesktopCourseRouteMapSourcePoint(
                    label = "${polygon.displayLabel} point ${index + 1}",
                    point = point,
                    type = DesktopCourseRouteMapPointType.Waypoint
                )
            }
        }
        val allSources = objectSources + lineSources + polygonSources
        require(allSources.size >= 2) {
            "KML/KMZ file did not contain at least two visible points or LineString coordinates to draw."
        }
        val referencePoint = allSources.referencePoint()
        val magneticDeclination = runCatching { magneticDeclinationProvider(referencePoint) }.getOrNull()
        val projected = DesktopCourseRouteMapProjection.project(allSources, magneticDeclination?.degrees)
        val bounds = DesktopCourseRouteMapProjection.bounds(projected)
        val projectedObjects = projected.take(objectSources.size)
        val projectedLinePoints = projected.drop(objectSources.size)
        var lineOffset = 0
        val lineStrings = visibleRoutes.map { route ->
            val points = projectedLinePoints
                .drop(lineOffset)
                .take(route.points.size)
                .map { projectedPoint ->
                    DesktopCourseRouteMapLinePoint(
                        xFraction = bounds.xFraction(projectedPoint.xMeters),
                        yFraction = bounds.yFraction(projectedPoint.yMeters)
                    )
                }
            lineOffset += route.points.size
            DesktopCourseRouteMapLine(route.displayLabel, points)
        }
        val projectedPolygonPoints = projectedLinePoints.drop(lineOffset)
        var polygonOffset = 0
        val polygons = visiblePolygons.map { polygon ->
            val points = projectedPolygonPoints
                .drop(polygonOffset)
                .take(polygon.points.size)
                .map { projectedPoint ->
                    DesktopCourseRouteMapLinePoint(
                        xFraction = bounds.xFraction(projectedPoint.xMeters),
                        yFraction = bounds.yFraction(projectedPoint.yMeters)
                    )
                }
            polygonOffset += polygon.points.size
            DesktopCourseRouteMapPolygon(polygon.displayLabel, points)
        }
        return DesktopCourseRouteMap(
            title = defaultTitle(path),
            points = projectedObjects.map { projectedPoint ->
                DesktopCourseRouteMapPoint(
                    label = projectedPoint.source.label,
                    xFraction = bounds.xFraction(projectedPoint.xMeters),
                    yFraction = bounds.yFraction(projectedPoint.yMeters),
                    type = projectedPoint.source.type
                )
            },
            routeLabels = emptyList(),
            polygons = polygons,
            lineStrings = lineStrings,
            xRangeMeters = bounds.xRange,
            yRangeMeters = bounds.yRange,
            magneticDeclinationDegrees = magneticDeclination?.degrees,
            magneticDeclinationUsesExpiredModel = magneticDeclination?.usesExpiredCoefficients == true
        )
    }

    private fun writePng(path: Path, routeMap: DesktopCourseRouteMap) {
        ImageIO.write(renderImage(routeMap, "png"), "png", path.toFile())
    }

    private fun writeJpg(path: Path, routeMap: DesktopCourseRouteMap) {
        ImageIO.write(renderImage(routeMap, "jpg"), "jpg", path.toFile())
    }

    private fun renderImage(routeMap: DesktopCourseRouteMap, format: String): BufferedImage {
        val imageType = if (format == "jpg") BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val image = BufferedImage(ImageWidth, ImageHeight, imageType)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, ImageWidth, ImageHeight)
            graphics.color = Color.BLACK
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 28)
            graphics.drawString(routeMap.title, 70, 54)
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
            graphics.color = Color.DARK_GRAY
            graphics.drawString(routeMap.northOrientationText(), 70, 86)
            graphics.color = Color(0xCBC8C8)
            graphics.stroke = BasicStroke(2f)
            graphics.drawRect(ImageMapLeft, ImageMapTop, ImageMapWidth, ImageMapHeight)
            drawImagePolygons(graphics, routeMap)
            drawImageLineStrings(graphics, routeMap)
            drawImagePoints(graphics, routeMap)
            drawImageScaleBar(graphics, routeMap)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun drawImagePolygons(graphics: java.awt.Graphics2D, routeMap: DesktopCourseRouteMap) {
        graphics.color = DesktopCourseRouteMapStyle.polygonAwtColor()
        routeMap.polygons.forEach { polygon ->
            val xPoints = polygon.points.map(::imageX).toIntArray()
            val yPoints = polygon.points.map(::imageY).toIntArray()
            graphics.fillPolygon(xPoints, yPoints, polygon.points.size)
        }
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 16)
        graphics.color = Color.BLACK
        routeMap.polygons.forEach { polygon ->
            polygon.labelPoint()?.let { point ->
                graphics.drawString(polygon.label, imageX(point) + 8, imageY(point) - 8)
            }
        }
    }

    private fun drawImageLineStrings(graphics: java.awt.Graphics2D, routeMap: DesktopCourseRouteMap) {
        graphics.color = DesktopCourseRouteMapStyle.lineAwtColor()
        graphics.stroke = BasicStroke(
            DesktopCourseRouteMapStyle.GraphicLineStrokePixels,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND,
            10f,
            floatArrayOf(
                DesktopCourseRouteMapStyle.GraphicDashPaintPixels,
                DesktopCourseRouteMapStyle.GraphicDashGapPixels
            ),
            0f
        )
        routeMap.lineStrings.forEach { line ->
            line.points.zipWithNext().forEach { (from, to) ->
                graphics.drawLine(imageX(from), imageY(from), imageX(to), imageY(to))
            }
        }
        graphics.stroke = BasicStroke(1f)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 16)
        graphics.color = Color.BLACK
        routeMap.lineStrings.forEach { line ->
            line.midpoint()?.let { point ->
                graphics.drawString(line.label, imageX(point) + 8, imageY(point) - 8)
            }
        }
    }

    private fun drawImagePoints(graphics: java.awt.Graphics2D, routeMap: DesktopCourseRouteMap) {
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        routeMap.points.forEach { point ->
            val x = imageX(point)
            val y = imageY(point)
            graphics.color = DesktopCourseRouteMapStyle.awtColor(point.type)
            graphics.fillOval(x - PointRadius, y - PointRadius, PointRadius * 2, PointRadius * 2)
            graphics.color = Color.BLACK
            graphics.drawString(point.label, x + 12, y - 10)
        }
    }

    private fun drawImageScaleBar(graphics: java.awt.Graphics2D, routeMap: DesktopCourseRouteMap) {
        val scaleBar = DesktopCourseRouteMapStyle.scaleBar(routeMap.xRangeMeters, ImageMapWidth.toDouble()) ?: return
        val left = ImageMapLeft
        val y = ImageHeight - 50
        val right = left + scaleBar.drawingLength.toInt()
        graphics.color = Color.BLACK
        graphics.stroke = BasicStroke(4f)
        graphics.drawLine(left, y, right, y)
        graphics.stroke = BasicStroke(2f)
        graphics.drawLine(left, y - 10, left, y + 10)
        graphics.drawLine(right, y - 10, right, y + 10)
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 18)
        graphics.drawString(scaleBar.label, left, y + 32)
    }

    private fun writePdf(path: Path, routeMap: DesktopCourseRouteMap) {
        Files.write(
            path,
            DesktopPdfDocument.bytes(
                listOf(pdfPageContent(routeMap)),
                pageWidth = 792.0,
                pageHeight = 612.0,
                extraResourceEntries = "/ExtGState << /GS1 << /Type /ExtGState /ca 0.5 /CA 0.5 >> >>"
            )
        )
    }

    private fun pdfPageContent(routeMap: DesktopCourseRouteMap): String =
        buildString {
            appendText(54.0, 560.0, 18, routeMap.title)
            appendText(54.0, 538.0, 10, routeMap.northOrientationText())
            appendLine("0.80 0.78 0.78 RG")
            appendLine("54.00 54.00 684.00 450.00 re S")
            appendPdfPolygons(routeMap)
            appendPdfLineStrings(routeMap)
            appendPdfPoints(routeMap)
            appendPdfScaleBar(routeMap)
        }

    private fun StringBuilder.appendPdfPolygons(routeMap: DesktopCourseRouteMap) {
        val (red, green, blue) = DesktopCourseRouteMapStyle.polygonPdfRgb()
        appendLine("q")
        appendLine("/GS1 gs")
        appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} rg")
        routeMap.polygons.forEach { polygon ->
            val first = polygon.points.firstOrNull() ?: return@forEach
            appendLine("${pdfPoint(first)} m")
            polygon.points.drop(1).forEach { point ->
                appendLine("${pdfPoint(point)} l")
            }
            appendLine("h f")
        }
        appendLine("Q")
        routeMap.polygons.forEach { polygon ->
            polygon.labelPoint()?.let { point ->
                appendText(pdfX(point) + 5.0, pdfY(point) + 5.0, 8, polygon.label)
            }
        }
    }

    private fun StringBuilder.appendPdfLineStrings(routeMap: DesktopCourseRouteMap) {
        val (red, green, blue) = DesktopCourseRouteMapStyle.linePdfRgb()
        appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} RG")
        appendLine("${pdfNumber(DesktopCourseRouteMapStyle.GraphicLineStrokePixels.toDouble())} w")
        appendLine(
            "[${pdfNumber(DesktopCourseRouteMapStyle.GraphicDashPaintPixels.toDouble())} " +
                "${pdfNumber(DesktopCourseRouteMapStyle.GraphicDashGapPixels.toDouble())}] 0 d"
        )
        routeMap.lineStrings.forEach { line ->
            line.points.zipWithNext().forEach { (from, to) ->
                appendLine("${pdfPoint(from)} m ${pdfPoint(to)} l S")
            }
        }
        appendLine("[] 0 d")
        routeMap.lineStrings.forEach { line ->
            line.midpoint()?.let { point ->
                appendText(pdfX(point) + 5.0, pdfY(point) + 5.0, 8, line.label)
            }
        }
    }

    private fun StringBuilder.appendPdfPoints(routeMap: DesktopCourseRouteMap) {
        routeMap.points.forEach { point ->
            val (red, green, blue) = DesktopCourseRouteMapStyle.pdfRgb(point.type)
            appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} rg")
            appendCircle(pdfX(point), pdfY(point), 4.0)
            appendText(pdfX(point) + 5.0, pdfY(point) + 5.0, 8, point.label)
        }
    }

    private fun StringBuilder.appendPdfScaleBar(routeMap: DesktopCourseRouteMap) {
        val scaleBar = DesktopCourseRouteMapStyle.scaleBar(routeMap.xRangeMeters, 684.0) ?: return
        val left = 54.0
        val y = 30.0
        val right = left + scaleBar.drawingLength
        appendLine("0 0 0 RG")
        appendLine("2 w")
        appendLine("${pdfNumber(left)} ${pdfNumber(y)} m ${pdfNumber(right)} ${pdfNumber(y)} l S")
        appendLine("${pdfNumber(left)} ${pdfNumber(y - 5.0)} m ${pdfNumber(left)} ${pdfNumber(y + 5.0)} l S")
        appendLine("${pdfNumber(right)} ${pdfNumber(y - 5.0)} m ${pdfNumber(right)} ${pdfNumber(y + 5.0)} l S")
        appendText(left, y - 17.0, 8, scaleBar.label)
    }

    private fun StringBuilder.appendCircle(centerX: Double, centerY: Double, radius: Double) {
        val kappa = 0.5522847498
        val control = radius * kappa
        appendLine("${pdfNumber(centerX + radius)} ${pdfNumber(centerY)} m")
        appendLine(
            "${pdfNumber(centerX + radius)} ${pdfNumber(centerY + control)} " +
                "${pdfNumber(centerX + control)} ${pdfNumber(centerY + radius)} " +
                "${pdfNumber(centerX)} ${pdfNumber(centerY + radius)} c"
        )
        appendLine(
            "${pdfNumber(centerX - control)} ${pdfNumber(centerY + radius)} " +
                "${pdfNumber(centerX - radius)} ${pdfNumber(centerY + control)} " +
                "${pdfNumber(centerX - radius)} ${pdfNumber(centerY)} c"
        )
        appendLine(
            "${pdfNumber(centerX - radius)} ${pdfNumber(centerY - control)} " +
                "${pdfNumber(centerX - control)} ${pdfNumber(centerY - radius)} " +
                "${pdfNumber(centerX)} ${pdfNumber(centerY - radius)} c"
        )
        appendLine(
            "${pdfNumber(centerX + control)} ${pdfNumber(centerY - radius)} " +
                "${pdfNumber(centerX + radius)} ${pdfNumber(centerY - control)} " +
                "${pdfNumber(centerX + radius)} ${pdfNumber(centerY)} c"
        )
        appendLine("f")
    }

    private fun StringBuilder.appendText(x: Double, y: Double, fontSize: Int, text: String) {
        appendLine("BT")
        appendLine("/F1 $fontSize Tf")
        appendLine("0 0 0 rg")
        appendLine("1 0 0 1 ${pdfNumber(x)} ${pdfNumber(y)} Tm")
        appendLine("(${DesktopExportPrimitives.pdfText(text)}) Tj")
        appendLine("ET")
    }

    private fun outputPaths(sourcePath: Path): DesktopCourseGraphicOutputPaths {
        val base = sourcePath.resolveSibling("${sourcePath.fileStem()} 2D Graphic")
        return DesktopCourseGraphicOutputPaths(
            pngPath = Path.of("$base.png"),
            jpgPath = Path.of("$base.jpg"),
            pdfPath = Path.of("$base.pdf")
        )
    }

    private fun defaultTitle(path: Path): String =
        "${path.fileStem()} 2D Graphic"

    private fun Path.fileStem(): String =
        fileName.toString().replace(Regex("""\.[^.]+$"""), "")

    private fun List<DesktopCourseRouteMapSourcePoint>.referencePoint(): CourseGeoPoint =
        CourseGeoPoint(
            latitude = map { it.point.latitude }.average(),
            longitude = map { it.point.longitude }.average()
        )

    private fun CourseControlPoint.graphicPointType(): DesktopCourseRouteMapPointType =
        when {
            description.hasCourseObjectType("START") || DesktopCoursePointLabelClassifier.isEndpointStartName(name) ->
                DesktopCourseRouteMapPointType.Start
            description.hasCourseObjectType("FINISH") || DesktopCoursePointLabelClassifier.isEndpointFinishName(name) ->
                DesktopCourseRouteMapPointType.Finish
            description.hasCourseObjectType("BEACON") || DesktopCoursePointLabelClassifier.isBeaconLabel(name) ->
                DesktopCourseRouteMapPointType.Beacon
            description.hasCourseObjectType("SPECTATOR") || DesktopCoursePointLabelClassifier.isSpectatorLabel(name) ->
                DesktopCourseRouteMapPointType.Spectator
            description.hasCourseObjectType("CONTROL") || ControlRoleLabelRules.foxNumber(name) != null ->
                DesktopCourseRouteMapPointType.Control
            else -> DesktopCourseRouteMapPointType.Waypoint
        }

    private fun String?.hasCourseObjectType(type: String): Boolean =
        this?.contains(Regex("""(?i)\btype\s*(?:=\s*)?${Regex.escape(type)}\b""")) == true

    private fun DesktopCourseRouteMapLine.midpoint(): DesktopCourseRouteMapLinePoint? =
        points.getOrNull(points.size / 2)

    private fun DesktopCourseRouteMapPolygon.labelPoint(): DesktopCourseRouteMapLinePoint? {
        if (points.isEmpty()) {
            return null
        }
        return DesktopCourseRouteMapLinePoint(
            xFraction = points.map { it.xFraction }.average(),
            yFraction = points.map { it.yFraction }.average()
        )
    }

    private fun imageX(point: DesktopCourseRouteMapPoint): Int =
        (ImageMapLeft + point.xFraction.coerceIn(0.0, 1.0) * ImageMapWidth).toInt()

    private fun imageY(point: DesktopCourseRouteMapPoint): Int =
        (ImageMapTop + point.yFraction.coerceIn(0.0, 1.0) * ImageMapHeight).toInt()

    private fun imageX(point: DesktopCourseRouteMapLinePoint): Int =
        (ImageMapLeft + point.xFraction.coerceIn(0.0, 1.0) * ImageMapWidth).toInt()

    private fun imageY(point: DesktopCourseRouteMapLinePoint): Int =
        (ImageMapTop + point.yFraction.coerceIn(0.0, 1.0) * ImageMapHeight).toInt()

    private fun pdfPoint(point: DesktopCourseRouteMapLinePoint): String =
        "${pdfNumber(pdfX(point))} ${pdfNumber(pdfY(point))}"

    private fun pdfX(point: DesktopCourseRouteMapPoint): Double =
        54.0 + point.xFraction.coerceIn(0.0, 1.0) * 684.0

    private fun pdfY(point: DesktopCourseRouteMapPoint): Double =
        54.0 + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * 450.0

    private fun pdfX(point: DesktopCourseRouteMapLinePoint): Double =
        54.0 + point.xFraction.coerceIn(0.0, 1.0) * 684.0

    private fun pdfY(point: DesktopCourseRouteMapLinePoint): Double =
        54.0 + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * 450.0

    private fun pdfNumber(value: Double): String =
        DesktopPdfDocument.number(value)
}
