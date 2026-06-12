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
    fun exportPdfAndKml(path: Path, result: DesktopCourseAnalysisSummary): DesktopCourseAnalysisExportPaths {
        exportPdf(path, result)
        val kmlPath = kmlPathForPdf(path)
        exportKml(kmlPath, result)
        return DesktopCourseAnalysisExportPaths(pdfPath = path, kmlPath = kmlPath)
    }

    fun exportPdf(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(result))
    }

    fun exportKml(path: Path, result: DesktopCourseAnalysisSummary) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, kmlText(result), StandardCharsets.UTF_8)
    }

    fun reportText(result: DesktopCourseAnalysisSummary): String =
        buildString {
            appendLine("Course Analyzer")
            appendLine("Category: ${result.categoryName}")
            appendLine("Rules applied: ${result.rulesDocumentLabel}")
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
        appendLine("${section.routeOrderLabel}: ${section.routeOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        appendRuleChecks(section.ruleChecks)
        if (section.summaryOnly) {
            return
        }
        section.secondaryRouteOrderLabel?.let { label ->
            appendLine("$label: ${section.secondaryRouteOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
        }
        appendLine("${section.comparisonLengthLabel}: ${sectionComparisonLengthText(section)}")
        appendLine("Horizontal length: ${kilometersText(section.straightLineMeters)}")
        appendLine("Route length: ${kilometersText(section.routeLengthMeters)}")
        appendLine("Climb: ${climbText(section.climbMeters)}")
        if (section.comparisonLengthLabel != "Effective length") {
            appendLine("Effective length: ${kilometersText(section.effectiveLengthMeters)}")
        }
        appendLine("Estimated ideal time: ${secondsText(section.estimatedIdealSeconds)}")
        appendTimingBreakdown(section.legRows, section.estimatedIdealSeconds)
        appendLegRows(section.legRows)
        if (includeRenumbering) {
            appendLine()
            appendLine("Stored-route wait-time analysis")
            appendWrapped(
                "This subsection estimates Classic fox arrival phases on the stored route and checks whether assigning different fox numbers to the same locations could reduce waiting. If a competitor reaches a fox while it is off the air, timing waits for that fox to transmit, then adds 30 seconds to find and punch before departure."
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
        if (result.idealOrderMatches == true) {
            appendLine("Stored ideal route: ${result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
            appendLine("Order comparison: Stored and calculated routes match")
        } else {
            appendLine("Calculated ideal route (calculated fox numbering): ${result.calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
            appendLine("Stored ideal route: ${result.providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }}")
            appendLine(
                "Order comparison: " + when (result.idealOrderMatches) {
                    false -> "Differs"
                    null -> "Unknown"
                    true -> "Agrees"
                }
            )
            appendLine("Calculated straight-line length: ${kilometersText(result.calculatedStraightLineMeters)}")
        }
        appendLine("Stored straight-line length: ${kilometersText(result.providedStraightLineMeters)}")
        appendLine("Stored route length: ${kilometersText(result.routeLengthMeters)}")
        appendLine("Climb: ${climbText(result.climbMeters)}")
        appendLine("Effective length: ${summaryMetricValue(result, "Effective length", kilometersText(result.effectiveLengthMeters))}")
        appendLine("Speed model: ${speedModelText(result.speedModel)}")
        appendLine("Estimated ideal time: ${secondsText(result.estimatedIdealSeconds)}")
        appendLine()
        appendLine("Goodness metrics")
        result.metrics.forEach { metric ->
            appendLine("${metric.label}: ${metric.value} (${metric.status.name})")
        }
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

    private fun summaryMetricValue(
        result: DesktopCourseAnalysisSummary,
        label: String,
        fallback: String
    ): String =
        result.metrics.firstOrNull { it.label == label }?.value ?: fallback

    private fun speedModelText(speedModel: DesktopCourseSpeedModel): String =
        "${twoDecimalText(speedModel.effectiveSpeedMetersPerSecond)} m/s; " +
            "${speedModel.categoryModelLabel} x${twoDecimalText(speedModel.categorySpeedMultiplier)}, " +
            "event x${twoDecimalText(speedModel.compensationFactor)}"

    private fun sectionComparisonLengthText(section: DesktopCourseAnalysisSection): String {
        val ruleValue = section.ruleChecks
            .firstOrNull { it.label.endsWith("course length") }
            ?.value
            ?.replace("${section.comparisonLengthLabel} ", "")
        return ruleValue ?: kilometersText(section.comparisonLengthMeters)
    }

    private fun StringBuilder.appendRuleChecks(ruleChecks: List<DesktopCourseGoodnessMetric>) {
        if (ruleChecks.isEmpty()) {
            return
        }
        appendLine()
        appendLine("USA rules checks")
        ruleChecks.forEach { check ->
            val prefix = if (check.status == DesktopCourseMetricStatus.Warning) "RULE VIOLATION: " else ""
            appendLine("$prefix${check.label}: ${check.value} (${check.status.name})")
        }
    }

    private fun kmlText(result: DesktopCourseAnalysisSummary): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${xmlText("Course Analyzer - ${result.categoryName}")}</name>")
            appendLine("    <Style id=\"storedRouteStyle\"><LineStyle><color>ff0057b8</color><width>4</width></LineStyle></Style>")
            appendLine("    <Style id=\"calculatedRouteStyle\"><LineStyle><color>ff00a676</color><width>4</width></LineStyle></Style>")
            appendLine("    <Style id=\"foxStyle\"><IconStyle><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon></IconStyle></Style>")
            result.kmlFolders.forEach { folder ->
                val routeStyleId = if (folder.title.startsWith("Stored")) {
                    "storedRouteStyle"
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
        val wrappedLines = reportText(result)
            .lineSequence()
            .flatMap { line -> wrapPdfLine(line, 100).asSequence() }
            .toList()
        val pageContents = wrappedLines
            .chunked(52)
            .ifEmpty { listOf(listOf("")) }
            .map(::textPageContent) + graphicsPageContents(result)
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids ${pageContents.indices.joinToString(separator = " ", prefix = "[", postfix = "]") { "${4 + it * 2} 0 R" }} /Count ${pageContents.size} >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        pageContents.forEachIndexed { index, content ->
            val pageObjectId = 4 + index * 2
            val contentObjectId = pageObjectId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjectId 0 R >>"
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

    private fun textPageContent(lines: List<String>): String =
        buildString {
            appendLine("BT")
            appendLine("/F1 10 Tf")
            appendLine("54 750 Td")
            appendLine("13 TL")
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    appendLine("T*")
                }
                if (line.startsWith("RULE VIOLATION:")) {
                    appendLine("0.78 0.10 0.10 rg")
                } else {
                    appendLine("0 0 0 rg")
                }
                appendLine("(${line.toPdfText()}) Tj")
            }
            append("ET")
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
