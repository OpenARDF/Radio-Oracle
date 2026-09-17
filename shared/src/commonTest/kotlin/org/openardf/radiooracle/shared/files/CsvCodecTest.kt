package org.openardf.radiooracle.shared.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsvCodecTest {
    @Test
    fun roundTripsQuotedFieldsWithBothLineEndingsAndTrailingEmptyCells() {
        val fields = listOf("José, Jane", "Club; East", "Fox \"one\"", "line1\r\nline2\nline3", "", "  spaced  ", "")
        val csv = CsvCodec.row(fields)
        assertTrue(csv.startsWith("\"José, Jane\",Club; East,\"Fox \"\"one\"\"\""))
        for (ending in listOf("\n", "\r\n", "\r")) {
            val records = CsvCodec.records("\uFEFF" + csv + ending + CsvCodec.row(listOf("next", "")) + ending)
            assertEquals(fields, records[0].fields)
            assertEquals(listOf("next", ""), records[1].fields)
            assertEquals(3, records[1].lineIndex)
            assertTrue(records.all { it.error == null })
        }
    }

    @Test
    fun readsLegacySemicolonFieldsWithoutSplittingCommaLists() {
        val records = CsvCodec.records("M21;\"club; east\";71,72,73;\"say \"\"go\"\"\";\n", ';')
        assertEquals(listOf("M21", "club; east", "71,72,73", "say \"go\"", ""), records.single().fields)
    }

    @Test
    fun reportsMalformedQuotesWithoutDiscardingFollowingValidRecords() {
        val records = CsvCodec.records("a,\"b\"oops,c\r\nvalid,row,\r\nbroken,\"open\nfield")
        assertNotNull(records[0].error)
        assertEquals(listOf("valid", "row", ""), records[1].fields)
        assertEquals(null, records[1].error)
        assertEquals(2, records[2].lineIndex)
        assertEquals("Unclosed quoted field", records[2].error)
    }

    @Test
    fun ignoresBlankPhysicalLinesButKeepsExplicitEmptyRecords() {
        val records = CsvCodec.records("\uFEFF\r\n \n,,\n\"\"\n")
        assertEquals(listOf(listOf("", "", ""), listOf("")), records.map { it.fields })
        assertEquals(listOf(2, 3), records.map { it.lineIndex })
    }
}
