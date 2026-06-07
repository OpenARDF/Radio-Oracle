package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain-text and PDF exports for the desktop Course Analyzer report. */
object DesktopCourseAnalysisExports {
    fun exportPdfAndKml(path: Path, result: DesktopCourseAnalysisSummary): DesktopCourseAnalysisExportPaths {
        exportPdf(path, result)
        val kmlPath = kmlPathForPdf(path)
        exportKml(kmlPath, result)
        return DesktopCourseAnalysisExportPaths(pdfPath = path, kmlPath = kmlPath)
    }

    fun exportPdf(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(reportText(result)))
    }

    fun exportKml(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, kmlText(result), StandardCharsets.UTF_8)
    }

    fun reportText(result: DesktopCourseAnalysisSummary): String =
        buildString {
            appendLine("Course Analyzer")
            appendLine("Category: ${result.categoryName}")
            appendLine()
            result.providedRouteSection?.let { section ->
                appendSection(section, includeRenumbering = true)
                appendLine()
            }
            result.calculatedRouteSection?.let { section ->
                appendSection(section, includeRenumbering = false)
                appendLine()
            }
            appendSummary(result)
            if (result.missingElements.isNotEmpty()) {
                appendLine()
                appendLine("Partial analysis")
                result.missingElements.forEach { appendLine("- $it") }
            }
        }.trimEnd() + "\n"

    private fun StringBuilder.appendSection(section: DesktopCourseAnalysisSection, includeRenumbering: Boolean) {
        appendLine(section.title)
        appendWrapped(section.explanation)
        appendLine("Route order: ${section.routeOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        appendLine("${section.comparisonLengthLabel}: ${kilometersText(section.comparisonLengthMeters)}")
        appendLine("Horizontal length: ${kilometersText(section.straightLineMeters)}")
        appendLine("Route length: ${kilometersText(section.routeLengthMeters)}")
        appendLine("Climb: ${climbText(section.climbMeters)}")
        appendLine("Effective length: ${kilometersText(section.effectiveLengthMeters)}")
        appendLine("Estimated ideal time: ${secondsText(section.estimatedIdealSeconds)}")
        appendTimingBreakdown(section.legRows, section.estimatedIdealSeconds)
        appendLegRows(section.legRows)
        if (includeRenumbering) {
            appendLine()
            appendLine("Provided-route wait-time analysis")
            appendWrapped(
                "This subsection estimates Classic fox arrival phases on the provided route and checks whether assigning different fox numbers to the same locations could reduce waiting. If a competitor reaches a fox while it is off the air, timing waits for that fox to transmit, then adds 30 seconds to find and punch before departure."
            )
            appendWaitRows("Current wait times", section.waitRows)
            section.waitRenumbering?.let { appendWaitRenumbering(it) }
        } else {
            appendWaitRows("Optimized wait times", section.waitRows)
        }
    }

    private fun StringBuilder.appendSummary(result: DesktopCourseAnalysisSummary) {
        appendLine("Section 3: Summary")
        appendWrapped(result.summaryExplanation)
        appendLine("Routes compared: ${result.calculatedRouteCount}")
        appendLine("Calculated ideal route: ${result.calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        appendLine("Provided ideal route: ${result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        appendLine(
            "Order comparison: " + when (result.idealOrderMatches) {
                true -> "Agrees"
                false -> "Differs"
                null -> "Unknown"
            }
        )
        appendLine("Calculated straight-line length: ${kilometersText(result.calculatedStraightLineMeters)}")
        appendLine("Provided straight-line length: ${kilometersText(result.providedStraightLineMeters)}")
        appendLine("Provided route length: ${kilometersText(result.routeLengthMeters)}")
        appendLine("Climb: ${climbText(result.climbMeters)}")
        appendLine("Effective length: ${kilometersText(result.effectiveLengthMeters)}")
        appendLine("Estimated ideal time: ${secondsText(result.estimatedIdealSeconds)}")
        appendLine()
        appendLine("Goodness metrics")
        result.metrics.forEach { metric ->
            appendLine("${metric.label}: ${metric.value} (${metric.status.name})")
        }
        appendLine()
        appendLine("Elevation profiles")
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

    private fun kmlText(result: DesktopCourseAnalysisSummary): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${xmlText("Course Analyzer - ${result.categoryName}")}</name>")
            appendLine("    <Style id=\"providedRouteStyle\"><LineStyle><color>ff0057b8</color><width>4</width></LineStyle></Style>")
            appendLine("    <Style id=\"calculatedRouteStyle\"><LineStyle><color>ff00a676</color><width>4</width></LineStyle></Style>")
            appendLine("    <Style id=\"foxStyle\"><IconStyle><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon></IconStyle></Style>")
            result.kmlFolders.forEach { folder ->
                val routeStyleId = if (folder.title.startsWith("Provided")) {
                    "providedRouteStyle"
                } else {
                    "calculatedRouteStyle"
                }
                appendKmlFolder(folder, routeStyleId)
            }
            appendLine("  </Document>")
            appendLine("</kml>")
        }

    private fun StringBuilder.appendKmlFolder(folder: DesktopCourseKmlExportFolder, routeStyleId: String) {
        appendLine("    <Folder>")
        appendLine("      <name>${xmlText(folder.title)}</name>")
        appendLine("      <open>1</open>")
        if (folder.routePoints.size >= 2) {
            appendLine("      <Placemark>")
            appendLine("        <name>${xmlText(folder.routeName)}</name>")
            appendLine("        <styleUrl>#$routeStyleId</styleUrl>")
            appendLine("        <LineString>")
            appendLine("          <tessellate>1</tessellate>")
            appendLine("          <coordinates>")
            folder.routePoints.forEach { point ->
                appendLine("            ${kmlCoordinate(point)}")
            }
            appendLine("          </coordinates>")
            appendLine("        </LineString>")
            appendLine("      </Placemark>")
        }
        folder.foxes.forEach { fox ->
            appendLine("      <Placemark>")
            appendLine("        <name>${xmlText(fox.label)}</name>")
            fox.originalLabel?.let {
                appendLine("        <description>${xmlText("Original label: $it")}</description>")
            }
            appendLine("        <styleUrl>#foxStyle</styleUrl>")
            appendLine("        <Point>")
            appendLine("          <coordinates>${kmlCoordinate(fox.point)}</coordinates>")
            appendLine("        </Point>")
            appendLine("      </Placemark>")
        }
        appendLine("    </Folder>")
    }

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

    private fun kmlPathForPdf(path: Path): Path {
        val fileName = path.fileName.toString()
        val stem = if (fileName.lowercase().endsWith(DesktopProjectFilePaths.PDF_EXTENSION)) {
            fileName.dropLast(DesktopProjectFilePaths.PDF_EXTENSION.length)
        } else {
            fileName
        }
        return path.resolveSibling("$stem.kml")
    }

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

    private fun pdfBytes(text: String): ByteArray {
        val wrappedLines = text
            .lineSequence()
            .flatMap { line -> wrapPdfLine(line, 100).asSequence() }
            .toList()
        val pages = wrappedLines.chunked(52).ifEmpty { listOf(listOf("")) }
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids ${pages.indices.joinToString(separator = " ", prefix = "[", postfix = "]") { "${4 + it * 2} 0 R" }} /Count ${pages.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        pages.forEachIndexed { index, pageLines ->
            val pageObjectId = 4 + index * 2
            val contentObjectId = pageObjectId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjectId 0 R >>"
            val content = pageContent(pageLines)
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

    private fun pageContent(lines: List<String>): String =
        buildString {
            appendLine("BT")
            appendLine("/F1 10 Tf")
            appendLine("54 750 Td")
            appendLine("13 TL")
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    appendLine("T*")
                }
                appendLine("(${line.toPdfText()}) Tj")
            }
            append("ET")
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
