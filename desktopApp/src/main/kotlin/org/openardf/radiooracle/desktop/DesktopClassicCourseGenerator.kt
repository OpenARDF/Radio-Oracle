package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class ClassicCourseGeneratorResult(
    val sourcePath: Path,
    val start: ClassicCoursePoint,
    val finish: ClassicCoursePoint,
    val beacon: ClassicCoursePoint?,
    val foxes: List<ClassicCoursePoint>,
    val requirementWarnings: List<ClassicCourseRequirementWarning>,
    val groups: List<ClassicCourseGeneratorGroup>,
    val elevationResolvedPointCount: Int,
    val missingElevationPointCount: Int
) {
    val rows: List<ClassicCourseGeneratorRow> = groups.flatMap { it.rows }
}

data class ClassicCoursePoint(
    val label: String,
    val point: CourseGeoPoint,
    val siCodeHint: Int? = null
)

data class ClassicCourseRequirementWarning(
    val label: String,
    val message: String
)

data class ClassicCourseGeneratorGroup(
    val foxCount: Int,
    val title: String,
    val rows: List<ClassicCourseGeneratorRow>
)

data class ClassicCourseGeneratorRow(
    val foxCount: Int,
    val effectiveLengthMeters: Double,
    val horizontalLengthMeters: Double,
    val climbMeters: Double?,
    val coursePoints: List<ClassicCoursePoint>,
    val routePoints: List<CourseGeoPoint>,
    val orderLabels: List<String>,
    val matchingCategories: List<String>
) {
    val hasCategoryMatch: Boolean = matchingCategories.isNotEmpty()
}

data class ClassicCourseGeneratorExportPaths(
    val pdfPath: Path,
    val kmlPath: Path
)

object DesktopClassicCourseGenerator {
    private const val CLASSIC_CLIMB_LIMIT_PERCENT = 6.0
    private const val CLASSIC_START_EXCLUSION_METERS = 750
    private const val CLASSIC_TRANSMITTER_SEPARATION_METERS = 400
    private const val COURSE_CANDIDATE_LINE_WIDTH = 3
    private val courseCandidateRouteColors = listOf(
        "ffb85700", // blue
        "ff1f7cff", // orange
        "ffa64ea6", // purple
        "ff3030d9", // red
        "ffd98200", // steel blue
        "ff008cff", // amber
        "ffd9538c", // violet
        "ff3366cc", // brick
        "ffcc99cc", // mauve
        "ff00bfff", // gold
        "ffff6666", // medium blue
        "ff663399", // maroon
        "ffff00cc", // magenta
        "ffcc6600", // deep blue
        "ff9966ff", // coral
        "ffff9999" // light blue
    )
    private val classicRequirements = linkedMapOf(
        "W12" to ClassicCourseRequirement(3, 3, 2_000, 3_000),
        "W14" to ClassicCourseRequirement(4, 4, 2_500, 3_000),
        "W16" to ClassicCourseRequirement(5, 5, 3_500, 4_000),
        "W19" to ClassicCourseRequirement(4, 4, 6_000, 8_000),
        "W21" to ClassicCourseRequirement(4, 4, 7_000, 9_000),
        "W35" to ClassicCourseRequirement(4, 5, 6_000, 8_000),
        "W45" to ClassicCourseRequirement(3, 4, 5_000, 7_000),
        "W55" to ClassicCourseRequirement(3, 4, 4_000, 6_000),
        "W65" to ClassicCourseRequirement(3, 4, 4_000, 6_000),
        "W75" to ClassicCourseRequirement(2, 4, 3_000, 5_000),
        "M12" to ClassicCourseRequirement(3, 3, 2_000, 3_000),
        "M14" to ClassicCourseRequirement(4, 4, 2_500, 3_000),
        "M16" to ClassicCourseRequirement(5, 5, 3_500, 4_000),
        "M19" to ClassicCourseRequirement(4, 4, 8_000, 10_000),
        "M21" to ClassicCourseRequirement(5, 5, 9_000, 12_000),
        "M40" to ClassicCourseRequirement(4, 4, 8_000, 10_000),
        "M50" to ClassicCourseRequirement(4, 5, 6_000, 8_000),
        "M60" to ClassicCourseRequirement(3, 4, 5_000, 7_000),
        "M70" to ClassicCourseRequirement(3, 4, 4_000, 6_000),
        "M80" to ClassicCourseRequirement(2, 4, 3_000, 5_000)
    )

    fun generate(
        sourcePath: Path,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult {
        val fileName = sourcePath.fileName.toString()
        require(fileName.endsWith(".kml", ignoreCase = true) || fileName.endsWith(".kmz", ignoreCase = true)) {
            "Choose a .kml or .kmz course points file."
        }
        val parsed = DesktopCourseKmlImporter.parse(sourcePath)
        return generate(sourcePath, parsed, elevationLookup)
    }

    fun generate(
        sourcePath: Path,
        courseData: DesktopCourseKmlData,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult {
        val classified = classifyCoursePoints(courseData.controls)
        val elevationResult = classified.withMissingElevations(elevationLookup)
        val elevated = elevationResult.classified
        val legSampleCache = mutableMapOf<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>()
        val groups = (3..elevated.foxes.size).map { foxCount ->
            ClassicCourseGeneratorGroup(
                foxCount = foxCount,
                title = groupTitle(foxCount),
                rows = elevated.foxes
                    .combinations(foxCount)
                    .map { foxSet ->
                        idealRow(
                            foxCount = foxCount,
                            start = elevated.start,
                            finish = elevated.finish,
                            beacon = elevated.beacon,
                            foxes = foxSet,
                            elevationLookup = elevationLookup,
                            legSampleCache = legSampleCache
                        )
                    }
                    .sortedWith(
                        compareBy<ClassicCourseGeneratorRow> { it.effectiveLengthMeters }
                            .thenBy { it.orderLabels.joinToString("\u0000") }
                    )
            )
        }
        return ClassicCourseGeneratorResult(
            sourcePath = sourcePath,
            start = elevated.start,
            finish = elevated.finish,
            beacon = elevated.beacon,
            foxes = elevated.foxes,
            requirementWarnings = requirementWarnings(elevated),
            groups = groups,
            elevationResolvedPointCount = elevationResult.resolvedPointCount,
            missingElevationPointCount = elevated.allPoints().count { it.point.elevationMeters == null }
        )
    }

    fun defaultPdfFileName(result: ClassicCourseGeneratorResult): String {
        val stem = result.sourcePath.fileName.toString()
            .removeSuffix(".kmz")
            .removeSuffix(".KMZ")
            .removeSuffix(".kml")
            .removeSuffix(".KML")
            .ifBlank { "Course Points" }
        return DesktopProjectFilePaths.defaultPdfFileName(stem, "Classic Course Generator")
    }

    fun exportPdf(path: Path, result: ClassicCourseGeneratorResult) {
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, pdfBytes(result))
    }

    fun exportPdfAndKml(path: Path, result: ClassicCourseGeneratorResult): ClassicCourseGeneratorExportPaths {
        exportPdf(path, result)
        val kmlPath = path.resolveSibling("${path.fileName.toString().removeSuffix(DesktopProjectFilePaths.PDF_EXTENSION)}.kml")
        exportKml(kmlPath, result)
        return ClassicCourseGeneratorExportPaths(pdfPath = path, kmlPath = kmlPath)
    }

    fun exportKml(path: Path, result: ClassicCourseGeneratorResult) {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, kmlText(result), StandardCharsets.UTF_8)
    }

    fun reportText(result: ClassicCourseGeneratorResult): String =
        buildString {
            appendLine("Classic Course Generator")
            appendLine("Source: ${result.sourcePath.fileName}")
            appendLine("Course points: Start, ${result.foxes.size} foxes, ${if (result.beacon == null) "no beacon" else "beacon"}, Finish")
            appendLine(elevationSummaryText(result))
            appendRequirementWarningText(result)
            appendLine()
            result.groups.forEach { group ->
                appendLine(group.title)
                appendLine("IDEAL EL : Course Order")
                group.rows.forEach { row ->
                    appendLine("${kilometers(row.effectiveLengthMeters)} : ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})")
                }
                appendLine()
            }
        }.trimEnd() + "\n"

    private fun classifyCoursePoints(points: List<CourseControlPoint>): ClassifiedClassicCoursePoints {
        val startPoints = mutableListOf<ClassicCoursePoint>()
        val finishPoints = mutableListOf<ClassicCoursePoint>()
        val beaconPoints = mutableListOf<ClassicCoursePoint>()
        val foxPoints = mutableListOf<ClassicCoursePoint>()
        points.forEach { point ->
            val coursePoint = ClassicCoursePoint(point.name, point.point, point.siCodeHint)
            when {
                point.name.isStartLabel() -> startPoints += coursePoint
                point.name.isFinishLabel() -> finishPoints += coursePoint
                point.name.isBeaconLabel() -> beaconPoints += coursePoint
                point.name.isSpectatorLabel() ->
                    throw IllegalArgumentException("Classic Course Generator does not accept spectator/separator points.")
                else -> foxPoints += coursePoint
            }
        }
        require(startPoints.size == 1) {
            "Course points file must contain exactly one Start point."
        }
        require(finishPoints.size == 1) {
            "Course points file must contain exactly one Finish point."
        }
        require(beaconPoints.size <= 1) {
            "Course points file must contain no more than one Beacon point."
        }
        require(foxPoints.size in 3..5) {
            "Course points file must contain between 3 and 5 fox points."
        }
        return ClassifiedClassicCoursePoints(
            start = startPoints.single(),
            finish = finishPoints.single(),
            beacon = beaconPoints.singleOrNull(),
            foxes = foxPoints
        )
    }

    private fun ClassifiedClassicCoursePoints.withMissingElevations(
        elevationLookup: (CourseGeoPoint) -> Double?
    ): ClassicCourseElevationResult {
        var resolvedPointCount = 0
        fun ClassicCoursePoint.withElevation(): ClassicCoursePoint {
            if (point.elevationMeters != null) {
                return this
            }
            val elevation = elevationLookup(point) ?: return this
            resolvedPointCount += 1
            return copy(point = point.copy(elevationMeters = elevation))
        }
        return ClassicCourseElevationResult(
            classified = copy(
                start = start.withElevation(),
                finish = finish.withElevation(),
                beacon = beacon?.withElevation(),
                foxes = foxes.map { it.withElevation() }
            ),
            resolvedPointCount = resolvedPointCount
        )
    }

    private fun idealRow(
        foxCount: Int,
        start: ClassicCoursePoint,
        finish: ClassicCoursePoint,
        beacon: ClassicCoursePoint?,
        foxes: List<ClassicCoursePoint>,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>
    ): ClassicCourseGeneratorRow =
        foxes.permutations()
            .map { orderedFoxes ->
                val orderedPoints = listOf(start) + orderedFoxes + listOfNotNull(beacon) + finish
                val routePoints = sampledCourseRoutePoints(orderedPoints, elevationLookup, legSampleCache)
                val horizontalLength = routePoints.routeLengthMeters()
                val climb = routePoints.climbMetersOrNull()
                val effectiveLength = if (climb == null) horizontalLength else horizontalLength + 10.0 * climb
                val matchingCategories = matchingClassicCategories(
                    foxCount = foxCount,
                    effectiveLengthMeters = effectiveLength,
                    horizontalLengthMeters = horizontalLength,
                    climbMeters = climb
                )
                ClassicCourseGeneratorRow(
                    foxCount = foxCount,
                    effectiveLengthMeters = effectiveLength,
                    horizontalLengthMeters = horizontalLength,
                    climbMeters = climb,
                    coursePoints = orderedPoints,
                    routePoints = routePoints,
                    orderLabels = listOf("S") + orderedFoxes.map { it.label } + listOfNotNull(beacon?.let { "B" }) + "F",
                    matchingCategories = matchingCategories
                )
            }
            .minWith(
                compareBy<ClassicCourseGeneratorRow> { it.effectiveLengthMeters }
                    .thenBy { it.orderLabels.joinToString("\u0000") }
            )

    private fun matchingClassicCategories(
        foxCount: Int,
        effectiveLengthMeters: Double,
        horizontalLengthMeters: Double,
        climbMeters: Double?
    ): List<String> {
        if (climbMeters == null || horizontalLengthMeters <= 0.0) {
            return emptyList()
        }
        val climbPercent = climbMeters / horizontalLengthMeters * 100.0
        if (climbPercent > CLASSIC_CLIMB_LIMIT_PERCENT) {
            return emptyList()
        }
        return classicRequirements.mapNotNull { (category, requirement) ->
            category.takeIf {
                foxCount in requirement.minControls..requirement.maxControls &&
                    effectiveLengthMeters.roundToInt() in requirement.minLengthMeters..requirement.maxLengthMeters
            }
        }
    }

    private fun sampledCourseRoutePoints(
        coursePoints: List<ClassicCoursePoint>,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>
    ): List<CourseGeoPoint> =
        DesktopCourseRouteSampler.sampledStraightRoutePoints(
            routePoints = coursePoints.map { it.point },
            elevationLookup = elevationLookup,
            legSampleCache = legSampleCache
        )

    private fun List<CourseGeoPoint>.routeLengthMeters(): Double =
        zipWithNext().sumOf { (from, to) -> from.distanceMetersTo(to) }

    private fun List<CourseGeoPoint>.climbMetersOrNull(): Double? {
        if (size < 2 || any { it.elevationMeters == null }) {
            return null
        }
        return zipWithNext().sumOf { (from, to) ->
            max(0.0, requireNotNull(to.elevationMeters) - requireNotNull(from.elevationMeters))
        }
    }

    private fun String.isStartLabel(): Boolean {
        val compact = compactCoursePointLabel()
        return compact == "start" || compact.endsWith("start")
    }

    private fun String.isFinishLabel(): Boolean {
        val compact = compactCoursePointLabel()
        return compact == "finish" || compact.endsWith("finish")
    }

    private fun String.isBeaconLabel(): Boolean =
        compactCoursePointLabel() in setOf("b", "m", "beacon")

    private fun String.isSpectatorLabel(): Boolean =
        compactCoursePointLabel() in setOf("s", "spectator", "separator")

    private fun String.compactCoursePointLabel(): String =
        trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")

    private fun groupTitle(foxCount: Int): String =
        when (foxCount) {
            3 -> "THREE-FOX COURSES"
            4 -> "FOUR-FOX COURSES"
            5 -> "FIVE-FOX COURSES"
            else -> "$foxCount-FOX COURSES"
        }

    private fun categoryText(row: ClassicCourseGeneratorRow): String =
        row.matchingCategories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "No category match"

    private fun requirementWarnings(classified: ClassifiedClassicCoursePoints): List<ClassicCourseRequirementWarning> =
        buildList {
            val transmitters = classified.transmitters()
            transmitters
                .map { transmitter -> transmitter.label to classified.start.point.distanceMetersTo(transmitter.point) }
                .minByOrNull { it.second }
                ?.takeIf { it.second + 0.5 < CLASSIC_START_EXCLUSION_METERS }
                ?.let { (label, distance) ->
                    add(
                        ClassicCourseRequirementWarning(
                            label = "Classic start exclusion zone",
                            message = "Violation: nearest transmitter $label ${distance.roundToInt()} m from Start (required at least $CLASSIC_START_EXCLUSION_METERS m)."
                        )
                    )
                }
            transmitters
                .flatMapIndexed { index, first ->
                    transmitters.drop(index + 1).map { second ->
                        "${first.label}-${second.label}" to first.point.distanceMetersTo(second.point)
                    }
                }
                .minByOrNull { it.second }
                ?.takeIf { it.second + 0.5 < CLASSIC_TRANSMITTER_SEPARATION_METERS }
                ?.let { (pair, distance) ->
                    add(
                        ClassicCourseRequirementWarning(
                            label = "Classic minimum transmitter spacing",
                            message = "Violation: closest transmitter pair $pair ${distance.roundToInt()} m apart (required at least $CLASSIC_TRANSMITTER_SEPARATION_METERS m)."
                        )
                    )
                }
        }

    private fun StringBuilder.appendRequirementWarningText(result: ClassicCourseGeneratorResult) {
        if (result.requirementWarnings.isEmpty()) {
            return
        }
        appendLine("Course requirement warnings:")
        result.requirementWarnings.forEach { warning ->
            appendLine("${warning.label}: ${warning.message}")
        }
    }

    private fun elevationSummaryText(result: ClassicCourseGeneratorResult): String =
        when {
            result.missingElevationPointCount == 0 && result.elevationResolvedPointCount > 0 ->
                "Elevation: filled ${result.elevationResolvedPointCount} missing point elevations from the local cache."
            result.missingElevationPointCount == 0 ->
                "Elevation: complete point elevations available."
            result.elevationResolvedPointCount > 0 ->
                "Elevation: filled ${result.elevationResolvedPointCount} missing point elevations from the local cache; ${result.missingElevationPointCount} point elevations remain missing."
            else ->
                "Elevation: ${result.missingElevationPointCount} point elevations missing; climb, effective length, and category matching are unavailable."
        }

    private fun kilometers(meters: Double): String =
        String.format(Locale.US, "%.2f km", meters / 1000.0)

    private fun pdfBytes(result: ClassicCourseGeneratorResult): ByteArray {
        val lines = pdfLines(result)
        val pages = lines.chunked(42).ifEmpty { listOf(listOf(PdfLine("", PdfColor.Body, 12))) }
        val objects = mutableListOf<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        val pageObjectIds = pages.indices.map { 3 + it * 2 }
        objects += "<< /Type /Pages /Kids ${pageObjectIds.joinToString(" ", prefix = "[", postfix = "]") { "$it 0 R" }} /Count ${pages.size} >>"
        pages.forEachIndexed { index, pageLines ->
            val pageId = pageObjectIds[index]
            val contentId = pageId + 1
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> /F2 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> >> >> /Contents $contentId 0 R >>"
            val content = pdfPageContent(pageLines)
            objects += "<< /Length ${content.toByteArray(StandardCharsets.UTF_8).size} >>\nstream\n$content\nendstream"
        }
        val output = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, obj ->
            offsets += output.length
            output.append("${index + 1} 0 obj\n$obj\nendobj\n")
        }
        val xrefOffset = output.length
        output.append("xref\n0 ${objects.size + 1}\n")
        output.append("0000000000 65535 f \n")
        offsets.drop(1).forEach { offset ->
            output.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        output.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return output.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun kmlText(result: ClassicCourseGeneratorResult): String {
        val greenRows = result.rows.filter { it.hasCategoryMatch }
        val courseObjects = (listOf(result.start) + result.foxes + listOfNotNull(result.beacon) + result.finish)
            .distinctBy { it.kmlObjectKey() }
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${xmlText(result.sourcePath.fileName.toString())} Classic Course Generator</name>")
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
            greenRows.indices.forEach { index ->
                appendCandidateRouteStyle(index)
            }
            appendLine("    <Folder>")
            appendLine("      <name>Course Objects</name>")
            courseObjects.forEach { courseObject ->
                appendLine("      <Placemark>")
                appendLine("        <name>${xmlText(courseObject.label)}</name>")
                courseObject.kmlDescription()?.let { description ->
                    appendLine("        <description>${xmlText(description)}</description>")
                }
                appendLine("        <styleUrl>#${courseObjectStyleId(courseObject, result)}</styleUrl>")
                appendLine("        <Point>")
                appendLine("          <coordinates>${courseObject.point.kmlCoordinate()}</coordinates>")
                appendLine("        </Point>")
                appendLine("      </Placemark>")
            }
            appendLine("    </Folder>")
            appendLine("    <Folder>")
            appendLine("      <name>Category-matching course candidates</name>")
            greenRows.forEachIndexed { index, row ->
                appendLine("      <Placemark>")
                appendLine("        <name>${xmlText(kmlRouteName(index + 1, row))}</name>")
                appendLine("        <description>${xmlText(kmlRouteDescription(row))}</description>")
                appendLine("        <styleUrl>#${candidateRouteStyleId(index)}</styleUrl>")
                appendLine("        <LineString>")
                appendLine("          <tessellate>1</tessellate>")
                appendLine("          <coordinates>")
                row.routePoints.forEach { routePoint ->
                    appendLine("            ${routePoint.kmlCoordinate()}")
                }
                appendLine("          </coordinates>")
                appendLine("        </LineString>")
                appendLine("      </Placemark>")
            }
            appendLine("    </Folder>")
            appendLine("  </Document>")
            appendLine("</kml>")
        }
    }

    private fun StringBuilder.appendCoursePointStyle(styleId: String, iconUrl: String) {
        appendLine("    <Style id=\"$styleId\">")
        appendLine("      <IconStyle>")
        appendLine("        <scale>${DesktopCourseKmlStyle.MarkerScale}</scale>")
        appendLine("        <color>${DesktopCourseKmlStyle.MarkerColor}</color>")
        appendLine("        <colorMode>normal</colorMode>")
        appendLine("        <Icon><href>$iconUrl</href></Icon>")
        appendLine("      </IconStyle>")
        appendLine("      <LabelStyle><color>${DesktopCourseKmlStyle.MarkerColor}</color><colorMode>normal</colorMode></LabelStyle>")
        appendLine("    </Style>")
    }

    private fun StringBuilder.appendCandidateRouteStyle(index: Int) {
        appendLine("    <Style id=\"${candidateRouteStyleId(index)}\">")
        appendLine("      <LineStyle>")
        appendLine("        <color>${courseCandidateRouteColors[index % courseCandidateRouteColors.size]}</color>")
        appendLine("        <width>$COURSE_CANDIDATE_LINE_WIDTH</width>")
        appendLine("      </LineStyle>")
        appendLine("    </Style>")
    }

    private fun candidateRouteStyleId(index: Int): String =
        "classicCourseCandidateRoute-${index + 1}"

    private fun courseObjectStyleId(
        courseObject: ClassicCoursePoint,
        result: ClassicCourseGeneratorResult
    ): String =
        when (courseObject.kmlObjectKey()) {
            result.start.kmlObjectKey() -> DesktopCourseKmlStyle.StartStyleId
            result.finish.kmlObjectKey() -> DesktopCourseKmlStyle.FinishStyleId
            else -> DesktopCourseKmlStyle.DonutStyleId
        }

    private fun kmlRouteName(index: Int, row: ClassicCourseGeneratorRow): String =
        "${index.toString().padStart(3, '0')} ${row.foxCount}-fox ${kilometers(row.effectiveLengthMeters)} ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})"

    private fun kmlRouteDescription(row: ClassicCourseGeneratorRow): String =
        listOf(
            "Matching Categories: ${row.matchingCategories.joinToString(", ")}",
            "Horizontal Length: ${kilometers(row.horizontalLengthMeters)}",
            "Climb: ${row.climbMeters?.roundToInt()?.let { "$it m" } ?: "Unknown"}",
            "Effective Length: ${kilometers(row.effectiveLengthMeters)}"
        ).joinToString("\n")

    private fun ClassicCoursePoint.kmlObjectKey(): String =
        "${label.trim().lowercase(Locale.US)}|${point.latitude}|${point.longitude}|${point.elevationMeters}"

    private fun ClassicCoursePoint.kmlDescription(): String? =
        siCodeHint?.let { "SI=$it" }

    private fun CourseGeoPoint.kmlCoordinate(): String =
        if (elevationMeters == null) {
            String.format(Locale.US, "%.8f,%.8f", longitude, latitude)
        } else {
            String.format(Locale.US, "%.8f,%.8f,%.2f", longitude, latitude, elevationMeters)
        }

    private fun xmlText(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun pdfLines(result: ClassicCourseGeneratorResult): List<PdfLine> =
        buildList {
            add(PdfLine("Classic Course Generator", PdfColor.Body, 18, bold = true))
            add(PdfLine("Source: ${result.sourcePath.fileName}", PdfColor.Body, 11))
            add(PdfLine("Course points: Start, ${result.foxes.size} foxes, ${if (result.beacon == null) "no beacon" else "beacon"}, Finish", PdfColor.Body, 11))
            add(PdfLine(elevationSummaryText(result), PdfColor.Body, 11))
            if (result.requirementWarnings.isNotEmpty()) {
                add(PdfLine("Course requirement warnings", PdfColor.WarningRed, 12, bold = true))
                result.requirementWarnings.forEach { warning ->
                    add(PdfLine("${warning.label}: ${warning.message}", PdfColor.WarningRed, 10))
                }
            }
            add(PdfLine("", PdfColor.Body, 8))
            result.groups.forEach { group ->
                add(PdfLine(group.title, PdfColor.Body, 14, bold = true))
                add(PdfLine("IDEAL EL : Course Order", PdfColor.Body, 11, bold = true))
                group.rows.forEach { row ->
                    add(
                        PdfLine(
                            "${kilometers(row.effectiveLengthMeters)} : ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})",
                            if (row.hasCategoryMatch) PdfColor.MatchGreen else PdfColor.NoMatchGray,
                            10
                        )
                    )
                }
                add(PdfLine("", PdfColor.Body, 8))
            }
        }

    private fun pdfPageContent(lines: List<PdfLine>): String =
        buildString {
            var y = 750.0
            lines.forEach { line ->
                appendLine("BT")
                appendLine("${line.color.r} ${line.color.g} ${line.color.b} rg")
                appendLine("/${if (line.bold) "F2" else "F1"} ${line.fontSize} Tf")
                appendLine("50 ${"%.2f".format(Locale.US, y)} Td")
                appendLine("(${line.text.toPdfText()}) Tj")
                appendLine("ET")
                y -= (line.fontSize + 5)
            }
        }

    private fun String.toPdfText(): String =
        replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private data class ClassifiedClassicCoursePoints(
        val start: ClassicCoursePoint,
        val finish: ClassicCoursePoint,
        val beacon: ClassicCoursePoint?,
        val foxes: List<ClassicCoursePoint>
    ) {
        fun allPoints(): List<ClassicCoursePoint> =
            listOf(start) + foxes + listOfNotNull(beacon) + finish

        fun transmitters(): List<ClassicCoursePoint> =
            foxes + listOfNotNull(beacon)
    }

    private data class ClassicCourseElevationResult(
        val classified: ClassifiedClassicCoursePoints,
        val resolvedPointCount: Int
    )

    private data class ClassicCourseRequirement(
        val minControls: Int,
        val maxControls: Int,
        val minLengthMeters: Int,
        val maxLengthMeters: Int
    )

    private data class PdfLine(
        val text: String,
        val color: PdfColor,
        val fontSize: Int,
        val bold: Boolean = false
    )

    private enum class PdfColor(val r: String, val g: String, val b: String) {
        Body("0", "0", "0"),
        WarningRed("0.78", "0.05", "0.05"),
        MatchGreen("0.00", "0.30", "0.08"),
        NoMatchGray("0.45", "0.45", "0.45")
    }
}

private fun <T> List<T>.combinations(size: Int): List<List<T>> {
    if (size == 0) return listOf(emptyList())
    if (size > this.size) return emptyList()
    if (size == this.size) return listOf(this)
    val result = mutableListOf<List<T>>()
    fun choose(startIndex: Int, selected: List<T>) {
        if (selected.size == size) {
            result += selected
            return
        }
        for (index in startIndex..(this.size - (size - selected.size))) {
            choose(index + 1, selected + this[index])
        }
    }
    choose(0, emptyList())
    return result
}

private fun <T> List<T>.permutations(): List<List<T>> {
    if (size <= 1) return listOf(this)
    val result = mutableListOf<List<T>>()
    fun permute(prefix: List<T>, remaining: List<T>) {
        if (remaining.isEmpty()) {
            result += prefix
            return
        }
        remaining.indices.forEach { index ->
            permute(prefix + remaining[index], remaining.take(index) + remaining.drop(index + 1))
        }
    }
    permute(emptyList(), this)
    return result
}
