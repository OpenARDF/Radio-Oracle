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

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.geom.Path2D
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

private data class GraphicLabelRequest(
    val label: String,
    val anchorX: Double,
    val anchorY: Double,
    val width: Double,
    val height: Double,
    val priority: Int,
    val kind: GraphicLabelKind,
    val normalX: Double = 0.0,
    val normalY: Double = -1.0,
    val strokeWidth: Double = 0.0,
    val pointGap: Double = 18.0
)

private data class ImageRenderStyle(
    val markerScale: Double,
    val labelFontSize: Int,
    val labelFontStyle: Int,
    val pointLabelGap: Double
) {
    val markerRadius: Int = (14.0 * markerScale).toInt()
}

private enum class GraphicLabelKind {
    Point,
    Line,
    Free
}

private data class GraphicLineLabelAnchor(
    val x: Double,
    val y: Double,
    val normalX: Double,
    val normalY: Double
)

private data class GraphicLineSegment(
    val fromX: Double,
    val fromY: Double,
    val dx: Double,
    val dy: Double,
    val length: Double
)

private data class GraphicLabelPlacement(
    val label: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)

object DesktopCourseGraphic {
    private const val ImageWidth = 1400
    private const val ImageHeight = 1000
    private const val ImageMapLeft = 70
    private const val ImageMapTop = 130
    private const val ImageMapWidth = 1260
    private const val ImageMapHeight = 800
    private const val GraphicFractionPadding = 0.06
    private const val WebGraphicFractionPadding = 0.12
    private const val PdfMapLeft = 54.0
    private const val PdfMapBottom = 54.0
    private const val PdfMapWidth = 684.0
    private const val PdfMapHeight = 450.0
    private val StandardImageStyle = ImageRenderStyle(
        markerScale = 1.0,
        labelFontSize = 18,
        labelFontStyle = Font.PLAIN,
        pointLabelGap = 18.0
    )
    private val WebImageStyle = ImageRenderStyle(
        markerScale = 1.75,
        labelFontSize = 30,
        labelFontStyle = Font.BOLD,
        pointLabelGap = 32.0
    )

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
                        xFraction = paddedFraction(bounds.xFraction(projectedPoint.xMeters)),
                        yFraction = paddedFraction(bounds.yFraction(projectedPoint.yMeters))
                    )
                }
            lineOffset += route.points.size
            val renderStyle = route.lineStyle?.blackRenderStyle()
            DesktopCourseRouteMapLine(
                label = route.displayLabel,
                points = points,
                strokeColorArgb = renderStyle?.argb,
                strokeWidthPixels = renderStyle?.widthPixels?.toFloat(),
                dashed = renderStyle?.isSolidLine() != true
            )
        }
        val projectedPolygonPoints = projectedLinePoints.drop(lineOffset)
        var polygonOffset = 0
        val polygons = visiblePolygons.map { polygon ->
            val points = projectedPolygonPoints
                .drop(polygonOffset)
                .take(polygon.points.size)
                .map { projectedPoint ->
                    DesktopCourseRouteMapLinePoint(
                        xFraction = paddedFraction(bounds.xFraction(projectedPoint.xMeters)),
                        yFraction = paddedFraction(bounds.yFraction(projectedPoint.yMeters))
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
                    xFraction = paddedFraction(bounds.xFraction(projectedPoint.xMeters)),
                    yFraction = paddedFraction(bounds.yFraction(projectedPoint.yMeters)),
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

    internal fun writePng(path: Path, routeMap: DesktopCourseRouteMap) {
        path.parent?.let(Files::createDirectories)
        ImageIO.write(renderImage(routeMap, "png", StandardImageStyle), "png", path.toFile())
    }

    internal fun writeWebPng(path: Path, routeMap: DesktopCourseRouteMap) {
        path.parent?.let(Files::createDirectories)
        ImageIO.write(
            renderImage(routeMap.withFractionPadding(WebGraphicFractionPadding), "png", WebImageStyle),
            "png",
            path.toFile()
        )
    }

    internal fun webRouteMap(routeMap: DesktopCourseRouteMap): DesktopCourseRouteMap =
        routeMap.withFractionPadding(WebGraphicFractionPadding)

    private fun writeJpg(path: Path, routeMap: DesktopCourseRouteMap) {
        ImageIO.write(renderImage(routeMap, "jpg", StandardImageStyle), "jpg", path.toFile())
    }

    private fun renderImage(
        routeMap: DesktopCourseRouteMap,
        format: String,
        imageStyle: ImageRenderStyle
    ): BufferedImage {
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
            drawImagePoints(graphics, routeMap, imageStyle)
            drawImageLabels(graphics, routeMap, imageStyle)
            drawImageMagneticNorthArrow(graphics)
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
    }

    private fun drawImageLineStrings(graphics: java.awt.Graphics2D, routeMap: DesktopCourseRouteMap) {
        routeMap.lineStrings.forEach { line ->
            graphics.color = line.strokeColorArgb
                ?.let(DesktopCourseRouteMapStyle::awtColor)
                ?: DesktopCourseRouteMapStyle.lineAwtColor()
            graphics.stroke = BasicStroke(
                line.strokeWidthPixels ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND,
                10f,
                line.dashPattern(),
                0f
            )
            graphics.draw(line.imageSmoothPath())
        }
    }

    private fun drawImagePoints(
        graphics: java.awt.Graphics2D,
        routeMap: DesktopCourseRouteMap,
        imageStyle: ImageRenderStyle
    ) {
        routeMap.points.forEach { point ->
            drawImageMarkerIcon(graphics, point.type, imageX(point), imageY(point), imageStyle.markerScale)
        }
    }

    private fun drawImageMarkerIcon(
        graphics: java.awt.Graphics2D,
        type: DesktopCourseRouteMapPointType,
        centerX: Int,
        centerY: Int,
        markerScale: Double
    ) {
        fun scaled(value: Int): Int = (value * markerScale).toInt()

        graphics.color = DesktopCourseRouteMapStyle.markerAwtColor()
        graphics.stroke = BasicStroke((4.0 * markerScale).toFloat())
        when (type) {
            DesktopCourseRouteMapPointType.Start -> {
                graphics.drawPolygon(
                    Polygon(
                        intArrayOf(centerX, centerX - scaled(12), centerX + scaled(12)),
                        intArrayOf(centerY - scaled(13), centerY + scaled(11), centerY + scaled(11)),
                        3
                    )
                )
            }
            DesktopCourseRouteMapPointType.Finish -> {
                graphics.drawOval(centerX - scaled(14), centerY - scaled(14), scaled(28), scaled(28))
                graphics.drawOval(centerX - scaled(7), centerY - scaled(7), scaled(14), scaled(14))
            }
            DesktopCourseRouteMapPointType.Waypoint -> {
                graphics.fillOval(centerX - scaled(10), centerY - scaled(10), scaled(20), scaled(20))
            }
            DesktopCourseRouteMapPointType.Control,
            DesktopCourseRouteMapPointType.Beacon,
            DesktopCourseRouteMapPointType.Spectator -> {
                graphics.drawOval(centerX - scaled(12), centerY - scaled(12), scaled(24), scaled(24))
            }
        }
    }

    private fun drawImageLabels(
        graphics: java.awt.Graphics2D,
        routeMap: DesktopCourseRouteMap,
        imageStyle: ImageRenderStyle
    ) {
        graphics.font = Font(Font.SANS_SERIF, imageStyle.labelFontStyle, imageStyle.labelFontSize)
        graphics.color = Color.BLACK
        imageLabelPlacements(graphics, routeMap, imageStyle).forEach { placement ->
            graphics.drawString(placement.label, placement.x.toInt(), (placement.y + placement.height - 4.0).toInt())
        }
    }

    private fun drawImageMagneticNorthArrow(graphics: java.awt.Graphics2D) {
        val centerX = ImageMapLeft + ImageMapWidth - 48
        val tipY = ImageMapTop + 26
        val tailY = tipY + 68
        graphics.color = Color.BLACK
        graphics.stroke = BasicStroke(3f)
        graphics.drawLine(centerX, tailY, centerX, tipY + 10)
        graphics.fillPolygon(
            Polygon(
                intArrayOf(centerX, centerX - 10, centerX + 10),
                intArrayOf(tipY, tipY + 20, tipY + 20),
                3
            )
        )
        graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        graphics.drawString("MN", centerX - 14, tailY + 24)
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

    private fun imageLabelPlacements(
        graphics: java.awt.Graphics2D,
        routeMap: DesktopCourseRouteMap,
        imageStyle: ImageRenderStyle
    ): List<GraphicLabelPlacement> {
        val metrics = graphics.fontMetrics
        val labelHeight = metrics.height.toDouble()
        val requests = buildList {
            routeMap.points.filter { it.label.isNotEmpty() }.forEach { point ->
                add(
                    GraphicLabelRequest(
                        label = point.label,
                        anchorX = imageX(point).toDouble(),
                        anchorY = imageY(point).toDouble(),
                        width = metrics.stringWidth(point.label).toDouble(),
                        height = labelHeight,
                        priority = 0,
                        kind = GraphicLabelKind.Point,
                        pointGap = imageStyle.pointLabelGap
                    )
                )
            }
            routeMap.lineStrings.filter { it.label.isNotEmpty() }.forEach { line ->
                line.labelAnchor(
                    x = { point -> imageX(point).toDouble() },
                    y = { point -> imageY(point).toDouble() }
                )?.let { anchor ->
                    add(
                        GraphicLabelRequest(
                            label = line.label,
                            anchorX = anchor.x,
                            anchorY = anchor.y,
                            width = metrics.stringWidth(line.label).toDouble(),
                            height = labelHeight,
                            priority = 1,
                            kind = GraphicLabelKind.Line,
                            normalX = anchor.normalX,
                            normalY = anchor.normalY,
                            strokeWidth = line.strokeWidthPixels?.toDouble()
                                ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels.toDouble()
                        )
                    )
                }
            }
            routeMap.polygons.filter { it.label.isNotEmpty() }.forEach { polygon ->
                polygon.labelPoint()?.let { point ->
                    add(
                        GraphicLabelRequest(
                            label = polygon.label,
                            anchorX = imageX(point).toDouble(),
                            anchorY = imageY(point).toDouble(),
                            width = metrics.stringWidth(polygon.label).toDouble(),
                            height = labelHeight,
                            priority = 2,
                            kind = GraphicLabelKind.Free
                        )
                    )
                }
            }
        }
        return placeLabels(
            requests = requests,
            bounds = Rectangle(ImageMapLeft + 6, ImageMapTop + 6, ImageMapWidth - 12, ImageMapHeight - 12),
            occupied = listOf(
                Rectangle(ImageMapLeft + ImageMapWidth - 106, ImageMapTop + 10, 96, 126),
                Rectangle(ImageMapLeft, ImageHeight - 72, 220, 66)
            ) + routeMap.imagePointMarkerBounds(imageStyle.markerRadius)
        )
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
            appendPdfLabels(routeMap)
            appendPdfMagneticNorthArrow()
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
    }

    private fun StringBuilder.appendPdfLineStrings(routeMap: DesktopCourseRouteMap) {
        routeMap.lineStrings.forEach { line ->
            appendPdfDashPattern(line)
            val (red, green, blue) = line.strokeColorArgb
                ?.let(DesktopCourseRouteMapStyle::pdfRgb)
                ?: DesktopCourseRouteMapStyle.linePdfRgb()
            appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} RG")
            appendLine("${pdfNumber((line.strokeWidthPixels ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels).toDouble())} w")
            appendPdfSmoothLine(line)
        }
        appendLine("[] 0 d")
    }

    private fun StringBuilder.appendPdfPoints(routeMap: DesktopCourseRouteMap) {
        routeMap.points.forEach { point ->
            appendPdfMarkerIcon(point.type, pdfX(point), pdfY(point))
        }
    }

    private fun StringBuilder.appendPdfMarkerIcon(type: DesktopCourseRouteMapPointType, centerX: Double, centerY: Double) {
        val (red, green, blue) = DesktopCourseRouteMapStyle.markerPdfRgb()
        appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} rg")
        appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} RG")
        appendLine("2 w")
        when (type) {
            DesktopCourseRouteMapPointType.Start -> {
                appendLine("${pdfNumber(centerX)} ${pdfNumber(centerY + 9.0)} m")
                appendLine("${pdfNumber(centerX - 9.0)} ${pdfNumber(centerY - 9.0)} l")
                appendLine("${pdfNumber(centerX + 9.0)} ${pdfNumber(centerY - 9.0)} l")
                appendLine("h S")
            }
            DesktopCourseRouteMapPointType.Finish -> {
                appendCircle(centerX, centerY, 10.0, fill = false)
                appendCircle(centerX, centerY, 4.5, fill = false)
            }
            DesktopCourseRouteMapPointType.Waypoint -> {
                appendCircle(centerX, centerY, 6.5, fill = true)
            }
            DesktopCourseRouteMapPointType.Control,
            DesktopCourseRouteMapPointType.Beacon,
            DesktopCourseRouteMapPointType.Spectator -> {
                appendCircle(centerX, centerY, 8.0, fill = false)
            }
        }
    }

    private fun StringBuilder.appendPdfLabels(routeMap: DesktopCourseRouteMap) {
        pdfLabelPlacements(routeMap).forEach { placement ->
            appendText(placement.x, pdfLabelBaselineY(placement), 8, placement.label)
        }
    }

    private fun StringBuilder.appendPdfMagneticNorthArrow() {
        val centerX = 708.0
        val tipY = 484.0
        val tailY = 430.0
        appendLine("0 0 0 RG")
        appendLine("2 w")
        appendLine("${pdfNumber(centerX)} ${pdfNumber(tailY)} m ${pdfNumber(centerX)} ${pdfNumber(tipY - 8.0)} l S")
        appendLine("${pdfNumber(centerX)} ${pdfNumber(tipY)} m")
        appendLine("${pdfNumber(centerX - 8.0)} ${pdfNumber(tipY - 16.0)} l")
        appendLine("${pdfNumber(centerX + 8.0)} ${pdfNumber(tipY - 16.0)} l")
        appendLine("h f")
        appendText(centerX - 12.0, tailY - 18.0, 10, "MN")
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

    private fun pdfLabelPlacements(routeMap: DesktopCourseRouteMap): List<GraphicLabelPlacement> {
        val fontSize = 8.0
        val labelHeight = 10.0
        val requests = buildList {
            routeMap.points.filter { it.label.isNotEmpty() }.forEach { point ->
                add(
                    GraphicLabelRequest(
                        label = point.label,
                        anchorX = pdfScreenX(point),
                        anchorY = pdfScreenY(point),
                        width = pdfLabelWidth(point.label, fontSize),
                        height = labelHeight,
                        priority = 0,
                        kind = GraphicLabelKind.Point
                    )
                )
            }
            routeMap.lineStrings.filter { it.label.isNotEmpty() }.forEach { line ->
                line.labelAnchor(::pdfScreenX, ::pdfScreenY)?.let { anchor ->
                    add(
                        GraphicLabelRequest(
                            label = line.label,
                            anchorX = anchor.x,
                            anchorY = anchor.y,
                            width = pdfLabelWidth(line.label, fontSize),
                            height = labelHeight,
                            priority = 1,
                            kind = GraphicLabelKind.Line,
                            normalX = anchor.normalX,
                            normalY = anchor.normalY,
                            strokeWidth = line.strokeWidthPixels?.toDouble()
                                ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels.toDouble()
                        )
                    )
                }
            }
            routeMap.polygons.filter { it.label.isNotEmpty() }.forEach { polygon ->
                polygon.labelPoint()?.let { point ->
                    add(
                        GraphicLabelRequest(
                            label = polygon.label,
                            anchorX = pdfScreenX(point),
                            anchorY = pdfScreenY(point),
                            width = pdfLabelWidth(polygon.label, fontSize),
                            height = labelHeight,
                            priority = 2,
                            kind = GraphicLabelKind.Free
                        )
                    )
                }
            }
        }
        return placeLabels(
            requests = requests,
            bounds = Rectangle(PdfMapLeft.toInt() + 4, PdfMapBottom.toInt() + 4, PdfMapWidth.toInt() - 8, PdfMapHeight.toInt() - 8),
            occupied = listOf(
                Rectangle((PdfMapLeft + PdfMapWidth - 62.0).toInt(), PdfMapBottom.toInt() + 10, 58, 76),
                Rectangle(PdfMapLeft.toInt(), PdfMapBottom.toInt() + PdfMapHeight.toInt() - 24, 130, 24)
            ) + routeMap.pdfPointMarkerBounds()
        )
    }

    private fun placeLabels(
        requests: List<GraphicLabelRequest>,
        bounds: Rectangle,
        occupied: List<Rectangle> = emptyList()
    ): List<GraphicLabelPlacement> {
        val placedRects = occupied.toMutableList()
        return requests.sortedWith(compareBy<GraphicLabelRequest> { it.priority }.thenBy { it.label }).map { request ->
            val placement = labelCandidates(request, bounds)
                .firstOrNull { candidate -> placedRects.none { it.intersects(candidate.toRectangle()) } }
                ?: request.takeIf { it.kind == GraphicLabelKind.Free }?.let {
                    gridLabelCandidate(it, bounds, placedRects)
                }
                ?: labelCandidates(request, bounds).minBy { candidate ->
                    placedRects.sumOf { it.overlapArea(candidate.toRectangle()) }
                }
            placedRects += placement.toRectangle()
            placement
        }
    }

    private fun labelCandidates(request: GraphicLabelRequest, bounds: Rectangle): List<GraphicLabelPlacement> {
        val offsets = when (request.kind) {
            GraphicLabelKind.Point -> pointLabelOffsets(request)
            GraphicLabelKind.Line -> lineLabelOffsets(request)
            GraphicLabelKind.Free -> freeLabelOffsets(request)
        }
        return offsets.map { (dx, dy) ->
            request.placementAt(request.anchorX + dx, request.anchorY + dy, bounds)
        }
    }

    private fun pointLabelOffsets(request: GraphicLabelRequest): List<Pair<Double, Double>> {
        val gap = request.pointGap
        return listOf(
            -request.width / 2.0 to -request.height - gap,
            -request.width / 2.0 to gap
        )
    }

    private fun lineLabelOffsets(request: GraphicLabelRequest): List<Pair<Double, Double>> {
        val gap = 8.0
        val halfNormalExtent =
            kotlin.math.abs(request.normalX) * request.width / 2.0 +
                kotlin.math.abs(request.normalY) * request.height / 2.0
        val distance = halfNormalExtent + request.strokeWidth / 2.0 + gap
        return listOf(
            lineLabelOffset(request, distance),
            lineLabelOffset(request, -distance)
        )
    }

    private fun lineLabelOffset(request: GraphicLabelRequest, distance: Double): Pair<Double, Double> =
        request.normalX * distance - request.width / 2.0 to
            request.normalY * distance - request.height / 2.0

    private fun freeLabelOffsets(request: GraphicLabelRequest): List<Pair<Double, Double>> {
        val gap = 16.0
        return listOf(
            gap to -request.height - gap,
            gap to gap,
            -request.width - gap to -request.height - gap,
            -request.width - gap to gap,
            -request.width / 2.0 to -request.height - gap,
            -request.width / 2.0 to gap,
            gap to -request.height / 2.0,
            -request.width - gap to -request.height / 2.0
        )
    }

    private fun gridLabelCandidate(
        request: GraphicLabelRequest,
        bounds: Rectangle,
        placedRects: List<Rectangle>
    ): GraphicLabelPlacement? {
        val maxX = bounds.maxX - request.width
        val maxY = bounds.maxY - request.height
        var y = bounds.y.toDouble()
        while (y <= maxY) {
            var x = bounds.x.toDouble()
            while (x <= maxX) {
                val candidate = GraphicLabelPlacement(request.label, x, y, request.width, request.height)
                if (placedRects.none { it.intersects(candidate.toRectangle()) }) {
                    return candidate
                }
                x += 8.0
            }
            y += 8.0
        }
        return null
    }

    private fun GraphicLabelRequest.placementAt(x: Double, y: Double, bounds: Rectangle): GraphicLabelPlacement =
        GraphicLabelPlacement(
            label = label,
            x = x.coerceIn(bounds.x.toDouble(), max(bounds.x.toDouble(), bounds.maxX - width)),
            y = y.coerceIn(bounds.y.toDouble(), max(bounds.y.toDouble(), bounds.maxY - height)),
            width = min(width, bounds.width.toDouble()),
            height = height
        )

    private fun GraphicLabelPlacement.toRectangle(): Rectangle =
        Rectangle(x.toInt(), y.toInt(), width.toInt() + 6, height.toInt() + 4)

    private fun Rectangle.overlapArea(other: Rectangle): Int {
        val overlapWidth = min(maxX, other.maxX) - max(x.toDouble(), other.x.toDouble())
        val overlapHeight = min(maxY, other.maxY) - max(y.toDouble(), other.y.toDouble())
        return if (overlapWidth > 0.0 && overlapHeight > 0.0) {
            (overlapWidth * overlapHeight).toInt()
        } else {
            0
        }
    }

    private fun StringBuilder.appendCircle(centerX: Double, centerY: Double, radius: Double, fill: Boolean) {
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
        appendLine(if (fill) "f" else "S")
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

    private fun paddedFraction(fraction: Double): Double =
        GraphicFractionPadding + fraction.coerceIn(0.0, 1.0) * (1.0 - GraphicFractionPadding * 2.0)

    private fun DesktopCourseRouteMap.withFractionPadding(padding: Double): DesktopCourseRouteMap {
        fun padded(fraction: Double): Double =
            padding + fraction.coerceIn(0.0, 1.0) * (1.0 - padding * 2.0)
        return copy(
            points = points.map { point ->
                point.copy(xFraction = padded(point.xFraction), yFraction = padded(point.yFraction))
            },
            polygons = polygons.map { polygon ->
                polygon.copy(
                    points = polygon.points.map { point ->
                        point.copy(xFraction = padded(point.xFraction), yFraction = padded(point.yFraction))
                    }
                )
            },
            lineStrings = lineStrings.map { line ->
                line.copy(
                    points = line.points.map { point ->
                        point.copy(xFraction = padded(point.xFraction), yFraction = padded(point.yFraction))
                    }
                )
            }
        )
    }

    private fun Path.fileStem(): String =
        fileName.toString().replace(Regex("""\.[^.]+$"""), "")

    private fun List<DesktopCourseRouteMapSourcePoint>.referencePoint(): CourseGeoPoint =
        CourseGeoPoint(
            latitude = map { it.point.latitude }.average(),
            longitude = map { it.point.longitude }.average()
        )

    private fun CourseControlPoint.graphicPointType(): DesktopCourseRouteMapPointType =
        when (symbol) {
            CoursePointSymbol.Triangle -> DesktopCourseRouteMapPointType.Start
            CoursePointSymbol.Donut -> DesktopCourseRouteMapPointType.Beacon
            CoursePointSymbol.Target -> DesktopCourseRouteMapPointType.Finish
            CoursePointSymbol.Circle,
            null -> DesktopCourseRouteMapPointType.Waypoint
        }

    private fun CourseLineStyle.blackRenderStyle(): CourseLineStyle? {
        val colorArgb = argb ?: return null
        return if ((colorArgb and 0x00FFFFFFL) == 0L) {
            this
        } else {
            null
        }
    }

    private fun CourseLineStyle.isSolidLine(): Boolean =
        widthPixels?.let { it > DesktopCourseRouteMapStyle.GraphicLineStrokePixels } == true

    private fun DesktopCourseRouteMapLine.labelAnchor(
        x: (DesktopCourseRouteMapLinePoint) -> Double,
        y: (DesktopCourseRouteMapLinePoint) -> Double
    ): GraphicLineLabelAnchor? {
        val segments = points.zipWithNext()
            .mapNotNull { (from, to) ->
                val fromX = x(from)
                val fromY = y(from)
                val toX = x(to)
                val toY = y(to)
                val dx = toX - fromX
                val dy = toY - fromY
                val length = hypot(dx, dy).takeIf { it > 0.0 } ?: return@mapNotNull null
                GraphicLineSegment(fromX, fromY, dx, dy, length)
            }
        val targetDistance = segments.sumOf { it.length } / 2.0
        var distanceSoFar = 0.0
        segments.forEach { segment ->
            if (distanceSoFar + segment.length >= targetDistance) {
                val fraction = ((targetDistance - distanceSoFar) / segment.length).coerceIn(0.0, 1.0)
                return GraphicLineLabelAnchor(
                    x = segment.fromX + segment.dx * fraction,
                    y = segment.fromY + segment.dy * fraction,
                    normalX = -segment.dy / segment.length,
                    normalY = segment.dx / segment.length
                )
            }
            distanceSoFar += segment.length
        }
        return null
    }

    private fun DesktopCourseRouteMapLine.imageSmoothPath(): Path2D.Double =
        smoothPath(
            x = { point -> imageX(point).toDouble() },
            y = { point -> imageY(point).toDouble() }
        )

    private fun DesktopCourseRouteMapLine.smoothPath(
        x: (DesktopCourseRouteMapLinePoint) -> Double,
        y: (DesktopCourseRouteMapLinePoint) -> Double
    ): Path2D.Double {
        val path = Path2D.Double()
        val first = points.firstOrNull() ?: return path
        path.moveTo(x(first), y(first))
        if (points.size < 2) {
            return path
        }
        if (points.size == 2) {
            val end = points[1]
            path.lineTo(x(end), y(end))
            return path
        }
        for (index in 1 until points.size - 2) {
            val control = points[index]
            val next = points[index + 1]
            path.quadTo(
                x(control),
                y(control),
                (x(control) + x(next)) / 2.0,
                (y(control) + y(next)) / 2.0
            )
        }
        val control = points[points.lastIndex - 1]
        val end = points.last()
        path.quadTo(x(control), y(control), x(end), y(end))
        return path
    }

    private fun StringBuilder.appendPdfSmoothLine(line: DesktopCourseRouteMapLine) {
        val first = line.points.firstOrNull() ?: return
        var currentX = pdfX(first)
        var currentY = pdfY(first)
        appendLine("${pdfNumber(currentX)} ${pdfNumber(currentY)} m")
        if (line.points.size < 2) {
            return
        }
        if (line.points.size == 2) {
            val end = line.points[1]
            appendLine("${pdfPoint(end)} l S")
            return
        }
        for (index in 1 until line.points.size - 2) {
            val control = line.points[index]
            val next = line.points[index + 1]
            val endX = (pdfX(control) + pdfX(next)) / 2.0
            val endY = (pdfY(control) + pdfY(next)) / 2.0
            appendPdfQuadratic(currentX, currentY, pdfX(control), pdfY(control), endX, endY)
            currentX = endX
            currentY = endY
        }
        val control = line.points[line.points.lastIndex - 1]
        val end = line.points.last()
        appendPdfQuadratic(currentX, currentY, pdfX(control), pdfY(control), pdfX(end), pdfY(end))
        appendLine("S")
    }

    private fun StringBuilder.appendPdfQuadratic(
        startX: Double,
        startY: Double,
        controlX: Double,
        controlY: Double,
        endX: Double,
        endY: Double
    ) {
        val firstControlX = startX + (controlX - startX) * 2.0 / 3.0
        val firstControlY = startY + (controlY - startY) * 2.0 / 3.0
        val secondControlX = endX + (controlX - endX) * 2.0 / 3.0
        val secondControlY = endY + (controlY - endY) * 2.0 / 3.0
        appendLine(
            "${pdfNumber(firstControlX)} ${pdfNumber(firstControlY)} " +
                "${pdfNumber(secondControlX)} ${pdfNumber(secondControlY)} " +
                "${pdfNumber(endX)} ${pdfNumber(endY)} c"
        )
    }

    private fun DesktopCourseRouteMapPolygon.labelPoint(): DesktopCourseRouteMapLinePoint? {
        if (points.isEmpty()) {
            return null
        }
        return DesktopCourseRouteMapLinePoint(
            xFraction = points.map { it.xFraction }.average(),
            yFraction = points.map { it.yFraction }.average()
        )
    }

    private fun DesktopCourseRouteMapLine.dashPattern(): FloatArray? =
        if (dashed) {
            val (paintPixels, gapPixels) = dashPatternPixels()
            floatArrayOf(paintPixels, gapPixels)
        } else {
            null
        }

    private fun DesktopCourseRouteMapLine.dashPatternPixels(): Pair<Float, Float> =
        if (isBlackStroke()) {
            DesktopCourseRouteMapStyle.GraphicBlackDashPaintPixels to
                DesktopCourseRouteMapStyle.GraphicBlackDashGapPixels
        } else {
            DesktopCourseRouteMapStyle.GraphicFuchsiaDashPaintPixels to
                DesktopCourseRouteMapStyle.GraphicFuchsiaDashGapPixels
        }

    private fun StringBuilder.appendPdfDashPattern(line: DesktopCourseRouteMapLine) {
        if (line.dashed) {
            val (paintPixels, gapPixels) = line.dashPatternPixels()
            appendLine(
                "[${pdfNumber(paintPixels.toDouble())} " +
                    "${pdfNumber(gapPixels.toDouble())}] 0 d"
            )
        } else {
            appendLine("[] 0 d")
        }
    }

    private fun DesktopCourseRouteMapLine.isBlackStroke(): Boolean =
        strokeColorArgb?.let { (it and 0x00FFFFFFL) == 0L } == true

    private fun DesktopCourseRouteMap.imagePointMarkerBounds(markerRadius: Int): List<Rectangle> =
        points.map { point ->
            Rectangle(
                imageX(point) - markerRadius,
                imageY(point) - markerRadius,
                markerRadius * 2,
                markerRadius * 2
            )
        }

    private fun DesktopCourseRouteMap.pdfPointMarkerBounds(): List<Rectangle> =
        points.map { point ->
            val x = pdfScreenX(point).toInt()
            val y = pdfScreenY(point).toInt()
            Rectangle(x - 10, y - 10, 20, 20)
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

    private fun pdfLabelWidth(label: String, fontSize: Double): Double =
        label.length * fontSize * 0.55

    private fun pdfLabelBaselineY(placement: GraphicLabelPlacement): Double =
        PdfMapBottom + PdfMapHeight - (placement.y - PdfMapBottom) - placement.height + 2.0

    private fun pdfScreenX(point: DesktopCourseRouteMapPoint): Double =
        PdfMapLeft + point.xFraction.coerceIn(0.0, 1.0) * PdfMapWidth

    private fun pdfScreenY(point: DesktopCourseRouteMapPoint): Double =
        PdfMapBottom + point.yFraction.coerceIn(0.0, 1.0) * PdfMapHeight

    private fun pdfScreenX(point: DesktopCourseRouteMapLinePoint): Double =
        PdfMapLeft + point.xFraction.coerceIn(0.0, 1.0) * PdfMapWidth

    private fun pdfScreenY(point: DesktopCourseRouteMapLinePoint): Double =
        PdfMapBottom + point.yFraction.coerceIn(0.0, 1.0) * PdfMapHeight

    private fun pdfX(point: DesktopCourseRouteMapPoint): Double =
        PdfMapLeft + point.xFraction.coerceIn(0.0, 1.0) * PdfMapWidth

    private fun pdfY(point: DesktopCourseRouteMapPoint): Double =
        PdfMapBottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * PdfMapHeight

    private fun pdfX(point: DesktopCourseRouteMapLinePoint): Double =
        PdfMapLeft + point.xFraction.coerceIn(0.0, 1.0) * PdfMapWidth

    private fun pdfY(point: DesktopCourseRouteMapLinePoint): Double =
        PdfMapBottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * PdfMapHeight

    private fun pdfNumber(value: Double): String =
        DesktopPdfDocument.number(value)
}
