package org.openardf.radiooracle.shared.event

import kotlin.test.Test
import kotlin.test.assertEquals
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
            "Championship Week.zip",
            EventFileTransferPayloads.fileNameForRaceOrSeries("Day 1", "Championship / Week")
        )
        assertEquals(
            EVENT_SERIES_PACKAGE_CONTENT_TYPE,
            EventFileTransferPayloads.contentTypeForRaceOrSeries("Championship / Week")
        )
    }

    @Test
    fun fallsBackToNeutralSingleEventName() {
        assertEquals("race.ardfjs", EventFileTransferPayloads.singleEventFileName("  "))
    }
}
