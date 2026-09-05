/*
 * MIT License
 *
 * Copyright (c) 2025 Pavel Kolský
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import org.openardf.radiooracle.shared.event.StandardCategoryRules
import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

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
    val deletedControlNames: List<String> = emptyList(),
    val categoryAssumptions: List<DesktopCourseKmlCategoryAssumption>,
    val rejectedRoutes: List<DesktopCourseKmlRejectedRoute> = emptyList(),
    val eventTypeWarnings: List<String>,
    val sourceSha256: String,
    val controlIdentityUpdateCount: Int = 0,
    val controlSiConflictCount: Int = 0,
    val controlPublicLabelUpdateCount: Int = 0,
    val staleCourseMappingCategoryIds: List<String> = emptyList(),
    val staleCourseMappingCategoryNames: List<String> = emptyList(),
    val duplicateRouteAssignments: List<DesktopCourseKmlDuplicateRouteAssignment> = emptyList()
) {
    val assignedCategoryControlCount: Int
        get() = categoryAssignmentUpdates.sumOf { it.controls.size }

    val isDuplicateOnly: Boolean
        get() = matchedCategoryCount > 0 &&
            importedCategoryCount == 0 &&
            assignedCategoryControlCount == 0 &&
            controlIdentityUpdateCount == 0 &&
            controlSiConflictCount == 0 &&
            controlPublicLabelUpdateCount == 0 &&
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
            controlSiConflictCount == 0 &&
            controlPublicLabelUpdateCount == 0 &&
            changedControlLocationCount == 0 &&
            matchedControlPointCount > 0

    val hasLabelConversions: Boolean
        get() = labelConversions.isNotEmpty()

    val hasControlPublicLabelUpdates: Boolean
        get() = controlPublicLabelUpdateCount > 0
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

data class DesktopCourseKmlDuplicateRouteAssignment(
    val categoryId: String,
    val categoryName: String,
    val routeChoices: List<DesktopCourseKmlDuplicateRouteChoice>,
    val selectedRouteKey: String?
)

data class DesktopCourseKmlDuplicateRouteChoice(
    val routeKey: String,
    val routeName: String,
    val routeIndex: Int
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

enum class DesktopCourseKmlSiImportPolicy {
    PreserveExisting,
    OverwriteFromImport
}

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
    private val CATEGORY_ASSUMPTION_SEQUENCE = listOf(
        "M21", "M35", "M40", "M45", "M50", "M55", "M60", "M65", "M70", "M75", "M80", "M85", "M90",
        "W21", "W35", "W40", "W45", "W50", "W55", "W60", "W65", "W70", "W75", "W80", "W85", "W90"
    )
    private const val SYNTHESIZED_ANALYZER_ROUTE_CATEGORY = "M21"
    private val json = Json { ignoreUnknownKeys = true }

    private fun activeAndInactiveCourseCategories(raceData: EventRaceData): List<EventCategoryData> {
        val activeNames = raceData.categories.mapTo(mutableSetOf()) { it.category.name.categoryMatchText() }
        return raceData.categories + raceData.courseMappings.filterNot { mapping ->
            mapping.category.name.categoryMatchText() in activeNames
        }
    }

    fun importProtectedCourseInfo(
        path: Path,
        projectFile: EventProjectFile,
        password: String?,
        categoryOverrideId: String? = null,
        elevationProvider: (CourseGeoPoint) -> Double? = { point -> DesktopVenueElevationCache.elevationMeters(point) },
        createMissingCategories: Boolean = false,
        missingCategoryNamesToCreate: Set<String>? = null,
        createMissingControls: Boolean = false,
        missingCategoryIdFactory: (String) -> String = { UUID.randomUUID().toString() },
        requireRoutes: Boolean = true,
        siImportPolicy: DesktopCourseKmlSiImportPolicy = DesktopCourseKmlSiImportPolicy.PreserveExisting,
        selectedDuplicateRouteChoices: Map<String, String> = emptyMap()
    ): Pair<EventProjectFile, DesktopCourseKmlImportSummary> {
        val storagePassword = projectFile.courseDataPassword(password)
        val sourceSha256 = fileSha256(path)
        val parsedCourseData = parse(path)
        val routeCandidateCourseData = if (requireRoutes) {
            parsedCourseData.withAnalyzerRouteCandidates(projectFile, path.fileName.toString())
        } else {
            parsedCourseData.withControlImportCourseRoutes()
        }
        val courseData = if (requireRoutes && routeCandidateCourseData.routes.isEmpty()) {
            routeCandidateCourseData.withSynthesizedAnalyzerRoute()
        } else {
            routeCandidateCourseData
        }
        val ignoredRouteCount = parsedCourseData.routes.size - routeCandidateCourseData.routes.size
        if (ignoredRouteCount > 0) {
            val importPurpose = if (requireRoutes) "Course Analyzer" else "Control import"
            DesktopDebugLog.info(
                "CourseKml",
                "$importPurpose ignored $ignoredRouteCount non-course route geometries from ${path.fileName}"
            )
        }
        DesktopDebugLog.info(
            "CourseKml",
            "Import parsed ${path.fileName}: hash=${sourceSha256.shortHash()} pointControls=${courseData.controls.size} routes=${courseData.routes.size}"
        )
        if (requireRoutes && courseData.routes.isEmpty()) {
            throw DesktopCourseKmlMissingRouteException(
                "Course Analyzer imports require at least one route LineString or GPX route/track. " +
                    "Import control-only files from Setup > Controls > Import."
            )
        }
        val missingCategoryNames = missingRouteCategoryNames(
            routes = courseData.routes,
            categories = activeAndInactiveCourseCategories(projectFile.raceData),
            sourceName = path.fileName.toString(),
            categoryOverrideId = categoryOverrideId
        )
        val selectedMissingCategoryMatchNames = missingCategoryNamesToCreate
            ?.mapTo(mutableSetOf()) { it.categoryMatchText() }
        val createdCategoryNames = when {
            createMissingCategories -> missingCategoryNames
            selectedMissingCategoryMatchNames != null ->
                missingCategoryNames.filter { it.categoryMatchText() in selectedMissingCategoryMatchNames }
            else -> emptyList()
        }
        val projectWithMissingCategories = if (createdCategoryNames.isEmpty()) {
            projectFile
        } else {
            val activeCategoryNames = projectFile.raceData.categories.mapTo(mutableSetOf()) { it.category.name.categoryMatchText() }
            val retainedCourseMappings = projectFile.raceData.courseMappings.filterNot { mapping ->
                mapping.category.name.categoryMatchText() in activeCategoryNames ||
                    createdCategoryNames.any { createdName ->
                        StandardCategoryRules.categoryNamesEquivalent(mapping.category.name, createdName)
                    }
            }
            val nextCourseMappingOrder = (
                projectFile.raceData.categories + projectFile.raceData.courseMappings
                ).maxOfOrNull { it.category.order } ?: -1
            projectFile.copy(
                raceData = projectFile.raceData.copy(
                    courseMappings = retainedCourseMappings + createdCategoryNames.mapIndexed { index, name ->
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
                                order = nextCourseMappingOrder + index + 1,
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
        val sourceName = path.fileName.toString()
        val sprintContext = isSprintCourseImport(projectWithMissingCategories, courseData, sourceName)
        val importControlLimitRaceType = importControlLimitRaceType(
            projectFile = projectWithMissingCategories,
            sourceName = sourceName,
            courseData = courseData
        )
        val endpointAwareControlsForMatching = if (sprintContext) {
            // Sprint maps may use one "S" as a route endpoint and another "S"/"Sp" as spectator;
            // keep those points route-specific so one course cannot reclassify another course's spectator.
            courseData.controls
        } else {
            endpointAwareImportedControlsForRoutes(
                routes = courseData.routes,
                importedControls = courseData.controls
            )
        }
        val importedControlsForControlMatching = controlMatchingCourseControls(
            importedControls = endpointAwareControlsForMatching,
            routes = courseData.routes,
            sprintContext = sprintContext
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
        val preliminaryMatchedControlResult = matchedControls(
            importedControlsForControlMatching,
            projectWithMissingControls.raceData.controls
        )
        val staleControlsToDelete = if (createMissingControls) {
            DesktopControlImportPruning.unmatchedControlsExceedingRaceLimits(
                projectFile = projectWithMissingControls,
                importedControlIds = preliminaryMatchedControlResult.controls.mapTo(mutableSetOf()) { it.controlId },
                raceTypeOverride = importControlLimitRaceType
            )
        } else {
            emptyList()
        }
        val projectWithPrunedControls = staleControlsToDelete.fold(projectWithMissingControls) { currentProject, control ->
            EventProjectEditor.removeControl(
                projectFile = currentProject,
                controlId = control.id,
                clearProtectedCourseData = false
            )
        }
        val matchedControlResult = matchedControls(importedControlsForControlMatching, projectWithPrunedControls.raceData.controls)
        val controlSiConflictCount = matchedControlResult.controls.count { it.hasSiCodeConflict() }
        val hintedProject = if (siImportPolicy == DesktopCourseKmlSiImportPolicy.OverwriteFromImport) {
            applyControlSiHints(projectWithPrunedControls, matchedControlResult.controls)
        } else {
            projectWithPrunedControls
        }
        val matchedControlsForHintedProject = matchedControls(importedControlsForControlMatching, hintedProject.raceData.controls)
        val labeledProject = applyControlPublicLabelHints(hintedProject, matchedControlsForHintedProject.controls)
        val controlPublicLabelUpdateCount = labeledProject.raceData.controls.count { updatedControl ->
            val beforeControl = hintedProject.raceData.controls.firstOrNull { it.id == updatedControl.id }
            beforeControl != null &&
                (beforeControl.publicLabel != updatedControl.publicLabel || beforeControl.notes != updatedControl.notes)
        }
        val matchedControlsForLabeledProject = matchedControls(importedControlsForControlMatching, labeledProject.raceData.controls)
        val controls = matchedControlsForLabeledProject.controls
        val categories = activeAndInactiveCourseCategories(labeledProject.raceData)
            .sortedWith(EventCategorySort.byDisplayName)
        val courseInfoByCategoryId = categories.mapNotNull { categoryData ->
            categoryData.category.storedCourseInfo(storagePassword)?.let { courseInfo ->
                categoryData.category.id to courseInfo
            }
        }.toMap()
        val routeCategoryTargets = routeCategoryTargets(
            routes = courseData.routes,
            categories = categories,
            sourceName = path.fileName.toString(),
            categoryOverrideId = categoryOverrideId
        )
        val hasSprintRouteTargets = routeCategoryTargets.targets.values.flatten().any { categoryData ->
            categoryData.category.effectiveRaceType(labeledProject.raceData.race) == RaceType.SPRINT
        } || isSprintCourseImport(labeledProject, courseData, sourceName)
        val controlsForLocationUpdates = if (hasSprintRouteTargets) {
            controls.filterNot { it.type == ControlPointType.SEPARATOR && it.point.isNearAnyRouteEndpoint(courseData.routes) }
        } else {
            controls
        }
        val sameSourceDuplicateCategoryIds = sameSourceDuplicateCategoryIds(
            routes = courseData.routes,
            routeCategoryTargets = routeCategoryTargets,
            eventRace = labeledProject.raceData.race,
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
                projectFile = labeledProject,
                courseInfoByCategoryId = courseInfoByCategoryId,
                updates = updates,
                password = storagePassword,
                elevationLookup = elevationProvider,
                invalidateAllReferencedProtectedCourses = false
            )
        }
        val controlsOnlyProtectedImportResult = if (courseData.routes.isEmpty() && courseInfoByCategoryId.isEmpty()) {
            importControlsOnlyProtectedCourseInfo(
                projectFile = locationUpdateResult?.projectFile ?: labeledProject,
                matchedControls = controlsForLocationUpdates,
                password = storagePassword,
                sourceName = path.fileName.toString(),
                sourceSha256 = sourceSha256,
                elevationProvider = elevationProvider
            )
        } else {
            null
        }
        val changedControlLocationCount = (
            controlLocationUpdates.map { it.controlId } +
                controlsOnlyProtectedImportResult?.updatedControlIds.orEmpty()
            )
            .distinct()
            .size
        var updatedProject = controlsOnlyProtectedImportResult?.projectFile
            ?: locationUpdateResult?.projectFile
            ?: labeledProject
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

        val routeKeysByRoute = courseData.routes.withIndex().associate { (index, route) ->
            route to route.routeSelectionKey(index)
        }
        val duplicateRouteAssignments = duplicateRouteAssignments(
            routes = courseData.routes,
            routeCategoryTargets = routeCategoryTargets,
            selectedDuplicateRouteChoices = selectedDuplicateRouteChoices
        )
        val selectedDuplicateRouteKeysByCategoryId = duplicateRouteAssignments.associate { assignment ->
            assignment.categoryId to assignment.selectedRouteKey
        }

        courseData.routes.forEach { route ->
            val routeKey = routeKeysByRoute.getValue(route)
            routeCategoryTargets.targets[route].orEmpty().forEach { categoryData ->
                selectedDuplicateRouteKeysByCategoryId[categoryData.category.id]?.let { selectedRouteKey ->
                    if (routeKey != selectedRouteKey) {
                        DesktopDebugLog.warn(
                            "CourseKml",
                            "Import skipped duplicate route assignment category=${categoryData.category.name}: route=${route.name}"
                        )
                        return@forEach
                    }
                }
                matchedCategoryCount++

                val existingCourseInfo = courseInfoByCategoryId[categoryData.category.id]
                val sameSourceCourseInfo = existingCourseInfo?.takeIf { it.sourceSha256 == sourceSha256 }
                // Build the optional public assignment update from the unsampled LineString. Sampling
                // is only for route/elevation facts; assignment matching should reflect the controls
                // intentionally placed near the imported category route.
                val raceType = categoryData.category.effectiveRaceType(updatedProject.raceData.race)
                val routeImportedControls = endpointAwareImportedControls(route.points, importedControlsForControlMatching)
                val routeMatchedControls = matchedControlsForRoute(raceType, route.points, controls)
                val orientedRouteGeometry = orientedRoutePoints(route.points, routeImportedControls)
                val routeGeometry = normalizedRouteGeometry(route.points, routeImportedControls, routeMatchedControls)
                val routeLineControls = controlsOnRoute(orientedRouteGeometry, routeMatchedControls)
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
                val routeWaypoints = waypointsOnRoute(orientedRouteGeometry, routeImportedControls, routeMatchedControls)
                val duplicateRouteControlIdValues = routeLineControls.map { it.controlId }
                val duplicateRouteWaypointLabels = routeWaypoints.map { it.label }
                val storedRouteControlIds = sameSourceCourseInfo?.controlPoints.orEmpty()
                    .map { it.controlId }
                val storedRouteWaypointLabels = sameSourceCourseInfo?.courseObjects.orEmpty()
                    .filter { it.type == ProtectedCourseObjectType.WAYPOINT }
                    .map { it.label }
                val storedRouteMatchesImported = sameSourceCourseInfo?.routeMatches(routeGeometry) == true
                if (
                    sameSourceCourseInfo != null &&
                    sameSourceCourseInfo.hasImportedLocationRecords() &&
                    storedRouteControlIds == duplicateRouteControlIdValues &&
                    storedRouteWaypointLabels == duplicateRouteWaypointLabels &&
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
                            "importRouteControls=${duplicateRouteControlIdValues.joinToString()} " +
                            "storedRouteWaypoints=${storedRouteWaypointLabels.joinToString()} " +
                            "importRouteWaypoints=${duplicateRouteWaypointLabels.joinToString()}"
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
                // Membership comes from the original LineString vertices. The sampled route adds
                // synthetic elevation points that must not make nearby course objects look visited.
                val routeControls = routeLineControls

                val idealOrder = routeControls.joinToString(" ") { it.label }
                val allProtectedControlPoints = routeMatchedControls.map { control ->
                    val elevation = sameSourceCourseInfo?.elevationFor(control) ?: elevationProvider(control.point)
                    control.controlId to ProtectedCourseControlPoint(
                        controlId = control.controlId,
                        label = control.label,
                        latitude = control.point.latitude,
                        longitude = control.point.longitude,
                        type = control.type,
                        elevationMeters = elevation,
                        speedFactor = control.speedFactorHint,
                        description = control.description
                    )
                }.toMap()
                val controlPoints = routeControls.mapNotNull { allProtectedControlPoints[it.controlId] }
                val protectedRouteWaypoints = routeWaypoints.mapIndexed { index, waypoint ->
                    ProtectedCourseObjectPoint(
                        id = "waypoint-${index + 1}-${waypoint.point.locationKey()}",
                        label = waypoint.label,
                        type = ProtectedCourseObjectType.WAYPOINT,
                        latitude = waypoint.point.latitude,
                        longitude = waypoint.point.longitude,
                        elevationMeters = sameSourceCourseInfo?.elevationForWaypoint(waypoint)
                            ?: elevationProvider(waypoint.point),
                        speedFactor = waypoint.speedFactorHint,
                        description = waypoint.description
                    )
                }
                val importedStart = routeImportedControls
                    .firstOrNull { it.isCourseStartPoint() && it.point.sameRoutePoint(routeGeometry.first()) }
                val importedFinish = routeImportedControls
                    .firstOrNull { it.isCourseFinishPoint() && it.point.sameRoutePoint(routeGeometry.last()) }
                val firstLegSpeedFactor = importedStart?.speedFactorHint ?: route.speedFactorHint
                val courseObjects = courseObjectsForRoute(
                    route = sampledRoute,
                    controls = controlPoints,
                    waypoints = protectedRouteWaypoints,
                    firstLegSpeedFactor = firstLegSpeedFactor,
                    startDescription = importedStart?.description,
                    finishDescription = importedFinish?.description
                )
                DesktopDebugLog.info(
                    "CourseKml",
                    "Import matched category=${categoryData.category.name}: route=${route.name} sampledRoutePoints=${sampledRoute.size} idealOrder='${idealOrder.ifBlank { "none" }}' routeControls=${controlPoints.size} visibleControls=${routeMatchedControls.size} courseObjects=${courseObjects.size} routeElevations=${sampledRoute.count { it.elevationMeters != null }} controlElevations=${controlPoints.count { it.elevationMeters != null }}"
                )
                // Route geometry and ideal order are competition-sensitive, but length/climb are
                // normal category facts. Calculate them from the imported geometry and elevation
                // cache rather than trusting source-file metadata.
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
                updatedProject = updatedProject.withStoredCourseInfo(
                    categoryData.category.id,
                    protectedCourseInfo,
                    storagePassword
                )
                updatedProject = updatedProject.withStoredIdealOrder(
                    categoryData.category.id,
                    idealOrder,
                    storagePassword
                )
                updatedProject = updateCategoryCalculatedCourseMetrics(
                    projectFile = updatedProject,
                    categoryId = categoryData.category.id,
                    courseInfo = protectedCourseInfo
                )
                importedCategoryCount++
            }
        }

        val importedRouteCategoryIds = matchedCategoryIds.toSet()
        val staleCourseMappingCategoryIds = locationUpdateResult?.affectedCategoryIds.orEmpty()
            .filterNot { it in importedRouteCategoryIds }
            .distinct()
        val staleCategoryNamesById = activeAndInactiveCourseCategories(updatedProject.raceData).associate { categoryData ->
            categoryData.category.id to categoryData.category.name
        }
        val staleCourseMappingCategoryNames = staleCourseMappingCategoryIds.mapNotNull(staleCategoryNamesById::get)

        require(matchedCategoryCount > 0 || controls.isNotEmpty() || missingCategoryNames.isNotEmpty()) {
            "No route names matched Race File category names, and no point controls matched existing controls."
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
            changedControlLocationCount = changedControlLocationCount,
            controlLocationAffectedCategoryCount = locationUpdateResult?.affectedCategoryCount
                ?: controlsOnlyProtectedImportResult?.affectedCategoryCount
                ?: 0,
            duplicateCategoryCount = duplicateCategoryCount,
            duplicateMissingElevationPointCount = duplicateMissingElevationPointCount,
            missingCategoryNames = missingCategoryNames,
            createdCategoryNames = createdCategoryNames,
            missingControlNames = missingControls.map { it.displayCourseLabel() },
            createdControlNames = createdControlNames,
            deletedControlNames = staleControlsToDelete.map { it.importDeletedControlDisplayName() },
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
            controlIdentityUpdateCount = if (siImportPolicy == DesktopCourseKmlSiImportPolicy.OverwriteFromImport) {
                controlSiConflictCount
            } else {
                0
            },
            controlSiConflictCount = controlSiConflictCount,
            controlPublicLabelUpdateCount = controlPublicLabelUpdateCount,
            staleCourseMappingCategoryIds = staleCourseMappingCategoryIds,
            staleCourseMappingCategoryNames = staleCourseMappingCategoryNames,
            duplicateRouteAssignments = duplicateRouteAssignments
        )
        DesktopDebugLog.info(
            "CourseKml",
            "Import summary for ${path.fileName}: hash=${sourceSha256.shortHash()} matchedCategories=${summary.matchedCategoryCount} importedCategories=${summary.importedCategoryCount} assignedCategoryControls=${summary.assignedCategoryControlCount} changedControlLocations=${summary.changedControlLocationCount} staleCourseMappings=${summary.staleCourseMappingCategoryNames.size} duplicateRouteAssignments=${summary.duplicateRouteAssignments.size} publicLabelUpdates=${summary.controlPublicLabelUpdateCount} duplicateCategories=${summary.duplicateCategoryCount} matchedControls=${summary.matchedControlPointCount}/${summary.controlPointCount} missingControls=${summary.missingControlNames.size} createdControls=${summary.createdControlNames.size} deletedControls=${summary.deletedControlNames.size} labelConversions=${summary.labelConversions.size} missingElevationPoints=${summary.missingElevationPointCount} duplicateMissingElevationPoints=${summary.duplicateMissingElevationPointCount}"
        )
        return updatedProject to summary
    }

    private fun DesktopCourseKmlData.withSynthesizedAnalyzerRoute(): DesktopCourseKmlData {
        val start = controls.firstOrNull { it.isCourseStartPoint() }
        val finish = controls.firstOrNull { it.isCourseFinishPoint() }
        val foxes = controls
            .filter { it.inferredRouteControlType() == ControlPointType.CONTROL }
            .sortedWith(synthesizedRouteControlComparator())
        val beacons = controls
            .filter { it.inferredRouteControlType() == ControlPointType.BEACON }
            .sortedWith(synthesizedRouteControlComparator())
        if (start == null || finish == null || foxes.isEmpty() || beacons.isEmpty()) {
            throw DesktopCourseKmlMissingRouteException(
                "Course Analyzer imports without route LineStrings require named Start, fox/control, Beacon, and Finish point placemarks."
            )
        }

        // When a map file supplies only course points, build the neutral M21 route the analyzer
        // needs before analysis begins: Start, all foxes in normal control order, beacon(s), Finish.
        return copy(
            routes = listOf(
                CourseRoute(
                    name = SYNTHESIZED_ANALYZER_ROUTE_CATEGORY,
                    points = listOf(start.point) + foxes.map { it.point } + beacons.map { it.point } + finish.point
                )
            )
        )
    }

    private fun CourseControlPoint.inferredRouteControlType(): ControlPointType? {
        if (isCourseStartPoint() || isCourseFinishPoint()) {
            return null
        }
        return ControlRoleLabelRules.inferredRole(name) ?: ControlPointType.CONTROL
    }

    private fun synthesizedRouteControlComparator(): Comparator<CourseControlPoint> =
        compareBy<CourseControlPoint> { control ->
            control.name.controlKeywordNumber() ?: control.name.singleEmbeddedNumber() ?: Int.MAX_VALUE
        }
            .thenBy { control ->
                control.inferredRouteControlType()?.let { type ->
                    inferredControlSiCode(control.name, type, control.siCodeHint)
                } ?: Int.MAX_VALUE
            }
            .thenBy { it.name.normalizedCourseName() }

    private fun missingRouteCategoryNames(
        routes: List<CourseRoute>,
        categories: List<EventCategoryData>,
        sourceName: String,
        categoryOverrideId: String?
    ): List<String> {
        val existingNames = categories.mapTo(mutableSetOf()) { it.category.name.categoryMatchText() }
        val listedCategoryNames = routes.flatMap { route -> route.listedCategoryNames() }
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
            .filter { it.listedCategoryNames().isEmpty() }
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

    private fun duplicateRouteAssignments(
        routes: List<CourseRoute>,
        routeCategoryTargets: RouteCategoryTargetResult,
        selectedDuplicateRouteChoices: Map<String, String>
    ): List<DesktopCourseKmlDuplicateRouteAssignment> {
        val routeKeysByRoute = routes.withIndex().associate { (index, route) ->
            route to route.routeSelectionKey(index)
        }
        val routeIndexesByRoute = routes.withIndex().associate { (index, route) ->
            route to index
        }
        val routeChoicesByCategoryId = linkedMapOf<String, Pair<String, MutableList<DesktopCourseKmlDuplicateRouteChoice>>>()
        routes.forEach { route ->
            routeCategoryTargets.targets[route].orEmpty().forEach { categoryData ->
                val choices = routeChoicesByCategoryId.getOrPut(categoryData.category.id) {
                    categoryData.category.name to mutableListOf()
                }.second
                choices += DesktopCourseKmlDuplicateRouteChoice(
                    routeKey = routeKeysByRoute.getValue(route),
                    routeName = route.name.ifBlank { "Route ${routeIndexesByRoute.getValue(route) + 1}" },
                    routeIndex = routeIndexesByRoute.getValue(route)
                )
            }
        }
        return routeChoicesByCategoryId.mapNotNull { (categoryId, categoryAndChoices) ->
            val (categoryName, choices) = categoryAndChoices
            if (choices.size <= 1) {
                null
            } else {
                val selectedRouteKey = selectedDuplicateRouteChoices[categoryId]
                    ?.takeIf { selectedKey -> choices.any { it.routeKey == selectedKey } }
                    ?: choices.first().routeKey
                DesktopCourseKmlDuplicateRouteAssignment(
                    categoryId = categoryId,
                    categoryName = categoryName,
                    routeChoices = choices,
                    selectedRouteKey = selectedRouteKey
                )
            }
        }
    }

    private fun CourseRoute.routeSelectionKey(routeIndex: Int): String =
        "${routeIndex}:${name}:${points.size}"

    private fun DesktopCourseKmlData.withAnalyzerRouteCandidates(
        projectFile: EventProjectFile,
        sourceName: String
    ): DesktopCourseKmlData {
        if (routes.isEmpty()) {
            return this
        }
        val sprintContext = isSprintCourseImport(projectFile, this, sourceName)
        val filteredRoutes = routes.filter { route ->
            route.isAnalyzerRouteCandidate(
                controls = controls,
                eventControls = projectFile.raceData.controls,
                sprintContext = sprintContext
            )
        }
        return if (filteredRoutes.size == routes.size) this else copy(routes = filteredRoutes)
    }

    private fun CourseRoute.isAnalyzerRouteCandidate(
        controls: List<CourseControlPoint>,
        eventControls: List<EventControl>,
        sprintContext: Boolean
    ): Boolean {
        if (points.size < 2) {
            return false
        }
        // Endpoint-aware classification distinguishes an abbreviated endpoint "S" from an
        // interior Sprint spectator "S" or "Sp" before applying the required-object checks.
        val routeControls = endpointAwareImportedControls(points, controls)
        val starts = routeControls.filter { it.isCourseStartPoint() }
        val finishes = routeControls.filter { it.isCourseFinishPoint() }
        val matchedRouteControls = matchedControls(routeControls, eventControls).controls
        val beacons = (
            routeControls
                .filter { ControlRoleLabelRules.inferredRole(it.name) == ControlPointType.BEACON }
                .map { it.point } +
                matchedRouteControls.filter { it.type == ControlPointType.BEACON }.map { it.point }
            ).distinctBy { it.locationKey() }

        // Older files may not carry labeled Start, Finish, or Beacon point objects. Preserve their
        // existing import path because the missing locations make a geometric candidate decision
        // impossible. When all three roles are known, however, partial course-object LineStrings
        // are never allowed to consume a category or become an analyzer route candidate.
        if (starts.isEmpty() || finishes.isEmpty() || beacons.isEmpty()) {
            return true
        }
        if (!hasStartAndFinishEndpoints(routeControls)) {
            return false
        }
        val orientedPoints = points.orientedFromStartToFinish(starts, finishes)
        if (beacons.none { beacon -> orientedPoints.intersectsCoursePoint(beacon) }) {
            return false
        }
        if (!sprintContext) {
            return true
        }
        val spectators = (
            routeControls
                .filter { ControlRoleLabelRules.inferredRole(it.name) == ControlPointType.SEPARATOR }
                .map { it.point } +
                matchedRouteControls.filter { it.type == ControlPointType.SEPARATOR }.map { it.point }
            ).distinctBy { it.locationKey() }
        val sprintPoints = sprintRouteCandidatePoints(routeControls, matchedRouteControls)
        return orientedPoints.hasSprintRouteCandidateShape(
            coursePoints = sprintPoints,
            spectatorIsUsed = spectators.isNotEmpty()
        )
    }

    private fun List<CourseGeoPoint>.orientedFromStartToFinish(
        starts: List<CourseControlPoint>,
        finishes: List<CourseControlPoint>
    ): List<CourseGeoPoint> =
        if (
            first().isNearAnyCoursePoint(finishes) &&
            last().isNearAnyCoursePoint(starts)
        ) {
            asReversed()
        } else {
            this
        }

    private fun sprintRouteCandidatePoints(
        importedControls: List<CourseControlPoint>,
        matchedControls: List<CourseMatchedControl>
    ): List<SprintRouteCandidatePoint> =
        (
            importedControls.mapNotNull { control ->
                control.name.sprintRouteCandidateRole()?.let { role ->
                    SprintRouteCandidatePoint(role, control.point)
                }
            } +
                matchedControls.mapNotNull { control ->
                    control.sprintRouteCandidateRole()?.let { role ->
                        SprintRouteCandidatePoint(role, control.point)
                    }
                }
            ).distinctBy { it.routeKey }

    private fun String.sprintRouteCandidateRole(): SprintRouteCandidateRole? =
        when {
            isCourseStartName() -> SprintRouteCandidateRole.START
            isCourseFinishName() -> SprintRouteCandidateRole.FINISH
            DesktopCoursePointLabelClassifier.sprintFastFoxNumber(this) != null -> SprintRouteCandidateRole.FAST_FOX
            DesktopCoursePointLabelClassifier.sprintSlowFoxNumber(this) != null -> SprintRouteCandidateRole.SLOW_FOX
            ControlRoleLabelRules.inferredRole(this) == ControlPointType.BEACON -> SprintRouteCandidateRole.BEACON
            ControlRoleLabelRules.inferredRole(this) == ControlPointType.SEPARATOR -> SprintRouteCandidateRole.SPECTATOR
            else -> null
        }

    private fun CourseMatchedControl.sprintRouteCandidateRole(): SprintRouteCandidateRole? {
        val labels = listOf(importedName, label, displayLabel)
        return when (type) {
            ControlPointType.BEACON -> SprintRouteCandidateRole.BEACON
            ControlPointType.SEPARATOR -> SprintRouteCandidateRole.SPECTATOR
            ControlPointType.CONTROL -> when {
                labels.any { DesktopCoursePointLabelClassifier.sprintFastFoxNumber(it) != null } ->
                    SprintRouteCandidateRole.FAST_FOX
                labels.any { DesktopCoursePointLabelClassifier.sprintSlowFoxNumber(it) != null } ->
                    SprintRouteCandidateRole.SLOW_FOX
                else -> null
            }
        }
    }

    private fun List<CourseGeoPoint>.hasSprintRouteCandidateShape(
        coursePoints: List<SprintRouteCandidatePoint>,
        spectatorIsUsed: Boolean
    ): Boolean {
        val roles = orderedSprintRouteCandidateRoles(coursePoints)
        var roleIndex = roles.indexOf(SprintRouteCandidateRole.START)
        if (roleIndex < 0) return false
        roleIndex = roles.indexOfAfter(SprintRouteCandidateRole.SLOW_FOX, roleIndex)
        if (roleIndex < 0) return false
        val transitionRole = if (spectatorIsUsed) {
            SprintRouteCandidateRole.SPECTATOR
        } else {
            SprintRouteCandidateRole.BEACON
        }
        roleIndex = roles.indexOfAfter(transitionRole, roleIndex)
        if (roleIndex < 0) return false
        roleIndex = roles.indexOfAfter(SprintRouteCandidateRole.FAST_FOX, roleIndex)
        if (roleIndex < 0) return false
        roleIndex = roles.indexOfAfter(SprintRouteCandidateRole.BEACON, roleIndex)
        if (roleIndex < 0) return false
        return roles.indexOfAfter(SprintRouteCandidateRole.FINISH, roleIndex) >= 0
    }

    private fun List<CourseGeoPoint>.orderedSprintRouteCandidateRoles(
        coursePoints: List<SprintRouteCandidatePoint>
    ): List<SprintRouteCandidateRole> {
        var distanceAlongRoute = 0.0
        val occurrences = mutableListOf<Pair<Double, SprintRouteCandidatePoint>>()
        forEachIndexed { index, endpoint ->
            coursePoints.forEach { coursePoint ->
                if (coursePoint.point.distanceMetersTo(endpoint) <= CONTROL_ROUTE_TOLERANCE_METERS) {
                    occurrences += distanceAlongRoute to coursePoint
                }
            }
            if (index < lastIndex) {
                distanceAlongRoute += endpoint.distanceMetersTo(this[index + 1])
            }
        }
        return occurrences
            .sortedBy { it.first }
            .fold(mutableListOf<SprintRouteCandidatePoint>()) { ordered, (_, point) ->
                // Neighboring vertices can both fall within tolerance of the same course point.
                // Keep one adjacent occurrence while preserving a later return to the same Beacon.
                if (ordered.lastOrNull()?.routeKey != point.routeKey) {
                    ordered += point
                }
                ordered
            }
            .map { it.role }
    }

    private fun List<SprintRouteCandidateRole>.indexOfAfter(
        role: SprintRouteCandidateRole,
        previousIndex: Int
    ): Int =
        indices.firstOrNull { index -> index > previousIndex && this[index] == role } ?: -1

    private data class SprintRouteCandidatePoint(
        val role: SprintRouteCandidateRole,
        val point: CourseGeoPoint
    ) {
        val routeKey: String = "$role|${point.locationKey()}"
    }

    private enum class SprintRouteCandidateRole {
        START,
        SLOW_FOX,
        SPECTATOR,
        FAST_FOX,
        BEACON,
        FINISH
    }

    private fun DesktopCourseKmlData.withControlImportCourseRoutes(): DesktopCourseKmlData {
        if (routes.isEmpty()) {
            return this
        }
        val filteredRoutes = routes.filter { route -> route.hasStartAndFinishEndpoints(controls) }
        return if (filteredRoutes.size == routes.size) this else copy(routes = filteredRoutes)
    }

    private fun CourseRoute.hasStartAndFinishEndpoints(controls: List<CourseControlPoint>): Boolean {
        val first = points.firstOrNull() ?: return false
        val last = points.lastOrNull() ?: return false
        val starts = controls.filter { it.isCourseStartPoint() }
        val finishes = controls.filter { it.isCourseFinishPoint() }
        return starts.isNotEmpty() &&
            finishes.isNotEmpty() &&
            (
                first.isNearAnyCoursePoint(starts) && last.isNearAnyCoursePoint(finishes) ||
                    first.isNearAnyCoursePoint(finishes) && last.isNearAnyCoursePoint(starts)
                )
    }

    private fun List<CourseGeoPoint>.intersectsCoursePoint(point: CourseGeoPoint): Boolean =
        any { endpoint -> endpoint.distanceMetersTo(point) <= CONTROL_ROUTE_TOLERANCE_METERS }

    private fun CourseGeoPoint.isNearAnyCoursePoint(controls: List<CourseControlPoint>): Boolean =
        controls.any { control -> distanceMetersTo(control.point) <= CONTROL_ROUTE_TOLERANCE_METERS }

    private fun routeMatchedCategories(
        route: CourseRoute,
        categories: List<EventCategoryData>
    ): List<EventCategoryData> {
        val nameMatches = route.name.matchedCategories(categories)
        if (nameMatches.isNotEmpty()) {
            return nameMatches
        }
        return route.description
            ?.matchedCategories(categories)
            .orEmpty()
    }

    private fun String.matchedCategories(categories: List<EventCategoryData>): List<EventCategoryData> {
        val exactCategoryData = categories.firstOrNull { categoryData ->
            categoryData.category.name.matchesCategoryRouteName(this)
        }
        return exactCategoryData?.let(::listOf)
            ?: categories.filter { categoryData ->
                containsEmbeddedCategoryName(categoryData.category.name)
            }
    }

    private fun CourseRoute.listedCategoryNames(): List<String> =
        name.listedCategoryNames().ifEmpty {
            description?.listedCategoryNames().orEmpty()
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

    private fun isSprintCourseImport(
        projectFile: EventProjectFile,
        courseData: DesktopCourseKmlData,
        sourceName: String
    ): Boolean =
        projectFile.raceData.race.raceType == RaceType.SPRINT ||
            importFormatText(sourceName, courseData).contains("sprint")

    private fun importControlLimitRaceType(
        projectFile: EventProjectFile,
        sourceName: String,
        courseData: DesktopCourseKmlData
    ): RaceType {
        val formatText = importFormatText(sourceName, courseData)
        return when {
            formatText.contains("sprint") -> RaceType.SPRINT
            formatText.contains("foxoring") || formatText.contains("fox-o") || formatText.contains("fox o") -> RaceType.FOXORING
            formatText.contains("classic") -> RaceType.CLASSIC
            formatText.contains("orienteering") -> RaceType.ORIENTEERING
            else -> projectFile.raceData.race.raceType
        }
    }

    private fun importFormatText(sourceName: String, courseData: DesktopCourseKmlData): String =
        (listOf(sourceName) + courseData.routes.map { it.name })
            .joinToString(" ")
            .lowercase()

    @Suppress("UNUSED_PARAMETER")
    private fun controlMatchingCourseControls(
        importedControls: List<CourseControlPoint>,
        routes: List<CourseRoute>,
        sprintContext: Boolean
    ): List<CourseControlPoint> {
        /*
         * Sprint maps can legitimately contain a clear endpoint start/finish plus a short spectator
         * label such as "Sp". Treat the prefix label as spectator only after endpoint evidence
         * proves that start or finish is already accounted for; otherwise a bare "S" still needs
         * the per-route endpoint check below to decide whether it is start or spectator.
         */
        if (!sprintContext || !hasClearSprintEndpointRole(importedControls, routes)) {
            return importedControls
        }
        return importedControls.map { control ->
            if (control.name.isSpectatorPrefixOnlyName()) {
                control.copy(name = "S")
            } else {
                control
            }
        }
    }

    private fun hasClearSprintEndpointRole(
        importedControls: List<CourseControlPoint>,
        routes: List<CourseRoute>
    ): Boolean {
        if (importedControls.any { it.isCourseStartPoint() || it.isCourseFinishPoint() }) {
            return true
        }
        return routes.any { route ->
            val first = route.points.firstOrNull() ?: return@any false
            val last = route.points.lastOrNull() ?: return@any false
            val firstControls = importedControls.filter { it.point.sameRoutePoint(first) }
            val lastControls = importedControls.filter { it.point.sameRoutePoint(last) }
            (firstControls.any { it.name.isEndpointStartName() } && lastControls.any { it.name.isEndpointFinishName() }) ||
                (lastControls.any { it.name.isEndpointStartName() } && firstControls.any { it.name.isEndpointFinishName() })
        }
    }

    private fun endpointAwareImportedControlsForRoutes(
        routes: List<CourseRoute>,
        importedControls: List<CourseControlPoint>
    ): List<CourseControlPoint> {
        if (routes.isEmpty()) return importedControls
        return importedControls.map { control ->
            routes.fold(control) { current, route ->
                endpointAwareImportedControls(route.points, listOf(current)).single()
            }
        }
    }

    private fun endpointAwareImportedControls(
        routePoints: List<CourseGeoPoint>,
        importedControls: List<CourseControlPoint>
    ): List<CourseControlPoint> {
        return importedControls.map { control ->
            if (!control.point.isNearRouteEndpoint(routePoints)) {
                return@map control
            }
            when {
                control.name.isEndpointStartName() -> control.copy(name = "Start")
                control.name.isEndpointFinishName() -> control.copy(name = "Finish")
                else -> control
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
        password: String?,
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
        val storagePassword = projectFile.courseDataPassword(password)
        val categoryIdSet = categoryIds.toSet()
        val categories = activeAndInactiveCourseCategories(projectFile.raceData)
            .filter { it.category.id in categoryIdSet }
            .mapNotNull { categoryData ->
                val courseInfo = categoryData.category.storedCourseInfo(storagePassword)
                    ?: return@mapNotNull null
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
            updatedProject = updatedProject.withStoredCourseInfo(
                target.categoryId,
                updatedCourseInfo,
                storagePassword
            )
            updatedProject = updateCategoryCalculatedCourseMetrics(
                projectFile = updatedProject,
                categoryId = target.categoryId,
                courseInfo = updatedCourseInfo
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

    fun parse(path: Path): DesktopCourseKmlData =
        DesktopCourseFileReader.read(path)

    private fun matchedControls(
        importedControls: List<CourseControlPoint>,
        eventControls: List<EventControl>
    ): CourseMatchedControlResult {
        // Control names from map files are user-authored, so match the visible identifiers users can
        // see in the Race File first. For explicit SI-like clues, also allow a unique SI/type match
        // so labels such as 2F can resolve to an existing control still labeled only as SI 42.
        // Duplicate tokens are ignored to avoid guessing.
        val controlTokens = eventControls.flatMap { control ->
            listOfNotNull(
                ControlMatchToken(control.siCode.toString(), control),
                control.label.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) }
            ) + control.roleAliasTokens()
        }
        val labelTokens = eventControls.flatMap { control ->
            listOfNotNull(
                control.label.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { ControlMatchToken(it, control) }
            )
        }
        val controlsByVisibleToken = uniqueControlTokensBy(labelTokens) { it.token.normalizedCourseName() }
        val controlsByCompactVisibleToken = uniqueControlTokensBy(labelTokens) { it.token.compactCourseName() }
        val controlsByToken = uniqueControlTokensBy(controlTokens) { it.token.normalizedCourseName() }
        val controlsByCompactToken = uniqueControlTokensBy(controlTokens) { it.token.compactCourseName() }
        val controlsByIdentity = uniqueControlTokensBy(
            eventControls.map { control -> ControlMatchToken(control.displayCourseLabel(), control) }
        ) { "${it.control.siCode}|${it.control.type.name}" }
        val controlsByNumber = uniqueControlTokensBy(labelTokens) { it.token.singleEmbeddedNumber()?.toString().orEmpty() }
            .filterKeys { it.isNotBlank() }
        val labelConversions = mutableListOf<DesktopCourseKmlLabelConversion>()
        val controls = importedControls.mapNotNull { imported ->
            if (imported.name.isCourseEndpointName()) {
                return@mapNotNull null
            }
            val exactMatch = controlsByVisibleToken[imported.name.normalizedCourseName()]
            val identityMatch = imported.inferredControlIdentity()?.let { identity ->
                controlsByIdentity["${identity.first}|${identity.second.name}"]
                    ?.takeUnless {
                        identity.second == ControlPointType.CONTROL &&
                            imported.name.isExplicitFoxKeywordLabel() &&
                            imported.name.hasAmbiguousControlRoleAliasMatch(eventControls)
                    }
            }
            val numberMatch = if (sprintFastFoxNumber(imported.name) == null) {
                imported.name.controlKeywordNumber()?.let { number -> controlsByNumber[number.toString()] }
                    ?: imported.name.singleEmbeddedNumber()?.let { number -> controlsByNumber[number.toString()] }
            } else {
                null
            }
            val match = exactMatch
                ?: controlsByCompactVisibleToken[imported.name.compactCourseName()]
                ?: controlsByToken[imported.name.normalizedCourseName()]
                ?: controlsByCompactToken[imported.name.compactCourseName()]
                ?: identityMatch
                ?: numberMatch
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
                    importedName = imported.name.trim(),
                    siCode = control.siCode,
                    siCodeHint = imported.siCodeHint,
                    type = control.type,
                    point = imported.point,
                    speedFactorHint = imported.speedFactorHint,
                    description = imported.description
                )
            }
        }
        return CourseMatchedControlResult(
            controls = controls,
            labelConversions = labelConversions.distinct()
        )
    }

    private fun CourseControlPoint.inferredControlIdentity(): Pair<Int, ControlPointType>? {
        val hasIdentityClue = siCodeHint?.takeIf(SportIdentCodes::isSICodeValid) != null ||
            sprintFastFoxNumber(name) != null ||
            ControlRoleLabelRules.foxNumber(name) != null ||
            name.trim().matches(Regex("""\d+"""))
        if (!hasIdentityClue) {
            return null
        }
        val type = ControlRoleLabelRules.inferredRole(name) ?: ControlPointType.CONTROL
        val siCode = inferredControlSiCode(name, type, siCodeHint) ?: return null
        return siCode to type
    }

    private fun String.hasAmbiguousControlRoleAliasMatch(eventControls: List<EventControl>): Boolean {
        val normalizedName = normalizedCourseName()
        val compactName = compactCourseName()
        val matchingControlIds = eventControls
            .filter { control -> control.type == ControlPointType.CONTROL }
            .filter { control ->
                control.roleAliasTokens().any { token ->
                    token.token.normalizedCourseName() == normalizedName ||
                        token.token.compactCourseName() == compactName
                }
            }
            .map { it.id }
            .distinct()
        return matchingControlIds.size > 1
    }

    private fun String.isExplicitFoxKeywordLabel(): Boolean =
        normalizedCourseName().contains("fox")

    private fun missingCourseControls(
        importedControls: List<CourseControlPoint>,
        eventControls: List<EventControl>,
        raceId: String
    ): List<EventControl> {
        val matchedImportedNames = matchedControls(importedControls, eventControls)
            .controls
            .mapTo(mutableSetOf()) { it.importedName.normalizedCourseName() }
        val existingControlKeys = eventControls
            .mapTo(mutableSetOf()) { it.siCode to it.type }
        val existingLabels = eventControls
            .mapTo(mutableSetOf()) { it.label.normalizedCourseName() }
        return importedControls
            .filterNot { it.name.trim().normalizedCourseName() in matchedImportedNames }
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
            publicLabel = name.trim().takeIf { it.isNotBlank() },
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
        return DesktopCoursePointLabelClassifier.sprintFastFoxNumber(name)
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

    private fun applyControlPublicLabelHints(
        projectFile: EventProjectFile,
        matchedControls: List<CourseMatchedControl>
    ): EventProjectFile {
        // Preserve organizer-authored course-file names as public labels, but do not
        // overwrite labels the Race File already owns.
        val updates = matchedControls
            .filter { matchedControl -> matchedControl.importedName.isNotBlank() }
            .distinctBy { it.controlId }
        if (updates.isEmpty()) {
            return projectFile
        }
        return updates.fold(projectFile) { currentProject, matchedControl ->
            val control = currentProject.raceData.controls.firstOrNull { it.id == matchedControl.controlId }
                ?: return@fold currentProject
            if (!control.publicLabel.isNullOrBlank()) {
                return@fold currentProject
            }
            val notes = control.notes
                ?.takeUnless { it.trim() == matchedControl.importedName.trim() }
                .orEmpty()
            EventProjectEditor.updateControl(
                projectFile = currentProject,
                controlId = control.id,
                label = control.label,
                siCode = control.siCode.toString(),
                type = control.type,
                scored = control.scored,
                publicLabel = matchedControl.importedName,
                notes = notes
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

    private fun importControlsOnlyProtectedCourseInfo(
        projectFile: EventProjectFile,
        matchedControls: List<CourseMatchedControl>,
        password: String?,
        sourceName: String,
        sourceSha256: String,
        elevationProvider: (CourseGeoPoint) -> Double?
    ): ControlsOnlyProtectedCourseInfoImportResult? {
        val matchedControlsById = matchedControls.associateBy { it.controlId }
        val matchedControlsByLegacyDefinition = matchedControls
            .groupBy { it.siCode to it.type }
            .mapValues { (_, controls) -> controls.distinctBy { it.controlId }.singleOrNull() }
        val elevationByControlId = mutableMapOf<String, Double?>()
        var updatedProject = projectFile
        val affectedCategoryIds = linkedSetOf<String>()
        val updatedControlIds = linkedSetOf<String>()
        projectFile.raceData.categories.forEach { categoryData ->
            val categoryControls = categoryData.controlsOnlyProtectedImportControls(
                matchedControlsById = matchedControlsById,
                matchedControlsByLegacyDefinition = matchedControlsByLegacyDefinition
            )
            if (categoryControls.isEmpty()) {
                return@forEach
            }
            val controlPoints = categoryControls.map { control ->
                val elevation = elevationByControlId.getOrPut(control.controlId) {
                    elevationProvider(control.point)
                }
                ProtectedCourseControlPoint(
                    controlId = control.controlId,
                    label = control.label,
                    latitude = control.point.latitude,
                    longitude = control.point.longitude,
                    type = control.type,
                    elevationMeters = elevation,
                    speedFactor = control.speedFactorHint,
                    description = control.description
                )
            }
            val protectedCourseInfo = ProtectedCourseInfo(
                idealOrder = categoryControls.joinToString(" ") { it.label },
                lengthMeters = null,
                climbMeters = null,
                sourceName = sourceName,
                sourceSha256 = sourceSha256,
                sampledPointCount = 0,
                route = emptyList(),
                controlPoints = controlPoints,
                courseObjects = emptyList()
            )
            updatedProject = updatedProject.withStoredCourseInfo(
                categoryData.category.id,
                protectedCourseInfo,
                password
            )
            affectedCategoryIds += categoryData.category.id
            categoryControls.mapTo(updatedControlIds) { it.controlId }
        }
        return affectedCategoryIds.takeIf { it.isNotEmpty() }?.let {
            ControlsOnlyProtectedCourseInfoImportResult(
                projectFile = updatedProject,
                updatedControlIds = updatedControlIds,
                affectedCategoryCount = affectedCategoryIds.size
            )
        }
    }

    private fun EventCategoryData.controlsOnlyProtectedImportControls(
        matchedControlsById: Map<String, CourseMatchedControl>,
        matchedControlsByLegacyDefinition: Map<Pair<Int, ControlPointType>, CourseMatchedControl?>
    ): List<CourseMatchedControl> {
        val controls = when {
            controlPoints.isNotEmpty() -> controlPoints.mapNotNull { controlPoint ->
                matchedControlsById[controlPoint.controlId]
                    ?: matchedControlsByLegacyDefinition[controlPoint.siCode to controlPoint.type]
            }
            publicControlIds.isNotEmpty() -> publicControlIds.mapNotNull { controlId ->
                matchedControlsById[controlId]
            }
            else -> emptyList()
        }
        return controls.distinctBy { it.controlId }
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
                val routeImportedControls = endpointAwareImportedControls(route.points, importedControls)
                val routeMatchedControls = matchedControlsForRoute(raceType, route.points, matchedControls)
                val orientedRouteGeometry = orientedRoutePoints(route.points, routeImportedControls)
                val routeGeometry = normalizedRouteGeometry(route.points, routeImportedControls, routeMatchedControls)
                val routeLineControlIds = controlsOnRoute(orientedRouteGeometry, routeMatchedControls).map { it.controlId }
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
        val activeCategoryIds = projectFile.raceData.categories.mapTo(mutableSetOf()) { it.category.id }
        return updates.filter { it.categoryId in activeCategoryIds }.fold(projectFile) { currentProject, update ->
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

    fun clearStaleCourseMappings(
        projectFile: EventProjectFile,
        categoryIds: Collection<String>
    ): EventProjectFile {
        if (categoryIds.isEmpty()) {
            return projectFile
        }
        val existingCategoryIds = activeAndInactiveCourseCategories(projectFile.raceData)
            .mapTo(mutableSetOf()) { it.category.id }
        return categoryIds
            .distinct()
            .filter { it in existingCategoryIds }
            .fold(projectFile) { currentProject, categoryId ->
                EventProjectEditor.updateCategoryIdealOrder(
                    projectFile = EventProjectEditor.updateCategoryCourseInfo(
                        currentProject,
                        categoryId,
                        courseInfo = null
                    ),
                    categoryId = categoryId,
                    idealOrder = null
                )
            }
    }

    private fun updateCategoryCalculatedCourseMetrics(
        projectFile: EventProjectFile,
        categoryId: String,
        courseInfo: ProtectedCourseInfo
    ): EventProjectFile =
        EventProjectEditor.updateCategoryPhysicalStats(
            projectFile = projectFile,
            categoryId = categoryId,
            lengthMeters = (courseInfo.lengthMeters ?: 0).toString(),
            climbMeters = (courseInfo.climbMeters ?: 0).toString()
        )

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
        val finalObjectPoint = routeEndingAtFinish.dropLast(1).lastOrNull()
        if (
            beacon == null ||
            routeEndingAtFinish.last().sameRoutePoint(beacon) ||
            finalObjectPoint?.sameRoutePoint(beacon) == true
        ) {
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
            issues += "LineString has no endpoint near any matched fox controls for $categoryName"
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

    private fun controlsOnRoute(
        route: List<CourseGeoPoint>,
        controls: List<CourseMatchedControl>
    ): List<CourseMatchedControl> {
        val requiredBeacon = controls.firstOrNull { it.type == ControlPointType.BEACON }
        val endpointControls = controls
            .filterNot { it.type == ControlPointType.BEACON }
            .mapNotNull { control ->
                // Only explicit LineString vertices identify visited course objects. A segment can
                // pass near an unrelated object without adding it to the course.
                val alongDistance = distanceAlongRouteEndpointOrNull(
                    route,
                    control.point,
                    CONTROL_ROUTE_TOLERANCE_METERS
                )
                alongDistance?.let { it to control }
            }
            .sortedBy { it.first }
            .map { it.second }
        // A specified Beacon is the one course-object exception: every route must visit it as the
        // final object before Finish, even when the imported LineString has no nearby vertex.
        return endpointControls + listOfNotNull(requiredBeacon)
    }

    private fun waypointsOnRoute(
        route: List<CourseGeoPoint>,
        importedPoints: List<CourseControlPoint>,
        matchedControls: List<CourseMatchedControl>
    ): List<CourseRouteWaypoint> =
        importedPoints
            .asSequence()
            .filterNot { it.name.isCourseEndpointName() }
            // A named point that looks like a fox, beacon, spectator, or SI-coded control should
            // stay in the missing-control review path if it did not match the Race File. Treating
            // it as a mandatory waypoint makes the analyzer appear to recognize the location while
            // silently excluding it from fox/beacon assignment and scoring logic.
            .filterNot { it.isLikelyCourseControlPoint() }
            .filterNot { importedPoint ->
                matchedControls.any { control ->
                    // A corridor or similar mandatory waypoint can legitimately sit close to a
                    // control; suppress only true duplicates, not merely nearby named points.
                    importedPoint.point.locationKey() == control.point.locationKey() ||
                        importedPoint.name.normalizedCourseName() == control.displayLabel.normalizedCourseName() ||
                        importedPoint.name.normalizedCourseName() == control.label.normalizedCourseName()
                }
            }
            .mapNotNull { importedPoint ->
                // Non-control KML points are mandatory course objects only when an explicit
                // LineString vertex visits them. Keep them out of idealOrder so they cannot become
                // scored controls or public category assignments.
                distanceAlongRouteEndpointOrNull(route, importedPoint.point, CONTROL_ROUTE_TOLERANCE_METERS)
                    ?.let { alongDistance ->
                        CourseRouteWaypoint(
                            label = importedPoint.name,
                            point = importedPoint.point,
                            alongDistanceMeters = alongDistance,
                            speedFactorHint = importedPoint.speedFactorHint,
                            description = importedPoint.description
                        )
                    }
            }
            .sortedBy { it.alongDistanceMeters }
            .distinctBy { "${it.label.normalizedCourseName()}|${it.point.locationKey()}" }
            .toList()

    private fun courseObjectsForRoute(
        route: List<CourseGeoPoint>,
        controls: List<ProtectedCourseControlPoint>,
        waypoints: List<ProtectedCourseObjectPoint> = emptyList(),
        firstLegSpeedFactor: Double? = null,
        startDescription: String? = null,
        finishDescription: String? = null
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
                        elevationMeters = start.elevationMeters,
                        speedFactor = firstLegSpeedFactor,
                        description = startDescription
                    )
                )
            }
            val routeObjects = controls.map { control ->
                ProtectedCourseObjectPoint(
                    id = control.controlId,
                    label = control.label,
                    type = control.type.toProtectedCourseObjectType(),
                    latitude = control.latitude,
                    longitude = control.longitude,
                    elevationMeters = control.elevationMeters,
                    speedFactor = control.speedFactor,
                    description = control.description
                )
            } + waypoints
            routeObjects
                .map { courseObject ->
                    val point = CourseGeoPoint(courseObject.latitude, courseObject.longitude, courseObject.elevationMeters)
                    (distanceAlongRouteOrNull(route, point, CONTROL_ROUTE_TOLERANCE_METERS) ?: Double.MAX_VALUE) to courseObject
                }
                .sortedWith(
                    compareBy<Pair<Double, ProtectedCourseObjectPoint>> {
                        if (it.second.type == ProtectedCourseObjectType.BEACON) 1 else 0
                    }.thenBy { it.first }
                )
                .map { it.second }
                .forEach(::add)
            route.lastOrNull()?.let { finish ->
                add(
                    ProtectedCourseObjectPoint(
                        id = "finish",
                        label = "Finish",
                        type = ProtectedCourseObjectType.FINISH,
                        latitude = finish.latitude,
                        longitude = finish.longitude,
                        elevationMeters = finish.elevationMeters,
                        description = finishDescription
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

    private fun distanceAlongRouteEndpointOrNull(
        route: List<CourseGeoPoint>,
        point: CourseGeoPoint,
        toleranceMeters: Double
    ): Double? {
        var distanceAlongRoute = 0.0
        var bestDistance = Double.MAX_VALUE
        var bestAlongDistance = 0.0
        route.forEachIndexed { index, endpoint ->
            val distanceToEndpoint = point.distanceMetersTo(endpoint)
            if (distanceToEndpoint < bestDistance) {
                bestDistance = distanceToEndpoint
                bestAlongDistance = distanceAlongRoute
            }
            if (index < route.lastIndex) {
                distanceAlongRoute += endpoint.distanceMetersTo(route[index + 1])
            }
        }
        return bestAlongDistance.takeIf { bestDistance <= toleranceMeters }
    }

    private fun distanceAlongRouteOrNull(
        route: List<CourseGeoPoint>,
        point: CourseGeoPoint,
        toleranceMeters: Double
    ): Double? {
        // Project a known route object onto the route to preserve its position in the stored
        // course-object sequence. Route membership is decided from explicit endpoints earlier.
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

    private fun ProtectedCourseInfo.elevationForWaypoint(waypoint: CourseRouteWaypoint): Double? =
        courseObjects.firstOrNull {
            it.type == ProtectedCourseObjectType.WAYPOINT &&
                it.label.normalizedCourseName() == waypoint.label.normalizedCourseName()
        }?.elevationMeters
            ?: courseObjects.firstOrNull {
                it.type == ProtectedCourseObjectType.WAYPOINT &&
                    CourseGeoPoint(it.latitude, it.longitude).locationKey() == waypoint.point.locationKey()
            }?.elevationMeters

    private fun routeLengthMeters(points: List<CourseGeoPoint>): Double =
        points.zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    private fun climbMetersOrNull(points: List<CourseGeoPoint>): Int? {
        return DesktopCourseRouteMetricsCalculator.climbMetersOrNull(points)?.roundToInt()
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
}

private data class CourseMatchedControl(
    val controlId: String,
    val label: String,
    val displayLabel: String,
    val importedName: String,
    val siCode: Int,
    val siCodeHint: Int?,
    val type: ControlPointType,
    val point: CourseGeoPoint,
    val speedFactorHint: Double? = null,
    val description: String? = null
)

private data class ControlsOnlyProtectedCourseInfoImportResult(
    val projectFile: EventProjectFile,
    val updatedControlIds: Set<String>,
    val affectedCategoryCount: Int
)

private fun CourseMatchedControl.hasSiCodeConflict(): Boolean =
    siCodeHint != null && siCodeHint != siCode

private data class CourseMatchedControlResult(
    val controls: List<CourseMatchedControl>,
    val labelConversions: List<DesktopCourseKmlLabelConversion>
)

private data class CourseRouteWaypoint(
    val label: String,
    val point: CourseGeoPoint,
    val alongDistanceMeters: Double,
    val speedFactorHint: Double? = null,
    val description: String? = null
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

private fun EventControl.roleAliasTokens(): List<ControlMatchToken> =
    when (type) {
        ControlPointType.SEPARATOR -> listOf("S", "Spectator", "Separator")
        ControlPointType.BEACON -> listOf("M", "B", "Beacon")
        ControlPointType.CONTROL -> foxNumber()?.let(ControlRoleLabelRules::foxAliasTokens).orEmpty()
    }.map { ControlMatchToken(it, this) }

private fun EventControl.foxNumber(): Int? =
    listOf(label, publicLabel, displayCourseLabel())
        .firstNotNullOfOrNull(ControlRoleLabelRules::foxNumber)

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

private fun CourseControlPoint.isCourseStartPoint(): Boolean =
    name.isCourseStartName()

private fun CourseControlPoint.isCourseFinishPoint(): Boolean =
    name.isCourseFinishName()

private fun CourseControlPoint.isLikelyCourseControlPoint(): Boolean =
    siCodeHint?.takeIf(SportIdentCodes::isSICodeValid) != null ||
        ControlRoleLabelRules.inferredRole(name) != null

private fun String.isCourseEndpointName(): Boolean =
    DesktopCoursePointLabelClassifier.isCourseEndpointName(this)

private fun String.isCourseStartName(): Boolean =
    DesktopCoursePointLabelClassifier.isCourseStartName(this)

private fun String.isCourseFinishName(): Boolean =
    DesktopCoursePointLabelClassifier.isCourseFinishName(this)

private fun String.isEndpointStartName(): Boolean =
    DesktopCoursePointLabelClassifier.isEndpointStartName(this)

private fun String.isEndpointFinishName(): Boolean =
    DesktopCoursePointLabelClassifier.isEndpointFinishName(this)

private fun String.isSpectatorPrefixOnlyName(): Boolean =
    DesktopCoursePointLabelClassifier.isSpectatorPrefixOnlyName(this)

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
