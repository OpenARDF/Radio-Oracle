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

package org.openardf.radiooracle.backend.commands

import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFingerprint
import org.openardf.radiooracle.shared.event.EventSeriesPackageFingerprint

object EventSeriesCommandFingerprintLog {
    fun lines(
        source: String,
        byteCount: Int,
        fingerprint: EventSeriesPackageFingerprint
    ): List<String> =
        listOf(
            "series-package source=$source id=${fingerprint.seriesId} " +
                "name=${fingerprint.name} members=${fingerprint.events.size} bytes=$byteCount"
        ) + fingerprint.events.mapIndexed { index, event ->
            eventLine(source, fingerprint.seriesId, index + 1, event)
        }

    private fun eventLine(
        source: String,
        seriesId: String,
        position: Int,
        event: EventSeriesPackageEventFingerprint
    ): String =
        "series-package-member source=$source series=$seriesId position=$position " +
            "event=${event.seriesEventId} order=${event.order} path=${event.eventFilePath} " +
            "display=${event.displayName} start=${event.startDateTimeIso} format=${event.formatLabel} " +
            "race=${event.raceName} raceStart=${event.raceStartDateTimeIso} " +
            "type=${event.raceType} level=${event.raceLevel} band=${event.raceBand} " +
            "timeLimit=${event.timeLimitSeconds} link=${event.seriesLink?.seriesId}/${event.seriesLink?.seriesEventId}"
}
