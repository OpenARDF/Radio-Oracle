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

    private fun seriesFile(events: List<EventSeriesEvent> = listOf(event("day-1", "day-1.rom.json", 0))): EventSeriesFile =
        EventSeriesFile(
            seriesId = "series-1",
            name = "Championship",
            events = events
        )

    private fun event(id: String, path: String, order: Int): EventSeriesEvent =
        EventSeriesEvent(
            seriesEventId = id,
            eventFilePath = path,
            order = order,
            displayName = id
        )
}
