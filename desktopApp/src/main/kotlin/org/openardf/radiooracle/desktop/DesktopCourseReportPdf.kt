package org.openardf.radiooracle.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import org.openardf.radiooracle.shared.event.EventProjectFile

/** Printable counterpart of the reports already calculated for Setup > Courses. */
internal object DesktopCourseReportPdf {
    private const val Left = 54.0
    private const val Right = 54.0
    private const val Top = 750.0
    private const val Bottom = 54.0
    private const val ContentWidth = DesktopPdfDocument.LetterWidth - Left - Right
    private const val TableRowHeight = 18.0

    fun defaultFileName(projectFile: EventProjectFile): String =
        DesktopProjectFilePaths.defaultPdfFileName(projectFile.raceData.race.name, "course report")

    fun exportPdf(path: Path, projectFile: EventProjectFile, reports: List<DesktopCourseBriefReport>) {
        require(reports.isNotEmpty()) { "No course reports are available to export." }
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(projectFile, reports))
    }

    internal fun pdfBytes(projectFile: EventProjectFile, reports: List<DesktopCourseBriefReport>): ByteArray {
        require(reports.isNotEmpty()) { "No course reports are available to export." }
        val pages = reports.flatMap { report -> reportPages(report) }
        val contents = pages.mapIndexed { index, page ->
            buildString {
                appendPageHeader(projectFile.raceData.race.name, reportName(page.report), index + 1, pages.size)
                when (page) {
                    is CoursePage.Overview -> appendOverview(page)
                    is CoursePage.Notes -> appendNotes(page)
                    is CoursePage.Graphics -> appendGraphics(page.report)
                }
            }
        }
        return DesktopPdfDocument.bytes(contents)
    }

    private sealed class CoursePage(open val report: DesktopCourseBriefReport) {
        data class Overview(
            override val report: DesktopCourseBriefReport,
            val includeMetrics: Boolean,
            val legs: List<DesktopCourseBriefLeg>,
            val continuation: Boolean,
            val notes: List<NoteLine> = emptyList()
        ) : CoursePage(report)

        data class Notes(
            override val report: DesktopCourseBriefReport,
            val lines: List<NoteLine>,
            val continuation: Boolean
        ) : CoursePage(report)

        data class Graphics(override val report: DesktopCourseBriefReport) : CoursePage(report)
    }

    private data class NoteLine(val text: String, val warning: Boolean)

    /** Keeps each table row intact and reserves a separate page for the two graphics shown in the UI. */
    private fun reportPages(report: DesktopCourseBriefReport): List<CoursePage> = buildList {
        val metricLines = overviewLines(report)
        val notes = noteLines(report)
        val yAfterMetrics = 690.0 - metricLines.size * 14.0
        val canInlineNotes = notes.size <= 10
        val inlineNotesHeight = if (canInlineNotes && notes.isNotEmpty()) 26.0 + notes.size * 14.0 else 0.0
        val firstLegCapacityWithNotes =
            ((yAfterMetrics - Bottom - 62.0 - inlineNotesHeight) / TableRowHeight).toInt().coerceAtLeast(0)
        val inlineNotes = canInlineNotes && report.idealRouteLegs.size <= firstLegCapacityWithNotes
        val firstLegCapacity = if (inlineNotes) firstLegCapacityWithNotes else
            ((yAfterMetrics - Bottom - 62.0) / TableRowHeight).toInt().coerceAtLeast(0)
        if (report.idealRouteLegs.isEmpty()) {
            add(CoursePage.Overview(report, includeMetrics = true, legs = emptyList(), continuation = false,
                notes = notes.takeIf { canInlineNotes }.orEmpty()))
        } else if (firstLegCapacity > 0) {
            val first = report.idealRouteLegs.take(firstLegCapacity)
            add(CoursePage.Overview(report, includeMetrics = true, legs = first, continuation = false,
                notes = notes.takeIf { inlineNotes }.orEmpty()))
            report.idealRouteLegs.drop(first.size).chunked(32).forEach { legs ->
                add(CoursePage.Overview(report, includeMetrics = false, legs = legs, continuation = true))
            }
        } else {
            add(CoursePage.Overview(report, includeMetrics = true, legs = emptyList(), continuation = false))
            report.idealRouteLegs.chunked(32).forEach { legs ->
                add(CoursePage.Overview(report, includeMetrics = false, legs = legs, continuation = true))
            }
        }
        notes.takeUnless { canInlineNotes && (report.idealRouteLegs.isEmpty() || inlineNotes) }.orEmpty()
            .chunked(45).forEachIndexed { index, lines ->
            add(CoursePage.Notes(report, lines, continuation = index > 0))
        }
        add(CoursePage.Graphics(report))
    }

    private fun reportName(report: DesktopCourseBriefReport): String = report.courseName.ifBlank { "Unnamed course" }

    private fun StringBuilder.appendPageHeader(raceName: String, courseName: String, page: Int, pageCount: Int) {
        appendText(Left, Top, 18, "Course Report", bold = true)
        appendText(Left, Top - 20.0, 10, "Race: ${raceName.ifBlank { "Untitled Race" }}")
        appendText(Left, Top - 35.0, 10, "Course: $courseName")
        appendText(DesktopPdfDocument.LetterWidth - Right - 68.0, Top - 20.0, 9, "Page $page of $pageCount")
        appendLine("0.72 0.72 0.72 RG")
        appendLine("0.6 w")
        appendLine("${number(Left)} ${number(Top - 45.0)} m ${number(DesktopPdfDocument.LetterWidth - Right)} ${number(Top - 45.0)} l S")
    }

    private fun StringBuilder.appendOverview(page: CoursePage.Overview) {
        var y = 690.0
        if (page.includeMetrics) {
            overviewLines(page.report).forEach { line ->
                appendText(Left, y, 10, line)
                y -= 14.0
            }
            y -= 8.0
        }
        if (page.legs.isNotEmpty()) {
            appendText(Left, y, 13, if (page.continuation) "Ideal route legs (continued)" else "Ideal route legs", bold = true)
            y -= 15.0
            if (!page.continuation) {
                appendText(Left, y, 9, "Each distance follows the ideal route between the listed course objects, including any mandatory bends.")
                y -= 13.0
            }
            y = appendLegTable(page.legs, y) - 18.0
        }
        if (page.notes.isNotEmpty()) {
            appendText(Left, y, 13, "Course notes", bold = true)
            y -= 20.0
            page.notes.forEach { line ->
                appendText(Left, y, 10, line.text, red = line.warning)
                y -= 14.0
            }
        }
    }

    private fun overviewLines(report: DesktopCourseBriefReport): List<String> = buildList {
        add("Horizontal length: ${DesktopCourseAnalyzer.summaryLengthText(report.horizontalLengthMeters)}")
        add("Total climb: ${DesktopCourseAnalyzer.summaryClimbText(report.climbMeters)}")
        add("Effective length: ${DesktopCourseAnalyzer.summaryLengthText(report.effectiveLengthMeters)}")
        val order = report.idealOrder.takeIf { it.isNotEmpty() }?.joinToString(" -> ") ?: "Unavailable"
        addAll(wrap("Ideal order: $order", 92))
        report.estimatedIdealSeconds?.let { seconds ->
            val pace = report.assumedPaceMinutesPerKm
                ?.let { " (${String.format(Locale.ROOT, "%.1f", it)} min/km)" }
                .orEmpty()
            add("Estimated ideal time: ${DesktopCourseAnalyzer.summaryDurationText(seconds)}$pace")
        }
    }

    private fun noteLines(report: DesktopCourseBriefReport): List<NoteLine> = buildList {
        report.legWarnings.forEach { warning -> wrap(warning, 96).forEach { add(NoteLine(it, warning = true)) } }
        report.notice?.let { notice -> wrap(notice, 96).forEach { add(NoteLine(it, warning = false)) } }
    }

    private fun StringBuilder.appendNotes(page: CoursePage.Notes) {
        var y = 690.0
        appendText(Left, y, 13, if (page.continuation) "Course notes (continued)" else "Course notes", bold = true)
        y -= 22.0
        page.lines.forEach { line ->
            appendText(Left, y, 10, line.text, red = line.warning)
            y -= 14.0
        }
    }

    private fun StringBuilder.appendLegTable(legs: List<DesktopCourseBriefLeg>, tableTop: Double): Double {
        val widths = listOf(355.0, 149.0)
        val headers = listOf("Ideal-order leg", "Distance")
        val headerBottom = tableTop - 20.0
        appendLine("0.90 0.90 0.90 rg")
        appendLine("${number(Left)} ${number(headerBottom)} ${number(ContentWidth)} 20 re f")
        appendLine("0.45 0.45 0.45 RG")
        appendLine("0.5 w")
        appendLine("${number(Left)} ${number(headerBottom)} ${number(ContentWidth)} 20 re S")
        var x = Left
        widths.zip(headers).forEach { (width, header) ->
            appendText(x + 4.0, tableTop - 14.0, 8, fitText(header, width, 8), bold = true)
            x += width
        }
        legs.forEachIndexed { index, leg ->
            val rowTop = headerBottom - index * TableRowHeight
            val rowBottom = rowTop - TableRowHeight
            if (index % 2 == 1) {
                appendLine("0.97 0.97 0.97 rg")
                appendLine("${number(Left)} ${number(rowBottom)} ${number(ContentWidth)} ${number(TableRowHeight)} re f")
            }
            val values = listOf(
                "${leg.fromLabel} -> ${leg.toLabel}",
                DesktopCourseAnalyzer.summaryLengthText(leg.distanceMeters)
            )
            x = Left
            widths.zip(values).forEach { (width, value) ->
                appendText(x + 4.0, rowTop - 12.0, 8, fitText(value, width, 8))
                x += width
            }
            appendLine("0.82 0.82 0.82 RG")
            appendLine("${number(Left)} ${number(rowBottom)} m ${number(Left + ContentWidth)} ${number(rowBottom)} l S")
        }
        x = Left
        widths.forEach { width ->
            appendLine("${number(x)} ${number(tableTop)} m ${number(x)} ${number(headerBottom - legs.size * TableRowHeight)} l S")
            x += width
        }
        appendLine("${number(x)} ${number(tableTop)} m ${number(x)} ${number(headerBottom - legs.size * TableRowHeight)} l S")
        return headerBottom - legs.size * TableRowHeight
    }

    private fun StringBuilder.appendGraphics(report: DesktopCourseBriefReport) {
        appendText(Left, 690.0, 13, "2D route depiction", bold = true)
        report.routeMap?.let { map ->
            appendText(Left, 674.0, 9, map.northOrientationText().replace("°", " degrees"))
            appendRouteMap(map, Left, 420.0, ContentWidth, 240.0)
        } ?: run {
            appendPlaceholder(Left, 420.0, ContentWidth, 240.0, "Course graphic unavailable.")
        }

        appendText(Left, 385.0, 13, "Elevation profile", bold = true)
        report.elevationProfile?.let { profile ->
            val minElevation = profile.profile.minOf { it.elevationMeters }
            val maxElevation = profile.profile.maxOf { it.elevationMeters }
            val distance = profile.profile.lastOrNull()?.distanceMeters ?: 0
            appendText(Left, 369.0, 9, "0.00 km to ${twoDecimals(distance / 1000.0)} km, ${minElevation.roundToInt()} m to ${maxElevation.roundToInt()} m")
            appendElevationProfile(profile, Left, 105.0, ContentWidth, 245.0)
        } ?: run {
            appendPlaceholder(Left, 105.0, ContentWidth, 245.0, "Elevation profile unavailable: route elevation data is incomplete.")
        }
    }

    private fun StringBuilder.appendPlaceholder(left: Double, bottom: Double, width: Double, height: Double, text: String) {
        appendLine("0.78 0.78 0.78 RG")
        appendLine("${number(left)} ${number(bottom)} ${number(width)} ${number(height)} re S")
        appendText(left + 12.0, bottom + height / 2.0, 10, text)
    }

    private fun StringBuilder.appendRouteMap(map: DesktopCourseRouteMap, left: Double, bottom: Double, width: Double, height: Double) {
        val inset = 12.0
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
            appendLine("${number((line.strokeWidthPixels ?: DesktopCourseRouteMapStyle.GraphicLineStrokePixels).toDouble())} w")
            if (line.dashed) appendLine("[10 5] 0 d") else appendLine("[] 0 d")
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
            appendCircle(x(point), y(point), 4.2)
            appendText(x(point) + 6.0, y(point) + 5.0, 8, point.label)
        }
        appendNorthArrow(left + width - 28.0, bottom + height - 22.0)
        DesktopCourseRouteMapStyle.scaleBar(map.xRangeMeters, drawingWidth)?.let { scale ->
            val barY = bottom + 12.0
            appendLine("0 0 0 RG")
            appendLine("1.4 w")
            appendLine("${number(drawingLeft)} ${number(barY)} m ${number(drawingLeft + scale.drawingLength)} ${number(barY)} l S")
            appendText(drawingLeft, barY + 5.0, 7, scale.label)
        }
    }

    private fun StringBuilder.appendNorthArrow(x: Double, y: Double) {
        appendLine("0 0 0 RG")
        appendLine("1.4 w")
        appendLine("${number(x)} ${number(y - 36.0)} m ${number(x)} ${number(y)} l S")
        appendLine("${number(x)} ${number(y)} m ${number(x - 5.0)} ${number(y - 9.0)} l ${number(x + 5.0)} ${number(y - 9.0)} l f")
        appendText(x - 3.0, y + 4.0, 8, "N", bold = true)
    }

    private fun StringBuilder.appendElevationProfile(
        summary: DesktopCourseElevationProfileSummary,
        left: Double,
        bottom: Double,
        width: Double,
        height: Double
    ) {
        val profile = summary.profile
        val plotLeft = left + 38.0
        val plotBottom = bottom + 30.0
        val plotWidth = width - 50.0
        val plotHeight = height - 45.0
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
        appendLine("2.2 w")
        profile.zipWithNext().forEach { (start, end) ->
            appendLine("${number(x(start.distanceMeters))} ${number(y(start.elevationMeters))} m ${number(x(end.distanceMeters))} ${number(y(end.elevationMeters))} l S")
        }
        summary.markers.forEach { marker ->
            appendLine("1.00 0.54 0.00 rg")
            appendCircle(x(marker.distanceMeters), y(marker.elevationMeters), 3.5)
        }
        val markerText = summary.markers.joinToString("  ") { marker ->
            "${marker.label} ${twoDecimals(marker.distanceMeters / 1000.0)} km"
        }
        wrap(markerText, 95).take(2).forEachIndexed { index, line ->
            appendText(left + 4.0, bottom + 14.0 - index * 10.0, 7, line)
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

    private fun twoDecimals(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    private fun number(value: Double): String = DesktopPdfDocument.number(value)
}
