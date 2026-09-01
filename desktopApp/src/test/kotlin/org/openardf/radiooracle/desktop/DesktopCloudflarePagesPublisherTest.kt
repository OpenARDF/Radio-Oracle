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
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openardf.radiooracle.shared.publicresults.CloudflarePagesApiException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DesktopCloudflarePagesPublisherTest {
    @Test
    fun publishesGeneratedSiteThroughCloudflarePagesApi() {
        val directory = generatedSiteDirectory()
        Files.writeString(directory.resolve("private-race-file.json"), "do-not-upload")
        val requests = mutableListOf<DesktopCloudflarePagesHttpRequest>()
        val publisher = DesktopCloudflarePagesPublisher(
            DesktopCloudflarePagesHttpTransport { request ->
                requests += request
                when {
                    request.uri.path.endsWith("/upload-token") ->
                        response("""{"jwt":"upload-jwt"}""")
                    request.uri.path == "/client/v4/pages/assets/check-missing" -> {
                        val hashes = Json.parseToJsonElement(
                            String(requireNotNull(request.body), StandardCharsets.UTF_8)
                        ).jsonObject.getValue("hashes")
                        response(hashes.toString())
                    }
                    request.uri.path == "/client/v4/pages/assets/upload" -> response("null")
                    request.uri.path == "/client/v4/pages/assets/upsert-hashes" -> response("null")
                    request.uri.path.endsWith("/deployments") ->
                        response(
                            """{"id":"deployment-id","url":"https://deployment.openardf-results.pages.dev"}"""
                        )
                    else -> error("Unexpected request: ${request.method} ${request.uri}")
                }
            }
        )

        val result = publisher.publish(publishRequest(directory))

        assertEquals(5, requests.size)
        assertEquals("GET", requests[0].method)
        assertEquals("Bearer api-token", requests[0].headers["Authorization"])
        assertEquals("Bearer upload-jwt", requests[1].headers["Authorization"])
        assertEquals("Bearer upload-jwt", requests[2].headers["Authorization"])
        assertEquals("Bearer upload-jwt", requests[3].headers["Authorization"])
        assertEquals("Bearer api-token", requests[4].headers["Authorization"])

        val uploadBody = String(requireNotNull(requests[2].body), StandardCharsets.UTF_8)
        assertTrue(uploadBody.contains("\"contentType\":\"application/json\""))
        assertTrue(uploadBody.contains("e30="))
        assertFalse(uploadBody.contains("do-not-upload".encodeBase64()))

        val deploymentBody = String(requireNotNull(requests[4].body), StandardCharsets.UTF_8)
        assertTrue(deploymentBody.contains("/index.html"))
        assertTrue(deploymentBody.contains("/data/races.json"))
        assertTrue(deploymentBody.contains("/2026-07-11-practice/index.html"))
        assertTrue(deploymentBody.contains("name=\"_headers\""))
        assertFalse(deploymentBody.contains("private-race-file.json"))

        assertEquals("openardf-results", result.projectName)
        assertEquals("main", result.branch)
        assertEquals("https://openardf-results.pages.dev", result.url)
        assertTrue(result.output.contains("new content objects for 5 public site files"))
        assertTrue(result.output.contains("deployment-id"))
    }

    @Test
    fun publishesOnlyGeneratedPublicSiteDirectories() {
        val directory = generatedSiteDirectory()
        val unrelatedDirectory = directory.resolve("Course Files")
        Files.createDirectories(unrelatedDirectory.resolve("data"))
        Files.writeString(unrelatedDirectory.resolve("index.html"), "private")
        Files.writeString(unrelatedDirectory.resolve("data/source.json"), "private")
        Files.writeString(directory.resolve("event.radio-oracle.json"), "private")
        Files.writeString(directory.resolve("data/private.json"), "private")

        val site = DesktopCloudflarePagesSiteReader.read(directory)
        val paths = site.assets.map(DesktopCloudflarePagesAsset::relativePath)

        assertEquals(
            listOf(
                "2026-07-11-practice/data/event-summary.json",
                "2026-07-11-practice/data/public-results.json",
                "2026-07-11-practice/index.html",
                "data/races.json",
                "index.html"
            ),
            paths.sorted()
        )
        assertEquals(directory.resolve("_headers"), site.headersFile)
    }

    @Test
    fun matchesWranglerBlake3AssetHash() {
        val directory = Files.createTempDirectory("rom-cloudflare-hash")
        val htmlPath = directory.resolve("index.html")
        val pngPath = directory.resolve("pixel.png")
        Files.writeString(htmlPath, "<!doctype html>")
        Files.write(pngPath, byteArrayOf(0, 1, 2, -1))

        assertEquals("9a43f62b308ef20fc63d0b5eab6dbd1a", cloudflarePagesAssetHash(htmlPath))
        assertEquals("a6886b27dd94ea1d072fabbbdc6c73d3", cloudflarePagesAssetHash(pngPath))
    }

    @Test
    fun rejectsIncompleteGeneratedSiteBeforeCallingCloudflare() {
        val directory = Files.createTempDirectory("rom-public-site-publish-missing")
        Files.writeString(directory.resolve("index.html"), "<!doctype html>")
        val publisher = DesktopCloudflarePagesPublisher(
            DesktopCloudflarePagesHttpTransport {
                throw AssertionError("Cloudflare should not be called for an incomplete public site.")
            }
        )

        val error = runCatching {
            publisher.publish(publishRequest(directory))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("data/races.json"))
    }

    @Test
    fun requiresCloudflareCredentialsBeforePublishing() {
        val directory = generatedSiteDirectory()
        val publisher = DesktopCloudflarePagesPublisher(
            DesktopCloudflarePagesHttpTransport {
                throw AssertionError("Cloudflare should not be called without credentials.")
            }
        )

        val accountError = runCatching {
            publisher.publish(
                DesktopCloudflarePagesPublishRequest(
                    directory = directory,
                    accountId = "",
                    apiToken = "token"
                )
            )
        }.exceptionOrNull()
        val tokenError = runCatching {
            publisher.publish(
                DesktopCloudflarePagesPublishRequest(
                    directory = directory,
                    accountId = "account",
                    apiToken = ""
                )
            )
        }.exceptionOrNull()

        assertTrue(accountError!!.message!!.contains("account ID"))
        assertTrue(tokenError!!.message!!.contains("API token"))
    }

    @Test
    fun reportsCloudflareApiErrorsWithoutExposingToken() {
        val directory = generatedSiteDirectory()
        val publisher = DesktopCloudflarePagesPublisher(
            DesktopCloudflarePagesHttpTransport {
                DesktopCloudflarePagesHttpResponse(
                    statusCode = 403,
                    body = """{"success":false,"errors":[{"code":9109,"message":"Invalid access token"}]}"""
                )
            }
        )

        val error = runCatching {
            publisher.publish(publishRequest(directory))
        }.exceptionOrNull()

        assertTrue(error is CloudflarePagesApiException)
        assertEquals(403, (error as CloudflarePagesApiException).statusCode)
        assertEquals("Cloudflare Pages upload authorization", error.operation)
        assertTrue(error.message!!.contains("Invalid access token"))
        assertTrue(error.message!!.contains("9109"))
        assertFalse(error.message!!.contains("api-token"))
        assertTrue(cloudflarePagesSettingsRejectionReason(error)!!.contains("rejected"))
    }

    @Test
    fun buildsPublishedPublicResultsEventUrl() {
        assertEquals(
            "https://openardf-results.pages.dev/2026-05-31-desktop-smoke-race/",
            DesktopCloudflarePagesPublisher.publicResultsUrl(
                "https://openardf-results.pages.dev/",
                "/2026-05-31-desktop-smoke-race/"
            )
        )
        assertEquals(
            "https://openardf-results.pages.dev",
            DesktopCloudflarePagesPublisher.publicResultsUrl(
                "https://openardf-results.pages.dev/",
                " "
            )
        )
    }

    @Test
    fun settingsCreateNormalizedPublishRequest() {
        val directory = Files.createTempDirectory("rom-public-site-publish-settings")
        val settings = DesktopCloudflarePagesPublishSettings(
            projectName = " openardf-results ",
            branch = " main ",
            accountId = " account ",
            apiToken = " token "
        )

        assertEquals(
            DesktopCloudflarePagesPublishRequest(
                directory = directory,
                projectName = "openardf-results",
                branch = "main",
                accountId = "account",
                apiToken = "token"
            ),
            settings.request(directory)
        )
    }

    @Test
    fun settingsAreCompleteOnlyWhenCloudflareCredentialsArePresent() {
        assertFalse(DesktopCloudflarePagesPublishSettings().isComplete())
        assertFalse(
            DesktopCloudflarePagesPublishSettings(
                accountId = "account",
                apiToken = " "
            ).isComplete()
        )

        val settings = DesktopCloudflarePagesPublishSettings(
            projectName = " openardf-results ",
            branch = " main ",
            accountId = " account ",
            apiToken = " token "
        )

        assertTrue(settings.isComplete())
        assertEquals("https://openardf-results.pages.dev", settings.publicSiteBaseUrl())
    }

    @Test
    fun publicResultsActionsRequireCompleteSettingsAndHonorKnownRejection() {
        val incomplete = DesktopCloudflarePagesPublishSettings()
        val complete = incomplete.copy(accountId = "account", apiToken = "token")
        val rejected = "Cloudflare rejected the saved settings."

        assertTrue(cloudflarePagesSettingsDisabledReason(incomplete, null)!!.contains("complete"))
        assertEquals(null, cloudflarePagesSettingsDisabledReason(complete, null))
        assertEquals(rejected, cloudflarePagesSettingsDisabledReason(complete, rejected))
        assertEquals(
            null,
            cloudflarePagesSettingsRejectionReason(
                CloudflarePagesApiException(
                    operation = "Cloudflare Pages asset upload",
                    statusCode = 403,
                    detail = "Forbidden"
                )
            )
        )
    }

    private fun generatedSiteDirectory(): Path {
        val root = Files.createTempDirectory("rom-public-site-publish")
        val eventDirectory = root.resolve("2026-07-11-practice")
        Files.createDirectories(root.resolve("data"))
        Files.createDirectories(eventDirectory.resolve("data"))
        Files.writeString(root.resolve("index.html"), "<!doctype html>")
        Files.writeString(
            root.resolve("data/races.json"),
            """{"races":[{"path":"2026-07-11-practice"}]}"""
        )
        Files.writeString(eventDirectory.resolve("index.html"), "<!doctype html>")
        Files.writeString(eventDirectory.resolve("data/event-summary.json"), "{}")
        Files.writeString(eventDirectory.resolve("data/public-results.json"), "{}")
        Files.writeString(root.resolve("_headers"), "/*\n  X-Content-Type-Options: nosniff\n")
        return root
    }

    private fun publishRequest(directory: Path): DesktopCloudflarePagesPublishRequest =
        DesktopCloudflarePagesPublishRequest(
            directory = directory,
            projectName = "openardf-results",
            branch = "main",
            accountId = "account-id",
            apiToken = "api-token"
        )

    private fun response(resultJson: String): DesktopCloudflarePagesHttpResponse =
        DesktopCloudflarePagesHttpResponse(
            statusCode = 200,
            body = """{"success":true,"errors":[],"messages":[],"result":$resultJson}"""
        )

    private fun String.encodeBase64(): String =
        java.util.Base64.getEncoder().encodeToString(toByteArray(StandardCharsets.UTF_8))
}
