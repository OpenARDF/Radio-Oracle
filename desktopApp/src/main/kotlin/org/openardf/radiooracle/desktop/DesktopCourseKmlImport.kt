package org.openardf.radiooracle.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.course.ControlPointRules
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventCategorySort
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseControlPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectPoint
import org.openardf.radiooracle.shared.event.ProtectedCourseObjectType
import org.openardf.radiooracle.shared.event.ProtectedCourseRoutePoint
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletionException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class DesktopCourseKmlImportSummary(
    val matchedCategoryCount: Int,
    val routeCount: Int,
    val controlPointCount: Int,
    val matchedControlPointCount: Int,
    val labelConversions: List<DesktopCourseKmlLabelConversion>,
    val matchedCategoryIds: List<String>,
    val matchedCategoryNames: List<String>,
    val routeElevationPointCount: Int,
    val importedCategoryCount: Int,
    val assignedCategoryControlCount: Int,
    val changedControlLocationCount: Int,
    val controlLocationAffectedCategoryCount: Int,
    val duplicateCategoryCount: Int,
    val duplicateMissingElevationPointCount: Int,
    val sourceSha256: String
) {
    val isDuplicateOnly: Boolean
        get() = matchedCategoryCount > 0 &&
            importedCategoryCount == 0 &&
            assignedCategoryControlCount == 0 &&
            changedControlLocationCount == 0 &&
            duplicateCategoryCount == matchedCategoryCount

    val hasDuplicateMissingElevations: Boolean
        get() = duplicateMissingElevationPointCount > 0

    val isControlLocationNoOp: Boolean
        get() = matchedCategoryCount == 0 &&
            importedCategoryCount == 0 &&
            assignedCategoryControlCount == 0 &&
            duplicateCategoryCount == 0 &&
            changedControlLocationCount == 0 &&
            matchedControlPointCount > 0

    val hasLabelConversions: Boolean
        get() = labelConversions.isNotEmpty()
}

data class DesktopCourseKmlLabelConversion(
    val importedName: String,
    val eventControlLabel: String
)

data class DesktopRouteElevationProgress(
    val completedPointCount: Int,
    val totalPointCount: Int,
    val categoryName: String
)

data class DesktopRouteElevationResult(
    val categoryCount: Int,
    val sampledPointCount: Int,
    val elevatedPointCount: Int,
    val resolvedPointCount: Int = elevatedPointCount,
    val cachedPointCount: Int = 0
)

object DesktopCourseKmlImporter {
    private const val ROUTE_SAMPLE_METERS = 25.0
    private const val USGS_3DEP_SAMPLE_METERS = 10.0
    private const val CONTROL_ROUTE_TOLERANCE_METERS = 50.0
    private const val CLIMB_NOISE_THRESHOLD_METERS = 1.0
    private val json = Json { ignoreUnknownKeys = true }

    fun importProtectedCourseInfo(
        path: Path,
        projectFile: EventProjectFile,
        password: String,
        categoryOverrideId: String? = null,
        elevationProvider: (CourseGeoPoint) -> Double? = { point -> DesktopVenueElevationCache.elevationMeters(point) }
    ): Pair<EventProjectFile, DesktopCourseKmlImportSummary> {
        val sourceSha256 = fileSha256(path)
        val courseData = parse(path)
        DesktopDebugLog.info(
            "CourseKml",
            "Import parsed ${path.fileName}: hash=${sourceSha256.shortHash()} pointPlacemarks=${courseData.controls.size} routePlacemarks=${courseData.routes.size}"
        )
        val matchedControlResult = matchedControls(courseData.controls, projectFile.raceData.controls)
        val controls = matchedControlResult.controls
        val controlsByLabel = controls.associateBy { it.label.normalizedCourseName() }
        val categories = projectFile.raceData.categories.sortedWith(EventCategorySort.byDisplayName)
        val courseInfoByCategoryId = projectFile.raceData.categories.mapNotNull { categoryData ->
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
        val controlLocationUpdates = controlLocationUpdates(
            matchedControls = controls,
            courseInfoByCategoryId = courseInfoByCategoryId
        )
        val locationUpdateResult = controlLocationUpdates.takeIf { it.isNotEmpty() }?.let { updates ->
            DesktopProtectedControlLocationUpdater.applyControlLocations(
                projectFile = projectFile,
                courseInfoByCategoryId = courseInfoByCategoryId,
                updates = updates,
                password = password,
                elevationLookup = elevationProvider,
                invalidateAllReferencedProtectedCourses = false
            )
        }
        var updatedProject = projectFile
        locationUpdateResult?.let { result ->
            updatedProject = result.projectFile
        }
        var matchedCategoryCount = 0
        var importedCategoryCount = 0
        var assignedCategoryControlCount = 0
        var duplicateCategoryCount = 0
        var duplicateMissingElevationPointCount = 0
        var routeElevationPointCount = 0
        val matchedCategoryIds = mutableListOf<String>()
        val matchedCategoryNames = mutableListOf<String>()

        courseData.routes.forEach { route ->
            val categoryData = routeCategoryTargets[route] ?: return@forEach
            matchedCategoryCount++
            matchedCategoryIds += categoryData.category.id
            matchedCategoryNames += categoryData.category.name

            updateCategoryAssignedControls(
                projectFile = updatedProject,
                categoryId = categoryData.category.id,
                controls = controls
            ).takeIf { it != updatedProject }?.let { assignedProject ->
                updatedProject = assignedProject
                assignedCategoryControlCount++
            }

            val existingCourseInfo = categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { DesktopProtectedCourseOrder.decryptCourseInfo(it, password) }
            val sameSourceCourseInfo = existingCourseInfo?.takeIf { it.sourceSha256 == sourceSha256 }
            if (sameSourceCourseInfo != null && sameSourceCourseInfo.hasImportedLocationRecords()) {
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
                        "existingCourseObjects=${sameSourceCourseInfo.courseObjects.size}"
                )
            }
            val routeGeometry = route.points.map { it.copy(elevationMeters = null) }
            val importedSampledRoute = sampledRoute(routeGeometry, ROUTE_SAMPLE_METERS).map { point ->
                val elevation = elevationProvider(point)
                if (elevation != null) {
                    routeElevationPointCount++
                }
                point.copy(elevationMeters = elevation)
            }
            val sampledRoute = sameSourceCourseInfo
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
            val idealOrder = idealOrderForRoute(sampledRoute, controlsByLabel.values.toList())
            val controlPoints = controls.map { control ->
                val elevation = sameSourceCourseInfo?.elevationFor(control) ?: elevationProvider(control.point)
                ProtectedCourseControlPoint(
                    controlId = control.controlId,
                    label = control.label,
                    latitude = control.point.latitude,
                    longitude = control.point.longitude,
                    type = control.type,
                    elevationMeters = elevation
                )
            }
            val courseObjects = courseObjectsForRoute(sampledRoute, controlPoints)
            DesktopDebugLog.info(
                "CourseKml",
                "Import matched category=${categoryData.category.name}: route=${route.name} sampledRoutePoints=${sampledRoute.size} idealOrder='${idealOrder.ifBlank { "none" }}' controls=${controlPoints.size} courseObjects=${courseObjects.size} routeElevations=${sampledRoute.count { it.elevationMeters != null }} controlElevations=${controlPoints.count { it.elevationMeters != null }}"
            )
            // Route-derived length and climb facts are competition-sensitive. Store them only in
            // the encrypted category payload; assigned controls are updated separately from the
            // matched KML/KMZ point placemarks.
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

        require(matchedCategoryCount > 0 || controls.isNotEmpty()) {
            "No KML/KMZ route names matched Event File category names, and no point placemarks matched existing controls."
        }

        val summary = DesktopCourseKmlImportSummary(
            matchedCategoryCount = matchedCategoryCount,
            routeCount = courseData.routes.size,
            controlPointCount = courseData.controls.size,
            matchedControlPointCount = controls.size,
            labelConversions = matchedControlResult.labelConversions,
            matchedCategoryIds = matchedCategoryIds,
            matchedCategoryNames = matchedCategoryNames,
            routeElevationPointCount = routeElevationPointCount,
            importedCategoryCount = importedCategoryCount,
            assignedCategoryControlCount = assignedCategoryControlCount,
            changedControlLocationCount = controlLocationUpdates.size,
            controlLocationAffectedCategoryCount = locationUpdateResult?.affectedCategoryCount ?: 0,
            duplicateCategoryCount = duplicateCategoryCount,
            duplicateMissingElevationPointCount = duplicateMissingElevationPointCount,
            sourceSha256 = sourceSha256
        )
        DesktopDebugLog.info(
            "CourseKml",
            "Import summary for ${path.fileName}: hash=${sourceSha256.shortHash()} matchedCategories=${summary.matchedCategoryCount} importedCategories=${summary.importedCategoryCount} assignedCategoryControls=${summary.assignedCategoryControlCount} changedControlLocations=${summary.changedControlLocationCount} duplicateCategories=${summary.duplicateCategoryCount} matchedControls=${summary.matchedControlPointCount}/${summary.controlPointCount} labelConversions=${summary.labelConversions.size} duplicateMissingElevationPoints=${summary.duplicateMissingElevationPointCount}"
        )
        return updatedProject to summary
    }

    private fun routeCategoryTargets(
        routes: List<CourseRoute>,
        categories: List<EventCategoryData>,
        sourceName: String,
        categoryOverrideId: String?
    ): Map<CourseRoute, EventCategoryData> {
        val targets = mutableMapOf<CourseRoute, EventCategoryData>()
        val usedCategoryIds = mutableSetOf<String>()
        routes.forEach { route ->
            val categoryData = categories.firstOrNull { categoryData ->
                categoryData.category.name.matchesCategoryRouteName(route.name)
            } ?: return@forEach
            targets[route] = categoryData
            usedCategoryIds += categoryData.category.id
        }

        val unmatchedRoutes = routes.filterNot { it in targets }
        if (unmatchedRoutes.size == 1) {
            val inferredCategory = filenameMatchedCategory(sourceName, categories)
                ?: categoryOverrideId
                    ?.let { id -> categories.firstOrNull { categoryData -> categoryData.category.id == id } }
            if (inferredCategory != null && inferredCategory.category.id !in usedCategoryIds) {
                targets[unmatchedRoutes.single()] = inferredCategory
            }
        }
        return targets
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

    suspend fun fetchProtectedCourseElevations(
        projectFile: EventProjectFile,
        categoryIds: List<String>,
        password: String,
        elevationProvider: suspend (CourseGeoPoint) -> Double? = { point ->
            withContext(Dispatchers.IO) { usgsElevationMeters(point) }
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

        suspend fun resolveElevation(point: CourseGeoPoint): Pair<Double?, Boolean> {
            localElevationProvider(point)?.let { return it to true }
            return elevationProvider(point) to false
        }

        onProgress(
            DesktopRouteElevationProgress(
                completedPointCount = 0,
                totalPointCount = totalPointCount,
                categoryName = categories.first().categoryName
            )
        )

        categories.forEach { target ->
            val elevatedRoute = target.sampledRoute.map { point ->
                if (point.elevationMeters != null) {
                    return@map point
                }
                kotlin.coroutines.coroutineContext.ensureActive()
                val (elevation, fromCache) = resolveElevation(point)
                completedPointCount++
                if (elevation != null) {
                    resolvedPointCount++
                    if (fromCache) {
                        cachedPointCount++
                    } else {
                        elevatedPointCount++
                    }
                }
                onProgress(
                    DesktopRouteElevationProgress(
                        completedPointCount = completedPointCount,
                        totalPointCount = totalPointCount,
                        categoryName = target.categoryName
                    )
                )
                point.copy(elevationMeters = elevation)
            }
            val elevatedCourseObjects = target.courseObjects.map { courseObject ->
                if (courseObject.elevationMeters != null) {
                    return@map courseObject
                }
                kotlin.coroutines.coroutineContext.ensureActive()
                val resolved = resolveElevation(
                    CourseGeoPoint(
                        latitude = courseObject.latitude,
                        longitude = courseObject.longitude,
                        elevationMeters = null
                    )
                )
                completedPointCount++
                if (resolved.first != null) {
                    resolvedPointCount++
                    if (resolved.second) {
                        cachedPointCount++
                    } else {
                        elevatedPointCount++
                    }
                }
                onProgress(
                    DesktopRouteElevationProgress(
                        completedPointCount = completedPointCount,
                        totalPointCount = totalPointCount,
                        categoryName = target.categoryName
                    )
                )
                courseObject.copy(elevationMeters = resolved.first)
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
            val elevatedControlPoints = target.courseInfo.controlPoints.map { control ->
                objectElevationByControlId[control.controlId]?.elevationMeters?.let { objectElevation ->
                    if (control.elevationMeters == null) {
                        completedPointCount++
                        resolvedPointCount++
                        onProgress(
                            DesktopRouteElevationProgress(
                                completedPointCount = completedPointCount,
                                totalPointCount = totalPointCount,
                                categoryName = target.categoryName
                            )
                        )
                    }
                    return@map control.copy(elevationMeters = objectElevation)
                }
                if (control.elevationMeters != null) {
                    return@map control
                }
                kotlin.coroutines.coroutineContext.ensureActive()
                val (elevation, fromCache) = resolveElevation(
                    CourseGeoPoint(
                        latitude = control.latitude,
                        longitude = control.longitude,
                        elevationMeters = null
                    )
                )
                completedPointCount++
                if (elevation != null) {
                    resolvedPointCount++
                    if (fromCache) {
                        cachedPointCount++
                    } else {
                        elevatedPointCount++
                    }
                }
                onProgress(
                    DesktopRouteElevationProgress(
                        completedPointCount = completedPointCount,
                        totalPointCount = totalPointCount,
                        categoryName = target.categoryName
                    )
                )
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
        val kmlText = if (path.fileName.toString().endsWith(".kmz", ignoreCase = true)) {
            readKmlFromKmz(path)
        } else {
            Files.readString(path)
        }
        return parseKml(kmlText)
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
                controls += CourseControlPoint(name = name, point = pointCoordinates)
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
            val exactMatch = controlsByToken[imported.name.normalizedCourseName()]
            val match = exactMatch
                ?: controlsByCompactToken[imported.name.compactCourseName()]
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
                    siCode = control.siCode,
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

    private fun controlLocationUpdates(
        matchedControls: List<CourseMatchedControl>,
        courseInfoByCategoryId: Map<String, ProtectedCourseInfo>
    ): List<DesktopProtectedControlLocationUpdate> {
        return matchedControls
            .distinctBy { it.controlId }
            .mapNotNull { matchedControl ->
                val protectedLocationDiffers = courseInfoByCategoryId.values.any { courseInfo ->
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

    private fun updateCategoryAssignedControls(
        projectFile: EventProjectFile,
        categoryId: String,
        controls: List<CourseMatchedControl>
    ): EventProjectFile {
        val categoryData = projectFile.raceData.categories
            .firstOrNull { it.category.id == categoryId }
            ?: return projectFile
        val raceType = categoryData.category.effectiveRaceType(projectFile.raceData.race)
        val sortedControls = controls
            .distinctBy { it.controlId }
            .sortedWith(
                compareBy<CourseMatchedControl> {
                    ControlPointRules.assignedControlSortGroup(it.siCode, it.type, raceType)
                }
                    .thenBy { it.siCode }
                    .thenBy { it.type.value }
            )
        val assignedControlsText = sortedControls
            .joinToString(" ") { it.siCode.toString() }
        if (assignedControlsText.isBlank()) {
            return projectFile
        }
        val currentControlIds = categoryData.controlPoints
            .map { it.controlId }
            .toList()
        val nextControlIds = sortedControls.map { it.controlId }
        if (currentControlIds == nextControlIds) {
            return projectFile
        }
        return EventProjectEditor.updateCategoryControlPoints(
            projectFile = projectFile,
            categoryId = categoryId,
            controlPointsText = assignedControlsText
        ) { index ->
            "$categoryId-kml-control-${index + 1}"
        }
    }

    private fun sameCoordinate(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) < 0.0000001

    private fun idealOrderForRoute(route: List<CourseGeoPoint>, controls: List<CourseMatchedControl>): String =
        controls
            .mapNotNull { control ->
                val alongDistance = distanceAlongRouteOrNull(route, control.point, CONTROL_ROUTE_TOLERANCE_METERS)
                alongDistance?.let { it to control }
            }
            .sortedBy { it.first }
            .joinToString(" ") { (_, control) -> control.label }

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
            courseObjectsForCourseInfo(courseInfo).count { it.elevationMeters == null }

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

    private suspend fun usgsElevationMeters(point: CourseGeoPoint): Double? {
        val url = "https://epqs.nationalmap.gov/v1/json?x=${point.longitude.url()}&y=${point.latitude.url()}&units=Meters&wkid=4326"
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Radio-Oracle/${DesktopBuildInfo.displayVersion}")
            .GET()
            .build()
        val response = sendRequest(
            request = request,
            responseBodyHandler = HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        )
        require(response.statusCode() in 200..299) {
            "USGS elevation service returned HTTP ${response.statusCode()}."
        }
        return json.parseToJsonElement(response.body())
            .jsonObject["value"]
            ?.jsonPrimitive
            ?.content
            ?.toDoubleOrNull()
    }

    private suspend fun <T> sendRequest(
        client: HttpClient,
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>
    ): HttpResponse<T> {
        val future = client.sendAsync(request, responseBodyHandler)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                future.cancel(true)
            }
            future.whenComplete { response, throwable ->
                if (!continuation.isActive) {
                    return@whenComplete
                }
                if (response != null) {
                    continuation.resume(response)
                    return@whenComplete
                }
                continuation.resumeWithException((throwable as? CompletionException)?.cause ?: throwable ?: IllegalStateException("Unknown network error."))
            }
        }
    }

    private fun Double.url(): String =
        URLEncoder.encode(toString(), StandardCharsets.UTF_8)

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
    val point: CourseGeoPoint
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
    val siCode: Int,
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

private fun String.normalizedCourseName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun String.compactCourseName(): String =
    normalizedCourseName().replace(" ", "")

private fun String.singleEmbeddedNumber(): Int? {
    val numbers = Regex("\\d+").findAll(this).mapNotNull { it.value.toIntOrNull() }.toList()
    return numbers.singleOrNull()
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
