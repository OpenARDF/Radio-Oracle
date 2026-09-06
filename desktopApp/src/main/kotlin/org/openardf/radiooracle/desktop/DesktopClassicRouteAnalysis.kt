package org.openardf.radiooracle.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.domain.SIRecordType
import org.openardf.radiooracle.shared.event.*
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

internal class DesktopFrozenElevationSurface(
    val sources: List<RouteElevationSource>,
    val elevation: (CourseGeoPoint) -> Double?
)

/** All calculation inputs are detached. Callers must revalidate before merging returned metadata. */
internal object DesktopClassicRouteAnalysis {
    // Identity resolution is part of calculation provenance. Old numbers must not survive this change.
    const val METHOD = "classic-straight-25m-median50-prominence2-rounded-components-applied-bindings-v3"
    const val PUNCH_POLICY = "ignore-unresolved-v1"
    private val json = Json { encodeDefaults = true }

    fun needsMethodRefresh(project: EventProjectFile): Boolean = project.raceData.race.raceType == RaceType.CLASSIC &&
        project.desktopRouteAnalysis?.let { saved -> saved.results.values.any {
            saved.contexts[it.contextId]?.method != METHOD
        } } == true

    fun terrainBounds(infos: Collection<ProtectedCourseInfo>): DesktopVenueElevationBoundingBox? {
        val points = infos.flatMap { info -> info.courseObjects.map { it.latitude to it.longitude } +
            info.controlPoints.map { it.latitude to it.longitude } }
            .filter { (lat, lon) -> lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0 }
        if (points.isEmpty()) return null
        return DesktopVenueElevationBoundingBox(points.minOf { it.first }, points.maxOf { it.first },
            points.minOf { it.second }, points.maxOf { it.second })
    }

    internal class CalculationCache {
        internal val prepared = mutableMapOf<String, Result<PreparedCourse>>()
        internal val sharedCourses = mutableMapOf<String, PreparedCourse>()
        internal val idealMetrics = mutableMapOf<String, Pair<Int, Int>>()
        internal val routeMetrics = object : LinkedHashMap<List<CourseGeoPoint>, Pair<Int, Int>>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<CourseGeoPoint>, Pair<Int, Int>>?) = size > 512
        }
        internal val legs = object : LinkedHashMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>?) = size > 512
        }
        internal var surfaceKey: String? = null
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun courseFingerprint(project: EventProjectFile): String = sha256(buildString {
        append(project.raceData.race.id).append('|').append(project.raceData.race.raceType)
        project.raceData.controls.sortedBy { it.id }.forEach {
            append(json.encodeToString(listOf(it.id, it.siCode.toString(), it.type.name, it.label, it.publicLabel.orEmpty())))
        }
        (project.raceData.categories + project.raceData.courseMappings).sortedBy { it.category.id }.forEach {
            val c = it.category
            append(json.encodeToString(listOf(c.id, c.encryptedCourseInfo.orEmpty(), c.encryptedIdealOrder.orEmpty(), c.idealOrder.orEmpty())))
            append(c.courseInfo?.let { info -> json.encodeToString(info) }.orEmpty())
            append(json.encodeToString(it.controlPoints)).append(json.encodeToString(it.publicControlIds))
        }
    })

    fun categoryId(data: EventCompetitorData): String? = data.readoutData?.result?.categoryId
        ?: data.competitorCategory.category?.id ?: data.competitorCategory.competitor.categoryId

    fun inputFingerprint(data: EventCompetitorData): String = sha256(buildString {
        append(categoryId(data)).append('|')
        val readout = data.readoutData ?: return@buildString
        append(readout.result.startTimeSeconds).append('|').append(readout.result.finishTimeSeconds)
        readout.punches.map { it.punch }.filter { it.punchType == SIRecordType.CONTROL }
            .sortedBy { it.order }.forEach { append("|${it.order}:${it.siCode}:${it.siTimeSeconds}") }
    })

    private fun controlPunches(data: EventCompetitorData): List<EventPunch> = data.readoutData?.punches.orEmpty()
        .map { it.punch }.filter { it.punchType == SIRecordType.CONTROL }.sortedBy { it.order }

    private fun validSavedPunches(data: EventCompetitorData, snapshot: ClassicRouteSnapshot): Boolean {
        if (snapshot.punchPolicy != "strict-v1" && snapshot.punchPolicy != PUNCH_POLICY) return false
        val ignored = snapshot.ignoredControlPunchIndexes
        if (snapshot.punchPolicy == "strict-v1" && ignored.isNotEmpty()) return false
        val punches = controlPunches(data)
        if (ignored != ignored.distinct().sorted() || ignored.any { it !in punches.indices }) return false
        val kept = punches.filterIndexed { index, _ -> index !in ignored }
        val start = data.readoutData?.result?.startTimeSeconds ?: return false
        val finish = data.readoutData?.result?.finishTimeSeconds ?: return false
        return finish >= start && kept.all { it.siTimeSeconds in start..finish } &&
            kept.zipWithNext().all { (a, b) -> a.siTimeSeconds <= b.siTimeSeconds }
    }

    fun projection(project: EventProjectFile): Map<String, ResultRouteLength> {
        if (project.raceData.race.raceType != RaceType.CLASSIC) return emptyMap()
        val saved = project.desktopRouteAnalysis?.takeIf { it.version == 1 } ?: return emptyMap()
        val course = courseFingerprint(project)
        return project.raceData.competitorData.mapNotNull { data ->
            val id = data.readoutData?.result?.id ?: return@mapNotNull null
            val snapshot = saved.results[id] ?: return@mapNotNull null
            val context = saved.contexts[snapshot.contextId] ?: return@mapNotNull null
            snapshot.length?.takeIf {
                context.method == METHOD && context.courseFingerprint == course &&
                    snapshot.inputFingerprint == inputFingerprint(data) && validSavedPunches(data, snapshot) && snapshot.categoryId == categoryId(data) && context.categoryId == snapshot.categoryId &&
                    context.horizontalMeters >= 0 && context.climbMeters >= 0 && context.permutations > 0 &&
                    context.effectiveMeters.toLong() == context.horizontalMeters.toLong() + 10L * context.climbMeters &&
                    it.horizontalMeters >= 0 && it.climbMeters >= 0 &&
                    it.effectiveMeters.toLong() == it.horizontalMeters.toLong() + 10L * it.climbMeters &&
                    it.idealEffectiveMeters == context.effectiveMeters
            }?.let { id to it.copy(idealRoute = DesktopClassicRouteHeading.order(project, context),
                missingAssignedPunches = missingAssignedPunches(project, data, snapshot)) }
        }.toMap()
    }

    /** Presentation only: use the same assigned identities and ignored punches as the saved calculation. */
    private fun missingAssignedPunches(project: EventProjectFile, data: EventCompetitorData, snapshot: ClassicRouteSnapshot): Boolean {
        val category = project.raceData.categories.firstOrNull { it.category.id == snapshot.categoryId } ?: return false
        val assignedIds = category.controlPoints.mapNotNull { point ->
            (project.raceData.controls.firstOrNull { it.id == point.controlId }
                ?: project.raceData.controls.filter { it.siCode == point.siCode && it.type == point.type }.singleOrNull())?.id
        }
        val visitedIds = controlPunches(data).filterIndexed { index, _ -> index !in snapshot.ignoredControlPunchIndexes }
            .mapNotNull { punch -> project.raceData.controls.filter { it.siCode == punch.siCode }.singleOrNull()?.id }.toSet()
        return assignedIds.any { it !in visitedIds }
    }

    fun status(project: EventProjectFile, resultId: String): String {
        return statuses(project)[resultId] ?: "Not calculated"
    }

    fun statuses(project: EventProjectFile): Map<String, String> {
        val ready = projection(project)
        val course = courseFingerprint(project)
        val metadata = project.desktopRouteAnalysis
        return project.raceData.competitorData.mapNotNull { data ->
            val id = data.readoutData?.result?.id ?: return@mapNotNull null
            val saved = metadata?.results?.get(id)
            val context = metadata?.contexts?.get(saved?.contextId)
            id to (ready[id]?.text ?: when {
                saved == null -> "Not calculated"
                metadata?.version == 1 && context?.method == METHOD && context.courseFingerprint == course &&
                    saved.inputFingerprint == inputFingerprint(data) -> saved.unavailableReason ?: "Unavailable"
                else -> "Stale"
            })
        }.toMap()
    }

    suspend fun calculate(
        project: EventProjectFile,
        courseInfo: Map<String, ProtectedCourseInfo>,
        surface: DesktopFrozenElevationSurface,
        checkCancelled: () -> Unit = {},
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onPartial: suspend (StoredClassicRouteAnalysis) -> Unit = {},
        cacheState: CalculationCache = CalculationCache()
    ): StoredClassicRouteAnalysis {
        require(project.raceData.race.raceType == RaceType.CLASSIC) { "Only Classic races support route analysis." }
        val fingerprint = courseFingerprint(project)
        val contexts = project.desktopRouteAnalysis?.takeIf { it.version == 1 }?.contexts.orEmpty().toMutableMap()
        val snapshots = project.desktopRouteAnalysis?.takeIf { it.version == 1 }?.results.orEmpty().toMutableMap()
        val candidates = project.raceData.competitorData.filter { it.readoutData != null }
        val validSavedIds = projection(project).keys
        val sourceKey = json.encodeToString(surface.sources)
        if (cacheState.surfaceKey != sourceKey || cacheState.prepared.size > 128) {
            cacheState.legs.clear()
            cacheState.prepared.clear()
            cacheState.sharedCourses.clear()
            cacheState.idealMetrics.clear()
            cacheState.routeMetrics.clear()
            cacheState.surfaceKey = sourceKey
        }
        val cache = cacheState.legs
        val strictElevation: (CourseGeoPoint) -> Double? = { point ->
            checkCancelled()
            surface.elevation(point)?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("Elevation cache does not cover all sampled Classic legs.")
        }
        val prepared = cacheState.prepared
        candidates.forEachIndexed { index, data ->
            checkCancelled()
            val category = categoryId(data).orEmpty()
            val resultId = requireNotNull(data.readoutData).result.id
            val input = inputFingerprint(data)
            val contextId = sha256(fingerprint + category + METHOD + sourceKey)
            if (resultId !in validSavedIds || snapshots[resultId]?.let { it.inputFingerprint == input && it.contextId == contextId && it.length != null } != true) {
                val at = Instant.now().toString()
                val attempt = try {
                    val course = prepared.getOrPut(contextId) {
                        try { Result.success(prepare(project, category, courseInfo, strictElevation, checkCancelled, cacheState.sharedCourses)) }
                        catch (e: CancellationException) { throw e }
                        catch (e: IllegalArgumentException) { Result.failure(e) }
                    }.getOrThrow()
                    val ideal = cacheState.idealMetrics.getOrPut(contextId) { metrics(course.idealStops, strictElevation, cache) }
                    contexts[contextId] = ClassicRouteReference(
                        category, fingerprint, METHOD, surface.sources, sha256(METHOD + course.idealStops.toString()),
                        ideal.first, ideal.second, effective(ideal), at, factorial(course.permutedCount),
                        courseInfo[category]?.effectiveLengthMeters()
                    )
                    val readout = requireNotNull(data.readoutData)
                    val start = requireNotNull(readout.result.startTimeSeconds) { "No Start time." }
                    val finish = requireNotNull(readout.result.finishTimeSeconds) { "No Finish time." }
                    require(finish >= start) { "Finish precedes Start." }
                    val ignored = mutableListOf<Int>()
                    val kept = controlPunches(data).mapIndexedNotNull { index, punch ->
                        val control = project.raceData.controls.filter { it.siCode == punch.siCode }.singleOrNull()
                        val point = control?.let {
                            try { resolve(it, courseInfo[category], courseInfo.values.toList()) }
                            catch (_: UnresolvedControlLocation) { null }
                        }
                        if (control == null || point == null) {
                            ignored += index
                            null
                        } else Triple(punch, control, point)
                    }
                    require(kept.all { it.first.siTimeSeconds in start..finish }) { "Control punches outside Start/Finish need review." }
                    require(kept.zipWithNext().all { (a, b) -> a.first.siTimeSeconds <= b.first.siTimeSeconds }) { "Control punch times run backwards; review readout order." }
                    val stops = listOf(course.start) + kept.map { it.third } + course.finish
                    val route = cacheState.routeMetrics.getOrPut(stops) { metrics(stops, strictElevation, cache) }
                    val ids = kept.map { it.second.id }
                    val comparison = when {
                        ids == course.idealIds -> "Ideal order"
                        ids.sorted() != course.idealIds.sorted() -> "Different control set"
                        ids.lastOrNull() != course.idealIds.lastOrNull() -> "Beacon not terminal"
                        effective(route) < effective(ideal) -> "Shorter than reference; review route"
                        else -> "Alternative order"
                    }
                    ClassicRouteSnapshot(category, input, contextId, at,
                        ResultRouteLength(route.first, route.second, effective(route), effective(ideal), comparison),
                        punchPolicy = PUNCH_POLICY, ignoredControlPunchIndexes = ignored)
                } catch (e: CancellationException) { throw e }
                catch (e: IllegalArgumentException) {
                    contexts.putIfAbsent(contextId, ClassicRouteReference(category, fingerprint, METHOD, surface.sources, "", 0, 0, 0, at, 0))
                    ClassicRouteSnapshot(category, input, contextId, at, unavailableReason = e.message ?: "Route unavailable", punchPolicy = PUNCH_POLICY)
                }
                snapshots[resultId] = attempt
                onPartial(StoredClassicRouteAnalysis(contexts = contexts.toMap(), results = snapshots.toMap()))
            }
            onProgress(index + 1, candidates.size)
        }
        val ids = candidates.map { requireNotNull(it.readoutData).result.id }.toSet()
        val retained = snapshots.filterKeys { it in ids }
        return StoredClassicRouteAnalysis(contexts = contexts.filterKeys { key -> retained.values.any { it.contextId == key } }, results = retained)
    }

    internal data class PreparedCourse(val start: CourseGeoPoint, val finish: CourseGeoPoint, val idealIds: List<String>, val idealStops: List<CourseGeoPoint>, val permutedCount: Int)

    private fun prepare(project: EventProjectFile, categoryId: String, infos: Map<String, ProtectedCourseInfo>, lookup: (CourseGeoPoint) -> Double?, checkCancelled: () -> Unit, sharedCourses: MutableMap<String, PreparedCourse>): PreparedCourse {
        val category = requireNotNull(project.raceData.categories.firstOrNull { it.category.id == categoryId }) { "No result category." }
        val info = requireNotNull(infos[categoryId]) { "Course geometry is unavailable; unlock or import course data." }
        require(info.courseObjects.none { it.type == ProtectedCourseObjectType.WAYPOINT }) { "Courses with mandatory waypoints require route review." }
        fun endpoint(type: ProtectedCourseObjectType): CourseGeoPoint {
            val point = info.courseObjects.filter { it.type == type }.distinctBy { it.latitude to it.longitude }.singleOrNull()
            return point?.let { geo(it.latitude, it.longitude) }
                ?: throw IllegalArgumentException("A unique ${type.name.lowercase()} location is required.")
        }
        val start = endpoint(ProtectedCourseObjectType.START)
        val finish = endpoint(ProtectedCourseObjectType.FINISH)
        val assigned = category.controlPoints.map { point ->
            project.raceData.controls.firstOrNull { it.id == point.controlId }
                ?: project.raceData.controls.filter { it.siCode == point.siCode && it.type == point.type }.singleOrNull()
                ?: throw IllegalArgumentException("Assigned control is unresolved.")
        }.distinctBy { it.id }
        val beacons = assigned.filter { it.type == ControlPointType.BEACON }
        require(beacons.size == 1) { "A unique assigned terminal Beacon is required." }
        val permuted = assigned.filter { it.type != ControlPointType.BEACON }
        require(permuted.isNotEmpty() && permuted.all { it.type == ControlPointType.CONTROL || it.type == ControlPointType.SEPARATOR }) { "Unsupported Classic control roles." }
        val points = assigned.associate { it.id to resolve(it, info, infos.values.toList()) }
        require(points.values.distinct().size == assigned.size) { "Assigned controls do not have unique field locations." }
        val geometryKey = "$start|$finish|" + assigned.joinToString("|") { "${it.id}:${it.type}:${points.getValue(it.id)}" }
        sharedCourses[geometryKey]?.let { return it }
        val idealIds = DesktopCourseAnalyzer.classicIdealOrder(start, finish, permuted.map { it to points.getValue(it.id) },
            beacons.single().let { it to points.getValue(it.id) }, lookup, checkCancelled)
        return PreparedCourse(start, finish, idealIds, listOf(start) + idealIds.map(points::getValue) + finish, permuted.size)
            .also { sharedCourses[geometryKey] = it }
    }

    private fun geo(latitude: Double, longitude: Double): CourseGeoPoint {
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "Invalid course coordinates." }
        return CourseGeoPoint(latitude, longitude)
    }

    private class UnresolvedControlLocation(message: String) : IllegalArgumentException(message)

    internal fun resolve(control: EventControl, categoryInfo: ProtectedCourseInfo?, all: List<ProtectedCourseInfo>): CourseGeoPoint {
        val resolution = CourseControlResolver.resolve(control, listOfNotNull(categoryInfo) + all.filter { it !== categoryInfo })
        val point = resolution.location ?: throw UnresolvedControlLocation(requireNotNull(resolution.explanation))
        // This calculation's frozen terrain surface supplies all elevations consistently.
        return CourseGeoPoint(point.latitude, point.longitude)
    }

    private fun metrics(points: List<CourseGeoPoint>, lookup: (CourseGeoPoint) -> Double?, cache: MutableMap<Pair<CourseGeoPoint, CourseGeoPoint>, List<CourseGeoPoint>>): Pair<Int, Int> {
        val unique = points.fold(mutableListOf<CourseGeoPoint>()) { list, p -> list.apply { if (lastOrNull() != p) add(p) } }
        val metrics = DesktopCourseRouteMetricsCalculator.metrics(DesktopCourseRouteSampler.sampledStraightRoutePoints(unique, lookup, legSampleCache = cache))
        return metrics.horizontalLengthMeters.roundToInt() to requireNotNull(metrics.climbMeters) { "Incomplete elevations." }.roundToInt()
    }
    private fun effective(metric: Pair<Int, Int>): Int = Math.toIntExact(metric.first.toLong() + 10L * metric.second)
    private fun factorial(n: Int): Int = (1..n).fold(1) { a, b -> a * b }
}
