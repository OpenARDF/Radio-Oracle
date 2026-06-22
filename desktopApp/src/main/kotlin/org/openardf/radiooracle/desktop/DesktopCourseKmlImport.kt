package org.openardf.radiooracle.desktop

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.ControlRoleLabelRules
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventCategorySort
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlCatalog
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Operator-facing summary of a controls/route map import.
 *
 * Route geometry and ideal-order facts are written immediately into the encrypted category payload,
 * while category assigned controls are reported as a separate pending update. The UI applies those
 * assignments only after explicit confirmation because they replace the public category assignment
 * list.
 */
data class DesktopCourseKmlImportSummary(
    val matchedCategoryCount: Int,
    val routeCount: Int,
    val controlPointCount: Int,
    val matchedControlPointCount: Int,
    val matchedFoxCount: Int,
    val matchedBeaconCount: Int,
    val matchedSpectatorCount: Int,
    val labelConversions: List<DesktopCourseKmlLabelConversion>,
    val matchedCategoryIds: List<String>,
    val matchedCategoryNames: List<String>,
    val routeElevationPointCount: Int,
    val missingElevationPointCount: Int,
    val importedCategoryCount: Int,
    val categoryAssignmentUpdates: List<DesktopCourseKmlCategoryAssignmentUpdate>,
    val changedControlLocationCount: Int,
    val controlLocationAffectedCategoryCount: Int,
    val duplicateCategoryCount: Int,
    val duplicateMissingElevationPointCount: Int,
    val missingCategoryNames: List<String>,
    val createdCategoryNames: List<String>,
    val missingControlNames: List<String> = emptyList(),
    val createdControlNames: List<String> = emptyList(),
    val categoryAssumptions: List<DesktopCourseKmlCategoryAssumption>,
    val rejectedRoutes: List<DesktopCourseKmlRejectedRoute> = emptyList(),
    val eventTypeWarnings: List<String>,
    val sourceSha256: String,
    val controlIdentityUpdateCount: Int = 0
) {
    val assignedCategoryControlCount: Int
        get() = categoryAssignmentUpdates.sumOf { it.controls.size }

    val isDuplicateOnly: Boolean
        get() = matchedCategoryCount > 0 &&
            importedCategoryCount == 0 &&
            assignedCategoryControlCount == 0 &&
            controlIdentityUpdateCount == 0 &&
            changedControlLocationCount == 0 &&
            duplicateCategoryCount == matchedCategoryCount

    val hasDuplicateMissingElevations: Boolean
        get() = duplicateMissingElevationPointCount > 0

    val hasMissingStoredElevations: Boolean
        get() = missingElevationPointCount > 0 || duplicateMissingElevationPointCount > 0

    val isControlLocationNoOp: Boolean
        get() = matchedCategoryCount == 0 &&
            importedCategoryCount == 0 &&
            assignedCategoryControlCount == 0 &&
            duplicateCategoryCount == 0 &&
            controlIdentityUpdateCount == 0 &&
            changedControlLocationCount == 0 &&
            matchedControlPointCount > 0

    val hasLabelConversions: Boolean
        get() = labelConversions.isNotEmpty()
}

data class DesktopCourseKmlLabelConversion(
    val importedName: String,
    val eventControlLabel: String
)

data class DesktopCourseKmlCategoryAssumption(
    val routeName: String,
    val categoryName: String
)

data class DesktopCourseKmlRejectedRoute(
    val routeName: String,
    val categoryName: String,
    val reason: String
)

data class DesktopCourseKmlAssignedControl(
    val controlId: String,
    val siCode: Int,
    val type: ControlPointType
)

data class DesktopCourseKmlCategoryAssignmentUpdate(
    val categoryId: String,
    val categoryName: String,
    val controlPointsText: String,
    val controls: List<DesktopCourseKmlAssignedControl>
)

class DesktopCourseKmlMissingRouteException(message: String) : IllegalArgumentException(message)

data class DesktopRouteElevationProgress(
    val completedPointCount: Int,
    val totalPointCount: Int,
    val categoryName: String,
    val downloadedPointCount: Int = 0,
    val cachedPointCount: Int = 0
)

data class DesktopRouteElevationResult(
    val categoryCount: Int,
    val sampledPointCount: Int,
    val elevatedPointCount: Int,
    val resolvedPointCount: Int = elevatedPointCount,
    val cachedPointCount: Int = 0
)

object DesktopCourseKmlImporter {
    // Imported LineStrings are used both for protected route facts and for finding nearby control
    // points. These constants keep route sampling dense enough for elevation graphs without
    // treating every small coordinate wobble as a different course.
    private const val ROUTE_SAMPLE_METERS = 25.0
    private const val USGS_3DEP_SAMPLE_METERS = 10.0
    private const val CONTROL_ROUTE_TOLERANCE_METERS = 50.0
    private const val ROUTE_ORIENTATION_TOLERANCE_METERS = 5.0
    private const val CLIMB_NOISE_THRESHOLD_METERS = 1.0
    private val CATEGORY_ASSUMPTION_SEQUENCE = listOf(
        "M21", "M35", "M40", "M45", "M50", "M55", "M60", "M65", "M70", "M75", "M80", "M85", "M90",
        "W21", "W35", "W40", "W45", "W50", "W55", "W60", "W65", "W70", "W75", "W80", "W85", "W90"
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun importProtectedCourseInfo(
        path: Path,
        projectFile: EventProjectFile,
        password: String,
        categoryOverrideId: String? = null,
        elevationProvider: (CourseGeoPoint) -> Double? = { point -> DesktopVenueElevationCache.elevationMeters(point) },
        createMissingCategories: Boolean = false,
        createMissingControls: Boolean = false,
        missingCategoryIdFactory: (String) -> String = { UUID.randomUUID().toString() },
        requireRoutes: Boolean = true
    ): Pair<EventProjectFile, DesktopCourseKmlImportSummary> {
        val sourceSha256 = fileSha256(path)
        val courseData = parse(path)
        DesktopDebugLog.info(
            "CourseKml",
            "Import parsed ${path.fileName}: hash=${sourceSha256.shortHash()} pointControls=${courseData.controls.size} routes=${courseData.routes.size}"
        )
        if (requireRoutes && courseData.routes.isEmpty()) {
            throw DesktopCourseKmlMissingRouteException(
                "Course Analyzer imports require at least one route LineString or GPX route/track. " +
                    "Import control-only files from Setup > Controls > Import/Export."
            )
        }
        val missingCategoryNames = missingRouteCategoryNames(
            routes = courseData.routes,
            categories = projectFile.raceData.categories,
            sourceName = path.fileName.toString(),
            categoryOverrideId = categoryOverrideId
        )
        val createdCategoryNames = if (createMissingCategories) missingCategoryNames else emptyList()
        val projectWithMissingCategories = if (createdCategoryNames.isEmpty()) {
            projectFile
        } else {
            projectFile.copy(
                raceData = projectFile.raceData.copy(
                    categories = projectFile.raceData.categories + createdCategoryNames.mapIndexed { index, name ->
                        EventCategoryData(
                            category = EventCategory(
                                id = missingCategoryIdFactory(name),
                                raceId = projectFile.raceData.race.id,
                                name = name,
                                isMan = StandardCategoryRules.inferIsManFromName(name)
                                    ?: name.trim().uppercase().startsWith("M"),
                                maxAge = name.filter(Char::isDigit).takeIf { it.isNotBlank() }?.toIntOrNull(),
                                lengthMeters = 0,
                                climbMeters = 0,
                                order = (projectFile.raceData.categories.maxOfOrNull { it.category.order } ?: -1) + index + 1,
                                differentProperties = false,
                                raceType = null,
                                raceBand = null,
                                timeLimitSeconds = null,
                                controlPointsString = ""
                            ),
                            controlPoints = emptyList(),
                            competitors = emptyList()
                        )
                    }
                )
            )
        }
        val importedControlsForControlMatching = controlMatchingCourseControls(
            importedControls = courseData.controls,
            routes = courseData.routes,
            sprintContext = isSprintCourseImport(projectWithMissingCategories, courseData)
        )
        val missingControls = missingCourseControls(
            importedControls = importedControlsForControlMatching,
            eventControls = projectWithMissingCategories.raceData.controls,
            raceId = projectWithMissingCategories.raceData.race.id
        )
        val createdControlNames = if (createMissingControls) {
            missingControls.map { it.displayCourseLabel() }
        } else {
            emptyList()
        }
        val projectWithMissingControls = if (createdControlNames.isEmpty()) {
            projectWithMissingCategories
        } else {
            projectWithMissingCategories.copy(
                raceData = projectWithMissingCategories.raceData.copy(
                    controls = EventControlCatalog.mergeControls(
                        projectWithMissingCategories.raceData.controls,
                        missingControls
                    )
                )
            )
        }
        val matchedControlResult = matchedControls(importedControlsForControlMatching, projectWithMissingControls.raceData.controls)
        val hintedProject = applyControlSiHints(projectWithMissingControls, matchedControlResult.controls)
        val matchedControlsForHintedProject = matchedControls(importedControlsForControlMatching, hintedProject.raceData.controls)
        val controls = matchedControlsForHintedProject.controls
        val categories = hintedProject.raceData.categories.sortedWith(EventCategorySort.byDisplayName)
        val courseInfoByCategoryId = hintedProject.raceData.categories.mapNotNull { categoryData ->
            categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }?.let { encryptedValue ->
                categoryData.category.id to DesktopProtectedCourseOrder.decryptCourseInfo(encryptedValue, password)
            }
        }.toMap()
        val routeCategoryTargets = routeCategoryTargets(
            routes = courseData.routes,
            categories = categories,
            sourceName = path.fileName.toString(),
            categoryOverrideId = categoryOverrideId
        )
        val hasSprintRouteTargets = routeCategoryTargets.targets.values.flatten().any { categoryData ->
            categoryData.category.effectiveRaceType(hintedProject.raceData.race) == RaceType.SPRINT
        } || isSprintCourseImport(hintedProject, courseData)
        val controlsForLocationUpdates = if (hasSprintRouteTargets) {
            controls.filterNot { it.type == ControlPointType.SEPARATOR && it.point.isNearAnyRouteEndpoint(courseData.routes) }
        } else {
            controls
        }
        val sameSourceDuplicateCategoryIds = sameSourceDuplicateCategoryIds(
            routes = courseData.routes,
            routeCategoryTargets = routeCategoryTargets,
            eventRace = hintedProject.raceData.race,
            courseInfoByCategoryId = courseInfoByCategoryId,
            sourceSha256 = sourceSha256,
            importedControls = courseData.controls,
            matchedControls = controls
        )
        val controlLocationUpdates = controlLocationUpdates(
            matchedControls = controlsForLocationUpdates,
            courseInfoByCategoryId = courseInfoByCategoryId,
            ignoredCategoryIds = sameSourceDuplicateCategoryIds
        )
        val locationUpdateResult = controlLocationUpdates.takeIf { it.isNotEmpty() }?.let { updates ->
            DesktopProtectedControlLocationUpdater.applyControlLocations(
                projectFile = projectWithMissingControls,
                courseInfoByCategoryId = courseInfoByCategoryId,
                updates = updates,
                password = password,
                elevationLookup = elevationProvider,
                invalidateAllReferencedProtectedCourses = false
            )
        }
        var updatedProject = hintedProject
        locationUpdateResult?.let { result ->
            updatedProject = result.projectFile
        }
        var matchedCategoryCount = 0
        var importedCategoryCount = 0
        var duplicateCategoryCount = 0
        var duplicateMissingElevationPointCount = 0
        var routeElevationPointCount = 0
        var missingElevationPointCount = 0
        val matchedCategoryIds = mutableListOf<String>()
        val matchedCategoryNames = mutableListOf<String>()
        val categoryAssignmentUpdates = mutableListOf<DesktopCourseKmlCategoryAssignmentUpdate>()
        val rejectedRoutes = mutableListOf<DesktopCourseKmlRejectedRoute>()

        courseData.routes.forEach { route ->
            routeCategoryTargets.targets[route].orEmpty().forEach { categoryData ->
                matchedCategoryCount++

                val existingCourseInfo = categoryData.category.encryptedCourseInfo
                    ?.takeIf { it.isNotBlank() }
                    ?.let { DesktopProtectedCourseOrder.decryptCourseInfo(it, password) }
                val sameSourceCourseInfo = existingCourseInfo?.takeIf { it.sourceSha256 == sourceSha256 }
                // Build the optional public assignment update from the unsampled LineString. Sampling
                // is only for route/elevation facts; assignment matching should reflect the controls
                // intentionally placed near the imported category route.
                val raceType = categoryData.category.effectiveRaceType(updatedProject.raceData.race)
                val routeImportedControls = endpointAwareImportedControls(route.points, courseData.controls, raceType)
                val routeMatchedControls = matchedControlsForRoute(raceType, route.points, controls)
                val orientedRouteGeometry = orientedRoutePoints(route.points, routeImportedControls)
                val routeGeometry = normalizedRouteGeometry(route.points, routeImportedControls, routeMatchedControls)
                val routeLineControls = controlsOnRoute(routeGeometry, routeMatchedControls)
                val explicitAssignmentControls = if (raceType == RaceType.CLASSIC || raceType == RaceType.SHORT) {
                    controlsFromExplicitClassicRouteOrder(route.name, controls)
                } else {
                    null
                }
                routeGeometryRejectionReason(
                    route = route,
                    categoryName = categoryData.category.name,
                    orientedRoute = orientedRouteGeometry,
                    routeLineControls = routeLineControls,
                    matchedControls = routeMatchedControls,
                    importedControls = routeImportedControls
                )?.let { reason ->
                    rejectedRoutes += DesktopCourseKmlRejectedRoute(
                        routeName = route.name,
                        categoryName = categoryData.category.name,
                        reason = reason
                    )
                    DesktopDebugLog.warn(
                        "CourseKml",
                        "Import skipped invalid route category=${categoryData.category.name}: route=${route.name} reason=$reason"
                    )
                    return@forEach
                }
                matchedCategoryIds += categoryData.category.id
                matchedCategoryNames += categoryData.category.name
                val assignmentControls = routeLineControls.ifEmpty { explicitAssignmentControls.orEmpty() }
                categoryAssignmentUpdate(
                    projectFile = updatedProject,
                    categoryId = categoryData.category.id,
                    controls = assignmentControls
                )?.let(categoryAssignmentUpdates::add)
                val duplicateRouteControlIdValues = routeLineControls.map { it.controlId }
                val storedRouteControlIds = sameSourceCourseInfo?.controlPoints.orEmpty()
                    .map { it.controlId }
                val storedRouteMatchesImported = sameSourceCourseInfo?.routeMatches(routeGeometry) == true
                if (
                    sameSourceCourseInfo != null &&
                    sameSourceCourseInfo.hasImportedLocationRecords() &&
                    storedRouteControlIds == duplicateRouteControlIdValues &&
                    storedRouteMatchesImported
                ) {
                    val missingElevationCount = missingElevationCount(sameSourceCourseInfo)
                    DesktopDebugLog.info(
                        "CourseKml",
                        "Import duplicate skipped for category=${categoryData.category.name}: hash=${sourceSha256.shortHash()} missingElevationPoints=$missingElevationCount"
                    )
                    duplicateCategoryCount++
                    duplicateMissingElevationPointCount += missingElevationCount
                    return@forEach
                }
                if (sameSourceCourseInfo != null) {
                    DesktopDebugLog.info(
                        "CourseKml",
                        "Import duplicate hash will be reprocessed for category=${categoryData.category.name}: " +
                            "hash=${sourceSha256.shortHash()} existingRoutePoints=${sameSourceCourseInfo.route.size} " +
                            "existingRouteElevations=${sameSourceCourseInfo.route.count { it.elevationMeters != null }} " +
                            "existingControlPoints=${sameSourceCourseInfo.controlPoints.size} " +
                            "existingCourseObjects=${sameSourceCourseInfo.courseObjects.size} " +
                            "storedRouteMatchesImported=$storedRouteMatchesImported " +
                            "storedRouteControls=${storedRouteControlIds.joinToString()} " +
                            "importRouteControls=${duplicateRouteControlIdValues.joinToString()}"
                    )
                }
                val importedSampledRoute = sampledRoute(routeGeometry, ROUTE_SAMPLE_METERS).map { point ->
                    val elevation = elevationProvider(point)
                    if (elevation != null) {
                        routeElevationPointCount++
                    }
                    point.copy(elevationMeters = elevation)
                }
                val sampledRoute = sameSourceCourseInfo
                    ?.takeIf { storedRouteMatchesImported }
                    ?.route
                    ?.takeIf { points -> points.isNotEmpty() && points.any { it.elevationMeters != null } }
                    ?.map { point ->
                        CourseGeoPoint(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            elevationMeters = point.elevationMeters
                        )
                    }
                    ?: importedSampledRoute
                val routeControls = controlsOnRoute(sampledRoute, routeMatchedControls)

                val idealOrder = routeControls.joinToString(" ") { it.label }
                val allProtectedControlPoints = routeMatchedControls.map { control ->
                    val elevation = sameSourceCourseInfo?.elevationFor(control) ?: elevationProvider(control.point)
                    control.controlId to ProtectedCourseControlPoint(
                        controlId = control.controlId,
                        label = control.label,
                        latitude = control.point.latitude,
                        longitude = control.point.longitude,
                        type = control.type,
                        elevationMeters = elevation
                    )
                }.toMap()
                val controlPoints = routeControls.mapNotNull { allProtectedControlPoints[it.controlId] }
                val courseObjects = courseObjectsForRoute(sampledRoute, allProtectedControlPoints.values.toList())
                DesktopDebugLog.info(
                    "CourseKml",
                    "Import matched category=${categoryData.category.name}: route=${route.name} sampledRoutePoints=${sampledRoute.size} idealOrder='${idealOrder.ifBlank { "none" }}' routeControls=${controlPoints.size} visibleControls=${routeMatchedControls.size} courseObjects=${courseObjects.size} routeElevations=${sampledRoute.count { it.elevationMeters != null }} controlElevations=${controlPoints.count { it.elevationMeters != null }}"
                )
                // Route-derived length and climb facts are competition-sensitive. Store them only in
                // the encrypted category payload; assigned controls are updated separately from the
                // matched map point controls.
                val protectedCourseInfo = ProtectedCourseInfo(
                    idealOrder = idealOrder,
                    lengthMeters = routeLengthMeters(sampledRoute).roundToInt(),
                    climbMeters = climbMetersOrNull(sampledRoute),
                    sourceName = path.fileName.toString(),
                    sourceSha256 = sourceSha256,
                    sampledPointCount = sampledRoute.size,
                    route = sampledRoute.map { point ->
                        ProtectedCourseRoutePoint(
                            latitude = point.latitude,
                            longitude = point.longitude,
                            elevationMeters = point.elevationMeters
                        )
                    },
                    controlPoints = controlPoints,
                    courseObjects = courseObjects
                )
                missingElevationPointCount += missingElevationCount(protectedCourseInfo)
                val encryptedCourseInfo = DesktopProtectedCourseOrder.encryptCourseInfo(protectedCourseInfo, password)
                val encryptedIdealOrder = idealOrder.takeIf { it.isNotBlank() }?.let {
                    DesktopProtectedCourseOrder.encrypt(it, password)
                }
                updatedProject = EventProjectEditor.updateCategoryEncryptedCourseInfo(
                    updatedProject,
                    categoryData.category.id,
                    encryptedCourseInfo
                )
                updatedProject = EventProjectEditor.updateCategoryEncryptedIdealOrder(
                    updatedProject,
                    categoryData.category.id,
                    encryptedIdealOrder
                )
                importedCategoryCount++
            }
        }

        require(matchedCategoryCount > 0 || controls.isNotEmpty() || missingCategoryNames.isNotEmpty()) {
            "No route names matched Event File category names, and no point controls matched existing controls."
        }

        val summary = DesktopCourseKmlImportSummary(
            matchedCategoryCount = matchedCategoryCount,
            routeCount = courseData.routes.size,
            controlPointCount = courseData.controls.size,
            matchedControlPointCount = controls.size,
            matchedFoxCount = controls.count { it.type == ControlPointType.CONTROL },
            matchedBeaconCount = controls.count { it.type == ControlPointType.BEACON },
            matchedSpectatorCount = controls.count { it.type == ControlPointType.SEPARATOR },
            labelConversions = matchedControlsForHintedProject.labelConversions,
            matchedCategoryIds = matchedCategoryIds,
            matchedCategoryNames = matchedCategoryNames,
            routeElevationPointCount = routeElevationPointCount,
            missingElevationPointCount = missingElevationPointCount,
            importedCategoryCount = importedCategoryCount,
            categoryAssignmentUpdates = categoryAssignmentUpdates,
            changedControlLocationCount = controlLocationUpdates.size,
            controlLocationAffectedCategoryCount = locationUpdateResult?.affectedCategoryCount ?: 0,
            duplicateCategoryCount = duplicateCategoryCount,
            duplicateMissingElevationPointCount = duplicateMissingElevationPointCount,
            missingCategoryNames = missingCategoryNames,
            createdCategoryNames = createdCategoryNames,
            missingControlNames = missingControls.map { it.displayCourseLabel() },
            createdControlNames = createdControlNames,
            categoryAssumptions = routeCategoryTargets.assumptions,
            rejectedRoutes = rejectedRoutes,
            eventTypeWarnings = DesktopImportPreviews.eventTypeWarnings(
                eventRaceType = projectFile.raceData.race.raceType,
                sourceName = path.fileName.toString(),
                clues = courseData.routes.map { it.name } + courseData.controls.map { it.name },
                controlCount = courseData.controls.size,
                controlTypes = controls.map { it.type }
            ),
            sourceSha256 = sourceSha256,
            controlIdentityUpdateCount = matchedControlResult.controls.count { it.siCodeHint != null && it.siCodeHint != it.siCode }
        )
        DesktopDebugLog.info(
            "CourseKml",
            "Import summary for ${path.fileName}: hash=${sourceSha256.shortHash()} matchedCategories=${summary.matchedCategoryCount} importedCategories=${summary.importedCategoryCount} assignedCategoryControls=${summary.assignedCategoryControlCount} changedControlLocations=${summary.changedControlLocationCount} duplicateCategories=${summary.duplicateCategoryCount} matchedControls=${summary.matchedControlPointCount}/${summary.controlPointCount} missingControls=${summary.missingControlNames.size} createdControls=${summary.createdControlNames.size} labelConversions=${summary.labelConversions.size} missingElevationPoints=${summary.missingElevationPointCount} duplicateMissingElevationPoints=${summary.duplicateMissingElevationPointCount}"
        )
        return updatedProject to summary
    }

    private fun missingRouteCategoryNames(
        routes: List<CourseRoute>,
        categories: List<EventCategoryData>,
        sourceName: String,
        categoryOverrideId: String?
    ): List<String> {
        val existingNames = categories.mapTo(mutableSetOf()) { it.category.name.categoryMatchText() }
        val listedCategoryNames = routes.flatMap { route -> route.name.listedCategoryNames() }
        val assumedCategoryNames = routeCategoryTargets(
            routes = routes,
            categories = categories,
            sourceName = sourceName,
            categoryOverrideId = categoryOverrideId
        ).assumptions.map { it.categoryName }
        return (listedCategoryNames + assumedCategoryNames)
            .distinctBy { it.categoryMatchText() }
            .filterNot { it.categoryMatchText() in existingNames }
    }

    private data class RouteCategoryTargetResult(
        val targets: Map<CourseRoute, List<EventCategoryData>>,
        val assumptions: List<DesktopCourseKmlCategoryAssumption>
    )

    private fun routeCategoryTargets(
        routes: List<CourseRoute>,
        categories: List<EventCategoryData>,
        sourceName: String,
        categoryOverrideId: String?
    ): RouteCategoryTargetResult {
        val targets = mutableMapOf<CourseRoute, List<EventCategoryData>>()
        val usedCategoryIds = mutableSetOf<String>()
        routes.forEach { route ->
            val matchedCategories = routeMatchedCategories(route, categories)
            if (matchedCategories.isEmpty()) {
                return@forEach
            }
            targets[route] = matchedCategories
            usedCategoryIds += matchedCategories.map { it.category.id }
        }

        val unmatchedRoutes = routes.filterNot { it in targets }
        if (unmatchedRoutes.size == 1) {
            val inferredCategory = filenameMatchedCategory(sourceName, categories)
                ?: categoryOverrideId
                    ?.let { id -> categories.firstOrNull { categoryData -> categoryData.category.id == id } }
            if (inferredCategory != null && inferredCategory.category.id !in usedCategoryIds) {
                targets[unmatchedRoutes.single()] = listOf(inferredCategory)
                usedCategoryIds += inferredCategory.category.id
            }
        }
        val assumptions = mutableListOf<DesktopCourseKmlCategoryAssumption>()
        val remainingUnmatchedRoutes = routes
            .filterNot { it in targets }
            .filter { it.name.listedCategoryNames().isEmpty() }
        val fallbackCategoryNames = categoryAssumptionNames(categories, usedCategoryIds, remainingUnmatchedRoutes.size)
        remainingUnmatchedRoutes.zip(fallbackCategoryNames).forEach { (route, categoryName) ->
            assumptions += DesktopCourseKmlCategoryAssumption(route.name, categoryName)
            categories.firstOrNull { it.category.name.categoryMatchText() == categoryName.categoryMatchText() }
                ?.takeIf { it.category.id !in usedCategoryIds }
                ?.let { categoryData ->
                    targets[route] = listOf(categoryData)
                    usedCategoryIds += categoryData.category.id
                }
        }
        return RouteCategoryTargetResult(targets, assumptions)
    }

    private fun routeMatchedCategories(
        route: CourseRoute,
        categories: List<EventCategoryData>
    ): List<EventCategoryData> {
        val exactCategoryData = categories.firstOrNull { categoryData ->
            categoryData.category.name.matchesCategoryRouteName(route.name)
        }
        return exactCategoryData
            ?.let(::listOf)
            ?: categories.filter { categoryData ->
                route.name.containsEmbeddedCategoryName(categoryData.category.name)
            }
    }

    private fun categoryAssumptionNames(
        categories: List<EventCategoryData>,
        usedCategoryIds: Set<String>,
        count: Int
    ): List<String> {
        if (count <= 0) {
            return emptyList()
        }
        val usedCategoryNames = categories
            .filter { it.category.id in usedCategoryIds }
            .mapTo(mutableSetOf()) { it.category.name.categoryMatchText() }
        val availableExistingNames = categories
            .filterNot { it.category.id in usedCategoryIds }
            .map { it.category.name }
        val availableExistingByMatchText = availableExistingNames.associateBy { it.categoryMatchText() }
        val orderedExistingNames = CATEGORY_ASSUMPTION_SEQUENCE.mapNotNull { fallbackName ->
            availableExistingByMatchText[fallbackName.categoryMatchText()]
        }
        val otherExistingNames = availableExistingNames.filterNot { existingName ->
            CATEGORY_ASSUMPTION_SEQUENCE.any { it.categoryMatchText() == existingName.categoryMatchText() }
        }
        return (listOf("M21") + orderedExistingNames + otherExistingNames + CATEGORY_ASSUMPTION_SEQUENCE)
            .distinctBy { it.categoryMatchText() }
            .filterNot { it.categoryMatchText() in usedCategoryNames }
            .take(count)
    }

    private fun filenameMatchedCategory(
        sourceName: String,
        categories: List<EventCategoryData>
    ): EventCategoryData? {
        val filenameText = sourceName.substringBeforeLast('.').categoryMatchText()
        return categories
            .filter { categoryData -> filenameText.containsCategoryName(categoryData.category.name) }
            .singleOrNull()
    }

    private fun isSprintCourseImport(projectFile: EventProjectFile, courseData: DesktopCourseKmlData): Boolean =
        projectFile.raceData.race.raceType == RaceType.SPRINT ||
            courseData.routes.any { route -> route.name.contains("sprint", ignoreCase = true) }

    @Suppress("UNUSED_PARAMETER")
    private fun controlMatchingCourseControls(
        importedControls: List<CourseControlPoint>,
        routes: List<CourseRoute>,
        sprintContext: Boolean
    ): List<CourseControlPoint> {
        /*
         * Sprint maps can legitimately contain two bare "S" points: one at the route endpoint for
         * start and one mid-route for spectator. Do not globally discard endpoint-looking S points
         * here because a bad or unrelated LineString can have an endpoint near the real spectator.
         * The per-route matcher below decides which S is start vs spectator using the route being
         * imported.
         */
        return importedControls
    }

    private fun endpointAwareImportedControls(
        routePoints: List<CourseGeoPoint>,
        importedControls: List<CourseControlPoint>,
        raceType: RaceType
    ): List<CourseControlPoint> {
        if (raceType != RaceType.SPRINT) {
            return importedControls
        }
        return importedControls.map { control ->
            if (control.name.isBareSprintStartLabel() && control.point.isNearRouteEndpoint(routePoints)) {
                control.copy(name = "Start")
            } else {
                control
            }
        }
    }

    private fun matchedControlsForRoute(
        raceType: RaceType,
        routePoints: List<CourseGeoPoint>,
        matchedControls: List<CourseMatchedControl>
    ): List<CourseMatchedControl> {
        if (raceType != RaceType.SPRINT) {
            return matchedControls.distinctBy { it.controlId }
        }
        return matchedControls
            .groupBy { it.controlId }
            .mapNotNull { (_, matches) ->
                if (matches.any { it.type == ControlPointType.SEPARATOR }) {
                    matches.firstOrNull { !it.point.isNearRouteEndpoint(routePoints) }
                } else {
                    matches.first()
                }
            }
    }

    suspend fun fetchProtectedCourseElevations(
        projectFile: EventProjectFile,
        categoryIds: List<String>,
        password: String,
        elevationProvider: (suspend (CourseGeoPoint) -> Double?)? = null,
        batchElevationProvider: suspend (List<CourseGeoPoint>) -> List<Double?> = { points ->
            val pointProvider = elevationProvider
            if (pointProvider == null) {
                DesktopVenueElevationCache.usgs3DepElevations(points)
            } else {
                val values = mutableListOf<Double?>()
                points.forEach { point ->
                    values += pointProvider(point)
                }
                values
            }
        },
        localElevationProvider: (CourseGeoPoint) -> Double? = { point ->
            DesktopVenueElevationCache.elevationMeters(point)
        },
        onProgress: (DesktopRouteElevationProgress) -> Unit = {}
    ): Pair<EventProjectFile, DesktopRouteElevationResult> {
        val categoryIdSet = categoryIds.toSet()
        val categories = projectFile.raceData.categories
            .filter { it.category.id in categoryIdSet }
            .mapNotNull { categoryData ->
                val encryptedCourseInfo = categoryData.category.encryptedCourseInfo?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val courseInfo = DesktopProtectedCourseOrder.decryptCourseInfo(encryptedCourseInfo, password)
                if (courseInfo.route.size < 2) {
                    null
                } else {
                    CategoryRouteElevationTarget(
                        categoryId = categoryData.category.id,
                        categoryName = categoryData.category.name,
                        courseInfo = courseInfo,
                        sampledRoute = routeWithMissingElevationSamples(courseInfo),
                        courseObjects = courseObjectsForCourseInfo(courseInfo)
                    )
                }
            }

        require(categories.isNotEmpty()) {
            "No imported route geometry is available for elevation retrieval."
        }
        val totalPointCount = categories.sumOf { it.missingElevationCount() }
        categories.forEach { target ->
            DesktopDebugLog.info(
                "CourseElevation",
                "Fetch plan category=${target.categoryName}: routeMissing=${target.missingRouteElevationCount()} courseObjectMissing=${target.missingCourseObjectElevationCount()} controlLocationMissing=${target.missingProtectedControlElevationCount()} totalRequested=${target.missingElevationCount()}"
            )
        }
        DesktopDebugLog.info(
            "CourseElevation",
            "Fetch started categories=${categories.size} totalRequested=$totalPointCount"
        )
        if (totalPointCount == 0) {
            DesktopDebugLog.warn(
                "CourseElevation",
                "Fetch requested with no missing route, course-object, or control-location elevation points."
            )
        }

        var completedPointCount = 0
        var elevatedPointCount = 0
        var resolvedPointCount = 0
        var cachedPointCount = 0
        var updatedProject = projectFile

        fun emitProgress(categoryName: String) {
            onProgress(
                DesktopRouteElevationProgress(
                    completedPointCount = completedPointCount,
                    totalPointCount = totalPointCount,
                    categoryName = categoryName,
                    downloadedPointCount = elevatedPointCount,
                    cachedPointCount = cachedPointCount
                )
            )
        }

        suspend fun resolveElevations(points: List<CourseGeoPoint>, categoryName: String): List<Pair<Double?, Boolean>> {
            if (points.isEmpty()) {
                return emptyList()
            }
            val resolved = MutableList<Pair<Double?, Boolean>?>(points.size) { null }
            val remoteIndexes = mutableListOf<Int>()
            val remotePoints = mutableListOf<CourseGeoPoint>()
            points.forEachIndexed { index, point ->
                kotlin.coroutines.coroutineContext.ensureActive()
                val cachedElevation = localElevationProvider(point)
                if (cachedElevation != null) {
                    completedPointCount++
                    resolvedPointCount++
                    cachedPointCount++
                    resolved[index] = cachedElevation to true
                    emitProgress(categoryName)
                } else {
                    remoteIndexes += index
                    remotePoints += point
                }
            }
            if (remotePoints.isNotEmpty()) {
                val remoteElevations = batchElevationProvider(remotePoints)
                require(remoteElevations.size == remotePoints.size) {
                    "Elevation provider returned ${remoteElevations.size} values for ${remotePoints.size} requested points."
                }
                remoteElevations.forEachIndexed { remoteIndex, elevation ->
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val pointIndex = remoteIndexes[remoteIndex]
                    completedPointCount++
                    if (elevation != null) {
                        resolvedPointCount++
                        elevatedPointCount++
                    }
                    resolved[pointIndex] = elevation to false
                    emitProgress(categoryName)
                }
            }
            return resolved.map { it ?: (null to false) }
        }

        emitProgress(categories.first().categoryName)

        categories.forEach { target ->
            val routeResolutions = resolveElevations(
                target.sampledRoute.filter { it.elevationMeters == null },
                target.categoryName
            ).iterator()
            val elevatedRoute = target.sampledRoute.map { point ->
                point.elevationMeters?.let { return@map point }
                point.copy(elevationMeters = routeResolutions.next().first)
            }
            val courseObjectResolutions = resolveElevations(
                target.courseObjects
                    .filter { it.elevationMeters == null }
                    .map { courseObject ->
                        CourseGeoPoint(
                            latitude = courseObject.latitude,
                            longitude = courseObject.longitude,
                            elevationMeters = null
                        )
                    },
                target.categoryName
            ).iterator()
            val elevatedCourseObjects = target.courseObjects.map { courseObject ->
                courseObject.elevationMeters?.let { return@map courseObject }
                courseObject.copy(elevationMeters = courseObjectResolutions.next().first)
            }
            val routeFetched = elevatedRoute.count { point ->
                target.sampledRoute.any { original ->
                    original.elevationMeters == null &&
                        original.latitude == point.latitude &&
                        original.longitude == point.longitude
                } && point.elevationMeters != null
            }
            val courseObjectFetched = elevatedCourseObjects.count { courseObject ->
                target.courseObjects.any { original ->
                    original.id == courseObject.id && original.elevationMeters == null
                } && courseObject.elevationMeters != null
            }
            val objectElevationByControlId = elevatedCourseObjects
                .filter { it.type == ProtectedCourseObjectType.CONTROL || it.type == ProtectedCourseObjectType.BEACON || it.type == ProtectedCourseObjectType.SPECTATOR }
                .associateBy { it.id }
            val controlResolutions = resolveElevations(
                target.courseInfo.controlPoints
                    .filter { control ->
                        control.elevationMeters == null &&
                            objectElevationByControlId[control.controlId]?.elevationMeters == null
                    }
                    .map { control ->
                        CourseGeoPoint(
                            latitude = control.latitude,
                            longitude = control.longitude,
                            elevationMeters = null
                        )
                    },
                target.categoryName
            ).iterator()
            val elevatedControlPoints = target.courseInfo.controlPoints.map { control ->
                objectElevationByControlId[control.controlId]?.elevationMeters?.let { objectElevation ->
                    if (control.elevationMeters == null) {
                        completedPointCount++
                        resolvedPointCount++
                        emitProgress(target.categoryName)
                    }
                    return@map control.copy(elevationMeters = objectElevation)
                }
                if (control.elevationMeters != null) {
                    return@map control
                }
                val elevation = controlResolutions.next().first
                control.copy(elevationMeters = elevation)
            }
            val controlFetched = elevatedControlPoints.count { control ->
                target.courseInfo.controlPoints.any { original ->
                    original.controlId == control.controlId && original.elevationMeters == null
                } && control.elevationMeters != null
            }
            val updatedCourseInfo = target.courseInfo.copy(
                lengthMeters = routeLengthMeters(elevatedRoute).roundToInt(),
                climbMeters = climbMetersOrNull(elevatedRoute),
                sampledPointCount = elevatedRoute.size,
                route = elevatedRoute.map { point ->
                    ProtectedCourseRoutePoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        elevationMeters = point.elevationMeters
                    )
                },
                controlPoints = elevatedControlPoints,
                courseObjects = elevatedCourseObjects
            )
            updatedProject = EventProjectEditor.updateCategoryEncryptedCourseInfo(
                updatedProject,
                target.categoryId,
                DesktopProtectedCourseOrder.encryptCourseInfo(updatedCourseInfo, password)
            )
            DesktopDebugLog.info(
                "CourseElevation",
                "Fetch category complete category=${target.categoryName}: routeFetched=$routeFetched courseObjectFetched=$courseObjectFetched controlLocationFetched=$controlFetched remainingRouteMissing=${elevatedRoute.count { it.elevationMeters == null }} remainingCourseObjectMissing=${elevatedCourseObjects.count { it.elevationMeters == null }} remainingControlLocationMissing=${updatedCourseInfo.controlPoints.count { it.elevationMeters == null }}"
            )
        }

        val result = DesktopRouteElevationResult(
            categoryCount = categories.size,
            sampledPointCount = totalPointCount,
            elevatedPointCount = elevatedPointCount,
            resolvedPointCount = resolvedPointCount,
            cachedPointCount = cachedPointCount
        )
        val logMessage = "Fetch summary categories=${result.categoryCount} requested=${result.sampledPointCount} downloaded=${result.elevatedPointCount} cached=${result.cachedPointCount} resolved=${result.resolvedPointCount}"
        if (result.resolvedPointCount == 0) {
            DesktopDebugLog.warn("CourseElevation", logMessage)
        } else {
            DesktopDebugLog.info("CourseElevation", logMessage)
        }
        return updatedProject to result
    }

    fun parse(path: Path): DesktopCourseKmlData {
        val fileName = path.fileName.toString()
        return when {
            fileName.endsWith(".gpx", ignoreCase = true) -> parseGpx(Files.readString(path))
            fileName.endsWith(".kmz", ignoreCase = true) -> parseKml(readKmlFromKmz(path))
            else -> parseKml(Files.readString(path))
        }
    }

    private fun parseKml(kmlText: String): DesktopCourseKmlData {
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(kmlText.toByteArray()))
        val placemarks = document.getElementsByTagNameNS("*", "Placemark")
        val controls = mutableListOf<CourseControlPoint>()
        val routes = mutableListOf<CourseRoute>()
        repeat(placemarks.length) { index ->
            val placemark = placemarks.item(index)
            val name = placemark.childText("name")?.trim().orEmpty()
            if (name.isBlank()) {
                return@repeat
            }
            val pointCoordinates = placemark
                .firstDescendantText("Point", "coordinates")
                ?.let(::parseCoordinates)
                ?.firstOrNull()
            if (pointCoordinates != null) {
                controls += CourseControlPoint(
                    name = name,
                    point = pointCoordinates,
                    siCodeHint = placemark.childText("description").siCodeHint()
                )
                return@repeat
            }
            val lineCoordinates = placemark
                .firstDescendantText("LineString", "coordinates")
                ?.let(::parseCoordinates)
                .orEmpty()
            if (lineCoordinates.size >= 2) {
                routes += CourseRoute(name = name, points = lineCoordinates)
            }
        }
        require(controls.isNotEmpty()) {
            "KML/KMZ file did not contain named control point placemarks."
        }
        return DesktopCourseKmlData(controls = controls, routes = routes)
    }

    private fun parseGpx(gpxText: String): DesktopCourseKmlData {
        val document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(gpxText.toByteArray()))
        val controls = mutableListOf<CourseControlPoint>()
        document.documentElement.namedDescendants("wpt").forEach { waypoint ->
            waypoint.toGpxPoint()?.let { point ->
                waypoint.childText("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                    controls += CourseControlPoint(name = name, point = point)
                }
            }
        }

        val routes = mutableListOf<CourseRoute>()
        document.documentElement.namedDescendants("rte").forEachIndexed { index, route ->
            val routeName = route.childText("name")?.trim().orEmpty()
                .ifBlank { "Route ${index + 1}" }
            val points = route.namedDescendants("rtept").mapNotNull { routePoint ->
                routePoint.toGpxPoint()?.also { point ->
                    routePoint.childText("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        controls += CourseControlPoint(name = name, point = point)
                    }
                }
            }
            if (points.size >= 2) {
                routes += CourseRoute(name = routeName, points = points)
            }
        }
        document.documentElement.namedDescendants("trk").forEachIndexed { index, track ->
            val trackName = track.childText("name")?.trim().orEmpty()
                .ifBlank { "Track ${index + 1}" }
            val points = track.namedDescendants("trkpt").mapNotNull { trackPoint ->
                trackPoint.toGpxPoint()?.also { point ->
                    trackPoint.childText("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        controls += CourseControlPoint(name = name, point = point)
                    }
                }
            }
            if (points.size >= 2) {
                routes += CourseRoute(name = trackName, points = points)
            }
        }

        val distinctControls = controls.distinctBy {
            "${it.name.normalizedCourseName()}|${it.point.locationKey()}"
        }
        require(distinctControls.isNotEmpty()) {
            "GPX file did not contain named control waypoints."
        }
        return DesktopCourseKmlData(controls = distinctControls, routes = routes)
    }

    private fun readKmlFromKmz(path: Path): String {
        ZipInputStream(Files.newInputStream(path)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".kml", ignoreCase = true)) {
                    return zip.readBytes().toString(StandardCharsets.UTF_8)
                }
            }
        }
        throw IllegalArgumentException("KMZ file did not contain a KML document.")
    }

    private fun parseCoordinates(value: String): List<CourseGeoPoint> =
        value
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull { coordinate ->
                val fields = coordinate.split(',')
                val longitude = fields.getOrNull(0)?.toDoubleOrNull()
                val latitude = fields.getOrNull(1)?.toDoubleOrNull()
                val elevation = fields.getOrNull(2)?.toDoubleOrNull()
                if (latitude == null || longitude == null) {
                    null
                } else {
                    CourseGeoPoint(latitude = latitude, longitude = longitude, elevationMeters = elevation)
                }
            }

    private fun matchedControls(
        importedControls: List<CourseControlPoint>,
        eventControls: List<EventControl>
    ): CourseMatchedControlResult {
        // Control names from map files are user-authored, so match the visible
        // identifiers users can see in the Event File: SI code, control label,
        // and public label. Duplicate tokens are ignored to avoid guessing.
        val controlTokens = eventControls.flatMap { control ->
            listOfNotNull(
                ControlMatchToken(control.siCode.toString(), control),
                control.label.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) }
            )
        }
        val labelTokens = eventControls.flatMap { control ->
            listOfNotNull(
                control.label.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) }
            )
        }
        val controlsByToken = uniqueControlTokensBy(controlTokens) { it.token.normalizedCourseName() }
        val controlsByCompactToken = uniqueControlTokensBy(controlTokens) { it.token.compactCourseName() }
        val controlsByNumber = uniqueControlTokensBy(labelTokens) { it.token.singleEmbeddedNumber()?.toString().orEmpty() }
            .filterKeys { it.isNotBlank() }
        val labelConversions = mutableListOf<DesktopCourseKmlLabelConversion>()
        val controls = importedControls.mapNotNull { imported ->
            if (imported.name.isCourseEndpointName()) {
                return@mapNotNull null
            }
            val exactMatch = controlsByToken[imported.name.normalizedCourseName()]
            val match = exactMatch
                ?: controlsByCompactToken[imported.name.compactCourseName()]
                ?: imported.name.controlKeywordNumber()?.let { number -> controlsByNumber[number.toString()] }
                ?: imported.name.singleEmbeddedNumber()?.let { number -> controlsByNumber[number.toString()] }
            match?.takeIf { imported.name.trim() != it.token.trim() }?.let { token ->
                labelConversions += DesktopCourseKmlLabelConversion(
                    importedName = imported.name,
                    eventControlLabel = token.control.displayCourseLabel()
                )
            }
            match?.control?.let { control ->
                CourseMatchedControl(
                    controlId = control.id,
                    label = control.idealOrderToken(),
                    displayLabel = control.displayCourseLabel(),
                    siCode = control.siCode,
                    siCodeHint = imported.siCodeHint,
                    type = control.type,
                    point = imported.point
                )
            }
        }
        return CourseMatchedControlResult(
            controls = controls,
            labelConversions = labelConversions.distinct()
        )
    }

    private fun missingCourseControls(
        importedControls: List<CourseControlPoint>,
        eventControls: List<EventControl>,
        raceId: String
    ): List<EventControl> {
        val existingControlKeys = eventControls
            .mapTo(mutableSetOf()) { it.siCode to it.type }
        val existingLabels = eventControls
            .mapTo(mutableSetOf()) { it.label.normalizedCourseName() }
        return importedControls
            .mapNotNull { it.toInferredEventControl(raceId) }
            .distinctBy { it.siCode to it.type }
            .filter { control ->
                (control.siCode to control.type) !in existingControlKeys &&
                    control.label.normalizedCourseName() !in existingLabels
            }
    }

    private fun CourseControlPoint.toInferredEventControl(raceId: String): EventControl? {
        if (name.isCourseEndpointName()) {
            return null
        }
        val type = ControlRoleLabelRules.inferredRole(name) ?: ControlPointType.CONTROL
        val siCode = inferredControlSiCode(name, type, siCodeHint) ?: return null
        val label = inferredControlLabel(name, type, siCode)
        return EventControl(
            id = EventControlCatalog.stableId(label, siCode, type),
            raceId = raceId,
            label = label,
            siCode = siCode,
            type = type,
            publicLabel = name.trim().takeIf { it.isNotBlank() && it != label },
            latitude = point.latitude,
            longitude = point.longitude
        )
    }

    private fun inferredControlSiCode(
        name: String,
        type: ControlPointType,
        siCodeHint: Int?
    ): Int? {
        siCodeHint?.takeIf(SportIdentCodes::isSICodeValid)?.let { return it }
        sprintFastFoxNumber(name)?.let { return 40 + it }
        if (type == ControlPointType.SEPARATOR) {
            return name.singleEmbeddedNumber()?.takeIf(SportIdentCodes::isSICodeValid) ?: 46
        }
        if (type == ControlPointType.BEACON) {
            return name.singleEmbeddedNumber()?.takeIf(SportIdentCodes::isSICodeValid) ?: 99
        }
        val number = name.controlKeywordNumber() ?: name.singleEmbeddedNumber() ?: return null
        val siCode = if (number in 1..5) 30 + number else number
        return siCode.takeIf(SportIdentCodes::isSICodeValid)
    }

    private fun inferredControlLabel(
        name: String,
        type: ControlPointType,
        siCode: Int
    ): String {
        sprintFastFoxNumber(name)?.let { return "F$it" }
        return when (type) {
            ControlPointType.SEPARATOR -> "S"
            ControlPointType.BEACON -> "M"
            ControlPointType.CONTROL -> {
                val number = name.controlKeywordNumber() ?: name.singleEmbeddedNumber()
                if (number != null && siCode == 30 + number) {
                    number.toString()
                } else {
                    name.trim().takeIf { it.isNotBlank() } ?: EventControlCatalog.defaultLabel(siCode, type)
                }
            }
        }
    }

    private fun sprintFastFoxNumber(name: String): Int? {
        val compactName = name.compactCourseName().uppercase()
        val match = Regex("""^(?:F([1-5])|([1-5])F)$""").matchEntire(compactName) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull()
    }

    private fun applyControlSiHints(
        projectFile: EventProjectFile,
        matchedControls: List<CourseMatchedControl>
    ): EventProjectFile {
        val updates = matchedControls
            .filter { matchedControl -> matchedControl.siCodeHint != null && matchedControl.siCodeHint != matchedControl.siCode }
            .distinctBy { it.controlId }
        if (updates.isEmpty()) {
            return projectFile
        }
        return updates.fold(projectFile) { currentProject, matchedControl ->
            val control = currentProject.raceData.controls.firstOrNull { it.id == matchedControl.controlId }
                ?: return@fold currentProject
            EventProjectEditor.updateControl(
                projectFile = currentProject,
                controlId = control.id,
                label = control.label,
                siCode = matchedControl.siCodeHint!!.toString(),
                type = control.type,
                scored = control.scored,
                publicLabel = control.publicLabel.orEmpty(),
                notes = control.notes.orEmpty()
            )
        }
    }

    private fun uniqueControlTokensBy(
        controlTokens: List<ControlMatchToken>,
        key: (ControlMatchToken) -> String
    ): Map<String, ControlMatchToken> {
        return controlTokens
            .groupBy(key)
            .mapNotNull { (token, matches) ->
                matches.map { it.control.id }.distinct().singleOrNull()?.let {
                    token to matches.first()
                }
            }
            .toMap()
    }

    private fun controlsFromExplicitClassicRouteOrder(
        routeName: String,
        controls: List<CourseMatchedControl>
    ): List<CourseMatchedControl>? {
        val tokens = explicitClassicRouteOrderTokens(routeName)
        if (tokens.isEmpty()) {
            return null
        }
        val controlsByToken = uniqueMatchedControlsByRouteToken(controls)
        val orderedControls = tokens.map { token ->
            controlsByToken[token.normalizedCourseName()]
                ?: controlsByToken[token.compactCourseName()]
                ?: return null
        }
        return orderedControls.distinctBy { it.controlId }.takeIf { it.isNotEmpty() }
    }

    private fun explicitClassicRouteOrderTokens(routeName: String): List<String> {
        val suffix = routeName.substringAfterLast('-', missingDelimiterValue = "").trim()
        if (suffix.isBlank() || suffix == routeName || (',' !in suffix && ';' !in suffix)) {
            return emptyList()
        }
        return suffix
            .split(Regex("[,;\\s]+"))
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotBlank() }
    }

    private fun uniqueMatchedControlsByRouteToken(
        controls: List<CourseMatchedControl>
    ): Map<String, CourseMatchedControl> {
        val tokens = controls.flatMap { control ->
            listOfNotNull(
                control.label,
                control.displayLabel,
                control.siCode.toString(),
                control.assignedControlToken(),
                control.label.controlKeywordNumber()?.toString(),
                control.displayLabel.controlKeywordNumber()?.toString(),
                control.displayLabel.singleEmbeddedNumber()?.toString()
            )
                .filter { it.isNotBlank() }
                .map { token -> token.normalizedCourseName() to control } +
                listOfNotNull(
                    control.label.compactCourseName(),
                    control.displayLabel.compactCourseName()
                )
                    .filter { it.isNotBlank() }
                    .map { token -> token to control }
        }
        return tokens
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (token, matches) ->
                matches.map { it.controlId }.distinct().singleOrNull()?.let { token to matches.first() }
            }
            .toMap()
    }

    private fun controlLocationUpdates(
        matchedControls: List<CourseMatchedControl>,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        ignoredCategoryIds: Set<String> = emptySet()
    ): List<DesktopProtectedControlLocationUpdate> {
        return matchedControls
            .distinctBy { it.controlId }
            .mapNotNull { matchedControl ->
                val protectedLocationDiffers = courseInfoByCategoryId.any { (categoryId, courseInfo) ->
                    if (categoryId in ignoredCategoryIds) {
                        return@any false
                    }
                    courseInfo.controlPoints.any { controlPoint ->
                        controlPoint.controlId == matchedControl.controlId &&
                            (!sameCoordinate(controlPoint.latitude, matchedControl.point.latitude) ||
                                !sameCoordinate(controlPoint.longitude, matchedControl.point.longitude))
                    } ||
                        courseInfo.courseObjects.any { courseObject ->
                            courseObject.id == matchedControl.controlId &&
                                (!sameCoordinate(courseObject.latitude, matchedControl.point.latitude) ||
                                    !sameCoordinate(courseObject.longitude, matchedControl.point.longitude))
                        }
                }
                if (protectedLocationDiffers) {
                    DesktopProtectedControlLocationUpdate(
                        controlId = matchedControl.controlId,
                        latitude = matchedControl.point.latitude,
                        longitude = matchedControl.point.longitude
                    )
                } else {
                    null
                }
            }
    }

    private fun sameSourceDuplicateCategoryIds(
        routes: List<CourseRoute>,
        routeCategoryTargets: RouteCategoryTargetResult,
        eventRace: EventRace,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>,
        sourceSha256: String,
        importedControls: List<CourseControlPoint>,
        matchedControls: List<CourseMatchedControl>
    ): Set<String> {
        val duplicateCategoryIds = linkedSetOf<String>()
        routes.forEach { route ->
            routeCategoryTargets.targets[route].orEmpty().forEach { categoryData ->
                val raceType = categoryData.category.effectiveRaceType(eventRace)
                val routeImportedControls = endpointAwareImportedControls(route.points, importedControls, raceType)
                val routeMatchedControls = matchedControlsForRoute(raceType, route.points, matchedControls)
                val routeGeometry = normalizedRouteGeometry(route.points, routeImportedControls, routeMatchedControls)
                val routeLineControlIds = controlsOnRoute(routeGeometry, routeMatchedControls).map { it.controlId }
                val sameSourceCourseInfo = courseInfoByCategoryId[categoryData.category.id]
                    ?.takeIf { it.sourceSha256 == sourceSha256 }
                    ?: return@forEach
                val storedRouteControlIds = sameSourceCourseInfo.controlPoints.map { it.controlId }
                if (
                    sameSourceCourseInfo.hasImportedLocationRecords() &&
                    storedRouteControlIds == routeLineControlIds &&
                    sameSourceCourseInfo.routeMatches(routeGeometry)
                ) {
                    duplicateCategoryIds += categoryData.category.id
                }
            }
        }
        return duplicateCategoryIds
    }

    fun applyCategoryAssignmentUpdates(
        projectFile: EventProjectFile,
        updates: List<DesktopCourseKmlCategoryAssignmentUpdate>
    ): EventProjectFile {
        if (updates.isEmpty()) {
            return projectFile
        }
        return updates.fold(projectFile) { currentProject, update ->
            // Replace, rather than merge, so an imported route cannot leave stale assigned foxes
            // behind. The review already resolved exact stored controls and sorted them into
            // neutral control order; the shared editor keeps derived category fields consistent.
            EventProjectEditor.replaceCategoryAssignedControls(
                projectFile = currentProject,
                categoryId = update.categoryId,
                controlIds = update.controls.map { it.controlId }
            ) { index ->
                "${update.categoryId}-kml-control-${index + 1}"
            }
        }
    }

    private fun categoryAssignmentUpdate(
        projectFile: EventProjectFile,
        categoryId: String,
        controls: List<CourseMatchedControl>
    ): DesktopCourseKmlCategoryAssignmentUpdate? {
        val categoryData = projectFile.raceData.categories
            .firstOrNull { it.category.id == categoryId }
            ?: return null
        val raceType = categoryData.category.effectiveRaceType(projectFile.raceData.race)
        // Category assignments are public course requirements, not ideal route advice. Always sort
        // them by normal fox/beacon/spectator order even when the LineString itself is in ideal
        // traversal order.
        val sortedControls = controls
            .distinctBy { it.controlId }
            .sortedWith(
                compareBy<CourseMatchedControl> {
                    ControlPointRules.assignedControlSortGroup(it.siCode, it.type, raceType)
                }
                    .thenBy { it.displayLabel.singleEmbeddedNumber() ?: Int.MAX_VALUE }
                    .thenBy { it.siCode }
                    .thenBy { it.displayLabel.normalizedCourseName() }
                    .thenBy { it.type.value }
            )
        val assignedControlsText = sortedControls
            .joinToString(" ") { it.assignedControlToken() }
        if (assignedControlsText.isBlank()) {
            return null
        }
        val currentControlIds = categoryData.controlPoints
            .map { it.controlId }
            .toList()
        val nextControlIds = sortedControls.map { it.controlId }
        if (currentControlIds == nextControlIds) {
            return null
        }
        return DesktopCourseKmlCategoryAssignmentUpdate(
            categoryId = categoryData.category.id,
            categoryName = categoryData.category.name,
            controlPointsText = assignedControlsText,
            controls = sortedControls.map { control ->
                DesktopCourseKmlAssignedControl(
                    controlId = control.controlId,
                    siCode = control.siCode,
                    type = control.type
                )
            }
        )
    }

    private fun sameCoordinate(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) < 0.0000001

    private fun orientedRoutePoints(
        routePoints: List<CourseGeoPoint>,
        importedControls: List<CourseControlPoint>
    ): List<CourseGeoPoint> {
        val geometry = routePoints.map { it.copy(elevationMeters = null) }
        if (geometry.size < 2) {
            return geometry
        }
        val startPoint = importedControls.firstOrNull { it.isCourseStartPoint() }?.point
        val finishPoint = importedControls.firstOrNull { it.isCourseFinishPoint() }?.point
        if (startPoint == null && finishPoint == null) {
            return geometry
        }
        val asDrawnDistance = listOfNotNull(
            startPoint?.let { geometry.first().distanceMetersTo(it) },
            finishPoint?.let { geometry.last().distanceMetersTo(it) }
        ).sum()
        val reversedDistance = listOfNotNull(
            finishPoint?.let { geometry.first().distanceMetersTo(it) },
            startPoint?.let { geometry.last().distanceMetersTo(it) }
        ).sum()
        return if (reversedDistance + ROUTE_ORIENTATION_TOLERANCE_METERS < asDrawnDistance) {
            geometry.reversed()
        } else {
            geometry
        }
    }

    private fun normalizedRouteGeometry(
        routePoints: List<CourseGeoPoint>,
        importedControls: List<CourseControlPoint>,
        matchedControls: List<CourseMatchedControl>
    ): List<CourseGeoPoint> {
        val oriented = orientedRoutePoints(routePoints, importedControls)
        if (oriented.isEmpty()) {
            return oriented
        }
        val explicitFinish = importedControls.firstOrNull { it.isCourseFinishPoint() }?.point
        val finish = explicitFinish ?: oriented.last()
        val routeEndingAtFinish = if (oriented.last().sameRoutePoint(finish)) {
            oriented
        } else {
            oriented + finish.copy(elevationMeters = null)
        }
        val beacon = matchedControls.firstOrNull { it.type == ControlPointType.BEACON }?.point
        if (beacon == null || routeEndingAtFinish.any { it.sameRoutePoint(beacon) }) {
            return routeEndingAtFinish
        }
        val cleanBeacon = beacon.copy(elevationMeters = null)
        return if (routeEndingAtFinish.size == 1) {
            listOf(cleanBeacon, routeEndingAtFinish.last())
        } else {
            routeEndingAtFinish.dropLast(1) + cleanBeacon + routeEndingAtFinish.last()
        }
    }

    private fun routeGeometryRejectionReason(
        route: CourseRoute,
        categoryName: String,
        orientedRoute: List<CourseGeoPoint>,
        routeLineControls: List<CourseMatchedControl>,
        matchedControls: List<CourseMatchedControl>,
        importedControls: List<CourseControlPoint>
    ): String? {
        if (orientedRoute.size < 2) {
            return "LineString has fewer than two points."
        }
        val issues = mutableListOf<String>()
        val explicitStart = importedControls.firstOrNull { it.isCourseStartPoint() }
        val explicitFinish = importedControls.firstOrNull { it.isCourseFinishPoint() }
        explicitStart?.let { start ->
            val distance = orientedRoute.first().distanceMetersTo(start.point)
            if (
                distance > CONTROL_ROUTE_TOLERANCE_METERS &&
                orientedRoute.first().isNotNearAnyMatchedControl(matchedControls)
            ) {
                issues += "start endpoint is ${distance.roundToInt()} m from ${start.name}"
            }
        }
        explicitFinish?.let { finish ->
            val distance = orientedRoute.last().distanceMetersTo(finish.point)
            if (
                distance > CONTROL_ROUTE_TOLERANCE_METERS &&
                orientedRoute.last().isNotNearAnyMatchedControl(matchedControls)
            ) {
                issues += "finish endpoint is ${distance.roundToInt()} m from ${finish.name}"
            }
        }
        if (matchedControls.any { it.type == ControlPointType.CONTROL } &&
            routeLineControls.none { it.type == ControlPointType.CONTROL }
        ) {
            issues += "LineString does not pass near any matched fox controls for $categoryName"
        }
        return issues.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "Route ${route.name}: ")
    }

    private fun CourseGeoPoint.isNotNearAnyMatchedControl(controls: List<CourseMatchedControl>): Boolean =
        controls.none { distanceMetersTo(it.point) <= CONTROL_ROUTE_TOLERANCE_METERS }

    private fun CourseGeoPoint.sameRoutePoint(other: CourseGeoPoint): Boolean =
        distanceMetersTo(other) <= ROUTE_ORIENTATION_TOLERANCE_METERS

    private fun CourseGeoPoint.isNearAnyRouteEndpoint(routes: List<CourseRoute>): Boolean =
        routes.any { route -> isNearRouteEndpoint(route.points) }

    private fun CourseGeoPoint.isNearRouteEndpoint(routePoints: List<CourseGeoPoint>): Boolean {
        val first = routePoints.firstOrNull()
        val last = routePoints.lastOrNull()
        return first?.let { distanceMetersTo(it) <= CONTROL_ROUTE_TOLERANCE_METERS } == true ||
            last?.let { distanceMetersTo(it) <= CONTROL_ROUTE_TOLERANCE_METERS } == true
    }

    private fun ProtectedCourseInfo.routeMatches(routeGeometry: List<CourseGeoPoint>): Boolean {
        if (route.isEmpty() || routeGeometry.isEmpty()) {
            return false
        }
        val storedStart = CourseGeoPoint(route.first().latitude, route.first().longitude, route.first().elevationMeters)
        val storedFinish = CourseGeoPoint(route.last().latitude, route.last().longitude, route.last().elevationMeters)
        return storedStart.distanceMetersTo(routeGeometry.first()) <= CONTROL_ROUTE_TOLERANCE_METERS &&
            storedFinish.distanceMetersTo(routeGeometry.last()) <= CONTROL_ROUTE_TOLERANCE_METERS
    }

    private fun controlsOnRoute(route: List<CourseGeoPoint>, controls: List<CourseMatchedControl>): List<CourseMatchedControl> =
        controls
            .mapNotNull { control ->
                // A route drawn through or very near a point counts as using that control. The
                // tolerance absorbs KML drawing imprecision but still excludes unrelated controls.
                val alongDistance = distanceAlongRouteOrNull(route, control.point, CONTROL_ROUTE_TOLERANCE_METERS)
                alongDistance?.let { it to control }
            }
            .sortedBy { it.first }
            .map { it.second }

    private fun courseObjectsForRoute(
        route: List<CourseGeoPoint>,
        controls: List<ProtectedCourseControlPoint>
    ): List<ProtectedCourseObjectPoint> =
        buildList {
            route.firstOrNull()?.let { start ->
                add(
                    ProtectedCourseObjectPoint(
                        id = "start",
                        label = "Start",
                        type = ProtectedCourseObjectType.START,
                        latitude = start.latitude,
                        longitude = start.longitude,
                        elevationMeters = start.elevationMeters
                    )
                )
            }
            controls.forEach { control ->
                add(
                    ProtectedCourseObjectPoint(
                        id = control.controlId,
                        label = control.label,
                        type = control.type.toProtectedCourseObjectType(),
                        latitude = control.latitude,
                        longitude = control.longitude,
                        elevationMeters = control.elevationMeters
                    )
                )
            }
            route.lastOrNull()?.let { finish ->
                add(
                    ProtectedCourseObjectPoint(
                        id = "finish",
                        label = "Finish",
                        type = ProtectedCourseObjectType.FINISH,
                        latitude = finish.latitude,
                        longitude = finish.longitude,
                        elevationMeters = finish.elevationMeters
                    )
                )
            }
        }

    private fun courseObjectsForCourseInfo(courseInfo: ProtectedCourseInfo): List<ProtectedCourseObjectPoint> =
        courseInfo.courseObjects.takeIf { it.isNotEmpty() }
            ?: courseObjectsForRoute(courseInfo.route.map { CourseGeoPoint(it.latitude, it.longitude, it.elevationMeters) }, courseInfo.controlPoints)

    private fun ControlPointType.toProtectedCourseObjectType(): ProtectedCourseObjectType =
        when (this) {
            ControlPointType.CONTROL -> ProtectedCourseObjectType.CONTROL
            ControlPointType.BEACON -> ProtectedCourseObjectType.BEACON
            ControlPointType.SEPARATOR -> ProtectedCourseObjectType.SPECTATOR
        }

    private fun distanceAlongRouteOrNull(
        route: List<CourseGeoPoint>,
        point: CourseGeoPoint,
        toleranceMeters: Double
    ): Double? {
        // Project each imported control onto the preferred route. Controls outside
        // the tolerance are omitted so unrelated map placemarks do not become part
        // of the stored ideal order.
        var distanceBeforeSegment = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestAlongDistance = 0.0
        route.zipWithNext().forEach { (start, end) ->
            val segmentLength = start.distanceMetersTo(end)
            if (segmentLength > 0.0) {
                val projected = point.projectedFractionOn(start, end)
                val interpolated = start.interpolate(end, projected)
                val distanceToSegment = point.distanceMetersTo(interpolated)
                if (distanceToSegment < bestDistance) {
                    bestDistance = distanceToSegment
                    bestAlongDistance = distanceBeforeSegment + segmentLength * projected
                }
                distanceBeforeSegment += segmentLength
            }
        }
        return bestAlongDistance.takeIf { bestDistance <= toleranceMeters }
    }

    private fun sampledRoute(points: List<CourseGeoPoint>, intervalMeters: Double): List<CourseGeoPoint> {
        if (points.size < 2) {
            return points
        }
        val sampled = mutableListOf(points.first())
        points.zipWithNext().forEach { (start, end) ->
            val distance = start.distanceMetersTo(end)
            val steps = max(1, (distance / intervalMeters).roundToInt())
            for (step in 1..steps) {
                sampled += start.interpolate(end, step.toDouble() / steps)
            }
        }
        return sampled
    }

    private fun sampledRouteAtFixedSpacing(points: List<CourseGeoPoint>, intervalMeters: Double): List<CourseGeoPoint> {
        if (points.size < 2) {
            return points
        }
        val sampled = mutableListOf(points.first())
        points.zipWithNext().forEach { (start, end) ->
            val distance = start.distanceMetersTo(end)
            if (distance <= 0.0) {
                return@forEach
            }
            var sampleDistance = intervalMeters
            while (sampleDistance < distance) {
                sampled += start.interpolate(end, sampleDistance / distance)
                sampleDistance += intervalMeters
            }
            sampled += end
        }
        return sampled
    }

    private fun routeWithMissingElevationSamples(courseInfo: ProtectedCourseInfo): List<CourseGeoPoint> {
        val existingRoute = courseInfo.route.map { routePoint ->
            CourseGeoPoint(
                latitude = routePoint.latitude,
                longitude = routePoint.longitude,
                elevationMeters = routePoint.elevationMeters
            )
        }
        if (existingRoute.all { it.elevationMeters != null }) {
            return existingRoute
        }
        val existingElevationsByLocation = existingRoute
            .filter { it.elevationMeters != null }
            .associateBy { it.locationKey() }
        return sampledRouteAtFixedSpacing(
            existingRoute.map { it.copy(elevationMeters = null) },
            USGS_3DEP_SAMPLE_METERS
        ).map { sampledPoint ->
            sampledPoint.copy(elevationMeters = existingElevationsByLocation[sampledPoint.locationKey()]?.elevationMeters)
        }
    }

    private fun missingElevationCount(courseInfo: ProtectedCourseInfo): Int =
        routeWithMissingElevationSamples(courseInfo).count { it.elevationMeters == null } +
            courseObjectsForCourseInfo(courseInfo).count { it.elevationMeters == null } +
            courseInfo.controlPoints.count { it.elevationMeters == null }

    private fun ProtectedCourseInfo.hasImportedLocationRecords(): Boolean =
        controlPoints.isNotEmpty() && courseObjects.isNotEmpty()

    private fun ProtectedCourseInfo.elevationFor(control: CourseMatchedControl): Double? {
        controlPoints.firstOrNull { it.controlId == control.controlId }?.elevationMeters?.let { return it }
        controlPoints.firstOrNull { it.label.normalizedCourseName() == control.label.normalizedCourseName() }
            ?.elevationMeters
            ?.let { return it }
        val controlLocationKey = control.point.locationKey()
        controlPoints.firstOrNull {
            CourseGeoPoint(it.latitude, it.longitude).locationKey() == controlLocationKey
        }?.elevationMeters?.let { return it }
        courseObjects.firstOrNull {
            it.id == control.controlId || it.label.normalizedCourseName() == control.label.normalizedCourseName()
        }?.elevationMeters?.let { return it }
        return courseObjects.firstOrNull {
            CourseGeoPoint(it.latitude, it.longitude).locationKey() == controlLocationKey
        }?.elevationMeters
    }

    private fun routeLengthMeters(points: List<CourseGeoPoint>): Double =
        points.zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    private fun climbMetersOrNull(points: List<CourseGeoPoint>): Int? {
        var total = 0.0
        var measuredSegmentCount = 0
        points.zipWithNext().forEach { (start, end) ->
            val gain = (end.elevationMeters ?: return@forEach) - (start.elevationMeters ?: return@forEach)
            measuredSegmentCount++
            if (gain > CLIMB_NOISE_THRESHOLD_METERS) {
                total += gain
            }
        }
        return total.roundToInt().takeIf { measuredSegmentCount > 0 }
    }

    private fun fileSha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun CourseGeoPoint.locationKey(): Pair<Int, Int> =
        (latitude * 10_000_000).roundToInt() to (longitude * 10_000_000).roundToInt()

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().also { factory ->
            factory.isNamespaceAware = true
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
        }
}

data class DesktopCourseKmlData(
    val controls: List<CourseControlPoint>,
    val routes: List<CourseRoute>
)

data class CourseControlPoint(
    val name: String,
    val point: CourseGeoPoint,
    val siCodeHint: Int? = null
)

data class CourseRoute(
    val name: String,
    val points: List<CourseGeoPoint>
)

data class CourseGeoPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null
) {
    fun distanceMetersTo(other: CourseGeoPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val deltaLat = Math.toRadians(other.latitude - latitude)
        val deltaLon = Math.toRadians(other.longitude - longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return earthRadiusMeters * 2 * asin(sqrt(a))
    }

    fun interpolate(other: CourseGeoPoint, fraction: Double): CourseGeoPoint {
        val bounded = fraction.coerceIn(0.0, 1.0)
        val interpolatedElevation = if (elevationMeters != null && other.elevationMeters != null) {
            elevationMeters + (other.elevationMeters - elevationMeters) * bounded
        } else {
            null
        }
        return CourseGeoPoint(
            latitude = latitude + (other.latitude - latitude) * bounded,
            longitude = longitude + (other.longitude - longitude) * bounded,
            elevationMeters = interpolatedElevation
        )
    }

    fun projectedFractionOn(start: CourseGeoPoint, end: CourseGeoPoint): Double {
        val meanLat = Math.toRadians((start.latitude + end.latitude) / 2.0)
        val x = (longitude - start.longitude) * cos(meanLat)
        val y = latitude - start.latitude
        val dx = (end.longitude - start.longitude) * cos(meanLat)
        val dy = end.latitude - start.latitude
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) {
            return 0.0
        }
        return ((x * dx + y * dy) / lengthSquared).coerceIn(0.0, 1.0)
    }
}

private data class CourseMatchedControl(
    val controlId: String,
    val label: String,
    val displayLabel: String,
    val siCode: Int,
    val siCodeHint: Int?,
    val type: ControlPointType,
    val point: CourseGeoPoint
)

private data class CourseMatchedControlResult(
    val controls: List<CourseMatchedControl>,
    val labelConversions: List<DesktopCourseKmlLabelConversion>
)

private data class ControlMatchToken(
    val token: String,
    val control: EventControl
)

private data class CategoryRouteElevationTarget(
    val categoryId: String,
    val categoryName: String,
    val courseInfo: ProtectedCourseInfo,
    val sampledRoute: List<CourseGeoPoint>,
    val courseObjects: List<ProtectedCourseObjectPoint>
) {
    fun missingElevationCount(): Int =
        missingRouteElevationCount() + missingCourseObjectElevationCount() + missingProtectedControlElevationCount()

    fun missingRouteElevationCount(): Int =
        sampledRoute.count { it.elevationMeters == null }

    fun missingCourseObjectElevationCount(): Int =
        courseObjects.count { it.elevationMeters == null }

    fun missingProtectedControlElevationCount(): Int =
        courseInfo.controlPoints.count { it.elevationMeters == null }

}

private fun String.shortHash(): String =
    take(12)

private fun EventControl.idealOrderToken(): String {
    val label = displayCourseLabel()
    val needsQuoting = label.any { it.isWhitespace() || it == ',' || it == ';' }
    return if (needsQuoting) "'$label'" else label
}

private fun EventControl.displayCourseLabel(): String =
    publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label

private fun CourseMatchedControl.assignedControlToken(): String =
    when (type) {
        ControlPointType.CONTROL -> siCode.toString()
        ControlPointType.SEPARATOR -> "$siCode${ControlPointRules.SPECTATOR_CONTROL_MARKER}"
        ControlPointType.BEACON -> "$siCode${ControlPointRules.BEACON_CONTROL_MARKER}"
    }

private fun String.normalizedCourseName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun String.compactCourseName(): String =
    normalizedCourseName().replace(" ", "")

private fun String.isBareSprintStartLabel(): Boolean =
    categoryMatchText() == "s"

private fun String.singleEmbeddedNumber(): Int? {
    val numbers = Regex("\\d+").findAll(this).mapNotNull { it.value.toIntOrNull() }.toList()
    return numbers.singleOrNull()
}

private fun String.controlKeywordNumber(): Int? {
    val match = Regex(
        "\\b(?:fox|transmitter|tx|control|ctrl)\\s*#?\\s*(\\d+)\\b",
        RegexOption.IGNORE_CASE
    ).find(this)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

private fun String?.siCodeHint(): Int? =
    this
        ?.lineSequence()
        ?.map { line -> line.trim() }
        ?.mapNotNull { line ->
            Regex("""^SI\s*=\s*(\d+)\s*$""", RegexOption.IGNORE_CASE)
                .matchEntire(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf(SportIdentCodes::isSICodeValid)
        }
        ?.firstOrNull()

private fun CourseControlPoint.isCourseStartPoint(): Boolean =
    name.isCourseStartName()

private fun CourseControlPoint.isCourseFinishPoint(): Boolean =
    name.isCourseFinishName()

private fun String.isCourseEndpointName(): Boolean =
    isCourseStartName() || isCourseFinishName()

private fun String.isCourseStartName(): Boolean {
    val normalized = categoryMatchText()
    return Regex("""\bstart\b""").containsMatchIn(normalized)
}

private fun String.isCourseFinishName(): Boolean {
    val normalized = categoryMatchText()
    return Regex("""\bfinish\b""").containsMatchIn(normalized)
}

private fun String.categoryMatchText(): String =
    normalizedCourseName()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun String.compactCategoryMatchText(): String =
    categoryMatchText().replace(" ", "")

private fun String.matchesCategoryRouteName(importedRouteName: String): Boolean =
    categoryMatchText() == importedRouteName.categoryMatchText() ||
        compactCategoryMatchText() == importedRouteName.compactCategoryMatchText()

private fun String.listedCategoryNames(): List<String> {
    ardfCategoryToken()?.let { return listOf(it.displayCategoryName()) }
    val parentheticalNames = parentheticalSegments()
        .flatMap { segment -> segment.split(',') }
        .map { it.trim() }
        .filter { it.isNotBlank() && it.any(Char::isLetter) }
        .map { part -> part.ardfCategoryToken()?.displayCategoryName() ?: part }
    if (parentheticalNames.isNotEmpty()) {
        return parentheticalNames.distinctBy { it.categoryMatchText() }
    }
    return ardfCategoryTokens()
        .map { it.displayCategoryName() }
        .distinctBy { it.categoryMatchText() }
}

private fun String.containsEmbeddedCategoryName(categoryName: String): Boolean {
    val categoryToken = categoryName.ardfCategoryToken() ?: return parentheticalSegments()
        .any { segment -> segment.containsCategoryName(categoryName) }
    return categoryToken in ardfCategoryTokens()
}

private fun String.ardfCategoryToken(): String? =
    Regex("^\\s*([mw])\\s*[-_ ]?\\s*(\\d{1,3})\\s*$", RegexOption.IGNORE_CASE)
        .matchEntire(this)
        ?.let { match -> "${match.groupValues[1]}${match.groupValues[2]}".lowercase() }

private fun String.ardfCategoryTokens(): Set<String> =
    Regex("(?i)(^|[^a-z0-9])([mw])\\s*[-_ ]?\\s*(\\d{1,3})(?=$|[^a-z0-9])")
        .findAll(this)
        .map { match -> "${match.groupValues[2]}${match.groupValues[3]}".lowercase() }
        .toSet()

private fun String.displayCategoryName(): String =
    take(1).uppercase() + drop(1)

private fun String.parentheticalSegments(): List<String> =
    Regex("\\(([^)]*)\\)")
        .findAll(this)
        .map { match -> match.groupValues[1] }
        .toList()

private fun String.containsCategoryName(categoryName: String): Boolean {
    val normalizedCategory = categoryName.categoryMatchText()
    val compactCategory = categoryName.compactCategoryMatchText()
    if (normalizedCategory.isBlank() || compactCategory.isBlank()) {
        return false
    }
    return Regex("(^|\\s)${Regex.escape(normalizedCategory)}(\\s|$)").containsMatchIn(this) ||
        compactCategoryMatchText().contains(compactCategory)
}

private fun org.w3c.dom.Node.childText(tagName: String): String? =
    childNodes.asSequence()
        .firstOrNull { it.localName == tagName || it.nodeName == tagName }
        ?.textContent

private fun org.w3c.dom.Node.firstDescendantText(parentTag: String, childTag: String): String? =
    descendants()
        .firstOrNull { it.localName == parentTag || it.nodeName == parentTag }
        ?.childText(childTag)

private fun org.w3c.dom.Node.namedDescendants(tagName: String): List<org.w3c.dom.Node> =
    descendants()
        .filter { it.localName == tagName || it.nodeName == tagName }
        .toList()

private fun org.w3c.dom.Node.toGpxPoint(): CourseGeoPoint? {
    val attributes = attributes ?: return null
    val latitude = attributes.getNamedItem("lat")?.nodeValue?.toDoubleOrNull() ?: return null
    val longitude = attributes.getNamedItem("lon")?.nodeValue?.toDoubleOrNull() ?: return null
    return CourseGeoPoint(
        latitude = latitude,
        longitude = longitude,
        elevationMeters = childText("ele")?.trim()?.toDoubleOrNull()
    )
}

private fun org.w3c.dom.Node.descendants(): Sequence<org.w3c.dom.Node> =
    sequence {
        childNodes.asSequence().forEach { child ->
            yield(child)
            yieldAll(child.descendants())
        }
    }

private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
    sequence {
        repeat(length) { index -> yield(item(index)) }
    }
