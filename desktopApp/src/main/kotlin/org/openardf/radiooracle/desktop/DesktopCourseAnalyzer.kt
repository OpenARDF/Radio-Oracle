package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.course.ControlPointDefinition
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.event.toDisplayLabel
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class DesktopCourseAnalysisSummary(
    val eventName: String,
    val eventFileName: String?,
    val eventFormatLabel: String,
    val eventTypeLabel: String,
    val analysisPerformedAtText: String,
    val categoryName: String,
    val sameCourseCategoryNames: List<String>,
    val assignedFoxCount: Int,
    val rulesDocumentLabel: String,
    val speedModel: DesktopCourseSpeedModel,
    val categorySpeedFactors: List<DesktopCourseCategorySpeedFactor>,
    val providedRouteSection: DesktopCourseAnalysisSection?,
    val calculatedRouteSection: DesktopCourseAnalysisSection?,
    val summaryExplanation: String,
    val summaryGroups: List<DesktopCourseAnalysisSummaryGroup>,
    val courseRecommendation: DesktopCourseRecommendation,
    val goodnessMetrics: DesktopCourseGoodnessMetrics,
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
    val hasMissingCalculatedRouteElevationData: Boolean,
    val calculatedRouteMissingElevationPointCount: Int,
    val calculatedRouteElevationBoundingBox: DesktopVenueElevationBoundingBox?,
    val elevationProfile: List<DesktopCourseElevationProfilePoint>,
    val providedLegRows: List<DesktopCourseLegRow>,
    val calculatedLegRows: List<DesktopCourseLegRow>,
    val waitRows: List<DesktopCourseWaitRow>,
    val waitRenumbering: DesktopCourseWaitRenumbering?,
    val metrics: List<DesktopCourseGoodnessMetric>
)

data class DesktopCourseAnalysisSummaryGroup(
    val title: String,
    val rows: List<DesktopCourseAnalysisSummaryRow>
)

data class DesktopCourseAnalysisSummaryRow(
    val label: String,
    val value: String
)

data class DesktopCourseRecommendation(
    val actionLabel: String,
    val paragraph: String
)

data class DesktopCourseGoodnessMetrics(
    val sharedMetrics: List<DesktopCourseGoodnessMetric>,
    val groups: List<DesktopCourseGoodnessMetricGroup>
)

data class DesktopCourseGoodnessMetricGroup(
    val title: String,
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
    val speedModel: DesktopCourseSpeedModel? = null,
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
    val routeLabels: List<String>,
    val routePointIndexes: List<Int> = emptyList()
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
    val routeStops: List<DesktopCourseKmlRouteStop>,
    val courseObjects: List<DesktopCourseKmlExportPoint>
)

data class DesktopCourseKmlRouteStop(
    val label: String,
    val point: CourseGeoPoint
)

data class DesktopCourseKmlExportPoint(
    val label: String,
    val originalLabel: String?,
    val point: CourseGeoPoint,
    val type: DesktopCourseKmlExportPointType
)

enum class DesktopCourseKmlExportPointType {
    START,
    FINISH,
    CONTROL,
    BEACON,
    SPECTATOR
}

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

data class DesktopCourseSpeedModel(
    val formatSpeedMetersPerSecond: Double,
    val categorySpeedMultiplier: Double,
    val compensationFactor: Double,
    val effectiveSpeedMetersPerSecond: Double,
    val categoryModelLabel: String,
    val categoryFactorSourceLabel: String,
    val categoryFactorExplanation: String
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
    val assignments: List<DesktopCourseWaitRenumberingAssignment>,
    val suggestedWaitRows: List<DesktopCourseWaitRow> = emptyList()
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
 * Builds the desktop Course Analyzer report from stored course geometry and category controls.
 *
 * The analyzer intentionally separates the imported route from the ideal route determined from
 * the course points. The ideal route is the route from start to finish through the required
 * controls that has the shortest effective length; it is not chosen by preference. Both imported
 * and calculated routes use the same measurement policy: effective length when complete elevation
 * data is available, otherwise horizontal distance. Classic-style wait-time checks replay the timed
 * route to identify whether a different fox numbering can reduce waiting. A Classic fox stop is
 * modeled as arrival near the fox, wait until transmission if needed, then a fixed 30-second
 * find-and-punch allowance before departure to the next leg.
 *
 * Elevation Cache resolution is local sample-grid spacing, not a guarantee of source DEM
 * resolution. USGS 3DEP is a multi-resolution source; a dense cache may still sample coarser
 * terrain data where 3-meter or better DEM coverage is unavailable.
 *
 * The analyzer does not currently import map passability. Out-of-bounds areas, dense vegetation,
 * lakes, uncrossable watercourses, fences, cliffs, and other navigation barriers are not modeled,
 * so map data can nullify a calculated route and wait-time estimates remain advisory.
 *
 * Estimated times use an elite-competitor baseline pace by race format, category age/gender speed
 * adjustments, and the event-wide compensation factor, then convert each leg to effective length
 * when elevation is available. Fatigue is not part of ideal-route selection; it can affect ideal
 * time, but the current estimate does not apply a separate accumulated-fatigue adjustment.
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
    private const val MIN_EFFECTIVE_SPEED_MPS = 0.25
    private const val MAX_PERMUTATION_CONTROLS = 8
    private const val FOXORING_EXHAUSTIVE_CONTROLS = 6
    private const val FOXORING_ROLLING_WINDOW_CONTROLS = 5
    private const val MAX_SPRINT_LOOP_PERMUTATIONS = 120
    private const val CALCULATED_ROUTE_SAMPLE_METERS = 25.0
    private const val ROUTE_ENDPOINT_EXACT_TOLERANCE_METERS = 0.5
    private const val ROUTE_STOP_TOLERANCE_METERS = 5.0
    private const val COURSE_RECOMMENDATION_WAIT_SECONDS = 30
    private const val ELEVATION_CACHE_RESOLUTION_NOTE =
        "Elevation Cache resolution is the local sample-grid spacing; USGS 3DEP source DEM resolution varies, so a 3 m cache does not guarantee 3 m source terrain data everywhere."
    private const val MAP_KNOWLEDGE_LIMITATION_NOTE =
        "The analyzer does not currently know map passability, so out-of-bounds areas, dense vegetation, water, uncrossable features, and other impediments can make the true on-foot route and wait timing differ from this estimate."
    private const val SPEED_MODEL_NOTE =
        "Estimated times use an elite-competitor baseline pace by race format, then apply a category age/gender multiplier and the event-wide Course Analyzer speed factor. When elevation is available, movement time uses effective length for each leg: horizontal length plus ten times positive climb. If elevation is incomplete, movement time falls back to horizontal distance. Fatigue is not part of ideal-route selection; it can affect ideal time, but this estimate does not apply a separate accumulated-fatigue adjustment."
    private const val CLASSIC_WAIT_TIMING_NOTE =
        "For Classic-style fox controls, timing assumes the competitor waits if the fox is off the air, then spends 30 seconds finding and punching before departing for the next leg; that delay affects later arrival phases."
    private val CATEGORY_SPEED_FACTOR_TABLE = DesktopCourseSpeedFactors.provisionalCategoryTable
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

    /**
     * Mirrors the analyzer's Section 1/Section 2 prerequisites without running the route search.
     * A stored route polyline alone can still produce only Section 3, which should not make the
     * desktop Analyze button look actionable.
     */
    fun analysisUnavailableReason(
        projectFile: EventProjectFile,
        categoryId: String?,
        protectedCourseInfo: ProtectedCourseInfo?,
        protectedIdealOrderText: String?
    ): String? {
        if (categoryId == null) {
            return "Import controls/route KML/KMZ or GPX data for a category before running analysis."
        }
        val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: return "Select a category before running analysis."
        if (protectedCourseInfo == null || protectedCourseInfo.route.size < 2) {
            return "Stored course route data is unavailable for the selected category. Import course KML/KMZ or GPX data before running analysis."
        }

        val idealOrderText = protectedIdealOrderText?.takeIf { it.isNotBlank() }
            ?: protectedCourseInfo.idealOrder.takeIf { it.isNotBlank() }
        val categoryAssignedControls = assignedControls(projectFile, categoryId)
        val allProtectedControls = protectedAssignedControls(projectFile, protectedCourseInfo, null)
        val terminalBeaconControl = allProtectedControls.firstOrNull { it.type == ControlPointType.BEACON }
            ?: categoryAssignedControls.firstOrNull { it.type == ControlPointType.BEACON }
        val protectedRouteControls = protectedAssignedControls(projectFile, protectedCourseInfo, idealOrderText)
            .withTerminalBeacon(terminalBeaconControl)
        val assignedControls = protectedRouteControls.ifEmpty { categoryAssignedControls.withTerminalBeacon(terminalBeaconControl) }
        val providedControls = idealOrderText
            ?.let { idealOrder ->
                runCatching {
                    val ids = ProtectedIdealOrderRules.resolveControlIds(idealOrder, assignedControls)
                    ids.mapNotNull { id -> assignedControls.firstOrNull { it.id == id } }
                }.getOrNull()
            }
            .orEmpty()
            .withTerminalBeacon(terminalBeaconControl)
        val canBuildImportedRouteSection = providedControls.isNotEmpty()

        val courseObjectPoints = protectedCourseInfo.effectiveCourseObjectPoints()
        val route = normalizedImportedRoute(
            protectedCourseInfo.route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) },
            courseObjectPoints
        )
        val protectedControlPointsById = protectedCourseInfo.controlPoints.associateBy { it.controlId }
        val protectedCoordinateLookup = protectedCoordinateLookup(protectedCourseInfo)
        val controlsWithPoints = assignedControls.map { control ->
            ControlAnalysisPoint(
                control = control,
                point = protectedControlPointsById[control.id]?.toGeoPoint()
                    ?: protectedPointForControl(control, protectedCoordinateLookup)
            )
        }
        val start = route.firstOrNull() ?: courseObjectPoints
            .firstOrNull { it.type == ProtectedCourseObjectType.START }
            ?.toGeoPoint()
        val finish = route.lastOrNull() ?: courseObjectPoints
            .firstOrNull { it.type == ProtectedCourseObjectType.FINISH }
            ?.toGeoPoint()
        val foxes = controlsWithPoints.filter { it.control.type == ControlPointType.CONTROL && it.point != null }
        val spectator = controlsWithPoints.firstOrNull { it.control.type == ControlPointType.SEPARATOR && it.point != null }
        val canBuildCalculatedRouteSection = canAttemptCalculatedRoute(
            raceType = categoryData.category.effectiveRaceType(projectFile.raceData.race),
            start = start,
            finish = finish,
            foxes = foxes,
            spectator = spectator
        )

        return if (canBuildImportedRouteSection || canBuildCalculatedRouteSection) {
            null
        } else {
            "The selected category has route geometry, but no usable control order or located controls. Import route data with control assignments/locations before running analysis."
        }
    }

    fun analyze(
        projectFile: EventProjectFile,
        categoryId: String,
        protectedCourseInfo: ProtectedCourseInfo?,
        protectedIdealOrderText: String?,
        eventFileName: String? = null,
        analysisPerformedAtText: String = DesktopDateTimeText.displayText(LocalDateTime.now().withNano(0)),
        elevationLookup: (CourseGeoPoint) -> Double? = { null },
        elevationCacheNotes: (List<CourseGeoPoint>) -> List<String> = { emptyList() }
    ): DesktopCourseAnalysisSummary {
        val categoryData = projectFile.raceData.categories.first { it.category.id == categoryId }
        val category = categoryData.category
        val raceType = category.effectiveRaceType(projectFile.raceData.race)
        val speedModel = speedModel(
            raceType = raceType,
            categoryName = category.name,
            compensationFactor = projectFile.raceData.race.courseAnalyzerSpeedCompensationFactor
        )
        val idealOrderText = protectedIdealOrderText?.takeIf { it.isNotBlank() }
            ?: protectedCourseInfo?.idealOrder?.takeIf { it.isNotBlank() }
        val categoryAssignedControls = assignedControls(projectFile, categoryId)
        val allProtectedControls = protectedCourseInfo
            ?.let { protectedAssignedControls(projectFile, it, null) }
            .orEmpty()
        val terminalBeaconControl = allProtectedControls.firstOrNull { it.type == ControlPointType.BEACON }
            ?: categoryAssignedControls.firstOrNull { it.type == ControlPointType.BEACON }
        val protectedRouteControls = protectedCourseInfo
            ?.let { protectedAssignedControls(projectFile, it, idealOrderText) }
            .orEmpty()
            .withTerminalBeacon(terminalBeaconControl)
        val assignedControls = protectedRouteControls.ifEmpty { categoryAssignedControls.withTerminalBeacon(terminalBeaconControl) }
        val sameCourseCategoryNames = sameCourseCategoryNames(projectFile, categoryId)
        val protectedControlPointsById = protectedCourseInfo?.controlPoints.orEmpty().associateBy { it.controlId }
        val protectedCoordinateLookup = protectedCoordinateLookup(protectedCourseInfo)
        val missing = mutableListOf<String>()

        if (protectedCourseInfo == null) {
            missing += "Route data is locked by the Event Password or has not been imported for ${category.name}."
        }
        val courseObjectPoints = protectedCourseInfo?.effectiveCourseObjectPoints().orEmpty()
        val route = normalizedImportedRoute(
            protectedCourseInfo?.route.orEmpty().map {
                CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters)
            },
            courseObjectPoints
        )
        if (route.size < 2) {
            missing += "Route geometry with start and finish points is missing."
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
                    missing += "Course object points are missing for start, finish, controls, beacon, or spectator if assigned."
                }
                courseInfo.courseObjects.any { it.elevationMeters == null } -> {
                    hasMissingCourseObjectElevations = true
                    missing += "Course object elevations are missing or incomplete."
                }
            }
            if (courseInfo.controlPoints.any { it.elevationMeters == null }) {
                hasMissingProtectedControlElevations = true
                missing += "Control location elevations are missing or incomplete."
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
        val displayControlsWithPoints = displayControlsWithPoints(projectFile, protectedControlPointsById, protectedCoordinateLookup)
            .ifEmpty { controlsWithPoints }
        val missingCoordinateControls = controlsWithPoints.filter { it.point == null }
        if (missingCoordinateControls.isNotEmpty()) {
            DesktopDebugLog.warn(
                "CourseAnalysis",
                    "Missing coordinates category=${category.name}: " +
                    "controls=${missingCoordinateControls.joinToString { it.control.publicDisplayLabel() }}; " +
                    "assigned=${assignedControls.size} controlLocationPoints=${protectedCourseInfo?.controlPoints?.size ?: 0} " +
                    "courseObjects=${protectedCourseInfo?.courseObjects?.size ?: 0} " +
                    "tokenMatches=${protectedCoordinateLookup.pointsByToken.size} " +
                    "singleBeacon=${protectedCoordinateLookup.singleBeaconPoint != null} " +
                    "singleSpectator=${protectedCoordinateLookup.singleSpectatorPoint != null}"
            )
        }
        missingCoordinateControls.forEach {
            missing += "Location latitude/longitude is missing for control ${it.control.publicDisplayLabel()}."
        }

        val start = route.firstOrNull() ?: courseObjectPoints
            .firstOrNull { it.type == ProtectedCourseObjectType.START }
            ?.toGeoPoint()
        val finish = route.lastOrNull() ?: courseObjectPoints
            .firstOrNull { it.type == ProtectedCourseObjectType.FINISH }
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

        val providedControlsFromOrder = idealOrderText
            ?.let { idealOrder ->
                runCatching {
                    val ids = ProtectedIdealOrderRules.resolveControlIds(idealOrder, assignedControls)
                    ids.mapNotNull { id -> assignedControls.firstOrNull { it.id == id } }
                }.getOrElse { error ->
                    missing += "Imported route order could not be resolved: ${error.message ?: error::class.simpleName}."
                    emptyList()
                }
            }
            .orEmpty()
        val providedControls = providedControlsFromOrder.withTerminalBeacon(terminalBeaconControl)
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
        val calculatedRouteElevationSamplePoints = if (
            start != null &&
            finish != null &&
            calculatedRoute != null &&
            !calculatedRouteMatchesStored
        ) {
            sampledCalculatedRoutePoints(
                start = start,
                controls = calculatedRoute.controls,
                finish = finish,
                elevationLookup = elevationLookup
            )
        } else {
            emptyList()
        }
        val calculatedRouteMissingElevationPointCount = calculatedRouteElevationSamplePoints.count { point ->
            elevationLookup(point) == null
        }
        val hasMissingCalculatedRouteElevationData = calculatedRouteElevationSamplePoints.size >= 2 &&
            calculatedRouteMissingElevationPointCount > 0
        if (hasMissingCalculatedRouteElevationData) {
            missing += "Calculated route elevation samples are missing from the local elevation cache; calculated route climb, effective length, timing, and comparison may use endpoint interpolation or horizontal straight-line distance instead of downloaded elevations along the route."
        }

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
            raceType = raceType,
            speedModel = speedModel
        )
        val providedLegRows = providedTiming.legRows
        val waitRows = providedTiming.waitRows
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
                    speedModel = speedModel,
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
                        speedModel = speedModel,
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
        val calculatedIdealOrder = calculatedRoute
            ?.let { routeCandidate -> calculatedRouteLabels(routeCandidate.controls, calculatedLabelOverrides, includeFinish = true) }
            .orEmpty()
        val calculatedTiming = calculatedRoute?.let { routeCandidate ->
            straightLineTiming(
                start = start,
                controls = routeCandidate.controls,
                finish = finish,
                raceType = raceType,
                speedModel = speedModel,
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
        val calculatedRouteAnalysisForChecks = if (calculatedRouteMatchesStored) {
            providedRouteAnalysis
        } else {
            calculatedRouteAnalysis
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
                routeLabel = "Imported route",
                raceType = raceType,
                categoryName = category.name,
                foxCount = foxes.size,
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                measurementLabel = analysis.measurementLabel,
                estimatedSeconds = analysis.estimatedSeconds?.roundToInt()
            ) + spacingRuleChecks
        }.orEmpty()
        val calculatedRuleChecks = calculatedRouteAnalysisForChecks?.let { analysis ->
            routeRuleChecks(
                routeLabel = "Calculated route",
                raceType = raceType,
                categoryName = category.name,
                foxCount = foxes.size,
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                measurementLabel = analysis.measurementLabel,
                estimatedSeconds = analysis.estimatedSeconds?.roundToInt()
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
                    explanation = "The analyzer determined the ideal route from the start, finish, controls, beacon, and spectator if assigned. The calculated ideal route matches the imported route, so no separate calculated-route leg, wait, elevation-profile, or map analysis is repeated in this section. Section 3 still summarizes the route comparison.",
                    routeOrder = listOf("Calculated ideal route matches imported route"),
                    routeOrderLabel = "Result",
                    summaryOnly = true,
                    comparisonLengthMeters = null,
                    comparisonLengthLabel = "Comparison length",
                    straightLineMeters = null,
                    routeLengthMeters = null,
                    climbMeters = null,
                    effectiveLengthMeters = null,
                    estimatedIdealSeconds = null,
                    speedModel = null,
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
                    routeOrder = calculatedRouteLabels(routeCandidate.controls, includeFinish = true),
                    routeOrderLabel = "Route order (imported fox numbering)",
                    secondaryRouteOrder = calculatedRouteLabels(routeCandidate.controls, calculatedLabelOverrides, includeFinish = true),
                    secondaryRouteOrderLabel = "Route order (calculated fox numbering)",
                    comparisonLengthMeters = calculatedRouteAnalysis?.comparisonLengthMeters?.roundToInt(),
                    comparisonLengthLabel = calculatedRouteAnalysis?.measurementLabel ?: "Unknown",
                    straightLineMeters = routeCandidate.distanceMeters.roundToInt(),
                    routeLengthMeters = calculatedRouteAnalysis?.routeLengthMeters?.roundToInt(),
                    climbMeters = calculatedRouteAnalysis?.climbMeters?.roundToInt(),
                    effectiveLengthMeters = calculatedRouteAnalysis?.effectiveLengthMeters?.roundToInt(),
                    estimatedIdealSeconds = calculatedRouteAnalysis?.estimatedSeconds?.roundToInt(),
                    speedModel = speedModel,
                    legRows = calculatedLegRows,
                    waitRows = calculatedWaitRows,
                    waitRenumbering = calculatedWaitRenumbering,
                    ruleChecks = calculatedRuleChecks,
                    elevationProfile = calculatedRouteAnalysis?.elevationProfile.orEmpty(),
                    routeMap = routeMap(
                        title = "Calculated route (calculated fox numbering)",
                        start = start,
                        finish = finish,
                        controls = displayControlsWithPoints,
                        routeControls = routeCandidate.controls,
                        labelOverrides = calculatedLabelOverrides
                    )
                )
            }
        }
        val providedSection = providedRouteAnalysis?.let { analysis ->
            DesktopCourseAnalysisSection(
                title = "Section 1: Imported route analysis",
                explanation = providedSectionExplanation(analysis),
                routeOrder = eventControlRouteLabels(providedControls, includeFinish = true),
                comparisonLengthMeters = analysis.comparisonLengthMeters.roundToInt(),
                comparisonLengthLabel = analysis.measurementLabel,
                straightLineMeters = providedStraightLineMeters,
                routeLengthMeters = analysis.routeLengthMeters.roundToInt(),
                climbMeters = analysis.climbMeters?.roundToInt(),
                effectiveLengthMeters = analysis.effectiveLengthMeters?.roundToInt(),
                estimatedIdealSeconds = analysis.estimatedSeconds?.roundToInt(),
                speedModel = speedModel,
                legRows = providedLegRows,
                waitRows = waitRows,
                waitRenumbering = waitRenumbering,
                ruleChecks = providedRuleChecks,
                elevationProfile = analysis.elevationProfile,
                routeMap = routeMap(
                    title = "Imported route",
                    start = start,
                    finish = finish,
                    controls = displayControlsWithPoints,
                    routeControls = providedControls.mapNotNull { control ->
                        controlsWithPoints.firstOrNull { it.control.id == control.id }
                    }
                )
            )
        }
        val metrics = goodnessMetrics(
            raceType = raceType,
            categoryName = category.name,
            routeLengthMeters = providedRouteAnalysis?.routeLengthMeters?.roundToInt(),
            climbMeters = providedRouteAnalysis?.climbMeters?.roundToInt(),
            calculatedRouteLengthMeters = calculatedRouteAnalysis?.routeLengthMeters?.roundToInt(),
            calculatedRouteClimbMeters = calculatedRouteClimbMeters(
                start = start,
                controls = calculatedRoute?.controls.orEmpty(),
                finish = finish,
                elevationLookup = elevationLookup
            ),
            importedComparisonLengthMeters = providedRouteAnalysis?.comparisonLengthMeters?.roundToInt(),
            calculatedComparisonLengthMeters = calculatedRouteAnalysisForChecks?.comparisonLengthMeters?.roundToInt(),
            effectiveLengthMeters = providedRouteAnalysis?.effectiveLengthMeters?.roundToInt(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            waitRows = waitRows,
            waitRenumbering = waitRenumbering,
            idealOrderMatches = idealOrderMatches
        ) + (providedRuleChecks + calculatedRuleChecks)
            .distinctBy { "${it.label}:${it.value}" }
        val profileComparison = buildList {
            providedSection?.let {
                add(
                    DesktopCourseElevationProfileSummary(
                        title = "Imported route",
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
                        title = "Imported foxes and route",
                        routeName = "Imported route",
                        routePoints = route,
                        routeStops = providedKmlRouteStops(route, providedControls, controlsWithPoints),
                        courseObjects = providedKmlCourseObjects(route, providedControls, controlsWithPoints)
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
                            routeStops = calculatedKmlRouteStops(
                                start = start,
                                controls = routeCandidate.controls,
                                finish = finish,
                                labelOverrides = calculatedLabelOverrides
                            ),
                            courseObjects = calculatedKmlCourseObjects(
                                start = start,
                                controls = routeCandidate.controls,
                                finish = finish,
                                renumbering = calculatedWaitRenumbering
                            )
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
        val summaryGroups = summaryGroups(
            providedSection = providedSection,
            calculatedSection = calculatedSection,
            calculatedRouteCount = calculatedRoute?.routeCount ?: 0,
            calculatedIdealOrder = calculatedIdealOrder,
            providedIdealOrder = eventControlRouteLabels(providedControls, includeFinish = true),
            idealOrderMatches = idealOrderMatches,
            waitRenumbering = waitRenumbering
        )
        val courseRecommendation = courseRecommendation(
            calculatedRouteApplication = calculatedRouteApplication,
            providedSection = providedSection,
            calculatedSection = calculatedSection,
            lengthRequirement = routeLengthRequirement(category.name, raceType),
            categoryName = category.name,
            calculatedIdealOrder = calculatedIdealOrder,
            idealOrderMatches = idealOrderMatches,
            waitRenumbering = waitRenumbering
        )
        val goodnessMetrics = goodnessMetrics(
            metrics = metrics,
            providedSection = providedSection,
            calculatedSection = calculatedSection,
            raceType = raceType,
            categoryName = category.name
        )

        return DesktopCourseAnalysisSummary(
            eventName = projectFile.raceData.race.name,
            eventFileName = eventFileName,
            eventFormatLabel = projectFile.raceData.race.raceType.toDisplayLabel(),
            eventTypeLabel = projectFile.raceData.race.raceLevel.toDisplayLabel(),
            analysisPerformedAtText = analysisPerformedAtText,
            categoryName = category.name,
            sameCourseCategoryNames = sameCourseCategoryNames,
            assignedFoxCount = assignedControls.count { it.type == ControlPointType.CONTROL },
            rulesDocumentLabel = USA_RULES_DOCUMENT_LABEL,
            speedModel = speedModel,
            categorySpeedFactors = CATEGORY_SPEED_FACTOR_TABLE.categoryFactors,
            providedRouteSection = providedSection,
            calculatedRouteSection = calculatedSection,
            summaryExplanation = summaryExplanation(providedSection, calculatedSection, waitRenumbering, speedModel),
            summaryGroups = summaryGroups,
            courseRecommendation = courseRecommendation,
            goodnessMetrics = goodnessMetrics,
            profileComparison = profileComparison,
            elevationCacheNotes = elevationCacheNotes(profileRoutePoints),
            routeMaps = routeMaps,
            kmlFolders = kmlFolders,
            calculatedRouteApplication = calculatedRouteApplication,
            missingElements = missing.distinct(),
            calculatedRouteCount = calculatedRoute?.routeCount ?: 0,
            calculatedIdealOrder = calculatedIdealOrder,
            providedIdealOrder = eventControlRouteLabels(providedControls, includeFinish = true),
            idealOrderMatches = idealOrderMatches,
            calculatedStraightLineMeters = calculatedRoute?.distanceMeters?.roundToInt(),
            providedStraightLineMeters = providedStraightLineMeters,
            routeLengthMeters = providedRouteAnalysis?.routeLengthMeters?.roundToInt(),
            climbMeters = providedRouteAnalysis?.climbMeters?.roundToInt(),
            effectiveLengthMeters = providedRouteAnalysis?.effectiveLengthMeters?.roundToInt(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            hasMissingElevationData = hasMissingElevationData,
            hasMissingCalculatedRouteElevationData = hasMissingCalculatedRouteElevationData,
            calculatedRouteMissingElevationPointCount = calculatedRouteMissingElevationPointCount,
            calculatedRouteElevationBoundingBox = calculatedRouteElevationSamplePoints.analysisBoundingBoxOrNull(),
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

    private fun displayControlsWithPoints(
        projectFile: EventProjectFile,
        protectedControlPointsById: Map<String, ProtectedCourseControlPoint>,
        protectedCoordinateLookup: ProtectedCoordinateLookup
    ): List<ControlAnalysisPoint> =
        projectFile.raceData.controls
            .filter {
                it.type == ControlPointType.CONTROL ||
                    it.type == ControlPointType.SEPARATOR ||
                    it.type == ControlPointType.BEACON
            }
            .map { control ->
                ControlAnalysisPoint(
                    control = control,
                    point = protectedControlPointsById[control.id]?.toGeoPoint()
                        ?: protectedPointForControl(control, protectedCoordinateLookup)
                )
            }
            .filter { it.point != null }
            .distinctBy { it.control.id }

    private fun protectedAssignedControls(
        projectFile: EventProjectFile,
        courseInfo: ProtectedCourseInfo,
        idealOrderText: String?
    ): List<EventControl> {
        val controlsById = projectFile.raceData.controls.associateBy { it.id }
        val projectControls = projectFile.raceData.controls
            .filter {
                it.type == ControlPointType.CONTROL ||
                    it.type == ControlPointType.SEPARATOR ||
                    it.type == ControlPointType.BEACON
            }
        val protectedControls = courseInfo.controlPoints
            .map { protectedControl ->
                controlsById[protectedControl.controlId]
                    ?: projectControls.firstOrNull { it.matchesProtectedControl(protectedControl) }
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

    private fun EventControl.matchesProtectedControl(protectedControl: ProtectedCourseControlPoint): Boolean {
        if (type != protectedControl.type) {
            return false
        }
        val protectedLabel = protectedControl.label.normalizedAnalysisControlName()
        val protectedCompactLabel = protectedControl.label.compactAnalysisControlName()
        val protectedNumber = protectedControl.label.singleAnalysisControlNumber()
        if (type == ControlPointType.BEACON && protectedCompactLabel in setOf("m", "beacon")) {
            return listOf(label, publicLabel.orEmpty()).any { it.compactAnalysisControlName() in setOf("m", "beacon") }
        }
        if (type == ControlPointType.SEPARATOR && protectedCompactLabel in setOf("s", "spectator", "separator")) {
            return listOf(label, publicLabel.orEmpty()).any { it.compactAnalysisControlName() in setOf("s", "spectator", "separator") }
        }
        return listOf(label, publicLabel.orEmpty(), siCode.toString()).any { token ->
            token.normalizedAnalysisControlName() == protectedLabel ||
                token.compactAnalysisControlName() == protectedCompactLabel ||
                (protectedNumber != null && token.singleAnalysisControlNumber() == protectedNumber)
        }
    }

    private fun String.normalizedAnalysisControlName(): String =
        trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun String.compactAnalysisControlName(): String =
        normalizedAnalysisControlName().replace(" ", "")

    private fun String.singleAnalysisControlNumber(): Int? {
        val matches = Regex("""\d+""").findAll(this).map { it.value.toInt() }.toList()
        return matches.singleOrNull()
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

    private fun canAttemptCalculatedRoute(
        raceType: RaceType,
        start: CourseGeoPoint?,
        finish: CourseGeoPoint?,
        foxes: List<ControlAnalysisPoint>,
        spectator: ControlAnalysisPoint?
    ): Boolean {
        if (start == null || finish == null || foxes.isEmpty()) {
            return false
        }
        return when (raceType) {
            RaceType.SPRINT,
            RaceType.FOXORING -> true
            else -> foxes.size + listOfNotNull(spectator).size <= MAX_PERMUTATION_CONTROLS
        }
    }

    /**
     * Section 1 analyzes the imported route. The imported route geometry is used for
     * actual length, climb, profile, and split estimates; if every route sample has elevation, the
     * comparison metric becomes effective length, defined by the referenced course-design guide as
     * horizontal length plus ten times total climb. If elevations are incomplete, the analyzer still
     * runs and falls back to horizontal length.
     */
    private fun providedRouteAnalysis(
        route: List<CourseGeoPoint>,
        providedRoutePoints: List<CourseGeoPoint>,
        protectedCourseInfo: ProtectedCourseInfo?,
        timing: RouteTimingAnalysis
    ): RouteAnalysis {
        val routeLengthMeters = route.straightLineMeters()
        val climbMeters = climbMetersOrNull(route)
        val hasCompleteElevation = route.all { it.elevationMeters != null } && climbMeters != null
        val effectiveLengthMeters = if (hasCompleteElevation) routeLengthMeters + 10.0 * requireNotNull(climbMeters) else null
        return RouteAnalysis(
            comparisonLengthMeters = effectiveLengthMeters ?: routeLengthMeters,
            measurementLabel = if (effectiveLengthMeters != null) "Effective length" else "Horizontal length",
            routeLengthMeters = routeLengthMeters,
            straightLineMeters = providedRoutePoints.takeIf { it.size >= 2 }?.straightLineMeters(),
            climbMeters = climbMeters,
            effectiveLengthMeters = effectiveLengthMeters,
            estimatedSeconds = timing.totalSeconds,
            elevationProfile = elevationProfile(route),
            arrivalSecondsByControlId = timing.arrivalSecondsByControlId
        )
    }

    /**
     * Section 2 determines the ideal route from the known course points when the route search is
     * exhaustive. Scored controls and an optional spectator are permuted, the beacon is kept as the
     * last radio point before the finish, and the shortest effective length defines the ideal route.
     * Complete point elevations use effective length; otherwise horizontal length is
     * used as a fallback comparison metric.
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
            measurementLabel = if (effectiveLengthMeters != null) "Effective length" else "Horizontal length",
            routeLengthMeters = routeLengthMeters,
            straightLineMeters = routeLengthMeters,
            climbMeters = climbMeters,
            effectiveLengthMeters = effectiveLengthMeters,
            estimatedSeconds = timing.totalSeconds,
            elevationProfile = elevationProfile(points),
            arrivalSecondsByControlId = timing.arrivalSecondsByControlId
        )
    }

    private fun providedSectionExplanation(analysis: RouteAnalysis): String =
            "This section analyzes the route supplied by the imported controls/routes file for the category. Leg lengths are taken from the imported route geometry, and estimated splits combine movement time with any Classic fox wait and find/punch time. " +
            "The primary comparison value is ${analysis.measurementLabel.lowercase()}; " +
            if (analysis.effectiveLengthMeters != null) {
                "the Elevation Cache data is complete, so effective length is calculated as horizontal length plus ten times total climb. $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE $ELEVATION_CACHE_RESOLUTION_NOTE $MAP_KNOWLEDGE_LIMITATION_NOTE"
            } else {
                "local elevation data is incomplete, so horizontal length is used instead of effective length. $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE $ELEVATION_CACHE_RESOLUTION_NOTE $MAP_KNOWLEDGE_LIMITATION_NOTE"
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
            "Elevation data was incomplete along the calculated straight-line legs, so horizontal length was used. $ELEVATION_CACHE_RESOLUTION_NOTE"
        }
        val assignmentText = assignmentDifferenceText(providedAssignments, calculatedAssignments)
        val routeCalculationText = routeCalculationNote?.let { " $it" }.orEmpty()
        val isNonExhaustiveSearch = routeCalculationNote?.contains("non-exhaustive", ignoreCase = true) == true
        val opening = if (routeCalculationNote == null) {
            "This section determines the ideal route by comparing all $routeCount possible orders of the foxes and any spectator point, with the beacon last before the finish."
        } else {
            "This section determines a route using the format-specific route search noted below; $routeCount route candidate order(s) were evaluated or generated."
        }
        val routeDefinitionText = when {
            routeCalculationNote == null ->
                "The route with the shortest $measurement is by definition the ideal route for the course; an imported route that is longer is not ideal."
            isNonExhaustiveSearch ->
                "Because this search is non-exhaustive, the calculated route is advisory rather than a definitive ideal route."
            else ->
                "Within the stated format-specific route model, the route with the shortest $measurement is the ideal route; an imported route that is longer is not ideal."
        }
        return "$opening $routeDefinitionText$routeCalculationText $elevationText $SPEED_MODEL_NOTE $CLASSIC_WAIT_TIMING_NOTE Large differences in estimated ideal time can come from fox wait-time optimization as well as horizontal length; compare the movement, wait, and find/punch timing breakdown rows. $MAP_KNOWLEDGE_LIMITATION_NOTE $assignmentText"
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
            "The optimized fox assignments match the imported-route assignment check."
        } else {
            "Compared with Section 1, the calculated route changes optimized assignments for " +
                differences.joinToString { "${it.controlLabel} -> ${it.suggestedSlotLabel}" } + "."
        }
    }

    private fun summaryExplanation(
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        waitRenumbering: DesktopCourseWaitRenumbering?,
        speedModel: DesktopCourseSpeedModel
    ): String {
        val speedText =
            " Assumed running speed is ${twoDecimals(speedModel.effectiveSpeedMetersPerSecond)} m/s: " +
                "${twoDecimals(speedModel.formatSpeedMetersPerSecond)} m/s race-format baseline x " +
                "${speedModel.categoryModelLabel} category multiplier ${twoDecimals(speedModel.categorySpeedMultiplier)} x " +
                "event speed factor ${twoDecimals(speedModel.compensationFactor)}. The event speed factor is the single event-wide adjustment for terrain, weather, or other conditions; below 1.00 slows all category estimates, and above 1.00 speeds them."
        val waitImprovementText = waitRenumbering
            ?.takeIf { it.improvesWait }
            ?.let { renumbering ->
                val improvementSeconds = (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)
                    .coerceAtLeast(0)
                " Section 1 identifies a fox-renumbering option that may reduce imported-route wait time by ${compactDurationText(improvementSeconds)}; see Section 1 for the assignment details."
            }
            .orEmpty()
        val baseText = if (providedSection != null && calculatedSection != null) {
            "This summary compares the imported route with the independently calculated candidate, including checks against the cited USA rules document, their primary distance metric, route order, estimated time, wait-time optimization, elevation profiles, and 2D point depictions."
        } else {
            "This summary reports checks against the cited USA rules document and the independently calculated route candidate because no imported route was available for Section 1."
        }
        return baseText + speedText + waitImprovementText
    }

    private fun summaryGroups(
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        calculatedRouteCount: Int,
        calculatedIdealOrder: List<String>,
        providedIdealOrder: List<String>,
        idealOrderMatches: Boolean?,
        waitRenumbering: DesktopCourseWaitRenumbering?
    ): List<DesktopCourseAnalysisSummaryGroup> =
        listOf(
            DesktopCourseAnalysisSummaryGroup(
                title = "Imported",
                rows = buildList {
                    if (providedSection == null) {
                        add(DesktopCourseAnalysisSummaryRow("Imported route", "Unavailable"))
                    } else {
                        add(DesktopCourseAnalysisSummaryRow("Imported route", providedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }))
                        add(DesktopCourseAnalysisSummaryRow("Horizontal length", summaryLengthText(providedSection.routeLengthMeters)))
                        add(DesktopCourseAnalysisSummaryRow("Climb", summaryClimbText(providedSection.climbMeters)))
                        add(DesktopCourseAnalysisSummaryRow("Effective length", summaryLengthText(providedSection.effectiveLengthMeters)))
                        add(DesktopCourseAnalysisSummaryRow("Estimated ideal time", summaryDurationText(providedSection.estimatedIdealSeconds)))
                        val currentWaitSeconds = providedSection.waitRows.sumOf { it.waitSeconds }
                        if (providedSection.waitRows.isNotEmpty()) {
                            add(DesktopCourseAnalysisSummaryRow("Imported numbering wait", summaryDurationText(currentWaitSeconds)))
                        }
                        waitRenumbering?.takeIf { it.improvesWait }?.let { renumbering ->
                            add(DesktopCourseAnalysisSummaryRow("Best renumbered wait", summaryDurationText(renumbering.bestTotalWaitSeconds)))
                            add(
                                DesktopCourseAnalysisSummaryRow(
                                    "Renumbering improvement",
                                    summaryDurationText(
                                        (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)
                                            .coerceAtLeast(0)
                                    )
                                )
                            )
                        }
                    }
                }
            ),
            DesktopCourseAnalysisSummaryGroup(
                title = "Calculated",
                rows = buildList {
                    add(DesktopCourseAnalysisSummaryRow("Routes compared", calculatedRouteCount.toString()))
                    if (calculatedSection == null) {
                        add(DesktopCourseAnalysisSummaryRow("Calculated route", "Unavailable"))
                    } else {
                        add(
                            DesktopCourseAnalysisSummaryRow(
                                "Order comparison",
                                when (idealOrderMatches) {
                                    true -> "Imported and calculated routes match"
                                    false -> "Calculated route differs from imported route"
                                    null -> "Unknown"
                                }
                            )
                        )
                        if (calculatedSection.summaryOnly) {
                            add(DesktopCourseAnalysisSummaryRow("Result", calculatedSection.routeOrder.joinToString(" -> ").ifBlank { "Unknown" }))
                        } else {
                            add(DesktopCourseAnalysisSummaryRow("Ideal route", calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }))
                            add(DesktopCourseAnalysisSummaryRow("Horizontal length", summaryLengthText(calculatedSection.routeLengthMeters)))
                            add(DesktopCourseAnalysisSummaryRow("Climb", summaryClimbText(calculatedSection.climbMeters)))
                            add(DesktopCourseAnalysisSummaryRow("Effective length", summaryLengthText(calculatedSection.effectiveLengthMeters)))
                            add(DesktopCourseAnalysisSummaryRow("Estimated ideal time", summaryDurationText(calculatedSection.estimatedIdealSeconds)))
                            val calculatedWaitSeconds = calculatedSection.waitRows.sumOf { it.waitSeconds }
                            if (calculatedSection.waitRows.isNotEmpty()) {
                                add(DesktopCourseAnalysisSummaryRow("Optimized wait", summaryDurationText(calculatedWaitSeconds)))
                            }
                        }
                    }
                }
            )
        )

    private fun courseRecommendation(
        calculatedRouteApplication: DesktopCourseCalculatedRouteApplication?,
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        lengthRequirement: CourseRuleRequirement?,
        categoryName: String,
        calculatedIdealOrder: List<String>,
        idealOrderMatches: Boolean?,
        waitRenumbering: DesktopCourseWaitRenumbering?
    ): DesktopCourseRecommendation {
        if (calculatedRouteApplication != null) {
            val storedLength = providedSection?.comparisonLengthMeters
            val calculatedLength = calculatedSection?.comparisonLengthMeters
            val shorterByMeters = if (storedLength != null && calculatedLength != null && calculatedLength < storedLength) {
                storedLength - calculatedLength
            } else {
                null
            }
            val reason = if (shorterByMeters != null && calculatedLength != null) {
                val percentLonger = if (calculatedLength > 0) {
                    (shorterByMeters.toDouble() / calculatedLength.toDouble() * 100.0).roundToInt()
                } else {
                    null
                }
                val percentText = percentLonger?.let { " ($it%)" }.orEmpty()
                val routeOrderText = calculatedIdealOrder.joinToString(" -> ").ifBlank { "Unknown" }
                "The imported route is ${summaryLengthText(shorterByMeters)}$percentText longer than the ideal route. The calculated ideal route effective length (${summaryLengthText(calculatedLength)}) should therefore be used as the course's effective length for $categoryName, and the ideal route order is $routeOrderText with calculated fox numbering as shown in the 2D route depiction graphic below."
            } else {
                "The calculated solution differs from the imported route under the current model, so applying it will replace the imported route and numbering with the calculated candidate."
            }
            val caveats = recommendationCaveats(
                calculatedRouteApplication = calculatedRouteApplication,
                providedSection = providedSection,
                calculatedSection = calculatedSection,
                lengthRequirement = lengthRequirement,
                idealOrderMatches = idealOrderMatches,
                waitRenumbering = waitRenumbering
            )
            return DesktopCourseRecommendation(
                actionLabel = "Apply Calculated Route",
                paragraph = "If map information or other data do not impact the analysis results, Radio-Oracle recommends Apply Calculated Route. $reason$caveats"
            )
        }
        val renumbering = waitRenumbering?.takeIf { it.improvesWait }
        if (idealOrderMatches == true && renumbering != null) {
            val improvementSeconds = (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)
                .coerceAtLeast(0)
            val caveats = recommendationCaveats(
                calculatedRouteApplication = calculatedRouteApplication,
                providedSection = providedSection,
                calculatedSection = calculatedSection,
                lengthRequirement = lengthRequirement,
                idealOrderMatches = idealOrderMatches,
                waitRenumbering = waitRenumbering
            )
            return DesktopCourseRecommendation(
                actionLabel = "Apply Fox Renumbering Only",
                paragraph = "If map information or other data do not impact the analysis results, Radio-Oracle recommends Apply Fox Renumbering Only. The calculated route matches the imported route, but renumbering the foxes reduces modeled wait time by ${compactDurationText(improvementSeconds)}.$caveats"
            )
        }
        val caveats = recommendationCaveats(
            calculatedRouteApplication = calculatedRouteApplication,
            providedSection = providedSection,
            calculatedSection = calculatedSection,
            lengthRequirement = lengthRequirement,
            idealOrderMatches = idealOrderMatches,
            waitRenumbering = waitRenumbering
        )
        return DesktopCourseRecommendation(
            actionLabel = "Use the imported data as is",
            paragraph = "If map information or other data do not impact the analysis results, Radio-Oracle recommends Use the imported data as is. The current analysis did not identify a calculated route or fox-renumbering change that should be applied.$caveats"
        )
    }

    private fun recommendationCaveats(
        calculatedRouteApplication: DesktopCourseCalculatedRouteApplication?,
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        lengthRequirement: CourseRuleRequirement?,
        idealOrderMatches: Boolean?,
        waitRenumbering: DesktopCourseWaitRenumbering?
    ): String =
        listOfNotNull(
            calculatedLengthRangeCaveat(
                calculatedSection = calculatedSection,
                providedSection = providedSection,
                lengthRequirement = lengthRequirement,
                idealOrderMatches = idealOrderMatches
            ),
            waitTimeRecommendationCaveat(
                calculatedRouteApplication = calculatedRouteApplication,
                providedSection = providedSection,
                calculatedSection = calculatedSection,
                waitRenumbering = waitRenumbering
            )
        )
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " ", prefix = " ")
            .orEmpty()

    private fun calculatedLengthRangeCaveat(
        calculatedSection: DesktopCourseAnalysisSection?,
        providedSection: DesktopCourseAnalysisSection?,
        idealOrderMatches: Boolean?,
        lengthRequirement: CourseRuleRequirement?
    ): String? {
        val calculatedLength = calculatedSection?.comparisonLengthMeters
            ?: providedSection?.comparisonLengthMeters?.takeIf { idealOrderMatches == true }
            ?: return null
        val requirement = lengthRequirement ?: return null
        if (calculatedLength in requirement.minLengthMeters..requirement.maxLengthMeters) {
            return null
        }
        val measurement = (
            calculatedSection?.comparisonLengthLabel
                ?: providedSection?.comparisonLengthLabel
                ?: "comparison length"
            ).lowercase()
        return "Caveat: the calculated ideal route's $measurement is ${summaryLengthText(calculatedLength)}, outside the ${requirement.lengthRangeText()} rules range. The calculated ideal route is still the honest representation of the course's overall difficulty and should be used when describing the course length. The better course-design action is to redesign the course by moving the start, finish, or foxes until the calculated ideal route effective length falls inside the rules range for the category."
    }

    private fun waitTimeRecommendationCaveat(
        calculatedRouteApplication: DesktopCourseCalculatedRouteApplication?,
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        waitRenumbering: DesktopCourseWaitRenumbering?
    ): String? {
        val caveats = mutableListOf<String>()
        val currentLongWaits = providedSection?.waitRows.orEmpty().filter { it.waitSeconds > COURSE_RECOMMENDATION_WAIT_SECONDS }
        if (calculatedRouteApplication == null && waitRenumbering?.improvesWait == true && currentLongWaits.isNotEmpty()) {
            caveats += "One or more waits with the current fox numbering exceed $COURSE_RECOMMENDATION_WAIT_SECONDS seconds; use the suggested fox numbering to reduce avoidable waiting."
        }
        val bestCaseWaitRows = when {
            calculatedRouteApplication != null -> calculatedSection?.waitRows.orEmpty()
            waitRenumbering?.suggestedWaitRows?.isNotEmpty() == true -> waitRenumbering.suggestedWaitRows
            calculatedSection?.waitRows?.isNotEmpty() == true -> calculatedSection.waitRows
            else -> providedSection?.waitRows.orEmpty()
        }
        val bestLongWaits = bestCaseWaitRows.filter { it.waitSeconds > COURSE_RECOMMENDATION_WAIT_SECONDS }
        if (bestLongWaits.isNotEmpty()) {
            val longestWait = bestLongWaits.maxOf { it.waitSeconds }
            val waitContext = if (calculatedRouteApplication != null) {
                "The calculated ideal route, with calculated fox numbering, still has"
            } else {
                "Even with the best-case fox numbering, the ideal route still has"
            }
            caveats += "$waitContext a fox wait longer than $COURSE_RECOMMENDATION_WAIT_SECONDS seconds (longest modeled wait ${compactDurationText(longestWait)}). Explore course modifications, such as moving the locations of some foxes, to reduce wait time at the affected foxes."
        }
        return caveats.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun goodnessMetrics(
        metrics: List<DesktopCourseGoodnessMetric>,
        providedSection: DesktopCourseAnalysisSection?,
        calculatedSection: DesktopCourseAnalysisSection?,
        raceType: RaceType,
        categoryName: String
    ): DesktopCourseGoodnessMetrics {
        val sharedMetrics = metrics.filter { it.isSharedGoodnessMetric() }
        val comparisonSection = calculatedSection
            ?.takeUnless { it.summaryOnly }
            ?: providedSection
        val targetSeconds = targetSecondsFor(raceType)
        val appliesClimbLimit = raceType == RaceType.CLASSIC || raceType == RaceType.SHORT
        val importedMetrics = providedSection?.let { section ->
            routeGoodnessMetrics(
                title = "Imported",
                section = section,
                shortestRouteMetric = shortestRouteMetric(
                    label = "Imported route is shortest possible route",
                    routeLabel = "imported",
                    routeComparisonLengthMeters = section.comparisonLengthMeters,
                    shortestComparisonLengthMeters = comparisonSection?.comparisonLengthMeters
                ),
                targetSeconds = targetSeconds,
                appliesClimbLimit = appliesClimbLimit
            )
        }.orEmpty()
        val calculatedMetricSection = calculatedSection
            ?.takeUnless { it.summaryOnly }
            ?: providedSection
        val calculatedMetrics = calculatedMetricSection?.let { section ->
            routeGoodnessMetrics(
                title = "Calculated",
                section = section,
                shortestRouteMetric = shortestRouteMetric(
                    label = "Calculated route is shortest possible route",
                    routeLabel = "calculated",
                    routeComparisonLengthMeters = section.comparisonLengthMeters,
                    shortestComparisonLengthMeters = comparisonSection?.comparisonLengthMeters
                ),
                targetSeconds = targetSeconds,
                appliesClimbLimit = appliesClimbLimit
            )
        }.orEmpty()
        return DesktopCourseGoodnessMetrics(
            sharedMetrics = sharedMetrics,
            groups = listOf(
                DesktopCourseGoodnessMetricGroup("Imported", importedMetrics),
                DesktopCourseGoodnessMetricGroup("Calculated", calculatedMetrics)
            ).filter { it.metrics.isNotEmpty() }
        )
    }

    private fun routeGoodnessMetrics(
        title: String,
        section: DesktopCourseAnalysisSection,
        shortestRouteMetric: DesktopCourseGoodnessMetric,
        targetSeconds: Int,
        appliesClimbLimit: Boolean
    ): List<DesktopCourseGoodnessMetric> =
        buildList {
            add(shortestRouteMetric)
            add(climbPercentMetric(section, appliesClimbLimit))
            add(waitTotalMetric(section.waitRows))
            section.waitRenumbering
                ?.takeIf { it.improvesWait }
                ?.let { renumbering ->
                    add(
                        DesktopCourseGoodnessMetric(
                            "Total ideal-route wait time with renumbering",
                            compactDurationText(renumbering.bestTotalWaitSeconds),
                            if (renumbering.bestTotalWaitSeconds == 0) {
                                DesktopCourseMetricStatus.Good
                            } else {
                                DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                }
            add(challengeMetric(section.estimatedIdealSeconds, targetSeconds))
            section.waitRenumbering
                ?.takeIf { it.improvesWait && section.estimatedIdealSeconds != null }
                ?.let { renumbering ->
                    val improvementSeconds = (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)
                        .coerceAtLeast(0)
                    val renumberedIdealSeconds = (requireNotNull(section.estimatedIdealSeconds) - improvementSeconds)
                        .coerceAtLeast(0)
                    add(
                        DesktopCourseGoodnessMetric(
                            "$title route finish time with renumbering",
                            "${compactDurationText(renumberedIdealSeconds)} / ${compactDurationText(targetSeconds)}",
                            if (abs(renumberedIdealSeconds - targetSeconds) <= targetSeconds * 0.15) {
                                DesktopCourseMetricStatus.Good
                            } else {
                                DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                }
            section.ruleChecks
                .filterNot { it.isSharedGoodnessMetric() }
                .forEach(::add)
        }

    private fun climbPercentMetric(section: DesktopCourseAnalysisSection, appliesClimbLimit: Boolean): DesktopCourseGoodnessMetric {
        val routeLengthMeters = section.routeLengthMeters
        val climbMeters = section.climbMeters
        val percent = if (routeLengthMeters != null && routeLengthMeters > 0 && climbMeters != null) {
            climbMeters.toDouble() / routeLengthMeters.toDouble() * 100.0
        } else {
            null
        }
        return DesktopCourseGoodnessMetric(
            "Climb percent of horizontal length",
            if (percent == null || routeLengthMeters == null || climbMeters == null) {
                if (appliesClimbLimit) "Unknown (limit 6.0%)" else "Unknown"
            } else {
                val lengthKm = routeLengthMeters.toDouble() / 1000.0
                val result = "$climbMeters m / ${twoDecimals(lengthKm)} km = ${oneDecimal(percent)}%"
                if (appliesClimbLimit) "$result (limit 6.0%)" else result
            },
            when {
                percent == null -> DesktopCourseMetricStatus.Unknown
                percent > 6.0 -> DesktopCourseMetricStatus.Warning
                else -> DesktopCourseMetricStatus.Good
            }
        )
    }

    private fun waitTotalMetric(waitRows: List<DesktopCourseWaitRow>): DesktopCourseGoodnessMetric {
        val totalWait = waitRows.sumOf { it.waitSeconds }
        return DesktopCourseGoodnessMetric(
            "Total ideal-route wait time",
            if (waitRows.isEmpty()) "Unknown" else compactDurationText(totalWait),
            when {
                waitRows.isEmpty() -> DesktopCourseMetricStatus.Unknown
                totalWait == 0 -> DesktopCourseMetricStatus.Good
                else -> DesktopCourseMetricStatus.Warning
            }
        )
    }

    private fun challengeMetric(estimatedIdealSeconds: Int?, targetSeconds: Int): DesktopCourseGoodnessMetric =
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

    private fun targetSecondsFor(raceType: RaceType): Int =
        when (raceType) {
            RaceType.SPRINT -> SPRINT_TARGET_SECONDS
            RaceType.FOXORING -> FOXORING_TARGET_SECONDS
            else -> CLASSIC_TARGET_SECONDS
        }

    private fun DesktopCourseGoodnessMetric.isSharedGoodnessMetric(): Boolean =
        label.startsWith("Calculated route agrees with imported route order") ||
            label.startsWith("Classic ") ||
            label.startsWith("Youth Classic ") ||
            label.startsWith("Sprint ") ||
            label.startsWith("Foxoring ")

    private fun summaryLengthText(value: Int?): String =
        value?.let { "${twoDecimals(it / 1000.0)} km" } ?: "Unknown"

    private fun summaryClimbText(value: Int?): String =
        value?.let { "$it m" } ?: "Unknown"

    private fun summaryDurationText(value: Int?): String =
        value?.let(::compactDurationText) ?: "Unknown"

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
        if (beacon == null || beacon.point == null) {
            missing += "Sprint loop route calculation requires a beacon control; a spectator cannot replace the beacon."
            return null
        }
        val transitionControl = spectator ?: beacon
        val transitionPoint = transitionControl.point
        if (transitionPoint == null) {
            missing += "Sprint loop route calculation requires a spectator or the beacon as the transition control."
            return null
        }
        val slowFoxes = foxes.filterNot { it.control.isSprintFastFox() }
        val fastFoxes = foxes.filter { it.control.isSprintFastFox() }
        val firstLoop = boundedLoopRoute(
            start = start,
            finish = transitionPoint,
            controls = slowFoxes,
            elevationLookup = elevationLookup,
            note = "Sprint first loop"
        )
        val secondLoop = boundedLoopRoute(
            start = transitionPoint,
            finish = finish,
            controls = fastFoxes,
            beacon = beacon,
            elevationLookup = elevationLookup,
            note = "Sprint fast loop"
        )
        val controls = firstLoop.controls + transitionControl + secondLoop.controls
        val routePoints = listOf(start) + controls.mapNotNull { it.point } + finish
        val transitionText = if (spectator != null) {
            "the assigned spectator"
        } else {
            "the beacon as the slow-to-fast transition"
        }
        return CalculatedRoute(
            controls = controls,
            distanceMeters = routePoints.straightLineMeters(),
            routeCount = firstLoop.routeCount + secondLoop.routeCount,
            calculationNote = "Sprint route calculated as separate first and fast loops using $transitionText; each loop used no more than $MAX_SPRINT_LOOP_PERMUTATIONS permutations."
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
            /*
             * Larger Foxoring courses cannot be exhaustively searched: 10 foxes would require
             * 10! route orders, and 12 foxes would require 12! route orders. Instead, build two
             * practical candidates and keep whichever is shorter under the same comparison metric
             * used everywhere else in the analyzer:
             *
             * 1. Nearest-neighbor plus 2-opt: fast, broad, and good at removing obvious
             *    crossings or backtracking.
             * 2. Rolling five-control exhaustive windows plus final 2-opt: slower, but it gives
             *    each local group a true exhaustive ordering before the whole route is cleaned up.
             *
             * The beacon is not part of the fox permutation. It remains a terminal control after
             * all foxes and before finish, matching Foxoring course structure and the general rule
             * that the beacon should be near the finish.
             */
            val nearestNeighborCandidate = heuristicRoute(
                start = start,
                finish = finish,
                controlsToPermute = foxes,
                beacon = beacon,
                elevationLookup = elevationLookup,
                calculationNote = null
            )
            val rollingWindowCandidate = rollingWindowFoxoringRoute(
                start = start,
                finish = finish,
                controlsToPermute = foxes,
                beacon = beacon,
                elevationLookup = elevationLookup
            )
            // Compare the two full candidates by effective length when every sampled point has
            // elevation; otherwise compare horizontal length. This keeps Foxoring behavior aligned
            // with Classic and Sprint route selection.
            val best = listOf(nearestNeighborCandidate, rollingWindowCandidate)
                .minBy { calculatedRouteComparisonLength(start, finish, it, elevationLookup) }
            best.copy(
                routeCount = nearestNeighborCandidate.routeCount + rollingWindowCandidate.routeCount,
                calculationNote = "Foxoring route uses a non-exhaustive hybrid search because more than $FOXORING_EXHAUSTIVE_CONTROLS foxes are assigned: nearest-neighbor plus 2-opt is compared with a rolling $FOXORING_ROLLING_WINDOW_CONTROLS-control exhaustive-window route followed by full-route 2-opt."
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
        // Sprint loops are optimized separately to keep the search bounded. Above the permutation
        // cap, fall back to the same effective-length-aware heuristic used for larger foxoring
        // control sets.
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
            // Effective length decides the winner only when every sampled point has elevation.
            // Otherwise the calculated-route search intentionally falls back to horizontal length.
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
        calculationNote: String?
    ): CalculatedRoute {
        // The nearest-neighbor seed is fast, and 2-opt then removes obvious crossing/ordering
        // mistakes using the same effective-length comparison as the exhaustive search.
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

    /**
     * Builds the slower Foxoring candidate using a moving five-fox window.
     *
     * The goal is to get more local ordering quality than nearest-neighbor alone without trying all
     * N! fox orders. Each five-fox window is exhaustively optimized from the current route anchor
     * toward the terminal point. After the best local order is found, the first fox is committed to
     * the route, the window slides forward by one, and the next remaining fox nearest to the last
     * window fox is added. When all foxes have entered the rolling window, a full-route 2-opt pass
     * is applied so earlier local decisions can still be improved by segment reversals.
     */
    private fun rollingWindowFoxoringRoute(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controlsToPermute: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): CalculatedRoute {
        if (controlsToPermute.size <= FOXORING_ROLLING_WINDOW_CONTROLS) {
            return shortestPermutation(start, finish, controlsToPermute, beacon, elevationLookup)
        }
        // Use the beacon as the planning target when it exists because the final route must pass
        // through it before finish. If there is no beacon data, fall back to finish so the heuristic
        // still produces a candidate for partial analyses.
        val terminalPoint = beacon?.point ?: finish
        val remaining = controlsToPermute.toMutableList()
        var window = initialFoxoringRollingWindow(start, finish, remaining)
        remaining.removeAll(window.toSet())

        var routeCount = 0
        var anchor = start
        val lockedControls = mutableListOf<ControlAnalysisPoint>()
        var optimizedWindow = optimizedControlWindow(anchor, terminalPoint, window, elevationLookup).also {
            routeCount += it.routeCount
        }.controls

        // Each pass locks the first control from the optimized window, slides the next four
        // controls forward, adds the nearest remaining fox to the previous window end, and
        // re-optimizes that five-control segment toward the beacon/finish terminal.
        while (remaining.isNotEmpty()) {
            val locked = optimizedWindow.firstOrNull() ?: break
            lockedControls += locked
            anchor = locked.point ?: anchor
            // Greedily choose the next fox from the end of the currently optimized window, not
            // from the just-locked fox. This makes the look-ahead window advance in the direction
            // the local optimizer is already favoring.
            val lastWindowPoint = optimizedWindow.lastOrNull()?.point ?: anchor
            val next = remaining.minBy { control ->
                control.point?.distanceMetersTo(lastWindowPoint) ?: Double.POSITIVE_INFINITY
            }
            remaining -= next
            window = optimizedWindow.drop(1) + next
            optimizedWindow = optimizedControlWindow(anchor, terminalPoint, window, elevationLookup).also {
                routeCount += it.routeCount
            }.controls
        }

        val rollingControls = lockedControls + optimizedWindow
        // The rolling windows optimize local neighborhoods. A final 2-opt pass over the entire
        // route lets the candidate remove larger backtracking/crossing patterns introduced by
        // earlier window locks.
        val improved = twoOptOrder(start, finish, rollingControls.distinctBy { it.control.id }, beacon, elevationLookup)
        val controls = if (beacon != null) improved + beacon else improved
        val points = listOf(start) + controls.mapNotNull { it.point } + finish
        return CalculatedRoute(
            controls = controls,
            distanceMeters = points.straightLineMeters(),
            routeCount = routeCount,
            calculationNote = null
        )
    }

    private fun initialFoxoringRollingWindow(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>
    ): List<ControlAnalysisPoint> {
        val byStartDistance = controls.sortedBy { control ->
            control.point?.distanceMetersTo(start) ?: Double.POSITIVE_INFINITY
        }
        // Avoid seeding the first window with foxes that are already closer to finish than start.
        // Those controls are more likely to belong late in a Foxoring route, and including them in
        // the first local group can lock the rolling heuristic into a finish-side detour too early.
        val startSideControls = byStartDistance.filter { control ->
            val point = control.point ?: return@filter false
            point.distanceMetersTo(start) <= point.distanceMetersTo(finish)
        }
        val fillControls = byStartDistance.filterNot { control -> control in startSideControls }
        // Prefer foxes that are on the start side of the course for the first rolling window. If
        // there are fewer than five, fill from the remaining nearest foxes so sparse layouts still
        // produce a complete optimization window.
        return (startSideControls + fillControls)
            .take(FOXORING_ROLLING_WINDOW_CONTROLS)
    }

    private fun optimizedControlWindow(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): CalculatedRoute {
        var bestControls = controls
        var bestComparisonLength = Double.POSITIVE_INFINITY
        var routeCount = 0
        // A five-control window is only 5! = 120 permutations, so each local segment can be solved
        // exactly while keeping a 10-12 fox course practical. The finish parameter is the look-ahead
        // anchor for this window, normally the beacon location.
        controls.permutations().forEach { permutation ->
            routeCount++
            val comparisonLength = routeComparisonLength(start, finish, permutation, null, elevationLookup)
            if (comparisonLength < bestComparisonLength) {
                bestComparisonLength = comparisonLength
                bestControls = permutation
            }
        }
        val points = listOf(start) + bestControls.mapNotNull { it.point } + finish
        return CalculatedRoute(
            controls = bestControls,
            distanceMeters = points.straightLineMeters(),
            routeCount = routeCount,
            calculationNote = null
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

    private fun calculatedRouteComparisonLength(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        route: CalculatedRoute,
        elevationLookup: (CourseGeoPoint) -> Double?
    ): Double {
        val sampled = sampledCalculatedRoutePoints(start, route.controls, finish, elevationLookup)
        return effectiveLengthMetersOrNull(sampled) ?: (listOf(start) + route.controls.mapNotNull { it.point } + finish).straightLineMeters()
    }

    private fun routeGeometryTiming(
        route: List<CourseGeoPoint>,
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>,
        raceType: RaceType,
        speedModel: DesktopCourseSpeedModel,
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
                estimatedIdealSecondsDouble(segment, speedModel)
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
            // Service time is part of the course clock. If a Classic fox is off the air, waiting
            // and the find/punch allowance delay all later legs and may change later waits.
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
        speedModel: DesktopCourseSpeedModel,
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
            val movementSeconds = estimatedIdealSecondsDouble(legPoints, speedModel) ?: 0.0
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
        labelOverrides: Map<String, String> = emptyMap(),
        includeFinish: Boolean = false
    ): List<String> =
        routeLabelsWithFinish(
            listOf("S") + controls.map { labelOverrides[it.control.id] ?: it.control.analysisRouteLabel() },
            includeFinish
        )

    private fun eventControlRouteLabels(controls: List<EventControl>, includeFinish: Boolean = false): List<String> =
        routeLabelsWithFinish(listOf("S") + controls.map { it.analysisRouteLabel() }, includeFinish)

    private fun routeLabelsWithFinish(labels: List<String>, includeFinish: Boolean): List<String> =
        if (includeFinish && labels.lastOrNull() != "F") {
            labels + "F"
        } else {
            labels
        }

    private fun estimatedIdealSecondsDouble(route: List<CourseGeoPoint>, speedModel: DesktopCourseSpeedModel): Double? {
        if (route.size < 2) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) -> segmentSeconds(start, end, speedModel) }
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

    private fun sameCourseCategoryNames(projectFile: EventProjectFile, categoryId: String): List<String> {
        val categoryData = projectFile.raceData.categories.firstOrNull { it.category.id == categoryId }
            ?: return emptyList()
        val targetControlIds = assignedControlIds(categoryData)
        if (targetControlIds.isEmpty()) {
            return listOf(categoryData.category.name)
        }
        return projectFile.raceData.categories
            .filter { assignedControlIds(it) == targetControlIds }
            .sortedBy { it.category.order }
            .map { it.category.name }
            .ifEmpty { listOf(categoryData.category.name) }
    }

    private fun assignedControlIds(categoryData: EventCategoryData): List<String> =
        if (categoryData.controlPoints.isNotEmpty()) {
            categoryData.controlPoints
                .sortedBy { it.order }
                .map { it.controlId }
        } else {
            categoryData.publicControlIds
        }

    private fun List<EventControl>.withTerminalBeacon(beacon: EventControl?): List<EventControl> =
        if (beacon == null || any { it.id == beacon.id }) {
            this
        } else {
            this + beacon
        }

    private fun ProtectedCourseInfo.effectiveCourseObjectPoints(): List<ProtectedCourseObjectPoint> =
        buildList {
            addAll(courseObjects)
            val existingIds = courseObjects.mapTo(mutableSetOf()) { it.id }
            controlPoints.forEach { controlPoint ->
                if (controlPoint.controlId in existingIds) {
                    return@forEach
                }
                add(
                    ProtectedCourseObjectPoint(
                        id = controlPoint.controlId,
                        label = controlPoint.label,
                        type = controlPoint.type.toProtectedCourseObjectType(),
                        latitude = controlPoint.latitude,
                        longitude = controlPoint.longitude,
                        elevationMeters = controlPoint.elevationMeters
                    )
                )
            }
        }

    private fun normalizedImportedRoute(
        route: List<CourseGeoPoint>,
        courseObjects: List<ProtectedCourseObjectPoint>
    ): List<CourseGeoPoint> {
        if (route.isEmpty()) {
            return route
        }
        val start = courseObjects
            .firstOrNull { it.type == ProtectedCourseObjectType.START }
            ?.toGeoPoint()
            // A bad or ambiguous import must not let the spectator masquerade as the course start.
            // When that happens, trust the route LineString endpoint instead of prepending spectator.
            ?.takeUnless { it.matchesCourseObjectType(ProtectedCourseObjectType.SPECTATOR, courseObjects) }
        val finish = courseObjects.firstOrNull { it.type == ProtectedCourseObjectType.FINISH }?.toGeoPoint()
        val beacon = courseObjects.firstOrNull { it.type == ProtectedCourseObjectType.BEACON }?.toGeoPoint()
        val orientedRoute = when {
            start != null &&
                route.last().matchesCourseEndpoint(start, ProtectedCourseObjectType.START, courseObjects) &&
                !route.first().matchesCourseEndpoint(start, ProtectedCourseObjectType.START, courseObjects) ->
                route.asReversed()
            finish != null &&
                route.first().matchesCourseEndpoint(finish, ProtectedCourseObjectType.FINISH, courseObjects) &&
                !route.last().matchesCourseEndpoint(finish, ProtectedCourseObjectType.FINISH, courseObjects) ->
                route.asReversed()
            else -> route
        }
        val routeStartingAtStart = if (start == null) {
            orientedRoute
        } else if (orientedRoute.first().matchesCourseEndpoint(start, ProtectedCourseObjectType.START, courseObjects)) {
            listOf(start) + orientedRoute.drop(1)
        } else {
            listOf(start) + orientedRoute
        }
        val routeEndingAtFinish = if (finish == null) {
            routeStartingAtStart
        } else if (routeStartingAtStart.last().matchesCourseEndpoint(finish, ProtectedCourseObjectType.FINISH, courseObjects)) {
            routeStartingAtStart.dropLast(1) + finish
        } else {
            routeStartingAtStart + finish
        }
        if (beacon == null || routeEndingAtFinish.any { it.sameRouteStop(beacon) }) {
            return routeEndingAtFinish
        }
        // A provided beacon is a mandatory course point even if the imported route LineString
        // omitted it, so force it into the measured route immediately before the finish.
        return if (routeEndingAtFinish.size == 1) {
            listOf(beacon, routeEndingAtFinish.last())
        } else {
            routeEndingAtFinish.dropLast(1) + beacon + routeEndingAtFinish.last()
        }
    }

    private fun CourseGeoPoint.matchesCourseEndpoint(
        endpoint: CourseGeoPoint,
        endpointType: ProtectedCourseObjectType,
        courseObjects: List<ProtectedCourseObjectPoint>
    ): Boolean {
        val endpointDistance = distanceMetersTo(endpoint)
        if (endpointDistance > ROUTE_STOP_TOLERANCE_METERS) {
            return false
        }
        val closerCourseObjectDistance = courseObjects
            .asSequence()
            .filterNot { it.type == endpointType }
            .map { distanceMetersTo(it.toGeoPoint()) }
            .minOrNull()
        return endpointDistance <= ROUTE_ENDPOINT_EXACT_TOLERANCE_METERS ||
            closerCourseObjectDistance == null ||
            endpointDistance < closerCourseObjectDistance
    }

    private fun CourseGeoPoint.sameRouteStop(other: CourseGeoPoint): Boolean =
        distanceMetersTo(other) <= ROUTE_STOP_TOLERANCE_METERS

    private fun CourseGeoPoint.matchesCourseObjectType(
        type: ProtectedCourseObjectType,
        courseObjects: List<ProtectedCourseObjectPoint>
    ): Boolean =
        courseObjects.any { it.type == type && distanceMetersTo(it.toGeoPoint()) <= ROUTE_STOP_TOLERANCE_METERS }

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

    private fun providedKmlCourseObjects(
        route: List<CourseGeoPoint>,
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>
    ): List<DesktopCourseKmlExportPoint> =
        buildList {
            route.firstOrNull()?.let { start ->
                add(DesktopCourseKmlExportPoint("Start", null, start, DesktopCourseKmlExportPointType.START))
            }
            controls
                .filter {
                    it.type == ControlPointType.CONTROL ||
                        it.type == ControlPointType.BEACON ||
                        it.type == ControlPointType.SEPARATOR
                }
                .mapNotNull { control ->
                    val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
                    DesktopCourseKmlExportPoint(
                        label = control.analysisRouteLabel(),
                        originalLabel = null,
                        point = point,
                        type = control.kmlExportPointType()
                    )
                }
                .forEach(::add)
            route.lastOrNull()?.let { finish ->
                add(DesktopCourseKmlExportPoint("Finish", null, finish, DesktopCourseKmlExportPointType.FINISH))
            }
        }

    private fun providedKmlRouteStops(
        route: List<CourseGeoPoint>,
        controls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>
    ): List<DesktopCourseKmlRouteStop> =
        buildList {
            route.firstOrNull()?.let { start ->
                add(DesktopCourseKmlRouteStop("S", start))
            }
            controls.mapNotNull { control ->
                val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
                val routeIndex = route.indices.minByOrNull { route[it].distanceMetersTo(point) } ?: return@mapNotNull null
                routeIndex to DesktopCourseKmlRouteStop(control.analysisRouteLabel(), point)
            }
                .sortedBy { it.first }
                .map { it.second }
                .forEach(::add)
            route.lastOrNull()?.let { finish ->
                add(DesktopCourseKmlRouteStop("F", finish))
            }
        }

    private fun calculatedKmlRouteStops(
        start: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint,
        labelOverrides: Map<String, String>
    ): List<DesktopCourseKmlRouteStop> =
        buildList {
            add(DesktopCourseKmlRouteStop("S", start))
            controls.mapNotNull { controlPoint ->
                val point = controlPoint.point ?: return@mapNotNull null
                DesktopCourseKmlRouteStop(
                    labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel(),
                    point
                )
            }.forEach(::add)
            add(DesktopCourseKmlRouteStop("F", finish))
        }

    private fun calculatedKmlCourseObjects(
        start: CourseGeoPoint,
        controls: List<ControlAnalysisPoint>,
        finish: CourseGeoPoint,
        renumbering: DesktopCourseWaitRenumbering?
    ): List<DesktopCourseKmlExportPoint> {
        val assignmentsByControlLabel = renumbering
            ?.assignments
            .orEmpty()
            .associateBy { it.controlLabel }
        return buildList {
            add(DesktopCourseKmlExportPoint("Start", null, start, DesktopCourseKmlExportPointType.START))
            controls
                .filter {
                    it.control.type == ControlPointType.CONTROL ||
                        it.control.type == ControlPointType.BEACON ||
                        it.control.type == ControlPointType.SEPARATOR
                }
                .mapNotNull { controlPoint ->
                    val point = controlPoint.point ?: return@mapNotNull null
                    val originalLabel = controlPoint.control.analysisRouteLabel()
                    val suggestedLabel = if (controlPoint.control.type == ControlPointType.CONTROL) {
                        assignmentsByControlLabel[controlPoint.control.publicDisplayLabel()]?.suggestedSlotLabel
                            ?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    DesktopCourseKmlExportPoint(
                        label = suggestedLabel ?: originalLabel,
                        originalLabel = originalLabel.takeIf { suggestedLabel != null && suggestedLabel != originalLabel },
                        point = point,
                        type = controlPoint.control.kmlExportPointType()
                    )
                }
                .forEach(::add)
            add(DesktopCourseKmlExportPoint("Finish", null, finish, DesktopCourseKmlExportPointType.FINISH))
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
        routeControls: List<ControlAnalysisPoint> = controls,
        labelOverrides: Map<String, String> = emptyMap()
    ): DesktopCourseRouteMap? {
        fun labelFor(controlPoint: ControlAnalysisPoint): String =
            labelOverrides[controlPoint.control.id] ?: controlPoint.control.analysisRouteLabel()
        val controlsToDisplay = (controls + routeControls).distinctBy { it.control.id }
        val labeledPoints = buildList {
            start?.let { add(RouteMapSourcePoint("S", it, DesktopCourseRouteMapPointType.Start)) }
            controlsToDisplay.forEach { controlPoint ->
                val point = controlPoint.point ?: return@forEach
                add(
                    RouteMapSourcePoint(
                        labelFor(controlPoint),
                        point,
                        controlPoint.control.routeMapType()
                    )
                )
            }
            finish?.let { add(RouteMapSourcePoint("F", it, DesktopCourseRouteMapPointType.Finish)) }
        }
        val routeLabels = buildList {
            if (start != null) {
                add("S")
            }
            routeControls.forEach { controlPoint ->
                if (controlPoint.point != null) {
                    add(labelFor(controlPoint))
                }
            }
            if (finish != null) {
                add("F")
            }
        }
        val routeSources = buildList {
            start?.let { add(RouteMapSourcePoint("S", it, DesktopCourseRouteMapPointType.Start)) }
            routeControls.forEach { controlPoint ->
                val point = controlPoint.point ?: return@forEach
                add(
                    RouteMapSourcePoint(
                        labelFor(controlPoint),
                        point,
                        controlPoint.control.routeMapType()
                    )
                )
            }
            finish?.let { add(RouteMapSourcePoint("F", it, DesktopCourseRouteMapPointType.Finish)) }
        }
        val routePointIndexes = routeSources.mapNotNull { routeSource ->
            labeledPoints.indexOfFirst { labeledPoint ->
                labeledPoint.label == routeSource.label &&
                    labeledPoint.type == routeSource.type &&
                    labeledPoint.point.sameRouteStop(routeSource.point)
            }.takeUnless { it < 0 }
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
            routeLabels = routeLabels,
            routePointIndexes = routePointIndexes
        )
    }

    private fun segmentSeconds(start: CourseGeoPoint, end: CourseGeoPoint, speedModel: DesktopCourseSpeedModel): Double {
        val horizontal = max(1.0, start.distanceMetersTo(end))
        val climb = if (start.elevationMeters != null && end.elevationMeters != null) {
            max(0.0, requireNotNull(end.elevationMeters) - requireNotNull(start.elevationMeters))
        } else {
            0.0
        }
        // Do not also apply a separate gradient-speed model here. Effective length is the elevation
        // compensation: horizontal distance plus ten times positive climb, divided by format pace.
        val movementMeters = horizontal + 10.0 * climb
        return movementMeters / speedModel.effectiveSpeedMetersPerSecond
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
        // The competitor can reach the vicinity before a fox transmits, but the next leg starts
        // only after the fox is on the air and the fixed Classic find/punch allowance is complete.
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
        var bestTiming = RouteTimingAnalysis.Empty
        val currentSlots = foxes.map { RenumberingSlot(it.currentSlotIndex, it.currentSlotLabel) }
        currentSlots.permutations().forEach { candidateSlots ->
            val slotOverrides = foxes.zip(candidateSlots).associate { (fox, slot) -> fox.control.id to slot }
            val timing = timingForSlots(slotOverrides)
            val total = timing.waitTotalFor(foxes)
            if (total < bestTotal) {
                bestTotal = total
                bestSlots = candidateSlots
                bestTiming = timing
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
            },
            suggestedWaitRows = bestTiming.waitRows
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

    private fun routeLengthRequirement(categoryName: String, raceType: RaceType): CourseRuleRequirement? =
        when (raceType) {
            RaceType.CLASSIC, RaceType.SHORT, RaceType.FOXORING -> categoryRequirement(categoryName, raceType)
            RaceType.SPRINT, RaceType.ORIENTEERING -> null
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

    private fun speedModel(
        raceType: RaceType,
        categoryName: String,
        compensationFactor: Double
    ): DesktopCourseSpeedModel {
        val formatSpeed = when (raceType) {
            RaceType.SPRINT -> SPRINT_FLAT_SPEED_MPS
            RaceType.FOXORING -> FOXORING_FLAT_SPEED_MPS
            else -> CLASSIC_FLAT_SPEED_MPS
        }
        val categoryKey = categoryRuleKey(categoryName)
        val categoryMultiplier = CATEGORY_SPEED_FACTOR_TABLE.categoryMultiplier(categoryKey)
        val boundedFactor = compensationFactor
            .takeIf { it.isFinite() }
            ?.coerceIn(0.25, 2.0)
            ?: 1.0
        val effectiveSpeed = (formatSpeed * categoryMultiplier * boundedFactor)
            .coerceAtLeast(MIN_EFFECTIVE_SPEED_MPS)
        return DesktopCourseSpeedModel(
            formatSpeedMetersPerSecond = formatSpeed,
            categorySpeedMultiplier = categoryMultiplier,
            compensationFactor = boundedFactor,
            effectiveSpeedMetersPerSecond = effectiveSpeed,
            categoryModelLabel = categoryKey ?: "unmatched category",
            categoryFactorSourceLabel = CATEGORY_SPEED_FACTOR_TABLE.sourceLabel,
            categoryFactorExplanation = CATEGORY_SPEED_FACTOR_TABLE.explanation
        )
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
        importedComparisonLengthMeters: Int?,
        calculatedComparisonLengthMeters: Int?,
        effectiveLengthMeters: Int?,
        estimatedIdealSeconds: Int?,
        waitRows: List<DesktopCourseWaitRow>,
        waitRenumbering: DesktopCourseWaitRenumbering?,
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
                    "Calculated route agrees with imported route order",
                    idealOrderMatches?.let { if (it) "Yes" else "No" } ?: "Unknown",
                    when (idealOrderMatches) {
                        true -> DesktopCourseMetricStatus.Good
                        false -> DesktopCourseMetricStatus.Warning
                        null -> DesktopCourseMetricStatus.Unknown
                    }
                )
            )
            add(
                shortestRouteMetric(
                    label = "Imported route is shortest possible route",
                    routeLabel = "imported",
                    routeComparisonLengthMeters = importedComparisonLengthMeters,
                    shortestComparisonLengthMeters = calculatedComparisonLengthMeters
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
            val climbMetric = if (idealOrderMatches == true && storedClimbPercent != null && routeLengthMeters != null && climbMeters != null) {
                Triple(storedClimbPercent, routeLengthMeters, climbMeters)
            } else if (calculatedClimbPercent != null && calculatedRouteLengthMeters != null && calculatedRouteClimbMeters != null) {
                Triple(calculatedClimbPercent, calculatedRouteLengthMeters, calculatedRouteClimbMeters)
            } else if (storedClimbPercent != null && routeLengthMeters != null && climbMeters != null) {
                Triple(storedClimbPercent, routeLengthMeters, climbMeters)
            } else {
                null
            }
            val appliesClimbLimit = raceType == RaceType.CLASSIC || raceType == RaceType.SHORT
            add(
                DesktopCourseGoodnessMetric(
                    "Climb percent of horizontal length",
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
            waitRenumbering
                ?.takeIf { it.improvesWait }
                ?.let { renumbering ->
                    add(
                        DesktopCourseGoodnessMetric(
                            "Total ideal-route wait time with renumbering",
                            compactDurationText(renumbering.bestTotalWaitSeconds),
                            if (renumbering.bestTotalWaitSeconds == 0) {
                                DesktopCourseMetricStatus.Good
                            } else {
                                DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                }
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
            waitRenumbering
                ?.takeIf { it.improvesWait && estimatedIdealSeconds != null }
                ?.let { renumbering ->
                    val improvementSeconds = (renumbering.currentTotalWaitSeconds - renumbering.bestTotalWaitSeconds)
                        .coerceAtLeast(0)
                    val renumberedIdealSeconds = (requireNotNull(estimatedIdealSeconds) - improvementSeconds)
                        .coerceAtLeast(0)
                    add(
                        DesktopCourseGoodnessMetric(
                            "Imported route finish time with renumbering",
                            "${compactDurationText(renumberedIdealSeconds)} / ${compactDurationText(targetSeconds)}",
                            if (abs(renumberedIdealSeconds - targetSeconds) <= targetSeconds * 0.15) {
                                DesktopCourseMetricStatus.Good
                            } else {
                                DesktopCourseMetricStatus.Warning
                            }
                        )
                    )
                }
        }
    }

    private fun shortestRouteMetric(
        label: String,
        routeLabel: String,
        routeComparisonLengthMeters: Int?,
        shortestComparisonLengthMeters: Int?
    ): DesktopCourseGoodnessMetric =
        when {
            routeComparisonLengthMeters == null || shortestComparisonLengthMeters == null ->
                DesktopCourseGoodnessMetric(
                    label,
                    "Unknown",
                    DesktopCourseMetricStatus.Unknown
                )
            routeComparisonLengthMeters <= shortestComparisonLengthMeters + 1 ->
                DesktopCourseGoodnessMetric(
                    label,
                    "Yes: $routeLabel ${twoDecimals(routeComparisonLengthMeters / 1000.0)} km; shortest ${twoDecimals(shortestComparisonLengthMeters / 1000.0)} km",
                    DesktopCourseMetricStatus.Good
                )
            else ->
                DesktopCourseGoodnessMetric(
                    label,
                    "No: $routeLabel ${twoDecimals(routeComparisonLengthMeters / 1000.0)} km; shortest ${twoDecimals(shortestComparisonLengthMeters / 1000.0)} km",
                    DesktopCourseMetricStatus.Warning
                )
        }

    private fun List<CourseGeoPoint>.straightLineMeters(): Double =
        zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    private fun List<CourseGeoPoint>.analysisBoundingBoxOrNull(): DesktopVenueElevationBoundingBox? =
        takeIf { it.isNotEmpty() }?.let { points ->
            DesktopVenueElevationBoundingBox(
                minLatitude = points.minOf { it.latitude },
                maxLatitude = points.maxOf { it.latitude },
                minLongitude = points.minOf { it.longitude },
                maxLongitude = points.maxOf { it.longitude }
            )
        }

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

    private fun EventControl.kmlExportPointType(): DesktopCourseKmlExportPointType =
        when (type) {
            ControlPointType.BEACON -> DesktopCourseKmlExportPointType.BEACON
            ControlPointType.SEPARATOR -> DesktopCourseKmlExportPointType.SPECTATOR
            ControlPointType.CONTROL -> DesktopCourseKmlExportPointType.CONTROL
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
    val estimatedSeconds: Double?,
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
