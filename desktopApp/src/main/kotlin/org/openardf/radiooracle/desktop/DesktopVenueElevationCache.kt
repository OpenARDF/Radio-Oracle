package org.openardf.radiooracle.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.ProtectedCourseInfo
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
import java.time.Instant
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class DesktopVenueElevationBoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    init {
        require(minLatitude <= maxLatitude) { "Minimum latitude must not exceed maximum latitude." }
        require(minLongitude <= maxLongitude) { "Minimum longitude must not exceed maximum longitude." }
    }

    fun expanded(bufferMeters: Double): DesktopVenueElevationBoundingBox {
        val clampedBuffer = max(0.0, bufferMeters)
        val latitudeBuffer = clampedBuffer / METERS_PER_DEGREE_LATITUDE
        val meanLatitude = (minLatitude + maxLatitude) / 2.0
        val longitudeMeters = metersPerDegreeLongitude(meanLatitude).coerceAtLeast(1.0)
        val longitudeBuffer = clampedBuffer / longitudeMeters
        return DesktopVenueElevationBoundingBox(
            minLatitude = minLatitude - latitudeBuffer,
            maxLatitude = maxLatitude + latitudeBuffer,
            minLongitude = minLongitude - longitudeBuffer,
            maxLongitude = maxLongitude + longitudeBuffer
        )
    }

    fun contains(point: CourseGeoPoint): Boolean =
        point.latitude in minLatitude..maxLatitude && point.longitude in minLongitude..maxLongitude

    fun widthMeters(): Double =
        (maxLongitude - minLongitude) * metersPerDegreeLongitude((minLatitude + maxLatitude) / 2.0)

    fun heightMeters(): Double =
        (maxLatitude - minLatitude) * METERS_PER_DEGREE_LATITUDE
}

data class DesktopVenueElevationCacheEstimate(
    val boundingBox: DesktopVenueElevationBoundingBox,
    val resolutionMeters: Double,
    val rowCount: Int,
    val columnCount: Int,
    val pointCount: Int,
    val rawBytes: Long
)

data class DesktopVenueElevationCacheProgress(
    val venueName: String,
    val completedPointCount: Int,
    val totalPointCount: Int
)

data class DesktopVenueElevationCacheSummary(
    val venueName: String,
    val path: Path,
    val rowCount: Int,
    val columnCount: Int,
    val pointCount: Int,
    val resolvedPointCount: Int,
    val sourceName: String,
    val resolutionMeters: Double,
    val fileSha256: String
)

data class DesktopVenueElevationCacheListing(
    val venueName: String,
    val path: Path,
    val sourceName: String,
    val resolutionMeters: Double,
    val rowCount: Int,
    val columnCount: Int,
    val resolvedPointCount: Int,
    val createdAtIso: String,
    val boundingBox: DesktopVenueElevationBoundingBox
)

object DesktopVenueElevationCache {
    private const val CACHE_VERSION = 1
    private const val DEFAULT_SOURCE_NAME = "USGS 3DEP"
    private const val USGS_SAMPLE_BATCH_SIZE = 250
    private const val USGS_SAMPLE_RETRY_COUNT = 4
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    @Volatile
    private var loadedSignature: String = ""

    @Volatile
    private var loadedCaches: List<DesktopVenueElevationCacheFile> = emptyList()

    fun cacheDirectory(): Path =
        Path.of(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "Radio-Oracle",
            "elevations"
        )

    fun estimate(
        boundingBox: DesktopVenueElevationBoundingBox,
        resolutionMeters: Double,
        bufferMeters: Double
    ): DesktopVenueElevationCacheEstimate {
        require(resolutionMeters > 0.0) { "Resolution must be greater than zero." }
        val expanded = boundingBox.expanded(bufferMeters)
        val columns = gridCount(expanded.widthMeters(), resolutionMeters)
        val rows = gridCount(expanded.heightMeters(), resolutionMeters)
        val pointCount = rows * columns
        return DesktopVenueElevationCacheEstimate(
            boundingBox = expanded,
            resolutionMeters = resolutionMeters,
            rowCount = rows,
            columnCount = columns,
            pointCount = pointCount,
            rawBytes = pointCount.toLong() * java.lang.Double.BYTES
        )
    }

    fun suggestedBoundingBox(
        projectFile: EventProjectFile,
        password: String
    ): DesktopVenueElevationBoundingBox? {
        val points = projectFile.raceData.categories.flatMap { categoryData ->
            val courseInfo = categoryData.category.encryptedCourseInfo
                ?.takeIf { it.isNotBlank() }
                ?.let { encrypted -> runCatching { DesktopProtectedCourseOrder.decryptCourseInfo(encrypted, password) }.getOrNull() }
                ?: return@flatMap emptyList()
            courseInfo.allGeoPoints()
        }
        return points.boundingBoxOrNull()
    }

    fun listings(): List<DesktopVenueElevationCacheListing> =
        loadCaches().map { cache ->
            DesktopVenueElevationCacheListing(
                venueName = cache.metadata.venueName,
                path = cache.path,
                sourceName = cache.metadata.sourceName,
                resolutionMeters = cache.metadata.resolutionMeters,
                rowCount = cache.metadata.rowCount,
                columnCount = cache.metadata.columnCount,
                resolvedPointCount = cache.elevations.count { it != null },
                createdAtIso = cache.metadata.createdAtIso,
                boundingBox = cache.metadata.boundingBox.toPublic()
            )
        }.sortedWith(compareBy<DesktopVenueElevationCacheListing> { it.venueName.lowercase() }.thenBy { it.resolutionMeters })

    fun elevationMeters(point: CourseGeoPoint): Double? =
        loadCaches()
            .filter { it.metadata.boundingBox.toPublic().contains(point) }
            .sortedBy { it.metadata.resolutionMeters }
            .firstNotNullOfOrNull { it.elevationMeters(point) }

    suspend fun download(
        venueName: String,
        boundingBox: DesktopVenueElevationBoundingBox,
        resolutionMeters: Double,
        bufferMeters: Double,
        onProgress: (DesktopVenueElevationCacheProgress) -> Unit = {}
    ): DesktopVenueElevationCacheSummary =
        withContext(Dispatchers.IO) {
            val cleanVenueName = venueName.trim().ifBlank { "Venue" }
            val estimate = estimate(boundingBox, resolutionMeters, bufferMeters)
            val points = estimate.gridPoints()
            onProgress(
                DesktopVenueElevationCacheProgress(
                    venueName = cleanVenueName,
                    completedPointCount = 0,
                    totalPointCount = points.size
                )
            )
            val elevations = MutableList<Double?>(points.size) { null }
            var completed = 0
            points.chunked(USGS_SAMPLE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                coroutineContext.ensureActive()
                val values = usgs3DepSamplesWithRetry(chunk, chunkIndex + 1)
                values.forEachIndexed { index, value ->
                    elevations[chunkIndex * USGS_SAMPLE_BATCH_SIZE + index] = value
                }
                completed += chunk.size
                onProgress(
                    DesktopVenueElevationCacheProgress(
                        venueName = cleanVenueName,
                        completedPointCount = completed,
                        totalPointCount = points.size
                    )
                )
            }
            val file = DesktopVenueElevationCacheFile(
                metadata = DesktopVenueElevationCacheMetadata(
                    version = CACHE_VERSION,
                    venueName = cleanVenueName,
                    sourceName = DEFAULT_SOURCE_NAME,
                    sourceUrl = USGS_GET_SAMPLES_URL,
                    resolutionMeters = resolutionMeters,
                    rowCount = estimate.rowCount,
                    columnCount = estimate.columnCount,
                    boundingBox = estimate.boundingBox.toSerializable(),
                    createdAtIso = Instant.now().toString()
                ),
                elevations = elevations
            )
            val directory = cacheDirectory()
            Files.createDirectories(directory)
            val path = uniqueCachePath(directory, cleanVenueName, resolutionMeters)
            Files.writeString(path, file.toJsonString())
            loadedSignature = ""
            val hash = fileSha256(path)
            DesktopDebugLog.info(
                "ElevationCache",
                "Downloaded venue=$cleanVenueName source=$DEFAULT_SOURCE_NAME resolution=${resolutionMeters}m " +
                    "points=${points.size} resolved=${elevations.count { it != null }} file=${path.fileName} hash=${hash.take(12)}"
            )
            DesktopVenueElevationCacheSummary(
                venueName = cleanVenueName,
                path = path,
                rowCount = estimate.rowCount,
                columnCount = estimate.columnCount,
                pointCount = points.size,
                resolvedPointCount = elevations.count { it != null },
                sourceName = DEFAULT_SOURCE_NAME,
                resolutionMeters = resolutionMeters,
                fileSha256 = hash
            )
        }

    private fun loadCaches(): List<DesktopVenueElevationCacheFile> {
        val directory = cacheDirectory()
        if (!Files.isDirectory(directory)) {
            loadedSignature = ""
            loadedCaches = emptyList()
            return emptyList()
        }
        val files = Files.list(directory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".roelev.json") }
                .sorted()
                .toList()
        }
        val signature = files.joinToString("|") { file ->
            val attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes::class.java)
            "${file.toAbsolutePath()}:${attributes.size()}:${attributes.lastModifiedTime().toMillis()}"
        }
        if (signature == loadedSignature) {
            return loadedCaches
        }
        val caches = files.mapNotNull { path ->
            runCatching {
                parseCacheFile(Files.readString(path), path)
            }.getOrElse { error ->
                DesktopDebugLog.warn("ElevationCache", "Could not read ${path.fileName}: ${error.message ?: error::class.simpleName}")
                null
            }
        }
        loadedSignature = signature
        loadedCaches = caches
        return caches
    }

    private fun DesktopVenueElevationCacheEstimate.gridPoints(): List<CourseGeoPoint> =
        buildList(pointCount) {
            repeat(rowCount) { row ->
                val latitude = if (rowCount <= 1) {
                    boundingBox.minLatitude
                } else {
                    boundingBox.minLatitude + (boundingBox.maxLatitude - boundingBox.minLatitude) * row.toDouble() / (rowCount - 1).toDouble()
                }
                repeat(columnCount) { column ->
                    val longitude = if (columnCount <= 1) {
                        boundingBox.minLongitude
                    } else {
                        boundingBox.minLongitude + (boundingBox.maxLongitude - boundingBox.minLongitude) * column.toDouble() / (columnCount - 1).toDouble()
                    }
                    add(CourseGeoPoint(latitude = latitude, longitude = longitude))
                }
            }
        }

    private fun DesktopVenueElevationCacheFile.elevationMeters(point: CourseGeoPoint): Double? {
        val bbox = metadata.boundingBox.toPublic()
        if (!bbox.contains(point) || metadata.rowCount <= 0 || metadata.columnCount <= 0) {
            return null
        }
        val rowPosition = if (metadata.rowCount <= 1) {
            0.0
        } else {
            (point.latitude - bbox.minLatitude) / (bbox.maxLatitude - bbox.minLatitude) * (metadata.rowCount - 1)
        }.coerceIn(0.0, (metadata.rowCount - 1).toDouble())
        val columnPosition = if (metadata.columnCount <= 1) {
            0.0
        } else {
            (point.longitude - bbox.minLongitude) / (bbox.maxLongitude - bbox.minLongitude) * (metadata.columnCount - 1)
        }.coerceIn(0.0, (metadata.columnCount - 1).toDouble())
        val row0 = floor(rowPosition).toInt()
        val row1 = ceil(rowPosition).toInt().coerceAtMost(metadata.rowCount - 1)
        val column0 = floor(columnPosition).toInt()
        val column1 = ceil(columnPosition).toInt().coerceAtMost(metadata.columnCount - 1)
        val samples = listOfNotNull(
            elevationAt(row0, column0),
            elevationAt(row0, column1),
            elevationAt(row1, column0),
            elevationAt(row1, column1)
        )
        if (samples.isEmpty()) {
            return null
        }
        return samples.average()
    }

    private fun DesktopVenueElevationCacheFile.elevationAt(row: Int, column: Int): Double? =
        elevations.getOrNull(row * metadata.columnCount + column)

    private fun ProtectedCourseInfo.allGeoPoints(): List<CourseGeoPoint> =
        route.map { CourseGeoPoint(it.latitude, it.longitude) } +
            controlPoints.map { CourseGeoPoint(it.latitude, it.longitude) } +
            courseObjects.map { CourseGeoPoint(it.latitude, it.longitude) }

    private fun List<CourseGeoPoint>.boundingBoxOrNull(): DesktopVenueElevationBoundingBox? =
        takeIf { it.isNotEmpty() }?.let { points ->
            DesktopVenueElevationBoundingBox(
                minLatitude = points.minOf { it.latitude },
                maxLatitude = points.maxOf { it.latitude },
                minLongitude = points.minOf { it.longitude },
                maxLongitude = points.maxOf { it.longitude }
            )
        }

    private fun gridCount(distanceMeters: Double, resolutionMeters: Double): Int =
        (ceil(max(0.0, distanceMeters) / resolutionMeters).toInt() + 1).coerceAtLeast(2)

    private fun DesktopVenueElevationBoundingBox.toSerializable(): DesktopVenueElevationBoundingBoxValue =
        DesktopVenueElevationBoundingBoxValue(
            minLatitude = minLatitude,
            maxLatitude = maxLatitude,
            minLongitude = minLongitude,
            maxLongitude = maxLongitude
        )

    private fun DesktopVenueElevationBoundingBoxValue.toPublic(): DesktopVenueElevationBoundingBox =
        DesktopVenueElevationBoundingBox(
            minLatitude = minLatitude,
            maxLatitude = maxLatitude,
            minLongitude = minLongitude,
            maxLongitude = maxLongitude
        )

    private fun uniqueCachePath(directory: Path, venueName: String, resolutionMeters: Double): Path {
        val slug = venueName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "venue" }
        val resolutionText = resolutionMeters.roundToInt().coerceAtLeast(1).toString()
        val timestamp = Instant.now().toString().replace(Regex("[^0-9TZ]"), "")
        return directory.resolve("$slug-${resolutionText}m-$timestamp.roelev.json")
    }

    private fun usgs3DepSamplesWithRetry(points: List<CourseGeoPoint>, batchNumber: Int): List<Double?> {
        var lastError: Throwable? = null
        repeat(USGS_SAMPLE_RETRY_COUNT) { attemptIndex ->
            try {
                return usgs3DepSamples(points)
            } catch (error: Throwable) {
                lastError = error
                val attempt = attemptIndex + 1
                if (!error.isRetryableElevationServiceError() || attempt == USGS_SAMPLE_RETRY_COUNT) {
                    DesktopDebugLog.warn(
                        "ElevationCache",
                        "USGS sample batch failed batch=$batchNumber attempt=$attempt points=${points.size}: ${error.message ?: error::class.simpleName}"
                    )
                    throw error
                }
                DesktopDebugLog.warn(
                    "ElevationCache",
                    "USGS sample batch retry batch=$batchNumber attempt=$attempt points=${points.size}: ${error.message ?: error::class.simpleName}"
                )
                Thread.sleep(1_000L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("USGS 3DEP sample request failed.")
    }

    private fun Throwable.isRetryableElevationServiceError(): Boolean =
        message?.contains("HTTP 502") == true ||
            message?.contains("HTTP 503") == true ||
            message?.contains("HTTP 504") == true ||
            message?.contains("timed out", ignoreCase = true) == true

    private fun usgs3DepSamples(points: List<CourseGeoPoint>): List<Double?> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val geometry = buildString {
            append("""{"points":[""")
            points.forEachIndexed { index, point ->
                if (index > 0) append(',')
                append('[')
                append(point.longitude)
                append(',')
                append(point.latitude)
                append(']')
            }
            append("""],"spatialReference":{"wkid":4326}}""")
        }
        val body = listOf(
            "f" to "json",
            "geometryType" to "esriGeometryMultipoint",
            "geometry" to geometry,
            "returnGeometry" to "false"
        ).joinToString("&") { (key, value) ->
            "${key.url()}=${value.url()}"
        }
        val request = HttpRequest.newBuilder(URI.create(USGS_GET_SAMPLES_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Radio-Oracle Desktop")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(45))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "USGS 3DEP sample service returned HTTP ${response.statusCode()}."
        }
        val values = MutableList<Double?>(points.size) { null }
        val root = json.parseToJsonElement(response.body()).jsonObject
        root["error"]?.let { error ->
            throw IllegalStateException(error.toString())
        }
        root["samples"]?.jsonArray?.forEach { sample ->
            val sampleObject = sample.jsonObject
            val locationId = sampleObject["locationId"]?.jsonPrimitive?.content?.toIntOrNull()
            val value = sampleObject["value"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: sampleObject["value"]?.jsonPrimitive?.doubleOrNull
            if (locationId != null && locationId in values.indices) {
                values[locationId] = value
            }
        }
        return values
    }

    private fun String.url(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private fun fileSha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun DesktopVenueElevationCacheFile.toJsonString(): String =
        buildJsonObject {
            put(
                "metadata",
                buildJsonObject {
                    put("version", metadata.version)
                    put("venueName", metadata.venueName)
                    put("sourceName", metadata.sourceName)
                    put("sourceUrl", metadata.sourceUrl)
                    put("resolutionMeters", metadata.resolutionMeters)
                    put("rowCount", metadata.rowCount)
                    put("columnCount", metadata.columnCount)
                    put(
                        "boundingBox",
                        buildJsonObject {
                            put("minLatitude", metadata.boundingBox.minLatitude)
                            put("maxLatitude", metadata.boundingBox.maxLatitude)
                            put("minLongitude", metadata.boundingBox.minLongitude)
                            put("maxLongitude", metadata.boundingBox.maxLongitude)
                        }
                    )
                    put("createdAtIso", metadata.createdAtIso)
                }
            )
            put(
                "elevations",
                buildJsonArray {
                    elevations.forEach { value ->
                        add(value?.let(::JsonPrimitive) ?: JsonNull)
                    }
                }
            )
        }.toString()

    private fun parseCacheFile(text: String, path: Path): DesktopVenueElevationCacheFile {
        val root = json.parseToJsonElement(text).jsonObject
        val metadata = root.getValue("metadata").jsonObject
        val boundingBox = metadata.getValue("boundingBox").jsonObject
        return DesktopVenueElevationCacheFile(
            metadata = DesktopVenueElevationCacheMetadata(
                version = metadata.getValue("version").jsonPrimitive.content.toInt(),
                venueName = metadata.getValue("venueName").jsonPrimitive.content,
                sourceName = metadata.getValue("sourceName").jsonPrimitive.content,
                sourceUrl = metadata.getValue("sourceUrl").jsonPrimitive.content,
                resolutionMeters = metadata.getValue("resolutionMeters").jsonPrimitive.content.toDouble(),
                rowCount = metadata.getValue("rowCount").jsonPrimitive.content.toInt(),
                columnCount = metadata.getValue("columnCount").jsonPrimitive.content.toInt(),
                boundingBox = DesktopVenueElevationBoundingBoxValue(
                    minLatitude = boundingBox.getValue("minLatitude").jsonPrimitive.content.toDouble(),
                    maxLatitude = boundingBox.getValue("maxLatitude").jsonPrimitive.content.toDouble(),
                    minLongitude = boundingBox.getValue("minLongitude").jsonPrimitive.content.toDouble(),
                    maxLongitude = boundingBox.getValue("maxLongitude").jsonPrimitive.content.toDouble()
                ),
                createdAtIso = metadata.getValue("createdAtIso").jsonPrimitive.content
            ),
            elevations = root.getValue("elevations").jsonArray.map { value ->
                if (value == JsonNull) null else value.jsonPrimitive.doubleOrNull
            },
            path = path
        )
    }
}

private data class DesktopVenueElevationCacheFile(
    val metadata: DesktopVenueElevationCacheMetadata,
    val elevations: List<Double?>,
    @kotlinx.serialization.Transient
    val path: Path = Path.of("")
)

private data class DesktopVenueElevationCacheMetadata(
    val version: Int,
    val venueName: String,
    val sourceName: String,
    val sourceUrl: String,
    val resolutionMeters: Double,
    val rowCount: Int,
    val columnCount: Int,
    val boundingBox: DesktopVenueElevationBoundingBoxValue,
    val createdAtIso: String
)

private data class DesktopVenueElevationBoundingBoxValue(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
private const val USGS_GET_SAMPLES_URL =
    "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/getSamples"

private fun metersPerDegreeLongitude(latitude: Double): Double =
    METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))
