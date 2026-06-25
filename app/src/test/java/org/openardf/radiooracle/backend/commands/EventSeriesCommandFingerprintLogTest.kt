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
                "series-package-member source=series:series-1 series=series-1 position=1 event=day-1 order=0 " +
                    "path=events/day-1.rom.json display=Day 1 start=2026-06-20T09:00 format=Classic " +
                    "race=Day 1 raceStart=2026-06-20T09:00 type=CLASSIC level=PRACTICE band=M80 " +
                    "timeLimit=7200 link=series-1/day-1"
            ),
            lines
        )
    }
}
