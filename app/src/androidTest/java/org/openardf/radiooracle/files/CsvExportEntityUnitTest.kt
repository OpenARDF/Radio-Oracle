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
            "123456789;0;Test;Tester;M20;0;2000;AC-Test;ACT0001;;0;;ACT0001;",
            competitor.toSimpleCsvString(categoryStr)
        )
    }
}
