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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EventSeriesArchiveTest {
    @Test
    fun zipCodecRoundTripsCompleteSeriesAggregate() {
        val archive = archive()

        val decoded = EventSeriesArchiveZipCodec.decode(EventSeriesArchiveZipCodec.encode(archive))

        assertEquals(archive.normalizedForStorage(), decoded)
        assertEquals("Championship.roseries", decoded.packageContent().fileName)
    }

    @Test
    fun memberUpdateRefreshesManifestMetadataAndBacklink() {
        val archive = archive()
        val updated = archive.updateMember(
            "day-1",
            projectFile("Renamed Day").copy(seriesLink = null)
        )

        assertEquals("Renamed Day", updated.seriesFile.events.single().displayName)
        assertEquals(EventSeriesLink("series-1", "day-1"), updated.member("day-1").seriesLink)
    }

    @Test
    fun removingMemberDetachesRaceAndPrunesCrossRaceOverrides() {
        val day2 = EventSeriesEvent("day-2", "day-2.json", 1, "Day 2")
        val original = archive().let { archive ->
            archive.copy(
                seriesFile = archive.seriesFile.copy(
                    events = archive.seriesFile.events + day2,
                    competitorMatchOverrides = listOf(
                        EventSeriesCompetitorMatchOverride("day-1", "alice-1", "day-2", "alice-2")
                    )
                ),
                membersBySeriesEventId = archive.membersBySeriesEventId +
                    ("day-2" to projectFile("Day 2"))
            )
        }

        val removal = original.removeMember("day-1")

        assertEquals(listOf("day-2"), removal.remainingArchive?.seriesFile?.events?.map { it.seriesEventId })
        assertEquals(emptyList(), removal.remainingArchive?.seriesFile?.competitorMatchOverrides)
        assertNull(removal.detachedProjectFile.seriesLink)
    }

    @Test
    fun rejectsArchiveWhoseMembersDoNotMatchManifest() {
        assertFailsWith<IllegalArgumentException> {
            EventSeriesArchive(
                seriesFile = archive().seriesFile,
                membersBySeriesEventId = emptyMap()
            )
        }
    }

    @Test
    fun rejectsDuplicateManifestRaceIdsAndPaths() {
        val original = archive()
        val originalEvent = original.seriesFile.events.single()

        assertFailsWith<IllegalArgumentException> {
            EventSeriesArchive(
                seriesFile = original.seriesFile.copy(
                    events = listOf(originalEvent, originalEvent.copy(order = 1))
                ),
                membersBySeriesEventId = original.membersBySeriesEventId
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EventSeriesArchive(
                seriesFile = original.seriesFile.copy(
                    events = listOf(
                        originalEvent,
                        originalEvent.copy(seriesEventId = "day-2", order = 1)
                    )
                ),
                membersBySeriesEventId = original.membersBySeriesEventId +
                    ("day-2" to projectFile("Day 2"))
            )
        }
    }

    @Test
    fun rejectsRaceFilesNotListedByManifest() {
        val original = archive()
        val entries = original.packageContent().entries +
            EventSeriesPackageTextEntry(
                path = "unlisted.json",
                text = EventProjectFileJson.encode(projectFile("Unlisted"))
            )

        assertFailsWith<IllegalArgumentException> {
            EventSeriesArchiveZipCodec.decodeEntries(entries)
        }
    }

    @Test
    fun rejectsDuplicateArchiveEntryPaths() {
        val bytes = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                listOf(EVENT_SERIES_FILE_NAME, "./$EVENT_SERIES_FILE_NAME").forEach { path ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(EventSeriesFileJson.encode(archive().seriesFile).toByteArray())
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

        assertFailsWith<IllegalArgumentException> {
            EventSeriesArchiveZipCodec.decode(bytes)
        }
    }

    private fun archive(): EventSeriesArchive {
        val event = EventSeriesEvent("day-1", "day-1.json", 0, "Day 1")
        return EventSeriesArchive(
            seriesFile = EventSeriesFile(
                seriesId = "series-1",
                name = "Championship",
                events = listOf(event)
            ),
            membersBySeriesEventId = mapOf("day-1" to projectFile("Day 1"))
        )
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
