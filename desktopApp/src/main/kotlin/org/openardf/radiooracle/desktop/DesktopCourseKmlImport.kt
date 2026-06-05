package org.openardf.radiooracle.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.event.EventCategorySort
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventProjectEditor
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
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
    val sampledElevationCount: Int
)

object DesktopCourseKmlImporter {
    private const val ROUTE_SAMPLE_METERS = 25.0
    private const val CONTROL_ROUTE_TOLERANCE_METERS = 50.0
    private const val CLIMB_NOISE_THRESHOLD_METERS = 1.0
    private val json = Json { ignoreUnknownKeys = true }

    fun importProtectedCourseInfo(
        path: Path,
        projectFile: EventProjectFile,
        password: String,
        elevationProvider: (CourseGeoPoint) -> Double? = ::usgsElevationMeters
    ): Pair<EventProjectFile, DesktopCourseKmlImportSummary> {
        val courseData = parse(path)
        val controls = matchedControls(courseData.controls, projectFile.raceData.controls)
        val controlsByLabel = controls.associateBy { it.label.normalizedCourseName() }
        val categories = projectFile.raceData.categories.sortedWith(EventCategorySort.byDisplayName)
        var updatedProject = projectFile
        var matchedCategoryCount = 0
        var sampledElevationCount = 0

        courseData.routes.forEach { route ->
            val categoryData = categories.firstOrNull { categoryData ->
                categoryData.category.name.normalizedCourseName() == route.name.normalizedCourseName()
            } ?: return@forEach
            val sampledRoute = sampledRoute(route.points, ROUTE_SAMPLE_METERS).map { point ->
                val elevation = elevationProvider(point)
                if (elevation != null) {
                    sampledElevationCount++
                }
                point.copy(elevationMeters = elevation)
            }
            val idealOrder = idealOrderForRoute(sampledRoute, controlsByLabel.values.toList())
            // Route-derived course facts are competition-sensitive. Store them only in the
            // encrypted category payload; do not copy them into public length, climb, or
            // category-control fields that exports and result pages can read without a key.
            val protectedCourseInfo = ProtectedCourseInfo(
                idealOrder = idealOrder,
                lengthMeters = routeLengthMeters(sampledRoute).roundToInt(),
                climbMeters = climbMeters(sampledRoute).roundToInt(),
                sourceName = path.fileName.toString(),
                sampledPointCount = sampledRoute.size,
                route = sampledRoute.map { point ->
                    ProtectedCourseRoutePoint(
                        latitude = point.latitude,
                        longitude = point.longitude,
                        elevationMeters = point.elevationMeters
                    )
                }
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
            matchedCategoryCount++
        }

        require(matchedCategoryCount > 0) {
            "No KML/KMZ route names matched Event File category names."
        }

        return updatedProject to DesktopCourseKmlImportSummary(
            matchedCategoryCount = matchedCategoryCount,
            routeCount = courseData.routes.size,
            controlPointCount = courseData.controls.size,
            sampledElevationCount = sampledElevationCount
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
            "KML/KMZ file did not contain named preferred-route LineString placemarks."
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
        // Control names from map files are user-authored, so match against every
        // stable event token users are likely to put in KML: SI code, internal
        // token, logical label, and public label.
        val controlsByToken = eventControls.flatMap { control ->
            listOfNotNull(
                control.siCode.toString() to control,
                control.courseToken() to control,
                control.label.takeIf { it.isNotBlank() }?.let { it to control },
                control.publicLabel?.takeIf { it.isNotBlank() }?.let { it to control }
            )
        }.associate { (token, control) -> token.normalizedCourseName() to control }
        return importedControls.mapNotNull { imported ->
            controlsByToken[imported.name.normalizedCourseName()]?.let { control ->
                CourseMatchedControl(
                    label = control.courseToken(),
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

    private fun routeLengthMeters(points: List<CourseGeoPoint>): Double =
        points.zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }

    private fun climbMeters(points: List<CourseGeoPoint>): Double {
        var total = 0.0
        points.zipWithNext().forEach { (start, end) ->
            val gain = (end.elevationMeters ?: return@forEach) - (start.elevationMeters ?: return@forEach)
            if (gain > CLIMB_NOISE_THRESHOLD_METERS) {
                total += gain
            }
        }
        return total
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
        return CourseGeoPoint(
            latitude = latitude + (other.latitude - latitude) * bounded,
            longitude = longitude + (other.longitude - longitude) * bounded,
            elevationMeters = null
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
    val label: String,
    val siCode: Int,
    val type: ControlPointType,
    val point: CourseGeoPoint
)

private fun EventControl.courseToken(): String =
    when (type) {
        ControlPointType.CONTROL -> siCode.toString()
        ControlPointType.BEACON -> "${siCode}B"
        ControlPointType.SEPARATOR -> "${siCode}S"
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
