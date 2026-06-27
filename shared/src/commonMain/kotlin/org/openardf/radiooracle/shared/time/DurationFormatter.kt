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

package org.openardf.radiooracle.shared.time

/** Shared duration formatting and parsing helpers used by import/export and UI adapters. */
object DurationFormatter {
    /** Formats seconds as either mmm:ss or HH:mm:ss, matching the existing Android behavior. */
    fun secondsToFormattedString(totalSeconds: Long, useMinutes: Boolean): String {
        val absSeconds = kotlin.math.abs(totalSeconds)

        return if (useMinutes) {
            val minutes = totalSeconds / 60
            val seconds = absSeconds % 60

            if (kotlin.math.abs(minutes) <= 99) {
                "%02d:%02d".format(minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        } else {
            val hours = totalSeconds / 3600
            val minutes = (absSeconds % 3600) / 60
            val seconds = absSeconds % 60

            "%02d:%02d:%02d".format(hours, minutes, seconds)
        }
    }

    /** Parses an mmm:ss duration string and returns total seconds. */
    fun minuteStringToSeconds(value: String): Long {
        val parts = value.split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid time format. Expected format: mmm:ss")
        }

        val minutes = parts[0].toLongOrNull() ?: throw IllegalArgumentException("Invalid minutes")
        val seconds = parts[1].toLongOrNull() ?: throw IllegalArgumentException("Invalid seconds")

        if (minutes < 0 || seconds < 0 || seconds >= 60) {
            throw IllegalArgumentException("Invalid time values in input: $value")
        }

        return minutes * 60 + seconds
    }
}
