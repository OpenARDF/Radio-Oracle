package org.openardf.radiooracle.shared.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCsvImportsTest {
    @Test
    fun parsesAndroidCategoryImportRows() {
        val result = EventCsvImports.parseAndroidCategoryRows(
            "M21;1;99;5000;100;0;SPRINT;45;80m;31 32 90B;"
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(1, result.rows.size)

        val row = result.rows.single()
        assertEquals("M21", row.name)
        assertTrue(row.isMan)
        assertEquals(99, row.maxAge)
        assertEquals(5000, row.lengthMeters)
        assertEquals(100, row.climbMeters)
        assertFalse(row.followsRacePresets)
        assertEquals(org.openardf.radiooracle.shared.domain.RaceType.SPRINT, row.raceType)
        assertEquals(45, row.timeLimitMinutes)
        assertEquals(org.openardf.radiooracle.shared.domain.RaceBand.M80, row.raceBand)
        assertEquals("31 32 90B", row.controlPointsText)
    }

    @Test
    fun parsesAndroidExportedCategoryControlColumn() {
        val result = EventCsvImports.parseAndroidCategoryRows(
            "M21;1;99;5000;100;1;;;;3;31,32,90B"
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals("31 32 90B", result.rows.single().controlPointsText)
    }

    @Test
    fun categoryControlPointColumnsComeFromSharedContract() {
        val fields = MutableList(EventCsvFormat.Category.COLUMN_COUNT) { "" }
        fields[EventCsvFormat.Category.ANDROID_IMPORT_CONTROL_POINTS] = "3"
        fields[EventCsvFormat.Category.EXPORTED_CONTROL_POINTS] = "31,32,90B"

        assertEquals("31 32 90B", EventCsvFormat.Category.controlPointsFrom(fields))
    }

    @Test
    fun reportsInvalidCategoryImportLines() {
        val result = EventCsvImports.parseAndroidCategoryRows(
            """
            ;1;99;5000;100;1;;;;3;31,32,90B
            W21;0;99;5000;100;1;;;;3;31,32,90B
            """.trimIndent()
        )

        assertEquals(1, result.rows.size)
        assertEquals("W21", result.rows.single().name)
        assertEquals(1, result.invalidLines.size)
        assertEquals("Invalid category data at line: 0", result.invalidLines.single().message)
    }

    @Test
    fun parsesAndroidCompetitorImportRows() {
        val result = EventCsvImports.parseAndroidCompetitorRows(
            "123456;42;Pavel;Kolsky;M21;0;1980;OK Lokomotiva;OK001;10:00;1"
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(1, result.rows.size)

        val row = result.rows.single()
        assertEquals(123456, row.siNumber)
        assertEquals(42, row.startNumber)
        assertEquals("Pavel", row.firstName)
        assertEquals("Kolsky", row.lastName)
        assertEquals("M21", row.categoryName)
        assertTrue(row.isMan)
        assertEquals(1980, row.birthYear)
        assertEquals("OK Lokomotiva", row.club)
        assertEquals("OK001", row.index)
        assertEquals("10:00", row.startTimeText)
        assertTrue(row.siRent)
    }

    @Test
    fun skipsOptionalCompetitorHeaderRow() {
        val result = EventCsvImports.parseAndroidCompetitorRows(
            """
            ${EventCsvFormat.Competitor.HEADER_ROW}
            123456;42;Pavel;Kolsky;M21;0;1980;OK Lokomotiva;OK001;10:00;1
            """.trimIndent()
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(1, result.rows.size)
        assertEquals("Pavel", result.rows.single().firstName)
    }

    @Test
    fun allowsOptionalCompetitorImportFields() {
        val result = EventCsvImports.parseAndroidCompetitorRows(
            "; ;Anna;Berg;;1;;;;;"
        )

        assertEquals(emptyList(), result.invalidLines)

        val row = result.rows.single()
        assertEquals(null, row.siNumber)
        assertEquals(null, row.startNumber)
        assertEquals("", row.categoryName)
        assertFalse(row.isMan)
        assertEquals(null, row.birthYear)
        assertEquals("", row.club)
        assertEquals("", row.index)
        assertEquals(null, row.startTimeText)
        assertFalse(row.siRent)
    }

    @Test
    fun reportsInvalidCompetitorImportLines() {
        val result = EventCsvImports.parseAndroidCompetitorRows(
            """
            999;1;Invalid;Card;M21;0
            123456;2;;Name;M21;0
            123457;3;Valid;Runner;W21;1
            """.trimIndent()
        )

        assertEquals(1, result.rows.size)
        assertEquals("Valid", result.rows.single().firstName)
        assertEquals(2, result.invalidLines.size)
        assertEquals(0, result.invalidLines[0].lineIndex)
        assertEquals("Invalid SI number at line: 0", result.invalidLines[0].message)
        assertEquals(1, result.invalidLines[1].lineIndex)
        assertEquals("Missing first/last name at line: 1", result.invalidLines[1].message)
    }

    @Test
    fun parsesQuotedSemicolonFields() {
        val result = EventCsvImports.parseAndroidCompetitorRows(
            "123456;42;\"Pa\"\"vel\";Kolsky;M21;0;1980;\"OK; Lokomotiva\";OK001;10:00;0"
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals("Pa\"vel", result.rows.single().firstName)
        assertEquals("OK; Lokomotiva", result.rows.single().club)
        assertFalse(result.rows.single().siRent)
    }

    @Test
    fun parsesAndroidCompetitorStartRows() {
        val result = EventCsvImports.parseAndroidCompetitorStartRows(
            """
            42;10:00;123456
            43;10:05;
            """.trimIndent()
        )

        assertEquals(emptyList(), result.invalidLines)
        assertEquals(
            listOf(
                CompetitorStartCsvImportRow(startNumber = 42, startTimeText = "10:00", siNumber = 123456),
                CompetitorStartCsvImportRow(startNumber = 43, startTimeText = "10:05", siNumber = null)
            ),
            result.rows
        )
    }

    @Test
    fun reportsInvalidCompetitorStartRows() {
        val result = EventCsvImports.parseAndroidCompetitorStartRows(
            """
            42;10:00;999
            43;10:05
            44;10:10;123456
            """.trimIndent()
        )

        assertEquals(
            listOf(CompetitorStartCsvImportRow(startNumber = 44, startTimeText = "10:10", siNumber = 123456)),
            result.rows
        )
        assertEquals(2, result.invalidLines.size)
        assertEquals("Invalid SI number at line: 0", result.invalidLines[0].message)
        assertEquals("Expected 3 columns at line: 1", result.invalidLines[1].message)
    }
}
