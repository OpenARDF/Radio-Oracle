package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class DesktopCourseAnalysisSummary(
    val categoryName: String,
    val rulesDocumentLabel: String,
    val providedRouteSection: DesktopCourseAnalysisSection?,
    val calculatedRouteSection: DesktopCourseAnalysisSection?,
    val summaryExplanation: String,
    val profileComparison: List<DesktopCourseElevationProfileSummary>,
    val elevationCacheNotes: List<String>,
    val routeMaps: List<DesktopCourseRouteMap>,
    val kmlFolders: List<DesktopCourseKmlExportFolder>,
    val calculatedRouteApplication: DesktopCourseCalculatedRouteApplication?,
    val missingElements: List<String>,
    val calculatedRouteCount: Int,
    val calculatedIdealOrder: List<String>,
    val providedIdealOrder: List<String>,
    val idealOrderMatches: Boolean?,
    val calculatedStraightLineMeters: Int?,
    val providedStraightLineMeters: Int?,
    val routeLengthMeters: Int?,
    val climbMeters: Int?,
    val effectiveLengthMeters: Int?,
    val estimatedIdealSeconds: Int?,
    val hasMissingElevationData: Boolean,
    val elevationProfile: List<DesktopCourseElevationProfilePoint>,
    val providedLegRows: List<DesktopCourseLegRow>,
    val calculatedLegRows: List<DesktopCourseLegRow>,
    val waitRows: List<DesktopCourseWaitRow>,
    val waitRenumbering: DesktopCourseWaitRenumbering?,
    val metrics: List<DesktopCourseGoodnessMetric>
)

data class DesktopCourseAnalysisSection(
    val title: String,
    val explanation: String,
    val routeOrder: List<String>,
    val routeOrderLabel: String = "Route order",
    val summaryOnly: Boolean = false,
    val secondaryRouteOrder: List<String> = emptyList(),
    val secondaryRouteOrderLabel: String? = null,
    val comparisonLengthMeters: Int?,
    val comparisonLengthLabel: String,
    val straightLineMeters: Int?,
    val routeLengthMeters: Int?,
    val climbMeters: Int?,
    val effectiveLengthMeters: Int?,
    val estimatedIdealSeconds: Int?,
    val legRows: List<DesktopCourseLegRow>,
    val waitRows: List<DesktopCourseWaitRow>,
    val waitRenumbering: DesktopCourseWaitRenumbering?,
    val ruleChecks: List<DesktopCourseGoodnessMetric> = emptyList(),
    val elevationProfile: List<DesktopCourseElevationProfilePoint>,
    val routeMap: DesktopCourseRouteMap?
)

data class DesktopCourseElevationProfileSummary(
    val title: String,
    val profile: List<DesktopCourseElevationProfilePoint>,
    val markers: List<DesktopCourseElevationProfileMarker> = emptyList()
)

data class DesktopCourseElevationProfilePoint(
    val distanceMeters: Int,
    val elevationMeters: Double
)

data class DesktopCourseElevationProfileMarker(
    val label: String,
    val distanceMeters: Int,
    val elevationMeters: Double
)

data class DesktopCourseRouteMap(
    val title: String,
    val points: List<DesktopCourseRouteMapPoint>,
    val routeLabels: List<String>
)

data class DesktopCourseRouteMapPoint(
    val label: String,
    val xFraction: Double,
    val yFraction: Double,
    val type: DesktopCourseRouteMapPointType
)

data class DesktopCourseKmlExportFolder(
    val title: String,
    val routeName: String,
    val routePoints: List<CourseGeoPoint>,
    val foxes: List<DesktopCourseKmlExportPoint>
)

data class DesktopCourseKmlExportPoint(
    val label: String,
    val originalLabel: String?,
    val point: CourseGeoPoint
)

data class DesktopCourseCalculatedRouteApplication(
    val categoryId: String,
    val idealOrderText: String,
    val routePoints: List<CourseGeoPoint>,
    val routeLengthMeters: Int?,
    val climbMeters: Int?,
    val foxAssignments: List<DesktopCourseCalculatedFoxAssignment>
)

data class DesktopCourseCalculatedFoxAssignment(
    val controlId: String,
    val originalLabel: String,
    val calculatedLabel: String
)

enum class DesktopCourseRouteMapPointType {
    Start,
    Finish,
    Control,
    Beacon,
    Spectator
}

data class DesktopCourseWaitRow(
    val controlId: String?,
    val controlLabel: String,
    val arrivalSeconds: Int,
    val waitSeconds: Int,
    val slotLabel: String? = null
)

data class DesktopCourseLegRow(
    val fromLabel: String,
    val toLabel: String,
    val lengthMeters: Int?,
    val splitSeconds: Int?,
    val cumulativeSeconds: Int?,
    val waitSeconds: Int? = null,
    val findPunchSeconds: Int? = null
)

data class DesktopCourseWaitRenumbering(
    val currentTotalWaitSeconds: Int,
    val bestTotalWaitSeconds: Int,
    val improvesWait: Boolean,
    val assignments: List<DesktopCourseWaitRenumberingAssignment>
)

data class DesktopCourseWaitRenumberingAssignment(
    val controlId: String,
    val controlLabel: String,
    val currentSlotLabel: String,
    val suggestedSlotLabel: String
)

data class DesktopCourseGoodnessMetric(
    val label: String,
    val value: String,
    val status: DesktopCourseMetricStatus
)

enum class DesktopCourseMetricStatus {
    Good,
    Warning,
    Unknown
}

/**
 * Builds the desktop Course Analyzer report from protected course geometry and category controls.
 *
 * The analyzer intentionally separates the course-setter-supplied route from the independently
 * calculated candidate. Both use the same measurement policy: effective length when complete
 * elevation data is available, otherwise horizontal distance. Classic-style wait-time checks replay
 * the timed route to identify whether a different fox numbering can reduce waiting. A Classic fox
 * stop is modeled as arrival near the fox, wait until transmission if needed, then a fixed
 * 30-second find-and-punch allowance before departure to the next leg.
 *
 * Elevation Cache resolution is local sample-grid spacing, not a guarantee of source DEM
 * resolution. USGS 3DEP is a multi-resolution source; a dense cache may still sample coarser
 * terrain data where 3-meter or better DEM coverage is unavailable.
 *
 * The analyzer does not currently import map passability. Out-of-bounds areas, dense vegetation,
 * lakes, uncrossable watercourses, fences, cliffs, and other navigation barriers are not modeled,
 * so route order and wait-time estimates remain advisory.
 *
 * Estimated times use an elite-competitor baseline pace by race format and convert each leg to
 * effective length when elevation is available. The current implementation does not yet tune that
 * baseline by category age or gender and does not model fatigue across the course.
 */
object DesktopCourseAnalyzer {
    private const val USA_RULES_DOCUMENT_LABEL = "USA Rules for Radio Orienteering, Effective Date: 1 Jan 2026"
    private const val CLASSIC_TRANSMIT_CYCLE_SECONDS = 300
    private const val CLASSIC_TRANSMIT_SLOT_SECONDS = 60
    private const val CLASSIC_CONTROL_FIND_PUNCH_SECONDS = 30
    private const val CLASSIC_TARGET_SECONDS = 60 * 60
    private const val SPRINT_TARGET_SECONDS = 15 * 60
    private const val FOXORING_TARGET_SECONDS = 45 * 60
    private const val CLASSIC_FLAT_SPEED_MPS = 3.6
    private const val SPRINT_FLAT_SPEED_MPS = 4.2
    private const val FOXORING_FLAT_SPEED_MPS = 3.4
    private const val MAX_PERMUTATION_CONTROLS = 8
    private const val FOXORING_EXHAUSTIVE_CONTROLS = 6
    private const val MAX_SPRINT_LOOP_PERMUTATIONS = 120
    private const val CALCULATED_ROUTE_SAMPLE_METERS = 25.0
    private const val ELEVATION_CACHE_RESOLUTION_NOTE =
        "Elevation Cache resolution is the local sample-grid spacing; USGS 3DEP source DEM resolution varies, so a 3 m cache does not guarantee 3 m source terrain data everywhere."
    private const val MAP_KNOWLEDGE_LIMITATION_NOTE =
        "The analyzer does not currently know map passability, so out-of-bounds areas, dense vegetation, water, uncrossable features, and other impediments can make the true on-foot route and wait timing differ from this estimate."
    private const val SPEED_MODEL_NOTE =
        "Estimated times use an elite-competitor baseline pace by race format: 4:38 min/km for Classic-style courses (3.6 m/s), 3:58 min/km for Sprint (4.2 m/s), and 4:54 min/km for Foxoring (3.4 m/s). When elevation is available, movement time uses effective length for each leg: horizontal length plus ten times positive climb. If elevation is incomplete, movement time falls back to horizontal distance. Fatigue is not modeled, and the current model is not yet adjusted by category age or gender."
    private const val CLASSIC_WAIT_TIMING_NOTE =
        "For Classic-style fox controls, timing assumes the competitor waits if the fox is off the air, then spends 30 seconds finding and punching before departing for the next leg; that delay affects later arrival phases."
    private val classicCategoryRequirements = mapOf(
        "W19" to CourseRuleRequirement(4, 4, 6_000, 8_000),
        "W21" to CourseRuleRequirement(4, 4, 7_000, 9_000),
        "W35" to CourseRuleRequirement(4, 5, 6_000, 8_000),
        "W45" to CourseRuleRequirement(3, 4, 5_000, 7_000),
        "W55" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "W65" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "W75" to CourseRuleRequirement(2, 4, 3_000, 5_000),
        "M19" to CourseRuleRequirement(4, 4, 8_000, 10_000),
        "M21" to CourseRuleRequirement(5, 5, 9_000, 12_000),
        "M40" to CourseRuleRequirement(4, 4, 8_000, 10_000),
        "M50" to CourseRuleRequirement(4, 5, 6_000, 8_000),
        "M60" to CourseRuleRequirement(3, 4, 5_000, 7_000),
        "M70" to CourseRuleRequirement(3, 4, 4_000, 6_000),
        "M80" to CourseRuleRequirement(2, 4, 3_000, 5_000)
    )
    private val youthClassicCategoryRequirements = mapOf(
        "W12" to CourseRuleRequirement(3, 3, 2_000, 3_000),
        "W14" to CourseRuleRequirement(4, 4, 2_500, 3_000),
        "W16" to CourseRuleRequirement(5, 5, 3_500, 4_000),
        "M12" to CourseRuleRequirement(3, 3, 2_000, 3_000),
        "M14" to CourseRuleRequirement(4, 4, 2_500, 3_000),
        "M16" to CourseRuleRequirement(5, 5, 3_500, 4_000)
    )
    private val foxoringCategoryRequirements = mapOf(
        "W19" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "W21" to CourseRuleRequirement(6, 10, 5_000, 7_000),
        "W35" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "W45" to CourseRuleRequirement(4, 7, 4_000, 6_000),
        "W55" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "W65" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "W75" to CourseRuleRequirement(4, 7, 3_000, 4_000),
        "M19" to CourseRuleRequirement(6, 8, 6_000, 8_000),
        "M21" to CourseRuleRequirement(8, 10, 7_000, 9_000),
        "M40" to CourseRuleRequirement(6, 8, 6_000, 8_000),
        "M50" to CourseRuleRequirement(5, 8, 5_000, 7_000),
        "M60" to CourseRuleRequirement(5, 8, 4_000, 6_000),
        "M70" to CourseRuleRequirement(4, 7, 3_000, 5_000),
        "M80" to CourseRuleRequirement(4, 7, 3_000, 4_000)
    )

    fun analyze(
        projectFile: EventProjectFile,
        categoryId: String,
        protectedCourseInfo: ProtectedCourseInfo?,
        protectedIdealOrderText: String?,
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        elevationCacheNotes: (List<CourseGeoPoint>) -> List<String> = { emptyList() }
    ): DesktopCourseAnalysisSummary {
        val categoryData = projectFile.raceData.categories.first { it.category.id == categoryId }
        val category = categoryData.category
        val raceType = category.effectiveRaceType(projectFile.raceData.race)
        val idealOrderText = protectedIdealOrderText?.takeIf { it.isNotBlank() }
            ?: protectedCourseInfo?.idealOrder?.takeIf { it.isNotBlank() }
        val categoryAssignedControls = assignedControls(projectFile, categoryId)
        val assignedControls = categoryAssignedControls.ifEmpty {
            protectedCourseInfo
                ?.let { protectedAssignedControls(projectFile, it, idealOrderText) }
                .orEmpty()
        }
        val protectedControlPointsById = protectedCourseInfo?.controlPoints.orEmpty().associateBy { it.controlId }
        val protectedCoordinateLookup = protectedCoordinateLookup(protectedCourseInfo)
        val missing = mutableListOf<String>()

        if (protectedCourseInfo == null) {
            missing += "Protected route data is locked or has not been imported for ${category.name}."
        }
        val route = protectedCourseInfo?.route.orEmpty().map {
            CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters)
        }
        if (route.size < 2) {
            missing += "Protected route geometry with start and finish points is missing."
        }
        val hasMissingRouteElevations = route.any { it.elevationMeters == null }
        if (hasMissingRouteElevations) {
            missing += "Route elevation samples are missing or incomplete."
        }
        var hasMissingCourseObjectElevations = false
        var hasMissingProtectedControlElevations = false
        protectedCourseInfo?.let { courseInfo ->
            when {
                courseInfo.courseObjects.isEmpty() -> {
                    missing += "Course object points are missing for start, finish, controls, beacon, or spectator."
                }
                courseInfo.courseObjects.any { it.elevationMeters == null } -> {
                    hasMissingCourseObjectElevations = true
                    missing += "Course object elevations are missing or incomplete."
                }
            }
            if (courseInfo.controlPoints.any { it.elevationMeters == null }) {
                hasMissingProtectedControlElevations = true
                missing += "Protected control point elevations are missing or incomplete."
            }
        }
        val hasMissingElevationData = route.size >= 2 &&
            (hasMissingRouteElevations || hasMissingCourseObjectElevations || hasMissingProtectedControlElevations)

        val controlsWithPoints = assignedControls.map { control ->
            ControlAnalysisPoint(
                control = control,
                point = protectedControlPointsById[control.id]?.toGeoPoint()
                    ?: protectedPointForControl(control, protectedCoordinateLookup)
            )
        }
        val missingCoordinateControls = controlsWithPoints.filter { it.point == null }
        if (missingCoordinateControls.isNotEmpty()) {
            DesktopDebugLog.warn(
                "CourseAnalysis",
                "Missing coordinates category=${category.name}: " +
                    "controls=${missingCoordinateControls.joinToString { it.control.publicDisplayLabel() }}; " +
                    "assigned=${assignedControls.size} protectedControlPoints=${protectedCourseInfo?.controlPoints?.size ?: 0} " +
                    "protectedCourseObjects=${protectedCourseInfo?.courseObjects?.size ?: 0} " +
                    "tokenMatches=${protectedCoordinateLookup.pointsByToken.size} " +
                    "singleBeacon=${protectedCoordinateLookup.singleBeaconPoint != null} " +
                    "singleSpectator=${protectedCoordinateLookup.singleSpectatorPoint != null}"
            )
        }
        missingCoordinateControls.forEach {
            missing += "Location latitude/longitude is missing for control ${it.control.publicDisplayLabel()}."
        }

        val start = route.firstOrNull() ?: protectedCourseInfo?.courseObjects
            ?.firstOrNull { it.type == ProtectedCourseObjectType.START }
            ?.toGeoPoint()
        val finish = route.lastOrNull() ?: protectedCourseInfo?.courseObjects
            ?.firstOrNull { it.type == ProtectedCourseObjectType.FINISH }
            ?.toGeoPoint()
        val foxes = controlsWithPoints
            .filter { it.control.type == ControlPointType.CONTROL && it.point != null }
        val spectator = controlsWithPoints
            .firstOrNull { it.control.type == ControlPointType.SEPARATOR && it.point != null }
        val beacon = controlsWithPoints
            .firstOrNull { it.control.type == ControlPointType.BEACON && it.point != null }
        val calculatedRoute = calculatedRouteCandidate(
            raceType = raceType,
            start = start,
            finish = finish,
            foxes = foxes,
            spectator = spectator,
            beacon = beacon,
            elevationLookup = elevationLookup,
            missing = missing
        )

        val providedControls = idealOrderText
            ?.let { idealOrder ->
                runCatching {
                    val ids = ProtectedIdealOrderRules.resolveControlIds(idealOrder, assignedControls)
                    ids.mapNotNull { id -> assignedControls.firstOrNull { it.id == id } }
                }.getOrElse { error ->
                    missing += "Protected ideal order could not be resolved: ${error.message ?: error::class.simpleName}."
                    emptyList()
                }
            }
            .orEmpty()
        val providedFoxIds = providedControls
            .filter { it.type == ControlPointType.CONTROL }
            .map { it.id }
        val calculatedFoxIds = calculatedRoute
            ?.controls
            ?.filter { it.control.type == ControlPointType.CONTROL }
            ?.map { it.control.id }
            .orEmpty()
        val idealOrderMatches = calculatedRoute?.let {
            providedFoxIds.isNotEmpty() && providedFoxIds == calculatedFoxIds
        }
        val calculatedRouteMatchesStored = idealOrderMatches == true

        val providedRoutePoints = buildList {
            start?.let(::add)
            providedControls.mapNotNull { control ->
                controlsWithPoints.firstOrNull { it.control.id == control.id }?.point
            }.forEach(::add)
            finish?.let(::add)
        }
        val providedStraightLineMeters = providedRoutePoints
            .takeIf { it.size >= 2 }
            ?.straightLineMeters()
            ?.roundToInt()
        val providedTiming = routeGeometryTiming(
            route = route,
            controls = providedControls,
            controlsWithPoints = controlsWithPoints,
            raceType = raceType
        )
        val providedLegRows = providedTiming.legRows
        val waitRows = providedTiming.waitRows
        if (waitRows.isEmpty() && providedControls.any { it.type == ControlPointType.CONTROL } && raceType != RaceType.CLASSIC && raceType != RaceType.SHORT) {
            missing += "Transmit-slot wait analysis is currently implemented for Classic-style five-minute cycles only."
        }
        if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
            providedControls
                .filter { it.type == ControlPointType.CONTROL && classicSlotIndex(it) == null }
                .forEach { control ->
                    missing += "Transmit slot could not be determined for control ${control.publicDisplayLabel()}."
                }
        }
        val waitRenumbering = if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
            waitRenumbering(providedControls) { slotOverrides ->
                routeGeometryTiming(
                    route = route,
                    controls = providedControls,
                    controlsWithPoints = controlsWithPoints,
                    raceType = raceType,
                    slotOverrides = slotOverrides
                )
            }
        } else {
            null
        }

        val estimatedIdealSeconds = providedTiming.totalSeconds?.roundToInt()
        val elevationProfile = elevationProfile(route)
        val providedRouteAnalysis = if (providedControls.isNotEmpty() && route.size >= 2) {
            providedRouteAnalysis(
                route = route,
                providedRoutePoints = providedRoutePoints,
                protectedCourseInfo = protectedCourseInfo,
                timing = providedTiming
            )
        } else {
            null
        }
        val calculatedWaitRenumbering = if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
            calculatedRoute?.let { routeCandidate ->
                waitRenumbering(routeCandidate.controls.map { it.control }) { slotOverrides ->
                    straightLineTiming(
                        start = start,
                        controls = routeCandidate.controls,
                        finish = finish,
                        raceType = raceType,
                        slotOverrides = slotOverrides,
                        elevationLookup = elevationLookup
                    )
                }
            }
        } else {
            null
        }
        val calculatedSlotOverrides = calculatedRoute
            ?.let { routeCandidate -> slotOverridesFromAssignments(routeCandidate.controls.map { it.control }, calculatedWaitRenumbering) }
            .orEmpty()
        val calculatedLabelOverrides = calculatedRoute
            ?.let { routeCandidate -> calculatedLabelOverrides(routeCandidate.controls, calculatedWaitRenumbering) }
            .orEmpty()
        val calculatedTiming = calculatedRoute?.let { routeCandidate ->
            straightLineTiming(
                start = start,
                controls = routeCandidate.controls,
                finish = finish,
                raceType = raceType,
                slotOverrides = calculatedSlotOverrides,
                labelOverrides = calculatedLabelOverrides,
                elevationLookup = elevationLookup
            )
        } ?: RouteTimingAnalysis.Empty
        val calculatedLegRows = calculatedTiming.legRows
        val calculatedWaitRows = calculatedTiming.waitRows
        val calculatedRouteAnalysis = calculatedRoute?.let { routeCandidate ->
            calculatedRouteAnalysis(
                start = start,
                finish = finish,
                calculatedRoute = routeCandidate,
                timing = calculatedTiming,
                elevationLookup = elevationLookup
            )
        }
        val spacingRuleChecks = coursePointRuleChecks(
            raceType = raceType,
            categoryName = category.name,
            start = start,
            foxes = foxes,
            spectator = spectator,
            beacon = beacon
        )
        val providedRuleChecks = providedRouteAnalysis?.let { analysis ->
            routeRuleChecks(
                routeLabel = "Stored route",
                raceType = raceType,
                categoryName = category.name,
                foxCount = foxes.size,
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                measurementLabel = analysis.measurementLabel,
                estimatedSeconds = analysis.estimatedSeconds.roundToInt()
            ) + spacingRuleChecks
        }.orEmpty()
        val calculatedRuleChecks = calculatedRouteAnalysis?.let { analysis ->
            routeRuleChecks(
                routeLabel = "Calculated route",
                raceType = raceType,
                categoryName = category.name,
                foxCount = foxes.size,
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                measurementLabel = analysis.measurementLabel,
                estimatedSeconds = analysis.estimatedSeconds.roundToInt()
            ) + spacingRuleChecks
        }.orEmpty()
        val calculatedSection = calculatedRoute?.let { routeCandidate ->
            val optimizedAssignments = calculatedWaitRenumbering
                ?.takeIf { raceType == RaceType.CLASSIC || raceType == RaceType.SHORT }
                ?.assignments
                .orEmpty()
            if (calculatedRouteMatchesStored) {
                DesktopCourseAnalysisSection(
                    title = "Section 2: Calculated ideal route",
                    explanation = "The analyzer calculated an ideal route from the start, finish, controls, beacon, and spectator if used. The calculated ideal route matches the stored ideal route, so no separate calculated-route leg, wait, elevation-profile, or map analysis is repeated in this section. Section 3 still summarizes the route comparison.",
                    routeOrder = listOf("Calculated ideal route matches stored ideal route"),
                    routeOrderLabel = "Result",
                    summaryOnly = true,
                    comparisonLengthMeters = null,
                    comparisonLengthLabel = "Comparison length",
                    straightLineMeters = null,
                    routeLengthMeters = null,
                    climbMeters = null,
                    effectiveLengthMeters = null,
                    estimatedIdealSeconds = null,
                    legRows = emptyList(),
                    waitRows = emptyList(),
                    waitRenumbering = null,
                    ruleChecks = calculatedRuleChecks,
                    elevationProfile = emptyList(),
                    routeMap = null
                )
            } else {
                DesktopCourseAnalysisSection(
                    title = "Section 2: Calculated ideal route",
                    explanation = calculatedSectionExplanation(
                        analysis = calculatedRouteAnalysis,
                        routeCount = routeCandidate.routeCount,
                        routeCalculationNote = routeCandidate.calculationNote,
                        providedAssignments = waitRenumbering?.assignments.orEmpty(),
                        calculatedAssignments = optimizedAssignments
                    ),
                    routeOrder = calculatedRouteLabels(routeCandidate.controls),
                    routeOrderLabel = "Route order (stored fox numbering)",
                    secondaryRouteOrder = calculatedRouteLabels(routeCandidate.controls, calculatedLabelOverrides),
                    secondaryRouteOrderLabel = "Route order (calculated fox numbering)",
                    comparisonLengthMeters = calculatedRouteAnalysis?.comparisonLengthMeters?.roundToInt(),
                    comparisonLengthLabel = calculatedRouteAnalysis?.measurementLabel ?: "Unknown",
                    straightLineMeters = routeCandidate.distanceMeters.roundToInt(),
                    routeLengthMeters = calculatedRouteAnalysis?.routeLengthMeters?.roundToInt(),
                    climbMeters = calculatedRouteAnalysis?.climbMeters?.roundToInt(),
                    effectiveLengthMeters = calculatedRouteAnalysis?.effectiveLengthMeters?.roundToInt(),
                    estimatedIdealSeconds = calculatedRouteAnalysis?.estimatedSeconds?.roundToInt(),
                    legRows = calculatedLegRows,
                    waitRows = calculatedWaitRows,
                    waitRenumbering = calculatedWaitRenumbering,
                    ruleChecks = calculatedRuleChecks,
                    elevationProfile = calculatedRouteAnalysis?.elevationProfile.orEmpty(),
                    routeMap = routeMap(
                        title = "Calculated route (calculated fox numbering)",
                        start = start,
                        finish = finish,
                        controls = routeCandidate.controls,
                        labelOverrides = calculatedLabelOverrides
                    )
                )
            }
        }
        val providedSection = providedRouteAnalysis?.let { analysis ->
            DesktopCourseAnalysisSection(
                title = "Section 1: Stored route analysis",
                explanation = providedSectionExplanation(analysis),
                routeOrder = eventControlRouteLabels(providedControls),
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                comparisonLengthLabel = analysis.measurementLabel,
                straightLineMeters = providedStraightLineMeters,
                routeLengthMeters = analysis.routeLengthMeters.roundToInt(),
                climbMeters = analysis.climbMeters?.roundToInt(),
                effectiveLengthMeters = analysis.effectiveLengthMeters?.roundToInt(),
                estimatedIdealSeconds = analysis.estimatedSeconds.roundToInt(),
                legRows = providedLegRows,
                waitRows = waitRows,
                waitRenumbering = waitRenumbering,
                ruleChecks = providedRuleChecks,
                elevationProfile = analysis.elevationProfile,
                routeMap = routeMap(
                    title = "Stored route",
                    start = start,
                    finish = finish,
                    controls = providedControls.mapNotNull { control ->
                        controlsWithPoints.firstOrNull { it.control.id == control.id }
                    }
                )
            )
        }
        val metrics = goodnessMetrics(
            raceType = raceType,
            categoryName = category.name,
            routeLengthMeters = protectedCourseInfo?.lengthMeters,
            climbMeters = protectedCourseInfo?.climbMeters,
            calculatedRouteLengthMeters = calculatedRoute?.distanceMeters?.roundToInt(),
            calculatedRouteClimbMeters = calculatedRouteClimbMeters(
                start = start,
                controls = calculatedRoute?.controls.orEmpty(),
                finish = finish,
                elevationLookup = elevationLookup
            ),
            effectiveLengthMeters = protectedCourseInfo?.effectiveLengthMeters(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            waitRows = waitRows,
            idealOrderMatches = idealOrderMatches
        ) + (providedRuleChecks + calculatedRuleChecks)
            .distinctBy { "${it.label}:${it.value}" }
        val profileComparison = buildList {
            providedSection?.let {
                add(
                    DesktopCourseElevationProfileSummary(
                        title = "Stored route",
                        profile = it.elevationProfile,
                        markers = providedElevationMarkers(route, providedControls, controlsWithPoints)
                    )
                )
            }
            calculatedSection?.takeUnless { it.summaryOnly }?.let {
                add(
                    DesktopCourseElevationProfileSummary(
                        title = "Calculated route (calculated fox numbering)",
                        profile = it.elevationProfile,
                        markers = calculatedElevationMarkers(
                            start = start,
                            controls = calculatedRoute?.controls.orEmpty(),
                            finish = finish,
                            elevationLookup = elevationLookup,
                            labelOverrides = calculatedLabelOverrides
                        )
                    )
                )
            }
        }
        val routeMaps = buildList {
            providedSection?.routeMap?.let(::add)
            calculatedSection?.routeMap?.let(::add)
        }
        val kmlFolders = buildList {
            if (providedSection != null) {
                add(
                    DesktopCourseKmlExportFolder(
                        title = "Stored foxes and route",
                        routeName = "Stored route",
                        routePoints = route,
                        foxes = providedKmlFoxes(providedControls, controlsWithPoints)
                    )
                )
            }
            calculatedRoute?.takeUnless { calculatedRouteMatchesStored }?.let { routeCandidate ->
                if (calculatedSection != null && start != null && finish != null) {
                    add(
                        DesktopCourseKmlExportFolder(
                            title = "Calculated foxes and route",
                            routeName = "Calculated route",
                            routePoints = sampledCalculatedRoutePoints(
                                start = start,
                                controls = routeCandidate.controls,
                                finish = finish,
                                elevationLookup = elevationLookup
                            ),
                            foxes = calculatedKmlFoxes(routeCandidate.controls, calculatedWaitRenumbering)
                        )
                    )
                }
            }
        }
        val calculatedRouteApplication = calculatedRoute?.takeUnless { calculatedRouteMatchesStored }?.let { routeCandidate ->
            calculatedRouteApplication(
                categoryId = categoryId,
                controls = routeCandidate.controls,
                labelOverrides = calculatedLabelOverrides,
                routePoints = if (start != null && finish != null) {
                    sampledCalculatedRoutePoints(
                        start = start,
                        controls = routeCandidate.controls,
                        finish = finish,
                        elevationLookup = elevationLookup
                    )
                } else {
                    emptyList()
                },
                routeLengthMeters = calculatedRouteAnalysis?.routeLengthMeters?.roundToInt(),
                climbMeters = calculatedRouteAnalysis?.climbMeters?.roundToInt()
            )
        }
        val profileRoutePoints = if (profileComparison.any { it.profile.isNotEmpty() }) {
            kmlFolders
                .flatMap { it.routePoints }
                .distinctBy { it.coordinateKey() }
        } else {
            emptyList()
        }

        return DesktopCourseAnalysisSummary(
            categoryName = category.name,
            rulesDocumentLabel = USA_RULES_DOCUMENT_LABEL,
            providedRouteSection = providedSection,
            calculatedRouteSection = calculatedSection,
            summaryExplanation = summaryExplanation(providedSection, calculatedSection),
            profileComparison = profileComparison,
            elevationCacheNotes = elevationCacheNotes(profileRoutePoints),
            routeMaps = routeMaps,
            kmlFolders = kmlFolders,
            calculatedRouteApplication = calculatedRouteApplication,
            missingElements = missing.distinct(),
            calculatedRouteCount = calculatedRoute?.routeCount ?: 0,
            calculatedIdealOrder = calculatedRouteLabels(calculatedRoute?.controls.orEmpty(), calculatedLabelOverrides),
            providedIdealOrder = eventControlRouteLabels(providedControls),
            idealOrderMatches = idealOrderMatches,
            calculatedStraightLineMeters = calculatedRoute?.distanceMeters?.roundToInt(),
            providedStraightLineMeters = providedStraightLineMeters,
            routeLengthMeters = protectedCourseInfo?.lengthMeters,
            climbMeters = protectedCourseInfo?.climbMeters,
            effectiveLengthMeters = protectedCourseInfo?.effectiveLengthMeters(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            hasMissingElevationData = hasMissingElevationData,
            elevationProfile = elevationProfile,
            providedLegRows = providedLegRows,
            calculatedLegRows = if (calculatedRouteMatchesStored) emptyList() else calculatedLegRows,
            waitRows = waitRows,
            waitRenumbering = waitRenumbering,
            metrics = metrics
        )
    }

    @Suppress("DEPRECATION")
    private fun assignedControls(projectFile: EventProjectFile, categoryId: String): List<EventControl> {
        val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId } ?: return emptyList()
        val controlsById = projectFile.raceData.controls.associateBy { it.id }
        val categoryControlPoints = if (categoryData.controlPoints.isNotEmpty()) {
            categoryData.controlPoints
        } else {
            categoryData.publicControlIds.mapIndexedNotNull { index, controlId ->
                val control = controlsById[controlId] ?: return@mapIndexedNotNull null
                EventControlPoint(
                    id = "public-$controlId",
                    categoryId = categoryId,
                    siCode = control.siCode,
                    type = control.type,
                    order = index + 1,
                    controlId = control.id
                )
            }
        }
        return categoryControlPoints
            .map { controlPoint ->
                controlsById[controlPoint.controlId]
                    ?: EventControlCatalog.controlForDefinition(
                        projectFile.raceData.race.id,
                        ControlPointDefinition(
                            siCode = controlPoint.siCode,
                            type = controlPoint.type,
                            order = controlPoint.order
                        )
                    )
            }
            .filter {
                it.type == ControlPointType.CONTROL ||
                    it.type == ControlPointType.SEPARATOR ||
                    it.type == ControlPointType.BEACON
            }
            .distinctBy { it.id }
    }

    private fun protectedAssignedControls(
        projectFile: EventProjectFile,
        courseInfo: ProtectedCourseInfo,
        idealOrderText: String?
    ): List<EventControl> {
        val controlsById = projectFile.raceData.controls.associateBy { it.id }
        val protectedControls = courseInfo.controlPoints
            .map { protectedControl ->
                controlsById[protectedControl.controlId]
                    ?: protectedControl.toEventControl(projectFile.raceData.race.id)
            }
            .filter {
                it.type == ControlPointType.CONTROL ||
                    it.type == ControlPointType.SEPARATOR ||
                    it.type == ControlPointType.BEACON
            }
            .distinctBy { it.id }
        return idealOrderText
            ?.let { idealOrder ->
                runCatching {
                    val controlsByProtectedOrder = protectedControls.associateBy { it.id }
                    ProtectedIdealOrderRules.resolveControlIds(idealOrder, protectedControls)
                        .mapNotNull { controlsByProtectedOrder[it] }
                        .distinctBy { it.id }
                }.getOrNull()
            }
            ?.takeIf { it.isNotEmpty() }
            ?: protectedControls
    }

    private fun ProtectedCourseControlPoint.toEventControl(raceId: String): EventControl =
        EventControl(
            id = controlId,
            raceId = raceId,
            label = label,
            siCode = label.filter(Char::isDigit).toIntOrNull() ?: 0,
            type = type,
            publicLabel = label
        )

    /**
     * Section 1 analyzes the stored route. The imported route geometry is used for
     * actual length, climb, profile, and split estimates; if every route sample has elevation, the
     * comparison metric becomes effective length, defined by the referenced course-design guide as
     * length plus ten times total climb. If elevations are incomplete, the analyzer still runs and
     * falls back to horizontal route length.
     */
    private fun providedRouteAnalysis(
        route: List<CourseGeoPoint>,
        providedRoutePoints: List<CourseGeoPoint>,
        protectedCourseInfo: ProtectedCourseInfo?,
        timing: RouteTimingAnalysis
    ): RouteAnalysis {
        val routeLengthMeters = protectedCourseInfo?.lengthMeters?.toDouble() ?: route.straightLineMeters()
        val climbMeters = protectedCourseInfo?.climbMeters?.toDouble() ?: climbMetersOrNull(route)
        val hasCompleteElevation = route.all { it.elevationMeters != null } && climbMeters != null
        val effectiveLengthMeters = if (hasCompleteElevation) routeLengthMeters + 10.0 * requireNotNull(climbMeters) else null
        return RouteAnalysis(
            comparisonLengthMeters = effectiveLengthMeters ?: routeLengthMeters,
            measurementLabel = if (effectiveLengthMeters != null) "Effective length" else "Horizontal route length",
            routeLengthMeters = routeLengthMeters,
            straightLineMeters = providedRoutePoints.takeIf { it.size >= 2 }?.straightLineMeters(),
            climbMeters = climbMeters,
            effectiveLengthMeters = effectiveLengthMeters,
            estimatedSeconds = requireNotNull(timing.totalSeconds),
            elevationProfile = elevationProfile(route),
            arrivalSecondsByControlId = timing.arrivalSecondsByControlId
        )
    }

    /**
     * Section 2 constructs an independent route candidate from the known course points. Scored
     * controls and an optional spectator are permuted exhaustively, the beacon is kept as the last
     * radio point before the finish, and the lowest comparison metric wins. Complete point elevations
     * switch that metric to effective length; otherwise straight-line horizontal distance is used.
     */
    private fun calculatedRouteAnalysis(
        start: CourseGeoPoint?,
        finish: CourseGeoPoint?,
        calculatedRoute: CalculatedRoute,
        timing: RouteTimingAnalysis,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): RouteAnalysis? {
        if (start == null || finish == null) {
            return null
        }
        val points = sampledCalculatedRoutePoints(
            start = start,
            controls = calculatedRoute.controls,
            finish = finish,
            elevationLookup = elevationLookup
        )
        if (points.size < 2) {
            return null
        }
        val routeLengthMeters = points.straightLineMeters()
        val climbMeters = climbMetersOrNull(points)
        val effectiveLengthMeters = climbMeters?.let { routeLengthMeters + 10.0 * it }
        return RouteAnalysis(
            comparisonLengthMeters = effectiveLengthMeters ?: routeLengthMeters,
            measurementLabel = if (effectiveLengthMeters != null) "Effective length" else "Horizontal straight-line distance",
            routeLengthMeters = routeLengthMeters,
            straightLineMeters = routeLengthMeters,
            climbMeters = climbMeters,
            effectiveLengthMeters = effectiveLengthMeters,
            estimatedSeconds = requireNotNull(timing.totalSeconds),
            elevationProfile = elevationProfile(points),
            arrivalSecondsByControlId = timing.arrivalSecondsByControlId
        )
    }

    private fun providedSectionExplanation(analysis: RouteAnalysis): String =
        "This section analyzes the route supplied for the category. Leg lengths are taken from the imported route geometry, and estimated splits combine movement time with any Classic fox wait and find/punch time. " +
            "The primary comparison value is ${analysis.measurementLabel.lowercase()}; " +
            if (analysis.effectiveLengthMeters != null) {
                "the Elevation Cache data is complete, so effective length is calculated as route length plus ten times total climb. $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE $ELEVATION_CACHE_RESOLUTION_NOTE $MAP_KNOWLEDGE_LIMITATION_NOTE"
            } else {
                "local elevation data is incomplete, so horizontal route length is used instead of effective length. $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE $ELEVATION_CACHE_RESOLUTION_NOTE $MAP_KNOWLEDGE_LIMITATION_NOTE"
            }

    private fun calculatedSectionExplanation(
        analysis: RouteAnalysis?,
        routeCount: Int,
        routeCalculationNote: String?,
        providedAssignments: List<DesktopCourseWaitRenumberingAssignment>,
        calculatedAssignments: List<DesktopCourseWaitRenumberingAssignment>
    ): String {
        val measurement = analysis?.measurementLabel?.lowercase() ?: "the available distance metric"
        val elevationText = if (analysis?.effectiveLengthMeters != null) {
            "Complete Elevation Cache samples were available along the calculated straight-line legs, so effective length was used. $ELEVATION_CACHE_RESOLUTION_NOTE"
        } else {
            "Elevation data was incomplete along the calculated straight-line legs, so straight-line horizontal distance was used. $ELEVATION_CACHE_RESOLUTION_NOTE"
        }
        val assignmentText = assignmentDifferenceText(providedAssignments, calculatedAssignments)
        val routeCalculationText = routeCalculationNote?.let { " $it" }.orEmpty()
        val opening = if (routeCalculationNote == null) {
            "This section calculates an independent ideal route by comparing $routeCount possible orders of the foxes and any spectator point, with the beacon last before the finish."
        } else {
            "This section calculates an independent ideal route using the format-specific route search noted below; $routeCount route candidate order(s) were evaluated or generated."
        }
        return "$opening The shortest candidate by $measurement is selected.$routeCalculationText $elevationText $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE Large differences in estimated ideal time can come from fox wait-time optimization as well as route length; compare the movement, wait, and find/punch timing breakdown rows. $MAP_KNOWLEDGE_LIMITATION_NOTE $assignmentText"
    }

    private fun assignmentDifferenceText(
        providedAssignments: List<DesktopCourseWaitRenumberingAssignment>,
        calculatedAssignments: List<DesktopCourseWaitRenumberingAssignment>
    ): String {
        if (calculatedAssignments.isEmpty()) {
            return "Classic fox-assignment optimization was not applicable to this category."
        }
        val providedByControl = providedAssignments.associateBy { it.controlLabel }
        val differences = calculatedAssignments.filter { calculated ->
            providedByControl[calculated.controlLabel]?.suggestedSlotLabel != calculated.suggestedSlotLabel
        }
        return if (differences.isEmpty()) {
            "The optimized fox assignments match the stored-route assignment check."
        } else {
            "Compared with Section 1, the calculated route changes optimized assignments for " +
                differences.joinToString { "${it.controlLabel} -> ${it.suggestedSlotLabel}" } + "."
        }
    }

    private fun summaryExplanation(
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?
    ): String =
        if (providedSection != null && calculatedSection != null) {
            "This summary compares the stored route with the independently calculated candidate, including checks against the cited USA rules document, their primary distance metric, route order, estimated time, wait-time optimization, elevation profiles, and 2D point depictions."
        } else {
            "This summary reports checks against the cited USA rules document and the independently calculated route candidate because no stored ideal route was available for Section 1."
        }

    private fun calculatedRouteCandidate(
        raceType: RaceType,
        start: CourseGeoPoint?,
        finish: CourseGeoPoint?,
        foxes: List<ControlAnalysisPoint>,
        spectator: ControlAnalysisPoint?,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?,
        missing: MutableList<String>
    ): CalculatedRoute? {
        if (start == null || finish == null || foxes.isEmpty()) {
            return null
        }
        return when (raceType) {
            RaceType.SPRINT -> sprintCalculatedRoute(start, finish, foxes, spectator, beacon, elevationLookup, missing)
            RaceType.FOXORING -> foxoringCalculatedRoute(start, finish, foxes, beacon, elevationLookup)
            else -> {
                val controlsToPermute = foxes + listOfNotNull(spectator)
                if (controlsToPermute.size <= MAX_PERMUTATION_CONTROLS) {
                    shortestPermutation(start, finish, controlsToPermute, beacon, elevationLookup)
                } else {
                    missing += "Too many course controls for exhaustive route calculation: ${controlsToPermute.size}."
                    null
                }
            }
        }
    }

    private fun sprintCalculatedRoute(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        foxes: List<ControlAnalysisPoint>,
        spectator: ControlAnalysisPoint?,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?,
        missing: MutableList<String>
    ): CalculatedRoute? {
        val spectatorPoint = spectator?.point
        if (spectator == null || spectatorPoint == null) {
            missing += "Sprint loop route calculation requires a spectator control; falling back to whole-course route calculation."
            val controlsToPermute = foxes
            return if (controlsToPermute.size <= MAX_PERMUTATION_CONTROLS) {
                shortestPermutation(start, finish, controlsToPermute, beacon, elevationLookup)
            } else {
                heuristicRoute(start, finish, controlsToPermute, beacon, elevationLookup, "non-exhaustive whole-course Sprint fallback")
            }
        }
        val slowFoxes = foxes.filterNot { it.control.isSprintFastFox() }
        val fastFoxes = foxes.filter { it.control.isSprintFastFox() }
        val firstLoop = boundedLoopRoute(
            start = start,
            finish = spectatorPoint,
            controls = slowFoxes,
            elevationLookup = elevationLookup,
            note = "Sprint first loop"
        )
        val secondLoop = boundedLoopRoute(
            start = spectatorPoint,
            finish = finish,
            controls = fastFoxes,
            beacon = beacon,
            elevationLookup = elevationLookup,
            note = "Sprint fast loop"
        )
        val controls = firstLoop.controls + spectator + secondLoop.controls
        val routePoints = listOf(start) + controls.mapNotNull { it.point } + finish
        return CalculatedRoute(
            controls = controls,
            distanceMeters = routePoints.straightLineMeters(),
            routeCount = firstLoop.routeCount + secondLoop.routeCount,
            calculationNote = "Sprint route calculated as separate first and fast loops; each loop used no more than $MAX_SPRINT_LOOP_PERMUTATIONS permutations."
        )
    }

    private fun foxoringCalculatedRoute(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        foxes: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): CalculatedRoute {
        return if (foxes.size <= FOXORING_EXHAUSTIVE_CONTROLS) {
            shortestPermutation(start, finish, foxes, beacon, elevationLookup)
        } else {
            heuristicRoute(
                start = start,
                finish = finish,
                controlsToPermute = foxes,
                beacon = beacon,
                elevationLookup = elevationLookup,
                calculationNote = "Foxoring route uses non-exhaustive nearest-neighbor plus 2-opt because more than $FOXORING_EXHAUSTIVE_CONTROLS foxes are assigned."
            )
        }
    }

    private fun boundedLoopRoute(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint? = null,
        elevationLookup: (CourseGeoPoint) -> Double?,
        note: String
    ): CalculatedRoute {
        val exactCount = factorial(controls.size)
        return if (exactCount <= MAX_SPRINT_LOOP_PERMUTATIONS) {
            shortestPermutation(start, finish, controls, beacon, elevationLookup).copy(calculationNote = "$note exact")
        } else {
            heuristicRoute(start, finish, controls, beacon, elevationLookup, "$note non-exhaustive fallback")
        }
    }

    private fun shortestPermutation(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controlsToPermute: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): CalculatedRoute {
        var bestControls = emptyList<ControlAnalysisPoint>()
        var bestComparisonLength = Double.POSITIVE_INFINITY
        var bestHorizontalDistance = Double.POSITIVE_INFINITY
        var routeCount = 0
        val legSampleCache = mutableMapOf<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>()
        controlsToPermute.permutations().forEach { permutation ->
            routeCount++
            val controls = if (beacon != null) permutation + beacon else permutation
            val points = listOf(start) + controls.mapNotNull { it.point } + finish
            val horizontalDistance = points.straightLineMeters()
            val sampledPoints = sampledCalculatedRoutePoints(
                start = start,
                controls = controls,
                finish = finish,
                elevationLookup = elevationLookup,
                legSampleCache = legSampleCache
            )
            val comparisonLength = effectiveLengthMetersOrNull(sampledPoints) ?: horizontalDistance
            if (comparisonLength < bestComparisonLength) {
                bestComparisonLength = comparisonLength
                bestHorizontalDistance = horizontalDistance
                bestControls = controls
            }
        }
        return CalculatedRoute(bestControls, bestHorizontalDistance, routeCount)
    }

    private fun heuristicRoute(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controlsToPermute: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?,
        calculationNote: String
    ): CalculatedRoute {
        val ordered = nearestNeighborOrder(start, controlsToPermute)
        val improved = twoOptOrder(start, finish, ordered, beacon, elevationLookup)
        val controls = if (beacon != null) improved + beacon else improved
        val points = listOf(start) + controls.mapNotNull { it.point } + finish
        return CalculatedRoute(
            controls = controls,
            distanceMeters = points.straightLineMeters(),
            routeCount = 1,
            calculationNote = calculationNote
        )
    }

    private fun nearestNeighborOrder(start: CourseGeoPoint, controls: List<ControlAnalysisPoint>): List<ControlAnalysisPoint> {
        val remaining = controls.toMutableList()
        val ordered = mutableListOf<ControlAnalysisPoint>()
        var current = start
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { it.point?.distanceMetersTo(current) ?: Double.POSITIVE_INFINITY }
            ordered += next
            remaining -= next
            current = next.point ?: current
        }
        return ordered
    }

    private fun twoOptOrder(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): List<ControlAnalysisPoint> {
        if (controls.size < 4) {
            return controls
        }
        var best = controls
        var improved = true
        while (improved) {
            improved = false
            for (i in 0 until best.lastIndex) {
                for (k in i + 1..best.lastIndex) {
                    val candidate = best.take(i) + best.subList(i, k + 1).asReversed() + best.drop(k + 1)
                    if (routeComparisonLength(start, finish, candidate, beacon, elevationLookup) < routeComparisonLength(start, finish, best, beacon, elevationLookup)) {
                        best = candidate
                        improved = true
                    }
                }
            }
        }
        return best
    }

    private fun routeComparisonLength(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): Double {
        val allControls = if (beacon != null) controls + beacon else controls
        val sampled = sampledCalculatedRoutePoints(start, allControls, finish, elevationLookup)
        return effectiveLengthMetersOrNull(sampled) ?: (listOf(start) + allControls.mapNotNull { it.point } + finish).straightLineMeters()
    }

    private fun routeGeometryTiming(
        route: List<CourseGeoPoint>,
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>,
        raceType: RaceType,
        slotOverrides: Map<String, RenumberingSlot> = emptyMap()
    ): RouteTimingAnalysis {
        if (route.size < 2) {
            return RouteTimingAnalysis.Empty
        }
        val stops = buildList {
            add(RouteStop("S", route.first(), 0, null))
            controls.mapNotNull { control ->
                val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
                val nearestIndex = route.indices.minByOrNull { route[it].distanceMetersTo(point) } ?: return@mapNotNull null
                RouteStop(control.analysisRouteLabel(), point, nearestIndex, control)
            }
                .forEach(::add)
            add(RouteStop("F", route.last(), route.lastIndex, null))
        }.sortedBy { it.routeIndex }
        if (stops.size < 2) {
            return RouteTimingAnalysis.Empty
        }
        val legRows = mutableListOf<DesktopCourseLegRow>()
        val waitRows = mutableListOf<DesktopCourseWaitRow>()
        val arrivalSecondsByControlId = mutableMapOf<String, Int>()
        var cumulativeSeconds: Double? = 0.0
        stops.zipWithNext().forEach { (from, to) ->
            val segment = route.subList(from.routeIndex, to.routeIndex + 1)
            val lengthMeters = segment.straightLineMeters().roundToInt()
            val movementSeconds = if (from.routeIndex == to.routeIndex) {
                0.0
            } else {
                estimatedIdealSecondsDouble(segment, raceType)
            }
            val startSeconds = cumulativeSeconds
            val arrivalSeconds = if (startSeconds != null && movementSeconds != null) {
                startSeconds + movementSeconds
            } else {
                null
            }
            val service = to.control?.let { control ->
                controlServiceTiming(control, arrivalSeconds, raceType, slotOverrides[control.id])
            } ?: ControlServiceTiming.None
            if (arrivalSeconds != null && to.control != null) {
                arrivalSecondsByControlId[to.control.id] = arrivalSeconds.roundToInt()
            }
            service.waitRow?.let(waitRows::add)
            cumulativeSeconds = if (arrivalSeconds != null && service.totalSeconds != null) {
                arrivalSeconds + service.totalSeconds
            } else {
                null
            }
            val splitSeconds = if (movementSeconds != null && service.totalSeconds != null) {
                movementSeconds + service.totalSeconds
            } else {
                null
            }
            legRows += DesktopCourseLegRow(
                fromLabel = from.label,
                toLabel = to.label,
                lengthMeters = lengthMeters,
                splitSeconds = splitSeconds?.roundToInt(),
                cumulativeSeconds = cumulativeSeconds?.roundToInt(),
                waitSeconds = service.waitSeconds,
                findPunchSeconds = service.findPunchSeconds
            )
        }
        return RouteTimingAnalysis(
            legRows = legRows,
            waitRows = waitRows,
            arrivalSecondsByControlId = arrivalSecondsByControlId,
            totalSeconds = cumulativeSeconds
        )
    }

    private fun straightLineTiming(
        start: CourseGeoPoint?,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint?,
        raceType: RaceType,
        slotOverrides: Map<String, RenumberingSlot> = emptyMap(),
        labelOverrides: Map<String, String> = emptyMap(),
        elevationLookup: (CourseGeoPoint) -> Double? = { null }
    ): RouteTimingAnalysis {
        if (start == null) {
            return RouteTimingAnalysis.Empty
        }
        val stops = buildList {
            add(StraightLineStop("S", start, null))
            controls.mapNotNull { controlPoint ->
                controlPoint.point?.let { point ->
                    StraightLineStop(
                        labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel(),
                        point,
                        controlPoint.control
                    )
                }
            }.forEach(::add)
            finish?.let { add(StraightLineStop("F", it, null)) }
        }
        if (stops.size < 2) {
            return RouteTimingAnalysis.Empty
        }
        val legRows = mutableListOf<DesktopCourseLegRow>()
        val waitRows = mutableListOf<DesktopCourseWaitRow>()
        val arrivalSecondsByControlId = mutableMapOf<String, Int>()
        var cumulativeSeconds: Double? = 0.0
        stops.zipWithNext().forEach { (from, to) ->
            val legPoints = sampledLegPoints(from.point, to.point, elevationLookup)
            val lengthMeters = legPoints.straightLineMeters().roundToInt()
            val movementSeconds = estimatedIdealSecondsDouble(legPoints, raceType) ?: 0.0
            val startSeconds = cumulativeSeconds
            val arrivalSeconds = if (startSeconds != null) {
                startSeconds + movementSeconds
            } else {
                null
            }
            val service = to.control?.let { control ->
                controlServiceTiming(
                    control = control,
                    arrivalSeconds = arrivalSeconds,
                    raceType = raceType,
                    slotOverride = slotOverrides[control.id],
                    labelOverride = labelOverrides[control.id]
                )
            } ?: ControlServiceTiming.None
            if (arrivalSeconds != null && to.control != null) {
                arrivalSecondsByControlId[to.control.id] = arrivalSeconds.roundToInt()
            }
            service.waitRow?.let(waitRows::add)
            cumulativeSeconds = if (arrivalSeconds != null && service.totalSeconds != null) {
                arrivalSeconds + service.totalSeconds
            } else {
                null
            }
            val splitSeconds = if (service.totalSeconds != null) {
                movementSeconds + service.totalSeconds
            } else {
                null
            }
            legRows += DesktopCourseLegRow(
                fromLabel = from.label,
                toLabel = to.label,
                lengthMeters = lengthMeters,
                splitSeconds = splitSeconds?.roundToInt(),
                cumulativeSeconds = cumulativeSeconds?.roundToInt(),
                waitSeconds = service.waitSeconds,
                findPunchSeconds = service.findPunchSeconds
            )
        }
        return RouteTimingAnalysis(
            legRows = legRows,
            waitRows = waitRows,
            arrivalSecondsByControlId = arrivalSecondsByControlId,
            totalSeconds = cumulativeSeconds
        )
    }

    private fun calculatedRouteClimbMeters(
        start: CourseGeoPoint?,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): Int? {
        if (start == null || finish == null) {
            return null
        }
        val points = sampledCalculatedRoutePoints(
            start = start,
            controls = controls,
            finish = finish,
            elevationLookup = elevationLookup
        )
        if (points.size < 2 || points.any { it.elevationMeters == null }) {
            return null
        }
        return points.zipWithNext()
            .sumOf { (from, to) -> max(0.0, requireNotNull(to.elevationMeters) - requireNotNull(from.elevationMeters)) }
            .roundToInt()
    }

    /**
     * The calculated route has no imported track geometry, so the analyzer samples each straight
     * leg at a fixed spacing and applies the local Elevation Data cache to those samples. Endpoint
     * elevation interpolation is retained as a fallback when cache data is absent.
     */
    private fun sampledCalculatedRoutePoints(
        start: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint,
        elevationLookup: (CourseGeoPoint) -> Double?,
        legSampleCache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>? = null
    ): List<CourseGeoPoint> {
        val stops = buildList {
            add(start)
            controls.mapNotNull { it.point }.forEach(::add)
            add(finish)
        }
        if (stops.size < 2) {
            return stops.map { it.withCachedElevation(elevationLookup) }
        }
        val sampled = mutableListOf<CourseGeoPoint>()
        stops.zipWithNext().forEach { (from, to) ->
            val legPoints = legSampleCache?.getOrPut(from to to) {
                sampledLegPoints(from, to, elevationLookup)
            } ?: sampledLegPoints(from, to, elevationLookup)
            if (sampled.isEmpty()) {
                sampled += legPoints
            } else {
                sampled += legPoints.drop(1)
            }
        }
        return sampled
    }

    private fun sampledLegPoints(
        start: CourseGeoPoint,
        end: CourseGeoPoint,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): List<CourseGeoPoint> {
        val intervals = max(
            1,
            ceil(start.distanceMetersTo(end) / CALCULATED_ROUTE_SAMPLE_METERS).toInt()
        )
        return (0..intervals).map { index ->
            start.interpolate(end, index.toDouble() / intervals.toDouble())
                .withCachedElevation(elevationLookup)
        }
    }

    private fun CourseGeoPoint.withCachedElevation(elevationLookup: (CourseGeoPoint) -> Double?): CourseGeoPoint =
        copy(elevationMeters = elevationLookup(this) ?: elevationMeters)

    private fun calculatedRouteLabels(
        controls: List<ControlAnalysisPoint>,
        labelOverrides: Map<String, String> = emptyMap()
    ): List<String> =
        listOf("S") + controls.map { labelOverrides[it.control.id] ?: it.control.analysisRouteLabel() }

    private fun eventControlRouteLabels(controls: List<EventControl>): List<String> =
        listOf("S") + controls.map { it.analysisRouteLabel() }

    private fun estimatedIdealSecondsDouble(route: List<CourseGeoPoint>, raceType: RaceType): Double? {
        if (route.size < 2) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) -> segmentSeconds(start, end, raceType) }
    }

    private fun elevationProfile(route: List<CourseGeoPoint>): List<DesktopCourseElevationProfilePoint> {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return emptyList()
        }
        var distanceMeters = 0.0
        return route.mapIndexed { index, point ->
            if (index > 0) {
                distanceMeters += route[index - 1].distanceMetersTo(point)
            }
            DesktopCourseElevationProfilePoint(
                distanceMeters = distanceMeters.roundToInt(),
                elevationMeters = requireNotNull(point.elevationMeters)
            )
        }
    }

    private fun providedElevationMarkers(
        route: List<CourseGeoPoint>,
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>
    ): List<DesktopCourseElevationProfileMarker> {
        val profile = elevationProfile(route)
        if (profile.isEmpty()) {
            return emptyList()
        }
        return controls
            .filter { it.type == ControlPointType.CONTROL }
            .mapNotNull { control ->
                val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
                val nearestIndex = route.indices.minByOrNull { route[it].distanceMetersTo(point) } ?: return@mapNotNull null
                val profilePoint = profile.getOrNull(nearestIndex) ?: return@mapNotNull null
                DesktopCourseElevationProfileMarker(
                    label = control.analysisRouteLabel(),
                    distanceMeters = profilePoint.distanceMeters,
                    elevationMeters = profilePoint.elevationMeters
                )
            }
    }

    private fun calculatedElevationMarkers(
        start: CourseGeoPoint?,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?,
        labelOverrides: Map<String, String> = emptyMap()
    ): List<DesktopCourseElevationProfileMarker> {
        if (start == null || finish == null) {
            return emptyList()
        }
        val stops = buildList {
            add(StraightLineStop("S", start, null))
            controls.mapNotNull { controlPoint ->
                controlPoint.point?.let { point ->
                    StraightLineStop(
                        labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel(),
                        point,
                        controlPoint.control
                    )
                }
            }.forEach(::add)
            add(StraightLineStop("F", finish, null))
        }
        if (stops.size < 2) {
            return emptyList()
        }
        val markers = mutableListOf<DesktopCourseElevationProfileMarker>()
        var distanceMeters = 0.0
        stops.zipWithNext().forEach { (from, to) ->
            val legPoints = sampledLegPoints(from.point, to.point, elevationLookup)
            distanceMeters += legPoints.straightLineMeters()
            val markerPoint = legPoints.lastOrNull()
            if (to.control?.type == ControlPointType.CONTROL && markerPoint?.elevationMeters != null) {
                markers += DesktopCourseElevationProfileMarker(
                    label = to.label,
                    distanceMeters = distanceMeters.roundToInt(),
                    elevationMeters = requireNotNull(markerPoint.elevationMeters)
                )
            }
        }
        return markers
    }

    private fun providedKmlFoxes(
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>
    ): List<DesktopCourseKmlExportPoint> =
        controls
            .filter { it.type == ControlPointType.CONTROL }
            .mapNotNull { control ->
                val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
                DesktopCourseKmlExportPoint(
                    label = control.analysisRouteLabel(),
                    originalLabel = null,
                    point = point
                )
            }

    private fun calculatedKmlFoxes(
        controls: List<ControlAnalysisPoint>,
        renumbering: DesktopCourseWaitRenumbering?
    ): List<DesktopCourseKmlExportPoint> {
        val assignmentsByControlLabel = renumbering
            ?.assignments
            .orEmpty()
            .associateBy { it.controlLabel }
        return controls
            .filter { it.control.type == ControlPointType.CONTROL }
            .mapNotNull { controlPoint ->
                val point = controlPoint.point ?: return@mapNotNull null
                val originalLabel = controlPoint.control.analysisRouteLabel()
                val suggestedLabel = assignmentsByControlLabel[controlPoint.control.publicDisplayLabel()]?.suggestedSlotLabel
                    ?.takeIf { it.isNotBlank() }
                DesktopCourseKmlExportPoint(
                    label = suggestedLabel ?: originalLabel,
                    originalLabel = originalLabel.takeIf { suggestedLabel != null && suggestedLabel != originalLabel },
                    point = point
                )
            }
    }

    private fun calculatedLabelOverrides(
        controls: List<ControlAnalysisPoint>,
        renumbering: DesktopCourseWaitRenumbering?
    ): Map<String, String> {
        val assignmentsByControlLabel = renumbering
            ?.assignments
            .orEmpty()
            .associateBy { it.controlLabel }
        return controls.mapNotNull { controlPoint ->
            if (controlPoint.control.type != ControlPointType.CONTROL) {
                return@mapNotNull null
            }
            val suggestedLabel = assignmentsByControlLabel[controlPoint.control.publicDisplayLabel()]?.suggestedSlotLabel
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            controlPoint.control.id to suggestedLabel
        }.toMap()
    }

    private fun calculatedRouteApplication(
        categoryId: String,
        controls: List<ControlAnalysisPoint>,
        labelOverrides: Map<String, String>,
        routePoints: List<CourseGeoPoint>,
        routeLengthMeters: Int?,
        climbMeters: Int?
    ): DesktopCourseCalculatedRouteApplication {
        val idealOrderTokens = controls.map { controlPoint ->
            controlPoint.control.idealOrderToken(labelOverrides[controlPoint.control.id])
        }
        return DesktopCourseCalculatedRouteApplication(
            categoryId = categoryId,
            idealOrderText = idealOrderTokens.joinToString(" "),
            routePoints = routePoints,
            routeLengthMeters = routeLengthMeters,
            climbMeters = climbMeters,
            foxAssignments = controls
                .filter { it.control.type == ControlPointType.CONTROL }
                .map { controlPoint ->
                    DesktopCourseCalculatedFoxAssignment(
                        controlId = controlPoint.control.id,
                        originalLabel = controlPoint.control.analysisRouteLabel(),
                        calculatedLabel = labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel()
                    )
                }
        )
    }

    private fun climbMetersOrNull(route: List<CourseGeoPoint>): Double? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) -> max(0.0, requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters)) }
    }

    private fun effectiveLengthMetersOrNull(route: List<CourseGeoPoint>): Double? {
        val climbMeters = climbMetersOrNull(route) ?: return null
        return route.straightLineMeters() + 10.0 * climbMeters
    }

    private fun routeMap(
        title: String,
        start: CourseGeoPoint?,
        finish: CourseGeoPoint?,
        controls: List<ControlAnalysisPoint>,
        labelOverrides: Map<String, String> = emptyMap()
    ): DesktopCourseRouteMap? {
        val labeledPoints = buildList {
            start?.let { add(RouteMapSourcePoint("S", it, DesktopCourseRouteMapPointType.Start)) }
            controls.forEach { controlPoint ->
                val point = controlPoint.point ?: return@forEach
                add(
                    RouteMapSourcePoint(
                        labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel(),
                        point,
                        controlPoint.control.routeMapType()
                    )
                )
            }
            finish?.let { add(RouteMapSourcePoint("F", it, DesktopCourseRouteMapPointType.Finish)) }
        }
        if (labeledPoints.size < 2) {
            return null
        }
        val minLatitude = labeledPoints.minOf { it.point.latitude }
        val maxLatitude = labeledPoints.maxOf { it.point.latitude }
        val minLongitude = labeledPoints.minOf { it.point.longitude }
        val maxLongitude = labeledPoints.maxOf { it.point.longitude }
        val latitudeRange = max(0.000001, maxLatitude - minLatitude)
        val longitudeRange = max(0.000001, maxLongitude - minLongitude)
        return DesktopCourseRouteMap(
            title = title,
            points = labeledPoints.map { source ->
                DesktopCourseRouteMapPoint(
                    label = source.label,
                    xFraction = (source.point.longitude - minLongitude) / longitudeRange,
                    yFraction = (maxLatitude - source.point.latitude) / latitudeRange,
                    type = source.type
                )
            },
            routeLabels = labeledPoints.map { it.label }
        )
    }

    private fun segmentSeconds(start: CourseGeoPoint, end: CourseGeoPoint, raceType: RaceType): Double {
        val horizontal = max(1.0, start.distanceMetersTo(end))
        val climb = if (start.elevationMeters != null && end.elevationMeters != null) {
            max(0.0, requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters))
        } else {
            0.0
        }
        val movementMeters = horizontal + 10.0 * climb
        val flatSpeed = when (raceType) {
            RaceType.SPRINT -> SPRINT_FLAT_SPEED_MPS
            RaceType.FOXORING -> FOXORING_FLAT_SPEED_MPS
            else -> CLASSIC_FLAT_SPEED_MPS
        }
        return movementMeters / flatSpeed
    }

    private fun controlServiceTiming(
        control: EventControl,
        arrivalSeconds: Double?,
        raceType: RaceType,
        slotOverride: RenumberingSlot?,
        labelOverride: String? = null
    ): ControlServiceTiming {
        if (!isClassicStyle(raceType) || control.type != ControlPointType.CONTROL || arrivalSeconds == null) {
            return ControlServiceTiming.None
        }
        val slotIndex = slotOverride?.slotIndex ?: classicSlotIndex(control) ?: return ControlServiceTiming.Unknown
        val slotLabel = slotOverride?.slotLabel ?: classicSlotLabel(control)
        val roundedArrival = arrivalSeconds.roundToInt()
        val waitSeconds = waitSecondsForClassicSlot(slotIndex, roundedArrival)
        return ControlServiceTiming(
            waitSeconds = waitSeconds,
            findPunchSeconds = CLASSIC_CONTROL_FIND_PUNCH_SECONDS,
            totalSeconds = waitSeconds + CLASSIC_CONTROL_FIND_PUNCH_SECONDS.toDouble(),
            waitRow = DesktopCourseWaitRow(
                controlId = control.id,
                controlLabel = labelOverride ?: control.publicDisplayLabel(),
                arrivalSeconds = roundedArrival,
                waitSeconds = waitSeconds,
                slotLabel = slotLabel
            )
        )
    }

    private fun isClassicStyle(raceType: RaceType): Boolean =
        raceType == RaceType.CLASSIC || raceType == RaceType.SHORT

    private fun waitSecondsForClassicSlot(slotIndex: Int, arrivalSeconds: Int): Int {
        val slotStart = slotIndex * CLASSIC_TRANSMIT_SLOT_SECONDS
        val phase = arrivalSeconds.floorMod(CLASSIC_TRANSMIT_CYCLE_SECONDS)
        return when {
            phase < slotStart -> slotStart - phase
            phase < slotStart + CLASSIC_TRANSMIT_SLOT_SECONDS -> 0
            else -> CLASSIC_TRANSMIT_CYCLE_SECONDS - phase + slotStart
        }
    }

    private fun classicSlotIndex(control: EventControl): Int? {
        val numericLabel = listOf(control.publicLabel, control.label)
            .firstNotNullOfOrNull { label -> label?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }?.toIntOrNull() }
        numericLabel?.let { label ->
            when (label) {
                in 1..5 -> return label - 1
                in 31..35 -> return label - 31
                in 41..45 -> return label - 41
            }
        }
        return when (control.siCode) {
            in 31..35 -> control.siCode - 31
            in 41..45 -> control.siCode - 41
            else -> null
        }
    }

    private fun classicSlotLabel(control: EventControl): String? {
        classicSlotIndex(control) ?: return null
        return listOfNotNull(control.publicLabel, control.label)
            .firstOrNull { classicSlotIndexForLabel(it) != null }
    }

    private fun classicSlotIndexForLabel(label: String): Int? {
        val number = label.filter(Char::isDigit).takeIf { it.isNotBlank() }?.toIntOrNull() ?: return null
        return when (number) {
            in 1..5 -> number - 1
            in 31..35 -> number - 31
            in 41..45 -> number - 41
            else -> null
        }
    }

    private fun EventControl.isSprintFastFox(): Boolean =
        type == ControlPointType.CONTROL &&
            (siCode in 41..45 || label.trim().uppercase().startsWith("F") || publicLabel.orEmpty().trim().uppercase().startsWith("F"))

    private fun factorial(value: Int): Int =
        if (value <= 1) 1 else (2..value).fold(1) { acc, next -> acc * next }

    private fun waitRenumbering(
        providedControls: List<EventControl>,
        timingForSlots: (Map<String, RenumberingSlot>) -> RouteTimingAnalysis
    ): DesktopCourseWaitRenumbering? {
        val foxes = providedControls
            .filter { it.type == ControlPointType.CONTROL }
            .mapNotNull { control ->
                val slotIndex = classicSlotIndex(control) ?: return@mapNotNull null
                RenumberingFox(control, slotIndex, classicSlotLabel(control) ?: control.publicDisplayLabel())
            }
        if (foxes.size < 2 || foxes.size > MAX_PERMUTATION_CONTROLS) {
            return null
        }
        val currentTotal = timingForSlots(emptyMap()).waitTotalFor(foxes)
        var bestTotal = Int.MAX_VALUE
        var bestSlots = emptyList<RenumberingSlot>()
        val currentSlots = foxes.map { RenumberingSlot(it.currentSlotIndex, it.currentSlotLabel) }
        currentSlots.permutations().forEach { candidateSlots ->
            val slotOverrides = foxes.zip(candidateSlots).associate { (fox, slot) -> fox.control.id to slot }
            val total = timingForSlots(slotOverrides).waitTotalFor(foxes)
            if (total < bestTotal) {
                bestTotal = total
                bestSlots = candidateSlots
            }
        }
        if (bestSlots.isEmpty()) {
            return null
        }
        return DesktopCourseWaitRenumbering(
            currentTotalWaitSeconds = currentTotal,
            bestTotalWaitSeconds = bestTotal,
            improvesWait = bestTotal < currentTotal,
            assignments = foxes.zip(bestSlots).map { (fox, slot) ->
                DesktopCourseWaitRenumberingAssignment(
                    controlId = fox.control.id,
                    controlLabel = fox.control.publicDisplayLabel(),
                    currentSlotLabel = fox.currentSlotLabel,
                    suggestedSlotLabel = slot.slotLabel
                )
            }
        )
    }

    private fun slotOverridesFromAssignments(
        controls: List<EventControl>,
        renumbering: DesktopCourseWaitRenumbering?
    ): Map<String, RenumberingSlot> {
        val assignmentByLabel = renumbering?.assignments.orEmpty().associateBy { it.controlLabel }
        return controls.mapNotNull { control ->
            val assignment = assignmentByLabel[control.publicDisplayLabel()] ?: return@mapNotNull null
            val slotIndex = classicSlotIndexForLabel(assignment.suggestedSlotLabel) ?: return@mapNotNull null
            control.id to RenumberingSlot(slotIndex, assignment.suggestedSlotLabel)
        }.toMap()
    }

    private fun RouteTimingAnalysis.waitTotalFor(foxes: List<RenumberingFox>): Int {
        val foxIds = foxes.map { it.control.id }.toSet()
        return waitRows
            .filter { it.controlId in foxIds }
            .sumOf { it.waitSeconds }
    }

    private fun routeRuleChecks(
        routeLabel: String,
        raceType: RaceType,
        categoryName: String,
        foxCount: Int,
        comparisonLengthMeters: Int?,
        measurementLabel: String,
        estimatedSeconds: Int?
    ): List<DesktopCourseGoodnessMetric> {
        val requirement = categoryRequirement(categoryName, raceType)
        val categoryKey = categoryRuleKey(categoryName)
        return buildList {
            if (requirement == null) {
                add(
                    DesktopCourseGoodnessMetric(
                        "$routeLabel USA category requirements",
                        "Unknown (category ${categoryKey ?: categoryName} is not in the cited USA rules table)",
                        DesktopCourseMetricStatus.Unknown
                    )
                )
            } else {
                if (categoryKey != null && !categoryNameContainsRuleKey(categoryName, categoryKey)) {
                    add(
                        DesktopCourseGoodnessMetric(
                            "$routeLabel USA category name",
                            "Using $categoryKey rules for category \"$categoryName\" (USA rules table names this category $categoryKey)",
                            DesktopCourseMetricStatus.Warning
                        )
                    )
                }
                add(
                    DesktopCourseGoodnessMetric(
                        "$routeLabel fox count",
                        "$foxCount foxes (required ${requirement.controlRangeText()} for ${categoryKey ?: categoryName})",
                        if (foxCount in requirement.minControls..requirement.maxControls) DesktopCourseMetricStatus.Good else DesktopCourseMetricStatus.Warning
                    )
                )
                when (raceType) {
                    RaceType.SPRINT -> add(
                        DesktopCourseGoodnessMetric(
                            "$routeLabel Sprint target time",
                            estimatedSeconds?.let { "${compactDurationText(it)} (target approximately ${compactDurationText(SPRINT_TARGET_SECONDS)})" }
                                ?: "Unknown (target approximately ${compactDurationText(SPRINT_TARGET_SECONDS)})",
                            when {
                                estimatedSeconds == null -> DesktopCourseMetricStatus.Unknown
                                abs(estimatedSeconds - SPRINT_TARGET_SECONDS) <= SPRINT_TARGET_SECONDS * 0.15 -> DesktopCourseMetricStatus.Good
                                else -> DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                    RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> add(
                        DesktopCourseGoodnessMetric(
                            "$routeLabel course length",
                            comparisonLengthMeters?.let {
                                "${twoDecimals(it / 1000.0)} km $measurementLabel (required ${requirement.lengthRangeText()})"
                            } ?: "Unknown (required ${requirement.lengthRangeText()})",
                            when {
                                comparisonLengthMeters == null -> DesktopCourseMetricStatus.Unknown
                                comparisonLengthMeters in requirement.minLengthMeters..requirement.maxLengthMeters -> DesktopCourseMetricStatus.Good
                                else -> DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                    RaceType.ORIENTEERING -> Unit
                }
            }
        }
    }

    private fun coursePointRuleChecks(
        raceType: RaceType,
        categoryName: String,
        start: CourseGeoPoint?,
        foxes: List<ControlAnalysisPoint>,
        spectator: ControlAnalysisPoint?,
        beacon: ControlAnalysisPoint?
    ): List<DesktopCourseGoodnessMetric> {
        val spacing = spacingRuleSet(raceType, categoryName) ?: return emptyList()
        val startChecks = spacing.startCheckedPoints(foxes, beacon)
        val pairChecks = spacing.pairCheckedPoints(foxes, spectator, beacon)
        return listOf(
            spacingMetric(
                label = "${spacing.formatLabel} start exclusion zone",
                requiredMeters = spacing.startMinMeters,
                distances = startChecks.mapNotNull { point ->
                    val startPoint = start ?: return@mapNotNull null
                    point.label to startPoint.distanceMetersTo(point.point)
                }
            ),
            spacingMetric(
                label = "${spacing.formatLabel} minimum transmitter spacing",
                requiredMeters = spacing.pairMinMeters,
                distances = pairChecks.flatMapIndexed { index, first ->
                    pairChecks.drop(index + 1).map { second ->
                        "${first.label}-${second.label}" to first.point.distanceMetersTo(second.point)
                    }
                }
            )
        )
    }

    private fun spacingMetric(
        label: String,
        requiredMeters: Int,
        distances: List<Pair<String, Double>>
    ): DesktopCourseGoodnessMetric {
        if (distances.isEmpty()) {
            return DesktopCourseGoodnessMetric(label, "Unknown (required at least $requiredMeters m)", DesktopCourseMetricStatus.Unknown)
        }
        val shortest = distances.minBy { it.second }
        val status = if (shortest.second + 0.5 >= requiredMeters) DesktopCourseMetricStatus.Good else DesktopCourseMetricStatus.Warning
        val prefix = if (status == DesktopCourseMetricStatus.Good) "OK" else "Violation"
        val comparedItem = if (label.contains("transmitter", ignoreCase = true)) {
            "closest pair"
        } else if (label.contains("start exclusion zone", ignoreCase = true)) {
            "nearest transmitter"
        } else {
            "nearest checked point"
        }
        return DesktopCourseGoodnessMetric(
            label = label,
            value = "$prefix: $comparedItem ${shortest.first} ${shortest.second.roundToInt()} m (required at least $requiredMeters m)",
            status = status
        )
    }

    private fun categoryRequirement(categoryName: String, raceType: RaceType): CourseRuleRequirement? {
        val key = categoryRuleKey(categoryName) ?: return null
        return when (raceType) {
            RaceType.FOXORING -> foxoringCategoryRequirements[key]
            RaceType.SPRINT -> classicCategoryRequirements[key]?.let {
                it.copy(minControls = it.minControls * 2, maxControls = it.maxControls * 2)
            }
            RaceType.CLASSIC, RaceType.SHORT -> youthClassicCategoryRequirements[key] ?: classicCategoryRequirements[key]
            RaceType.ORIENTEERING -> null
        }
    }

    private fun spacingRuleSet(raceType: RaceType, categoryName: String): CourseSpacingRuleSet? {
        val key = categoryRuleKey(categoryName)
        return when (raceType) {
            RaceType.SPRINT -> CourseSpacingRuleSet(
                "Sprint",
                100,
                100,
                includeBeaconInStartCheck = false,
                includeSpectatorInPairCheck = true,
                includeBeaconInPairCheck = true
            )
            RaceType.FOXORING -> CourseSpacingRuleSet(
                "Foxoring",
                250,
                250,
                includeBeaconInStartCheck = true,
                includeSpectatorInPairCheck = false,
                includeBeaconInPairCheck = true
            )
            RaceType.CLASSIC, RaceType.SHORT -> CourseSpacingRuleSet(
                formatLabel = if (key in youthClassicCategoryRequirements) "Youth Classic" else "Classic",
                startMinMeters = if (key in youthClassicCategoryRequirements) 500 else 750,
                pairMinMeters = 400,
                includeBeaconInStartCheck = true,
                includeSpectatorInPairCheck = false,
                includeBeaconInPairCheck = true
            )
            RaceType.ORIENTEERING -> null
        }
    }

    private fun CourseSpacingRuleSet.startCheckedPoints(
        foxes: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?
    ): List<LabeledCoursePoint> =
        buildList {
            foxes.mapNotNullTo(this) { it.labeledPoint() }
            if (includeBeaconInStartCheck) {
                beacon?.labeledPoint()?.let(::add)
            }
        }

    private fun CourseSpacingRuleSet.pairCheckedPoints(
        foxes: List<ControlAnalysisPoint>,
        spectator: ControlAnalysisPoint?,
        beacon: ControlAnalysisPoint?
    ): List<LabeledCoursePoint> =
        buildList {
            foxes.mapNotNullTo(this) { it.labeledPoint() }
            if (includeSpectatorInPairCheck) {
                spectator?.labeledPoint()?.let(::add)
            }
            if (includeBeaconInPairCheck) {
                beacon?.labeledPoint()?.let(::add)
            }
        }

    private fun ControlAnalysisPoint.labeledPoint(): LabeledCoursePoint? =
        point?.let { LabeledCoursePoint(control.analysisRouteLabel(), it) }

    private fun categoryRuleKey(categoryName: String): String? {
        val rawKey = Regex("""\b[WMD][\s_-]*\d{2}\b""")
            .find(categoryName.uppercase())
            ?.value
            ?: return null
        val compactKey = rawKey.filter { it.isLetterOrDigit() }
        return if (compactKey.startsWith("D")) "W${compactKey.drop(1)}" else compactKey
    }

    private fun categoryNameContainsRuleKey(categoryName: String, categoryKey: String): Boolean =
        Regex("""\b${Regex.escape(categoryKey)}\b""")
            .containsMatchIn(categoryName.uppercase())

    private fun goodnessMetrics(
        raceType: RaceType,
        categoryName: String,
        routeLengthMeters: Int?,
        climbMeters: Int?,
        calculatedRouteLengthMeters: Int?,
        calculatedRouteClimbMeters: Int?,
        effectiveLengthMeters: Int?,
        estimatedIdealSeconds: Int?,
        waitRows: List<DesktopCourseWaitRow>,
        idealOrderMatches: Boolean?
    ): List<DesktopCourseGoodnessMetric> {
        val targetSeconds = when (raceType) {
            RaceType.SPRINT -> SPRINT_TARGET_SECONDS
            RaceType.FOXORING -> FOXORING_TARGET_SECONDS
            else -> CLASSIC_TARGET_SECONDS
        }
        val lengthRequirement = categoryRequirement(categoryName, raceType)
            ?.takeIf { raceType == RaceType.CLASSIC || raceType == RaceType.SHORT || raceType == RaceType.FOXORING }
        return buildList {
            add(
                DesktopCourseGoodnessMetric(
                    "Calculated route agrees with stored ideal order",
                    idealOrderMatches?.let { if (it) "Yes" else "No" } ?: "Unknown",
                    when (idealOrderMatches) {
                        true -> DesktopCourseMetricStatus.Good
                        false -> DesktopCourseMetricStatus.Warning
                        null -> DesktopCourseMetricStatus.Unknown
                    }
                )
            )
            val calculatedClimbPercent = if (
                calculatedRouteLengthMeters != null &&
                calculatedRouteLengthMeters > 0 &&
                calculatedRouteClimbMeters != null
            ) {
                calculatedRouteClimbMeters.toDouble() / calculatedRouteLengthMeters.toDouble() * 100.0
            } else {
                null
            }
            val storedClimbPercent = if (routeLengthMeters != null && routeLengthMeters > 0 && climbMeters != null) {
                climbMeters.toDouble() / routeLengthMeters.toDouble() * 100.0
            } else {
                null
            }
            val climbMetric = if (calculatedClimbPercent != null && calculatedRouteLengthMeters != null && calculatedRouteClimbMeters != null) {
                Triple(calculatedClimbPercent, calculatedRouteLengthMeters, calculatedRouteClimbMeters)
            } else if (storedClimbPercent != null && routeLengthMeters != null && climbMeters != null) {
                Triple(storedClimbPercent, routeLengthMeters, climbMeters)
            } else {
                null
            }
            val appliesClimbLimit = raceType == RaceType.CLASSIC || raceType == RaceType.SHORT
            add(
                DesktopCourseGoodnessMetric(
                    "Climb percent of route length",
                    climbMetric?.let { (percent, lengthMeters, climbValueMeters) ->
                        val lengthKm = lengthMeters.toDouble() / 1000.0
                        val result = "$climbValueMeters m / ${twoDecimals(lengthKm)} km = ${oneDecimal(percent)}%"
                        if (appliesClimbLimit) "$result (limit 6.0%)" else result
                    } ?: if (appliesClimbLimit) "Unknown (limit 6.0%)" else "Unknown",
                    when {
                        climbMetric == null -> DesktopCourseMetricStatus.Unknown
                        appliesClimbLimit && climbMetric.first > 6.0 -> DesktopCourseMetricStatus.Warning
                        else -> DesktopCourseMetricStatus.Good
                    }
                )
            )
            add(
                DesktopCourseGoodnessMetric(
                    "Effective length",
                    effectiveLengthMeters?.let { effectiveLength ->
                        val measured = "${twoDecimals(effectiveLength / 1000.0)} km"
                        if (lengthRequirement == null) {
                            measured
                        } else {
                            "$measured (required ${lengthRequirement.lengthRangeText()})"
                        }
                    } ?: lengthRequirement?.let { "Unknown (required ${it.lengthRangeText()})" } ?: "Unknown",
                    when {
                        effectiveLengthMeters == null -> DesktopCourseMetricStatus.Unknown
                        lengthRequirement == null -> DesktopCourseMetricStatus.Good
                        effectiveLengthMeters in lengthRequirement.minLengthMeters..lengthRequirement.maxLengthMeters -> DesktopCourseMetricStatus.Good
                        else -> DesktopCourseMetricStatus.Warning
                    }
                )
            )
            val totalWait = waitRows.sumOf { it.waitSeconds }
            add(
                DesktopCourseGoodnessMetric(
                    "Total ideal-route wait time",
                    if (waitRows.isEmpty()) "Unknown" else compactDurationText(totalWait),
                    when {
                        waitRows.isEmpty() -> DesktopCourseMetricStatus.Unknown
                        totalWait == 0 -> DesktopCourseMetricStatus.Good
                        else -> DesktopCourseMetricStatus.Warning
                    }
                )
            )
            add(
                DesktopCourseGoodnessMetric(
                    "Challenge vs target winning time",
                    estimatedIdealSeconds?.let {
                        "${compactDurationText(it)} / ${compactDurationText(targetSeconds)}"
                    } ?: "Unknown",
                    if (estimatedIdealSeconds == null) {
                        DesktopCourseMetricStatus.Unknown
                    } else if (abs(estimatedIdealSeconds - targetSeconds) <= targetSeconds * 0.15) {
                        DesktopCourseMetricStatus.Good
                    } else {
                        DesktopCourseMetricStatus.Warning
                    }
                )
            )
        }
    }

    private fun List<CourseGeoPoint>.straightLineMeters(): Double =
        zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    private fun <T> List<T>.permutations(): Sequence<List<T>> =
        sequence {
            if (size <= 1) {
                yield(this@permutations)
            } else {
                for (index in indices) {
                    val item = this@permutations[index]
                    val rest = this@permutations.filterIndexed { restIndex, _ -> restIndex != index }
                    rest.permutations().forEach { yield(listOf(item) + it) }
                }
            }
        }

    private fun Int.floorMod(divisor: Int): Int =
        ((this % divisor) + divisor) % divisor

    private fun ProtectedCourseControlPoint.toGeoPoint(): CourseGeoPoint =
        CourseGeoPoint(latitude, longitude, elevationMeters)

    private fun ProtectedCourseObjectPoint.toGeoPoint(): CourseGeoPoint =
        CourseGeoPoint(latitude, longitude, elevationMeters)

    private fun EventControl.publicDisplayLabel(): String =
        publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label

    private fun EventControl.analysisRouteLabel(): String =
        when (type) {
            ControlPointType.BEACON -> "B"
            ControlPointType.SEPARATOR -> publicDisplayLabel().takeIf { it.isNotBlank() } ?: "Spectator"
            else -> publicDisplayLabel()
        }

    private fun EventControl.idealOrderToken(labelOverride: String?): String =
        quoteIdealOrderToken(labelOverride?.takeIf { it.isNotBlank() } ?: publicDisplayLabel())

    private fun quoteIdealOrderToken(token: String): String {
        val trimmedToken = token.trim()
        val needsQuoting = trimmedToken.any { it.isWhitespace() || it == ',' || it == ';' }
        if (!needsQuoting) {
            return trimmedToken
        }
        return when {
            '\'' !in trimmedToken -> "'$trimmedToken'"
            '"' !in trimmedToken -> "\"$trimmedToken\""
            else -> trimmedToken
        }
    }

    private fun EventControl.routeMapType(): DesktopCourseRouteMapPointType =
        when (type) {
            ControlPointType.CONTROL -> DesktopCourseRouteMapPointType.Control
            ControlPointType.BEACON -> DesktopCourseRouteMapPointType.Beacon
            ControlPointType.SEPARATOR -> DesktopCourseRouteMapPointType.Spectator
        }

    private fun protectedCoordinateLookup(courseInfo: ProtectedCourseInfo?): ProtectedCoordinateLookup {
        if (courseInfo == null) {
            return ProtectedCoordinateLookup(
                pointsByToken = emptyMap(),
                singleBeaconPoint = null,
                singleSpectatorPoint = null
            )
        }
        val typedPoints = buildList {
            courseInfo.controlPoints.forEach { controlPoint ->
                add(
                    ProtectedCoordinateCandidate(
                        label = controlPoint.label,
                        type = controlPoint.type.toProtectedCourseObjectType(),
                        point = controlPoint.toGeoPoint()
                    )
                )
            }
            courseInfo.courseObjects
                .filter {
                    it.type == ProtectedCourseObjectType.CONTROL ||
                        it.type == ProtectedCourseObjectType.BEACON ||
                        it.type == ProtectedCourseObjectType.SPECTATOR
                }
                .forEach { courseObject ->
                    add(
                        ProtectedCoordinateCandidate(
                            label = courseObject.label,
                            type = courseObject.type,
                            point = courseObject.toGeoPoint()
                        )
                    )
                }
        }
        val pointsByToken = typedPoints
            .flatMap { candidate ->
                candidate.label.expandedProtectedCoordinateTokens().map { token -> token to candidate.point }
            }
            .groupBy { it.first }
            .mapNotNull { (token, matches) ->
                matches.map { it.second.coordinateKey() }.distinct().singleOrNull()?.let {
                    token to matches.first().second
                }
            }
            .toMap()
        return ProtectedCoordinateLookup(
            pointsByToken = pointsByToken,
            singleBeaconPoint = typedPoints.singleUniquePoint(ProtectedCourseObjectType.BEACON),
            singleSpectatorPoint = typedPoints.singleUniquePoint(ProtectedCourseObjectType.SPECTATOR)
        )
    }

    private fun protectedPointForControl(
        control: EventControl,
        protectedCoordinateLookup: ProtectedCoordinateLookup
    ): CourseGeoPoint? =
        control.protectedCoordinateTokens()
            .firstNotNullOfOrNull { token -> protectedCoordinateLookup.pointsByToken[token] }
            ?: when (control.type) {
                ControlPointType.BEACON -> protectedCoordinateLookup.singleBeaconPoint
                ControlPointType.SEPARATOR -> protectedCoordinateLookup.singleSpectatorPoint
                ControlPointType.CONTROL -> null
            }

    private fun EventControl.protectedCoordinateTokens(): List<String> =
        buildList {
            publicLabel?.let(::add)
            add(label)
            add(siCode.toString())
            publicDisplayLabel().filter(Char::isDigit).takeIf { it.isNotBlank() }?.let(::add)
        }
            .flatMap { it.expandedProtectedCoordinateTokens() }
            .distinct()

    private fun String.expandedProtectedCoordinateTokens(): List<String> {
        val normalized = normalizedProtectedCoordinateToken() ?: return emptyList()
        val digits = normalized.filter(Char::isDigit).takeIf { it.isNotBlank() }
        return buildList {
            add(normalized)
            digits?.toIntOrNull()?.let { number ->
                add(number.toString())
                when (number) {
                    in 1..5 -> {
                        add((30 + number).toString())
                        add((40 + number).toString())
                    }
                    in 31..35 -> add((number - 30).toString())
                    in 41..45 -> add((number - 40).toString())
                }
            }
        }
            .mapNotNull { it.normalizedProtectedCoordinateToken() }
            .distinct()
    }

    private fun String.normalizedProtectedCoordinateToken(): String? {
        val trimmed = trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .trim()
        return trimmed
            .takeIf { it.isNotBlank() }
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
    }

    private fun CourseGeoPoint.coordinateKey(): Pair<Int, Int> =
        (latitude * 10_000_000).roundToInt() to (longitude * 10_000_000).roundToInt()

    private fun List<ProtectedCoordinateCandidate>.singleUniquePoint(type: ProtectedCourseObjectType): CourseGeoPoint? {
        val matches = filter { it.type == type }
        val uniqueKeys = matches.map { it.point.coordinateKey() }.distinct()
        return uniqueKeys.singleOrNull()?.let { matches.first().point }
    }

    private fun ControlPointType.toProtectedCourseObjectType(): ProtectedCourseObjectType =
        when (this) {
            ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
            ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
            ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
        }

    private fun oneDecimal(value: Double): String =
        (value * 10.0).roundToInt().let { "${it / 10}.${abs(it % 10)}" }

    private fun twoDecimals(value: Double): String =
        (value * 100.0).roundToInt().let { "${it / 100}.${(abs(it % 100)).toString().padStart(2, '0')}" }

    private fun compactDurationText(value: Int): String {
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
}

private data class ProtectedCoordinateLookup(
    val pointsByToken: Map<String, CourseGeoPoint>,
    val singleBeaconPoint: CourseGeoPoint?,
    val singleSpectatorPoint: CourseGeoPoint?
)

private data class ProtectedCoordinateCandidate(
    val label: String,
    val type: ProtectedCourseObjectType,
    val point: CourseGeoPoint
)

private data class ControlAnalysisPoint(
    val control: EventControl,
    val point: CourseGeoPoint?
)

private data class RouteStop(
    val label: String,
    val point: CourseGeoPoint,
    val routeIndex: Int,
    val control: EventControl?
)

private data class StraightLineStop(
    val label: String,
    val point: CourseGeoPoint,
    val control: EventControl?
)

private data class RouteTimingAnalysis(
    val legRows: List<DesktopCourseLegRow>,
    val waitRows: List<DesktopCourseWaitRow>,
    val arrivalSecondsByControlId: Map<String, Int>,
    val totalSeconds: Double?
) {
    companion object {
        val Empty = RouteTimingAnalysis(
            legRows = emptyList(),
            waitRows = emptyList(),
            arrivalSecondsByControlId = emptyMap(),
            totalSeconds = null
        )
    }
}

private data class ControlServiceTiming(
    val waitSeconds: Int?,
    val findPunchSeconds: Int?,
    val totalSeconds: Double?,
    val waitRow: DesktopCourseWaitRow?
) {
    companion object {
        val None = ControlServiceTiming(null, null, 0.0, null)
        val Unknown = ControlServiceTiming(null, null, null, null)
    }
}

private data class RouteAnalysis(
    val comparisonLengthMeters: Double,
    val measurementLabel: String,
    val routeLengthMeters: Double,
    val straightLineMeters: Double?,
    val climbMeters: Double?,
    val effectiveLengthMeters: Double?,
    val estimatedSeconds: Double,
    val elevationProfile: List<DesktopCourseElevationProfilePoint>,
    val arrivalSecondsByControlId: Map<String, Int>
)

private data class RouteMapSourcePoint(
    val label: String,
    val point: CourseGeoPoint,
    val type: DesktopCourseRouteMapPointType
)

private data class CalculatedRoute(
    val controls: List<ControlAnalysisPoint>,
    val distanceMeters: Double,
    val routeCount: Int,
    val calculationNote: String? = null
)

private data class CourseRuleRequirement(
    val minControls: Int,
    val maxControls: Int,
    val minLengthMeters: Int,
    val maxLengthMeters: Int
) {
    fun controlRangeText(): String =
        if (minControls == maxControls) minControls.toString() else "$minControls-$maxControls"

    fun lengthRangeText(): String =
        "${lengthValueText(minLengthMeters)}-${lengthValueText(maxLengthMeters)} km"

    private fun lengthValueText(meters: Int): String =
        if (meters % 1000 == 0) {
            "${meters / 1000}"
        } else {
            "${meters / 1000}.${(meters % 1000).toString().padStart(3, '0').trimEnd('0')}"
        }
}

private data class CourseSpacingRuleSet(
    val formatLabel: String,
    val startMinMeters: Int,
    val pairMinMeters: Int,
    val includeBeaconInStartCheck: Boolean,
    val includeSpectatorInPairCheck: Boolean,
    val includeBeaconInPairCheck: Boolean
)

private data class LabeledCoursePoint(
    val label: String,
    val point: CourseGeoPoint
)

private data class RenumberingFox(
    val control: EventControl,
    val currentSlotIndex: Int,
    val currentSlotLabel: String
)

private data class RenumberingSlot(
    val slotIndex: Int,
    val slotLabel: String
)
