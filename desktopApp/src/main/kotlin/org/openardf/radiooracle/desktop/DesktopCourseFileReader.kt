package org.openardf.radiooracle.desktop

import org.openardf.radiooracle.shared.sportident.SportIdentCodes
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object DesktopCourseFileReader {
    fun read(path: Path): DesktopCourseKmlData {
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
            "${it.name.normalizedCourseFileName()}|${it.point.courseFileLocationKey()}"
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

private fun String.normalizedCourseFileName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun CourseGeoPoint.courseFileLocationKey(): Pair<Int, Int> =
    (latitude * 10_000_000).roundToInt() to (longitude * 10_000_000).roundToInt()

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
