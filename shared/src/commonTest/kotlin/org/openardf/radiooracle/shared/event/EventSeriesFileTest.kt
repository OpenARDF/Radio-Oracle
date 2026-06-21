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
