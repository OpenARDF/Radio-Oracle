package org.openardf.radiooracle.desktop

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object DesktopDateTimeText {
    private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun defaultStartDateTime(): LocalDateTime =
        LocalDateTime.now().withSecond(0).withNano(0)

    fun dateText(value: LocalDateTime): String =
        value.toLocalDate().format(DateFormatter)

    fun timeText(value: LocalDateTime): String =
        value.toLocalTime().withNano(0).format(TimeFormatter)

    fun isoText(value: LocalDateTime): String =
        value.withNano(0).toString()

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
