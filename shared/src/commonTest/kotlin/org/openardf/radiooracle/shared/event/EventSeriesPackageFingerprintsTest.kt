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

package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventSeriesPackageFingerprintsTest {
    @Test
    fun fingerprintsManifestAndMemberEventFiles() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = listOf(
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1")
            )
        )
        val content = EventSeriesPackageContents.build(
            seriesFile = seriesFile,
            eventFiles = seriesFile.events.map { event ->
                EventSeriesPackageEventFile(
                    event = event,
                    projectFile = projectFile(
                        raceId = "race-${event.seriesEventId}",
                        raceName = event.displayName,
                        seriesEventId = event.seriesEventId
                    )
                )
            },
            manifestEntryPath = "Championship.series.radio-oracle.json"
        )

        val fingerprint = EventSeriesPackageFingerprints.fromTextEntries(
            content.entries.associate { it.path to it.text }
        )

        assertEquals("series-1", fingerprint.seriesId)
        assertEquals("Championship", fingerprint.name)
        assertEquals(listOf("day-1", "day-2"), fingerprint.events.map { it.seriesEventId })
        assertEquals(listOf("Day 1", "Day 2"), fingerprint.events.map { it.raceName })
        assertEquals(
            listOf(EventSeriesLink("series-1", "day-1"), EventSeriesLink("series-1", "day-2")),
            fingerprint.events.map { it.seriesLink }
        )
    }

    @Test
    fun rejectsMissingEventFileEntry() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = listOf(EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1"))
        )

        val error = assertFailsWith<IllegalArgumentException> {
            EventSeriesPackageFingerprints.fromTextEntries(
                mapOf("series.radio-oracle.json" to EventSeriesFileJson.encode(seriesFile))
            )
        }

        assertEquals("Race Series package is missing Race File entry: events/day-1.rom.json", error.message)
    }

    private fun projectFile(
        raceId: String,
        raceName: String,
        seriesEventId: String
    ): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = raceId,
                    name = raceName,
                    apiKey = "",
                    startDateTimeIso = "2026-06-25T10:00:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M2,
                    timeLimitSeconds = 3_600
                ),
                categories = emptyList(),
                aliases = emptyList(),
                competitorData = emptyList(),
                unmatchedReadoutData = emptyList()
            ),
            seriesLink = EventSeriesLink("series-1", seriesEventId)
        )
}
