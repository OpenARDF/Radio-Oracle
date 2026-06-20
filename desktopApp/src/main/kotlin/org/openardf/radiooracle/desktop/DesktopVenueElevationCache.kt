package org.openardf.radiooracle.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class DesktopVenueElevationReferenceSource(
    val label: String
) {
    Usgs3DepLive("USGS 3DEP live"),
    OpenTopoDataNed10m("OpenTopoData NED 10m")
}

enum class DesktopVenueElevationCacheSource(
    val label: String
) {
    Usgs3Dep("USGS 3DEP"),
    WashingtonDnrLidarDtm("Washington DNR LiDAR DTM"),
    OregonDogamiLidarDtm("Oregon DOGAMI LiDAR DTM"),
    LocalLidarRaster("Local LiDAR Raster")
}

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
    val totalPointCount: Int,
    val estimatedRawBytes: Long? = null
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

data class DesktopVenueElevationSpotCheckSummary(
    val venueName: String,
    val cachePath: Path,
    val referenceSource: DesktopVenueElevationReferenceSource,
    val requestedPointCount: Int,
    val comparedPointCount: Int,
    val missingCacheCount: Int,
    val missingReferenceCount: Int,
    val averageDifferenceMeters: Double?,
    val averageAbsoluteDifferenceMeters: Double?,
    val maximumAbsoluteDifferenceMeters: Double?,
    val rows: List<DesktopVenueElevationSpotCheckRow>
)

data class DesktopVenueElevationSpotCheckRow(
    val row: Int,
    val column: Int,
    val latitude: Double,
    val longitude: Double,
    val cachedMeters: Double?,
    val referenceMeters: Double?,
    val differenceMeters: Double?
)

data class DesktopVenueElevationDemImportCandidate(
    val displayName: String,
    val sourcePath: Path,
    val zipEntryName: String?,
    val targetPath: Path,
    val venueName: String,
    val sourceName: String,
    val resolutionMeters: Double,
    val rowCount: Int,
    val columnCount: Int,
    val willOverwrite: Boolean
)

data class DesktopVenueElevationDemImportIssue(
    val displayName: String,
    val reason: String
)

data class DesktopVenueElevationDemImportReview(
    val candidates: List<DesktopVenueElevationDemImportCandidate>,
    val issues: List<DesktopVenueElevationDemImportIssue>
) {
    val importableCount: Int get() = candidates.size
    val overwriteCount: Int get() = candidates.count { it.willOverwrite }
}

data class DesktopVenueElevationDemImportSummary(
    val importedCount: Int,
    val overwrittenCount: Int,
    val targetDirectory: Path
)

object DesktopVenueElevationCache {
    private const val CACHE_VERSION = 1
    private const val DEFAULT_SOURCE_NAME = "USGS 3DEP"
    private const val WASHINGTON_DNR_SOURCE_NAME = "Washington DNR LiDAR DTM"
    private const val OREGON_DOGAMI_SOURCE_NAME = "Oregon DOGAMI LiDAR DTM"
    private const val ARCGIS_SAMPLE_BATCH_SIZE = 250
    private const val ARCGIS_SAMPLE_RETRY_COUNT = 4
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private suspend fun <T> sendRequest(
        request: HttpRequest,
        bodyHandler: HttpResponse.BodyHandler<T>
    ): HttpResponse<T> {
        val requestFuture = httpClient.sendAsync(request, bodyHandler)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                requestFuture.cancel(true)
            }
            requestFuture.whenComplete { response, throwable ->
                if (!continuation.isActive) {
                    return@whenComplete
                }
                if (response != null) {
                    continuation.resume(response)
                } else {
                    continuation.resumeWithException((throwable as? CompletionException)?.cause ?: throwable ?: IOException("Unknown network error."))
                }
            }
        }
    }

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

    fun reviewDemFileImport(paths: List<Path>): DesktopVenueElevationDemImportReview {
        val targetDirectory = cacheDirectory()
        val candidates = mutableListOf<DesktopVenueElevationDemImportCandidate>()
        val issues = mutableListOf<DesktopVenueElevationDemImportIssue>()
        paths.forEach { path ->
            val fileName = path.fileName?.toString().orEmpty()
            when {
                fileName.endsWith(".zip", ignoreCase = true) ->
                    reviewDemZipImport(path, targetDirectory, candidates, issues)
                fileName.endsWith(".json", ignoreCase = true) ->
                    reviewDemJsonImport(path, null, fileName, targetDirectory, candidates, issues)
                else ->
                    issues += DesktopVenueElevationDemImportIssue(
                        displayName = fileName.ifBlank { path.toString() },
                        reason = "Only .json DEM files and .zip archives are supported."
                    )
            }
        }
        val duplicateTargets = candidates
            .groupBy { it.targetPath.fileName.toString().lowercase(Locale.US) }
            .filterValues { it.size > 1 }
            .values
            .flatten()
            .toSet()
        if (duplicateTargets.isNotEmpty()) {
            duplicateTargets.forEach { candidate ->
                issues += DesktopVenueElevationDemImportIssue(
                    displayName = candidate.displayName,
                    reason = "Multiple selected DEM files would import as ${candidate.targetPath.fileName}."
                )
            }
        }
        return DesktopVenueElevationDemImportReview(
            candidates = candidates.filterNot { it in duplicateTargets }
                .sortedWith(compareBy<DesktopVenueElevationDemImportCandidate> { it.targetPath.fileName.toString().lowercase(Locale.US) }
                    .thenBy { it.displayName.lowercase(Locale.US) }),
            issues = issues.sortedBy { it.displayName.lowercase(Locale.US) }
        )
    }

    fun importReviewedDemFiles(review: DesktopVenueElevationDemImportReview): DesktopVenueElevationDemImportSummary {
        val targetDirectory = cacheDirectory()
        Files.createDirectories(targetDirectory)
        var importedCount = 0
        var overwrittenCount = 0
        review.candidates.forEach { candidate ->
            if (candidate.willOverwrite && Files.exists(candidate.targetPath)) {
                overwrittenCount += 1
            }
            if (candidate.zipEntryName == null) {
                if (candidate.sourcePath.toAbsolutePath().normalize() != candidate.targetPath.toAbsolutePath().normalize()) {
                    Files.copy(candidate.sourcePath, candidate.targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                ZipFile(candidate.sourcePath.toFile()).use { zipFile ->
                    val entry = zipFile.getEntry(candidate.zipEntryName)
                        ?: throw IOException("${candidate.sourcePath.fileName} no longer contains ${candidate.zipEntryName}.")
                    zipFile.getInputStream(entry).use { input ->
                        Files.copy(input, candidate.targetPath, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            importedCount += 1
        }
        loadedSignature = ""
        loadedCaches = emptyList()
        return DesktopVenueElevationDemImportSummary(
            importedCount = importedCount,
            overwrittenCount = overwrittenCount,
            targetDirectory = targetDirectory
        )
    }

    fun elevationMeters(point: CourseGeoPoint): Double? =
        loadCaches()
            .filter { it.metadata.boundingBox.toPublic().contains(point) }
            .sortedWith(
                compareBy<DesktopVenueElevationCacheFile> { it.sourcePriority() }
                    .thenBy { it.metadata.resolutionMeters }
                    .thenByDescending { it.metadata.createdAtIso }
                    .thenBy { it.path.fileName.toString() }
            )
            .firstNotNullOfOrNull { it.elevationMeters(point) }

    fun analysisSourceNotes(points: List<CourseGeoPoint>): List<String> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val selectedCaches = points
            .mapNotNull { point -> selectedCache(point) }
            .distinctBy { it.path }
            .sortedWith(
                compareBy<DesktopVenueElevationCacheFile> { it.metadata.venueName.lowercase() }
                    .thenBy { it.sourcePriority() }
                    .thenBy { it.metadata.resolutionMeters }
                    .thenBy { it.path.fileName.toString() }
            )
        return if (selectedCaches.isEmpty()) {
            listOf("Elevation cache: no local cache file matched the route/profile points.")
        } else {
            listOf(
                "Elevation cache: " + selectedCaches.joinToString("; ") { cache ->
                    "${cache.metadata.venueName} - ${cache.metadata.sourceName}, " +
                        "${cache.metadata.resolutionMeters.metersText()} grid (${cache.path.fileName})"
                }
            )
        }
    }

    suspend fun spotCheck(
        cachePath: Path,
        referenceSource: DesktopVenueElevationReferenceSource,
        samplePointCount: Int = 100
    ): DesktopVenueElevationSpotCheckSummary =
        withContext(Dispatchers.IO) {
            val cache = parseCacheFile(Files.readString(cachePath), cachePath)
            val samplePoints = cache.spotCheckPoints(samplePointCount)
            val referenceValues = when (referenceSource) {
                DesktopVenueElevationReferenceSource.Usgs3DepLive ->
                    samplePoints.chunked(ARCGIS_SAMPLE_BATCH_SIZE).flatMapIndexed { chunkIndex, chunk ->
                        usgs3DepSamplesWithRetry(chunk.map { it.point }, chunkIndex + 1)
                    }
                DesktopVenueElevationReferenceSource.OpenTopoDataNed10m ->
                    openTopoDataElevations("ned10m", samplePoints.map { it.point })
            }
            val rows = samplePoints.mapIndexed { index, sample ->
                val cached = cache.elevationAt(sample.row, sample.column)
                val reference = referenceValues.getOrNull(index)
                DesktopVenueElevationSpotCheckRow(
                    row = sample.row,
                    column = sample.column,
                    latitude = sample.point.latitude,
                    longitude = sample.point.longitude,
                    cachedMeters = cached,
                    referenceMeters = reference,
                    differenceMeters = if (cached != null && reference != null) cached - reference else null
                )
            }
            val differences = rows.mapNotNull { it.differenceMeters }
            DesktopVenueElevationSpotCheckSummary(
                venueName = cache.metadata.venueName,
                cachePath = cache.path,
                referenceSource = referenceSource,
                requestedPointCount = samplePoints.size,
                comparedPointCount = differences.size,
                missingCacheCount = rows.count { it.cachedMeters == null },
                missingReferenceCount = rows.count { it.referenceMeters == null },
                averageDifferenceMeters = differences.takeIf { it.isNotEmpty() }?.average(),
                averageAbsoluteDifferenceMeters = differences.takeIf { it.isNotEmpty() }?.map(::abs)?.average(),
                maximumAbsoluteDifferenceMeters = differences.takeIf { it.isNotEmpty() }?.maxOf { abs(it) },
                rows = rows.sortedByDescending { abs(it.differenceMeters ?: 0.0) }
            )
        }

    suspend fun download(
        venueName: String,
        boundingBox: DesktopVenueElevationBoundingBox?,
        resolutionMeters: Double,
        bufferMeters: Double,
        source: DesktopVenueElevationCacheSource = DesktopVenueElevationCacheSource.Usgs3Dep,
        sourceUrl: String = "",
        onProgress: (DesktopVenueElevationCacheProgress) -> Unit = {}
    ): DesktopVenueElevationCacheSummary =
        when (source) {
            DesktopVenueElevationCacheSource.Usgs3Dep ->
                downloadUsgs3Dep(
                    venueName = venueName,
                    boundingBox = requireNotNull(boundingBox) { "USGS 3DEP download requires bounding coordinates." },
                    resolutionMeters = resolutionMeters,
                    bufferMeters = bufferMeters,
                    onProgress = onProgress
                )
            DesktopVenueElevationCacheSource.WashingtonDnrLidarDtm ->
                downloadWashingtonDnrLidarDtm(
                    venueName = venueName,
                    boundingBox = requireNotNull(boundingBox) { "Washington DNR LiDAR DTM download requires bounding coordinates." },
                    resolutionMeters = resolutionMeters,
                    bufferMeters = bufferMeters,
                    onProgress = onProgress
                )
            DesktopVenueElevationCacheSource.OregonDogamiLidarDtm ->
                downloadOregonDogamiLidarDtm(
                    venueName = venueName,
                    boundingBox = requireNotNull(boundingBox) { "Oregon DOGAMI LiDAR DTM download requires bounding coordinates." },
                    resolutionMeters = resolutionMeters,
                    bufferMeters = bufferMeters,
                    onProgress = onProgress
                )
            DesktopVenueElevationCacheSource.LocalLidarRaster ->
                createFromLocalLidarRaster(
                    venueName = venueName,
                    boundingBox = boundingBox,
                    resolutionMeters = resolutionMeters,
                    bufferMeters = bufferMeters,
                    sourcePathText = sourceUrl,
                    onProgress = onProgress
                )
        }

    private suspend fun downloadUsgs3Dep(
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
                    totalPointCount = points.size,
                    estimatedRawBytes = estimate.rawBytes
                )
            )
            val elevations = MutableList<Double?>(points.size) { null }
            var completed = 0
            points.chunked(ARCGIS_SAMPLE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                coroutineContext.ensureActive()
                val values = usgs3DepSamplesWithRetry(chunk, chunkIndex + 1)
                values.forEachIndexed { index, value ->
                    elevations[chunkIndex * ARCGIS_SAMPLE_BATCH_SIZE + index] = value
                }
                completed += chunk.size
                onProgress(
                    DesktopVenueElevationCacheProgress(
                        venueName = cleanVenueName,
                        completedPointCount = completed,
                        totalPointCount = points.size,
                        estimatedRawBytes = estimate.rawBytes
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

    private suspend fun downloadWashingtonDnrLidarDtm(
        venueName: String,
        boundingBox: DesktopVenueElevationBoundingBox,
        resolutionMeters: Double,
        bufferMeters: Double,
        onProgress: (DesktopVenueElevationCacheProgress) -> Unit = {}
    ): DesktopVenueElevationCacheSummary =
        withContext(Dispatchers.IO) {
            val gdal = DesktopGdalTools.requireAvailable()
            val cleanVenueName = venueName.trim().ifBlank { "Venue" }
            val estimate = estimate(boundingBox, resolutionMeters, bufferMeters)
            val projectIndex = washingtonDnrProjectIndex()
            val dataset = projectIndex.bestDtmDatasetFor(estimate.boundingBox)
                ?: error("No Washington DNR DTM dataset intersects the requested bounding box.")
            val points = estimate.gridPoints()
            onProgress(
                DesktopVenueElevationCacheProgress(
                    venueName = cleanVenueName,
                    completedPointCount = 0,
                    totalPointCount = points.size,
                    estimatedRawBytes = estimate.rawBytes
                )
            )
            val directory = cacheDirectory()
            Files.createDirectories(directory)
            val sourceDirectory = directory.resolve("sources")
            Files.createDirectories(sourceDirectory)
            val zipPath = sourceDirectory.resolve(
                "${cacheSlug(cleanVenueName)}-wa-dnr-dtm-${dataset.id}-${Instant.now().toString().replace(Regex("[^0-9TZ]"), "")}.zip"
            )
            val downloadUri = washingtonDnrDownloadUri(estimate.boundingBox, dataset.id)
            downloadFile(downloadUri, zipPath, WASHINGTON_DNR_SOURCE_NAME)
            coroutineContext.ensureActive()
            val workDirectory = Files.createTempDirectory("radio-oracle-wa-dnr-dtm-")
            try {
                val rasterPath = extractRasterArchive(
                    workDirectory = workDirectory,
                    zipPath = zipPath,
                    gdal = gdal,
                    sourceLabel = WASHINGTON_DNR_SOURCE_NAME
                )
                coroutineContext.ensureActive()
                val elevations = gdal.sampleWgs84(rasterPath, points)
                onProgress(
                    DesktopVenueElevationCacheProgress(
                        venueName = cleanVenueName,
                        completedPointCount = points.size,
                        totalPointCount = points.size,
                        estimatedRawBytes = estimate.rawBytes
                    )
                )
                val file = DesktopVenueElevationCacheFile(
                    metadata = DesktopVenueElevationCacheMetadata(
                        version = CACHE_VERSION,
                        venueName = cleanVenueName,
                        sourceName = "$WASHINGTON_DNR_SOURCE_NAME - ${dataset.projectName}",
                        sourceUrl = downloadUri.toString(),
                        resolutionMeters = resolutionMeters,
                        rowCount = estimate.rowCount,
                        columnCount = estimate.columnCount,
                        boundingBox = estimate.boundingBox.toSerializable(),
                        createdAtIso = Instant.now().toString()
                    ),
                    elevations = elevations
                )
                val path = uniqueCachePath(directory, cleanVenueName, resolutionMeters)
                Files.writeString(path, file.toJsonString())
                loadedSignature = ""
                val hash = fileSha256(path)
                DesktopDebugLog.info(
                    "ElevationCache",
                    "Downloaded venue=$cleanVenueName source=$WASHINGTON_DNR_SOURCE_NAME project=${dataset.projectName} " +
                        "resolution=${resolutionMeters}m points=${points.size} resolved=${elevations.count { it != null }} " +
                        "file=${path.fileName} hash=${hash.take(12)}"
                )
                DesktopVenueElevationCacheSummary(
                    venueName = cleanVenueName,
                    path = path,
                    rowCount = estimate.rowCount,
                    columnCount = estimate.columnCount,
                    pointCount = points.size,
                    resolvedPointCount = elevations.count { it != null },
                    sourceName = file.metadata.sourceName,
                    resolutionMeters = resolutionMeters,
                    fileSha256 = hash
                )
            } finally {
                runCatching { deleteRecursively(workDirectory) }
            }
        }

    private suspend fun downloadOregonDogamiLidarDtm(
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
                    totalPointCount = points.size,
                    estimatedRawBytes = estimate.rawBytes
                )
            )
            val elevations = MutableList<Double?>(points.size) { null }
            var completed = 0
            points.chunked(ARCGIS_SAMPLE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                coroutineContext.ensureActive()
                val values = oregonDogamiSamplesWithRetry(chunk, chunkIndex + 1)
                values.forEachIndexed { index, value ->
                    elevations[chunkIndex * ARCGIS_SAMPLE_BATCH_SIZE + index] = value
                }
                completed += chunk.size
                onProgress(
                    DesktopVenueElevationCacheProgress(
                        venueName = cleanVenueName,
                        completedPointCount = completed,
                        totalPointCount = points.size,
                        estimatedRawBytes = estimate.rawBytes
                    )
                )
            }
            val file = DesktopVenueElevationCacheFile(
                metadata = DesktopVenueElevationCacheMetadata(
                    version = CACHE_VERSION,
                    venueName = cleanVenueName,
                    sourceName = OREGON_DOGAMI_SOURCE_NAME,
                    sourceUrl = OREGON_DOGAMI_DTM_GET_SAMPLES_URL,
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
                "Downloaded venue=$cleanVenueName source=$OREGON_DOGAMI_SOURCE_NAME resolution=${resolutionMeters}m " +
                    "points=${points.size} resolved=${elevations.count { it != null }} file=${path.fileName} hash=${hash.take(12)}"
            )
            DesktopVenueElevationCacheSummary(
                venueName = cleanVenueName,
                path = path,
                rowCount = estimate.rowCount,
                columnCount = estimate.columnCount,
                pointCount = points.size,
                resolvedPointCount = elevations.count { it != null },
                sourceName = OREGON_DOGAMI_SOURCE_NAME,
                resolutionMeters = resolutionMeters,
                fileSha256 = hash
            )
        }

    private suspend fun createFromLocalLidarRaster(
        venueName: String,
        boundingBox: DesktopVenueElevationBoundingBox?,
        resolutionMeters: Double,
        bufferMeters: Double,
        sourcePathText: String,
        onProgress: (DesktopVenueElevationCacheProgress) -> Unit = {}
    ): DesktopVenueElevationCacheSummary =
        withContext(Dispatchers.IO) {
            val sources = localElevationSources(sourcePathText)
            val sourcePaths = sources.paths
            val sourceType = sources.type
            val cleanVenueName = venueName.trim().ifBlank { "Venue" }
            val gdal = DesktopGdalTools.requireAvailable()
            val workDirectory = Files.createTempDirectory("radio-oracle-local-lidar-")
            var elevationUnits = DesktopGdalElevationUnits("unspecified", 1.0)
            var estimate: DesktopVenueElevationCacheEstimate? = null
            var points = emptyList<CourseGeoPoint>()
            val elevations = try {
                val rasterPath = when (sourceType) {
                    LocalElevationSourceType.GeoTiff ->
                        sourcePaths.single().toString()
                    LocalElevationSourceType.GeoTiffZip ->
                        rasterPathForZipArchive(
                            workDirectory = workDirectory,
                            zipPath = sourcePaths.single(),
                            gdal = gdal,
                            sourceLabel = "Local LiDAR raster"
                        )
                    LocalElevationSourceType.LasPointCloud -> {
                        val pdal = DesktopPdalTools.requireAvailable()
                        val outputRaster = workDirectory.resolve("point-cloud-dem.tif")
                        elevationUnits = pdal.commonElevationUnits(sourcePaths)
                        estimate = runCatching {
                            estimate(pdal.wgs84BoundingBox(sourcePaths), resolutionMeters, 0.0)
                        }.onFailure { error ->
                            DesktopDebugLog.info(
                                "ElevationCache",
                                "Could not estimate LAS/LAZ point-cloud extent before rasterization: ${error.message ?: error::class.simpleName}"
                            )
                        }.getOrNull()
                        estimate?.let { pointCloudEstimate ->
                            onProgress(
                                DesktopVenueElevationCacheProgress(
                                    venueName = cleanVenueName,
                                    completedPointCount = 0,
                                    totalPointCount = pointCloudEstimate.pointCount,
                                    estimatedRawBytes = pointCloudEstimate.rawBytes
                                )
                            )
                        }
                        pdal.rasterizeLasPointCloud(
                            sourcePaths = sourcePaths,
                            outputRaster = outputRaster,
                            resolutionMeters = resolutionMeters,
                            workDirectory = workDirectory
                        )
                        estimate = estimate(gdal.wgs84BoundingBox(outputRaster.toString()), resolutionMeters, 0.0)
                        outputRaster.toString()
                    }
                }
                if (sourceType != LocalElevationSourceType.LasPointCloud) {
                    elevationUnits = gdal.elevationUnits(rasterPath)
                    estimate = estimate(
                        boundingBox ?: gdal.wgs84BoundingBox(rasterPath),
                        resolutionMeters,
                        if (boundingBox == null) 0.0 else bufferMeters
                    )
                }
                val cacheEstimate = requireNotNull(estimate) {
                    "Local elevation source extent could not be determined."
                }
                points = cacheEstimate.gridPoints()
                onProgress(
                    DesktopVenueElevationCacheProgress(
                        venueName = cleanVenueName,
                        completedPointCount = 0,
                        totalPointCount = points.size,
                        estimatedRawBytes = cacheEstimate.rawBytes
                    )
                )
                gdal.sampleWgs84(
                    rasterPath = rasterPath,
                    points = points,
                    valueMultiplier = elevationUnits.valueMultiplier,
                    onSampledCount = { completed ->
                        onProgress(
                            DesktopVenueElevationCacheProgress(
                                venueName = cleanVenueName,
                                completedPointCount = completed,
                                totalPointCount = points.size,
                                estimatedRawBytes = cacheEstimate.rawBytes
                            )
                        )
                    }
                ).also {
                    onProgress(
                        DesktopVenueElevationCacheProgress(
                            venueName = cleanVenueName,
                            completedPointCount = points.size,
                            totalPointCount = points.size,
                            estimatedRawBytes = cacheEstimate.rawBytes
                        )
                    )
                }
            } finally {
                runCatching { deleteRecursively(workDirectory) }
            }
            val cacheEstimate = requireNotNull(estimate) {
                "Local elevation source extent could not be determined."
            }
            onProgress(
                DesktopVenueElevationCacheProgress(
                    venueName = cleanVenueName,
                    completedPointCount = points.size,
                    totalPointCount = points.size,
                    estimatedRawBytes = cacheEstimate.rawBytes
                )
            )
            val file = DesktopVenueElevationCacheFile(
                metadata = DesktopVenueElevationCacheMetadata(
                    version = CACHE_VERSION,
                    venueName = cleanVenueName,
                    sourceName = "Local LiDAR Raster - ${localElevationSourceLabel(sourcePaths)}${elevationUnits.sourceNameSuffix()}",
                    sourceUrl = sourcePaths.joinToString(System.lineSeparator()) { it.toString() },
                    resolutionMeters = resolutionMeters,
                    rowCount = cacheEstimate.rowCount,
                    columnCount = cacheEstimate.columnCount,
                    boundingBox = cacheEstimate.boundingBox.toSerializable(),
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
                "Created venue=$cleanVenueName source=Local LiDAR Raster sourceFiles=${localElevationSourceLabel(sourcePaths)} " +
                    "unit=${elevationUnits.label} multiplier=${elevationUnits.valueMultiplier} " +
                    "resolution=${resolutionMeters}m points=${points.size} resolved=${elevations.count { it != null }} " +
                    "file=${path.fileName} hash=${hash.take(12)}"
            )
            DesktopVenueElevationCacheSummary(
                venueName = cleanVenueName,
                path = path,
                rowCount = cacheEstimate.rowCount,
                columnCount = cacheEstimate.columnCount,
                pointCount = points.size,
                resolvedPointCount = elevations.count { it != null },
                sourceName = file.metadata.sourceName,
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

    private fun reviewDemZipImport(
        path: Path,
        targetDirectory: Path,
        candidates: MutableList<DesktopVenueElevationDemImportCandidate>,
        issues: MutableList<DesktopVenueElevationDemImportIssue>
    ) {
        runCatching {
            var jsonEntryCount = 0
            ZipFile(path.toFile()).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".json", ignoreCase = true)) {
                        continue
                    }
                    jsonEntryCount += 1
                    val displayName = "${path.fileName}:${entry.name}"
                    val originalName = Path.of(entry.name).fileName?.toString().orEmpty()
                    val text = zipFile.getInputStream(entry).use { input ->
                        input.reader(StandardCharsets.UTF_8).readText()
                    }
                    reviewDemJsonText(
                        text = text,
                        sourcePath = path,
                        zipEntryName = entry.name,
                        originalName = originalName,
                        displayName = displayName,
                        targetDirectory = targetDirectory,
                        candidates = candidates,
                        issues = issues
                    )
                }
            }
            if (jsonEntryCount == 0) {
                issues += DesktopVenueElevationDemImportIssue(
                    displayName = path.fileName.toString(),
                    reason = "ZIP archive contains no .json DEM files."
                )
            }
        }.onFailure { error ->
            issues += DesktopVenueElevationDemImportIssue(
                displayName = path.fileName.toString(),
                reason = "Could not read ZIP archive: ${error.message ?: error::class.simpleName}"
            )
        }
    }

    private fun reviewDemJsonImport(
        sourcePath: Path,
        zipEntryName: String?,
        originalName: String,
        targetDirectory: Path,
        candidates: MutableList<DesktopVenueElevationDemImportCandidate>,
        issues: MutableList<DesktopVenueElevationDemImportIssue>
    ) {
        val displayName = sourcePath.fileName?.toString() ?: sourcePath.toString()
        runCatching {
            Files.readString(sourcePath)
        }.onSuccess { text ->
            reviewDemJsonText(
                text = text,
                sourcePath = sourcePath,
                zipEntryName = zipEntryName,
                originalName = originalName,
                displayName = displayName,
                targetDirectory = targetDirectory,
                candidates = candidates,
                issues = issues
            )
        }.onFailure { error ->
            issues += DesktopVenueElevationDemImportIssue(
                displayName = displayName,
                reason = "Could not read file: ${error.message ?: error::class.simpleName}"
            )
        }
    }

    private fun reviewDemJsonText(
        text: String,
        sourcePath: Path,
        zipEntryName: String?,
        originalName: String,
        displayName: String,
        targetDirectory: Path,
        candidates: MutableList<DesktopVenueElevationDemImportCandidate>,
        issues: MutableList<DesktopVenueElevationDemImportIssue>
    ) {
        runCatching {
            val targetFileName = importedDemTargetFileName(originalName)
            val targetPath = targetDirectory.resolve(targetFileName)
            val cacheFile = parseCacheFile(text, targetPath)
            validateImportedDemCache(cacheFile)
            candidates += DesktopVenueElevationDemImportCandidate(
                displayName = displayName,
                sourcePath = sourcePath,
                zipEntryName = zipEntryName,
                targetPath = targetPath,
                venueName = cacheFile.metadata.venueName,
                sourceName = cacheFile.metadata.sourceName,
                resolutionMeters = cacheFile.metadata.resolutionMeters,
                rowCount = cacheFile.metadata.rowCount,
                columnCount = cacheFile.metadata.columnCount,
                willOverwrite = Files.exists(targetPath)
            )
        }.onFailure { error ->
            issues += DesktopVenueElevationDemImportIssue(
                displayName = displayName,
                reason = "Not a valid Radio-Oracle DEM cache JSON: ${error.message ?: error::class.simpleName}"
            )
        }
    }

    private fun importedDemTargetFileName(originalName: String): String {
        val jsonStem = originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffixIgnoreCase(".json")
            .ifBlank { "venue" }
        val roelevStem = if (jsonStem.endsWith(".roelev", ignoreCase = true)) {
            jsonStem
        } else {
            "$jsonStem.roelev"
        }
        return roelevStem
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .ifBlank { "venue.roelev" } + ".json"
    }

    private fun validateImportedDemCache(cacheFile: DesktopVenueElevationCacheFile) {
        val metadata = cacheFile.metadata
        require(metadata.version == CACHE_VERSION) { "Unsupported cache version ${metadata.version}." }
        require(metadata.venueName.isNotBlank()) { "Missing venue name." }
        require(metadata.sourceName.isNotBlank()) { "Missing source name." }
        require(metadata.resolutionMeters > 0.0) { "Resolution must be greater than zero." }
        require(metadata.rowCount > 0) { "Row count must be greater than zero." }
        require(metadata.columnCount > 0) { "Column count must be greater than zero." }
        require(metadata.rowCount.toLong() * metadata.columnCount.toLong() == cacheFile.elevations.size.toLong()) {
            "Elevation count ${cacheFile.elevations.size} does not match ${metadata.rowCount} x ${metadata.columnCount}."
        }
        metadata.boundingBox.toPublic()
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) {
            dropLast(suffix.length)
        } else {
            this
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

    private fun DesktopVenueElevationCacheFile.spotCheckPoints(samplePointCount: Int): List<DesktopVenueElevationSpotCheckPoint> {
        val targetCount = samplePointCount.coerceAtLeast(1)
        val rowTarget = ceil(
            sqrt(targetCount.toDouble() * metadata.rowCount.toDouble() / metadata.columnCount.toDouble().coerceAtLeast(1.0))
        ).toInt().coerceIn(1, metadata.rowCount.coerceAtLeast(1))
        var columnTarget = ceil(targetCount.toDouble() / rowTarget.toDouble()).toInt()
            .coerceIn(1, metadata.columnCount.coerceAtLeast(1))
        var adjustedRowTarget = rowTarget
        while (adjustedRowTarget * columnTarget < targetCount &&
            (adjustedRowTarget < metadata.rowCount || columnTarget < metadata.columnCount)
        ) {
            if (columnTarget < metadata.columnCount) {
                columnTarget += 1
            } else {
                adjustedRowTarget += 1
            }
        }
        val rows = evenlySpacedIndices(metadata.rowCount, adjustedRowTarget)
        val columns = evenlySpacedIndices(metadata.columnCount, columnTarget)
        val candidates = buildList {
            rows.forEach { row ->
                columns.forEach { column ->
                    add(
                        DesktopVenueElevationSpotCheckPoint(
                            row = row,
                            column = column,
                            point = gridPoint(row, column)
                        )
                    )
                }
            }
        }
        return evenlySpacedIndices(candidates.size, targetCount).map(candidates::get)
    }

    private fun DesktopVenueElevationCacheFile.gridPoint(row: Int, column: Int): CourseGeoPoint {
        val bbox = metadata.boundingBox.toPublic()
        val latitude = if (metadata.rowCount <= 1) {
            bbox.minLatitude
        } else {
            bbox.minLatitude + (bbox.maxLatitude - bbox.minLatitude) * row.toDouble() / (metadata.rowCount - 1).toDouble()
        }
        val longitude = if (metadata.columnCount <= 1) {
            bbox.minLongitude
        } else {
            bbox.minLongitude + (bbox.maxLongitude - bbox.minLongitude) * column.toDouble() / (metadata.columnCount - 1).toDouble()
        }
        return CourseGeoPoint(latitude = latitude, longitude = longitude)
    }

    private fun evenlySpacedIndices(size: Int, targetCount: Int): List<Int> {
        if (size <= 0 || targetCount <= 0) {
            return emptyList()
        }
        val count = min(size, targetCount).coerceAtLeast(1)
        if (count == 1) {
            return listOf(0)
        }
        return (0 until count)
            .map { index ->
                ((size - 1).toDouble() * index.toDouble() / (count - 1).toDouble()).roundToInt()
            }
            .distinct()
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

    private fun selectedCache(point: CourseGeoPoint): DesktopVenueElevationCacheFile? =
        loadCaches()
            .filter { it.metadata.boundingBox.toPublic().contains(point) }
            .sortedWith(
                compareBy<DesktopVenueElevationCacheFile> { it.sourcePriority() }
                    .thenBy { it.metadata.resolutionMeters }
                    .thenByDescending { it.metadata.createdAtIso }
                    .thenBy { it.path.fileName.toString() }
            )
            .firstOrNull { it.elevationMeters(point) != null }

    private fun DesktopVenueElevationCacheFile.sourcePriority(): Int =
        when {
            metadata.sourceName.contains("LiDAR", ignoreCase = true) -> 0
            metadata.sourceName == DEFAULT_SOURCE_NAME -> 1
            else -> 2
        }

    private fun Double.metersText(): String =
        if (kotlin.math.abs(this - roundToInt()) < 0.01) {
            "${roundToInt()} m"
        } else {
            "%.2f m".format(Locale.US, this)
        }

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
        val slug = cacheSlug(venueName)
        val resolutionText = resolutionMeters.roundToInt().coerceAtLeast(1).toString()
        val timestamp = Instant.now().toString().replace(Regex("[^0-9TZ]"), "")
        return directory.resolve("$slug-${resolutionText}m-$timestamp.roelev.json")
    }

    private fun cacheSlug(value: String): String =
        value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "venue" }

    private suspend fun usgs3DepSamplesWithRetry(points: List<CourseGeoPoint>, batchNumber: Int): List<Double?> {
        var lastError: Throwable? = null
        repeat(ARCGIS_SAMPLE_RETRY_COUNT) { attemptIndex ->
            try {
                return usgs3DepSamples(points)
            } catch (error: Throwable) {
                lastError = error
                val attempt = attemptIndex + 1
                if (!error.isRetryableElevationServiceError() || attempt == ARCGIS_SAMPLE_RETRY_COUNT) {
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
                delay(1_000L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("USGS 3DEP sample request failed.")
    }

    suspend fun usgs3DepElevations(points: List<CourseGeoPoint>): List<Double?> =
        withContext(Dispatchers.IO) {
            if (points.isEmpty()) {
                return@withContext emptyList()
            }
            val elevations = MutableList<Double?>(points.size) { null }
            points.chunked(ARCGIS_SAMPLE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                coroutineContext.ensureActive()
                val values = usgs3DepSamplesWithRetry(chunk, chunkIndex + 1)
                values.forEachIndexed { index, value ->
                    elevations[chunkIndex * ARCGIS_SAMPLE_BATCH_SIZE + index] = value
                }
            }
            elevations
        }

    private suspend fun oregonDogamiSamplesWithRetry(points: List<CourseGeoPoint>, batchNumber: Int): List<Double?> {
        var lastError: Throwable? = null
        repeat(ARCGIS_SAMPLE_RETRY_COUNT) { attemptIndex ->
            try {
                return arcGisImageSamples(
                    serviceUrl = OREGON_DOGAMI_DTM_GET_SAMPLES_URL,
                    points = points,
                    sourceName = OREGON_DOGAMI_SOURCE_NAME,
                    valueMultiplier = FEET_TO_METERS
                )
            } catch (error: Throwable) {
                lastError = error
                val attempt = attemptIndex + 1
                if (!error.isRetryableElevationServiceError() || attempt == ARCGIS_SAMPLE_RETRY_COUNT) {
                    DesktopDebugLog.warn(
                        "ElevationCache",
                        "Oregon DOGAMI sample batch failed batch=$batchNumber attempt=$attempt points=${points.size}: ${error.message ?: error::class.simpleName}"
                    )
                    throw error
                }
                DesktopDebugLog.warn(
                    "ElevationCache",
                    "Oregon DOGAMI sample batch retry batch=$batchNumber attempt=$attempt points=${points.size}: ${error.message ?: error::class.simpleName}"
                )
                delay(1_000L * attempt)
            }
        }
        throw lastError ?: IllegalStateException("Oregon DOGAMI sample request failed.")
    }

    private fun Throwable.isRetryableElevationServiceError(): Boolean =
        message?.contains("HTTP 502") == true ||
            message?.contains("HTTP 503") == true ||
            message?.contains("HTTP 504") == true ||
            message?.contains("timed out", ignoreCase = true) == true

    private suspend fun usgs3DepSamples(points: List<CourseGeoPoint>): List<Double?> {
        return arcGisImageSamples(
            serviceUrl = USGS_GET_SAMPLES_URL,
            points = points,
            sourceName = "USGS 3DEP sample service"
        )
    }

    private suspend fun arcGisImageSamples(
        serviceUrl: String,
        points: List<CourseGeoPoint>,
        sourceName: String,
        valueMultiplier: Double = 1.0
    ): List<Double?> {
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
        val request = HttpRequest.newBuilder(URI.create(serviceUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Radio-Oracle Desktop")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(45))
            .build()
        val response = sendRequest(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "$sourceName returned HTTP ${response.statusCode()}."
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
                values[locationId] = value?.times(valueMultiplier)
            }
        }
        return values
    }

    private suspend fun openTopoDataElevations(dataset: String, points: List<CourseGeoPoint>): List<Double?> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val locations = points.joinToString("|") { point ->
            "${point.latitude},${point.longitude}"
        }
        val body = buildJsonObject {
            put("locations", locations)
            put("interpolation", "bilinear")
        }.toString()
        val request = HttpRequest.newBuilder(URI.create("https://api.opentopodata.org/v1/$dataset"))
            .header("User-Agent", "Radio-Oracle Desktop")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofSeconds(45))
            .build()
        val response = sendRequest(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "OpenTopoData returned HTTP ${response.statusCode()}."
        }
        val root = json.parseToJsonElement(response.body()).jsonObject
        val status = root["status"]?.jsonPrimitive?.content
        require(status == null || status == "OK") {
            "OpenTopoData returned status ${status ?: "UNKNOWN"}."
        }
        return root["results"]?.jsonArray?.map { result ->
            result.jsonObject["elevation"]?.jsonPrimitive?.doubleOrNull
        } ?: emptyList()
    }

    private fun String.url(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private suspend fun washingtonDnrProjectIndex(): List<WashingtonDnrLidarProject> {
        val request = HttpRequest.newBuilder(URI.create(WASHINGTON_DNR_PROJECT_URL))
            .header("User-Agent", "Radio-Oracle Desktop")
            .GET()
            .timeout(Duration.ofSeconds(60))
            .build()
        val response = sendRequest(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "Washington DNR project index returned HTTP ${response.statusCode()}."
        }
        return json.parseToJsonElement(response.body()).jsonArray.mapNotNull { projectElement ->
            val project = projectElement.jsonObject
            val projectName = project["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val datasets = project["datasets"]?.jsonArray.orEmpty().mapNotNull { datasetElement ->
                val dataset = datasetElement.jsonObject
                val name = dataset["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                WashingtonDnrLidarDataset(
                    id = dataset["ID"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null,
                    projectName = projectName,
                    name = name,
                    boundingBox = DesktopVenueElevationBoundingBox(
                        minLatitude = dataset["YMin"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null,
                        maxLatitude = dataset["YMax"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null,
                        minLongitude = dataset["XMin"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null,
                        maxLongitude = dataset["XMax"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    )
                )
            }
            WashingtonDnrLidarProject(name = projectName, datasets = datasets)
        }
    }

    private fun List<WashingtonDnrLidarProject>.bestDtmDatasetFor(
        boundingBox: DesktopVenueElevationBoundingBox
    ): WashingtonDnrLidarDataset? {
        val center = CourseGeoPoint(
            latitude = (boundingBox.minLatitude + boundingBox.maxLatitude) / 2.0,
            longitude = (boundingBox.minLongitude + boundingBox.maxLongitude) / 2.0
        )
        return flatMap { it.datasets }
            .filter { it.name == "DTM" && it.boundingBox.intersects(boundingBox) }
            .maxWithOrNull(
                compareBy<WashingtonDnrLidarDataset> { if (it.boundingBox.contains(center)) 1 else 0 }
                    .thenBy { it.boundingBox.overlapAreaDegrees(boundingBox) }
                    .thenBy { it.projectYear() }
                    .thenBy { it.id }
            )
    }

    private fun WashingtonDnrLidarDataset.projectYear(): Int =
        Regex("""\b(19|20)\d{2}\b""")
            .findAll(projectName)
            .mapNotNull { it.value.toIntOrNull() }
            .maxOrNull() ?: 0

    private fun DesktopVenueElevationBoundingBox.intersects(other: DesktopVenueElevationBoundingBox): Boolean =
        minLatitude <= other.maxLatitude &&
            maxLatitude >= other.minLatitude &&
            minLongitude <= other.maxLongitude &&
            maxLongitude >= other.minLongitude

    private fun DesktopVenueElevationBoundingBox.overlapAreaDegrees(other: DesktopVenueElevationBoundingBox): Double {
        val latitude = (min(maxLatitude, other.maxLatitude) - max(minLatitude, other.minLatitude)).coerceAtLeast(0.0)
        val longitude = (min(maxLongitude, other.maxLongitude) - max(minLongitude, other.minLongitude)).coerceAtLeast(0.0)
        return latitude * longitude
    }

    private fun washingtonDnrDownloadUri(
        boundingBox: DesktopVenueElevationBoundingBox,
        datasetId: Int
    ): URI {
        val geoJson = buildString {
            append("""{"type":"Polygon","coordinates":[[[""")
            append(boundingBox.minLongitude.decimal(6))
            append(',')
            append(boundingBox.minLatitude.decimal(6))
            append("],[")
            append(boundingBox.minLongitude.decimal(6))
            append(',')
            append(boundingBox.maxLatitude.decimal(6))
            append("],[")
            append(boundingBox.maxLongitude.decimal(6))
            append(',')
            append(boundingBox.maxLatitude.decimal(6))
            append("],[")
            append(boundingBox.maxLongitude.decimal(6))
            append(',')
            append(boundingBox.minLatitude.decimal(6))
            append("],[")
            append(boundingBox.minLongitude.decimal(6))
            append(',')
            append(boundingBox.minLatitude.decimal(6))
            append("]]]}")
        }
        return URI.create("$WASHINGTON_DNR_DOWNLOAD_URL?geojson=${geoJson.url()}&ids=$datasetId")
    }

    private fun Double.decimal(places: Int): String =
        "%.${places}f".format(Locale.US, this)

    private suspend fun downloadFile(uri: URI, path: Path, sourceName: String) {
        val temporaryPath = path.resolveSibling("${path.fileName}.download")
        Files.deleteIfExists(temporaryPath)
        val request = HttpRequest.newBuilder(uri)
            .header("User-Agent", "Radio-Oracle Desktop")
            .GET()
            .timeout(Duration.ofMinutes(30))
            .build()
        var completed = false
        try {
            val response = sendRequest(request, HttpResponse.BodyHandlers.ofFile(temporaryPath))
            require(response.statusCode() in 200..299) {
                "$sourceName download returned HTTP ${response.statusCode()}."
            }
            require(Files.size(temporaryPath) > 0L) {
                "$sourceName download returned an empty file."
            }
            Files.move(
                temporaryPath,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            completed = true
        } finally {
            if (!completed) {
                runCatching { Files.deleteIfExists(temporaryPath) }
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        Files.walk(path).use { stream ->
            stream
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun isZipArchive(path: Path): Boolean {
        return runCatching {
            Files.newInputStream(path).use { input ->
                val header = ByteArray(4)
                val readCount = input.read(header)
                readCount == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        }.getOrElse { false }
    }

    private fun localElevationSourceType(path: Path): LocalElevationSourceType? =
        desktopLocalElevationSourceType(path.fileName.toString())

    private fun localElevationSources(sourcePathText: String): LocalElevationSources {
        val sourcePaths = desktopLocalElevationSourcePathTexts(sourcePathText)
            .map { Path.of(it).toAbsolutePath().normalize() }
        require(sourcePaths.isNotEmpty()) {
            "Local elevation import requires a source file."
        }
        sourcePaths.forEach { sourcePath ->
            require(Files.isRegularFile(sourcePath)) {
                "Local elevation source file was not found: $sourcePath"
            }
        }
        val sourceTypes = sourcePaths.map { sourcePath ->
            localElevationSourceType(sourcePath)
                ?: throw IllegalArgumentException(
                    "Local elevation source must be a GeoTIFF raster (.tif/.tiff), GeoTIFF ZIP (.zip), " +
                        "or LAS/LAZ point cloud (.las/.laz): $sourcePath"
                )
        }
        val distinctTypes = sourceTypes.distinct()
        require(distinctTypes.size == 1) {
            "Local elevation import cannot mix raster, ZIP, and LAS/LAZ point-cloud sources."
        }
        val sourceType = distinctTypes.single()
        require(sourcePaths.size == 1 || sourceType == LocalElevationSourceType.LasPointCloud) {
            "Only LAS/LAZ point-cloud imports support selecting more than one source file."
        }
        return LocalElevationSources(paths = sourcePaths, type = sourceType)
    }

    internal fun desktopLocalElevationSourcePathTexts(sourcePathText: String): List<String> =
        sourcePathText
            .splitToSequence(Regex("[;\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    internal fun desktopLocalElevationSourceTypes(sourcePathText: String): List<LocalElevationSourceType> =
        desktopLocalElevationSourcePathTexts(sourcePathText).mapNotNull { sourcePath ->
            val fileName = runCatching { Path.of(sourcePath).fileName?.toString() }
                .getOrNull()
                ?: sourcePath
            desktopLocalElevationSourceType(fileName)
        }

    internal fun desktopLocalElevationSourceType(fileName: String): LocalElevationSourceType? {
        val name = fileName.lowercase(Locale.US)
        return when {
            name.endsWith(".zip") ->
                LocalElevationSourceType.GeoTiffZip
            name.endsWith(".tif") || name.endsWith(".tiff") ->
                LocalElevationSourceType.GeoTiff
            name.endsWith(".las") || name.endsWith(".laz") ->
                LocalElevationSourceType.LasPointCloud
            else ->
                null
        }
    }

    private fun localElevationSourceLabel(sourcePaths: List<Path>): String =
        if (sourcePaths.size == 1) {
            sourcePaths.single().fileName.toString()
        } else {
            "${sourcePaths.size} LAS/LAZ files"
        }

    private suspend fun rasterPathForZipArchive(
        workDirectory: Path,
        zipPath: Path,
        gdal: DesktopGdalTools,
        sourceLabel: String
    ): String {
        val rasters = ZipFile(zipPath.toFile()).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .filter {
                    val name = it.lowercase(Locale.US)
                    name.endsWith(".tif") || name.endsWith(".tiff")
                }
                .sorted()
                .toList()
        }
        require(rasters.isNotEmpty()) {
            "$sourceLabel package did not contain GeoTIFF raster files."
        }
        val zipVsiPrefix = "/vsizip/${zipPath.toAbsolutePath().normalize()}"
        if (rasters.size == 1) {
            return "$zipVsiPrefix/${rasters.single()}"
        }
        val inputList = workDirectory.resolve("rasters.txt")
        Files.writeString(inputList, rasters.joinToString(System.lineSeparator()) { "$zipVsiPrefix/$it" })
        val vrt = workDirectory.resolve("dtm.vrt")
        gdal.runCommand(
            listOf(
                gdal.gdalBuildVrt.toString(),
                "-quiet",
                "-overwrite",
                "-input_file_list",
                inputList.toString(),
                vrt.toString()
            )
        )
        return vrt.toString()
    }

    private suspend fun extractRasterArchive(
        workDirectory: Path,
        zipPath: Path,
        gdal: DesktopGdalTools,
        sourceLabel: String
    ): Path {
        val extractDirectory = workDirectory.resolve("extract")
        Files.createDirectories(extractDirectory)
        ZipInputStream(Files.newInputStream(zipPath)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val fileName = Path.of(entry.name).fileName?.toString() ?: continue
                if (entry.isDirectory || fileName.isBlank()) {
                    continue
                }
                val output = extractDirectory.resolve(fileName).normalize()
                require(output.startsWith(extractDirectory)) {
                    "$sourceLabel ZIP entry is outside the extraction directory: ${entry.name}"
                }
                Files.copy(zip, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val rasters = Files.walk(extractDirectory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter {
                    val name = it.fileName.toString().lowercase(Locale.US)
                    name.endsWith(".tif") || name.endsWith(".tiff")
                }
                .sorted()
                .toList()
        }
        require(rasters.isNotEmpty()) {
            "$sourceLabel download package did not contain GeoTIFF raster files."
        }
        if (rasters.size == 1) {
            return rasters.single()
        }
        val inputList = workDirectory.resolve("rasters.txt")
        Files.writeString(inputList, rasters.joinToString(System.lineSeparator()) { it.toAbsolutePath().toString() })
        val vrt = workDirectory.resolve("dtm.vrt")
        gdal.runCommand(
            listOf(
                gdal.gdalBuildVrt.toString(),
                "-quiet",
                "-overwrite",
                "-input_file_list",
                inputList.toString(),
                vrt.toString()
            )
        )
        return vrt
    }

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

internal enum class LocalElevationSourceType {
    GeoTiff,
    GeoTiffZip,
    LasPointCloud
}

private data class LocalElevationSources(
    val paths: List<Path>,
    val type: LocalElevationSourceType
)

private data class DesktopVenueElevationCacheFile(
    val metadata: DesktopVenueElevationCacheMetadata,
    val elevations: List<Double?>,
    @kotlinx.serialization.Transient
    val path: Path = Path.of("")
)

private data class DesktopVenueElevationSpotCheckPoint(
    val row: Int,
    val column: Int,
    val point: CourseGeoPoint
)

private data class WashingtonDnrLidarProject(
    val name: String,
    val datasets: List<WashingtonDnrLidarDataset>
)

private data class WashingtonDnrLidarDataset(
    val id: Int,
    val projectName: String,
    val name: String,
    val boundingBox: DesktopVenueElevationBoundingBox
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
private const val OREGON_DOGAMI_DTM_GET_SAMPLES_URL =
    "https://gis.dogami.oregon.gov/arcgis/rest/services/lidar/DIGITAL_TERRAIN_MODEL_MOSAIC/ImageServer/getSamples"
private const val WASHINGTON_DNR_PROJECT_URL = "https://lidarportal.dnr.wa.gov/project"
private const val WASHINGTON_DNR_DOWNLOAD_URL = "https://lidarportal.dnr.wa.gov/download"
private const val FEET_TO_METERS = 0.3048
private const val US_SURVEY_FOOT_TO_METERS = 1200.0 / 3937.0
private const val GDAL_SAMPLE_PROGRESS_INTERVAL = 5_000

internal data class DesktopGdalElevationUnits(
    val label: String,
    val valueMultiplier: Double
) {
    fun sourceNameSuffix(): String =
        if (valueMultiplier == 1.0) {
            ""
        } else {
            " ($label to meters)"
    }
}

internal fun desktopGdalElevationUnitsFromInfo(
    output: String,
    rasterPath: String = ""
): DesktopGdalElevationUnits {
    Regex("""(?m)^\s*Unit Type:\s*(.+?)\s*$""")
        .find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.let(::desktopGdalElevationUnitsFromUnitText)
        ?.let { return it }

    val lowerOutput = output.lowercase(Locale.US)
    val lowerPath = rasterPath.lowercase(Locale.US)
    val hasUsSurveyFootCrs =
        lowerOutput.contains("projcrs[\"") &&
            (lowerOutput.contains("(ftus)") || lowerOutput.contains("us survey foot") || lowerOutput.contains("\"ftus\""))
    val hasFootCrs =
        lowerOutput.contains("projcrs[\"") &&
            (hasUsSurveyFootCrs || lowerOutput.contains("(ft)") || lowerOutput.contains("foot") || lowerOutput.contains("feet"))
    val pathSuggestsUsSurveyFoot =
        lowerPath.contains("ftus") || lowerPath.contains("us_survey")
    val pathSuggestsFoot =
        pathSuggestsUsSurveyFoot ||
            Regex("""(?i)(^|[/\\_.\-\s])\d{1,2}\s*ft($|[/\\_.\-\s])""").containsMatchIn(rasterPath) ||
            Regex("""(?i)(^|[/\\_.\-\s])ft($|[/\\_.\-\s])""").containsMatchIn(rasterPath)

    return when {
        hasUsSurveyFootCrs || pathSuggestsUsSurveyFoot ->
            DesktopGdalElevationUnits("US survey foot (inferred)", US_SURVEY_FOOT_TO_METERS)
        hasFootCrs || pathSuggestsFoot ->
            DesktopGdalElevationUnits("foot (inferred)", FEET_TO_METERS)
        else ->
            DesktopGdalElevationUnits("unspecified", 1.0)
    }
}

private fun desktopGdalElevationUnitsFromUnitText(unitText: String): DesktopGdalElevationUnits {
    val unit = unitText.trim().lowercase(Locale.US)
    return when {
        unit.contains("us survey foot") || unit == "ftus" ->
            DesktopGdalElevationUnits("US survey foot", US_SURVEY_FOOT_TO_METERS)
        unit.contains("foot") || unit.contains("feet") || unit == "ft" ->
            DesktopGdalElevationUnits("foot", FEET_TO_METERS)
        unit.contains("metre") || unit.contains("meter") || unit == "m" ->
            DesktopGdalElevationUnits("meter", 1.0)
        else ->
            DesktopGdalElevationUnits(unit, 1.0)
    }
}

private fun metersPerDegreeLongitude(latitude: Double): Double =
    METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(latitude))

private fun Double.gdalCoordinateText(): String =
    "%.8f".format(Locale.US, this)

private val desktopGdalJson = Json { ignoreUnknownKeys = true }

private fun findDesktopExecutable(name: String): Path? {
    val candidates = buildList {
        System.getenv("PATH")
            ?.split(java.io.File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?.map { Path.of(it).resolve(name) }
            ?.let(::addAll)
        add(Path.of("/opt/local/bin").resolve(name))
        add(Path.of("/opt/homebrew/bin").resolve(name))
        add(Path.of("/usr/local/bin").resolve(name))
        add(Path.of("/usr/bin").resolve(name))
    }
    return candidates.firstOrNull { Files.isExecutable(it) }
}

private suspend fun Process.waitForCancellable(): Int =
    suspendCancellableCoroutine { continuation ->
        val future = CompletableFuture.supplyAsync {
            waitFor()
        }
        continuation.invokeOnCancellation {
            destroyForcibly()
            future.cancel(true)
        }
        future.whenComplete { exitCode, throwable ->
            if (!continuation.isActive) {
                return@whenComplete
            }
            if (throwable != null) {
                continuation.resumeWithException((throwable as? CompletionException)?.cause ?: throwable)
            } else {
                continuation.resume(exitCode)
            }
        }
    }

private class DesktopGdalTools(
    val gdalBuildVrt: Path,
    private val gdalInfo: Path,
    private val gdalLocationInfo: Path
) {
    suspend fun sampleWgs84(
        rasterPath: Path,
        points: List<CourseGeoPoint>,
        valueMultiplier: Double = 1.0,
        onSampledCount: (Int) -> Unit = {}
    ): List<Double?> {
        return sampleWgs84(rasterPath.toString(), points, valueMultiplier, onSampledCount)
    }

    suspend fun sampleWgs84(
        rasterPath: String,
        points: List<CourseGeoPoint>,
        valueMultiplier: Double = 1.0,
        onSampledCount: (Int) -> Unit = {}
    ): List<Double?> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val process = ProcessBuilder(
            gdalLocationInfo.toString(),
            "-valonly",
            "-E",
            "-field_sep",
            ",",
            "-wgs84",
            "-r",
            "bilinear",
            rasterPath
        )
            .start()
        val errorFuture = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val inputFuture = CompletableFuture.runAsync {
            process.outputStream.bufferedWriter().use { writer ->
                points.forEach { point ->
                    writer.append(point.longitude.gdalCoordinateText())
                    writer.append(' ')
                    writer.append(point.latitude.gdalCoordinateText())
                    writer.newLine()
                }
            }
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                process.destroyForcibly()
            }
        }
        val elevations = MutableList<Double?>(points.size) { null }
        var outputLineCount = 0
        val exitCode = try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (outputLineCount < points.size) {
                        elevations[outputLineCount] = line
                            .split(',')
                            .lastOrNull()
                            ?.trim()
                            ?.takeUnless {
                                it.equals("nan", ignoreCase = true) || it.equals("inf", ignoreCase = true)
                            }
                            ?.toDoubleOrNull()
                            ?.times(valueMultiplier)
                    }
                    outputLineCount += 1
                    if (outputLineCount % GDAL_SAMPLE_PROGRESS_INTERVAL == 0 || outputLineCount == points.size) {
                        onSampledCount(outputLineCount.coerceAtMost(points.size))
                    }
                }
            }
            process.waitForCancellable()
        } finally {
            cancellationHandle?.dispose()
        }
        coroutineContext.ensureActive()
        val error = errorFuture.get()
        runCatching { inputFuture.get() }
            .onFailure { failure ->
                if (exitCode == 0) {
                    throw failure
                }
            }
        require(gdalLocationInfoExitIsAcceptable(exitCode, error, outputLineCount, points.size)) {
            "GDAL gdallocationinfo failed with exit code $exitCode: ${error.trim()}"
        }
        onSampledCount(outputLineCount.coerceAtMost(points.size))
        return elevations
    }

    suspend fun elevationUnits(rasterPath: String): DesktopGdalElevationUnits {
        val output = processOutput(listOf(gdalInfo.toString(), rasterPath))
        return desktopGdalElevationUnitsFromInfo(output, rasterPath)
    }

    suspend fun wgs84BoundingBox(rasterPath: String): DesktopVenueElevationBoundingBox {
        val output = processOutput(listOf(gdalInfo.toString(), "-json", rasterPath))
        return desktopGdalWgs84BoundingBoxFromInfo(output)
    }

    suspend fun runCommand(command: List<String>) {
        processOutput(command)
    }

    private suspend fun processOutput(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val outputFuture = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                process.destroyForcibly()
            }
        }
        val exitCode = try {
            process.waitForCancellable()
        } finally {
            cancellationHandle?.dispose()
        }
        coroutineContext.ensureActive()
        val output = outputFuture.get()
        require(exitCode == 0) {
            "GDAL command failed with exit code $exitCode: ${output.trim()}"
        }
        return output
    }

    companion object {
        fun requireAvailable(): DesktopGdalTools {
            val buildVrt = findDesktopExecutable("gdalbuildvrt")
            val info = findDesktopExecutable("gdalinfo")
            val locationInfo = findDesktopExecutable("gdallocationinfo")
            require(buildVrt != null && info != null && locationInfo != null) {
                "LiDAR raster import requires GDAL command-line tools (gdalbuildvrt, gdalinfo, and gdallocationinfo)."
            }
            return DesktopGdalTools(gdalBuildVrt = buildVrt, gdalInfo = info, gdalLocationInfo = locationInfo)
        }
    }
}

internal fun gdalLocationInfoExitIsAcceptable(
    exitCode: Int,
    error: String,
    outputLineCount: Int,
    pointCount: Int
): Boolean =
    exitCode == 0 ||
        (exitCode == 1 && error.isBlank() && outputLineCount >= pointCount)

private class DesktopPdalTools(
    private val pdal: Path
) {
    suspend fun commonElevationUnits(sourcePaths: List<Path>): DesktopGdalElevationUnits {
        val units = sourcePaths.map { sourcePath -> elevationUnits(sourcePath) }
            .distinctBy { it.label to it.valueMultiplier }
        require(units.size == 1) {
            "Selected LAS/LAZ files must use the same elevation units."
        }
        return units.single()
    }

    private suspend fun elevationUnits(sourcePath: Path): DesktopGdalElevationUnits {
        val output = processOutput(listOf(pdal.toString(), "info", "--metadata", sourcePath.toString()))
        return desktopGdalElevationUnitsFromInfo(output, sourcePath.toString())
    }

    suspend fun wgs84BoundingBox(sourcePaths: List<Path>): DesktopVenueElevationBoundingBox {
        require(sourcePaths.isNotEmpty()) {
            "At least one LAS/LAZ source file is required."
        }
        return sourcePaths
            .map { sourcePath ->
                desktopPdalStacWgs84BoundingBoxFromInfo(
                    processOutput(listOf(pdal.toString(), "info", "--stac", sourcePath.toString()))
                )
            }
            .reduce { combined, boundingBox ->
                DesktopVenueElevationBoundingBox(
                    minLatitude = min(combined.minLatitude, boundingBox.minLatitude),
                    maxLatitude = max(combined.maxLatitude, boundingBox.maxLatitude),
                    minLongitude = min(combined.minLongitude, boundingBox.minLongitude),
                    maxLongitude = max(combined.maxLongitude, boundingBox.maxLongitude)
                )
            }
    }

    suspend fun rasterizeLasPointCloud(
        sourcePaths: List<Path>,
        outputRaster: Path,
        resolutionMeters: Double,
        workDirectory: Path
    ) {
        val pipelinePath = workDirectory.resolve("pdal-laz-to-dem.json")
        Files.writeString(
            pipelinePath,
            desktopPdalLasPointCloudRasterPipeline(
                sourcePaths = sourcePaths,
                outputRaster = outputRaster,
                resolutionMeters = resolutionMeters
            )
        )
        processOutput(listOf(pdal.toString(), "pipeline", pipelinePath.toString()))
    }

    private suspend fun processOutput(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val outputFuture = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                process.destroyForcibly()
            }
        }
        val exitCode = try {
            process.waitForCancellable()
        } finally {
            cancellationHandle?.dispose()
        }
        coroutineContext.ensureActive()
        val output = outputFuture.get()
        require(exitCode == 0) {
            "PDAL command failed with exit code $exitCode: ${output.trim()}"
        }
        return output
    }

    companion object {
        fun requireAvailable(): DesktopPdalTools {
            val pdal = findDesktopExecutable("pdal")
            require(pdal != null) {
                "LAS/LAZ elevation imports require PDAL command-line tools with readers.las and writers.gdal support."
            }
            return DesktopPdalTools(pdal)
        }
    }
}

internal fun desktopPdalLasPointCloudRasterPipeline(
    sourcePaths: List<Path>,
    outputRaster: Path,
    resolutionMeters: Double
): String {
    require(sourcePaths.isNotEmpty()) {
        "At least one LAS/LAZ source file is required."
    }
    val cellSize = resolutionMeters.coerceAtLeast(0.01)
    val radius = (cellSize * sqrt(2.0)).coerceAtLeast(1.0)
    return buildJsonObject {
        put(
            "pipeline",
            buildJsonArray {
                sourcePaths.forEachIndexed { index, sourcePath ->
                    add(
                        buildJsonObject {
                            put("type", "readers.las")
                            put("filename", sourcePath.toString())
                            put("tag", "source_$index")
                        }
                    )
                }
                val reprojectionInput = if (sourcePaths.size == 1) {
                    "source_0"
                } else {
                    add(
                        buildJsonObject {
                            put("type", "filters.merge")
                            put(
                                "inputs",
                                buildJsonArray {
                                    sourcePaths.indices.forEach { index ->
                                        add(JsonPrimitive("source_$index"))
                                    }
                                }
                            )
                            put("tag", "merged_sources")
                        }
                    )
                    "merged_sources"
                }
                add(
                    buildJsonObject {
                        put("type", "filters.reprojection")
                        put("inputs", buildJsonArray { add(JsonPrimitive(reprojectionInput)) })
                        put("out_srs", "EPSG:3857")
                        put("error_on_failure", true)
                    }
                )
                add(
                    buildJsonObject {
                        put("type", "writers.gdal")
                        put("filename", outputRaster.toString())
                        put("gdaldriver", "GTiff")
                        put("resolution", cellSize)
                        put("radius", radius)
                        put("output_type", "idw")
                        put("data_type", "float")
                        put("gdalopts", "COMPRESS=DEFLATE,TILED=YES")
                    }
                )
            }
        )
    }.toString()
}

internal fun desktopGdalWgs84BoundingBoxFromInfo(output: String): DesktopVenueElevationBoundingBox {
    val root = desktopGdalJson.parseToJsonElement(output).jsonObject
    val coordinates = root["wgs84Extent"]
        ?.jsonObject
        ?.get("coordinates")
        ?: throw IllegalArgumentException("GDAL info did not include a WGS84 extent.")
    val points = mutableListOf<CourseGeoPoint>()
    collectWgs84CoordinatePairs(coordinates, points)
    require(points.isNotEmpty()) {
        "GDAL info WGS84 extent did not include coordinate pairs."
    }
    return DesktopVenueElevationBoundingBox(
        minLatitude = points.minOf { it.latitude },
        maxLatitude = points.maxOf { it.latitude },
        minLongitude = points.minOf { it.longitude },
        maxLongitude = points.maxOf { it.longitude }
    )
}

internal fun desktopPdalStacWgs84BoundingBoxFromInfo(output: String): DesktopVenueElevationBoundingBox {
    val jsonStart = output.indexOf('{')
    require(jsonStart >= 0) {
        "PDAL STAC info did not include JSON output."
    }
    val root = desktopGdalJson.parseToJsonElement(output.substring(jsonStart)).jsonObject
    val values = root["bbox"]
        ?.jsonArray
        ?.map { value -> value.jsonPrimitive.doubleOrNull }
        ?: throw IllegalArgumentException("PDAL STAC info did not include a WGS84 bbox.")
    require(values.all { it != null } && (values.size == 4 || values.size == 6)) {
        "PDAL STAC bbox must contain four 2D or six 3D numeric values."
    }
    val minLongitude = values[0] ?: error("Missing minimum longitude.")
    val minLatitude = values[1] ?: error("Missing minimum latitude.")
    val maxLongitude = values[if (values.size == 6) 3 else 2] ?: error("Missing maximum longitude.")
    val maxLatitude = values[if (values.size == 6) 4 else 3] ?: error("Missing maximum latitude.")
    require(minLatitude in -90.0..90.0 && maxLatitude in -90.0..90.0) {
        "PDAL STAC bbox latitude values are outside WGS84 range."
    }
    require(minLongitude in -180.0..180.0 && maxLongitude in -180.0..180.0) {
        "PDAL STAC bbox longitude values are outside WGS84 range."
    }
    return DesktopVenueElevationBoundingBox(
        minLatitude = minLatitude,
        maxLatitude = maxLatitude,
        minLongitude = minLongitude,
        maxLongitude = maxLongitude
    )
}

private fun collectWgs84CoordinatePairs(element: JsonElement, points: MutableList<CourseGeoPoint>) {
    if (element !is JsonArray) {
        return
    }
    val longitude = (element.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
    val latitude = (element.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
    if (longitude != null && latitude != null) {
        points += CourseGeoPoint(latitude = latitude, longitude = longitude)
        return
    }
    element.forEach { child ->
        collectWgs84CoordinatePairs(child, points)
    }
}
