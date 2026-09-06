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

package org.openardf.radiooracle.backend.publicresults

import org.openardf.radiooracle.shared.publicresults.CloudflarePagesAsset
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesHttpRequest
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesHttpResponse
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesHttpTransport
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesProtocol
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublishRequest
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublishResult
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublisher
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesSite
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Android filesystem and OkHttp adapter for the shared Cloudflare publishing engine. */
class AndroidCloudflarePagesPublisher(
    private val transport: CloudflarePagesHttpTransport = OkHttpCloudflarePagesTransport()
) {
    fun publish(
        directory: File,
        settings: AndroidCloudflarePagesPublishSettings
    ): CloudflarePagesPublishResult {
        val normalized = settings.normalized()
        return CloudflarePagesPublisher(transport).publish(
            request = CloudflarePagesPublishRequest(
                projectName = normalized.projectName,
                branch = normalized.branch,
                accountId = normalized.accountId,
                apiToken = normalized.apiToken,
                userAgent = "Radio-Oracle Android"
            ),
            site = AndroidCloudflarePagesSiteReader.read(directory)
        )
    }
}

internal object AndroidCloudflarePagesSiteReader {
    fun read(directory: File): CloudflarePagesSite {
        val root = directory.canonicalFile
        require(root.isDirectory) {
            "Public results site directory does not exist: $root"
        }
        val index = root.resolve("index.html")
        val catalog = root.resolve("data/races.json")
        require(index.isFile) {
            "Public results site is missing index.html: $root"
        }
        require(catalog.isFile) {
            "Public results site is missing data/races.json: $root"
        }

        val generatedDirectories = root.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .filter(::isGeneratedSiteDirectory)
        val publicFiles = buildList {
            add(index)
            add(catalog)
            generatedDirectories.forEach { directory ->
                directory.walkTopDown().filter(File::isFile).forEach(::add)
            }
        }.distinctBy { it.canonicalPath }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }

        val assets = publicFiles.map { file ->
            val relativePath = file.relativeTo(root).invariantSeparatorsPath
            CloudflarePagesAsset(
                relativePath = relativePath,
                content = file.readBytes(),
                contentType = CloudflarePagesProtocol.contentType(relativePath)
            )
        }
        return CloudflarePagesSite(
            assets = assets,
            headersFile = root.resolve("_headers").takeIf(File::isFile)?.readBytes(),
            redirectsFile = root.resolve("_redirects").takeIf(File::isFile)?.readBytes()
        )
    }

    private fun isGeneratedSiteDirectory(directory: File): Boolean =
        directory.resolve("index.html").isFile &&
            listOf(
                "event-summary.json",
                "public-results.json",
                "series-results.json"
            ).any { directory.resolve("data/$it").isFile }
}

internal class OkHttpCloudflarePagesTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .callTimeout(3, TimeUnit.MINUTES)
        .followRedirects(true)
        .build()
) : CloudflarePagesHttpTransport {
    override fun send(request: CloudflarePagesHttpRequest): CloudflarePagesHttpResponse {
        val contentType = request.headers["Content-Type"]?.toMediaTypeOrNull()
        val requestBody = request.body?.toRequestBody(contentType)
        val builder = Request.Builder()
            .url(request.url)
            .method(request.method, requestBody)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return client.newCall(builder.build()).execute().use { response ->
            val bytes = response.body.bytes()
            CloudflarePagesHttpResponse(
                statusCode = response.code,
                body = bytes.toString(Charsets.UTF_8),
                bodyBytes = bytes
            )
        }
    }
}
