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

package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventFileTransferPayloadsTest {
    @Test
    fun detectsSeriesPackageByFileExtension() {
        assertTrue(EventFileTransferPayloads.isSeriesPackage("Championship.ZIP", null))
        assertTrue(EventFileTransferPayloads.isSeriesPackage("Championship.ROSERIES", null))
    }

    @Test
    fun detectsSeriesPackageByContentType() {
        assertTrue(EventFileTransferPayloads.isSeriesPackage(null, EVENT_SERIES_ARCHIVE_CONTENT_TYPE))
        assertTrue(EventFileTransferPayloads.isSeriesPackage(null, "application/x-zip-compressed"))
    }

    @Test
    fun ignoresSingleEventPayloads() {
        assertFalse(EventFileTransferPayloads.isSeriesPackage("event.rom.json", EVENT_FILE_TRANSFER_CONTENT_TYPE))
        assertFalse(EventFileTransferPayloads.isSeriesPackage("event.ardfjs", "application/json; charset=utf-8"))
        assertFalse(EventFileTransferPayloads.isSeriesPackage(null, null))
    }

    @Test
    fun buildsSingleEventTransferMetadata() {
        assertEquals(
            "Day 1 Classic.ardfjs",
            EventFileTransferPayloads.fileNameForRaceOrSeries("Day 1 / Classic", null)
        )
        assertEquals(
            EVENT_FILE_TRANSFER_CONTENT_TYPE,
            EventFileTransferPayloads.contentTypeForRaceOrSeries(null)
        )
    }

    @Test
    fun buildsSeriesPackageTransferMetadata() {
        assertEquals(
            "Championship Week.roseries",
            EventFileTransferPayloads.fileNameForRaceOrSeries("Day 1", "Championship / Week")
        )
        assertEquals(
            EVENT_SERIES_ARCHIVE_CONTENT_TYPE,
            EventFileTransferPayloads.contentTypeForRaceOrSeries("Championship / Week")
        )
    }

    @Test
    fun fallsBackToNeutralSingleEventName() {
        assertEquals("race.ardfjs", EventFileTransferPayloads.singleEventFileName("  "))
    }
}
