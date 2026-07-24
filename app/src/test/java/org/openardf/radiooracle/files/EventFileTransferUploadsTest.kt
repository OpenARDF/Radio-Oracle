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

package org.openardf.radiooracle.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.files.EventFileTransferUploads
import org.openardf.radiooracle.shared.event.EVENT_FILE_TRANSFER_CONTENT_TYPE
import org.openardf.radiooracle.shared.event.EVENT_SERIES_ARCHIVE_CONTENT_TYPE

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

        assertEquals("Championship Week.roseries", upload.fileName)
        assertEquals(EVENT_SERIES_ARCHIVE_CONTENT_TYPE, upload.contentType)
        assertArrayEquals(bytes, upload.bytes)
    }
}
