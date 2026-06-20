package org.openardf.radiooracle.shared.event

import org.openardf.radiooracle.shared.domain.RaceBand
import org.openardf.radiooracle.shared.domain.RaceLevel
import org.openardf.radiooracle.shared.domain.RaceType
import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventSeriesSupportTest {
    @Test
    fun validatesMissingAndMismatchedBacklinks() {
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1)))
        val linkedEvents = listOf(
            linkedEvent("day-1", projectFile("Day 1", seriesLink = EventSeriesLink("series-1", "day-1"))),
            linkedEvent("day-2", projectFile("Day 2", seriesLink = EventSeriesLink("other", "day-2")))
        )

        val issues = EventSeriesSupport.validateLinkedEvents(series, linkedEvents)

        assertEquals(listOf("Event File 'day-2' links to a different series."), issues.map { it.message })
    }

    @Test
    fun validatesDuplicateUnderlyingRaceIdsAcrossLinkedEvents() {
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1)))
        val linkedEvents = listOf(
            linkedEvent(
                "day-1",
                projectFile("Day 1", raceId = "copied-race-id", seriesLink = EventSeriesLink("series-1", "day-1"))
            ),
            linkedEvent(
                "day-2",
                projectFile("Day 2", raceId = "copied-race-id", seriesLink = EventSeriesLink("series-1", "day-2"))
            )
        )

        val issues = EventSeriesSupport.validateLinkedEvents(series, linkedEvents)

        assertEquals(
            listOf(
                "Event File 'day-1' has a duplicate race ID shared with: day-2.",
                "Event File 'day-2' has a duplicate race ID shared with: day-1."
            ),
            issues.map { it.message }
        )
        assertTrue(issues.all { it.severity == EventSeriesIssueSeverity.WARNING })
    }

    @Test
    fun extractsPriorStartRowsInManifestOrder() {
        val series = seriesFile()
        val linkedEvents = listOf(
            linkedEvent(
                "day-1",
                projectFile(
                    "Day 1",
                    competitors = listOf(competitorData("alice", 11, 123456, drawnStartTimeSeconds = 600)),
                    seriesLink = EventSeriesLink("series-1", "day-1")
                )
            ),
            linkedEvent(
                "day-2",
                projectFile(
                    "Day 2",
                    competitors = listOf(competitorData("alice-2", 11, 123456, drawnStartTimeSeconds = 1200)),
                    seriesLink = EventSeriesLink("series-1", "day-2")
                )
            )
        )

        val rows = EventSeriesSupport.priorStartRowsForCurrentEvent(series, linkedEvents, "day-3")

        assertEquals(
            listOf(
                listOf(CompetitorStartCsvImportRow(startNumber = 11, startTimeText = "10:00", siNumber = 123456)),
                listOf(CompetitorStartCsvImportRow(startNumber = 11, startTimeText = "20:00", siNumber = 123456))
            ),
            rows
        )
    }

    @Test
    fun matchesCompetitorsBySiNumberThenStartNumberFallback() {
        val series = seriesFile()
        val from = linkedEvent(
            "day-1",
            projectFile(
                "Day 1",
                competitors = listOf(
                    competitorData("si-source", 11, 123456),
                    competitorData("start-source", 22, null)
                )
            )
        )
        val to = linkedEvent(
            "day-2",
            projectFile(
                "Day 2",
                competitors = listOf(
                    competitorData("si-target", 99, 123456),
                    competitorData("start-target", 22, null)
                )
            )
        )

        val report = EventSeriesSupport.matchCompetitors(series, from, to)

        assertEquals(
            listOf(EventSeriesCompetitorMatchMethod.SI_NUMBER, EventSeriesCompetitorMatchMethod.START_NUMBER),
            report.matches.map { it.method }
        )
        assertEquals(listOf("si-target", "start-target"), report.matches.map { it.toCompetitorId })
    }

    @Test
    fun seriesBalancedDrawUsesSameHistoryAsCsvBalancedDraw() {
        val previousRows = listOf(
            listOf(
                CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "00:00", siNumber = 111),
                CompetitorStartCsvImportRow(startNumber = 2, startTimeText = "10:00", siNumber = 222),
                CompetitorStartCsvImportRow(startNumber = 3, startTimeText = "20:00", siNumber = 333)
            )
        )
        val currentProject = projectFile(
            "Day 2",
            competitors = listOf(
                competitorData("current-1", 1, 111),
                competitorData("current-2", 2, 222),
                competitorData("current-3", 3, 333)
            ),
            seriesLink = EventSeriesLink("series-1", "day-2")
        )
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1)))
        val linkedEvents = listOf(
            linkedEvent(
                "day-1",
                projectFile(
                    "Day 1",
                    competitors = listOf(
                        competitorData("prior-1", 1, 111, drawnStartTimeSeconds = 0),
                        competitorData("prior-2", 2, 222, drawnStartTimeSeconds = 600),
                        competitorData("prior-3", 3, 333, drawnStartTimeSeconds = 1200)
                    ),
                    seriesLink = EventSeriesLink("series-1", "day-1")
                )
            ),
            linkedEvent("day-2", currentProject)
        )

        val csvDraw = EventProjectEditor.drawStartListWithBalancedStartGroups(
            projectFile = currentProject,
            intervalText = "10:00",
            options = StartDrawOptions(seed = "series-test"),
            previousStartLists = previousRows
        )
        val seriesDraw = EventSeriesSupport.drawStartListWithSeriesBalancedStartGroups(
            seriesFile = series,
            linkedEvents = linkedEvents,
            currentSeriesEventId = "day-2",
            currentProjectFile = currentProject,
            intervalText = "10:00",
            options = StartDrawOptions(seed = "series-test")
        )

        assertEquals(csvDraw.preferredGroupsByStartNumber(), seriesDraw.preferredGroupsByStartNumber())
    }

    private fun EventProjectFile.preferredGroupsByStartNumber(): Map<Int, Int?> =
        raceData.competitorData.associate {
            val competitor = it.competitorCategory.competitor
            competitor.startNumber to competitor.preferredStartGroup
        }

    private fun seriesFile(events: List<EventSeriesEvent> = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1), seriesEvent("day-3", 2))): EventSeriesFile =
        EventSeriesFile(seriesId = "series-1", name = "Championship", events = events)

    private fun seriesEvent(id: String, order: Int): EventSeriesEvent =
        EventSeriesEvent(
            seriesEventId = id,
            eventFilePath = "$id.rom.json",
            order = order,
            displayName = id
        )

    private fun linkedEvent(id: String, projectFile: EventProjectFile): EventSeriesLinkedEvent =
        EventSeriesLinkedEvent(seriesEvent(id, when (id) { "day-1" -> 0; "day-2" -> 1; else -> 2 }), projectFile)

    private fun projectFile(
        name: String,
        raceId: String = name,
        competitors: List<EventCompetitorData> = emptyList(),
        seriesLink: EventSeriesLink? = null
    ): EventProjectFile {
        val category = category()
        return EventProjectFile(
            raceData = EventRaceData(
                race = EventRace(
                    id = raceId,
                    name = name,
                    apiKey = "",
                    startDateTimeIso = "2026-06-01T10:00",
                    raceType = RaceType.CLASSIC,
                    raceLevel = RaceLevel.PRACTICE,
                    raceBand = RaceBand.M80,
                    timeLimitSeconds = 7200
                ),
                categories = listOf(categoryData(category)),
                aliases = emptyList(),
                competitorData = competitors.map { data ->
                    data.copy(
                        competitorCategory = data.competitorCategory.copy(category = category)
                    )
                },
                unmatchedReadoutData = emptyList()
            ),
            seriesLink = seriesLink
        )
    }

    private fun category(): EventCategory =
        EventCategory(
            id = "m21",
            raceId = "race",
            name = "M21",
            isMan = true,
            maxAge = null,
            lengthMeters = 5000,
            climbMeters = 120,
            order = 1,
            differentProperties = false,
            raceType = null,
            raceBand = null,
            timeLimitSeconds = null,
            controlPointsString = ""
        )

    private fun categoryData(category: EventCategory): EventCategoryData =
        EventCategoryData(category = category, controlPoints = emptyList(), competitors = emptyList())

    private fun competitorData(
        id: String,
        startNumber: Int,
        siNumber: Int?,
        drawnStartTimeSeconds: Long? = null
    ): EventCompetitorData =
        EventCompetitorData(
            competitorCategory = EventCompetitorCategory(
                competitor = EventCompetitor(
                    id = id,
                    raceId = "race",
                    categoryId = "m21",
                    firstName = id,
                    lastName = "Runner",
                    club = "OPEN",
                    index = "",
                    isMan = true,
                    birthYear = null,
                    siNumber = siNumber,
                    siRent = false,
                    startNumber = startNumber,
                    drawnStartTimeSeconds = drawnStartTimeSeconds
                ),
                category = null
            ),
            readoutData = null
        )
}
