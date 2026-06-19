package org.openardf.radiooracle.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.backend.files.DesktopFileReceiveUrlValidator
import org.openardf.radiooracle.backend.files.EventFileTransferException
import org.openardf.radiooracle.backend.files.EventFileTransferUrlValidator

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
}
