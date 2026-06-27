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

package org.openardf.radiooracle.backend.files.json.adapters

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Moshi adapter for the legacy and current local date-time formats used in JSON exports. */
class LocalDateTimeAdapter : JsonAdapter<LocalDateTime>() {
    private val legacyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val isoFormatterSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    @FromJson
    override fun fromJson(reader: JsonReader): LocalDateTime? {
        // Moshi can route nullable date fields here, so consume explicit null tokens directly.
        val peek = reader.peek()
        if (peek == JsonReader.Token.NULL) {
            reader.nextNull<Unit>()
            return null
        }

        val string = reader.nextString()
        if (string.isNullOrBlank()) return null

        return try {
            // Prefer ISO-8601 without zone/offset, e.g. 2025-09-20T14:30:00.
            LocalDateTime.parse(string, isoFormatter)
        } catch (_: Exception) {
            try {
                // Accept the pre-multiplatform export format for old race files.
                LocalDateTime.parse(string, legacyFormatter)
            } catch (_: Exception) {
                throw JsonDataException("Invalid date format: $string")
            }
        }
    }

    /** Writes local date-times in the current JSON export format. */
    @ToJson
    override fun toJson(writer: JsonWriter, value: LocalDateTime?) {
        writer.value(value?.format(isoFormatterSeconds))
    }
}
