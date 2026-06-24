package org.openardf.radiooracle.files

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openardf.radiooracle.backend.files.EventSeriesExport
import org.openardf.radiooracle.backend.files.EventSeriesImport
import org.openardf.radiooracle.backend.room.entity.embeddeds.EventSeriesData
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

class EventSeriesExportTests {
    @Test
    fun exportsDesktopImportableSeriesPackage() {
        val originalImport = EventSeriesImport.prepare(
            manifestJson = EventSeriesFileJson.encode(seriesManifest()),
            eventFileJsonByPath = mapOf(
                "events/day-1.rom.json" to eventFileJson("race-day-1", "Day 1", "day-1"),
                "events/day-2.rom.json" to eventFileJson("race-day-2", "Day 2", "day-2")
            )
        )
        val exportedBytes = EventSeriesExport.packageBytes(
            seriesData = EventSeriesData(
                series = originalImport.series,
                members = originalImport.members
            ),
            raceDataById = originalImport.races.associateBy { it.race.id }
        )

        val exportedImport = EventSeriesImport.prepareZipPackage(ByteArrayInputStream(exportedBytes))

        assertEquals("series-2026", exportedImport.series.seriesId)
        assertEquals(listOf("day-1", "day-2"), exportedImport.members.map { it.seriesEventId })
        assertEquals(listOf("Day 1", "Day 2"), exportedImport.races.map { it.race.name })
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

    private fun eventFileJson(raceId: String, raceName: String, seriesEventId: String): String =
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
}
