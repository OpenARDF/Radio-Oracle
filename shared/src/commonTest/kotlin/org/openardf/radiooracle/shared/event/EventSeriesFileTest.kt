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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EventSeriesFileTest {
    @Test
    fun serializesAndDeserializesSeriesManifest() {
        val original = seriesFile()

        val encoded = EventSeriesFileJson.encode(original)
        val decoded = EventSeriesFileJson.decode(encoded)

        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        assertTrue(encoded.contains("\"eventFilePath\": \"day-1.rom.json\""))
        assertEquals(original, decoded)
    }

    @Test
    fun acceptsUnknownFieldsWithinSupportedSchema() {
        val encoded = EventSeriesFileJson.encode(seriesFile())
            .replace("\"name\": \"Championship\"", "\"name\": \"Championship\", \"future\": true")

        assertEquals("Championship", EventSeriesFileJson.decode(encoded).name)
    }

    @Test
    fun serializesAndDeserializesPublicResultsPublication() {
        val publication = PublicResultsPublication(
            url = "https://openardf-results.pages.dev/2026-08-13-championship-series/",
            publishedAtIso = "2026-07-27T12:00:00Z"
        )
        val original = seriesFile().copy(publicResultsPublication = publication)

        val encoded = EventSeriesFileJson.encode(original)
        val decoded = EventSeriesFileJson.decode(encoded)

        assertTrue(encoded.contains("\"publicResultsPublication\""))
        assertEquals(publication, decoded.publicResultsPublication)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val encoded = EventSeriesFileJson.encode(seriesFile())
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

        assertFailsWith<IllegalArgumentException> {
            EventSeriesFileJson.decode(encoded)
        }
    }

    @Test
    fun rejectsDuplicateMembershipData() {
        assertFailsWith<IllegalArgumentException> {
            seriesFile(
                events = listOf(
                    event("day-1", "day-1.rom.json", 0),
                    event("day-1", "copy.rom.json", 1)
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            seriesFile(
                events = listOf(
                    event("day-1", "day-1.rom.json", 0),
                    event("day-2", "day-2.rom.json", 0)
                )
            )
        }
    }

    @Test
    fun rejectsPathsThatEscapeSeriesFolder() {
        assertFailsWith<IllegalArgumentException> {
            event("day-1", "../day-1.rom.json", 0)
        }
    }

    @Test
    fun validatesCompetitorMatchOverrideEventIds() {
        val valid = seriesFile(
            events = listOf(
                event("day-1", "day-1.rom.json", 0),
                event("day-2", "day-2.rom.json", 1)
            ),
            competitorMatchOverrides = listOf(
                EventSeriesCompetitorMatchOverride(
                    fromSeriesEventId = "day-1",
                    fromCompetitorId = "alice-1",
                    toSeriesEventId = "day-2",
                    toCompetitorId = "alice-2"
                )
            )
        )

        assertEquals(1, valid.competitorMatchOverrides.size)
        assertFailsWith<IllegalArgumentException> {
            seriesFile(
                events = listOf(event("day-1", "day-1.rom.json", 0)),
                competitorMatchOverrides = listOf(
                    EventSeriesCompetitorMatchOverride(
                        fromSeriesEventId = "day-1",
                        fromCompetitorId = "alice-1",
                        toSeriesEventId = "missing-day",
                        toCompetitorId = "alice-2"
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            seriesFile(
                events = listOf(event("day-1", "day-1.rom.json", 0)),
                competitorMatchOverrides = listOf(
                    EventSeriesCompetitorMatchOverride(
                        fromSeriesEventId = "day-1",
                        fromCompetitorId = "alice-1",
                        toSeriesEventId = "day-1",
                        toCompetitorId = "alice-2"
                    )
                )
            )
        }
    }

    @Test
    fun sortedEventsUsesDateTimeWhenEveryEventHasUsableDate() {
        val series = seriesFile(
            events = listOf(
                event("day-2", "day-2.rom.json", 0, startDateTimeIso = "2026-06-02T10:00"),
                event("day-1", "day-1.rom.json", 1, startDateTimeIso = "2026-06-01T10:00"),
                event("day-3", "day-3.rom.json", 2, startDateTimeIso = "2026-06-03T10:00:00")
            )
        )

        assertTrue(series.usesDateTimeEventOrder())
        assertEquals(listOf("day-1", "day-2", "day-3"), series.sortedEvents().map { it.seriesEventId })
    }

    @Test
    fun sortedEventsFallsBackToStoredOrderWhenAnyDateIsMissingOrInvalid() {
        val missingDate = seriesFile(
            events = listOf(
                event("day-2", "day-2.rom.json", 0, startDateTimeIso = "2026-06-02T10:00"),
                event("day-1", "day-1.rom.json", 1, startDateTimeIso = "")
            )
        )
        val invalidDate = seriesFile(
            events = listOf(
                event("day-2", "day-2.rom.json", 0, startDateTimeIso = "2026-06-02T10:00"),
                event("day-1", "day-1.rom.json", 1, startDateTimeIso = "not-a-date")
            )
        )

        assertEquals(listOf("day-2", "day-1"), missingDate.sortedEvents().map { it.seriesEventId })
        assertEquals(false, missingDate.usesDateTimeEventOrder())
        assertEquals(listOf("day-2", "day-1"), invalidDate.sortedEvents().map { it.seriesEventId })
        assertEquals(false, invalidDate.usesDateTimeEventOrder())
    }

    private fun seriesFile(
        events: List<EventSeriesEvent> = listOf(event("day-1", "day-1.rom.json", 0)),
        competitorMatchOverrides: List<EventSeriesCompetitorMatchOverride> = emptyList()
    ): EventSeriesFile =
        EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = events,
            competitorMatchOverrides = competitorMatchOverrides
        )

    private fun event(id: String, path: String, order: Int, startDateTimeIso: String = ""): EventSeriesEvent =
        EventSeriesEvent(
            seriesEventId = id,
            eventFilePath = path,
            order = order,
            displayName = id,
            startDateTimeIso = startDateTimeIso
        )
}
