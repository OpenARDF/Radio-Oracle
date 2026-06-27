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

package org.openardf.radiooracle.backend.helpers

import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.shared.time.DurationFormatter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Android-facing time formatting and race-clock helper functions. */
object TimeProcessor {
    /** Formats a date-time as HH:mm for compact display. */
    fun hoursMinutesFormatter(time: LocalDateTime): String {
        return DateTimeFormatter.ofPattern("HH:mm").format(time).toString()
    }

    /** Formats a date-time for human-readable generated text and JSON compatibility. */
    fun formatDisplayLocalDateTime(time: LocalDateTime): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(time).toString()
    }

    /** Formats a date-time with the ISO local date-time formatter. */
    fun formatIsoLocalDateTime(time: LocalDateTime): String {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time).toString()
    }

    /** Formats a date as yyyy-MM-dd. */
    fun formatLocalDate(time: LocalDate): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(time).toString()
    }

    /** Formats a time as HH:mm:ss. */
    fun formatLocalTime(time: LocalTime): String {
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(time).toString()
    }

    /** Converts a duration to mm:ss or HH:mm:ss according to the user's time-format preference. */
    fun durationToFormattedString(
        duration: Duration,
        useMinutes: Boolean
    ): String {
        return DurationFormatter.secondsToFormattedString(duration.seconds, useMinutes)
    }

    /** Parses the app's minute-style duration string into a duration. */
    @Throws(IllegalArgumentException::class)
    fun minuteStringToDuration(string: String): Duration {
        return Duration.ofSeconds(DurationFormatter.minuteStringToSeconds(string))
    }

    /** Converts a competitor start offset into an absolute race date-time. */
    fun getAbsoluteDateTimeFromRelativeTime(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration
    ): LocalDateTime {
        return startDateTime.plusSeconds(relativeStartTime.seconds)
    }


    /** Returns whether the competitor has reached their scheduled start time. */
    fun hasStarted(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration,
        curTime: LocalDateTime
    ): Boolean {
        return (curTime.isAfter(
            getAbsoluteDateTimeFromRelativeTime(
                startDateTime,
                relativeStartTime
            )
        ))
    }

    /** Returns elapsed run time since start, or null if the competitor has not started yet. */
    fun runDurationFromStart(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration,
        curTime: LocalDateTime
    ): Duration? {
        if (hasStarted(startDateTime, relativeStartTime, curTime)) {
            return Duration.between(startDateTime + relativeStartTime, curTime)
        }
        return null
    }

    /** Formats elapsed run time since start, or returns an empty string before the start time. */
    fun runDurationFromStartString(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration,
        dataProcessor: DataProcessor,
        curTime: LocalDateTime
    ): String {
        if (hasStarted(startDateTime, relativeStartTime, curTime)) {
            return durationToFormattedString(
                Duration.between(
                    startDateTime + relativeStartTime,
                    curTime
                ), dataProcessor.useMinuteTimeFormat()
            )
        }
        return ""
    }

    /** Returns whether the current time is still inside the competitor's race time limit. */
    fun isInLimit(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration,
        timeLimit: Duration,
        curTime: LocalDateTime
    ): Boolean {
        return if (hasStarted(startDateTime, relativeStartTime, curTime)) {
            curTime.isBefore(startDateTime.plusSeconds(timeLimit.seconds))
        } else true
    }

    /** Returns remaining time to the competitor's limit, or null before the start time. */
    fun durationToLimit(
        startDateTime: LocalDateTime,
        relativeStartTime: Duration,
        timeLimit: Duration,
        curTime: LocalDateTime
    ): Duration? {
        if (hasStarted(startDateTime, relativeStartTime, curTime)) {
            return Duration.between(
                curTime,
                (startDateTime + relativeStartTime).plusSeconds(timeLimit.seconds)
            )
        }
        return null
    }
}
