package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class DesktopPublicResultSitePreviewServerTest {
    @Test
    fun servesStaticPublicSiteFilesOnLoopback() {
        val directory = Files.createTempDirectory("rom-public-site-preview")
        Files.writeString(directory.resolve("index.html"), "<!doctype html><h1>Preview</h1>")
        Files.createDirectories(directory.resolve("data"))
        Files.writeString(directory.resolve("data").resolve("public-results.json"), """{"ok":true}""")

        val server = DesktopPublicResultSitePreviewServer(directory)
        try {
            val url = server.start()
            val indexConnection = URL(url).openConnection() as HttpURLConnection
            val indexHtml = indexConnection.inputStream.bufferedReader().readText()
            val jsonConnection = URL("${url}data/public-results.json").openConnection() as HttpURLConnection
            val json = jsonConnection.inputStream.bufferedReader().readText()

            assertTrue(url.startsWith("http://127.0.0.1:"))
            assertEquals("text/html; charset=utf-8", indexConnection.contentType)
            assertEquals("application/json; charset=utf-8", jsonConnection.contentType)
            assertEquals("no-store", indexConnection.getHeaderField("Cache-Control"))
            assertTrue(indexHtml.contains("Preview"))
            assertEquals("""{"ok":true}""", json)
        } finally {
            server.stop()
        }
    }

    @Test
    fun canAdvertiseSharedAddressForWifiServing() {
        val directory = Files.createTempDirectory("rom-public-site-preview-wifi")
        Files.writeString(directory.resolve("index.html"), "<!doctype html><h1>Shared</h1>")
        val server = DesktopPublicResultSitePreviewServer(
            directory,
            sharedAddress = DesktopEventFileTransferAddress("Test WiFi", "127.0.0.1", "en0")
        )
        try {
            val url = server.start()
            val connection = URL(url).openConnection() as HttpURLConnection
            val indexHtml = connection.inputStream.bufferedReader().readText()

            assertTrue(url.startsWith("http://127.0.0.1:"))
            assertEquals(200, connection.responseCode)
            assertTrue(indexHtml.contains("Shared"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun supportsHeadRequests() {
        val directory = Files.createTempDirectory("rom-public-site-preview-head")
        Files.writeString(directory.resolve("index.html"), "<!doctype html>")
        val server = DesktopPublicResultSitePreviewServer(directory)
        try {
            val connection = URL(server.start()).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"

            assertEquals(200, connection.responseCode)
            assertEquals("text/html; charset=utf-8", connection.contentType)
        } finally {
            server.stop()
        }
    }

    @Test
    fun servesDirectoryIndexFilesForEventFolders() {
        val directory = Files.createTempDirectory("rom-public-site-preview-event")
        Files.writeString(directory.resolve("index.html"), "<!doctype html><h1>Root</h1>")
        Files.createDirectories(directory.resolve("event-one"))
        Files.writeString(directory.resolve("event-one").resolve("index.html"), "<!doctype html><h1>Event</h1>")
        val server = DesktopPublicResultSitePreviewServer(directory)
        try {
            val connection = URL("${server.start()}event-one/").openConnection() as HttpURLConnection
            val eventHtml = connection.inputStream.bufferedReader().readText()

            assertEquals(200, connection.responseCode)
            assertEquals("text/html; charset=utf-8", connection.contentType)
            assertTrue(eventHtml.contains("Event"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsNonGetRequests() {
        val directory = Files.createTempDirectory("rom-public-site-preview-post")
        Files.writeString(directory.resolve("index.html"), "<!doctype html>")
        val server = DesktopPublicResultSitePreviewServer(directory)
        try {
            val connection = URL(server.start()).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"

            assertEquals(405, connection.responseCode)
            assertEquals("GET, HEAD", connection.getHeaderField("Allow"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsMissingFiles() {
        val directory = Files.createTempDirectory("rom-public-site-preview-missing")
        Files.writeString(directory.resolve("index.html"), "<!doctype html>")
        val server = DesktopPublicResultSitePreviewServer(directory)
        try {
            val connection = URL("${server.start()}missing.js").openConnection() as HttpURLConnection

            assertEquals(404, connection.responseCode)
        } finally {
            server.stop()
        }
    }
}
