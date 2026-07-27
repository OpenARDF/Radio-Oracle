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
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesAsset
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesMultipartPart
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesProtocol
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

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
        CloudflarePagesProtocol.contentType(path.fileName.toString())
}

internal fun cloudflarePagesAssetHash(path: Path): String =
    CloudflarePagesProtocol.assetHash(path.fileName.toString(), Files.readAllBytes(path))

internal fun cloudflarePagesCheckMissingBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    CloudflarePagesProtocol.checkMissingBody(assets.map(DesktopCloudflarePagesAsset::toSharedAsset))

internal fun cloudflarePagesUploadBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    CloudflarePagesProtocol.uploadBody(assets.map(DesktopCloudflarePagesAsset::toSharedAsset))

internal fun cloudflarePagesUpsertHashesBody(assets: List<DesktopCloudflarePagesAsset>): ByteArray =
    cloudflarePagesCheckMissingBody(assets)

internal fun cloudflarePagesManifest(assets: List<DesktopCloudflarePagesAsset>): JsonObject =
    CloudflarePagesProtocol.manifest(assets.map(DesktopCloudflarePagesAsset::toSharedAsset))

internal fun cloudflarePagesUploadBuckets(
    assets: List<DesktopCloudflarePagesAsset>,
    maxBucketBytes: Long = 40L * 1024L * 1024L,
    maxBucketFiles: Int = 2_000
): List<List<DesktopCloudflarePagesAsset>> {
    val assetsByHash = assets.associateBy(DesktopCloudflarePagesAsset::hash)
    return CloudflarePagesProtocol.uploadBuckets(
        assets = assets.map(DesktopCloudflarePagesAsset::toSharedAsset),
        maxBucketBytes = maxBucketBytes,
        maxBucketFiles = maxBucketFiles
    ).map { bucket -> bucket.map { assetsByHash.getValue(it.hash) } }
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
): ByteArray =
    CloudflarePagesProtocol.multipartBody(
        boundary,
        parts.map {
            CloudflarePagesMultipartPart(
                name = it.name,
                content = it.content,
                fileName = it.fileName,
                contentType = it.contentType
            )
        }
    )

private fun DesktopCloudflarePagesAsset.toSharedAsset(): CloudflarePagesAsset =
    CloudflarePagesAsset(
        relativePath = relativePath,
        content = Files.readAllBytes(source),
        contentType = contentType,
        hash = hash
    )

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
