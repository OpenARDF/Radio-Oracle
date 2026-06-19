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
        val publisher = DesktopCloudflarePagesPublisher { command, workingDirectory ->
            commands += command
            assertEquals(directory, workingDirectory)
            DesktopCloudflarePagesProcessResult(0, "Uploaded")
        }

        val result = publisher.publish(DesktopCloudflarePagesPublishRequest(directory = directory))

        assertEquals(1, commands.size)
        assertEquals("openardf-results", result.projectName)
        assertEquals("main", result.branch)
        assertEquals("https://openardf-results.pages.dev", result.url)
        assertEquals("Uploaded", result.output)
    }

    @Test
    fun rejectsDirectoryBeforeStartingRunner() {
        val directory = Files.createTempDirectory("rom-public-site-publish-missing")
        val publisher = DesktopCloudflarePagesPublisher { _, _ ->
            throw AssertionError("Runner should not start for incomplete public site.")
        }

        val error = runCatching {
            publisher.publish(DesktopCloudflarePagesPublishRequest(directory = directory))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("index.html"))
    }
}
