package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import org.openardf.radiooracle.shared.event.EventProjectFile

/** Compact printable counterpart of the reports already calculated for Setup > Courses. */
internal object DesktopCourseReportPdf {
    private const val Left = 42.0
    private const val Right = 42.0
    private const val DocumentTop = 758.0
    private const val HeaderBottom = 731.0
    private const val ContentTop = 721.0
    private const val Bottom = 42.0
    private const val ContentWidth = DesktopPdfDocument.LetterWidth - Left - Right
    private const val MetricsWidth = 238.0
    private const val ColumnGap = 10.0
    private const val LegTableWidth = ContentWidth - MetricsWidth - ColumnGap
    private const val CourseGap = 8.0
    private const val CourseHeaderHeight = 24.0
    private const val MetricLineHeight = 9.0
    private const val LegTitleHeight = 10.0
    private const val TableHeaderHeight = 9.0
    private const val TableRowHeight = 9.0
    private const val NoteLineHeight = 8.0
    private const val GraphicsGap = 8.0
    private const val GraphicsLabelHeight = 19.0
    private const val GraphicFrameHeight = 120.0
    private const val GraphicGap = 10.0
    private const val GraphicWidth = (ContentWidth - GraphicGap) / 2.0
    private const val BlockBottomPadding = 6.0

    fun defaultFileName(projectFile: EventProjectFile): String =
        DesktopProjectFilePaths.defaultPdfFileName(projectFile.raceData.race.name, "course report")

    fun exportPdf(path: Path, projectFile: EventProjectFile, reports: List<DesktopCourseBriefReport>) {
        require(reports.isNotEmpty()) { "No course reports are available to export." }
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(projectFile, reports))
    }

    internal fun pdfBytes(projectFile: EventProjectFile, reports: List<DesktopCourseBriefReport>): ByteArray {
        require(reports.isNotEmpty()) { "No course reports are available to export." }
        val pages = reportPages(reports)
        val contents = pages.mapIndexed { pageIndex, blocks ->
            buildString {
                appendPageHeader(projectFile.raceData.race.name, pageIndex + 1, pages.size)
                var top = ContentTop
                blocks.forEach { block ->
                    appendCourseBlock(block, top)
                    top -= block.height + CourseGap
                }
            }
        }
        return DesktopPdfDocument.bytes(contents)
    }

    private data class NoteLine(val text: String, val warning: Boolean)

    private data class CourseBlock(
        val report: DesktopCourseBriefReport,
        val metrics: List<String>,
        val notes: List<NoteLine>,
        val detailsHeight: Double,
        val noteHeight: Double,
        val height: Double
    )

    /** Packs complete course blocks without splitting a table or either graphic across pages. */
    private fun reportPages(reports: List<DesktopCourseBriefReport>): List<List<CourseBlock>> {
        val availableHeight = ContentTop - Bottom
        val pages = mutableListOf<List<CourseBlock>>()
        var current = mutableListOf<CourseBlock>()
        var usedHeight = 0.0
        reports.map(::courseBlock).forEach { block ->
            val requiredHeight = block.height + if (current.isEmpty()) 0.0 else CourseGap
            if (current.isNotEmpty() && usedHeight + requiredHeight > availableHeight) {
                pages += current
                current = mutableListOf()
                usedHeight = 0.0
            }
            current += block
            usedHeight += block.height + if (current.size == 1) 0.0 else CourseGap
        }
        if (current.isNotEmpty()) pages += current
        return pages
    }

    private fun courseBlock(report: DesktopCourseBriefReport): CourseBlock {
        val metrics = overviewLines(report)
        val notes = noteLines(report)
        val metricHeight = metrics.size * MetricLineHeight
        val tableHeight = if (report.idealRouteLegs.isEmpty()) 0.0 else
            LegTitleHeight + TableHeaderHeight + report.idealRouteLegs.size * TableRowHeight
        val detailsHeight = max(metricHeight, tableHeight)
        val noteHeight = if (notes.isEmpty()) 0.0 else 5.0 + notes.size * NoteLineHeight
        val height = CourseHeaderHeight + detailsHeight + noteHeight + GraphicsGap +
            GraphicsLabelHeight + GraphicFrameHeight + BlockBottomPadding
        return CourseBlock(report, metrics, notes, detailsHeight, noteHeight, height)
    }

    private fun StringBuilder.appendPageHeader(raceName: String, page: Int, pageCount: Int) {
        appendText(Left, DocumentTop, 15, "Course Report", bold = true)
        appendText(Left, DocumentTop - 16.0, 8, "Race: ${raceName.ifBlank { "Untitled Race" }}")
        appendText(DesktopPdfDocument.LetterWidth - Right - 62.0, DocumentTop - 10.0, 8, "Page $page of $pageCount")
        appendLine("0.72 0.72 0.72 RG")
        appendLine("0.6 w")
        appendLine("${number(Left)} ${number(HeaderBottom)} m ${number(Left + ContentWidth)} ${number(HeaderBottom)} l S")
    }

    private fun StringBuilder.appendCourseBlock(block: CourseBlock, top: Double) {
        val report = block.report
        appendText(Left, top - 11.0, 10, "Course: ${reportName(report)}", bold = true)
        appendLine("0.82 0.82 0.82 RG")
        appendLine("0.4 w")
        appendLine("${number(Left)} ${number(top - 17.0)} m ${number(Left + ContentWidth)} ${number(top - 17.0)} l S")

        val detailsTop = top - CourseHeaderHeight
        appendMetrics(block.metrics, detailsTop)
        if (report.idealRouteLegs.isNotEmpty()) {
            appendLegTable(report.idealRouteLegs, Left + MetricsWidth + ColumnGap, detailsTop)
        }

        val detailsBottom = detailsTop - block.detailsHeight
        var noteY = detailsBottom - 7.0
        block.notes.forEach { line ->
            appendText(Left, noteY, 6, line.text, red = line.warning)
            noteY -= NoteLineHeight
        }

        val graphicsTop = detailsBottom - block.noteHeight - GraphicsGap
        appendGraphics(report, graphicsTop)
    }

    private fun StringBuilder.appendMetrics(lines: List<String>, top: Double) {
        var y = top - 7.0
        lines.forEach { line ->
            appendText(Left, y, 7, fitText(line, MetricsWidth, 7))
            y -= MetricLineHeight
        }
    }

    private fun overviewLines(report: DesktopCourseBriefReport): List<String> = buildList {
        add("Horizontal length: ${DesktopCourseAnalyzer.summaryLengthText(report.horizontalLengthMeters)}")
        add("Total climb: ${DesktopCourseAnalyzer.summaryClimbText(report.climbMeters)}")
        add("Effective length: ${DesktopCourseAnalyzer.summaryLengthText(report.effectiveLengthMeters)}")
        val order = report.idealOrder.takeIf { it.isNotEmpty() }?.joinToString(" -> ") ?: "Unavailable"
        addAll(wrap("Ideal order: $order", 58))
        report.estimatedIdealSeconds?.let { seconds ->
            val pace = report.assumedPaceMinutesPerKm
                ?.let { " (${String.format(Locale.ROOT, "%.1f", it)} min/km)" }
                .orEmpty()
            add("Estimated ideal time: ${DesktopCourseAnalyzer.summaryDurationText(seconds)}$pace")
        }
    }

    private fun noteLines(report: DesktopCourseBriefReport): List<NoteLine> = buildList {
        report.legWarnings.forEach { warning -> wrap(warning, 125).forEach { add(NoteLine(it, warning = true)) } }
        report.notice?.let { notice -> wrap(notice, 125).forEach { add(NoteLine(it, warning = false)) } }
    }

    private fun StringBuilder.appendLegTable(legs: List<DesktopCourseBriefLeg>, left: Double, top: Double) {
        appendText(left, top - 7.0, 6, "Ideal route legs - course-object distances include mandatory bends", bold = true)
        val tableTop = top - LegTitleHeight
        val legWidth = LegTableWidth - 61.0
        val widths = listOf(legWidth, 61.0)
        val headers = listOf("Ideal-order leg", "Distance")
        val headerBottom = tableTop - TableHeaderHeight
        appendLine("0.90 0.90 0.90 rg")
        appendLine("${number(left)} ${number(headerBottom)} ${number(LegTableWidth)} ${number(TableHeaderHeight)} re f")
        appendLine("0.45 0.45 0.45 RG")
        appendLine("0.4 w")
        appendLine("${number(left)} ${number(headerBottom)} ${number(LegTableWidth)} ${number(TableHeaderHeight)} re S")
        var x = left
        widths.zip(headers).forEach { (width, header) ->
            appendText(x + 2.0, tableTop - 6.5, 6, fitText(header, width, 6), bold = true)
            x += width
        }
        legs.forEachIndexed { index, leg ->
            val rowTop = headerBottom - index * TableRowHeight
            val rowBottom = rowTop - TableRowHeight
            if (index % 2 == 1) {
                appendLine("0.97 0.97 0.97 rg")
                appendLine("${number(left)} ${number(rowBottom)} ${number(LegTableWidth)} ${number(TableRowHeight)} re f")
            }
            val values = listOf(
                "${leg.fromLabel} -> ${leg.toLabel}",
                leg.distanceText
            )
            x = left
            widths.zip(values).forEach { (width, value) ->
                appendText(x + 2.0, rowTop - 6.5, 6, fitText(value, width, 6))
                x += width
            }
            appendLine("0.82 0.82 0.82 RG")
            appendLine("${number(left)} ${number(rowBottom)} m ${number(left + LegTableWidth)} ${number(rowBottom)} l S")
        }
        x = left
        widths.forEach { width ->
            appendLine("${number(x)} ${number(tableTop)} m ${number(x)} ${number(headerBottom - legs.size * TableRowHeight)} l S")
            x += width
        }
        appendLine("${number(x)} ${number(tableTop)} m ${number(x)} ${number(headerBottom - legs.size * TableRowHeight)} l S")
    }

    private fun StringBuilder.appendGraphics(report: DesktopCourseBriefReport, top: Double) {
        val profileLeft = Left + GraphicWidth + GraphicGap
        appendText(Left, top - 7.0, 7, "2D route depiction", bold = true)
        report.routeMap?.let { map ->
            appendText(Left, top - 15.0, 5, map.northOrientationText().replace("°", " degrees"))
        }
        appendText(profileLeft, top - 7.0, 7, "Elevation profile", bold = true)
        report.elevationProfile?.let { profile ->
            val minElevation = profile.profile.minOf { it.elevationMeters }
            val maxElevation = profile.profile.maxOf { it.elevationMeters }
            val distance = profile.profile.lastOrNull()?.distanceMeters ?: 0
            appendText(profileLeft, top - 15.0, 5,
                "0.00-${twoDecimals(distance / 1000.0)} km, ${minElevation.roundToInt()}-${maxElevation.roundToInt()} m")
        }

        val frameTop = top - GraphicsLabelHeight
        val frameBottom = frameTop - GraphicFrameHeight
        report.routeMap?.let { map ->
            appendRouteMap(map, Left, frameBottom, GraphicWidth, GraphicFrameHeight)
        } ?: run {
            appendPlaceholder(Left, frameBottom, GraphicWidth, GraphicFrameHeight, "Course graphic unavailable.")
        }
        report.elevationProfile?.let { profile ->
            appendElevationProfile(profile, profileLeft, frameBottom, GraphicWidth, GraphicFrameHeight)
        } ?: run {
            appendPlaceholder(profileLeft, frameBottom, GraphicWidth, GraphicFrameHeight,
                "Elevation profile unavailable: route elevation data is incomplete.")
        }
    }

    private fun StringBuilder.appendPlaceholder(left: Double, bottom: Double, width: Double, height: Double, text: String) {
        appendLine("0.78 0.78 0.78 RG")
        appendLine("${number(left)} ${number(bottom)} ${number(width)} ${number(height)} re S")
        appendText(left + 6.0, bottom + height / 2.0, 6, fitText(text, width - 12.0, 6))
    }

    private fun StringBuilder.appendRouteMap(map: DesktopCourseRouteMap, left: Double, bottom: Double, width: Double, height: Double) {
        val inset = 6.0
        val drawingLeft = left + inset
        val drawingBottom = bottom + inset
        val drawingWidth = width - inset * 2
        val drawingHeight = height - inset * 2
        fun x(point: DesktopCourseRouteMapLinePoint): Double = drawingLeft + point.xFraction.coerceIn(0.0, 1.0) * drawingWidth
        fun y(point: DesktopCourseRouteMapLinePoint): Double = drawingBottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * drawingHeight
        fun x(point: DesktopCourseRouteMapPoint): Double = drawingLeft + point.xFraction.coerceIn(0.0, 1.0) * drawingWidth
        fun y(point: DesktopCourseRouteMapPoint): Double = drawingBottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * drawingHeight

        appendLine("0.70 0.70 0.70 RG")
        appendLine("${number(left)} ${number(bottom)} ${number(width)} ${number(height)} re S")
        map.routeLinesForDrawing().forEach { line ->
            val (red, green, blue) = line.strokeColorArgb?.let(DesktopCourseRouteMapStyle::pdfRgb)
                ?: DesktopCourseRouteMapStyle.linePdfRgb()
            appendLine("${number(red)} ${number(green)} ${number(blue)} RG")
            val stroke = (line.strokeWidthPixels ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels).toDouble() * 0.55
            appendLine("${number(stroke)} w")
            if (line.dashed) appendLine("[6 3] 0 d") else appendLine("[] 0 d")
            line.points.firstOrNull()?.let { first ->
                appendLine("${number(x(first))} ${number(y(first))} m")
                line.points.drop(1).forEach { point -> appendLine("${number(x(point))} ${number(y(point))} l") }
                appendLine("S")
            }
        }
        appendLine("[] 0 d")
        map.pointsForDrawing().forEach { point ->
            val (red, green, blue) = DesktopCourseRouteMapStyle.pdfRgb(point.type)
            appendLine("${number(red)} ${number(green)} ${number(blue)} rg")
            appendCircle(x(point), y(point), 2.5)
            appendText(x(point) + 3.5, y(point) + 2.5, 5, point.label)
        }
        appendNorthArrow(left + width - 14.0, bottom + height - 10.0)
        DesktopCourseRouteMapStyle.scaleBar(map.xRangeMeters, drawingWidth)?.let { scale ->
            val barY = bottom + 6.0
            appendLine("0 0 0 RG")
            appendLine("0.8 w")
            appendLine("${number(drawingLeft)} ${number(barY)} m ${number(drawingLeft + scale.drawingLength)} ${number(barY)} l S")
            appendText(drawingLeft, barY + 2.0, 5, scale.label)
        }
    }

    private fun StringBuilder.appendNorthArrow(x: Double, y: Double) {
        appendLine("0 0 0 RG")
        appendLine("0.8 w")
        appendLine("${number(x)} ${number(y - 18.0)} m ${number(x)} ${number(y)} l S")
        appendLine("${number(x)} ${number(y)} m ${number(x - 3.0)} ${number(y - 5.0)} l ${number(x + 3.0)} ${number(y - 5.0)} l f")
        appendText(x - 2.0, y + 2.0, 5, "N", bold = true)
    }

    private fun StringBuilder.appendElevationProfile(
        summary: DesktopCourseElevationProfileSummary,
        left: Double,
        bottom: Double,
        width: Double,
        height: Double
    ) {
        val profile = summary.profile
        val plotLeft = left + 22.0
        val plotBottom = bottom + 16.0
        val plotWidth = width - 28.0
        val plotHeight = height - 24.0
        val minElevation = profile.minOf { it.elevationMeters }
        val maxElevation = profile.maxOf { it.elevationMeters }
        val totalDistance = max(1.0, profile.lastOrNull()?.distanceMeters?.toDouble() ?: 1.0)
        val elevationRange = max(1.0, maxElevation - minElevation)
        fun x(distanceMeters: Int): Double = plotLeft + distanceMeters / totalDistance * plotWidth
        fun y(elevationMeters: Double): Double = plotBottom + (elevationMeters - minElevation) / elevationRange * plotHeight

        appendLine("0.70 0.70 0.70 RG")
        appendLine("${number(left)} ${number(bottom)} ${number(width)} ${number(height)} re S")
        appendLine("0.86 0.86 0.86 RG")
        repeat(4) { index ->
            val gridY = plotBottom + plotHeight * index / 3.0
            appendLine("${number(plotLeft)} ${number(gridY)} m ${number(plotLeft + plotWidth)} ${number(gridY)} l S")
        }
        appendLine("0.00 0.35 0.72 RG")
        appendLine("1.2 w")
        profile.zipWithNext().forEach { (start, end) ->
            appendLine("${number(x(start.distanceMeters))} ${number(y(start.elevationMeters))} m ${number(x(end.distanceMeters))} ${number(y(end.elevationMeters))} l S")
        }
        summary.markers.forEach { marker ->
            appendLine("1.00 0.54 0.00 rg")
            appendCircle(x(marker.distanceMeters), y(marker.elevationMeters), 2.2)
        }
        val markerText = summary.markers.joinToString("  ") { marker ->
            "${marker.label} ${twoDecimals(marker.distanceMeters / 1000.0)} km"
        }
        wrap(markerText, 72).take(2).forEachIndexed { index, line ->
            appendText(left + 3.0, bottom + 7.0 - index * 6.0, 5, line)
        }
    }

    private fun StringBuilder.appendCircle(centerX: Double, centerY: Double, radius: Double) {
        val control = radius * 0.5522847498
        appendLine("${number(centerX + radius)} ${number(centerY)} m")
        appendLine("${number(centerX + radius)} ${number(centerY + control)} ${number(centerX + control)} ${number(centerY + radius)} ${number(centerX)} ${number(centerY + radius)} c")
        appendLine("${number(centerX - control)} ${number(centerY + radius)} ${number(centerX - radius)} ${number(centerY + control)} ${number(centerX - radius)} ${number(centerY)} c")
        appendLine("${number(centerX - radius)} ${number(centerY - control)} ${number(centerX - control)} ${number(centerY - radius)} ${number(centerX)} ${number(centerY - radius)} c")
        appendLine("${number(centerX + control)} ${number(centerY - radius)} ${number(centerX + radius)} ${number(centerY - control)} ${number(centerX + radius)} ${number(centerY)} c f")
    }

    private fun StringBuilder.appendText(
        x: Double,
        y: Double,
        fontSize: Int,
        text: String,
        bold: Boolean = false,
        red: Boolean = false
    ) {
        appendLine("BT")
        appendLine("${if (bold) "/F2" else "/F1"} $fontSize Tf")
        appendLine(if (red) "0.78 0.10 0.10 rg" else "0 0 0 rg")
        appendLine("1 0 0 1 ${number(x)} ${number(y)} Tm")
        appendLine("(${DesktopExportPrimitives.pdfText(text)}) Tj")
        appendLine("ET")
    }

    private fun fitText(text: String, width: Double, fontSize: Int): String {
        val maxCharacters = (width / (fontSize * 0.52)).toInt().coerceAtLeast(4)
        return if (text.length <= maxCharacters) text else text.take(maxCharacters - 3).trimEnd() + "..."
    }

    private fun wrap(text: String, width: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.trim().split(Regex("\\s+")).forEach { word ->
            if (current.isEmpty()) current.append(word)
            else if (current.length + word.length + 1 <= width) current.append(' ').append(word)
            else {
                lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun reportName(report: DesktopCourseBriefReport): String = report.courseName.ifBlank { "Unnamed course" }
    private fun twoDecimals(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    private fun number(value: Double): String = DesktopPdfDocument.number(value)
}
