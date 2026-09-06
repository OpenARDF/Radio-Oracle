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

import org.openardf.radiooracle.shared.publicresults.CloudflarePagesAsset
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesHttpResponse
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesHttpTransport
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublishRequest
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesPublisher
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesSite
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

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

/** Desktop filesystem/HTTP adapter around the shared Cloudflare publishing engine. */
internal class DesktopCloudflarePagesPublisher(
    private val transport: DesktopCloudflarePagesHttpTransport =
        JavaDesktopCloudflarePagesHttpTransport()
) {
    fun publish(request: DesktopCloudflarePagesPublishRequest): DesktopCloudflarePagesPublishResult {
        require(Files.isDirectory(request.directory)) {
            "Public results site directory does not exist: ${request.directory}"
        }
        val desktopSite = DesktopCloudflarePagesSiteReader.read(request.directory)
        val sharedPublisher = CloudflarePagesPublisher(
            CloudflarePagesHttpTransport { sharedRequest ->
                val response = transport.send(
                    DesktopCloudflarePagesHttpRequest(
                        method = sharedRequest.method,
                        uri = URI.create(sharedRequest.url),
                        headers = sharedRequest.headers,
                        body = sharedRequest.body
                    )
                )
                CloudflarePagesHttpResponse(response.statusCode, response.body, response.bodyBytes)
            }
        )
        val result = sharedPublisher.publish(
            request = CloudflarePagesPublishRequest(
                projectName = request.projectName,
                branch = request.branch,
                accountId = request.accountId,
                apiToken = request.apiToken,
                userAgent = "Radio-Oracle Desktop"
            ),
            site = desktopSite.readFrozenSite()
        )
        return DesktopCloudflarePagesPublishResult(
            projectName = result.projectName,
            branch = result.branch,
            url = result.url,
            output = result.output
        )
    }

    companion object {
        fun publicResultsUrl(baseUrl: String, eventPath: String?): String =
            CloudflarePagesPublisher.publicResultsUrl(baseUrl, eventPath)
    }
}

/** Freeze bytes once, checking any file changes against the reader's selected content hashes. */
internal fun DesktopCloudflarePagesSite.readFrozenSite(): CloudflarePagesSite =
CloudflarePagesSite(
                assets = assets.map { asset ->
                    CloudflarePagesAsset(
                        relativePath = asset.relativePath,
                        content = Files.readAllBytes(asset.source),
                        contentType = asset.contentType,
                        hash = asset.hash
                    )
                },
                headersFile = headersFile?.let(Files::readAllBytes),
                redirectsFile = redirectsFile?.let(Files::readAllBytes)
            )
