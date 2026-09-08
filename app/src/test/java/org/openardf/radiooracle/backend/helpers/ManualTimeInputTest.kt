package org.openardf.radiooracle.backend.helpers

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ManualTimeInputTest {
    @Test
    fun clockInputAcceptsCompactAndExistingFormats() {
        listOf("123456", "12:34:56", " 123456 ").forEach {
            assertEquals(LocalTime.of(12, 34, 56), TimeProcessor.parseClockInput(it))
        }
        listOf("1234", "12:34").forEach {
            assertEquals(LocalTime.of(12, 34), TimeProcessor.parseClockInput(it))
        }
        assertEquals(LocalTime.MIDNIGHT, TimeProcessor.parseClockInput("000000"))
        assertEquals(LocalTime.of(23, 59, 59), TimeProcessor.parseClockInput("235959"))
    }

    @Test
    fun incompleteOrOutOfRangeClocksAreNotGuessed() {
        listOf("", "1", "123", "12345", "240000", "126000", "123460", "12:3", "abcd").forEach {
            assertTrue(it, runCatching { TimeProcessor.parseClockInput(it) }.isFailure)
        }
    }

    @Test
    fun competitorStartOffsetsRemainMinutesAndSeconds() {
        listOf("12:34", "1234").forEach {
            assertEquals(754L, TimeProcessor.parseMinuteInput(it).seconds)
        }
        assertEquals(754L, TimeProcessor.parseMinuteInput(" 1234 ").seconds)
        assertEquals(90L, TimeProcessor.parseMinuteInput("130").seconds)
        assertEquals(59999L, TimeProcessor.parseMinuteInput("99959").seconds)
        listOf("", "12", "1260", "-123", "12:60").forEach {
            assertTrue(it, runCatching { TimeProcessor.parseMinuteInput(it) }.isFailure)
        }
        // Import/export parsing has not acquired the UI's compact-input convention.
        assertTrue(runCatching { TimeProcessor.minuteStringToDuration("1234") }.isFailure)
    }
}
