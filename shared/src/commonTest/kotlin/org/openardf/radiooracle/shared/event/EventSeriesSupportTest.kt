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
import org.openardf.radiooracle.shared.files.CompetitorStartCsvImportRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventSeriesSupportTest {
    @Test
    fun exposesSharedCompetitorIdentityKeysInSeriesPriorityOrder() {
        val competitor = competitorData(
            id = "competitor-1",
            startNumber = 1,
            siNumber = 123456,
            bibNumber = "b-7",
            callSign = "k0abc"
        ).competitorCategory.competitor

        val identities = EventSeriesSupport.competitorIdentities(competitor)

        assertEquals(
            listOf(
                EventSeriesCompetitorIdentity("si:123456", "SI 123456"),
                EventSeriesCompetitorIdentity("bib:B-7", "Bib b-7"),
                EventSeriesCompetitorIdentity("call:K0ABC", "Call K0ABC")
            ),
            identities
        )
        assertEquals(identities.first(), EventSeriesSupport.primaryCompetitorIdentity(competitor))
    }

    @Test
    fun sortsCompetitorIdentityLabelsBySeriesIdentityPriority() {
        val labels = listOf("Manual override", "Call K0ABC", "Bib 12", "SI 123456")

        val sorted = labels.sortedWith(EventSeriesSupport.competitorIdentityLabelComparator())

        assertEquals(listOf("SI 123456", "Bib 12", "Call K0ABC", "Manual override"), sorted)
    }

    @Test
    fun validatesMissingAndMismatchedBacklinks() {
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1)))
        val linkedEvents = listOf(
            linkedEvent("day-1", projectFile("Day 1", seriesLink = EventSeriesLink("series-1", "day-1"))),
            linkedEvent("day-2", projectFile("Day 2", seriesLink = EventSeriesLink("other", "day-2")))
        )

        val issues = EventSeriesSupport.validateLinkedEvents(series, linkedEvents)

        assertEquals(listOf("Race File 'day-2' links to a different series."), issues.map { it.message })
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
                "Race File 'day-1' has a duplicate race ID shared with: day-2.",
                "Race File 'day-2' has a duplicate race ID shared with: day-1."
            ),
            issues.map { it.message }
        )
        assertTrue(issues.all { it.severity == EventSeriesIssueSeverity.WARNING })
    }

    @Test
    fun validatesRaceLevelCompatibilityAcrossLinkedEvents() {
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1)))
        val linkedEvents = listOf(
            linkedEvent(
                "day-1",
                projectFile(
                    "Day 1",
                    seriesLink = EventSeriesLink("series-1", "day-1"),
                    raceLevel = RaceLevel.PRACTICE
                )
            ),
            linkedEvent(
                "day-2",
                projectFile(
                    "Day 2",
                    seriesLink = EventSeriesLink("series-1", "day-2"),
                    raceLevel = RaceLevel.REGIONAL
                )
            )
        )

        val issues = EventSeriesSupport.validateLinkedEvents(series, linkedEvents)

        assertEquals(
            listOf("Race File 'day-2' has race level Regional; series member races must all use Practice."),
            issues.map { it.message }
        )
        assertTrue(issues.all { it.severity == EventSeriesIssueSeverity.ERROR })
    }

    @Test
    fun extractsOtherSeriesStartRowsWithGeneratedStartsInManifestOrder() {
        val series = seriesFile(
            events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1), seriesEvent("day-3", 2), seriesEvent("day-4", 3))
        )
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
            ),
            linkedEvent(
                "day-3",
                projectFile(
                    "Day 3",
                    competitors = listOf(competitorData("current-alice", 11, 123456, drawnStartTimeSeconds = 1800)),
                    seriesLink = EventSeriesLink("series-1", "day-3")
                )
            ),
            linkedEvent(
                "day-4",
                projectFile(
                    "Day 4",
                    competitors = listOf(competitorData("alice-4", 11, 123456, drawnStartTimeSeconds = 2400)),
                    seriesLink = EventSeriesLink("series-1", "day-4")
                )
            )
        )

        val rows = EventSeriesSupport.otherSeriesStartRowsForCurrentEvent(series, linkedEvents, "day-3")

        assertEquals(
            listOf(
                listOf(CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "10:00", siNumber = 123456)),
                listOf(CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "20:00", siNumber = 123456)),
                listOf(CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "40:00", siNumber = 123456))
            ),
            rows
        )
    }

    @Test
    fun matchesCompetitorsByPersistentIdentityFields() {
        val series = seriesFile()
        val from = linkedEvent(
            "day-1",
            projectFile(
                "Day 1",
                competitors = listOf(
                    competitorData("si-source", 11, 123456),
                    competitorData("bib-source", 22, null, bibNumber = "B-22"),
                    competitorData("call-source", 33, null, callSign = "K0ABC")
                )
            )
        )
        val to = linkedEvent(
            "day-2",
            projectFile(
                "Day 2",
                competitors = listOf(
                    competitorData("si-target", 99, 123456),
                    competitorData("bib-target", 88, null, bibNumber = "B-22"),
                    competitorData("call-target", 77, null, callSign = "k0abc")
                )
            )
        )

        val report = EventSeriesSupport.matchCompetitors(series, from, to)

        assertEquals(
            listOf(
                EventSeriesCompetitorMatchMethod.SI_NUMBER,
                EventSeriesCompetitorMatchMethod.BIB_NUMBER,
                EventSeriesCompetitorMatchMethod.CALL_SIGN
            ),
            report.matches.map { it.method }
        )
        assertEquals(listOf("si-target", "bib-target", "call-target"), report.matches.map { it.toCompetitorId })
    }

    @Test
    fun doesNotMatchCompetitorsByStartNumberAlone() {
        val series = seriesFile()
        val from = linkedEvent(
            "day-1",
            projectFile("Day 1", competitors = listOf(competitorData("source", 22, null)))
        )
        val to = linkedEvent(
            "day-2",
            projectFile("Day 2", competitors = listOf(competitorData("target", 22, null)))
        )

        val report = EventSeriesSupport.matchCompetitors(series, from, to)

        assertEquals(emptyList<EventSeriesCompetitorMatch>(), report.matches)
    }

    @Test
    fun seriesBalancedDrawUsesSameHistoryAsCsvBalancedDraw() {
        val otherSeriesRows = listOf(
            listOf(
                CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "00:00", siNumber = 111),
                CompetitorStartCsvImportRow(startNumber = 2, startTimeText = "10:00", siNumber = 222),
                CompetitorStartCsvImportRow(startNumber = 3, startTimeText = "20:00", siNumber = 333)
            ),
            listOf(
                CompetitorStartCsvImportRow(startNumber = 1, startTimeText = "20:00", siNumber = 111),
                CompetitorStartCsvImportRow(startNumber = 2, startTimeText = "00:00", siNumber = 222),
                CompetitorStartCsvImportRow(startNumber = 3, startTimeText = "10:00", siNumber = 333)
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
        val series = seriesFile(events = listOf(seriesEvent("day-1", 0), seriesEvent("day-2", 1), seriesEvent("day-3", 2)))
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
            linkedEvent("day-2", currentProject),
            linkedEvent(
                "day-3",
                projectFile(
                    "Day 3",
                    competitors = listOf(
                        competitorData("other-1", 1, 111, drawnStartTimeSeconds = 1200),
                        competitorData("other-2", 2, 222, drawnStartTimeSeconds = 0),
                        competitorData("other-3", 3, 333, drawnStartTimeSeconds = 600)
                    ),
                    seriesLink = EventSeriesLink("series-1", "day-3")
                )
            )
        )

        val csvDraw = EventProjectEditor.drawStartListWithBalancedStartGroups(
            projectFile = currentProject,
            intervalText = "10:00",
            options = StartDrawOptions(seed = "series-test"),
            previousStartLists = otherSeriesRows
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
        raceData.competitorData.mapNotNull {
            val competitor = it.competitorCategory.competitor
            competitor.startNumber?.let { startNumber -> startNumber to competitor.preferredStartGroup }
        }.toMap()

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
        seriesLink: EventSeriesLink? = null,
        raceLevel: RaceLevel = RaceLevel.PRACTICE
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
                    raceLevel = raceLevel,
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
        drawnStartTimeSeconds: Long? = null,
        bibNumber: String = "",
        callSign: String = ""
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
                    drawnStartTimeSeconds = drawnStartTimeSeconds,
                    bibNumber = bibNumber,
                    callSign = callSign
                ),
                category = null
            ),
            readoutData = null
        )
}
