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

package org.openardf.radiooracle.publicresults

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsPublicationLookup
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsPublishingService
import org.openardf.radiooracle.backend.publicresults.AndroidPublicResultsRemotePublication
import org.openardf.radiooracle.backend.publicresults.publicResultsPublicationId
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.EventSeries
import org.openardf.radiooracle.backend.room.entity.EventSeriesMember
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.RaceBand
import org.openardf.radiooracle.backend.room.enums.RaceLevel
import org.openardf.radiooracle.backend.room.enums.RaceType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AndroidPublicResultsPublishingServiceTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun initializeBackend() {
        DataProcessor.resetForTests()
        ARDFRepository.resetForTests()
        context.deleteDatabase("event-database")
        ARDFRepository.initialize(context)
        DataProcessor.initialize(context)
    }

    @Test
    fun recoversPublishedSeriesUrlWhenPackagePredatesDesktopPublication() = runBlocking {
        val processor = DataProcessor.get()
        val race = race("Imported Sprint")
        processor.saveRaceData(raceData(race))
        val series = EventSeries(seriesId = "series-1", name = "Championship")
        processor.saveEventSeries(
            series,
            listOf(
                EventSeriesMember(
                    seriesId = series.seriesId,
                    seriesEventId = "sprint",
                    localRaceId = race.id,
                    eventFilePath = "sprint.rom.json",
                    eventOrder = 0,
                    displayName = race.name,
                    startDateTimeIso = "2026-08-13T09:00:00",
                    formatLabel = "Sprint"
                )
            )
        )
        val lookup = AndroidPublicResultsPublicationLookup { baseUrl, publicationId ->
            assertEquals("https://openardf-results.pages.dev", baseUrl)
            assertEquals("series:series-1", publicationId)
            PUBLICATION
        }
        val service = AndroidPublicResultsPublishingService(
            context = context,
            dataProcessor = processor,
            publicationLookup = lookup
        )

        val target = service.target(race.id, "https://openardf-results.pages.dev")

        assertEquals(PUBLICATION.url, target.savedUrl)
        assertEquals(PUBLICATION.publishedAtIso, target.publishedAtIso)
        assertTrue(target.canViewPublicResults)
        assertEquals(
            PUBLICATION.url,
            processor.getEventSeries(series.seriesId)?.series?.publicResultsUrl
        )
    }

    @Test
    fun importedStandaloneRaceUsesOriginalDesktopPublicationId() = runBlocking {
        val processor = DataProcessor.get()
        val race = race("Imported Classic").copy(importSourceId = "event-file:desktop-race-id")
        processor.saveRaceData(raceData(race))
        val lookup = AndroidPublicResultsPublicationLookup { _, publicationId ->
            assertEquals("race:desktop-race-id", publicationId)
            PUBLICATION
        }
        val service = AndroidPublicResultsPublishingService(
            context = context,
            dataProcessor = processor,
            publicationLookup = lookup
        )

        val target = service.target(race.id, "https://openardf-results.pages.dev")

        assertEquals(PUBLICATION.url, target.savedUrl)
        assertEquals(PUBLICATION.url, processor.getRace(race.id)?.publicResultsUrl)
    }

    @Test
    fun savedPublicationDoesNotRequireRemoteLookup() = runBlocking {
        val processor = DataProcessor.get()
        val race = race("Already published").copy(
            publicResultsUrl = PUBLICATION.url,
            publicResultsPublishedAtIso = PUBLICATION.publishedAtIso
        )
        processor.saveRaceData(raceData(race))
        val service = AndroidPublicResultsPublishingService(
            context = context,
            dataProcessor = processor,
            publicationLookup = AndroidPublicResultsPublicationLookup { _, _ ->
                throw AssertionError("Saved publications must not trigger a network lookup.")
            }
        )

        val target = service.target(race.id, "https://openardf-results.pages.dev")

        assertEquals(PUBLICATION.url, target.savedUrl)
        assertTrue(target.canViewPublicResults)
    }

    @Test
    fun seriesImportSourceStillResolvesOriginalRacePublicationId() {
        assertEquals(
            "race:desktop-race-id",
            publicResultsPublicationId(
                "event-series:series-1:day-1:event-file:desktop-race-id",
                UUID.fromString("11111111-1111-1111-1111-111111111111")
            )
        )
    }

    private fun race(name: String): Race =
        Race(
            id = UUID.randomUUID(),
            name = name,
            apiKey = "",
            startDateTime = LocalDateTime.of(2026, 8, 13, 9, 0),
            raceType = RaceType.CLASSIC,
            raceLevel = RaceLevel.PRACTICE,
            raceBand = RaceBand.M80,
            timeLimit = Duration.ofHours(2)
        )

    private fun raceData(race: Race): RaceData =
        RaceData(
            race = race,
            categories = emptyList(),
            aliases = emptyList(),
            competitorData = emptyList(),
            unmatchedReadoutData = emptyList()
        )

    companion object {
        private val PUBLICATION = AndroidPublicResultsRemotePublication(
            url = "https://openardf-results.pages.dev/championship-series/",
            publishedAtIso = "2026-08-11T22:47:21Z"
        )
    }
}
