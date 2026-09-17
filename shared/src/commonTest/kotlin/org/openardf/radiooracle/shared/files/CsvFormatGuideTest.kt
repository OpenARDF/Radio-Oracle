package org.openardf.radiooracle.shared.files

import kotlin.test.*

class CsvFormatGuideTest {
    @Test fun recommendedTemplatesAndExamplesWorkWithExistingImporters() {
        val guides = listOf(CsvFormatGuides.categories(true), CsvFormatGuides.competitors(true),
            CsvFormatGuides.controls(true), CsvFormatGuides.starts(true))
        guides.forEach { guide ->
            val csv = guide.template + guide.exampleRow + "\r\n"
            val result = when (guide.id) {
                "categories" -> EventCsvImports.parseAndroidCategoryRows(csv)
                "competitors" -> EventCsvImports.parseAndroidCompetitorRows(csv)
                "controls" -> EventCsvImports.parseControlRows(csv)
                else -> EventCsvImports.parseAndroidCompetitorStartRows(csv)
            }
            assertEquals(emptyList(), result.invalidLines, guide.title)
            assertEquals(1, result.rows.size, guide.title)
            assertEquals(listOf(guide.columns), CsvCodec.records(guide.template).map { it.fields })
        }
        CsvFormatGuides.starts(true).alternatives.forEach { guide ->
            val result = EventCsvImports.parseAndroidCompetitorStartRows(guide.template + guide.exampleRow)
            assertEquals(emptyList(), result.invalidLines, guide.title)
            assertEquals(1, result.rows.size)
        }
    }

    @Test fun dynamicExamplesHaveTheSameNumberOfFieldsAndCorrectTimeContext() {
        for (count in listOf(0, 1, 7)) {
            val guide = CsvFormatGuides.readouts(count)
            assertEquals(guide.columns.size, CsvCodec.records(guide.exampleRow).single().fields.size)
            assertEquals(count.toString(), CsvCodec.records(guide.exampleRow).single().fields[4])
            assertEquals("10:00:00", CsvCodec.records(guide.exampleRow).single().fields[2])
        }
        assertTrue(CsvFormatGuides.robisStarts().description("start_time").contains("hours:minutes:seconds"))
        assertFalse(CsvFormatGuides.robisStarts().includesHeader)
        assertEquals("", CsvFormatGuides.robisStarts().template)
        assertEquals(';', CsvFormatGuides.ardfEventResults().delimiter)
    }

    @Test fun controlsExampleDemonstratesQuotingAndOptionalFieldsRemainInPlace() {
        val guide = CsvFormatGuides.controls(true)
        assertTrue(guide.exampleRow.contains("\"Near path, north side\""))
        val row = EventCsvImports.parseControlRows(guide.template + guide.exampleRow).rows.single()
        assertEquals("Near path, north side", row.notes)
        assertTrue(guide.orderRule.contains("headers do not map or reorder"))
        assertTrue(CsvFormatGuides.competitors(true).description("preferred_start_group").contains("1, 2 or 3"))
    }
}
