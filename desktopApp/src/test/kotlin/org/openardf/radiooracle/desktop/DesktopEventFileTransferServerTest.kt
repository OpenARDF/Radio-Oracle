package org.openardf.radiooracle.desktop

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
import java.util.concurrent.atomic.AtomicReference

class DesktopEventFileTransferServerTest {
    private val address = DesktopEventFileTransferAddress("Local", "127.0.0.1")
    private val client = HttpClient.newHttpClient()

    @Test
    fun correctTokenReturnsEventFileBytesAndStopsServer() {
        val path = eventFile("""{"appName":"Radio-Oracle","raceData":{}}""")
        val server = server(path)
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url)).GET().build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        )

        assertEquals(200, response.statusCode())
        assertEquals("""{"appName":"Radio-Oracle","raceData":{}}""", response.body())
        assertTrue(
            response.headers().firstValue("Content-Type").orElse("").startsWith(
                "application/vnd.openardf.radiooracle.event+json"
            )
        )
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").contains(path.fileName.toString()))

        eventuallyUnavailable(session.url)
    }

    @Test
    fun successfulDownloadReportsDownloadedStopReason() {
        val stopReason = AtomicReference<DesktopEventFileTransferStopReason?>()
        val server = server(eventFile("""{"ok":true}"""), onStopped = stopReason::set)
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url)).GET().build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(200, response.statusCode())
        assertEquals(DesktopEventFileTransferStopReason.Downloaded, awaitStopReason(stopReason))
    }

    @Test
    fun wrongTokenIsRejected() {
        val server = server(eventFile("""{"ok":true}"""))
        val session = server.start(address)

        val response = client.send(
            HttpRequest.newBuilder(URI.create(session.url.replace("test-token", "wrong-token"))).GET().build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(403, response.statusCode())
        server.stop()
    }

    @Test
    fun unknownPathIsRejected() {
        val server = server(eventFile("""{"ok":true}"""))
        val session = server.start(address)
        val url = "http://${session.address.host}:${session.port}/radio-oracle/other?token=${session.token}"

        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.discarding()
        )

        assertEquals(404, response.statusCode())
        server.stop()
    }

    @Test
    fun timeoutShutsServerDown() {
        val server = server(eventFile("""{"ok":true}"""), timeoutMillis = 25)
        val session = server.start(address)

        Thread.sleep(150)

        eventuallyUnavailable(session.url)
    }

    @Test
    fun timeoutReportsTimeoutStopReason() {
        val stopReason = AtomicReference<DesktopEventFileTransferStopReason?>()
        val server = server(eventFile("""{"ok":true}"""), timeoutMillis = 25, onStopped = stopReason::set)

        server.start(address)

        assertEquals(DesktopEventFileTransferStopReason.Timeout, awaitStopReason(stopReason))
    }

    @Test
    fun cancelShutsServerDown() {
        val server = server(eventFile("""{"ok":true}"""))
        val session = server.start(address)

        server.stop()

        eventuallyUnavailable(session.url)
    }

    @Test
    fun generatedUrlIncludesSelectedHostPortPathAndToken() {
        val server = server(eventFile("""{"ok":true}"""))
        val session = server.start(address)

        assertTrue(session.url.startsWith("http://127.0.0.1:${session.port}/radio-oracle/event-file?"))
        assertTrue(session.url.contains("token=test-token"))
        server.stop()
    }

    @Test
    fun addressSortingPrefersPhysicalLanBeforeVirtualBridgeAddresses() {
        val sorted = sortDesktopEventFileTransferAddresses(
            listOf(
                DesktopEventFileTransferAddress("bridge0 (192.168.3.1)", "192.168.3.1", "bridge0"),
                DesktopEventFileTransferAddress("bridge101 (192.168.116.1)", "192.168.116.1", "bridge101"),
                DesktopEventFileTransferAddress("en0 (10.0.4.39)", "10.0.4.39", "en0")
            )
        )

        assertEquals("10.0.4.39", sorted.first().host)
    }

    private fun server(
        path: java.nio.file.Path,
        timeoutMillis: Long = 60_000L,
        onStopped: (DesktopEventFileTransferStopReason) -> Unit = {}
    ): DesktopEventFileTransferServer =
        DesktopEventFileTransferServer(
            filePath = path,
            addressesProvider = { listOf(address) },
            tokenFactory = { "test-token" },
            timeoutMillis = timeoutMillis,
            onStopped = onStopped
        )

    private fun eventFile(text: String): java.nio.file.Path {
        val path = Files.createTempFile("radio-oracle-event", ".rom.json")
        Files.writeString(path, text)
        return path
    }

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

    private fun awaitStopReason(
        stopReason: AtomicReference<DesktopEventFileTransferStopReason?>
    ): DesktopEventFileTransferStopReason {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            stopReason.get()?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("Server stop reason was not reported.")
    }
}
