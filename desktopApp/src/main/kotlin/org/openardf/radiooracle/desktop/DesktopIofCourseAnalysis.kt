package org.openardf.radiooracle.desktop

import kotlin.math.abs
import kotlin.math.roundToInt
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.*

/** All work stays in an immutable candidate until the import transaction is accepted. */
internal object DesktopIofCourseAnalysis {
    const val StraightLineToleranceMeters = 3.0
    data class Result(val project: EventProjectFile, val reports: List<DesktopCourseBriefReport>)

    suspend fun fetchCandidateElevations(project: EventProjectFile, categoryIds: Set<String>, password: String?,
        provider: suspend (List<CourseGeoPoint>) -> List<Double?> = DesktopVenueElevationCache::usgs3DepElevations,
        local: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters
    ): (CourseGeoPoint) -> Double? {
        val objects = (project.raceData.categories + project.raceData.courseMappings)
            .filter { it.category.id in categoryIds }
            .mapNotNull { it.category.storedCourseInfo(password) }.map { it.courseObjects }
        val points = objects.flatMap { course -> course.flatMapIndexed { index, from ->
            course.drop(index + 1).flatMap { to ->
                DesktopCourseRouteSampler.sampledStraightLegPoints(from.geo(), to.geo(), { null })
            }
        } }.distinctBy(::elevationKey)
            .filter { it.elevationMeters == null && local(it) == null }
        val values = if (points.isEmpty()) emptyList() else provider(points)
        require(values.size == points.size) { "Elevation provider returned an incomplete response." }
        val downloaded = points.zip(values).associate { (point, elevation) -> elevationKey(point) to elevation?.takeIf { it.isFinite() } }
        return { point -> local(point) ?: downloaded[elevationKey(point)] ?: point.elevationMeters }
    }

    private fun elevationKey(point: CourseGeoPoint) =
        kotlin.math.round(point.latitude * 1e7).toLong() to kotlin.math.round(point.longitude * 1e7).toLong()

    fun prepare(project: EventProjectFile, categoryIds: Set<String>, password: String?,
                elevationLookup: (CourseGeoPoint) -> Double? = DesktopVenueElevationCache::elevationMeters,
                checkCancelled: () -> Unit = {}): Result {
        val storagePassword = project.courseDataPassword(password)
        var candidate = project
        val categories = (project.raceData.categories + project.raceData.courseMappings).filter { it.category.id in categoryIds }
        // Geometry, assigned controls, event type and supplied distances all participate in uniqueness.
        val groups = categories.groupBy { data ->
            Triple(data.category.storedCourseInfo(storagePassword)?.let { info -> info.copy(
                sourceName = "", sourceSha256 = "", route = emptyList(), sampledPointCount = 0,
                lengthMeters = null, climbMeters = null, idealOrder = "",
                courseObjects = info.courseObjects.sortedBy { it.id }, controlPoints = info.controlPoints.sortedBy { it.controlId },
                suppliedLegLengths = info.suppliedLegLengths.sortedWith(compareBy({ it.fromId }, { it.toId }, { it.lengthMeters }))) },
                data.controlPoints.map { it.controlId }.toSet(), data.category.effectiveRaceType(project.raceData.race))
        }
        val reports = groups.values.map { group ->
            checkCancelled()
            val data = group.first()
            val original = data.category.storedCourseInfo(storagePassword)
            val name = group.joinToString(", ") { it.category.name }
            val objects = original?.courseObjects.orEmpty()
            val complete = objects.count { it.type == ProtectedCourseObjectType.START } == 1 &&
                objects.count { it.type == ProtectedCourseObjectType.FINISH } == 1 &&
                data.controlPoints.all { cp -> objects.any { it.id == cp.controlId } }
            if (original == null || !complete) {
                group.forEach { item ->
                    candidate = candidate.withStoredCourseInfo(item.category.id, original, storagePassword)
                        .withStoredIdealOrder(item.category.id, null, storagePassword)
                }
                return@map DesktopCourseBriefReport(data.category.id, name,
                    notice = "Ideal route, horizontal length and climb cannot be calculated: Start, Finish or assigned control coordinates are missing. Assignments and supplied XML leg lengths are retained.",
                    legWarnings = original?.let(::legWarnings).orEmpty())
            }
            val calculated = calculate(original, data.category.effectiveRaceType(project.raceData.race),
                project.raceData.controls, elevationLookup, checkCancelled)
            group.forEach { item ->
                candidate = candidate.withStoredCourseInfo(item.category.id, calculated.info, storagePassword)
                    .withStoredIdealOrder(item.category.id, calculated.info.idealOrder, storagePassword)
                candidate = EventProjectEditor.updateCategoryPhysicalStats(candidate, item.category.id,
                    calculated.info.lengthMeters.toString(), (calculated.info.climbMeters ?: 0).toString())
            }
            candidate = DesktopAuthoritativeCourseImport.prepare(candidate, group.map { it.category.id }.toSet(), storagePassword)
            val baseReport = DesktopCourseBriefReports.imported(candidate, setOf(data.category.id), storagePassword, checkCancelled).single()
            baseReport.copy(courseName = name, horizontalLengthMeters = calculated.info.lengthMeters,
                climbMeters = calculated.info.climbMeters,
                effectiveLengthMeters = calculated.info.effectiveLengthMeters(),
                idealOrder = (candidate.raceData.categories + candidate.raceData.courseMappings)
                    .single { it.category.id == data.category.id }.category.storedCourseInfo(storagePassword)!!.courseObjects.map { it.label },
                // The analyzer's timing uses drawn geometry; it cannot time an unknown detour shape.
                estimatedIdealSeconds = baseReport.estimatedIdealSeconds.takeIf { legWarnings(original).isEmpty() },
                notice = calculated.notice,
                routeMap = baseReport.routeMap?.copy(title = "Calculated ideal route"), legWarnings = legWarnings(original))
        }
        return Result(candidate, reports)
    }

    private data class Calculated(val info: ProtectedCourseInfo, val notice: String)
    private data class Leg(val points: List<CourseGeoPoint>, val horizontal: Double, val climb: Double?)

    private fun calculate(info: ProtectedCourseInfo, type: RaceType, controls: List<EventControl>,
                          elevationLookup: (CourseGeoPoint) -> Double?, checkCancelled: () -> Unit): Calculated {
        val objects = info.courseObjects
        val start = objects.single { it.type == ProtectedCourseObjectType.START }
        val finish = objects.single { it.type == ProtectedCourseObjectType.FINISH }
        val sampling = DesktopCourseAnalysisSampling(info.route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) }, elevationLookup)
        val legs = mutableMapOf<Pair<String, String>, Leg>()
        objects.forEach { from -> objects.filter { it.id != from.id }.forEach { to ->
            checkCancelled()
            val points = DesktopCourseRouteSampler.sampledStraightLegPoints(from.geo(), to.geo(), sampling::elevation)
            val declared = info.suppliedLegLengths.firstOrNull { it.fromId == from.id && it.toId == to.id }
                ?: info.suppliedLegLengths.firstOrNull { it.fromId == to.id && it.toId == from.id }
            legs[from.id to to.id] = Leg(points, declared?.lengthMeters ?: from.geo().distanceMetersTo(to.geo()),
                DesktopCourseRouteMetricsCalculator.climbMetersOrNull(points))
        } }
        val useElevation = legs.values.all { it.climb != null }
        fun leg(from: ProtectedCourseObjectPoint, to: ProtectedCourseObjectPoint) =
            if (from.id == to.id) Leg(listOf(from.geo()), 0.0, 0.0) else legs.getValue(from.id to to.id)
        fun cost(route: List<ProtectedCourseObjectPoint>) = route.zipWithNext().sumOf { (from, to) ->
            val value = leg(from, to)
            value.horizontal + if (useElevation) 10 * requireNotNull(value.climb) else 0.0
        }
        fun select(from: ProtectedCourseObjectPoint, to: ProtectedCourseObjectPoint, middle: List<ProtectedCourseObjectPoint>) =
            DesktopCourseAnalyzer.minimumCostCourseOrder(middle, { cost(listOf(from) + it + to) }, checkCancelled)
        val beacon = objects.singleOrNull { it.type == ProtectedCourseObjectType.BEACON }
        val spectator = objects.singleOrNull { it.type == ProtectedCourseObjectType.SPECTATOR }
        val foxes = objects.filter { it.type == ProtectedCourseObjectType.CONTROL }
        val terminal = beacon ?: finish
        val transition = spectator ?: beacon
        val middle = if (type == RaceType.SPRINT && transition != null) {
            val byId = controls.associateBy { it.id }
            val (fast, slow) = foxes.partition { point ->
                byId[point.id]?.let(DesktopCourseAnalyzer::isSprintFastFox) == true
            }
            select(start, transition, slow) + transition + select(transition, terminal, fast)
        } else select(start, terminal, foxes + listOfNotNull(spectator))
        val ordered = listOf(start) + middle + listOfNotNull(beacon) + finish
        val chosen = ordered.zipWithNext().map { (from, to) -> leg(from, to) }
        val points = chosen.flatMapIndexed { index, value -> if (index == 0) value.points else value.points.drop(1) }
        val horizontal = chosen.sumOf { it.horizontal }.roundToInt()
        val climb = if (chosen.all { it.climb != null }) chosen.sumOf { requireNotNull(it.climb) }.roundToInt() else null
        val orderedIds = middle.map { it.id } + listOfNotNull(beacon?.id)
        val order = ProtectedIdealOrderRules.formatControlIds(orderedIds, controls)
        val notice = buildList {
            add(if (useElevation) "Route selected by effective length (horizontal length + 10 × climb)." else "Route selected by horizontal length because elevation coverage is incomplete. Climb is unknown where heights are missing.")
            if (foxes.size > 8) add("Large course: a heuristic search was used; a global optimum is not guaranteed.")
            if (legWarnings(info).isNotEmpty()) add("XML distances are retained and used for matching legs in either direction. Other legs use straight-line distance. Drawn lines and elevation profiles do not describe unknown detours; climb is an estimate and the route needs a terrain check.")
        }.joinToString(" ")
        return Calculated(info.copy(idealOrder = order, lengthMeters = horizontal, climbMeters = climb,
            courseObjects = ordered.map { it.copy(elevationMeters = sampling.elevation(it.geo()) ?: it.elevationMeters) },
            controlPoints = info.controlPoints.map { it.copy(elevationMeters = sampling.elevation(CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters)) ?: it.elevationMeters) },
            route = points.map { ProtectedCourseRoutePoint(it.latitude, it.longitude, it.elevationMeters) },
            sampledPointCount = points.size, appliedBindings = null), notice)
    }

    internal fun legWarnings(info: ProtectedCourseInfo): List<String> {
        val byId = info.courseObjects.associateBy { it.id }
        return info.suppliedLegLengths.mapNotNull { leg ->
            val from = byId[leg.fromId]
            val to = byId[leg.toId]
            val label = "${from?.label ?: leg.fromId} → ${to?.label ?: leg.toId}"
            if (from == null || to == null) return@mapNotNull "$label: XML ${leg.lengthMeters} m retained; straight-line comparison unavailable."
            val direct = from.geo().distanceMetersTo(to.geo())
            if (abs(leg.lengthMeters - direct) <= StraightLineToleranceMeters) null
            else "$label: XML ${leg.lengthMeters} m; straight line ${direct.roundToInt()} m; difference ${if (leg.lengthMeters > direct) "+" else ""}${(leg.lengthMeters - direct).roundToInt()} m. " +
                if (leg.lengthMeters > direct) "May avoid uncrossable terrain or a keep-out region." else "XML distance is shorter; check the source data."
        }
    }

    private fun ProtectedCourseObjectPoint.geo() = CourseGeoPoint(latitude, longitude, elevationMeters)
}
