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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class DesktopCloudflarePagesPublishRequest(
    val directory: Path,
    val projectName: String = "openardf-results",
    val branch: String = "main",
    val accountId: String = "",
    val apiToken: String = ""
)

data class DesktopCloudflarePagesPublishResult(
    val projectName: String,
    val branch: String,
    val url: String,
    val output: String
)

internal class DesktopCloudflarePagesPublisher(
    private val transport: DesktopCloudflarePagesHttpTransport = JavaDesktopCloudflarePagesHttpTransport()
) {
    fun publish(request: DesktopCloudflarePagesPublishRequest): DesktopCloudflarePagesPublishResult {
        require(Files.isDirectory(request.directory)) {
            "Public results site directory does not exist: ${request.directory}"
        }
        require(request.projectName.isNotBlank()) {
            "Cloudflare Pages project name is required."
        }
        require(request.branch.isNotBlank()) {
            "Cloudflare Pages branch is required."
        }
        require(request.accountId.isNotBlank()) {
            "Cloudflare account ID is required. Save it in Cloudflare Settings before publishing."
        }
        require(request.apiToken.isNotBlank()) {
            "Cloudflare API token is required. Save it in Cloudflare Settings before publishing."
        }

        val site = DesktopCloudflarePagesSiteReader.read(request.directory)
        val uploadToken = uploadToken(request)
        val missingHashes = missingHashes(uploadToken, site.assets)
        val assetsByHash = site.assets.associateBy(DesktopCloudflarePagesAsset::hash)
        val missingAssets = missingHashes.mapNotNull(assetsByHash::get).distinctBy(DesktopCloudflarePagesAsset::hash)
        cloudflarePagesUploadBuckets(missingAssets).forEach { bucket ->
            uploadAssets(uploadToken, bucket)
        }
        val cacheUpdated = runCatching {
            upsertHashes(uploadToken, site.assets)
        }.isSuccess
        val deployment = createDeployment(request, site)
        val deploymentUrl = deployment.string("url")
        val deploymentId = deployment.string("id")
        val output = buildString {
            append("Uploaded ${missingAssets.size} new content objects for ${site.assets.size} public site files")
            deploymentId?.let { append("; Cloudflare deployment $it created") }
            deploymentUrl?.let { append(" at $it") }
            if (!cacheUpdated) {
                append(". Cloudflare did not update its upload cache, so the next publish may re-upload files")
            }
            append('.')
        }
        return DesktopCloudflarePagesPublishResult(
            projectName = request.projectName,
            branch = request.branch,
            url = "https://${request.projectName}.pages.dev",
            output = output
        )
    }

    private fun uploadToken(request: DesktopCloudflarePagesPublishRequest): String {
        val result = apiResult(
            request = httpRequest(
                method = "GET",
                path = "/accounts/${pathSegment(request.accountId)}/pages/projects/" +
                    "${pathSegment(request.projectName)}/upload-token",
                bearerToken = request.apiToken
            ),
            operation = "Cloudflare Pages upload authorization"
        ).jsonObject
        return result.string("jwt")
            ?: throw IllegalStateException("Cloudflare Pages upload authorization did not return an upload token.")
    }

    private fun missingHashes(
        uploadToken: String,
        assets: List<DesktopCloudflarePagesAsset>
    ): List<String> {
        val result = apiResult(
            request = httpRequest(
                method = "POST",
                path = "/pages/assets/check-missing",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = cloudflarePagesCheckMissingBody(assets)
            ),
            operation = "Cloudflare Pages asset check"
        )
        return (result as? JsonArray)
            ?.mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            ?: throw IllegalStateException("Cloudflare Pages asset check returned an invalid response.")
    }

    private fun uploadAssets(uploadToken: String, assets: List<DesktopCloudflarePagesAsset>) {
        apiResult(
            request = httpRequest(
                method = "POST",
                path = "/pages/assets/upload",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = cloudflarePagesUploadBody(assets)
            ),
            operation = "Cloudflare Pages asset upload"
        )
    }

    private fun upsertHashes(uploadToken: String, assets: List<DesktopCloudflarePagesAsset>) {
        apiResult(
            request = httpRequest(
                method = "POST",
                path = "/pages/assets/upsert-hashes",
                bearerToken = uploadToken,
                contentType = JSON_CONTENT_TYPE,
                body = cloudflarePagesUpsertHashesBody(assets)
            ),
            operation = "Cloudflare Pages upload cache update"
        )
    }

    private fun createDeployment(
        request: DesktopCloudflarePagesPublishRequest,
        site: DesktopCloudflarePagesSite
    ): JsonObject {
        val boundary = "radio-oracle-${UUID.randomUUID()}"
        val parts = buildList {
            add(
                DesktopCloudflarePagesMultipartPart(
                    name = "manifest",
                    content = cloudflarePagesManifest(site.assets).toString().toByteArray(StandardCharsets.UTF_8)
                )
            )
            add(
                DesktopCloudflarePagesMultipartPart(
                    name = "branch",
                    content = request.branch.toByteArray(StandardCharsets.UTF_8)
                )
            )
            site.headersFile?.let { headersFile ->
                add(
                    DesktopCloudflarePagesMultipartPart(
                        name = "_headers",
                        fileName = "_headers",
                        contentType = "text/plain",
                        content = Files.readAllBytes(headersFile)
                    )
                )
            }
            site.redirectsFile?.let { redirectsFile ->
                add(
                    DesktopCloudflarePagesMultipartPart(
                        name = "_redirects",
                        fileName = "_redirects",
                        contentType = "text/plain",
                        content = Files.readAllBytes(redirectsFile)
                    )
                )
            }
        }
        return apiResult(
            request = httpRequest(
                method = "POST",
                path = "/accounts/${pathSegment(request.accountId)}/pages/projects/" +
                    "${pathSegment(request.projectName)}/deployments",
                bearerToken = request.apiToken,
                contentType = "multipart/form-data; boundary=$boundary",
                body = cloudflarePagesMultipartBody(boundary, parts)
            ),
            operation = "Cloudflare Pages deployment"
        ).jsonObject
    }

    private fun apiResult(
        request: DesktopCloudflarePagesHttpRequest,
        operation: String
    ): JsonElement {
        val response = transport.send(request)
        val document = runCatching {
            Json.parseToJsonElement(response.body).jsonObject
        }.getOrNull()
        val success = document?.get("success")?.jsonPrimitive?.booleanOrNull
        if (response.statusCode !in 200..299 || success == false || document == null) {
            throw IllegalStateException(apiFailureMessage(operation, response, document))
        }
        return document["result"] ?: JsonNull
    }

    private fun apiFailureMessage(
        operation: String,
        response: DesktopCloudflarePagesHttpResponse,
        document: JsonObject?
    ): String {
        val errors = (document?.get("errors") as? JsonArray)
            ?.mapNotNull { error ->
                val errorObject = error as? JsonObject ?: return@mapNotNull null
                val message = errorObject.string("message") ?: return@mapNotNull null
                errorObject.string("code")?.let { code -> "$message (code $code)" } ?: message
            }
            .orEmpty()
        val detail = errors.joinToString("; ").ifBlank {
            response.body.trim().take(500).ifBlank { "HTTP ${response.statusCode}" }
        }
        return "$operation failed: $detail"
    }

    private fun httpRequest(
        method: String,
        path: String,
        bearerToken: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): DesktopCloudflarePagesHttpRequest {
        val headers = buildMap {
            put("Accept", JSON_CONTENT_TYPE)
            put("Authorization", "Bearer $bearerToken")
            put("User-Agent", "Radio-Oracle Desktop")
            contentType?.let { put("Content-Type", it) }
        }
        return DesktopCloudflarePagesHttpRequest(
            method = method,
            uri = URI.create("$API_BASE$path"),
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
                ?.takeIf { it.isNotBlank() }
            return normalizedEventPath?.let { "$rootUrl/$it/" } ?: rootUrl
        }

        private fun pathSegment(value: String): String =
            URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20")
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
