package org.openardf.radiooracle.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventCategorySort
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
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
    val matchedCategoryIds: List<String>,
    val matchedCategoryNames: List<String>,
    val routeElevationPointCount: Int,
    val importedCategoryCount: Int,
    val duplicateCategoryCount: Int,
    val duplicateMissingElevationPointCount: Int,
    val sourceSha256: String
) {
    val isDuplicateOnly: Boolean
        get() = matchedCategoryCount > 0 && importedCategoryCount == 0 && duplicateCategoryCount == matchedCategoryCount

    val hasDuplicateMissingElevations: Boolean
        get() = duplicateMissingElevationPointCount > 0
}

data class DesktopRouteElevationProgress(
    val completedPointCount: Int,
    val totalPointCount: Int,
    val categoryName: String
)

data class DesktopRouteElevationResult(
    val categoryCount: Int,
    val sampledPointCount: Int,
    val elevatedPointCount: Int
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
        elevationProvider: (CourseGeoPoint) -> Double? = { null }
    ): Pair<EventProjectFile, DesktopCourseKmlImportSummary> {
        val sourceSha256 = fileSha256(path)
        val courseData = parse(path)
        val controls = matchedControls(courseData.controls, projectFile.raceData.controls)
        val controlsByLabel = controls.associateBy { it.label.normalizedCourseName() }
        val categories = projectFile.raceData.categories.sortedWith(EventCategorySort.byDisplayName)
        var updatedProject = projectFile
        var matchedCategoryCount = 0
        var importedCategoryCount = 0
        var duplicateCategoryCount = 0
        var duplicateMissingElevationPointCount = 0
        var routeElevationPointCount = 0
        val matchedCategoryIds = mutableListOf<String>()
        val matchedCategoryNames = mutableListOf<String>()

        courseData.routes.forEach { route ->
            val categoryData = categories.firstOrNull { categoryData ->
                categoryData.category.name.normalizedCourseName() == route.name.normalizedCourseName()
            } ?: return@forEach
            matchedCategoryCount++
            matchedCategoryIds += categoryData.category.id
            matchedCategoryNames += categoryData.category.name

            val existingCourseInfo = categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { DesktopProtectedCourseOrder.decryptCourseInfo(it, password) }
            if (existingCourseInfo?.sourceSha256 == sourceSha256) {
                duplicateCategoryCount++
                duplicateMissingElevationPointCount += missingElevationCount(existingCourseInfo)
                return@forEach
            }
            val routeGeometry = route.points.map { it.copy(elevationMeters = null) }
            val sampledRoute = sampledRoute(routeGeometry, ROUTE_SAMPLE_METERS).map { point ->
                val elevation = elevationProvider(point)
                if (elevation != null) {
                    routeElevationPointCount++
                }
                point.copy(elevationMeters = elevation)
            }
            val idealOrder = idealOrderForRoute(sampledRoute, controlsByLabel.values.toList())
            val controlPoints = controls.map { control ->
                val elevation = elevationProvider(control.point)
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
            // Route-derived course facts are competition-sensitive. Store them only in the
            // encrypted category payload; do not copy them into public length, climb, or
            // category-control fields that exports and result pages can read without a key.
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

        require(matchedCategoryCount > 0) {
            "No KML/KMZ route names matched Event File category names."
        }

        return updatedProject to DesktopCourseKmlImportSummary(
            matchedCategoryCount = matchedCategoryCount,
            routeCount = courseData.routes.size,
            controlPointCount = courseData.controls.size,
            matchedControlPointCount = controls.size,
            matchedCategoryIds = matchedCategoryIds,
            matchedCategoryNames = matchedCategoryNames,
            routeElevationPointCount = routeElevationPointCount,
            importedCategoryCount = importedCategoryCount,
            duplicateCategoryCount = duplicateCategoryCount,
            duplicateMissingElevationPointCount = duplicateMissingElevationPointCount,
            sourceSha256 = sourceSha256
        )
    }

    suspend fun fetchProtectedCourseElevations(
        projectFile: EventProjectFile,
        categoryIds: List<String>,
        password: String,
        elevationProvider: suspend (CourseGeoPoint) -> Double? = { point ->
            withContext(Dispatchers.IO) { usgsElevationMeters(point) }
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

        var completedPointCount = 0
        var elevatedPointCount = 0
        var updatedProject = projectFile
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
                val elevation = elevationProvider(point)
                completedPointCount++
                if (elevation != null) {
                    elevatedPointCount++
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
                val elevation = elevationProvider(
                    CourseGeoPoint(
                        latitude = courseObject.latitude,
                        longitude = courseObject.longitude,
                        elevationMeters = null
                    )
                )
                completedPointCount++
                if (elevation != null) {
                    elevatedPointCount++
                }
                onProgress(
                    DesktopRouteElevationProgress(
                        completedPointCount = completedPointCount,
                        totalPointCount = totalPointCount,
                        categoryName = target.categoryName
                    )
                )
                courseObject.copy(elevationMeters = elevation)
            }
            val objectElevationByControlId = elevatedCourseObjects
                .filter { it.type == ProtectedCourseObjectType.CONTROL || it.type == ProtectedCourseObjectType.BEACON || it.type == ProtectedCourseObjectType.SPECTATOR }
                .associateBy { it.id }
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
                controlPoints = target.courseInfo.controlPoints.map { control ->
                    objectElevationByControlId[control.controlId]?.let { objectPoint ->
                        control.copy(elevationMeters = objectPoint.elevationMeters)
                    } ?: control
                },
                courseObjects = elevatedCourseObjects
            )
            updatedProject = EventProjectEditor.updateCategoryEncryptedCourseInfo(
                updatedProject,
                target.categoryId,
                DesktopProtectedCourseOrder.encryptCourseInfo(updatedCourseInfo, password)
            )
        }

        return updatedProject to DesktopRouteElevationResult(
            categoryCount = categories.size,
            sampledPointCount = totalPointCount,
            elevatedPointCount = elevatedPointCount
        )
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
        require(routes.isNotEmpty()) {
            "KML/KMZ file did not contain named category route LineString placemarks."
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
    ): List<CourseMatchedControl> {
        // Control names from map files are user-authored, so match the visible
        // identifiers users can see in the Event File: SI code, control label,
        // and public label. Duplicate tokens are ignored to avoid guessing.
        val controlsByToken = eventControls.flatMap { control ->
            listOfNotNull(
                control.siCode.toString() to control,
                control.label.takeIf { it.isNotBlank() }?.let { it to control },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { it to control }
            )
        }
            .groupBy { (token, _) -> token.normalizedCourseName() }
            .mapNotNull { (token, matches) ->
                matches.map { (_, control) -> control.id }.distinct().singleOrNull()?.let {
                    token to matches.first().second
                }
            }
            .toMap()
        return importedControls.mapNotNull { imported ->
            controlsByToken[imported.name.normalizedCourseName()]?.let { control ->
                CourseMatchedControl(
                    controlId = control.id,
                    label = control.idealOrderToken(),
                    siCode = control.siCode,
                    type = control.type,
                    point = imported.point
                )
            }
        }
    }

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
        // of the protected ideal order.
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

    private fun usgsElevationMeters(point: CourseGeoPoint): Double? {
        val url = "https://epqs.nationalmap.gov/v1/json?x=${point.longitude.url()}&y=${point.latitude.url()}&units=Meters&wkid=4326"
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Radio-Oracle/${DesktopBuildInfo.displayVersion}")
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        require(response.statusCode() in 200..299) {
            "USGS elevation service returned HTTP ${response.statusCode()}."
        }
        return json.parseToJsonElement(response.body())
            .jsonObject["value"]
            ?.jsonPrimitive
            ?.content
            ?.toDoubleOrNull()
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

private data class CategoryRouteElevationTarget(
    val categoryId: String,
    val categoryName: String,
    val courseInfo: ProtectedCourseInfo,
    val sampledRoute: List<CourseGeoPoint>,
    val courseObjects: List<ProtectedCourseObjectPoint>
) {
    fun missingElevationCount(): Int =
        sampledRoute.count { it.elevationMeters == null } +
            courseObjects.count { it.elevationMeters == null }
}

private fun EventControl.idealOrderToken(): String {
    val label = publicLabel?.trim()?.takeIf { it.isNotEmpty() } ?: label
    val needsQuoting = label.any { it.isWhitespace() || it == ',' || it == ';' }
    return if (needsQuoting) "'$label'" else label
}

private fun String.normalizedCourseName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

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
