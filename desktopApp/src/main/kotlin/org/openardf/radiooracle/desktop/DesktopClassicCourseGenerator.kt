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
    val recommendedCourseSets: List<ClassicCourseGeneratorRecommendedSet> = emptyList(),
    val groups: List<ClassicCourseGeneratorGroup>,
    val elevationResolvedPointCount: Int,
    val missingElevationPointCount: Int,
    val generatorTitle: String = "Classic Course Generator",
    val formatLabel: String = "Classic"
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

data class ClassicCourseGeneratorRecommendedSet(
    val index: Int,
    val courseCount: Int,
    val uniqueFirstFoxCount: Int,
    val categoryFoxMinimum: Int,
    val categoryFoxTotal: Int,
    val coveredCategories: List<String>,
    val rows: List<ClassicCourseGeneratorRow>
)

data class ClassicCourseGeneratorExportPaths(
    val pdfPath: Path,
    val kmlPath: Path
)

object DesktopClassicCourseGenerator {
    private const val CLASSIC_CLIMB_LIMIT_PERCENT = 6.0
    private const val CLASSIC_START_EXCLUSION_METERS = 750
    private const val CLASSIC_TRANSMITTER_SEPARATION_METERS = 400
    private const val FOXORING_START_EXCLUSION_METERS = 250
    private const val FOXORING_TRANSMITTER_SEPARATION_METERS = 250
    private const val COURSE_CANDIDATE_LINE_WIDTH = 3
    private const val FOXORING_RECOMMENDATION_LIMIT = 10
    private const val FOXORING_RECOMMENDATION_CANDIDATE_LIMIT = 120
    private const val FOXORING_RECOMMENDATION_SCORE_BUFFER = 200
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
    private val foxoringRequirements = linkedMapOf(
        "W19" to ClassicCourseRequirement(5, 8, 4_000, 6_000),
        "W21" to ClassicCourseRequirement(6, 10, 5_000, 7_000),
        "W35" to ClassicCourseRequirement(5, 8, 4_000, 6_000),
        "W45" to ClassicCourseRequirement(4, 7, 4_000, 6_000),
        "W55" to ClassicCourseRequirement(4, 7, 3_000, 5_000),
        "W65" to ClassicCourseRequirement(4, 7, 3_000, 5_000),
        "W75" to ClassicCourseRequirement(4, 7, 3_000, 4_000),
        "M19" to ClassicCourseRequirement(6, 8, 6_000, 8_000),
        "M21" to ClassicCourseRequirement(8, 10, 7_000, 9_000),
        "M40" to ClassicCourseRequirement(6, 8, 6_000, 8_000),
        "M50" to ClassicCourseRequirement(5, 8, 5_000, 7_000),
        "M60" to ClassicCourseRequirement(5, 8, 4_000, 6_000),
        "M70" to ClassicCourseRequirement(4, 7, 3_000, 5_000),
        "M80" to ClassicCourseRequirement(4, 7, 3_000, 4_000)
    )
    private val classicConfig = CourseGeneratorConfig(
        generatorTitle = "Classic Course Generator",
        formatLabel = "Classic",
        minimumFoxes = 3,
        maximumFoxes = 5,
        foxCounts = { totalFoxes -> 3..totalFoxes },
        requirements = classicRequirements,
        startExclusionMeters = CLASSIC_START_EXCLUSION_METERS,
        transmitterSeparationMeters = CLASSIC_TRANSMITTER_SEPARATION_METERS,
        useSubsetDynamicProgramming = false
    )
    private val foxoringConfig = CourseGeneratorConfig(
        generatorTitle = "Foxoring Course Generator",
        formatLabel = "Foxoring",
        minimumFoxes = 5,
        maximumFoxes = 12,
        foxCounts = { totalFoxes -> 4 until totalFoxes },
        requirements = foxoringRequirements,
        startExclusionMeters = FOXORING_START_EXCLUSION_METERS,
        transmitterSeparationMeters = FOXORING_TRANSMITTER_SEPARATION_METERS,
        useSubsetDynamicProgramming = true,
        recommendCourseSets = true
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

    fun generateFoxoring(
        sourcePath: Path,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult {
        val fileName = sourcePath.fileName.toString()
        require(fileName.endsWith(".kml", ignoreCase = true) || fileName.endsWith(".kmz", ignoreCase = true)) {
            "Choose a .kml or .kmz course points file."
        }
        val parsed = DesktopCourseKmlImporter.parse(sourcePath)
        return generateFoxoring(sourcePath, parsed, elevationLookup)
    }

    fun generate(
        sourcePath: Path,
        courseData: DesktopCourseKmlData,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult =
        generateWithConfig(sourcePath, courseData, elevationLookup, classicConfig)

    fun generateFoxoring(
        sourcePath: Path,
        courseData: DesktopCourseKmlData,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult =
        generateWithConfig(sourcePath, courseData, elevationLookup, foxoringConfig)

    private fun generateWithConfig(
        sourcePath: Path,
        courseData: DesktopCourseKmlData,
        elevationLookup: (CourseGeoPoint) -> Double?,
        config: CourseGeneratorConfig
    ): ClassicCourseGeneratorResult {
        val classified = classifyCoursePoints(courseData.controls, config)
        val elevationResult = classified.withMissingElevations(elevationLookup)
        val elevated = elevationResult.classified
        val legSampleCache = mutableMapOf<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>()
        val groups = config.foxCounts(elevated.foxes.size).map { foxCount ->
            ClassicCourseGeneratorGroup(
                foxCount = foxCount,
                title = groupTitle(foxCount),
                rows = idealRows(
                    foxCount = foxCount,
                    classified = elevated,
                    elevationLookup = elevationLookup,
                    legSampleCache = legSampleCache,
                    config = config
                )
            )
        }
        val recommendedCourseSets = if (config.recommendCourseSets) {
            recommendedFoxoringCourseSets(groups.flatMap { it.rows }, config)
        } else {
            emptyList()
        }
        return ClassicCourseGeneratorResult(
            sourcePath = sourcePath,
            start = elevated.start,
            finish = elevated.finish,
            beacon = elevated.beacon,
            foxes = elevated.foxes,
            requirementWarnings = requirementWarnings(elevated, config),
            recommendedCourseSets = recommendedCourseSets,
            groups = groups,
            elevationResolvedPointCount = elevationResult.resolvedPointCount,
            missingElevationPointCount = elevated.allPoints().count { it.point.elevationMeters == null },
            generatorTitle = config.generatorTitle,
            formatLabel = config.formatLabel
        )
    }

    fun defaultPdfFileName(result: ClassicCourseGeneratorResult): String {
        val stem = result.sourcePath.fileName.toString()
            .removeSuffix(".kmz")
            .removeSuffix(".KMZ")
            .removeSuffix(".kml")
            .removeSuffix(".KML")
            .ifBlank { "Course Points" }
        return DesktopProjectFilePaths.defaultPdfFileName(stem, result.generatorTitle)
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
            appendLine(result.generatorTitle)
            appendLine("Source: ${result.sourcePath.fileName}")
            appendLine("Course points: Start, ${result.foxes.size} foxes, ${if (result.beacon == null) "no beacon" else "beacon"}, Finish")
            appendLine(elevationSummaryText(result))
        appendRequirementWarningText(result)
        appendLine()
        appendRecommendedCourseSetText(result)
        result.groups.forEach { group ->
            appendLine(group.title)
                appendLine("IDEAL EL : Course Order")
                group.rows.forEach { row ->
                    appendLine("${kilometers(row.effectiveLengthMeters)} : ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})")
                }
                appendLine()
            }
        }.trimEnd() + "\n"

    private fun classifyCoursePoints(
        points: List<CourseControlPoint>,
        config: CourseGeneratorConfig
    ): ClassifiedClassicCoursePoints {
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
                    throw IllegalArgumentException("${config.generatorTitle} does not accept spectator/separator points.")
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
        require(foxPoints.size in config.minimumFoxes..config.maximumFoxes) {
            "Course points file must contain between ${config.minimumFoxes} and ${config.maximumFoxes} fox points."
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

    private fun idealRows(
        foxCount: Int,
        classified: ClassifiedClassicCoursePoints,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>,
        config: CourseGeneratorConfig
    ): List<ClassicCourseGeneratorRow> =
        if (config.useSubsetDynamicProgramming) {
            exactSubsetRows(
                foxCount = foxCount,
                classified = classified,
                elevationLookup = elevationLookup,
                legSampleCache = legSampleCache,
                config = config
            )
        } else {
            classified.foxes
                .combinations(foxCount)
                .map { foxSet ->
                    idealRow(
                        foxCount = foxCount,
                        start = classified.start,
                        finish = classified.finish,
                        beacon = classified.beacon,
                        foxes = foxSet,
                        elevationLookup = elevationLookup,
                        legSampleCache = legSampleCache,
                        config = config
                    )
                }
                .sortedWith(
                    compareBy<ClassicCourseGeneratorRow> { it.effectiveLengthMeters }
                        .thenBy { it.orderLabels.joinToString("\u0000") }
                )
        }

    private fun idealRow(
        foxCount: Int,
        start: ClassicCoursePoint,
        finish: ClassicCoursePoint,
        beacon: ClassicCoursePoint?,
        foxes: List<ClassicCoursePoint>,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>,
        config: CourseGeneratorConfig
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
                    climbMeters = climb,
                    config = config
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

    private fun exactSubsetRows(
        foxCount: Int,
        classified: ClassifiedClassicCoursePoints,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>,
        config: CourseGeneratorConfig
    ): List<ClassicCourseGeneratorRow> {
        val foxes = classified.foxes
        val foxSize = foxes.size
        val maskCount = 1 shl foxSize
        val edgeWeights = Array(foxSize + 1) { DoubleArray(foxSize) }
        val startIndex = foxSize
        repeat(foxSize) { to ->
            edgeWeights[startIndex][to] = legComparisonLength(
                from = classified.start,
                to = foxes[to],
                elevationLookup = elevationLookup,
                legSampleCache = legSampleCache
            )
        }
        repeat(foxSize) { from ->
            repeat(foxSize) { to ->
                edgeWeights[from][to] = if (from == to) {
                    0.0
                } else {
                    legComparisonLength(
                        from = foxes[from],
                        to = foxes[to],
                        elevationLookup = elevationLookup,
                        legSampleCache = legSampleCache
                    )
                }
            }
        }
        val terminalWeights = DoubleArray(foxSize) { from ->
            if (classified.beacon == null) {
                legComparisonLength(foxes[from], classified.finish, elevationLookup, legSampleCache)
            } else {
                legComparisonLength(foxes[from], classified.beacon, elevationLookup, legSampleCache) +
                    legComparisonLength(classified.beacon, classified.finish, elevationLookup, legSampleCache)
            }
        }
        val dp = Array(maskCount) { DoubleArray(foxSize) { Double.POSITIVE_INFINITY } }
        val parent = Array(maskCount) { IntArray(foxSize) { -1 } }
        repeat(foxSize) { foxIndex ->
            dp[1 shl foxIndex][foxIndex] = edgeWeights[startIndex][foxIndex]
        }
        for (mask in 1 until maskCount) {
            repeat(foxSize) { last ->
                val current = dp[mask][last]
                if (!current.isFinite()) return@repeat
                repeat(foxSize) { next ->
                    if (mask and (1 shl next) == 0) {
                        val nextMask = mask or (1 shl next)
                        val nextValue = current + edgeWeights[last][next]
                        if (nextValue < dp[nextMask][next] - 0.0001) {
                            dp[nextMask][next] = nextValue
                            parent[nextMask][next] = last
                        }
                    }
                }
            }
        }
        return (1 until maskCount)
            .filter { it.countOneBits() == foxCount }
            .map { mask ->
                val last = (0 until foxSize).minWith(
                    compareBy<Int> { dp[mask][it] + terminalWeights[it] }
                        .thenBy { foxes[it].label }
                )
                val orderedFoxes = reconstructFoxOrder(mask, last, parent, foxes)
                rowFromOrderedFoxes(
                    foxCount = foxCount,
                    start = classified.start,
                    finish = classified.finish,
                    beacon = classified.beacon,
                    orderedFoxes = orderedFoxes,
                    elevationLookup = elevationLookup,
                    legSampleCache = legSampleCache,
                    config = config
                )
            }
            .sortedWith(
                compareBy<ClassicCourseGeneratorRow> { it.effectiveLengthMeters }
                    .thenBy { it.orderLabels.joinToString("\u0000") }
            )
    }

    private fun reconstructFoxOrder(
        mask: Int,
        last: Int,
        parent: Array<IntArray>,
        foxes: List<ClassicCoursePoint>
    ): List<ClassicCoursePoint> {
        val reversed = mutableListOf<ClassicCoursePoint>()
        var currentMask = mask
        var current = last
        while (current >= 0) {
            reversed += foxes[current]
            val previous = parent[currentMask][current]
            currentMask = currentMask and (1 shl current).inv()
            current = previous
        }
        return reversed.asReversed()
    }

    private fun rowFromOrderedFoxes(
        foxCount: Int,
        start: ClassicCoursePoint,
        finish: ClassicCoursePoint,
        beacon: ClassicCoursePoint?,
        orderedFoxes: List<ClassicCoursePoint>,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>,
        config: CourseGeneratorConfig
    ): ClassicCourseGeneratorRow {
        val orderedPoints = listOf(start) + orderedFoxes + listOfNotNull(beacon) + finish
        val routePoints = sampledCourseRoutePoints(orderedPoints, elevationLookup, legSampleCache)
        val horizontalLength = routePoints.routeLengthMeters()
        val climb = routePoints.climbMetersOrNull()
        val effectiveLength = if (climb == null) horizontalLength else horizontalLength + 10.0 * climb
        return ClassicCourseGeneratorRow(
            foxCount = foxCount,
            effectiveLengthMeters = effectiveLength,
            horizontalLengthMeters = horizontalLength,
            climbMeters = climb,
            coursePoints = orderedPoints,
            routePoints = routePoints,
            orderLabels = listOf("S") + orderedFoxes.map { it.label } + listOfNotNull(beacon?.let { "B" }) + "F",
            matchingCategories = matchingClassicCategories(
                foxCount = foxCount,
                effectiveLengthMeters = effectiveLength,
                horizontalLengthMeters = horizontalLength,
                climbMeters = climb,
                config = config
            )
        )
    }

    private fun legComparisonLength(
        from: ClassicCoursePoint,
        to: ClassicCoursePoint,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>
    ): Double {
        val routePoints = sampledCourseRoutePoints(listOf(from, to), elevationLookup, legSampleCache)
        val horizontalLength = routePoints.routeLengthMeters()
        val climb = routePoints.climbMetersOrNull()
        return if (climb == null) horizontalLength else horizontalLength + 10.0 * climb
    }

    private fun matchingClassicCategories(
        foxCount: Int,
        effectiveLengthMeters: Double,
        horizontalLengthMeters: Double,
        climbMeters: Double?,
        config: CourseGeneratorConfig
    ): List<String> {
        if (climbMeters == null || horizontalLengthMeters <= 0.0) {
            return emptyList()
        }
        val climbPercent = climbMeters / horizontalLengthMeters * 100.0
        if (climbPercent > CLASSIC_CLIMB_LIMIT_PERCENT) {
            return emptyList()
        }
        return config.requirements.mapNotNull { (category, requirement) ->
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
            6 -> "SIX-FOX COURSES"
            7 -> "SEVEN-FOX COURSES"
            else -> "$foxCount-FOX COURSES"
        }

    private fun categoryText(row: ClassicCourseGeneratorRow): String =
        row.matchingCategories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "No category match"

    private fun recommendedFoxoringCourseSets(
        rows: List<ClassicCourseGeneratorRow>,
        config: CourseGeneratorConfig
    ): List<ClassicCourseGeneratorRecommendedSet> {
        val allCategories = config.requirements.keys.toList()
        val fullMask = (1 shl allCategories.size) - 1
        val categoryIndex = allCategories.withIndex().associate { it.value to it.index }
        val candidates = recommendationCandidateRows(rows, allCategories, categoryIndex)
        if (candidates.isEmpty()) {
            return emptyList()
        }
        val scoredSets = mutableListOf<RecommendedCourseSetScore>()
        val setComparator = recommendedSetComparator()
        fun addScoredSet(score: RecommendedCourseSetScore) {
            if (scoredSets.size < FOXORING_RECOMMENDATION_SCORE_BUFFER) {
                scoredSets += score
                return
            }
            val worst = scoredSets.maxWith(setComparator)
            if (setComparator.compare(score, worst) < 0) {
                scoredSets.remove(worst)
                scoredSets += score
            }
        }
        for (size in 3..4) {
            visitCandidateCombinations(candidates, size) { combination ->
                val categoryMask = combination.fold(0) { mask, candidate -> mask or candidate.categoryMask }
                if (categoryMask != fullMask) {
                    return@visitCandidateCombinations
                }
                addScoredSet(
                    scoreRecommendedSet(
                        rows = combination.map { it.row },
                        allCategories = allCategories,
                        categoryIndex = categoryIndex
                    )
                )
            }
        }
        return scoredSets
            .distinctBy { score ->
                score.rows
                    .map { it.orderLabels.joinToString(" -> ") }
                    .sorted()
                    .joinToString("\u0000")
            }
            .sortedWith(setComparator)
            .take(FOXORING_RECOMMENDATION_LIMIT)
            .mapIndexed { index, score ->
                ClassicCourseGeneratorRecommendedSet(
                    index = index + 1,
                    courseCount = score.rows.size,
                    uniqueFirstFoxCount = score.uniqueFirstFoxCount,
                    categoryFoxMinimum = score.categoryFoxMinimum,
                    categoryFoxTotal = score.categoryFoxTotal,
                    coveredCategories = allCategories,
                    rows = score.rows.sortedWith(
                        compareByDescending<ClassicCourseGeneratorRow> { it.foxCount }
                            .thenBy { it.effectiveLengthMeters }
                            .thenBy { it.orderLabels.joinToString("\u0000") }
                    )
                )
            }
    }

    private fun visitCandidateCombinations(
        candidates: List<RecommendationCandidateRow>,
        size: Int,
        visit: (List<RecommendationCandidateRow>) -> Unit
    ) {
        val selected = ArrayList<RecommendationCandidateRow>(size)
        fun recurse(startIndex: Int) {
            if (selected.size == size) {
                visit(selected.toList())
                return
            }
            val remainingNeeded = size - selected.size
            val maxStart = candidates.size - remainingNeeded
            for (index in startIndex..maxStart) {
                selected += candidates[index]
                recurse(index + 1)
                selected.removeAt(selected.lastIndex)
            }
        }
        if (candidates.size >= size) {
            recurse(0)
        }
    }

    private fun recommendationCandidateRows(
        rows: List<ClassicCourseGeneratorRow>,
        allCategories: List<String>,
        categoryIndex: Map<String, Int>
    ): List<RecommendationCandidateRow> {
        val candidates = rows
            .filter { it.hasCategoryMatch }
            .map { row ->
                RecommendationCandidateRow(
                    row = row,
                    categoryMask = row.matchingCategories.fold(0) { mask, category ->
                        mask or (1 shl requireNotNull(categoryIndex[category]))
                    }
                )
            }
        if (candidates.size <= FOXORING_RECOMMENDATION_CANDIDATE_LIMIT) {
            return candidates
        }
        val selected = linkedMapOf<String, RecommendationCandidateRow>()
        fun add(candidate: RecommendationCandidateRow) {
            val key = candidate.row.orderLabels.joinToString("\u0000")
            selected.putIfAbsent(key, candidate)
        }
        allCategories.forEach { category ->
            candidates
                .filter { category in it.row.matchingCategories }
                .sortedWith(recommendationCandidateComparator())
                .take(12)
                .forEach(::add)
        }
        candidates
            .groupBy { it.row.orderLabels.getOrNull(1).orEmpty() }
            .values
            .forEach { sameFirstFox ->
                sameFirstFox
                    .sortedWith(recommendationCandidateComparator())
                    .take(8)
                    .forEach(::add)
            }
        candidates
            .sortedWith(recommendationCandidateComparator())
            .take(FOXORING_RECOMMENDATION_CANDIDATE_LIMIT)
            .forEach(::add)
        return selected.values
            .sortedWith(recommendationCandidateComparator())
            .take(FOXORING_RECOMMENDATION_CANDIDATE_LIMIT)
    }

    private fun scoreRecommendedSet(
        rows: List<ClassicCourseGeneratorRow>,
        allCategories: List<String>,
        categoryIndex: Map<String, Int>
    ): RecommendedCourseSetScore {
        val firstFoxCounts = rows
            .mapNotNull { it.orderLabels.getOrNull(1) }
            .groupingBy { it }
            .eachCount()
        val uniqueFirstFoxCount = rows.count { row ->
            firstFoxCounts[row.orderLabels.getOrNull(1)] == 1
        }
        val categoryFoxCounts = IntArray(allCategories.size)
        rows.forEach { row ->
            row.matchingCategories.forEach { category ->
                val index = requireNotNull(categoryIndex[category])
                categoryFoxCounts[index] = max(categoryFoxCounts[index], row.foxCount)
            }
        }
        return RecommendedCourseSetScore(
            rows = rows,
            uniqueFirstFoxCount = uniqueFirstFoxCount,
            categoryFoxMinimum = categoryFoxCounts.minOrNull() ?: 0,
            categoryFoxTotal = categoryFoxCounts.sum(),
            totalEffectiveLengthMeters = rows.sumOf { it.effectiveLengthMeters }
        )
    }

    private fun recommendationCandidateComparator(): Comparator<RecommendationCandidateRow> =
        compareByDescending<RecommendationCandidateRow> { it.row.matchingCategories.size }
            .thenByDescending { it.row.foxCount }
            .thenBy { it.row.effectiveLengthMeters }
            .thenBy { it.row.orderLabels.joinToString("\u0000") }

    private fun recommendedSetComparator(): Comparator<RecommendedCourseSetScore> =
        compareByDescending<RecommendedCourseSetScore> { it.uniqueFirstFoxCount }
            .thenByDescending { it.categoryFoxMinimum }
            .thenByDescending { it.categoryFoxTotal }
            .thenByDescending { it.rows.size }
            .thenBy { it.totalEffectiveLengthMeters }
            .thenBy { it.rows.joinToString("\u0000") { row -> row.orderLabels.joinToString(" -> ") } }

    private fun requirementWarnings(
        classified: ClassifiedClassicCoursePoints,
        config: CourseGeneratorConfig = classicConfig
    ): List<ClassicCourseRequirementWarning> =
        buildList {
            val transmitters = classified.transmitters()
            transmitters
                .map { transmitter -> transmitter.label to classified.start.point.distanceMetersTo(transmitter.point) }
                .minByOrNull { it.second }
                ?.takeIf { it.second + 0.5 < config.startExclusionMeters }
                ?.let { (label, distance) ->
                    add(
                        ClassicCourseRequirementWarning(
                            label = "${config.formatLabel} start exclusion zone",
                            message = "Violation: nearest transmitter $label ${distance.roundToInt()} m from Start (required at least ${config.startExclusionMeters} m)."
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
                ?.takeIf { it.second + 0.5 < config.transmitterSeparationMeters }
                ?.let { (pair, distance) ->
                    add(
                        ClassicCourseRequirementWarning(
                            label = "${config.formatLabel} minimum transmitter spacing",
                            message = "Violation: closest transmitter pair $pair ${distance.roundToInt()} m apart (required at least ${config.transmitterSeparationMeters} m)."
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

    private fun StringBuilder.appendRecommendedCourseSetText(result: ClassicCourseGeneratorResult) {
        if (result.recommendedCourseSets.isEmpty()) {
            return
        }
        appendLine("FOXORING COURSE COMBINATIONS")
        result.recommendedCourseSets.forEach { set ->
            appendLine(
                "Combination ${set.index}: ${set.courseCount} courses, " +
                    "${set.uniqueFirstFoxCount} unique first foxes, minimum category fox count ${set.categoryFoxMinimum}, total category fox count ${set.categoryFoxTotal}"
            )
            set.rows.forEach { row ->
                appendLine("  ${kilometers(row.effectiveLengthMeters)} : ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})")
            }
        }
        appendLine()
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
            appendLine("    <name>${xmlText(result.sourcePath.fileName.toString())} ${xmlText(result.generatorTitle)}</name>")
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
                row.coursePoints.forEach { coursePoint ->
                    appendLine("            ${coursePoint.point.kmlCoordinate()}")
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
            add(PdfLine(result.generatorTitle, PdfColor.Body, 18, bold = true))
            add(PdfLine("Source: ${result.sourcePath.fileName}", PdfColor.Body, 11))
            add(PdfLine("Course points: Start, ${result.foxes.size} foxes, ${if (result.beacon == null) "no beacon" else "beacon"}, Finish", PdfColor.Body, 11))
            add(PdfLine(elevationSummaryText(result), PdfColor.Body, 11))
            if (result.requirementWarnings.isNotEmpty()) {
                add(PdfLine("Course requirement warnings", PdfColor.WarningRed, 12, bold = true))
                result.requirementWarnings.forEach { warning ->
                    add(PdfLine("${warning.label}: ${warning.message}", PdfColor.WarningRed, 10))
                }
            }
            if (result.recommendedCourseSets.isNotEmpty()) {
                add(PdfLine("FOXORING COURSE COMBINATIONS", PdfColor.Body, 14, bold = true))
                result.recommendedCourseSets.forEach { set ->
                    add(
                        PdfLine(
                            "Combination ${set.index}: ${set.courseCount} courses, ${set.uniqueFirstFoxCount} unique first foxes, minimum category fox count ${set.categoryFoxMinimum}, total category fox count ${set.categoryFoxTotal}",
                            PdfColor.Body,
                            11,
                            bold = true
                        )
                    )
                    set.rows.forEach { row ->
                        add(
                            PdfLine(
                                "${kilometers(row.effectiveLengthMeters)} : ${row.orderLabels.joinToString(" -> ")} (${categoryText(row)})",
                                PdfColor.MatchGreen,
                                10
                            )
                        )
                    }
                }
                add(PdfLine("", PdfColor.Body, 8))
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

    private data class CourseGeneratorConfig(
        val generatorTitle: String,
        val formatLabel: String,
        val minimumFoxes: Int,
        val maximumFoxes: Int,
        val foxCounts: (Int) -> IntRange,
        val requirements: LinkedHashMap<String, ClassicCourseRequirement>,
        val startExclusionMeters: Int,
        val transmitterSeparationMeters: Int,
        val useSubsetDynamicProgramming: Boolean,
        val recommendCourseSets: Boolean = false
    )

    private data class RecommendationCandidateRow(
        val row: ClassicCourseGeneratorRow,
        val categoryMask: Int
    )

    private data class RecommendedCourseSetScore(
        val rows: List<ClassicCourseGeneratorRow>,
        val uniqueFirstFoxCount: Int,
        val categoryFoxMinimum: Int,
        val categoryFoxTotal: Int,
        val totalEffectiveLengthMeters: Double
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

object DesktopFoxoringCourseGenerator {
    fun generate(
        sourcePath: Path,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult =
        DesktopClassicCourseGenerator.generateFoxoring(sourcePath, elevationLookup)

    fun generate(
        sourcePath: Path,
        courseData: DesktopCourseKmlData,
        elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): ClassicCourseGeneratorResult =
        DesktopClassicCourseGenerator.generateFoxoring(sourcePath, courseData, elevationLookup)

    fun defaultPdfFileName(result: ClassicCourseGeneratorResult): String =
        DesktopClassicCourseGenerator.defaultPdfFileName(result)

    fun exportPdf(path: Path, result: ClassicCourseGeneratorResult) {
        DesktopClassicCourseGenerator.exportPdf(path, result)
    }

    fun exportPdfAndKml(path: Path, result: ClassicCourseGeneratorResult): ClassicCourseGeneratorExportPaths =
        DesktopClassicCourseGenerator.exportPdfAndKml(path, result)

    fun exportKml(path: Path, result: ClassicCourseGeneratorResult) {
        DesktopClassicCourseGenerator.exportKml(path, result)
    }

    fun reportText(result: ClassicCourseGeneratorResult): String =
        DesktopClassicCourseGenerator.reportText(result)
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
