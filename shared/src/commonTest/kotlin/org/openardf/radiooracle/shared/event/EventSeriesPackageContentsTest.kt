package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventSeriesPackageContentsTest {
    @Test
    fun buildsManifestFirstThenSortedEventEntries() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship / Week",
            events = listOf(
                EventSeriesEvent("day-2", "events/day-2.rom.json", 1, "Day 2"),
                EventSeriesEvent("day-1", "events/day-1.rom.json", 0, "Day 1")
            )
        )

        val content = EventSeriesPackageContents.build(
            seriesFile = seriesFile,
            eventFiles = listOf(
                EventSeriesPackageEventFile(seriesFile.events[0], projectFile("Day 2")),
                EventSeriesPackageEventFile(seriesFile.events[1], projectFile("Day 1"))
            )
        )

        assertEquals("Championship Week.zip", content.fileName)
        assertEquals(
            listOf(EVENT_SERIES_FILE_NAME, "events/day-1.rom.json", "events/day-2.rom.json"),
            content.entries.map { it.path }
        )
        assertTrue(content.entries[0].text.contains("series-1"))
        assertTrue(content.entries[1].text.contains("Day 1"))
    }

    @Test
    fun buildsPackageForSingleKnownSeriesMember() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Practice",
            events = listOf(EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"))
        )

        val content = EventSeriesPackageContents.build(
            seriesFile = seriesFile,
            eventFiles = listOf(EventSeriesPackageEventFile(seriesFile.events[0], projectFile("Day 1")))
        )

        assertEquals(listOf(EVENT_SERIES_FILE_NAME, "day-1.rom.json"), content.entries.map { it.path })
    }

    @Test
    fun normalizesPackageEntryPaths() {
        assertEquals(
            "series/events/day-1.rom.json",
            EventSeriesPackageContents.normalizedPackagePath("./series\\events/day-1.rom.json")
        )
    }

    @Test
    fun rejectsMissingEventFiles() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventSeriesPackageContents.build(
                seriesFile = seriesFile,
                eventFiles = listOf(EventSeriesPackageEventFile(seriesFile.events[0], projectFile("Day 1")))
            )
        }
    }

    @Test
    fun rejectsUnsafePackagePaths() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventSeriesPackageContents.build(
                seriesFile = seriesFile,
                eventFiles = listOf(
                    EventSeriesPackageEventFile(seriesFile.events[0], projectFile("Day 1")),
                    EventSeriesPackageEventFile(seriesFile.events[1], projectFile("Day 2"))
                ),
                manifestEntryPath = "../series.radio-oracle.json"
            )
        }
    }

    @Test
    fun rejectsAbsolutePackagePaths() {
        val seriesFile = EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = listOf(
                EventSeriesEvent("day-1", "day-1.rom.json", 0, "Day 1"),
                EventSeriesEvent("day-2", "day-2.rom.json", 1, "Day 2")
            )
        )

        assertFailsWith<IllegalArgumentException> {
            EventSeriesPackageContents.build(
                seriesFile = seriesFile,
                eventFiles = listOf(
                    EventSeriesPackageEventFile(seriesFile.events[0], projectFile("Day 1")),
                    EventSeriesPackageEventFile(seriesFile.events[1], projectFile("Day 2"))
                ),
                manifestEntryPath = "/series.radio-oracle.json"
            )
        }
    }

    private fun projectFile(name: String): EventProjectFile =
        EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = name.lowercase().replace(" ", "-"),
                    name = name,
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
            )
        )
}
