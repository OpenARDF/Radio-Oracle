package org.openardf.radiooracle.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.files.EventFileTransferUploads
import org.openardf.radiooracle.shared.event.EVENT_FILE_TRANSFER_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EVENT_SERIES_PACKAGE_CONTENT_TYPE

class EventFileTransferUploadsTest {
    @Test
    fun buildsSingleEventUploadMetadata() {
        val bytes = """{"race":"day-1"}""".toByteArray()

        val upload = EventFileTransferUploads.forRaceOrSeries(
            raceName = "Day 1 / Classic",
            seriesName = null,
            bytes = bytes
        )

        assertEquals("Day 1 Classic.ardfjs", upload.fileName)
        assertEquals(EVENT_FILE_TRANSFER_CONTENT_TYPE, upload.contentType)
        assertArrayEquals(bytes, upload.bytes)
    }

    @Test
    fun buildsSeriesPackageUploadMetadata() {
        val bytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

        val upload = EventFileTransferUploads.forRaceOrSeries(
            raceName = "Day 1",
            seriesName = "Championship / Week",
            bytes = bytes
        )

        assertEquals("Championship Week.zip", upload.fileName)
        assertEquals(EVENT_SERIES_PACKAGE_CONTENT_TYPE, upload.contentType)
        assertArrayEquals(bytes, upload.bytes)
    }
}
