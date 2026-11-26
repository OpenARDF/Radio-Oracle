package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.course.ElevationCoverage
import org.openardf.radiooracle.shared.course.ElevationBounds
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType.*

internal data class CourseElevationFinding(
    val areaHasGap: Boolean = false,
    val controls: Set<String> = emptySet(),
    val legs: Set<String> = emptySet(),
    val otherLegs: Set<PossibleElevationLeg> = emptySet(),
    val unchecked: Set<String> = emptySet()
)

internal data class PossibleElevationLeg(val courseId: String, val description: String,
    val fromLabels: List<String>, val toLabels: List<String>) {
    fun onReportedRoute(legs: List<DesktopCourseBriefLeg>) = legs.any {
        (it.fromLabel in fromLabels && it.toLabel in toLabels) || (it.toLabel in fromLabels && it.fromLabel in toLabels)
    }
}

private data class CoverageStop(val label: String, val point: CourseGeoPoint, val type: ProtectedCourseObjectType,
    val control: EventControl? = null)

private data class CoverageLeg(val from: CoverageStop, val to: CoverageStop, val path: List<CourseGeoPoint>)

/** The checked surface is local to this computer. Stored elevations and route interpolation never fill a cache hole. */
internal fun eventElevationCoverage(
    project: EventProjectFile, unlocked: Map<String, ProtectedCourseInfo>,
    checkCancelled: () -> Unit, loadCoverage: (ElevationBounds, () -> Unit) -> ElevationCoverage = DesktopVenueElevationCache::coverage
): CourseElevationFinding {
    val race = project.raceData
    val courses = race.categories + race.courseMappings
    val infos = courses.mapNotNull { data ->
        (unlocked[data.category.id] ?: data.category.courseInfo.takeIf { data.category.encryptedCourseInfo == null })
            ?.let { data.category.id to it }
    }.toMap()
    val controls = race.controls.associateBy { it.id }
    val sources = infos.values.toList()
    val catalog = race.controls.associate { control ->
        checkCancelled()
        val resolution = CourseControlResolver.resolve(control, sources)
        // Some legacy catalog-only races carry coordinates directly. Never override a conflicting course placement.
        val point = resolution.location?.let { CourseGeoPoint(it.latitude, it.longitude) }
            ?: control.legacyLocation().takeIf { resolution.status == CourseResolutionStatus.MISSING }
        control.id to point
    }
    val unchecked = race.controls.filter { catalog[it.id] == null }.map { it.coverageLabel() }.toSet()
    val bounds = (catalog.values.filterNotNull() + infos.values.flatMap { it.finiteCourseGeoPoints() }).venueBoundingBoxOrNull()
        ?: return CourseElevationFinding(unchecked = unchecked)
    val coverage = loadCoverage(bounds, checkCancelled)
    if (!coverage.hasGap(bounds)) return CourseElevationFinding(unchecked = unchecked)

    val affected = mutableSetOf<String>()
    val legs = mutableSetOf<String>()
    val alternatives = mutableSetOf<PossibleElevationLeg>()
    val segments = mutableMapOf<Pair<CourseGeoPoint, CourseGeoPoint>, Boolean>()
    fun missing(a: CourseGeoPoint, b: CourseGeoPoint): Boolean =
        segments[b to a] ?: segments.getOrPut(a to b) { coverage.legHasGap(a.latitude, a.longitude, b.latitude, b.longitude) }
    race.controls.forEach { control ->
        catalog[control.id]?.let { if (missing(it, it)) affected += control.coverageLabel() }
    }
    for (data in courses) {
        checkCancelled()
        val course = data.category.name
        val info = infos[data.category.id]?.withFiniteCourseGeometry() ?: ProtectedCourseInfo()
        val route = DesktopCourseAnalyzer.normalizedImportedRoute(info)
        val stops = courseStops(data, info, controls, catalog, route)
        stops.forEach { stop ->
            if (stop.type != WAYPOINT && stop.control?.id !in catalog && missing(stop.point, stop.point)) {
                affected += "$course · ${stop.label}"
            }
        }
        val drawn = if (info.route.isEmpty() && info.courseObjects.isEmpty() && race.race.raceType != RaceType.ORIENTEERING)
            emptyList() else storedLegs(info, stops, route)
        fun hasGap(path: List<CourseGeoPoint>): Boolean =
            (1 until path.size).any { missing(path[it - 1], path[it]) }
        for (leg in drawn) {
            if (hasGap(leg.path)) legs += "$course · ${leg.from.label} → ${leg.to.label}"
        }
        val bends = DesktopMandatoryCourseLegs.from(info)
        for ((a, b) in possibleLegs(race.race.raceType, stops)) {
            checkCancelled()
            val path = DesktopMandatoryCourseLegs.expand(listOf(a.point, b.point), bends)
            if (drawn.any { it.from == a && it.to == b && DesktopCourseAnalysisSampling.followsVertices(it.path, path) ||
                    it.from == b && it.to == a && DesktopCourseAnalysisSampling.followsVertices(it.path.asReversed(), path) }) continue
            if (hasGap(path)) alternatives += PossibleElevationLeg(data.category.id, "$course · ${a.label} → ${b.label}", a.routeLabels(), b.routeLabels())
        }
    }
    return CourseElevationFinding(true, affected, legs, alternatives, unchecked)
}

private fun EventControl.coverageLabel() = "${publicLabel ?: label} (SI $siCode)"

private fun EventControl.legacyLocation(): CourseGeoPoint? {
    val lat = latitude?.validLatitudeOrNull() ?: return null
    val lon = longitude?.validLongitudeOrNull() ?: return null
    return CourseGeoPoint(lat, lon)
}

private fun CoverageStop.routeLabels() = listOfNotNull(label, control?.label, control?.publicLabel,
    when (type) { START -> "S"; FINISH -> "F"; BEACON -> "B"; else -> null })

private fun courseStops(data: EventCategoryData, info: ProtectedCourseInfo, controls: Map<String, EventControl>,
    catalog: Map<String, CourseGeoPoint?>, route: List<CourseGeoPoint>): List<CoverageStop> {
    val placements = info.placements().distinctBy { it.id }.associateBy { it.id }
    val bindings = info.appliedBindings
    val bound = bindings?.controls.orEmpty().associate { it.placementId to it.controlId }
    val visits = bindings?.orderedPlacementIds ?: placements.keys.toList()
    val stops = visits.mapNotNull { placements[it] }.map { p ->
        val control = controls[bound[p.id] ?: p.id]?.takeIf { it.type == p.type.controlRole() }
        CoverageStop(control?.publicLabel ?: control?.label ?: p.label, CourseGeoPoint(p.latitude, p.longitude), p.type, control)
    }.toMutableList()
    val assigned = data.controlPoints.sortedBy { it.order }.map { it.controlId }.ifEmpty { data.publicControlIds }
    assigned.forEach { id ->
        val control = controls[id] ?: return@forEach
        val point = catalog[id] ?: return@forEach
        val type = control.type.placementType()
        // Older imports can use geometry IDs distinct from the logical catalog ID.
        val index = stops.indexOfFirst { it.point == point && it.type == type && (it.control == null || it.control.id == id) }
        if (index >= 0) stops[index] = stops[index].copy(label = control.publicLabel ?: control.label, control = control)
        else if (stops.none { it.control?.id == id }) stops += CoverageStop(control.publicLabel ?: control.label, point, type, control)
    }
    if (route.size >= 2) {
        fun endpoint(type: ProtectedCourseObjectType, label: String, point: CourseGeoPoint) {
            val index = stops.indexOfFirst { it.type == type }
            val location = CourseGeoPoint(point.latitude, point.longitude)
            if (index >= 0) stops[index] = stops[index].copy(point = location)
            else stops.add(if (type == START) 0 else stops.size, CoverageStop(label, location, type))
        }
        endpoint(START, "Start", route.first())
        endpoint(FINISH, "Finish", route.last())
    }
    // Legacy files may list endpoint objects separately from their control-point records.
    if (bindings != null) return stops
    return stops.filter { it.type == START } + stops.filter { it.type != START && it.type != FINISH } +
        stops.filter { it.type == FINISH }
}

private fun storedLegs(info: ProtectedCourseInfo, stops: List<CoverageStop>, route: List<CourseGeoPoint>): List<CoverageLeg> {
    if (route.size < 2) {
        val bends = DesktopMandatoryCourseLegs.from(info)
        return stops.filter { it.type != WAYPOINT }.zipWithNext { a, b ->
            CoverageLeg(a, b, DesktopMandatoryCourseLegs.expand(listOf(a.point, b.point), bends))
        }
    }
    // Keep every stored bend between stops. An applied visit order may return to the same location later.
    var cursor = 0
    val indexed = stops.filter { it.type != WAYPOINT }.map { stop ->
        val index = when (stop.type) {
            START -> 0
            FINISH -> route.lastIndex
            else -> (cursor..route.lastIndex).minBy { route[it].distanceMetersTo(stop.point) }
        }
        if (info.appliedBindings != null) cursor = index
        index to stop
    }.sortedBy { it.first }
    return indexed.zipWithNext { (i, a), (j, b) -> CoverageLeg(a, b, route.subList(i, j + 1)) }
}

/** Enumerate distinct legal endpoint pairs without enumerating every fox order. */
private fun possibleLegs(race: RaceType, stops: List<CoverageStop>): List<Pair<CoverageStop, CoverageStop>> = buildList {
    if (race == RaceType.ORIENTEERING) return@buildList
    val start = stops.firstOrNull { it.type == START }
    val finish = stops.firstOrNull { it.type == FINISH }
    val beacon = stops.firstOrNull { it.type == BEACON }
    val spectator = stops.firstOrNull { it.type == SPECTATOR }
    val foxes = stops.filter { it.type == CONTROL }.distinct()
    fun connect(a: CoverageStop?, b: CoverageStop?) { if (a != null && b != null && a != b) add(a to b) }
    fun loop(from: CoverageStop?, via: List<CoverageStop>, to: CoverageStop?) {
        if (via.isEmpty()) connect(from, to)
        via.forEachIndexed { i, fox ->
            connect(from, fox)
            for (j in i + 1 until via.size) connect(fox, via[j])
            connect(fox, to)
        }
    }
    if (race == RaceType.SPRINT) {
        if (beacon == null) return@buildList
        val (fast, slow) = foxes.partition { it.control?.let(DesktopCourseAnalyzer::isSprintFastFox)
            ?: (DesktopCoursePointLabelClassifier.sprintFastFoxNumber(it.label) != null) }
        loop(start, slow, spectator ?: beacon)
        loop(spectator ?: beacon, fast, beacon)
    } else {
        loop(start, if (race == RaceType.FOXORING) foxes else foxes + listOfNotNull(spectator), beacon ?: finish)
    }
    connect(beacon, finish)
}
