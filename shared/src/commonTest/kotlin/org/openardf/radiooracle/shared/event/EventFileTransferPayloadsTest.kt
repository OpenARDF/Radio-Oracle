package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventFileTransferPayloadsTest {
    @Test
    fun detectsSeriesPackageByFileExtension() {
        assertTrue(EventFileTransferPayloads.isSeriesPackage("Championship.ZIP", null))
    }

    @Test
    fun detectsSeriesPackageByContentType() {
        assertTrue(EventFileTransferPayloads.isSeriesPackage(null, EVENT_SERIES_PACKAGE_CONTENT_TYPE))
        assertTrue(EventFileTransferPayloads.isSeriesPackage(null, "application/x-zip-compressed"))
    }

    @Test
    fun ignoresSingleEventPayloads() {
        assertFalse(EventFileTransferPayloads.isSeriesPackage("event.rom.json", EVENT_FILE_TRANSFER_CONTENT_TYPE))
        assertFalse(EventFileTransferPayloads.isSeriesPackage("event.ardfjs", "application/json; charset=utf-8"))
        assertFalse(EventFileTransferPayloads.isSeriesPackage(null, null))
    }
}
