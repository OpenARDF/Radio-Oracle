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
import org.openardf.radiooracle.shared.event.ProtectedIdealOrderRules
import org.openardf.radiooracle.shared.event.effectiveLengthMeters
import org.openardf.radiooracle.shared.time.DurationFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class DesktopCourseAnalysisSummary(
    val categoryName: String,
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
    val elevationProfile: List<DesktopCourseElevationProfilePoint>,
    val waitRows: List<DesktopCourseWaitRow>,
    val waitRenumbering: DesktopCourseWaitRenumbering?,
    val metrics: List<DesktopCourseGoodnessMetric>
)

data class DesktopCourseElevationProfilePoint(
    val distanceMeters: Int,
    val elevationMeters: Double
)

data class DesktopCourseWaitRow(
    val controlLabel: String,
    val arrivalSeconds: Int,
    val waitSeconds: Int
)

data class DesktopCourseWaitRenumbering(
    val currentTotalWaitSeconds: Int,
    val bestTotalWaitSeconds: Int,
    val improvesWait: Boolean,
    val assignments: List<DesktopCourseWaitRenumberingAssignment>
)

data class DesktopCourseWaitRenumberingAssignment(
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

object DesktopCourseAnalyzer {
    private const val CLASSIC_TRANSMIT_CYCLE_SECONDS = 300
    private const val CLASSIC_TRANSMIT_SLOT_SECONDS = 60
    private const val CLASSIC_TARGET_SECONDS = 60 * 60
    private const val SPRINT_TARGET_SECONDS = 15 * 60
    private const val FOXORING_TARGET_SECONDS = 45 * 60
    private const val CLASSIC_FLAT_SPEED_MPS = 3.6
    private const val SPRINT_FLAT_SPEED_MPS = 4.2
    private const val FOXORING_FLAT_SPEED_MPS = 3.4
    private const val MAX_PERMUTATION_CONTROLS = 8

    fun analyze(
        projectFile: EventProjectFile,
        categoryId: String,
        protectedCourseInfo: ProtectedCourseInfo?,
        protectedIdealOrderText: String?
    ): DesktopCourseAnalysisSummary {
        val categoryData = projectFile.raceData.categories.first { it.category.id == categoryId }
        val category = categoryData.category
        val raceType = category.effectiveRaceType(projectFile.raceData.race)
        val assignedControls = assignedControls(projectFile, categoryId)
        val protectedControlPointsById = protectedCourseInfo?.controlPoints.orEmpty().associateBy { it.controlId }
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
        if (route.any { it.elevationMeters == null }) {
            missing += "Route elevation samples are missing or incomplete."
        }
        protectedCourseInfo?.let { courseInfo ->
            when {
                courseInfo.courseObjects.isEmpty() ->
                    missing += "Course object points are missing for start, finish, controls, beacon, or spectator."
                courseInfo.courseObjects.any { it.elevationMeters == null } ->
                    missing += "Course object elevations are missing or incomplete."
            }
        }

        val controlsWithPoints = assignedControls.map { control ->
            ControlAnalysisPoint(
                control = control,
                point = protectedControlPointsById[control.id]?.toGeoPoint()
                    ?: control.latitude?.let { latitude ->
                        control.longitude?.let { longitude -> CourseGeoPoint(latitude, longitude) }
                    }
            )
        }
        controlsWithPoints
            .filter { it.point == null }
            .forEach { missing += "Coordinate is missing for control ${it.control.publicDisplayLabel()}." }

        val start = route.firstOrNull()
        val finish = route.lastOrNull()
        val foxes = controlsWithPoints
            .filter { it.control.type == ControlPointType.CONTROL && it.point != null }
        val beacon = controlsWithPoints
            .firstOrNull { it.control.type == ControlPointType.BEACON && it.point != null }
        val calculatedRoute = if (start != null && finish != null && foxes.isNotEmpty() && foxes.size <= MAX_PERMUTATION_CONTROLS) {
            shortestPermutation(start, finish, foxes, beacon)
        } else {
            if (foxes.size > MAX_PERMUTATION_CONTROLS) {
                missing += "Too many scored controls for exhaustive route calculation: ${foxes.size}."
            }
            null
        }

        val idealOrderText = protectedIdealOrderText?.takeIf { it.isNotBlank() }
            ?: protectedCourseInfo?.idealOrder?.takeIf { it.isNotBlank() }
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
        if (providedControls.isEmpty()) {
            missing += "Protected ideal order is missing for ${category.name}."
        }

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

        val cumulativeArrivalSeconds = routeArrivalSeconds(
            route = route,
            orderedControls = providedControls,
            controlsWithPoints = controlsWithPoints,
            raceType = raceType
        )
        val waitRows = if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
            providedControls
                .filter { it.type == ControlPointType.CONTROL }
                .mapNotNull { control ->
                    val arrival = cumulativeArrivalSeconds[control.id] ?: return@mapNotNull null
                    if (classicSlotIndex(control) == null) {
                        missing += "Transmit slot could not be determined for control ${control.publicDisplayLabel()}."
                    }
                    DesktopCourseWaitRow(
                        controlLabel = control.publicDisplayLabel(),
                        arrivalSeconds = arrival,
                        waitSeconds = waitSecondsForClassicControl(control, arrival)
                    )
                }
        } else {
            emptyList()
        }
        if (waitRows.isEmpty() && providedControls.any { it.type == ControlPointType.CONTROL } && raceType != RaceType.CLASSIC && raceType != RaceType.SHORT) {
            missing += "Transmit-slot wait analysis is currently implemented for Classic-style five-minute cycles only."
        }
        val waitRenumbering = if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
            waitRenumbering(providedControls, cumulativeArrivalSeconds)
        } else {
            null
        }

        val estimatedIdealSeconds = estimatedIdealSeconds(route, raceType)
        val elevationProfile = elevationProfile(route)
        val metrics = goodnessMetrics(
            raceType = raceType,
            routeLengthMeters = protectedCourseInfo?.lengthMeters,
            climbMeters = protectedCourseInfo?.climbMeters,
            effectiveLengthMeters = protectedCourseInfo?.effectiveLengthMeters(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            waitRows = waitRows,
            idealOrderMatches = idealOrderMatches
        )

        return DesktopCourseAnalysisSummary(
            categoryName = category.name,
            missingElements = missing.distinct(),
            calculatedRouteCount = calculatedRoute?.routeCount ?: 0,
            calculatedIdealOrder = calculatedRoute?.controls.orEmpty().map { it.control.publicDisplayLabel() },
            providedIdealOrder = providedControls.map { it.publicDisplayLabel() },
            idealOrderMatches = idealOrderMatches,
            calculatedStraightLineMeters = calculatedRoute?.distanceMeters?.roundToInt(),
            providedStraightLineMeters = providedStraightLineMeters,
            routeLengthMeters = protectedCourseInfo?.lengthMeters,
            climbMeters = protectedCourseInfo?.climbMeters,
            effectiveLengthMeters = protectedCourseInfo?.effectiveLengthMeters(),
            estimatedIdealSeconds = estimatedIdealSeconds,
            elevationProfile = elevationProfile,
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
            .filter { it.type == ControlPointType.CONTROL || it.type == ControlPointType.BEACON }
            .distinctBy { it.id }
    }

    private fun shortestPermutation(
        start: CourseGeoPoint,
        finish: CourseGeoPoint,
        foxes: List<ControlAnalysisPoint>,
        beacon: ControlAnalysisPoint?
    ): CalculatedRoute {
        var bestControls = emptyList<ControlAnalysisPoint>()
        var bestDistance = Double.POSITIVE_INFINITY
        var routeCount = 0
        foxes.permutations().forEach { permutation ->
            routeCount++
            val controls = if (beacon != null) permutation + beacon else permutation
            val points = listOf(start) + controls.mapNotNull { it.point } + finish
            val distance = points.straightLineMeters()
            if (distance < bestDistance) {
                bestDistance = distance
                bestControls = controls
            }
        }
        return CalculatedRoute(bestControls, bestDistance, routeCount)
    }

    private fun routeArrivalSeconds(
        route: List<CourseGeoPoint>,
        orderedControls: List<EventControl>,
        controlsWithPoints: List<ControlAnalysisPoint>,
        raceType: RaceType
    ): Map<String, Int> {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return emptyMap()
        }
        val cumulativeSeconds = mutableListOf(0.0)
        route.zipWithNext().forEach { (start, end) ->
            cumulativeSeconds += cumulativeSeconds.last() + segmentSeconds(start, end, raceType)
        }
        return orderedControls.mapNotNull { control ->
            val point = controlsWithPoints.firstOrNull { it.control.id == control.id }?.point ?: return@mapNotNull null
            val nearestIndex = route.indices.minByOrNull { route[it].distanceMetersTo(point) } ?: return@mapNotNull null
            control.id to cumulativeSeconds[nearestIndex].roundToInt()
        }.toMap()
    }

    private fun estimatedIdealSeconds(route: List<CourseGeoPoint>, raceType: RaceType): Int? {
        if (route.size < 2 || route.any { it.elevationMeters == null }) {
            return null
        }
        return route.zipWithNext()
            .sumOf { (start, end) -> segmentSeconds(start, end, raceType) }
            .roundToInt()
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

    private fun segmentSeconds(start: CourseGeoPoint, end: CourseGeoPoint, raceType: RaceType): Double {
        val horizontal = max(1.0, start.distanceMetersTo(end))
        val gain = (end.elevationMeters ?: 0.0) - (start.elevationMeters ?: 0.0)
        val slope = gain / horizontal
        val flatSpeed = when (raceType) {
            RaceType.SPRINT -> SPRINT_FLAT_SPEED_MPS
            RaceType.FOXORING -> FOXORING_FLAT_SPEED_MPS
            else -> CLASSIC_FLAT_SPEED_MPS
        }
        val penalty = when {
            slope >= 0.0 -> 1.0 + 6.0 * slope
            else -> 1.0 + 2.5 * abs(slope)
        }.coerceIn(0.7, 3.0)
        return horizontal / (flatSpeed / penalty)
    }

    private fun waitSecondsForClassicControl(control: EventControl, arrivalSeconds: Int): Int {
        val slotIndex = classicSlotIndex(control) ?: return 0
        return waitSecondsForClassicSlot(slotIndex, arrivalSeconds)
    }

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

    private fun waitRenumbering(
        providedControls: List<EventControl>,
        cumulativeArrivalSeconds: Map<String, Int>
    ): DesktopCourseWaitRenumbering? {
        val foxes = providedControls
            .filter { it.type == ControlPointType.CONTROL }
            .mapNotNull { control ->
                val arrival = cumulativeArrivalSeconds[control.id] ?: return@mapNotNull null
                val slotIndex = classicSlotIndex(control) ?: return@mapNotNull null
                RenumberingFox(control, arrival, slotIndex, control.publicDisplayLabel())
            }
        if (foxes.size < 2 || foxes.size > MAX_PERMUTATION_CONTROLS) {
            return null
        }
        val currentTotal = foxes.sumOf { waitSecondsForClassicSlot(it.currentSlotIndex, it.arrivalSeconds) }
        var bestTotal = Int.MAX_VALUE
        var bestSlots = emptyList<RenumberingSlot>()
        val currentSlots = foxes.map { RenumberingSlot(it.currentSlotIndex, it.currentSlotLabel) }
        currentSlots.permutations().forEach { candidateSlots ->
            val total = foxes.zip(candidateSlots).sumOf { (fox, slot) ->
                waitSecondsForClassicSlot(slot.slotIndex, fox.arrivalSeconds)
            }
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
                    controlLabel = fox.control.publicDisplayLabel(),
                    currentSlotLabel = fox.currentSlotLabel,
                    suggestedSlotLabel = slot.slotLabel
                )
            }
        )
    }

    private fun goodnessMetrics(
        raceType: RaceType,
        routeLengthMeters: Int?,
        climbMeters: Int?,
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
        return buildList {
            add(
                DesktopCourseGoodnessMetric(
                    "Calculated route agrees with provided ideal order",
                    idealOrderMatches?.let { if (it) "Yes" else "No" } ?: "Unknown",
                    when (idealOrderMatches) {
                        true -> DesktopCourseMetricStatus.Good
                        false -> DesktopCourseMetricStatus.Warning
                        null -> DesktopCourseMetricStatus.Unknown
                    }
                )
            )
            val climbPercent = if (routeLengthMeters != null && routeLengthMeters > 0 && climbMeters != null) {
                climbMeters.toDouble() / routeLengthMeters.toDouble() * 100.0
            } else {
                null
            }
            add(
                DesktopCourseGoodnessMetric(
                    "Climb percent of route length",
                    climbPercent?.let { "${oneDecimal(it)}%" } ?: "Unknown",
                    if (climbPercent == null) {
                        DesktopCourseMetricStatus.Unknown
                    } else if (raceType == RaceType.CLASSIC && climbPercent > 6.0) {
                        DesktopCourseMetricStatus.Warning
                    } else {
                        DesktopCourseMetricStatus.Good
                    }
                )
            )
            add(
                DesktopCourseGoodnessMetric(
                    "Effective length",
                    effectiveLengthMeters?.let { "${oneDecimal(it / 1000.0)} km" } ?: "Unknown",
                    if (effectiveLengthMeters == null) DesktopCourseMetricStatus.Unknown else DesktopCourseMetricStatus.Good
                )
            )
            val totalWait = waitRows.sumOf { it.waitSeconds }
            add(
                DesktopCourseGoodnessMetric(
                    "Total ideal-route wait time",
                    if (waitRows.isEmpty()) "Unknown" else DurationFormatter.secondsToFormattedString(totalWait.toLong(), useMinutes = false),
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
                        "${DurationFormatter.secondsToFormattedString(it.toLong(), useMinutes = false)} / " +
                            DurationFormatter.secondsToFormattedString(targetSeconds.toLong(), useMinutes = false)
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
        CourseGeoPoint(latitude, longitude)

    private fun EventControl.publicDisplayLabel(): String =
        publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label

    private fun oneDecimal(value: Double): String =
        (value * 10.0).roundToInt().let { "${it / 10}.${abs(it % 10)}" }
}

private data class ControlAnalysisPoint(
    val control: EventControl,
    val point: CourseGeoPoint?
)

private data class CalculatedRoute(
    val controls: List<ControlAnalysisPoint>,
    val distanceMeters: Double,
    val routeCount: Int
)

private data class RenumberingFox(
    val control: EventControl,
    val arrivalSeconds: Int,
    val currentSlotIndex: Int,
    val currentSlotLabel: String
)

private data class RenumberingSlot(
    val slotIndex: Int,
    val slotLabel: String
)
