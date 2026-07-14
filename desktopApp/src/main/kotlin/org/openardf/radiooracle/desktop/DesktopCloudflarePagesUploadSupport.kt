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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.bouncycastle.crypto.digests.Blake3Digest
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

internal data class DesktopCloudflarePagesAsset(
    val relativePath: String,
    val source: Path,
    val sizeInBytes: Long,
    val contentType: String,
    val hash: String
)

internal data class DesktopCloudflarePagesSite(
    val root: Path,
    val assets: List<DesktopCloudflarePagesAsset>,
    val headersFile: Path?,
    val redirectsFile: Path?
)

internal object DesktopCloudflarePagesSiteReader {
    private const val MAX_ASSET_COUNT = 20_000
    private const val MAX_ASSET_SIZE_BYTES = 25L * 1024L * 1024L

    fun read(directory: Path): DesktopCloudflarePagesSite {
        val root = directory.toAbsolutePath().normalize()
        val indexHtml = root.resolve("index.html")
        val rootDataDirectory = root.resolve("data")
        val racesJson = rootDataDirectory.resolve("races.json")
        require(isSafeRegularFile(indexHtml)) {
            "Public results site is missing index.html: $root"
        }
        require(isSafeRegularFile(racesJson)) {
            "Public results site is missing data/races.json: $root"
        }

        val generatedDirectories = generatedSiteDirectories(root)
        val listedDirectories = listedSiteDirectories(root, racesJson)
        listedDirectories.forEach { listedDirectory ->
            require(listedDirectory in generatedDirectories) {
                "Public results site references a missing or incomplete event directory: ${root.relativize(listedDirectory)}"
            }
        }

        // The export chooser may point at an existing event folder. Publish only files
        // created by the public-site exporter, never arbitrary siblings in that folder.
        val publicRoots = buildList {
            add(indexHtml)
            add(racesJson)
            addAll(generatedDirectories)
        }
        val publicFiles = publicRoots
            .flatMap(::regularFiles)
            .distinct()
            .sortedBy { root.relativize(it).toString() }
        require(publicFiles.isNotEmpty()) {
            "Public results site does not contain any files to publish: $root"
        }
        require(publicFiles.size <= MAX_ASSET_COUNT) {
            "Cloudflare Pages supports at most $MAX_ASSET_COUNT files per deployment; found ${publicFiles.size}."
        }

        val assets = publicFiles.map { path -> asset(root, path) }
        return DesktopCloudflarePagesSite(
            root = root,
            assets = assets,
            headersFile = root.resolve("_headers").takeIf(::isSafeRegularFile),
            redirectsFile = root.resolve("_redirects").takeIf(::isSafeRegularFile)
        )
    }

    private fun generatedSiteDirectories(root: Path): Set<Path> =
        Files.list(root).use { entries ->
            entries
                .filter(::isSafeDirectory)
                .filter(::isGeneratedSiteDirectory)
                .map { it.toAbsolutePath().normalize() }
                .toList()
                .toSet()
        }

    private fun isGeneratedSiteDirectory(directory: Path): Boolean {
        if (!isSafeRegularFile(directory.resolve("index.html"))) {
            return false
        }
        val dataDirectory = directory.resolve("data")
        return isSafeDirectory(dataDirectory) && listOf(
            "event-summary.json",
            "public-results.json",
            "series-results.json"
        ).any { isSafeRegularFile(dataDirectory.resolve(it)) }
    }

    private fun listedSiteDirectories(root: Path, racesJson: Path): Set<Path> {
        val document = runCatching {
            Json.parseToJsonElement(Files.readString(racesJson, StandardCharsets.UTF_8)).jsonObject
        }.getOrElse { error ->
            throw IllegalArgumentException("Public results site has invalid data/races.json: ${error.message}", error)
        }
        val races = document["races"] as? JsonArray
            ?: throw IllegalArgumentException("Public results site data/races.json is missing the races array.")
        require(races.isNotEmpty()) {
            "Public results site data/races.json does not list any races or series."
        }
        return races.map { element ->
            val pathText = (element as? JsonObject)
                ?.get("path")
                ?.let { it as? JsonPrimitive }
                ?.content
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Public results site data/races.json contains an invalid path.")
            val resolved = root.resolve(pathText).normalize()
            require(resolved.parent == root && resolved != root) {
                "Public results site contains an unsafe event path: $pathText"
            }
            resolved
        }.toSet()
    }

    private fun regularFiles(path: Path): List<Path> {
        if (isSafeRegularFile(path)) {
            return listOf(path.toAbsolutePath().normalize())
        }
        if (!isSafeDirectory(path)) {
            return emptyList()
        }
        return Files.walk(path).use { entries ->
            entries
                .filter { candidate -> Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate) }
                .map { it.toAbsolutePath().normalize() }
                .toList()
        }
    }

    private fun isSafeRegularFile(path: Path): Boolean =
        !Files.isSymbolicLink(path) && Files.isRegularFile(path)

    private fun isSafeDirectory(path: Path): Boolean =
        !Files.isSymbolicLink(path) && Files.isDirectory(path)

    private fun asset(root: Path, path: Path): DesktopCloudflarePagesAsset {
        val size = Files.size(path)
        require(size <= MAX_ASSET_SIZE_BYTES) {
            "Cloudflare Pages supports files up to 25 MiB; ${root.relativize(path)} is $size bytes."
        }
        val relativePath = root.relativize(path).joinToString("/") { it.toString() }
        return DesktopCloudflarePagesAsset(
            relativePath = relativePath,
            source = path,
            sizeInBytes = size,
            contentType = contentType(path),
            hash = cloudflarePagesAssetHash(path)
        )
    }

    private fun contentType(path: Path): String =
        when (path.fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "css" -> "text/css"
            "csv" -> "text/csv"
            "gif" -> "image/gif"
            "html", "htm" -> "text/html"
            "ico" -> "image/x-icon"
            "jpeg", "jpg" -> "image/jpeg"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "kml" -> "application/vnd.google-earth.kml+xml"
            "png" -> "image/png"
            "svg" -> "image/svg+xml"
            "txt" -> "text/plain"
            "webp" -> "image/webp"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
}

internal fun cloudflarePagesAssetHash(path: Path): String {
    // Cloudflare Pages deduplicates Wrangler uploads with the first 16 bytes of
    // BLAKE3(base64(file contents) + file extension). Keep native uploads compatible.
    val fileBytes = Files.readAllBytes(path)
    val base64Bytes = Base64.getEncoder().encode(fileBytes)
    val fileName = path.fileName.toString()
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .toByteArray(StandardCharsets.UTF_8)
    val digest = Blake3Digest()
    digest.update(base64Bytes, 0, base64Bytes.size)
    digest.update(extension, 0, extension.size)
    val output = ByteArray(digest.digestSize)
    digest.doFinal(output, 0)
    return output.take(16).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun cloudflarePagesCheckMissingBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    JsonObject(
        mapOf(
            "hashes" to JsonArray(
                assets.map(DesktopCloudflarePagesAsset::hash)
                    .distinct()
                    .map(::JsonPrimitive)
            )
        )
    ).toString().toByteArray(StandardCharsets.UTF_8)

internal fun cloudflarePagesUploadBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    JsonArray(
        assets.map { asset ->
            JsonObject(
                mapOf(
                    "key" to JsonPrimitive(asset.hash),
                    "value" to JsonPrimitive(
                        Base64.getEncoder().encodeToString(Files.readAllBytes(asset.source))
                    ),
                    "metadata" to JsonObject(
                        mapOf("contentType" to JsonPrimitive(asset.contentType))
                    ),
                    "base64" to JsonPrimitive(true)
                )
            )
        }
    ).toString().toByteArray(StandardCharsets.UTF_8)

internal fun cloudflarePagesUpsertHashesBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    cloudflarePagesCheckMissingBody(assets)

internal fun cloudflarePagesManifest(assets: List<DesktopCloudflarePagesAsset>): JsonObject =
    JsonObject(
        assets.associate { asset -> "/${asset.relativePath}" to JsonPrimitive(asset.hash) }
    )

internal fun cloudflarePagesUploadBuckets(
    assets: List<DesktopCloudflarePagesAsset>,
    maxBucketBytes: Long = 40L * 1024L * 1024L,
    maxBucketFiles: Int = 2_000
): List<List<DesktopCloudflarePagesAsset>> {
    val buckets = mutableListOf<MutableList<DesktopCloudflarePagesAsset>>()
    var current = mutableListOf<DesktopCloudflarePagesAsset>()
    var currentBytes = 0L
    assets.sortedByDescending(DesktopCloudflarePagesAsset::sizeInBytes).forEach { asset ->
        if (current.isNotEmpty() &&
            (currentBytes + asset.sizeInBytes > maxBucketBytes || current.size >= maxBucketFiles)
        ) {
            buckets += current
            current = mutableListOf()
            currentBytes = 0L
        }
        current += asset
        currentBytes += asset.sizeInBytes
    }
    if (current.isNotEmpty()) {
        buckets += current
    }
    return buckets
}

internal data class DesktopCloudflarePagesMultipartPart(
    val name: String,
    val content: ByteArray,
    val fileName: String? = null,
    val contentType: String? = null
)

internal fun cloudflarePagesMultipartBody(
    boundary: String,
    parts: List<DesktopCloudflarePagesMultipartPart>
): ByteArray {
    val output = ByteArrayOutputStream()
    parts.forEach { part ->
        output.write("--$boundary\r\n".toByteArray(StandardCharsets.UTF_8))
        val disposition = buildString {
            append("Content-Disposition: form-data; name=\"")
            append(part.name)
            append('"')
            part.fileName?.let { append("; filename=\"$it\"") }
            append("\r\n")
        }
        output.write(disposition.toByteArray(StandardCharsets.UTF_8))
        part.contentType?.let { contentType ->
            output.write("Content-Type: $contentType\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        output.write(part.content)
        output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
    }
    output.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
    return output.toByteArray()
}

internal data class DesktopCloudflarePagesHttpRequest(
    val method: String,
    val uri: URI,
    val headers: Map<String, String>,
    val body: ByteArray? = null
)

internal data class DesktopCloudflarePagesHttpResponse(
    val statusCode: Int,
    val body: String
)

internal fun interface DesktopCloudflarePagesHttpTransport {
    fun send(request: DesktopCloudflarePagesHttpRequest): DesktopCloudflarePagesHttpResponse
}

internal class JavaDesktopCloudflarePagesHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) : DesktopCloudflarePagesHttpTransport {
    override fun send(request: DesktopCloudflarePagesHttpRequest): DesktopCloudflarePagesHttpResponse {
        val bodyPublisher = request.body
            ?.let(HttpRequest.BodyPublishers::ofByteArray)
            ?: HttpRequest.BodyPublishers.noBody()
        val builder = HttpRequest.newBuilder(request.uri)
            .timeout(Duration.ofMinutes(2))
            .method(request.method, bodyPublisher)
        request.headers.forEach(builder::header)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        return DesktopCloudflarePagesHttpResponse(response.statusCode(), response.body())
    }
}
