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
    private const val CIRCULAR_LINESTRING_MIN_POINT_COUNT = 20
    private const val CIRCULAR_LINESTRING_CLOSE_METERS = 20.0

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
        val lineStylesByUrl = document.kmlLineStylesByUrl()
        val controls = mutableListOf<CourseControlPoint>()
        val routes = mutableListOf<CourseRoute>()
        val polygons = mutableListOf<CoursePolygon>()
        repeat(placemarks.length) { index ->
            val placemark = placemarks.item(index)
            val name = placemark.childText("name")?.trim().orEmpty()
            if (name.isBlank()) {
                return@repeat
            }
            val description = placemark.childText("description")
            val displayLabel = description.printTextLabel() ?: name
            val isVisible = !placemark.isHiddenByVisibility()
            val pointCoordinates = placemark
                .firstDescendantText("Point", "coordinates")
                ?.let(::parseCoordinates)
                ?.firstOrNull()
            if (pointCoordinates != null) {
                controls += CourseControlPoint(
                    name = name,
                    point = pointCoordinates,
                    description = description,
                    displayLabel = displayLabel,
                    isVisible = isVisible,
                    siCodeHint = description.siCodeHint(),
                    speedFactorHint = if (DesktopCoursePointLabelClassifier.isEndpointFinishName(name)) {
                        null
                    } else {
                        description.speedFactorHint(name)
                    }
                )
                return@repeat
            }
            val lineCoordinates = placemark
                .firstDescendantText("LineString", "coordinates")
                ?.let(::parseCoordinates)
                .orEmpty()
            if (lineCoordinates.size >= 2 && !lineCoordinates.isLikelyCircularLineString()) {
                routes += CourseRoute(
                    name = name,
                    points = lineCoordinates,
                    speedFactorHint = description.speedFactorHint(name),
                    description = description,
                    displayLabel = displayLabel,
                    isVisible = isVisible,
                    lineStyle = lineStylesByUrl[placemark.childText("styleUrl")?.trim()]
                )
                return@repeat
            }
            val polygonCoordinates = placemark
                .firstDescendantText("LinearRing", "coordinates")
                ?.let(::parseCoordinates)
                .orEmpty()
            if (polygonCoordinates.size >= 3) {
                polygons += CoursePolygon(
                    name = name,
                    points = polygonCoordinates,
                    description = description,
                    displayLabel = displayLabel,
                    isVisible = isVisible
                )
            }
        }
        require(controls.isNotEmpty()) {
            "KML/KMZ file did not contain named control point placemarks."
        }
        return DesktopCourseKmlData(controls = controls, routes = routes, polygons = polygons)
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
            val routeDescription = route.childText("desc")
            val points = route.namedDescendants("rtept").mapNotNull { routePoint ->
                routePoint.toGpxPoint()?.also { point ->
                    routePoint.childText("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        controls += CourseControlPoint(name = name, point = point)
                    }
                }
            }
            if (points.size >= 2) {
                routes += CourseRoute(name = routeName, points = points, description = routeDescription)
            }
        }
        document.documentElement.namedDescendants("trk").forEachIndexed { index, track ->
            val trackName = track.childText("name")?.trim().orEmpty()
                .ifBlank { "Track ${index + 1}" }
            val trackDescription = track.childText("desc")
            val points = track.namedDescendants("trkpt").mapNotNull { trackPoint ->
                trackPoint.toGpxPoint()?.also { point ->
                    trackPoint.childText("name")?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                        controls += CourseControlPoint(name = name, point = point)
                    }
                }
            }
            if (points.size >= 2) {
                routes += CourseRoute(name = trackName, points = points, description = trackDescription)
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
                val longitude = fields.getOrNull(0)?.toDoubleOrNull().validLongitudeOrNull()
                val latitude = fields.getOrNull(1)?.toDoubleOrNull().validLatitudeOrNull()
                val elevation = fields.getOrNull(2)?.toDoubleOrNull().finiteCourseValueOrNull()
                if (latitude == null || longitude == null) {
                    null
                } else {
                    CourseGeoPoint(latitude = latitude, longitude = longitude, elevationMeters = elevation)
                }
            }

    private fun List<CourseGeoPoint>.isLikelyCircularLineString(): Boolean {
        if (size <= CIRCULAR_LINESTRING_MIN_POINT_COUNT) {
            return false
        }
        val closeDistanceMeters = first().distanceMetersTo(last())
        if (closeDistanceMeters > CIRCULAR_LINESTRING_CLOSE_METERS) {
            return false
        }
        val routeLengthMeters = zipWithNext().sumOf { (start, end) -> start.distanceMetersTo(end) }
        return routeLengthMeters > CIRCULAR_LINESTRING_CLOSE_METERS * 4
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
    val routes: List<CourseRoute>,
    val polygons: List<CoursePolygon> = emptyList()
)

data class CourseControlPoint(
    val name: String,
    val point: CourseGeoPoint,
    val description: String? = null,
    val displayLabel: String = name,
    val isVisible: Boolean = true,
    val siCodeHint: Int? = null,
    val speedFactorHint: Double? = null
)

data class CourseRoute(
    val name: String,
    val points: List<CourseGeoPoint>,
    val speedFactorHint: Double? = null,
    val description: String? = null,
    val displayLabel: String = name,
    val isVisible: Boolean = true,
    val lineStyle: CourseLineStyle? = null
)

data class CourseLineStyle(
    val argb: Long?,
    val widthPixels: Double?
)

data class CoursePolygon(
    val name: String,
    val points: List<CourseGeoPoint>,
    val description: String? = null,
    val displayLabel: String = name,
    val isVisible: Boolean = true
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

private fun String?.speedFactorHint(placemarkName: String): Double? {
    val text = this ?: return null
    val match = Regex("""(?i)(?:^|[\s;,])SS\s*=\s*([^\s;,<]+)""").find(text) ?: return null
    val token = match.groupValues.getOrNull(1).orEmpty()
    val value = token.toDoubleOrNull()
    require(value != null && value in 0.01..4.99) {
        "Invalid SS speed specifier for $placemarkName: '$token'. Use SS=#.## with a value from 0.01 through 4.99."
    }
    return value
}

private fun String?.printTextLabel(): String? {
    val text = this ?: return null
    return Regex("(?i)(?:^|[\\s;,<])Text\\s*=\\s*\"([^\"]+)\"")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun String.normalizedCourseFileName(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")

private fun CourseGeoPoint.courseFileLocationKey(): Pair<Int, Int> =
    (latitude * 10_000_000).roundToInt() to (longitude * 10_000_000).roundToInt()

private fun org.w3c.dom.Node.childText(tagName: String): String? =
    childNodes.asSequence()
        .firstOrNull { it.localName == tagName || it.nodeName == tagName }
        ?.textContent

private fun org.w3c.dom.Document.kmlLineStylesByUrl(): Map<String, CourseLineStyle> {
    val directStyles = mutableMapOf<String, CourseLineStyle>()
    val normalStyleUrlByMapUrl = mutableMapOf<String, String>()
    documentElement.namedDescendants("Style").forEach { style ->
        val id = style.attributeText("id") ?: return@forEach
        style.lineStyleOrNull()?.let { lineStyle ->
            directStyles["#$id"] = lineStyle
        }
    }
    documentElement.namedDescendants("StyleMap").forEach { styleMap ->
        val id = styleMap.attributeText("id") ?: return@forEach
        styleMap.namedDescendants("Pair")
            .firstOrNull { pair -> pair.childText("key")?.trim() == "normal" }
            ?.childText("styleUrl")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { styleUrl -> normalStyleUrlByMapUrl["#$id"] = styleUrl }
    }
    return buildMap {
        putAll(directStyles)
        normalStyleUrlByMapUrl.forEach { (styleMapUrl, styleUrl) ->
            directStyles[styleUrl]?.let { put(styleMapUrl, it) }
        }
    }
}

private fun org.w3c.dom.Node.lineStyleOrNull(): CourseLineStyle? {
    val lineStyle = namedDescendants("LineStyle").firstOrNull() ?: return null
    return CourseLineStyle(
        argb = lineStyle.childText("color")?.trim()?.kmlColorToArgb(),
        widthPixels = lineStyle.childText("width")?.trim()?.toDoubleOrNull()?.finiteCourseValueOrNull()
    )
}

private fun org.w3c.dom.Node.attributeText(name: String): String? =
    attributes?.getNamedItem(name)?.nodeValue?.trim()?.takeIf { it.isNotBlank() }

private fun String.kmlColorToArgb(): Long? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 8 || normalized.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) {
        return null
    }
    val alpha = normalized.substring(0, 2).toLong(16)
    val blue = normalized.substring(2, 4).toLong(16)
    val green = normalized.substring(4, 6).toLong(16)
    val red = normalized.substring(6, 8).toLong(16)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun org.w3c.dom.Node.isHiddenByVisibility(): Boolean =
    generateSequence(this) { node ->
        node.parentNode?.takeUnless { parent ->
            parent.nodeType == org.w3c.dom.Node.DOCUMENT_NODE
        }
    }.any { node ->
        node.childText("visibility")?.trim() == "0"
    }

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
    val latitude = attributes.getNamedItem("lat")?.nodeValue?.toDoubleOrNull().validLatitudeOrNull() ?: return null
    val longitude = attributes.getNamedItem("lon")?.nodeValue?.toDoubleOrNull().validLongitudeOrNull() ?: return null
    return CourseGeoPoint(
        latitude = latitude,
        longitude = longitude,
        elevationMeters = childText("ele")?.trim()?.toDoubleOrNull().finiteCourseValueOrNull()
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
