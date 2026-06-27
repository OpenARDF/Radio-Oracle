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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopCloudflarePagesPublisherTest {
    @Test
    fun buildsWranglerDeployCommandForGeneratedSite() {
        val directory = Files.createTempDirectory("rom-public-site-publish")
        val request = DesktopCloudflarePagesPublishRequest(directory = directory)

        assertEquals(
            listOf(
                "npx",
                "wrangler",
                "pages",
                "deploy",
                directory.toAbsolutePath().normalize().toString(),
                "--project-name",
                "openardf-results",
                "--branch",
                "main"
            ),
            DesktopCloudflarePagesPublisher.commandFor(request)
        )
    }

    @Test
    fun publishesGeneratedDirectoryThroughRunner() {
        val directory = Files.createTempDirectory("rom-public-site-publish-runner")
        Files.writeString(directory.resolve("index.html"), "<!doctype html>")
        val commands = mutableListOf<List<String>>()
        val environments = mutableListOf<Map<String, String>>()
        val publisher = DesktopCloudflarePagesPublisher { command, workingDirectory, environment ->
            commands += command
            environments += environment
            assertEquals(directory, workingDirectory)
            DesktopCloudflarePagesProcessResult(0, "Uploaded")
        }

        val result = publisher.publish(
            DesktopCloudflarePagesPublishRequest(
                directory = directory,
                accountId = "account-id",
                apiToken = "api-token"
            )
        )

        assertEquals(1, commands.size)
        assertEquals(
            mapOf(
                "CLOUDFLARE_ACCOUNT_ID" to "account-id",
                "CLOUDFLARE_API_TOKEN" to "api-token"
            ),
            environments.single()
        )
        assertEquals("openardf-results", result.projectName)
        assertEquals("main", result.branch)
        assertEquals("https://openardf-results.pages.dev", result.url)
        assertEquals("Uploaded", result.output)
    }

    @Test
    fun rejectsDirectoryBeforeStartingRunner() {
        val directory = Files.createTempDirectory("rom-public-site-publish-missing")
        val publisher = DesktopCloudflarePagesPublisher { _, _, _ ->
            throw AssertionError("Runner should not start for incomplete public site.")
        }

        val error = runCatching {
            publisher.publish(DesktopCloudflarePagesPublishRequest(directory = directory))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("index.html"))
    }

    @Test
    fun omitsBlankCloudflareEnvironmentValues() {
        val request = DesktopCloudflarePagesPublishRequest(
            directory = Files.createTempDirectory("rom-public-site-publish-env"),
            accountId = " ",
            apiToken = "token"
        )

        assertEquals(
            mapOf("CLOUDFLARE_API_TOKEN" to "token"),
            DesktopCloudflarePagesPublisher.environmentFor(request)
        )
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
}
