package org.openardf.radiooracle.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class DesktopCloudflarePagesPublishRequest(
    val directory: Path,
    val projectName: String = "openardf-results",
    val branch: String = "main"
)

data class DesktopCloudflarePagesPublishResult(
    val projectName: String,
    val branch: String,
    val url: String,
    val output: String
)

class DesktopCloudflarePagesPublisher(
    private val processRunner: (List<String>, Path) -> DesktopCloudflarePagesProcessResult =
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

        val processResult = processRunner(commandFor(request), request.directory)
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

        private fun runProcess(command: List<String>, directory: Path): DesktopCloudflarePagesProcessResult {
            val outputPath = Files.createTempFile("rom-cloudflare-pages-publish", ".log")
            try {
                val process = ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile())
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
