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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.ARDFRepository
import org.openardf.radiooracle.backend.room.entity.ControlPoint
import org.openardf.radiooracle.backend.room.entity.embeddeds.CategoryData
import org.openardf.radiooracle.backend.room.enums.ControlPointType
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CsvExportInstrumentedTest {

    @Test
    fun testCategoryCSVExport() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        ARDFRepository.initialize(appContext)
        DataProcessor.initialize(appContext)

        val stream = ByteArrayOutputStream()
        val categoryData = ArrayList<CategoryData>()
        val controlPoints = ArrayList<ControlPoint>()

        //Mock the control points
        for (i in 1..5) {
            controlPoints.add(
                ControlPoint(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    30 + i,
                    ControlPointType.CONTROL,
                    i,
                )
            )
        }
//        val category = Category(
//            UUID.randomUUID(),
//            UUID.randomUUID(),
//            "M20",
//            true,
//            40,
//            4.3F,
//            30F,
//            0,
//            true,
//            RaceType.CLASSIC,
//            Duration.ofMinutes(120),
//            null,
//            null,""
//        )


//        categoryData.add(
//            CategoryData(
//                category,
//                controlPoints, emptyList()
//            )
//        )
//        runBlocking {
//           // CsvProcessor.exportCategories(categoryData, stream)
//        }
//        val expected = ""
//        Assert.assertEquals(expected, stream.toString())
    }
}