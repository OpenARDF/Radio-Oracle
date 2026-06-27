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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

class DesktopAndroidFileReceiveServerTest {
    private val address = DesktopEventFileTransferAddress("Local", "127.0.0.1")
    private val client = HttpClient.newHttpClient()

    @Test
    fun correctTokenStoresUploadedFileAndStopsServer() {
        val directory = Files.createTempDirectory("radio-oracle-receive")
        var received: DesktopAndroidFileReceiveResult? = null
        val server = server(directory) { received = it }
        val session = server.start(address)
        val body = """{"race_name":"Test"}""".toByteArray(StandardCharsets.UTF_8)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url))
                .header("X-Radio-Oracle-Filename-B64", encodedFileName("Test Event.ardfjs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(201, response.statusCode())
        val result = awaitReceived { received }
        assertEquals("Test Event.ardfjs", result.fileName)
        assertEquals(body.size.toLong(), result.byteCount)
        assertArrayEquals(body, Files.readAllBytes(result.path))
        eventuallyUnavailable(session.url)
    }

    @Test
    fun wrongTokenIsRejected() {
        val server = server(Files.createTempDirectory("radio-oracle-receive"))
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url.replace("test-token", "wrong-token")))
                .header("X-Radio-Oracle-Filename-B64", encodedFileName("race.ardfjs"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(403, response.statusCode())
        server.stop()
    }

    @Test
    fun duplicateFileNamesAreMadeUnique() {
        val directory = Files.createTempDirectory("radio-oracle-receive")
        Files.writeString(directory.resolve("race.ardfjs"), "existing")
        var received: DesktopAndroidFileReceiveResult? = null
        val server = server(directory) { received = it }
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url))
                .header("X-Radio-Oracle-Filename-B64", encodedFileName("race.ardfjs"))
                .POST(HttpRequest.BodyPublishers.ofString("new"))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(201, response.statusCode())
        assertEquals("race 2.ardfjs", awaitReceived { received }.fileName)
    }

    @Test
    fun oversizedUploadIsRejected() {
        val server = server(Files.createTempDirectory("radio-oracle-receive"), maxUploadBytes = 2)
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url))
                .header("X-Radio-Oracle-Filename-B64", encodedFileName("race.ardfjs"))
                .POST(HttpRequest.BodyPublishers.ofString("123"))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(413, response.statusCode())
        server.stop()
    }

    @Test
    fun receiveDirectoryIsUnderDocumentsRadioOracle() {
        val root = Files.createTempDirectory("radio-oracle-home")

        val directory = DesktopAndroidFileReceiveLocations.receiveDirectory(root)

        assertTrue(directory.endsWith("Documents/Radio-Oracle/received-from-android"))
    }

    private fun server(
        directory: java.nio.file.Path,
        maxUploadBytes: Long = 60_000L,
        onReceived: (DesktopAndroidFileReceiveResult) -> Unit = {}
    ): DesktopAndroidFileReceiveServer =
        DesktopAndroidFileReceiveServer(
            receiveDirectory = directory,
            addressesProvider = { listOf(address) },
            tokenFactory = { "test-token" },
            timeoutMillis = 60_000L,
            maxUploadBytes = maxUploadBytes,
            onReceived = onReceived
        )

    private fun encodedFileName(fileName: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(fileName.toByteArray(StandardCharsets.UTF_8))

    private fun eventuallyUnavailable(url: String) {
        val deadline = System.nanoTime() + 2_000_000_000L
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()
                )
            } catch (error: ConnectException) {
                return
            } catch (error: java.io.IOException) {
                return
            } catch (error: Throwable) {
                lastError = error
            }
            Thread.sleep(25)
        }
        throw AssertionError("Server remained available.", lastError)
    }

    private fun awaitReceived(
        resultProvider: () -> DesktopAndroidFileReceiveResult?
    ): DesktopAndroidFileReceiveResult {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            resultProvider()?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("Receive callback was not invoked.")
    }
}
