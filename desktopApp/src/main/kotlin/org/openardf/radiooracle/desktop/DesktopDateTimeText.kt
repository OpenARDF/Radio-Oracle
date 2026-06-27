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

package org.openardf.radiooracle.desktop

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DesktopDateTimeText {
    private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DisplayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy h:mm a", Locale.US)

    fun defaultStartDateTime(): LocalDateTime =
        LocalDateTime.now().withSecond(0).withNano(0)

    fun dateText(value: LocalDateTime): String =
        value.toLocalDate().format(DateFormatter)

    fun timeText(value: LocalDateTime): String =
        value.toLocalTime().withNano(0).format(TimeFormatter)

    fun isoText(value: LocalDateTime): String =
        value.withNano(0).toString()

    fun displayText(value: LocalDateTime): String =
        value.withNano(0).format(DisplayFormatter)

    fun displayIsoOrRaw(value: String): String =
        parseIsoOrNull(value)?.let(::displayText) ?: value

    fun parseIsoOrNull(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value.trim()).withNano(0) }.getOrNull()

    fun parseOrNull(dateText: String, timeText: String): LocalDateTime? =
        runCatching {
            LocalDateTime.of(
                LocalDate.parse(dateText.trim(), DateFormatter),
                LocalTime.parse(normalizedTimeText(timeText.trim()))
            ).withNano(0)
        }.getOrNull()

    private fun normalizedTimeText(value: String): String {
        val parts = value.split(":")
        require(parts.size == 2 || parts.size == 3)
        val padded = parts.map { part ->
            require(part.length in 1..2)
            require(part.all(Char::isDigit))
            part.padStart(2, '0')
        }
        return if (padded.size == 2) {
            "${padded[0]}:${padded[1]}:00"
        } else {
            "${padded[0]}:${padded[1]}:${padded[2]}"
        }
    }
}
