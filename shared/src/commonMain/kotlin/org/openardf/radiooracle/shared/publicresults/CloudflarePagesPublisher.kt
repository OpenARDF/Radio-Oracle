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

package org.openardf.radiooracle.shared.publicresults

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.crypto.digests.Blake3Digest
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

data class CloudflarePagesAsset(
    val relativePath: String,
    val content: ByteArray,
    val contentType: String,
    val hash: String = CloudflarePagesProtocol.assetHash(relativePath, content)
) {
    init {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) {
            "Cloudflare Pages asset paths must be non-blank and relative."
        }
        require(content.size <= CloudflarePagesProtocol.MAX_ASSET_SIZE_BYTES) {
            "Cloudflare Pages supports files up to 25 MiB; $relativePath is ${content.size} bytes."
        }
    }
}

data class CloudflarePagesSite(
    val assets: List<CloudflarePagesAsset>,
    val headersFile: ByteArray? = null,
    val redirectsFile: ByteArray? = null
) {
    init {
        require(assets.isNotEmpty()) {
            "The public results site does not contain any files."
        }
        require(assets.size <= CloudflarePagesProtocol.MAX_ASSET_COUNT) {
            "Cloudflare Pages supports at most ${CloudflarePagesProtocol.MAX_ASSET_COUNT} files per deployment; found ${assets.size}."
        }
        require(assets.map(CloudflarePagesAsset::relativePath).distinct().size == assets.size) {
            "The public results site contains duplicate asset paths."
        }
    }
}

data class CloudflarePagesPublishRequest(
    val projectName: String,
    val branch: String,
    val accountId: String,
    val apiToken: String,
    val userAgent: String = "Radio-Oracle"
) {
    fun normalized(): CloudflarePagesPublishRequest =
        copy(
            projectName = projectName.trim(),
            branch = branch.trim(),
            accountId = accountId.trim(),
            apiToken = apiToken.trim(),
            userAgent = userAgent.trim().ifBlank { "Radio-Oracle" }
        )
}

data class CloudflarePagesPublishResult(
    val projectName: String,
    val branch: String,
    val url: String,
    val output: String
)

data class CloudflarePagesHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null
)

data class CloudflarePagesHttpResponse(
    val statusCode: Int,
    val body: String,
    val bodyBytes: ByteArray = body.toByteArray(Charsets.UTF_8)
)

class CloudflarePagesApiException(
    val operation: String,
    val statusCode: Int,
    detail: String
) : IllegalStateException("$operation failed: $detail")

fun Throwable.isCloudflarePagesSettingsRejection(): Boolean {
    val apiError = generateSequence(this) { it.cause }
        .filterIsInstance<CloudflarePagesApiException>()
        .firstOrNull()
        ?: return false
    return apiError.operation == "Cloudflare Pages upload authorization" &&
        apiError.statusCode in setOf(400, 401, 403, 404)
}

fun interface CloudflarePagesHttpTransport {
    fun send(request: CloudflarePagesHttpRequest): CloudflarePagesHttpResponse
}

/** Shared Cloudflare direct-upload workflow used by desktop and Android. */
class CloudflarePagesPublisher(
    private val transport: CloudflarePagesHttpTransport,
    private val verificationAttempts: Int = 6,
    private val pauseBeforeVerificationRetry: () -> Unit = { Thread.sleep(2_000) }
) {
    init { require(verificationAttempts in 1..30) { "Invalid public verification retry count." } }

    fun publish(
        request: CloudflarePagesPublishRequest,
        site: CloudflarePagesSite
    ): CloudflarePagesPublishResult {
        val normalized = request.normalized()
        require(normalized.projectName.isNotBlank()) {
            "Cloudflare Pages project name is required."
        }
        require(normalized.branch.isNotBlank()) {
            "Cloudflare Pages branch is required."
        }
        require(normalized.accountId.isNotBlank()) {
            "Cloudflare account ID is required. Save it in Cloudflare Settings before publishing."
        }
        require(normalized.apiToken.isNotBlank()) {
            "Cloudflare API token is required. Save it in Cloudflare Settings before publishing."
        }

        val inventory = PublicResultsArtifactVerification.manifest(site)
        val uploadToken = uploadToken(normalized)
        val missingHashes = missingHashes(normalized, uploadToken, site.assets)
        val assetsByHash = site.assets.associateBy(CloudflarePagesAsset::hash)
        val missingAssets = missingHashes
            .mapNotNull(assetsByHash::get)
            .distinctBy(CloudflarePagesAsset::hash)
        CloudflarePagesProtocol.uploadBuckets(missingAssets).forEach { bucket ->
            uploadAssets(normalized, uploadToken, bucket)
        }
        val cacheUpdated = runCatching {
            upsertHashes(normalized, uploadToken, site.assets)
        }.isSuccess
        val deployment = createDeployment(normalized, site)
        val deploymentUrl = deployment.string("url")
        val deploymentId = deployment.string("id")
        val publicUrl = "https://${normalized.projectName}.pages.dev"
        var failed = inventory.artifacts.map { it.path }
        for (attempt in 0 until verificationAttempts) {
            if (attempt > 0) pauseBeforeVerificationRetry()
            failed = PublicResultsArtifactVerification.verify(inventory) { path ->
                val response = transport.send(CloudflarePagesHttpRequest("GET",
                    java.net.URI("$publicUrl/").resolve(java.net.URI(null, null, path, "roverify=${java.util.UUID.randomUUID()}", null)).toString(),
                    mapOf("Cache-Control" to "no-cache, no-store", "Pragma" to "no-cache")))
                require(response.statusCode in 200..299) { "Public artifact is not available." }
                response.bodyBytes
            }
            if (failed.isEmpty()) break
        }
        require(failed.isEmpty()) {
            "Cloudflare accepted deployment ${deploymentId ?: "(identity unavailable)"}, but fresh public verification failed for ${failed.size} of ${inventory.artifacts.size} artifacts. The public site may still be updating; verify it before treating this publication as complete."
        }

        val output = buildString {
            append("Uploaded ${missingAssets.size} new content objects for ${site.assets.size} public site files")
            deploymentId?.let { append("; Cloudflare deployment $it created") }
            deploymentUrl?.let { append(" at $it") }
            if (!cacheUpdated) {
                append(". Cloudflare did not update its upload cache, so the next publish may re-upload files")
            }
            append(". Fresh public downloads verified ${inventory.artifacts.size} artifacts.")
        }
        return CloudflarePagesPublishResult(
            projectName = normalized.projectName,
            branch = normalized.branch,
            url = "https://${normalized.projectName}.pages.dev",
            output = output
        )
    }

    private fun uploadToken(request: CloudflarePagesPublishRequest): String {
        val result = apiResult(
            request = httpRequest(
                request = request,
                method = "GET",
                path = "/accounts/${pathSegment(request.accountId)}/pages/projects/" +
                    "${pathSegment(request.projectName)}/upload-token",
                bearerToken = request.apiToken
            ),
            operation = "Cloudflare Pages upload authorization"
        ).jsonObject
        return result.string("jwt")
            ?: throw IllegalStateException(
                "Cloudflare Pages upload authorization did not return an upload token."
            )
    }

    private fun missingHashes(
        request: CloudflarePagesPublishRequest,
        uploadToken: String,
        assets: List<CloudflarePagesAsset>
    ): List<String> {
        val result = apiResult(
            request = httpRequest(
                request = request,
                method = "POST",
                path = "/pages/assets/check-missing",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = CloudflarePagesProtocol.checkMissingBody(assets)
            ),
            operation = "Cloudflare Pages asset check"
        )
        return (result as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: throw IllegalStateException("Cloudflare Pages asset check returned an invalid response.")
    }

    private fun uploadAssets(
        request: CloudflarePagesPublishRequest,
        uploadToken: String,
        assets: List<CloudflarePagesAsset>
    ) {
        apiResult(
            request = httpRequest(
                request = request,
                method = "POST",
                path = "/pages/assets/upload",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = CloudflarePagesProtocol.uploadBody(assets)
            ),
            operation = "Cloudflare Pages asset upload"
        )
    }

    private fun upsertHashes(
        request: CloudflarePagesPublishRequest,
        uploadToken: String,
        assets: List<CloudflarePagesAsset>
    ) {
        apiResult(
            request = httpRequest(
                request = request,
                method = "POST",
                path = "/pages/assets/upsert-hashes",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = CloudflarePagesProtocol.checkMissingBody(assets)
            ),
            operation = "Cloudflare Pages upload cache update"
        )
    }

    private fun createDeployment(
        request: CloudflarePagesPublishRequest,
        site: CloudflarePagesSite
    ): JsonObject {
        val boundary = "radio-oracle-${UUID.randomUUID()}"
        val parts = buildList {
            add(
                CloudflarePagesMultipartPart(
                    name = "manifest",
                    content = CloudflarePagesProtocol.manifest(site.assets)
                        .toString()
                        .toByteArray(StandardCharsets.UTF_8)
                )
            )
            add(
                CloudflarePagesMultipartPart(
                    name = "branch",
                    content = request.branch.toByteArray(StandardCharsets.UTF_8)
                )
            )
            site.headersFile?.let {
                add(
                    CloudflarePagesMultipartPart(
                        name = "_headers",
                        fileName = "_headers",
                        contentType = "text/plain",
                        content = it
                    )
                )
            }
            site.redirectsFile?.let {
                add(
                    CloudflarePagesMultipartPart(
                        name = "_redirects",
                        fileName = "_redirects",
                        contentType = "text/plain",
                        content = it
                    )
                )
            }
        }
        return apiResult(
            request = httpRequest(
                request = request,
                method = "POST",
                path = "/accounts/${pathSegment(request.accountId)}/pages/projects/" +
                    "${pathSegment(request.projectName)}/deployments",
                bearerToken = request.apiToken,
                contentType = "multipart/form-data; boundary=$boundary",
                body = CloudflarePagesProtocol.multipartBody(boundary, parts)
            ),
            operation = "Cloudflare Pages deployment"
        ).jsonObject
    }

    private fun apiResult(request: CloudflarePagesHttpRequest, operation: String): JsonElement {
        val response = transport.send(request)
        val document = runCatching {
            Json.parseToJsonElement(response.body).jsonObject
        }.getOrNull()
        val success = document?.get("success")?.jsonPrimitive?.booleanOrNull
        if (response.statusCode !in 200..299 || success == false || document == null) {
            throw CloudflarePagesApiException(
                operation = operation,
                statusCode = response.statusCode,
                detail = apiFailureDetail(response, document)
            )
        }
        return document["result"] ?: JsonNull
    }

    private fun apiFailureDetail(
        response: CloudflarePagesHttpResponse,
        document: JsonObject?
    ): String {
        val errors = (document?.get("errors") as? JsonArray)
            ?.mapNotNull { error ->
                val value = error as? JsonObject ?: return@mapNotNull null
                val message = value.string("message") ?: return@mapNotNull null
                value.string("code")?.let { "$message (code $it)" } ?: message
            }
            .orEmpty()
        val detail = errors.joinToString("; ").ifBlank {
            response.body.trim().take(500).ifBlank { "HTTP ${response.statusCode}" }
        }
        return detail
    }

    private fun httpRequest(
        request: CloudflarePagesPublishRequest,
        method: String,
        path: String,
        bearerToken: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): CloudflarePagesHttpRequest {
        val headers = buildMap {
            put("Accept", JSON_CONTENT_TYPE)
            put("Authorization", "Bearer $bearerToken")
            put("User-Agent", request.userAgent)
            contentType?.let { put("Content-Type", it) }
        }
        return CloudflarePagesHttpRequest(
            method = method,
            url = "$API_BASE$path",
            headers = headers,
            body = body
        )
    }

    companion object {
        private const val API_BASE = "https://api.cloudflare.com/client/v4"
        private const val JSON_CONTENT_TYPE = "application/json"

        fun publicResultsUrl(baseUrl: String, eventPath: String?): String {
            val rootUrl = baseUrl.trim().trimEnd('/')
            val normalizedEventPath = eventPath
                ?.trim()
                ?.trim('/')
                ?.takeIf(String::isNotBlank)
            return normalizedEventPath?.let { "$rootUrl/$it/" } ?: rootUrl
        }

        private fun pathSegment(value: String): String =
            URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20")
    }
}

data class CloudflarePagesMultipartPart(
    val name: String,
    val content: ByteArray,
    val fileName: String? = null,
    val contentType: String? = null
)

object CloudflarePagesProtocol {
    const val MAX_ASSET_COUNT = 20_000
    const val MAX_ASSET_SIZE_BYTES = 25 * 1024 * 1024

    fun assetHash(fileNameOrPath: String, content: ByteArray): String {
        // Cloudflare Pages deduplicates Wrangler uploads with the first 16 bytes of
        // BLAKE3(base64(file contents) + file extension).
        val base64Bytes = Base64.getEncoder().encode(content)
        val fileName = fileNameOrPath.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .toByteArray(StandardCharsets.UTF_8)
        val digest = Blake3Digest()
        digest.update(base64Bytes, 0, base64Bytes.size)
        digest.update(extension, 0, extension.size)
        val output = ByteArray(digest.digestSize)
        digest.doFinal(output, 0)
        return output.take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun checkMissingBody(assets: List<CloudflarePagesAsset>): ByteArray =
        JsonObject(
            mapOf(
                "hashes" to JsonArray(
                    assets.map(CloudflarePagesAsset::hash)
                        .distinct()
                        .map(::JsonPrimitive)
                )
            )
        ).toString().toByteArray(StandardCharsets.UTF_8)

    fun uploadBody(assets: List<CloudflarePagesAsset>): ByteArray =
        JsonArray(
            assets.map { asset ->
                JsonObject(
                    mapOf(
                        "key" to JsonPrimitive(asset.hash),
                        "value" to JsonPrimitive(
                            Base64.getEncoder().encodeToString(asset.content)
                        ),
                        "metadata" to JsonObject(
                            mapOf("contentType" to JsonPrimitive(asset.contentType))
                        ),
                        "base64" to JsonPrimitive(true)
                    )
                )
            }
        ).toString().toByteArray(StandardCharsets.UTF_8)

    fun manifest(assets: List<CloudflarePagesAsset>): JsonObject =
        JsonObject(
            assets.associate { "/${it.relativePath}" to JsonPrimitive(it.hash) }
        )

    fun uploadBuckets(
        assets: List<CloudflarePagesAsset>,
        maxBucketBytes: Long = 40L * 1024L * 1024L,
        maxBucketFiles: Int = 2_000
    ): List<List<CloudflarePagesAsset>> {
        val buckets = mutableListOf<MutableList<CloudflarePagesAsset>>()
        var current = mutableListOf<CloudflarePagesAsset>()
        var currentBytes = 0L
        assets.sortedByDescending { it.content.size }.forEach { asset ->
            if (current.isNotEmpty() &&
                (currentBytes + asset.content.size > maxBucketBytes || current.size >= maxBucketFiles)
            ) {
                buckets += current
                current = mutableListOf()
                currentBytes = 0L
            }
            current += asset
            currentBytes += asset.content.size
        }
        if (current.isNotEmpty()) {
            buckets += current
        }
        return buckets
    }

    fun multipartBody(
        boundary: String,
        parts: List<CloudflarePagesMultipartPart>
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
            part.contentType?.let {
                output.write("Content-Type: $it\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
            output.write(part.content)
            output.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        output.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        return output.toByteArray()
    }

    fun contentType(fileNameOrPath: String): String =
        when (fileNameOrPath.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
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

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
