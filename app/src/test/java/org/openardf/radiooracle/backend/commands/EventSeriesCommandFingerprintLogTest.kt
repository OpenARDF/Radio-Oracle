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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.shared.event.EventSeriesLink
import org.openardf.radiooracle.shared.event.EventSeriesPackageEventFingerprint
import org.openardf.radiooracle.shared.event.EventSeriesPackageFingerprint

class EventSeriesCommandFingerprintLogTest {
    @Test
    fun formatsStableSeriesPackageFingerprintLines() {
        val lines = EventSeriesCommandFingerprintLog.lines(
            source = "series:series-1",
            byteCount = 1234,
            fingerprint = EventSeriesPackageFingerprint(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(
                    EventSeriesPackageEventFingerprint(
                        seriesEventId = "day-1",
                        eventFilePath = "events/day-1.rom.json",
                        order = 0,
                        displayName = "Day 1",
                        startDateTimeIso = "2026-06-20T09:00",
                        formatLabel = "Classic",
                        raceName = "Day 1",
                        raceStartDateTimeIso = "2026-06-20T09:00",
                        raceType = "CLASSIC",
                        raceLevel = "PRACTICE",
                        raceBand = "M80",
                        timeLimitSeconds = 7200,
                        seriesLink = EventSeriesLink("series-1", "day-1")
                    )
                ),
                competitorMatchOverrides = emptyList()
            )
        )

        assertEquals(
            listOf(
                "series-package source=series:series-1 id=series-1 name=Championship members=1 bytes=1234",
                "series-package-member source=series:series-1 series=series-1 position=1 seriesRace=day-1 order=0 " +
                    "raceFilePath=events/day-1.rom.json display=Day 1 start=2026-06-20T09:00 format=Classic " +
                    "race=Day 1 raceStart=2026-06-20T09:00 type=CLASSIC level=PRACTICE band=M80 " +
                    "timeLimit=7200 link=series-1/day-1"
            ),
            lines
        )
    }
}
