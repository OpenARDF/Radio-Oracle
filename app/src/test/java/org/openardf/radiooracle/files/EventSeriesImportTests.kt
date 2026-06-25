package org.openardf.radiooracle.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.openardf.radiooracle.backend.files.EventSeriesImport
import org.openardf.radiooracle.shared.domain.ControlPointType
import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.event.EventCategory
import org.openardf.radiooracle.shared.event.EventCategoryData
import org.openardf.radiooracle.shared.event.EventControl
import org.openardf.radiooracle.shared.event.EventControlPoint
import org.openardf.radiooracle.shared.event.EventProjectFile
import org.openardf.radiooracle.shared.event.EventProjectFileJson
import org.openardf.radiooracle.shared.event.EventRace
import org.openardf.radiooracle.shared.event.EventRaceData
import org.openardf.radiooracle.shared.event.EventSeriesEvent
import org.openardf.radiooracle.shared.event.EventSeriesFile
import org.openardf.radiooracle.shared.event.EventSeriesFileJson
import org.openardf.radiooracle.shared.event.EventSeriesLink
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EventSeriesImportTests {
    @Test
    fun preparesSeriesImportFromZipPackage() {
        val packageBytes = zipOf(
            "./series/Championship.series.radio-oracle.json" to EventSeriesFileJson.encode(seriesManifest()),
            "./series/events/day-1.rom.json" to eventFileJson(
                raceId = "race-day-1",
                raceName = "Day 1",
                seriesEventId = "day-1"
            ),
            "./series/events/day-2.rom.json" to eventFileJson(
                raceId = "race-day-2",
                raceName = "Day 2",
                seriesEventId = "day-2"
            )
        )

        val import = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(packageBytes))

        assertEquals("series-2026", import.series.seriesId)
        assertEquals(listOf("day-1", "day-2"), import.members.map { it.seriesEventId })
        assertEquals(listOf("events/day-1.rom.json", "events/day-2.rom.json"), import.members.map { it.eventFilePath })
    }

    @Test
    fun rejectsZipPackageWithoutSeriesManifest() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EventSeriesImport.readZipPackage(
                ByteArrayInputStream(zipOf("events/day-1.rom.json" to "{}"))
            )
        }

        assertEquals("Event Series package does not contain a series manifest.", error.message)
    }

    @Test
    fun rejectsZipPackageWithMultipleSeriesManifests() {
        val manifestJson = EventSeriesFileJson.encode(seriesManifest())
        val error = assertThrows(IllegalArgumentException::class.java) {
            EventSeriesImport.readZipPackage(
                ByteArrayInputStream(
                    zipOf(
                        "series/Championship.series.radio-oracle.json" to manifestJson,
                        "series/Other.series.radio-oracle.json" to manifestJson
                    )
                )
            )
        }

        assertEquals("Event Series package contains more than one manifest.", error.message)
    }

    @Test
    fun rejectsUnsafeZipPackageEntryPath() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EventSeriesImport.readZipPackage(
                ByteArrayInputStream(
                    zipOf("../Championship.series.radio-oracle.json" to EventSeriesFileJson.encode(seriesManifest()))
                )
            )
        }

        assertEquals("Event Series package contains an unsafe path: ../Championship.series.radio-oracle.json", error.message)
    }

    @Test
    fun preparesSeriesImportFromManifestAndEventFiles() {
        val manifest = seriesManifest()
        val import = EventSeriesImport.prepare(
            manifestJson = EventSeriesFileJson.encode(manifest),
            eventFileJsonByPath = mapOf(
                "events/day-2.rom.json" to eventFileJson(
                    raceId = "race-day-2",
                    raceName = "Day 2",
                    seriesEventId = "day-2"
                ),
                "events/day-1.rom.json" to eventFileJson(
                    raceId = "race-day-1",
                    raceName = "Day 1",
                    seriesEventId = "day-1"
                )
            )
        )

        assertEquals("series-2026", import.series.seriesId)
        assertEquals("Championship", import.series.name)
        assertEquals(listOf("day-1", "day-2"), import.members.map { it.seriesEventId })
        assertEquals(listOf("Day 1", "Day 2"), import.races.map { it.race.name })
        assertEquals(import.races.map { it.race.id }, import.members.map { it.localRaceId })
        assertNotEquals(import.races[0].race.id, import.races[1].race.id)
        assertEquals(
            "event-series:series-2026:day-1:event-file:race-day-1",
            import.races[0].race.importSourceId
        )
        assertEquals(64, import.races[0].race.importFingerprint?.length)
    }

    @Test
    fun rejectsMissingManifestMemberFile() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EventSeriesImport.prepare(
                manifestJson = EventSeriesFileJson.encode(seriesManifest()),
                eventFileJsonByPath = mapOf(
                    "events/day-1.rom.json" to eventFileJson(
                        raceId = "race-day-1",
                        raceName = "Day 1",
                        seriesEventId = "day-1"
                    )
                )
            )
        }

        assertEquals("Missing Event File for series event 'Day 2'.", error.message)
    }

    @Test
    fun rejectsContradictoryEventFileBacklink() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EventSeriesImport.prepare(
                manifestJson = EventSeriesFileJson.encode(seriesManifest()),
                eventFileJsonByPath = mapOf(
                    "events/day-1.rom.json" to eventFileJson(
                        raceId = "race-day-1",
                        raceName = "Day 1",
                        seriesEventId = "day-1"
                    ),
                    "events/day-2.rom.json" to eventFileJson(
                        raceId = "race-day-2",
                        raceName = "Day 2",
                        seriesEventId = "different-day"
                    )
                )
            )
        }

        assertEquals("Event File 'Day 2' links to a different Event Series member.", error.message)
    }

    private fun seriesManifest(): EventSeriesFile =
        EventSeriesFile(
            seriesId = "series-2026",
            name = "Championship",
            events = listOf(
                EventSeriesEvent(
                    seriesEventId = "day-1",
                    eventFilePath = "events/day-1.rom.json",
                    order = 0,
                    displayName = "Day 1",
                    startDateTimeIso = "2026-06-20T09:00",
                    formatLabel = "Classic"
                ),
                EventSeriesEvent(
                    seriesEventId = "day-2",
                    eventFilePath = "events/day-2.rom.json",
                    order = 1,
                    displayName = "Day 2",
                    startDateTimeIso = "2026-06-21T09:00",
                    formatLabel = "Sprint"
                )
            )
        )

    private fun eventFileJson(
        raceId: String,
        raceName: String,
        seriesEventId: String
    ): String =
        EventProjectFileJson.encode(
            EventProjectFile(
                seriesLink = EventSeriesLink(seriesId = "series-2026", seriesEventId = seriesEventId),
                raceData = EventRaceData(
                    race = EventRace(
                        id = raceId,
                        name = raceName,
                        apiKey = "",
                        startDateTimeIso = "2026-06-20T09:00",
                        raceType = RaceType.CLASSIC,
                        raceLevel = RaceLevel.PRACTICE,
                        raceBand = RaceBand.M80,
                        timeLimitSeconds = 7_200
                    ),
                    categories = listOf(
                        EventCategoryData(
                            category = EventCategory(
                                id = "$raceId-category",
                                raceId = raceId,
                                name = "M21",
                                isMan = true,
                                maxAge = null,
                                lengthMeters = 5_000,
                                climbMeters = 100,
                                order = 0,
                                differentProperties = false,
                                raceType = null,
                                raceBand = null,
                                timeLimitSeconds = null,
                                controlPointsString = ""
                            ),
                            controlPoints = listOf(
                                EventControlPoint(
                                    id = "$raceId-category-control-1",
                                    categoryId = "$raceId-category",
                                    siCode = 0,
                                    type = ControlPointType.CONTROL,
                                    order = 1,
                                    controlId = "$raceId-control-31"
                                )
                            ),
                            competitors = emptyList(),
                            publicControlIds = listOf("$raceId-control-31")
                        )
                    ),
                    aliases = emptyList(),
                    competitorData = emptyList(),
                    unmatchedReadoutData = emptyList(),
                    controls = listOf(
                        EventControl(
                            id = "$raceId-control-31",
                            raceId = raceId,
                            label = "1",
                            siCode = 31,
                            type = ControlPointType.CONTROL,
                            publicLabel = "FOX 1"
                        )
                    )
                )
            )
        )

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (path, contents) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(contents.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
