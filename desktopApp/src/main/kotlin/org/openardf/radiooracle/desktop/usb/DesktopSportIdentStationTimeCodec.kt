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

package org.openardf.radiooracle.desktop.usb

import java.time.DayOfWeek
import java.time.LocalDateTime

internal data class DesktopSportIdentStationTime(
    val dateTime: LocalDateTime,
    val siDayOfWeek: Int,
    val halfDaySeconds: Int,
    val tick: Int
) {
    val halfDayLabel: String = if (dateTime.hour >= 12) "PM" else "AM"
    val tickNanos: Long = (tick * 1_000_000_000L + 128L) / 256L
    val preciseDateTime: LocalDateTime = dateTime.plusNanos(tickNanos)
}

internal object DesktopSportIdentStationTimeCodec {
    private const val RESPONSE_PREFIX_LENGTH = 2
    private const val STATION_TIME_PAYLOAD_LENGTH = 7
    private const val MAX_HALF_DAY_SECONDS = 12 * 60 * 60

    fun encodePayload(sourceTime: LocalDateTime): ByteArray {
        val normalizedTime = sourceTime.withRoundedSportIdentTick()
        require(normalizedTime.year in 2000..2099) {
            "SPORTident station time payloads store a two-digit year."
        }
        val siDayOfWeek = normalizedTime.dayOfWeek.toSportIdentDayIndex()
        val halfDaySeconds = normalizedTime.toSecondOfDay() % MAX_HALF_DAY_SECONDS
        val dayHalfByte = (siDayOfWeek shl 1) or if (normalizedTime.hour >= 12) 1 else 0

        return byteArrayOf(
            (normalizedTime.year - 2000).toByte(),
            normalizedTime.monthValue.toByte(),
            normalizedTime.dayOfMonth.toByte(),
            dayHalfByte.toByte(),
            ((halfDaySeconds and 0xff00) shr 8).toByte(),
            (halfDaySeconds and 0xff).toByte(),
            normalizedTime.sportIdentTick().toByte()
        )
    }

    fun decodePayload(data: ByteArray): DesktopSportIdentStationTime? {
        val payload = when {
            data.size == STATION_TIME_PAYLOAD_LENGTH -> data
            data.size == STATION_TIME_PAYLOAD_LENGTH + RESPONSE_PREFIX_LENGTH -> data.copyOfRange(
                RESPONSE_PREFIX_LENGTH,
                data.size
            )
            else -> return null
        }

        val year = 2000 + payload[0].toUnsignedInt()
        val month = payload[1].toUnsignedInt()
        val day = payload[2].toUnsignedInt()
        val dayHalf = payload[3].toUnsignedInt()
        val siDayOfWeek = (dayHalf shr 1) and 0x07
        val hourOffset = if (dayHalf and 0x01 == 0x01) 12 else 0
        val halfDaySeconds = (payload[4].toUnsignedInt() shl 8) or payload[5].toUnsignedInt()
        if (halfDaySeconds !in 0 until MAX_HALF_DAY_SECONDS) {
            return null
        }

        val hour = hourOffset + halfDaySeconds / 3600
        val minute = (halfDaySeconds % 3600) / 60
        val second = halfDaySeconds % 60
        return runCatching {
            DesktopSportIdentStationTime(
                dateTime = LocalDateTime.of(year, month, day, hour, minute, second),
                siDayOfWeek = siDayOfWeek,
                halfDaySeconds = halfDaySeconds,
                tick = payload[6].toUnsignedInt()
            )
        }.getOrNull()
    }

    private fun LocalDateTime.toSecondOfDay(): Int =
        hour * 3600 + minute * 60 + second

    private fun LocalDateTime.withRoundedSportIdentTick(): LocalDateTime =
        if (sportIdentTick() == 256) {
            plusSeconds(1).withNano(0)
        } else {
            this
        }

    private fun LocalDateTime.sportIdentTick(): Int =
        ((nano * 256L + 500_000_000L) / 1_000_000_000L).toInt()

    private fun DayOfWeek.toSportIdentDayIndex(): Int =
        when (this) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }

    private fun Byte.toUnsignedInt(): Int = toInt() and 0xff
}
