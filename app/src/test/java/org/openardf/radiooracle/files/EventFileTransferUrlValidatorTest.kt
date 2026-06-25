package org.openardf.radiooracle.files

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload
import org.openardf.radiooracle.backend.files.DesktopFileTransferUploader
import org.openardf.radiooracle.backend.files.DesktopFileReceiveUrlValidator
import org.openardf.radiooracle.backend.files.EventFileTransferDownloader
import org.openardf.radiooracle.backend.files.EventFileTransferException
import org.openardf.radiooracle.backend.files.EventFileTransferUrlValidator
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

class EventFileTransferUrlValidatorTest {
    @Test
    fun acceptsPrivateIpv4TransferUrls() {
        val url = "http://192.168.1.10:49321/radio-oracle/event-file?token=abc123"

        assertEquals(url, EventFileTransferUrlValidator.validate(url))
    }

    @Test
    fun acceptsLocalHostnames() {
        val url = "http://radio-oracle.local:49321/radio-oracle/event-file?token=abc123"

        assertEquals(url, EventFileTransferUrlValidator.validate(url))
    }

    @Test
    fun rejectsHttpsUrls() {
        assertThrows(EventFileTransferException::class.java) {
            EventFileTransferUrlValidator.validate("https://192.168.1.10/radio-oracle/event-file?token=abc123")
        }
    }

    @Test
    fun rejectsPublicHosts() {
        assertThrows(EventFileTransferException::class.java) {
            EventFileTransferUrlValidator.validate("http://example.com/radio-oracle/event-file?token=abc123")
        }
    }

    @Test
    fun rejectsMissingToken() {
        assertThrows(EventFileTransferException::class.java) {
            EventFileTransferUrlValidator.validate("http://10.0.0.5:49321/radio-oracle/event-file")
        }
    }

    @Test
    fun acceptsPrivateIpv4DesktopReceiveUrls() {
        val url = "http://192.168.1.10:49321/radio-oracle/file-receive?token=abc123"

        assertEquals(url, DesktopFileReceiveUrlValidator.validate(url))
    }

    @Test
    fun desktopReceiveRejectsEventFileDownloadUrls() {
        assertThrows(EventFileTransferException::class.java) {
            DesktopFileReceiveUrlValidator.validate("http://192.168.1.10:49321/radio-oracle/event-file?token=abc123")
        }
    }

    @Test
    fun desktopReceiveRejectsPublicHosts() {
        assertThrows(EventFileTransferException::class.java) {
            DesktopFileReceiveUrlValidator.validate("http://example.com/radio-oracle/file-receive?token=abc123")
        }
    }

    @Test
    fun downloaderPreservesZipBytesFileNameAndContentType() {
        val bytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", EVENT_SERIES_PACKAGE_CONTENT_TYPE)
                .setHeader("Content-Disposition", "attachment; filename=\"Series.zip\"")
                .setBody(okio.Buffer().write(bytes))
        )
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val download = EventFileTransferDownloader().download(
                server.url("/radio-oracle/event-file?token=abc123").toString()
            )

            assertEquals("Series.zip", download.fileName)
            assertEquals(EVENT_SERIES_PACKAGE_CONTENT_TYPE, download.contentType)
            assertTrue(download.isZip)
            assertEquals(bytes.toList(), download.bytes.toList())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun uploaderPostsBytesContentTypeAndEncodedFileName() {
        val bytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(201))
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            DesktopFileTransferUploader().upload(
                rawUrl = server.url("/radio-oracle/file-receive?token=abc123").toString(),
                upload = DesktopFileTransferUpload(
                    fileName = "Series.zip",
                    contentType = EVENT_SERIES_PACKAGE_CONTENT_TYPE,
                    bytes = bytes
                )
            )

            val request = server.takeRequest(1, TimeUnit.SECONDS)
                ?: throw AssertionError("Upload request was not received.")
            assertEquals("POST", request.method)
            assertEquals(EVENT_SERIES_PACKAGE_CONTENT_TYPE, request.getHeader("Content-Type"))
            assertEquals(
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("Series.zip".toByteArray(StandardCharsets.UTF_8)),
                request.getHeader("X-Radio-Oracle-Filename-B64")
            )
            assertEquals(bytes.toList(), request.body.readByteArray().toList())
        } finally {
            server.shutdown()
        }
    }
}
