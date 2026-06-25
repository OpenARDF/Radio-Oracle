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

        assertEquals("Event Series package is missing Event File entry: events/day-1.rom.json", error.message)
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
