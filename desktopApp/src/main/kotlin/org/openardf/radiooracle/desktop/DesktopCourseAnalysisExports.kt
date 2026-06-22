package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Plain-text and PDF exports for the desktop Course Analyzer report. */
object DesktopCourseAnalysisExports {
    private const val PdfDividerLine = "----------------------------------------"

    fun defaultPdfFileName(result: DesktopCourseAnalysisSummary): String =
        "${courseAnalysisFileStem(result)}${DesktopProjectFilePaths.PDF_EXTENSION}"

    fun exportPdfAndKml(path: Path, result: DesktopCourseAnalysisSummary): DesktopCourseAnalysisExportPaths {
        exportPdf(path, result)
        val kmlPath = kmlPathForPdf(path, result)
        exportKml(kmlPath, result)
        return DesktopCourseAnalysisExportPaths(pdfPath = path, kmlPath = kmlPath)
    }

    fun exportPdf(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(result))
    }

    fun exportKml(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, kmlText(result, kmlFileStem(path)), StandardCharsets.UTF_8)
    }

    fun reportText(result: DesktopCourseAnalysisSummary): String =
        buildString {
            appendLine("Course Analyzer")
            appendLine("Event: ${result.eventName.ifBlank { "Untitled Event" }}")
            appendLine("Event file: ${result.eventFileName?.takeIf { it.isNotBlank() } ?: "Unsaved Event File"}")
            appendLine("Event format: ${result.eventFormatLabel}")
            appendLine("Event type: ${result.eventTypeLabel}")
            appendLine("Analyzed: ${result.analysisPerformedAtText}")
            appendLine("Category: ${result.categoryName}")
            appendLine("Rules applied: ${result.rulesDocumentLabel}")
            appendLine()
            val importedSummaryGroup = result.summaryGroups.firstOrNull { it.title == "Imported" }
            val calculatedSummaryGroup = result.summaryGroups.firstOrNull { it.title == "Calculated" }
            val importedMetricGroup = result.goodnessMetrics.groups.firstOrNull { it.title == "Imported" }
            val calculatedMetricGroup = result.goodnessMetrics.groups.firstOrNull { it.title == "Calculated" }
            result.providedRouteSection?.let { section ->
                appendSection(
                    section = section,
                    includeRenumbering = true,
                    summaryGroup = importedSummaryGroup,
                    metricGroup = importedMetricGroup
                )
                appendLine()
            }
            result.calculatedRouteSection?.let { section ->
                appendSection(
                    section = section,
                    includeRenumbering = false,
                    summaryGroup = calculatedSummaryGroup,
                    metricGroup = calculatedMetricGroup
                )
                appendLine()
            }
            appendSummary(result)
            if (result.missingElements.isNotEmpty()) {
                appendLine()
                appendLine("Partial analysis")
                result.missingElements.forEach { appendLine("- $it") }
            }
        }.trimEnd() + "\n"

    private fun StringBuilder.appendSection(
        section: DesktopCourseAnalysisSection,
        includeRenumbering: Boolean,
        summaryGroup: DesktopCourseAnalysisSummaryGroup?,
        metricGroup: DesktopCourseGoodnessMetricGroup?
    ) {
        appendLine(section.title)
        appendWrapped(section.explanation)
        appendLine("${section.routeOrderLabel}: ${section.routeOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        if (!section.summaryOnly) {
            section.secondaryRouteOrderLabel?.let { label ->
                appendLine("$label: ${section.secondaryRouteOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
            }
            appendLine("Horizontal length: ${kilometersText(section.routeLengthMeters)}")
            appendLine("Climb: ${climbText(section.climbMeters)}")
            appendLine("Effective length: ${kilometersText(section.effectiveLengthMeters)}")
            section.speedModel?.let { speedModel ->
                appendLine("Assumed running speed: ${speedModelText(speedModel)}")
            }
            appendLine("Estimated ideal time: ${secondsText(section.estimatedIdealSeconds)}")
            appendTimingBreakdown(section.legRows, section.estimatedIdealSeconds)
            appendLegRows(section.legRows)
            if (includeRenumbering) {
                appendLine()
                appendLine("Imported-route wait-time analysis")
                appendWrapped(
                    "This subsection estimates Classic fox arrival phases on the imported route and checks whether assigning different fox numbers to the same locations could reduce waiting. If a competitor reaches a fox while it is off the air, timing waits for that fox to transmit, then adds 30 seconds to find and punch before departure."
                )
                appendWaitRows("Current wait times", section.waitRows)
                section.waitRenumbering?.let { appendWaitRenumbering(it) }
            } else {
                appendWaitRows("Optimized wait times", section.waitRows)
            }
        }
        appendSectionSummaryRows(summaryGroup)
        appendMetricGroup(metricGroup)
    }

    private fun StringBuilder.appendSummary(result: DesktopCourseAnalysisSummary) {
        appendLine("Section 3: Summary")
        appendWrapped(result.summaryExplanation)
        if (result.goodnessMetrics.sharedMetrics.isNotEmpty()) {
            appendLine()
            appendLine(PdfDividerLine)
            appendLine("Goodness metrics")
            result.goodnessMetrics.sharedMetrics.forEach { metric ->
                appendLine("${metric.label}: ${metric.value} (${metric.status.name})")
            }
            appendLine()
        }
        appendLine(PdfDividerLine)
        appendLine("Course Recommendation")
        appendWrapped(result.courseRecommendation.paragraph)
        appendLine()
        appendLine("Speed model factors")
        appendWrapped(speedFactorExplanation(result.speedModel))
        result.categorySpeedFactors.forEach { factor ->
            appendLine("${factor.categoryCodes.joinToString("/")}: x${twoDecimalText(factor.multiplier)}")
        }
        appendLine("Unmatched categories: x1.00")
        appendLine()
        appendLine("Elevation profiles")
        result.elevationCacheNotes.forEach { note ->
            appendLine(note)
        }
        if (result.profileComparison.isEmpty() || result.profileComparison.all { it.profile.isEmpty() }) {
            appendLine("No elevation profiles available because local elevation data is incomplete.")
        } else {
            result.profileComparison.forEach { profile ->
                val first = profile.profile.firstOrNull()
                val last = profile.profile.lastOrNull()
                if (first == null || last == null) {
                    appendLine("${profile.title}: unavailable")
                } else {
                    appendLine(
                        "${profile.title}: ${kilometersText(first.distanceMeters)} to ${kilometersText(last.distanceMeters)}, " +
                            "${first.elevationMeters.roundToInt()} m to ${profile.profile.maxOf { it.elevationMeters }.roundToInt()} m"
                    )
                    if (profile.markers.isNotEmpty()) {
                        appendLine(
                            "  Fox markers: " + profile.markers.joinToString("; ") { marker ->
                                "${marker.label} at ${kilometersText(marker.distanceMeters)}, ${marker.elevationMeters.roundToInt()} m"
                            }
                        )
                    }
                }
            }
        }
        appendLine()
        appendLine("2D route depictions")
        if (result.routeMaps.isEmpty()) {
            appendLine("No route depictions available.")
        } else {
            result.routeMaps.forEach { routeMap ->
                appendLine("${routeMap.title}: ${routeMap.routeLabels.joinToString(" -> ")}")
                routeMap.points.forEach { point ->
                    appendLine("  ${point.label}: x=${twoDecimalText(point.xFraction)}, y=${twoDecimalText(point.yFraction)}, ${point.type}")
                }
            }
        }
    }

    private fun StringBuilder.appendSectionSummaryRows(group: DesktopCourseAnalysisSummaryGroup?) {
        val rows = group?.rows
            .orEmpty()
            .filterNot { it.label in CourseAnalysisSectionDuplicateSummaryLabels }
        if (rows.isEmpty()) {
            return
        }
        appendLine()
        appendLine("Section summary")
        rows.forEach { row ->
            appendLine("${row.label}: ${row.value}")
        }
    }

    private fun StringBuilder.appendMetricGroup(group: DesktopCourseGoodnessMetricGroup?) {
        if (group == null || group.metrics.isEmpty()) {
            return
        }
        appendLine()
        appendLine("${group.title} checks and metrics")
        group.metrics.forEach { metric ->
            val prefix = if (metric.isRuleViolationMetric()) "RULE VIOLATION: " else ""
            appendLine("$prefix${metric.label}: ${metric.value} (${metric.status.name})")
        }
    }

    private fun DesktopCourseGoodnessMetric.isRuleViolationMetric(): Boolean =
        status == DesktopCourseMetricStatus.Warning &&
            (
                label.endsWith("fox count") ||
                    label.endsWith("course length") ||
                    label.endsWith("Sprint target time") ||
                    label.contains("minimum transmitter spacing") ||
                    label.contains("start exclusion zone") ||
                    label.contains("USA category name")
                )

    private fun speedModelText(speedModel: DesktopCourseSpeedModel): String =
        "${twoDecimalText(speedModel.effectiveSpeedMetersPerSecond)} m/s; " +
            "${speedModel.categoryModelLabel} x${twoDecimalText(speedModel.categorySpeedMultiplier)}, " +
            "event x${twoDecimalText(speedModel.compensationFactor)}"

    private fun speedFactorExplanation(speedModel: DesktopCourseSpeedModel): String =
        "Assumed running speed equals race-format baseline speed x category multiplier x event speed factor. " +
            "${speedModel.categoryFactorSourceLabel}: ${speedModel.categoryFactorExplanation} " +
            "The event speed factor is adjustable, saved in the Event File, and applies to every category; the current event factor is x${twoDecimalText(speedModel.compensationFactor)}."

    private fun kmlText(result: DesktopCourseAnalysisSummary, kmlFileStem: String): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${xmlText(kmlFileStem)}</name>")
            appendLine("    <Style id=\"storedRouteStyle\"><LineStyle><color>ff0057b8</color><width>4</width></LineStyle></Style>")
            appendLine("    <Style id=\"calculatedRouteStyle\"><LineStyle><color>ff00a676</color><width>4</width></LineStyle></Style>")
            appendCoursePointStyle(
                styleId = DesktopCourseKmlStyle.DonutStyleId,
                iconUrl = DesktopCourseKmlStyle.DonutIconUrl
            )
            appendCoursePointStyle(
                styleId = DesktopCourseKmlStyle.StartStyleId,
                iconUrl = DesktopCourseKmlStyle.StartIconUrl
            )
            appendCoursePointStyle(
                styleId = DesktopCourseKmlStyle.FinishStyleId,
                iconUrl = DesktopCourseKmlStyle.FinishIconUrl
            )
            result.kmlFolders.forEach { folder ->
                val routeStyleId = if (folder.title.startsWith("Imported")) {
                    "storedRouteStyle"
                } else {
                    "calculatedRouteStyle"
                }
                appendKmlFolder(folder, routeStyleId, kmlFileStem)
            }
            appendLine("  </Document>")
            appendLine("</kml>")
        }

    private fun StringBuilder.appendKmlFolder(
        folder: DesktopCourseKmlExportFolder,
        routeStyleId: String,
        routeName: String
    ) {
        appendLine("    <Folder>")
        appendLine("      <name>${xmlText(folder.title)}</name>")
        appendLine("      <open>1</open>")
        val routeCoordinates = kmlRouteCoordinates(folder)
        if (routeCoordinates.size >= 2) {
            appendLine("      <Placemark>")
            appendLine("        <name>${xmlText(routeName)}</name>")
            appendLine("        <styleUrl>#$routeStyleId</styleUrl>")
            appendLine("        <LineString>")
            appendLine("          <tessellate>1</tessellate>")
            appendLine("          <coordinates>")
            routeCoordinates.forEach { point ->
                appendLine("            ${kmlCoordinate(point)}")
            }
            appendLine("          </coordinates>")
            appendLine("        </LineString>")
            appendLine("      </Placemark>")
        }
        folder.courseObjects.forEach { courseObject ->
            appendLine("      <Placemark>")
            appendLine("        <name>${xmlText(courseObject.label)}</name>")
            courseObject.originalLabel?.let {
                appendLine("        <description>${xmlText("Original label: $it")}</description>")
            }
            appendLine("        <styleUrl>#${courseObjectStyleId(courseObject.type)}</styleUrl>")
            appendLine("        <Point>")
            appendLine("          <coordinates>${kmlCoordinate(courseObject.point)}</coordinates>")
            appendLine("        </Point>")
            appendLine("      </Placemark>")
        }
        appendLine("    </Folder>")
    }

    private fun StringBuilder.appendCoursePointStyle(styleId: String, iconUrl: String) {
        appendLine("    <Style id=\"$styleId\">")
        appendLine("      <IconStyle>")
        appendLine("        <scale>${DesktopCourseKmlStyle.MarkerScale}</scale>")
        appendLine("        <color>${DesktopCourseKmlStyle.MarkerColor}</color>")
        appendLine("        <Icon><href>$iconUrl</href></Icon>")
        appendLine("      </IconStyle>")
        appendLine("    </Style>")
    }

    private fun courseObjectStyleId(type: DesktopCourseKmlExportPointType): String =
        when (type) {
            DesktopCourseKmlExportPointType.START -> DesktopCourseKmlStyle.StartStyleId
            DesktopCourseKmlExportPointType.FINISH -> DesktopCourseKmlStyle.FinishStyleId
            DesktopCourseKmlExportPointType.CONTROL,
            DesktopCourseKmlExportPointType.BEACON,
            DesktopCourseKmlExportPointType.SPECTATOR -> DesktopCourseKmlStyle.DonutStyleId
        }

    private fun kmlRouteCoordinates(folder: DesktopCourseKmlExportFolder): List<CourseGeoPoint> =
        folder.routeStops
            .takeIf { it.size >= 2 }
            ?.map { it.point }
            ?: folder.routePoints

    private fun kmlCoordinate(point: CourseGeoPoint): String =
        if (point.elevationMeters != null) {
            String.format(Locale.US, "%.8f,%.8f,%.2f", point.longitude, point.latitude, point.elevationMeters)
        } else {
            String.format(Locale.US, "%.8f,%.8f", point.longitude, point.latitude)
        }

    private fun xmlText(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun kmlPathForPdf(path: Path, result: DesktopCourseAnalysisSummary): Path =
        path.resolveSibling("${courseAnalysisFileStem(result)}.kml")

    private fun kmlFileStem(path: Path): String {
        val fileName = path.fileName?.toString().orEmpty()
        return if (fileName.endsWith(".kml", ignoreCase = true)) {
            fileName.dropLast(4)
        } else {
            fileName.substringBeforeLast('.', fileName)
        }
    }

    private fun courseAnalysisFileStem(result: DesktopCourseAnalysisSummary): String {
        val format = fileNamePart(result.eventFormatLabel.ifBlank { "Course Analysis" })
        val foxCount = result.assignedFoxCount
        val foxes = "$foxCount ${if (foxCount == 1) "Fox" else "Foxes"}"
        val length = calculatedIdealRouteLengthMeters(result)
            ?.let { String.format(Locale.US, "%.2f km", it / 1000.0) }
            ?: "unknown length"
        val categories = result.sameCourseCategoryNames
            .ifEmpty { listOf(result.categoryName) }
            .joinToString(",")
            .let(::fileNamePart)
        return listOf(format, foxes, length, categories)
            .joinToString(" - ")
    }

    private fun calculatedIdealRouteLengthMeters(result: DesktopCourseAnalysisSummary): Int? =
        result.calculatedRouteSection?.effectiveLengthMeters
            ?: result.calculatedRouteSection?.routeLengthMeters

    private fun fileNamePart(text: String): String =
        text
            .trim()
            .map { character ->
                if (character.isISOControl() || character in """\/:*?"<>|""") ' ' else character
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown" }

    private fun StringBuilder.appendTimingBreakdown(legs: List<DesktopCourseLegRow>, estimatedIdealSeconds: Int?) {
        val totalSeconds = estimatedIdealSeconds ?: return
        val waitSeconds = legs.sumOf { it.waitSeconds ?: 0 }
        val findPunchSeconds = legs.sumOf { it.findPunchSeconds ?: 0 }
        if (waitSeconds == 0 && findPunchSeconds == 0) {
            return
        }
        val movementSeconds = (totalSeconds - waitSeconds - findPunchSeconds).coerceAtLeast(0)
        appendLine("Movement time: ${secondsText(movementSeconds)}")
        appendLine("Fox wait time: ${secondsText(waitSeconds)}")
        appendLine("Find/punch allowance: ${secondsText(findPunchSeconds)}")
    }

    private fun StringBuilder.appendLegRows(legs: List<DesktopCourseLegRow>) {
        appendLine()
        appendLine("Leg analysis")
        if (legs.isEmpty()) {
            appendLine("No leg rows available.")
            return
        }
        legs.forEach { leg ->
            val waitText = leg.waitSeconds?.let { " (waits ${secondsText(it)})" }.orEmpty()
            appendLine(
                "${leg.fromLabel} -> ${leg.toLabel}: ${kilometersText(leg.lengthMeters)}  " +
                    "split ${secondsText(leg.splitSeconds)}  cumulative ${secondsText(leg.cumulativeSeconds)}$waitText"
            )
        }
    }

    private fun StringBuilder.appendWaitRows(title: String, waitRows: List<DesktopCourseWaitRow>) {
        appendLine()
        appendLine(title)
        if (waitRows.isEmpty()) {
            appendLine("No wait-time rows available.")
            return
        }
        waitRows.forEach { row ->
            appendLine(
                "${row.controlLabel}: arrival ${secondsText(row.arrivalSeconds)}, " +
                    listOfNotNull(row.slotLabel?.let { "fox $it" }, "wait ${secondsText(row.waitSeconds)}").joinToString(", ")
            )
        }
    }

    private fun StringBuilder.appendWaitRenumbering(renumbering: DesktopCourseWaitRenumbering) {
        appendLine()
        appendLine("Wait-time renumbering check")
        appendLine("Current / best wait: ${secondsText(renumbering.currentTotalWaitSeconds)} / ${secondsText(renumbering.bestTotalWaitSeconds)}")
        val improvement = (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds).coerceAtLeast(0)
        appendLine("Likely improvement: ${secondsText(improvement)}")
        if (renumbering.improvesWait) {
            renumbering.assignments.forEach { assignment ->
                appendLine("${assignment.controlLabel}: ${assignment.currentSlotLabel} -> ${assignment.suggestedSlotLabel}")
            }
            appendWaitRows("Renumbered wait times", renumbering.suggestedWaitRows)
        } else {
            appendLine("Current fox numbering is already best for ideal-route wait time.")
        }
    }

    private fun StringBuilder.appendWrapped(text: String, width: Int = 100) {
        text.split(Regex("\\s+")).fold(StringBuilder()) { line, word ->
            if (line.isEmpty()) {
                line.append(word)
            } else if (line.length + 1 + word.length <= width) {
                line.append(' ').append(word)
            } else {
                appendLine(line.toString())
                line.clear().append(word)
            }
            line
        }.takeIf { it.isNotEmpty() }?.let { appendLine(it.toString()) }
    }

    private fun pdfBytes(result: DesktopCourseAnalysisSummary): ByteArray {
        val wrappedLines = styledReportLines(result)
            .flatMap { line ->
                wrapPdfLine(line.text, line.style.wrapWidth).map { wrappedLine ->
                    line.copy(text = wrappedLine)
                }
            }
        val pageContents = paginatePdfLines(wrappedLines)
            .ifEmpty { listOf(listOf(PdfTextLine("", PdfTextStyle.Body))) }
            .map(::textPageContent) + graphicsPageContents(result)
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids ${pageContents.indices.joinToString(separator = " ", prefix = "[", postfix = "]") { "${4 + it * 2} 0 R" }} /Count ${pageContents.size} >>"
        objects += "<< /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> /F2 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> >>"
        pageContents.forEachIndexed { index, content ->
            val pageObjectId = 4 + index * 2
            val contentObjectId = pageObjectId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font 3 0 R >> /Contents $contentObjectId 0 R >>"
            val length = content.toByteArray(StandardCharsets.ISO_8859_1).size
            objects += "<< /Length $length >>\nstream\n$content\nendstream"
        }

        val output = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, obj ->
            offsets += output.toString().toByteArray(StandardCharsets.ISO_8859_1).size
            output.append("${index + 1} 0 obj\n")
            output.append(obj)
            output.append("\nendobj\n")
        }
        val xrefOffset = output.toString().toByteArray(StandardCharsets.ISO_8859_1).size
        output.append("xref\n")
        output.append("0 ${objects.size + 1}\n")
        output.append("0000000000 65535 f \n")
        offsets.forEach { output.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        output.append("trailer\n")
        output.append("<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        output.append("startxref\n")
        output.append(xrefOffset)
        output.append("\n%%EOF\n")
        return output.toString().toByteArray(StandardCharsets.ISO_8859_1)
    }

    private data class PdfTextLine(
        val text: String,
        val style: PdfTextStyle
    )

    private enum class PdfTextStyle(
        val fontName: String,
        val fontSize: Int,
        val lineHeight: Double,
        val wrapWidth: Int
    ) {
        Title("/F2", 18, 24.0, 62),
        SectionHeading("/F2", 14, 19.0, 78),
        Subheading("/F2", 11, 15.0, 92),
        Body("/F1", 10, 13.0, 100)
    }

    private fun styledReportLines(result: DesktopCourseAnalysisSummary): List<PdfTextLine> =
        reportText(result)
            .lineSequence()
            .mapIndexed { index, line ->
                PdfTextLine(
                    text = line,
                    style = when {
                        index == 0 -> PdfTextStyle.Title
                        line == PdfDividerLine -> PdfTextStyle.Body
                        line.startsWith("Section ") || line == "Partial analysis" -> PdfTextStyle.SectionHeading
                        line in PdfSubheadingLabels -> PdfTextStyle.Subheading
                        else -> PdfTextStyle.Body
                    }
                )
            }
            .toList()

    private val PdfSubheadingLabels = setOf(
        "USA rules checks",
        "Imported-route wait-time analysis",
        "Current wait times",
        "Optimized wait times",
        "Wait-time renumbering check",
        "Renumbered wait times",
        "Section summary",
        "Imported checks and metrics",
        "Calculated checks and metrics",
        "Course Recommendation",
        "Speed model factors",
        "Goodness metrics",
        "Elevation profiles",
        "2D route depictions",
        "Leg analysis"
    )

    private val CourseAnalysisSectionDuplicateSummaryLabels = setOf(
        "Imported route",
        "Calculated route",
        "Ideal route",
        "Result",
        "Horizontal length",
        "Climb",
        "Effective length",
        "Estimated ideal time"
    )

    private fun paginatePdfLines(lines: List<PdfTextLine>): List<List<PdfTextLine>> {
        val pages = mutableListOf<List<PdfTextLine>>()
        val currentPage = mutableListOf<PdfTextLine>()
        var usedHeight = 0.0
        val maxHeight = 696.0
        lines.forEach { line ->
            val lineHeight = if (line.text.isBlank()) 8.0 else line.style.lineHeight
            if (currentPage.isNotEmpty() && usedHeight + lineHeight > maxHeight) {
                pages += currentPage.toList()
                currentPage.clear()
                usedHeight = 0.0
            }
            currentPage += line
            usedHeight += lineHeight
        }
        if (currentPage.isNotEmpty()) {
            pages += currentPage.toList()
        }
        return pages
    }

    private fun wrapPdfLine(line: String, width: Int): List<String> {
        if (line.length <= width) {
            return listOf(line)
        }
        val lines = mutableListOf<String>()
        var remaining = line
        while (remaining.length > width) {
            val splitIndex = remaining.take(width + 1).lastIndexOf(' ').takeIf { it > 0 } ?: width
            lines += remaining.take(splitIndex).trimEnd()
            remaining = remaining.drop(splitIndex).trimStart()
        }
        lines += remaining
        return lines
    }

    private fun textPageContent(lines: List<PdfTextLine>): String =
        buildString {
            var y = 750.0
            lines.forEach { line ->
                if (line.text.isBlank()) {
                    y -= 8.0
                    return@forEach
                }
                if (line.text == PdfDividerLine) {
                    appendLine("0.75 0.75 0.75 RG")
                    appendLine("0.5 w")
                    appendLine("54 ${pdfNumber(y - 3.0)} m 558 ${pdfNumber(y - 3.0)} l S")
                    y -= line.style.lineHeight
                    return@forEach
                }
                appendLine("BT")
                appendLine("${line.style.fontName} ${line.style.fontSize} Tf")
                if (line.text.startsWith("RULE VIOLATION:")) {
                    appendLine("0.78 0.10 0.10 rg")
                } else {
                    appendLine("0 0 0 rg")
                }
                appendLine("1 0 0 1 54 ${pdfNumber(y)} Tm")
                appendLine("(${line.text.toPdfText()}) Tj")
                appendLine("ET")
                y -= line.style.lineHeight
            }
        }

    private fun graphicsPageContents(result: DesktopCourseAnalysisSummary): List<String> =
        buildList {
            if (result.profileComparison.any { it.profile.isNotEmpty() }) {
                add(elevationProfilesPageContent(result.profileComparison, result.elevationCacheNotes))
            }
            if (result.routeMaps.isNotEmpty()) {
                add(routeMapsPageContent(result.routeMaps))
            }
        }

    private fun elevationProfilesPageContent(
        profiles: List<DesktopCourseElevationProfileSummary>,
        elevationCacheNotes: List<String>
    ): String =
        buildString {
            appendText(54.0, 750.0, 16, "Elevation Profile Graphics")
            elevationCacheNotes.take(3).forEachIndexed { index, note ->
                appendText(54.0, 728.0 - index * 12.0, 9, note)
            }
            val firstTop = 690.0 - elevationCacheNotes.take(3).size * 12.0
            profiles
                .filter { it.profile.isNotEmpty() }
                .take(3)
                .forEachIndexed { index, profile ->
                    val top = firstTop - index * 205.0
                    appendText(54.0, top + 18.0, 12, profile.title)
                    appendElevationProfile(profile, 54.0, top - 150.0, 500.0, 145.0)
                }
        }

    private fun StringBuilder.appendElevationProfile(
        summary: DesktopCourseElevationProfileSummary,
        left: Double,
        bottom: Double,
        width: Double,
        height: Double
    ) {
        val profile = summary.profile
        val minElevation = profile.minOf { it.elevationMeters }
        val maxElevation = profile.maxOf { it.elevationMeters }
        val totalDistance = max(1.0, profile.lastOrNull()?.distanceMeters?.toDouble() ?: 1.0)
        val elevationRange = max(1.0, maxElevation - minElevation)
        fun x(distanceMeters: Int): Double = left + distanceMeters / totalDistance * width
        fun y(elevationMeters: Double): Double = bottom + (elevationMeters - minElevation) / elevationRange * height

        appendLine("0.85 0.85 0.85 RG")
        repeat(4) { index ->
            val gridY = bottom + height * index / 3.0
            appendLine("${pdfNumber(left)} ${pdfNumber(gridY)} m ${pdfNumber(left + width)} ${pdfNumber(gridY)} l S")
        }
        appendLine("0.25 0.25 0.25 RG")
        appendLine("${pdfNumber(left)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re S")
        appendLine("0.00 0.35 0.72 RG")
        appendLine("2.4 w")
        profile.zipWithNext().forEach { (start, end) ->
            appendLine(
                "${pdfNumber(x(start.distanceMeters))} ${pdfNumber(y(start.elevationMeters))} m " +
                    "${pdfNumber(x(end.distanceMeters))} ${pdfNumber(y(end.elevationMeters))} l S"
            )
        }
        appendLine("1.00 0.54 0.00 rg")
        summary.markers.forEach { marker ->
            appendLine("1.00 0.54 0.00 rg")
            appendCircle(x(marker.distanceMeters), y(marker.elevationMeters), 3.8, fill = true)
            appendText(x(marker.distanceMeters) + 5.0, y(marker.elevationMeters) + 4.0, 8, marker.label)
        }
        appendText(
            left,
            bottom - 16.0,
            8,
            "0.00 km to ${twoDecimalText(totalDistance / 1000.0)} km, ${minElevation.roundToInt()} m to ${maxElevation.roundToInt()} m"
        )
    }

    private fun routeMapsPageContent(routeMaps: List<DesktopCourseRouteMap>): String =
        buildString {
            appendText(54.0, 750.0, 16, "2D Route Depiction Graphics")
            routeMaps
                .take(4)
                .forEachIndexed { index, routeMap ->
                    val column = index % 2
                    val row = index / 2
                    val left = 54.0 + column * 270.0
                    val bottom = 405.0 - row * 255.0
                    appendText(left, bottom + 205.0, 12, routeMap.title)
                    appendRouteMap(routeMap, left, bottom, 225.0, 185.0)
                }
        }

    private fun StringBuilder.appendRouteMap(
        routeMap: DesktopCourseRouteMap,
        left: Double,
        bottom: Double,
        width: Double,
        height: Double
    ) {
        fun x(point: DesktopCourseRouteMapPoint): Double = left + point.xFraction.coerceIn(0.0, 1.0) * width
        fun y(point: DesktopCourseRouteMapPoint): Double = bottom + (1.0 - point.yFraction.coerceIn(0.0, 1.0)) * height
        appendLine("0.25 0.25 0.25 RG")
        appendLine("${pdfNumber(left)} ${pdfNumber(bottom)} ${pdfNumber(width)} ${pdfNumber(height)} re S")
        val byLabel = routeMap.points.associateBy { it.label }
        appendLine("0.00 0.35 0.72 RG")
        appendLine("2 w")
        routeMap.routeLabels.zipWithNext().forEach { (fromLabel, toLabel) ->
            val from = byLabel[fromLabel] ?: return@forEach
            val to = byLabel[toLabel] ?: return@forEach
            appendLine("${pdfNumber(x(from))} ${pdfNumber(y(from))} m ${pdfNumber(x(to))} ${pdfNumber(y(to))} l S")
        }
        routeMap.points.forEach { point ->
            val (red, green, blue) = routeMapPointRgb(point.type)
            appendLine("${pdfNumber(red)} ${pdfNumber(green)} ${pdfNumber(blue)} rg")
            appendCircle(x(point), y(point), 4.0, fill = true)
            appendText(x(point) + 5.0, y(point) + 5.0, 8, point.label)
        }
    }

    private fun routeMapPointRgb(type: DesktopCourseRouteMapPointType): Triple<Double, Double, Double> =
        when (type) {
            DesktopCourseRouteMapPointType.Start -> Triple(0.06, 0.55, 0.22)
            DesktopCourseRouteMapPointType.Finish -> Triple(0.78, 0.10, 0.10)
            DesktopCourseRouteMapPointType.Control -> Triple(0.00, 0.35, 0.72)
            DesktopCourseRouteMapPointType.Beacon -> Triple(1.00, 0.54, 0.00)
            DesktopCourseRouteMapPointType.Spectator -> Triple(0.38, 0.38, 0.38)
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
        appendLine("(${text.toPdfText()}) Tj")
        appendLine("ET")
    }

    private fun String.toPdfText(): String =
        map { character ->
            when (character) {
                '\\' -> "\\\\"
                '(' -> "\\("
                ')' -> "\\)"
                in ' '..'~' -> character.toString()
                else -> "?"
            }
        }.joinToString("")

    private fun kilometersText(value: Int?): String =
        value?.let { "${twoDecimalText(it / 1000.0)} km" } ?: "Unknown"

    private fun climbText(value: Int?): String =
        value?.let { "$it m" } ?: "Unknown"

    private fun secondsText(value: Int?): String =
        value?.let(::compactSecondsText) ?: "Unknown"

    private fun pdfNumber(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun compactSecondsText(value: Int): String {
        val sign = if (value < 0) "-" else ""
        val absoluteSeconds = abs(value)
        val hours = absoluteSeconds / 3600
        val minutes = (absoluteSeconds % 3600) / 60
        val seconds = absoluteSeconds % 60
        return if (hours == 0) {
            "$sign$minutes:${seconds.toString().padStart(2, '0')}"
        } else {
            "$sign$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    private fun twoDecimalText(value: Double): String =
        (value * 100.0).roundToInt().let { "${it / 100}.${(abs(it % 100)).toString().padStart(2, '0')}" }
}

data class DesktopCourseAnalysisExportPaths(
    val pdfPath: Path,
    val kmlPath: Path
)
