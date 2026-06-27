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

package org.openardf.radiooracle.backend.room.database

import androidx.room.TypeConverter
import org.openardf.radiooracle.backend.sportident.SITime
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Room type converters for Java time types and SportIdent time values. */
class DateTimeTypeConverter {
    /** Converts a local date-time to its ISO-8601 string form. */
    @TypeConverter
    fun fromDateTime(date: LocalDateTime): String {
        return date.toString()
    }

    /** Parses a local date-time from its ISO-8601 string form. */
    @TypeConverter
    fun toDateTime(stringDate: String): LocalDateTime {
        return LocalDateTime.parse(stringDate)
    }

    /** Parses a local time from its ISO-8601 string form. */
    @TypeConverter
    fun fromLocalTime(stringTime: String): LocalTime {
        return LocalTime.parse(stringTime)
    }

    /** Converts a local time to its ISO-8601 string form. */
    @TypeConverter
    fun toLocalTime(time: LocalTime): String {
        return time.toString()
    }

    /** Converts a local date to its ISO-8601 string form. */
    @TypeConverter
    fun fromDate(date: LocalDate): String {
        return date.toString()
    }

    /** Parses a local date from its ISO-8601 string form. */
    @TypeConverter
    fun toDate(stringDate: String): LocalDate {
        return LocalDate.parse(stringDate)
    }

    /** Converts a duration to its ISO-8601 string form. */
    @TypeConverter
    fun fromDuration(duration: Duration): String {
        return duration.toString()
    }

    /** Parses a duration from its ISO-8601 string form. */
    @TypeConverter
    fun toDuration(stringDuration: String): Duration {
        return Duration.parse(stringDuration)
    }

    /** Converts SportIdent time to its serialized string form. */
    @TypeConverter
    fun fromSITime(siTime: SITime): String {
        return siTime.toString()
    }

    /** Parses SportIdent time from its serialized string form. */
    @TypeConverter
    fun toSITime(string: String): SITime {
        return SITime.from(string)
    }

}
