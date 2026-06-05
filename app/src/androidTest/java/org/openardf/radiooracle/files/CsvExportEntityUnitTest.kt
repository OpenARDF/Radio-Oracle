package org.openardf.radiooracle.files

import org.openardf.radiooracle.backend.room.entity.Category
import org.openardf.radiooracle.backend.room.entity.Competitor
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportEntityUnitTest {

    @Test
    fun testCategoryCsvString() {
        val category = Category("TEST")
        assertEquals("TEST;1;99;0;0;1;;;", category.toCSVString())
    }

    @Test
    fun testControlPointCsvString() {
        val controlPoint = ControlPoint()
        assertEquals("31", controlPoint.toCsvString())
        controlPoint.siCode = 99
        controlPoint.type = ControlPointType.BEACON
        assertEquals("99B", controlPoint.toCsvString())
        controlPoint.siCode = 40
        controlPoint.type = ControlPointType.SEPARATOR
        assertEquals("40!", controlPoint.toCsvString())
    }

    @Test
    fun testCompetitorCsvString() {
        val competitor = Competitor()
        val categoryStr = "M20"
        assertEquals(
            "123456789;0;Test;Tester;M20;0;2000;AC-Test;ACT0001;;0",
            competitor.toSimpleCsvString(categoryStr)
        )
    }
}
