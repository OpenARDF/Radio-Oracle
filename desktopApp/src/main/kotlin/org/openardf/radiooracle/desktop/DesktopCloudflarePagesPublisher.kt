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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

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

class DesktopCloudflarePagesPublisher(
    private val processRunner: (List<String>, Path, Map<String, String>) -> DesktopCloudflarePagesProcessResult =
        DesktopCloudflarePagesPublisher::runProcess
) {
    fun publish(request: DesktopCloudflarePagesPublishRequest): DesktopCloudflarePagesPublishResult {
        require(Files.isDirectory(request.directory)) {
            "Public results site directory does not exist: ${request.directory}"
        }
        require(Files.exists(request.directory.resolve("index.html"))) {
            "Public results site is missing index.html: ${request.directory}"
        }
        require(request.projectName.isNotBlank()) {
            "Cloudflare Pages project name is required."
        }
        require(request.branch.isNotBlank()) {
            "Cloudflare Pages branch is required."
        }

        val processResult = processRunner(commandFor(request), request.directory, environmentFor(request))
        if (processResult.exitCode != 0) {
            throw IllegalStateException(processResult.output.ifBlank {
                "Wrangler exited with status ${processResult.exitCode}."
            })
        }
        return DesktopCloudflarePagesPublishResult(
            projectName = request.projectName,
            branch = request.branch,
            url = "https://${request.projectName}.pages.dev",
            output = processResult.output
        )
    }

    companion object {
        fun commandFor(request: DesktopCloudflarePagesPublishRequest): List<String> =
            listOf(
                "npx",
                "wrangler",
                "pages",
                "deploy",
                request.directory.toAbsolutePath().normalize().toString(),
                "--project-name",
                request.projectName,
                "--branch",
                request.branch
            )

        fun environmentFor(request: DesktopCloudflarePagesPublishRequest): Map<String, String> =
            buildMap {
                if (request.accountId.isNotBlank()) {
                    put("CLOUDFLARE_ACCOUNT_ID", request.accountId.trim())
                }
                if (request.apiToken.isNotBlank()) {
                    put("CLOUDFLARE_API_TOKEN", request.apiToken.trim())
                }
            }

        fun publicResultsUrl(baseUrl: String, eventPath: String?): String {
            val rootUrl = baseUrl.trim().trimEnd('/')
            val normalizedEventPath = eventPath
                ?.trim()
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
            return normalizedEventPath?.let { "$rootUrl/$it/" } ?: rootUrl
        }

        private fun runProcess(
            command: List<String>,
            directory: Path,
            environment: Map<String, String>
        ): DesktopCloudflarePagesProcessResult {
            val outputPath = Files.createTempFile("rom-cloudflare-pages-publish", ".log")
            try {
                val process = ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile())
                    .also { builder -> builder.environment().putAll(environment) }
                    .start()
                val completed = process.waitFor(5, TimeUnit.MINUTES)
                if (!completed) {
                    process.destroyForcibly()
                    throw IllegalStateException("Wrangler publish timed out after 5 minutes.")
                }
                return DesktopCloudflarePagesProcessResult(
                    exitCode = process.exitValue(),
                    output = Files.readString(outputPath, StandardCharsets.UTF_8).trim()
                )
            } finally {
                Files.deleteIfExists(outputPath)
            }
        }
    }
}

data class DesktopCloudflarePagesProcessResult(
    val exitCode: Int,
    val output: String
)
